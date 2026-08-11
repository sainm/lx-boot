#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)_$$"
RECOVERY_SCHEMA="${PSY_EXPORT_RECOVERY_SCHEMA:-psy_export_recovery_${RUN_ID}}"
DB_HOST="${PSY_EXPORT_RECOVERY_DB_HOST:-localhost}"
DB_PORT="${PSY_EXPORT_RECOVERY_DB_PORT:-5432}"
DB_NAME="${PSY_EXPORT_RECOVERY_DB_NAME:-postgres}"
DB_USERNAME="${PSY_EXPORT_RECOVERY_DB_USERNAME:-$(id -un)}"
DB_PASSWORD="${PSY_EXPORT_RECOVERY_DB_PASSWORD:-}"
BACKEND_PORT="${PSY_EXPORT_RECOVERY_BACKEND_PORT:-8092}"
STORAGE_PORT="${PSY_EXPORT_RECOVERY_STORAGE_PORT:-18092}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/psy-export-worker-recovery.XXXXXX")"
TMP_DIR="$(cd "${TMP_DIR}" && pwd -P)"
ARTIFACT_DIR="${TMP_DIR}/artifacts"
PUT_MARKER="${TMP_DIR}/put-received"
JOB_ID="export-worker-recovery-${RUN_ID}"
BACKEND_PID=""
STORAGE_PID=""
SCHEMA_CREATED=false

if [[ ! "${RECOVERY_SCHEMA}" =~ ^psy_export_recovery_[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe recovery schema name: ${RECOVERY_SCHEMA}" >&2
  exit 2
fi
for port_value in "${BACKEND_PORT}" "${STORAGE_PORT}"; do
  if [[ ! "${port_value}" =~ ^[0-9]+$ ]] || (( port_value < 1024 || port_value > 65535 )); then
    echo "Unsafe rehearsal port: ${port_value}" >&2
    exit 2
  fi
done
for command_name in psql curl lsof python3 java; do
  command -v "${command_name}" >/dev/null || { echo "Required command not found: ${command_name}" >&2; exit 2; }
done
for port_value in "${BACKEND_PORT}" "${STORAGE_PORT}"; do
  if lsof -nP -iTCP:"${port_value}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${port_value} is already in use; refusing to stop or reuse an unrelated process." >&2
    exit 2
  fi
done

export PGPASSWORD="${DB_PASSWORD}"
psql_cmd=(psql -X -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -v ON_ERROR_STOP=1)

stop_backend() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
    BACKEND_PID=""
  fi
}

stop_storage() {
  if [[ -n "${STORAGE_PID}" ]]; then
    kill "${STORAGE_PID}" 2>/dev/null || true
    wait "${STORAGE_PID}" 2>/dev/null || true
    STORAGE_PID=""
  fi
}

cleanup() {
  local exit_code=$?
  set +e
  stop_backend
  stop_storage
  if [[ "${SCHEMA_CREATED}" == "true" ]]; then
    "${psql_cmd[@]}" -c "drop schema if exists \"${RECOVERY_SCHEMA}\" cascade" >/dev/null 2>&1
  fi
  if [[ ${exit_code} -eq 0 ]]; then
    find "${TMP_DIR}" -type f -delete
    find "${TMP_DIR}" -depth -type d -empty -delete
  else
    echo "Export worker recovery rehearsal failed. Evidence retained at ${TMP_DIR}" >&2
    [[ -f "${TMP_DIR}/backend-before-crash.log" ]] && tail -n 100 "${TMP_DIR}/backend-before-crash.log" >&2
    [[ -f "${TMP_DIR}/backend-after-restart.log" ]] && tail -n 100 "${TMP_DIR}/backend-after-restart.log" >&2
  fi
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

start_backend() {
  local log_path=$1
  local sql_init_mode=$2
  local storage_mode=$3
  local processing_timeout_seconds=$4

  PSY_DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=${RECOVERY_SCHEMA}" \
  PSY_DB_USERNAME="${DB_USERNAME}" \
  PSY_DB_PASSWORD="${DB_PASSWORD}" \
  PSY_FLYWAY_ENABLED=true \
  PSY_SQL_INIT_MODE="${sql_init_mode}" \
  PSY_SCHEDULER_LOCK_ENABLED=false \
  PSY_EXPORT_PENDING_SCAN_DELAY_MS=250 \
  PSY_EXPORT_PENDING_BATCH_SIZE=5 \
  PSY_EXPORT_PROCESSING_TIMEOUT_SECONDS="${processing_timeout_seconds}" \
  PSY_EXPORT_MAX_ATTEMPTS=3 \
  PSY_EXPORT_INITIAL_RETRY_DELAY_SECONDS=1 \
  PSY_EXPORT_MAX_RETRY_DELAY_SECONDS=2 \
  PSY_EXPORT_ARTIFACT_STORAGE_MODE="${storage_mode}" \
  PSY_EXPORT_ARTIFACT_BASE_DIR="${ARTIFACT_DIR}" \
  PSY_EXPORT_ARTIFACT_ENDPOINT_URL="http://127.0.0.1:${STORAGE_PORT}" \
  PSY_EXPORT_ARTIFACT_REQUEST_TIMEOUT_MILLIS=300000 \
  SERVER_PORT="${BACKEND_PORT}" \
  java -jar "${PROJECT_ROOT}/backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar" >"${log_path}" 2>&1 &
  BACKEND_PID=$!

  for _ in $(seq 1 90); do
    if curl --silent --fail "http://127.0.0.1:${BACKEND_PORT}/auth/register/options" >/dev/null; then
      return
    fi
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      echo "Backend exited before becoming ready." >&2
      exit 1
    fi
    sleep 1
  done
  echo "Backend did not become ready within 90 seconds." >&2
  exit 1
}

job_value() {
  local expression=$1
  PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -Atc \
    "select ${expression} from psy_export_job where id = '${JOB_ID}'"
}

wait_for_job_status() {
  local expected_status=$1
  local attempts=$2
  for _ in $(seq 1 "${attempts}"); do
    if [[ "$(job_value status)" == "${expected_status}" ]]; then
      return
    fi
    sleep 1
  done
  echo "Job ${JOB_ID} did not reach ${expected_status}; current status=$(job_value status)" >&2
  exit 1
}

echo "Building backend artifact for crash/restart rehearsal..."
(
  cd "${PROJECT_ROOT}/backend"
  ./gradlew bootJar --no-daemon
)

"${psql_cmd[@]}" -c "create schema \"${RECOVERY_SCHEMA}\""
SCHEMA_CREATED=true

python3 "${PROJECT_ROOT}/scripts/fixtures/hanging_object_storage.py" \
  --port "${STORAGE_PORT}" --marker "${PUT_MARKER}" >"${TMP_DIR}/storage.log" 2>&1 &
STORAGE_PID=$!

start_backend "${TMP_DIR}/backend-before-crash.log" always HTTP_OBJECT_STORAGE 300

PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -c "
  insert into psy_export_job (
    id, tenant_id, created_by, status, report_id, result_id, export_format,
    locale_tag, desensitized_flag, created_at, updated_at
  )
  select
    '${JOB_ID}', sheet.tenant_id, coalesce(report.author_user_id, sheet.user_id),
    'PENDING', report.id, result.id, 'TEXT', 'zh-CN', true,
    current_timestamp, current_timestamp
  from psy_report report
  join psy_assessment_result result on result.id = report.result_id
  join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id
  where sheet.tenant_id is not null
  order by report.id
  limit 1;
"

for _ in $(seq 1 60); do
  if [[ -f "${PUT_MARKER}" ]] && [[ "$(job_value status)" == "PROCESSING" ]]; then
    break
  fi
  sleep 1
done
if [[ ! -f "${PUT_MARKER}" ]] || [[ "$(job_value status)" != "PROCESSING" ]]; then
  echo "Worker did not block in external artifact storage as expected." >&2
  exit 1
fi
first_processing_token="$(job_value processing_token)"
if [[ -z "${first_processing_token}" ]]; then
  echo "PROCESSING job has no lease token." >&2
  exit 1
fi

crashed_pid="${BACKEND_PID}"
kill -9 "${crashed_pid}"
wait "${crashed_pid}" 2>/dev/null || true
BACKEND_PID=""
stop_storage
echo "Killed worker process ${crashed_pid} while its artifact PUT was blocked."

sleep 3
start_backend "${TMP_DIR}/backend-after-restart.log" never LOCAL_PATH 2
wait_for_job_status DONE 90

retry_count="$(job_value retry_count)"
processing_token="$(job_value "coalesce(processing_token, '')")"
artifact_path="$(job_value "coalesce(file_path, '')")"
job_count="$(PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -Atc "select count(*) from psy_export_job where id = '${JOB_ID}'")"
if [[ "${retry_count}" != "1" ]]; then
  echo "Expected exactly one recovered attempt, got retry_count=${retry_count}." >&2
  exit 1
fi
if [[ -n "${processing_token}" ]]; then
  echo "Completed job still has a processing token." >&2
  exit 1
fi
if [[ "${job_count}" != "1" ]]; then
  echo "Expected one durable export job row, got ${job_count}." >&2
  exit 1
fi
if [[ -z "${artifact_path}" || ! -f "${artifact_path}" ]]; then
  echo "Recovered export artifact does not exist: ${artifact_path}" >&2
  exit 1
fi
if [[ "${artifact_path}" != "${ARTIFACT_DIR}"/* ]]; then
  echo "Recovered export artifact escaped the isolated rehearsal directory." >&2
  exit 1
fi

echo "Export worker crash/restart rehearsal passed."
echo "schema=${RECOVERY_SCHEMA} job=${JOB_ID} final_status=DONE retry_count=${retry_count} rows=${job_count}"
