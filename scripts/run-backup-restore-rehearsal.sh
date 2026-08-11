#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)_$$"
SOURCE_DB="${PSY_RECOVERY_SOURCE_DB:-psy_recovery_source_${RUN_ID}}"
RESTORE_DB="${PSY_RECOVERY_RESTORE_DB:-psy_recovery_restore_${RUN_ID}}"
DB_HOST="${PSY_RECOVERY_DB_HOST:-localhost}"
DB_PORT="${PSY_RECOVERY_DB_PORT:-5432}"
DB_USERNAME="${PSY_RECOVERY_DB_USERNAME:-$(id -un)}"
DB_PASSWORD="${PSY_RECOVERY_DB_PASSWORD:-}"
BACKEND_PORT="${PSY_RECOVERY_BACKEND_PORT:-8091}"
KEEP_DATABASES="${PSY_RECOVERY_KEEP_DATABASES:-false}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/psy-backup-restore.XXXXXX")"
BACKEND_PID=""
SOURCE_CREATED=false
RESTORE_CREATED=false

if [[ ! "${SOURCE_DB}" =~ ^psy_recovery_source_[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe source database name: ${SOURCE_DB}" >&2
  exit 2
fi
if [[ ! "${RESTORE_DB}" =~ ^psy_recovery_restore_[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe restore database name: ${RESTORE_DB}" >&2
  exit 2
fi
if [[ "${SOURCE_DB}" == "${RESTORE_DB}" ]]; then
  echo "Source and restore database names must differ." >&2
  exit 2
fi
if [[ ! "${BACKEND_PORT}" =~ ^[0-9]+$ ]] || (( BACKEND_PORT < 1024 || BACKEND_PORT > 65535 )); then
  echo "Unsafe backend port: ${BACKEND_PORT}" >&2
  exit 2
fi

for command_name in psql pg_dump pg_restore createdb dropdb curl lsof python3 java shasum; do
  command -v "${command_name}" >/dev/null || { echo "Required command not found: ${command_name}" >&2; exit 2; }
done
if lsof -nP -iTCP:"${BACKEND_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Backend port ${BACKEND_PORT} is already in use; refusing to stop or reuse an unrelated process." >&2
  exit 2
fi

export PGPASSWORD="${DB_PASSWORD}"
psql_base=(psql -X -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -v ON_ERROR_STOP=1)
createdb_base=(createdb -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}")
dropdb_base=(dropdb -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" --if-exists)

now_ms() {
  python3 -c 'import time; print(time.time_ns() // 1_000_000)'
}

stop_backend() {
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null || true
    wait "${BACKEND_PID}" 2>/dev/null || true
    BACKEND_PID=""
  fi
}

cleanup() {
  local exit_code=$?
  set +e
  stop_backend
  if [[ "${KEEP_DATABASES}" != "true" ]]; then
    [[ "${RESTORE_CREATED}" == "true" ]] && "${dropdb_base[@]}" --force "${RESTORE_DB}" >/dev/null 2>&1
    [[ "${SOURCE_CREATED}" == "true" ]] && "${dropdb_base[@]}" --force "${SOURCE_DB}" >/dev/null 2>&1
  fi
  if [[ ${exit_code} -eq 0 && "${KEEP_DATABASES}" != "true" ]]; then
    find "${TMP_DIR}" -type f -delete
    rmdir "${TMP_DIR}"
  else
    echo "Recovery rehearsal artifacts retained at ${TMP_DIR}" >&2
    [[ -f "${TMP_DIR}/backend.log" ]] && tail -n 120 "${TMP_DIR}/backend.log" >&2
  fi
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

start_backend() {
  local database_name=$1
  local sql_init_mode=$2
  : >"${TMP_DIR}/backend.log"
  PSY_DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${database_name}" \
  PSY_DB_USERNAME="${DB_USERNAME}" \
  PSY_DB_PASSWORD="${DB_PASSWORD}" \
  PSY_FLYWAY_ENABLED=true \
  PSY_SQL_INIT_MODE="${sql_init_mode}" \
  PSY_SCHEDULER_LOCK_ENABLED=false \
  FLYWAY_POSTGRESQL_TRANSACTIONAL_LOCK=false \
  SERVER_PORT="${BACKEND_PORT}" \
  java -jar "${PROJECT_ROOT}/backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar" \
    >"${TMP_DIR}/backend.log" 2>&1 &
  BACKEND_PID=$!

  for _ in $(seq 1 90); do
    if curl --silent --fail "http://127.0.0.1:${BACKEND_PORT}/auth/register/options" >/dev/null; then
      return
    fi
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      echo "Backend exited before becoming ready for ${database_name}." >&2
      return 1
    fi
    sleep 1
  done
  echo "Backend readiness timed out for ${database_name}." >&2
  return 1
}

smoke_business_apis() {
  local database_name=$1
  local export_job_id expected_export_hex
  export_job_id=$("${psql_base[@]}" -d "${database_name}" -Atc "
    select job.id
    from psy_export_job job
    join sys_tenant tenant on tenant.id = job.tenant_id
    where job.status='DONE' and job.file_bytes is not null and tenant.tenant_code='DEFAULT'
    order by job.id
    limit 1
  ")
  if [[ -z "${export_job_id}" ]]; then
    echo "No completed DEFAULT tenant export artifact is available for application smoke." >&2
    return 1
  fi
  expected_export_hex=$("${psql_base[@]}" -d "${database_name}" -Atc "select encode(file_bytes, 'hex') from psy_export_job where id = '${export_job_id}'")
  PSY_RECOVERY_BASE_URL="http://127.0.0.1:${BACKEND_PORT}" \
  PSY_RECOVERY_EXPORT_JOB_ID="${export_job_id}" \
  PSY_RECOVERY_EXPORT_HEX="${expected_export_hex}" \
  python3 <<'PY'
import hashlib
import json
import os
import urllib.error
import urllib.request

base_url = os.environ["PSY_RECOVERY_BASE_URL"]

def request(path, *, method="GET", payload=None, token=None):
    headers = {"Accept-Language": "en-US"}
    body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(base_url + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read(), response.headers
    except urllib.error.HTTPError as error:
        raise SystemExit(f"{method} {path} failed with {error.code}: {error.read().decode('utf-8', 'replace')}") from error

def login(principal):
    status, body, _ = request(
        "/auth/login/password",
        method="POST",
        payload={
            "principal": principal,
            "password": "ChangeMe123",
            "deviceId": f"backup-restore-rehearsal-{principal}",
            "deviceType": "OPS",
            "deviceName": "Backup restore rehearsal",
        },
    )
    if status != 200:
        raise SystemExit(f"login for {principal} returned {status}")
    return json.loads(body)["data"]["accessToken"]

assessor_token = login("assessor")
campus_assessor_token = login("campus_assessor")
report_items = None

for path in (
    "/api/v1/tasks?page=1&size=20",
    "/api/v1/reports?page=1&size=20",
):
    status, body, _ = request(path, token=assessor_token)
    payload = json.loads(body)
    data = payload.get("data")
    items = data.get("list") if isinstance(data, dict) and "list" in data else data
    if status != 200 or not isinstance(items, list) or not items:
        raise SystemExit(f"restored API smoke returned no default-tenant business rows for {path}: {payload}")
    if path.startswith("/api/v1/reports?"):
        report_items = items

report_id = report_items[0]["reportId"]
status, body, _ = request(f"/api/v1/reports/{report_id}", token=assessor_token)
report_detail = json.loads(body)
if status != 200 or report_detail.get("data", {}).get("reportId") != report_id:
    raise SystemExit(f"restored report detail smoke failed: {report_detail}")

status, body, _ = request("/api/v1/warnings?page=1&size=20", token=campus_assessor_token)
warning_payload = json.loads(body)
warning_data = warning_payload.get("data")
warning_items = warning_data.get("list") if isinstance(warning_data, dict) else warning_data
if status != 200 or not isinstance(warning_items, list) or not warning_items:
    raise SystemExit(f"restored API smoke returned no campus warning rows: {warning_payload}")

job_id = os.environ["PSY_RECOVERY_EXPORT_JOB_ID"]
status, artifact, _ = request(f"/api/v1/exports/reports/jobs/{job_id}/download", token=assessor_token)
actual_sha = hashlib.sha256(artifact).hexdigest()
expected_bytes = bytes.fromhex(os.environ["PSY_RECOVERY_EXPORT_HEX"])
expected_sha = hashlib.sha256(expected_bytes).hexdigest()
if status != 200 or actual_sha != expected_sha:
    raise SystemExit(f"restored export artifact digest mismatch: expected={expected_sha}, actual={actual_sha}")

print("restored application smoke verified: tenant-scoped login, tasks, reports, warnings, export artifact download")
PY
}

write_catalog_manifest() {
  local database_name=$1
  local output_path=$2
  : >"${output_path}"
  while IFS= read -r table_name; do
    local row_count
    row_count=$("${psql_base[@]}" -d "${database_name}" -Atc "select count(*) from public.\"${table_name}\"")
    printf 'table|%s|%s\n' "${table_name}" "${row_count}" >>"${output_path}"
  done < <("${psql_base[@]}" -d "${database_name}" -Atc "select tablename from pg_tables where schemaname='public' order by tablename")
  "${psql_base[@]}" -d "${database_name}" -Atc "
    select 'constraint|' || conrelid::regclass::text || '|' || conname || '|' || contype::text || '|' ||
           convalidated::text || '|' || condeferrable::text || '|' || condeferred::text || '|' ||
           coalesce(conkey::text, '') || '|' || coalesce(confrelid::regclass::text, '') || '|' || coalesce(confkey::text, '')
    from pg_constraint
    where connamespace = 'public'::regnamespace
    order by conrelid::regclass::text, conname;
    select 'index|' || count(*) || '|' || md5(string_agg(indexname || '|' || indexdef, E'\\n' order by indexname))
    from pg_indexes where schemaname='public';
    select 'sequence|' || sequencename || '|' || last_value
    from pg_sequences where schemaname='public' order by sequencename;
  " >>"${output_path}"
}

echo "Building immutable backend artifact..."
(
  cd "${PROJECT_ROOT}/backend"
  ./gradlew bootJar --no-daemon
)

"${createdb_base[@]}" --template=template0 "${SOURCE_DB}"
SOURCE_CREATED=true
"${createdb_base[@]}" --template=template0 "${RESTORE_DB}"
RESTORE_CREATED=true

echo "Migrating and seeding isolated source database ${SOURCE_DB}..."
start_backend "${SOURCE_DB}" always
smoke_business_apis "${SOURCE_DB}"
stop_backend

"${psql_base[@]}" -d "${SOURCE_DB}" -f "${PROJECT_ROOT}/scripts/sql/assert-backup-restore-core.sql"
write_catalog_manifest "${SOURCE_DB}" "${TMP_DIR}/source.catalog"

backup_started_ms=$(now_ms)
pg_dump \
  -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" \
  --format=custom --compress=6 --no-owner --no-acl \
  --file="${TMP_DIR}/psy-backup.dump" "${SOURCE_DB}"
backup_completed_ms=$(now_ms)

restore_started_ms=$(now_ms)
pg_restore \
  -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" \
  --dbname="${RESTORE_DB}" --exit-on-error --single-transaction --no-owner --no-acl \
  "${TMP_DIR}/psy-backup.dump"
restore_completed_ms=$(now_ms)

"${psql_base[@]}" -d "${RESTORE_DB}" -f "${PROJECT_ROOT}/scripts/sql/assert-backup-restore-core.sql"
write_catalog_manifest "${RESTORE_DB}" "${TMP_DIR}/restore.catalog"
diff -u "${TMP_DIR}/source.catalog" "${TMP_DIR}/restore.catalog"

pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" \
  --data-only --inserts --rows-per-insert=1000 --no-owner --no-acl --no-comments \
  "${SOURCE_DB}" >"${TMP_DIR}/source.data.raw.sql"
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" \
  --data-only --inserts --rows-per-insert=1000 --no-owner --no-acl --no-comments \
  "${RESTORE_DB}" >"${TMP_DIR}/restore.data.raw.sql"
sed -e '/^\\restrict /d' -e '/^\\unrestrict /d' "${TMP_DIR}/source.data.raw.sql" >"${TMP_DIR}/source.data.sql"
sed -e '/^\\restrict /d' -e '/^\\unrestrict /d' "${TMP_DIR}/restore.data.raw.sql" >"${TMP_DIR}/restore.data.sql"
diff -u "${TMP_DIR}/source.data.sql" "${TMP_DIR}/restore.data.sql"

recovery_started_ms=$(now_ms)
start_backend "${RESTORE_DB}" never
smoke_business_apis "${RESTORE_DB}"
recovery_completed_ms=$(now_ms)
stop_backend

backup_ms=$((backup_completed_ms - backup_started_ms))
restore_ms=$((restore_completed_ms - restore_started_ms))
rto_ms=$((recovery_completed_ms - recovery_started_ms + restore_ms))
dump_bytes=$(wc -c <"${TMP_DIR}/psy-backup.dump" | tr -d ' ')
dump_sha256=$(shasum -a 256 "${TMP_DIR}/psy-backup.dump" | awk '{print $1}')

printf '%s\n' \
  "Backup recovery rehearsal succeeded." \
  "source_database=${SOURCE_DB}" \
  "restore_database=${RESTORE_DB}" \
  "backup_format=PostgreSQL custom" \
  "backup_bytes=${dump_bytes}" \
  "backup_sha256=${dump_sha256}" \
  "backup_duration_ms=${backup_ms}" \
  "restore_duration_ms=${restore_ms}" \
  "measured_rpo_seconds=0 (quiesced-source snapshot boundary)" \
  "measured_rto_ms=${rto_ms} (restore start through authenticated application smoke)" \
  "external_object_storage=not_exercised" \
  "pitr=not_exercised"
