#!/usr/bin/env bash
set -euo pipefail

# Reproducible, disposable capacity baseline. This script never uses the
# application's existing lx/public schema and never changes PostgreSQL server
# configuration. It creates one psy_perf_* schema, measures 1x, expands the
# same schema to 10x, and drops it on exit.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)_$$"
PERF_SCHEMA="${PSY_PERF_SCHEMA:-psy_perf_${RUN_ID}}"
DB_HOST="${PSY_PERF_DB_HOST:-localhost}"
DB_PORT="${PSY_PERF_DB_PORT:-5432}"
DB_NAME="${PSY_PERF_DB_NAME:-postgres}"
DB_USERNAME="${PSY_PERF_DB_USERNAME:-$(id -un)}"
DB_PASSWORD="${PSY_PERF_DB_PASSWORD:-}"
BACKEND_PORT="${PSY_PERF_BACKEND_PORT:-8092}"
TARGET_1X="${PSY_PERF_TARGET_1X:-100}"
TARGET_10X="${PSY_PERF_TARGET_10X:-1000}"
LIVE_PER_PHASE="${PSY_PERF_LIVE_PER_PHASE:-20}"
REQUESTS="${PSY_PERF_REQUESTS:-15}"
WARMUPS="${PSY_PERF_WARMUPS:-2}"
OUTPUT_DIR="${PSY_PERF_OUTPUT_DIR:-${TMPDIR:-/tmp}/psy-performance-baseline.${RUN_ID}}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/psy-performance.XXXXXX")"
BACKEND_PID=""
SCHEMA_CREATED=false

if [[ ! "${PERF_SCHEMA}" =~ ^psy_perf_[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe performance schema name: ${PERF_SCHEMA}" >&2
  exit 2
fi
if [[ "${DB_NAME}" == "lx" && "${PERF_SCHEMA}" == "public" ]]; then
  echo "Refusing to use lx/public for performance measurements." >&2
  exit 2
fi
if [[ ! "${BACKEND_PORT}" =~ ^[0-9]+$ ]] || (( BACKEND_PORT < 1024 || BACKEND_PORT > 65535 )); then
  echo "Unsafe backend port: ${BACKEND_PORT}" >&2
  exit 2
fi
if [[ ! "${TARGET_1X}" =~ ^[0-9]+$ ]] || [[ ! "${TARGET_10X}" =~ ^[0-9]+$ ]] ||
   (( TARGET_1X < 1 || TARGET_10X <= TARGET_1X )); then
  echo "Performance targets must be positive and 10x must be greater than 1x." >&2
  exit 2
fi

for command_name in psql curl lsof python3 java; do
  command -v "${command_name}" >/dev/null || {
    echo "Required command not found: ${command_name}" >&2
    exit 2
  }
done
if lsof -nP -iTCP:"${BACKEND_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Backend port ${BACKEND_PORT} is already in use; refusing to reuse an unrelated process." >&2
  exit 2
fi

mkdir -p "${OUTPUT_DIR}"
export PGPASSWORD="${DB_PASSWORD}"
psql_base=(psql -X -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USERNAME}" -d "${DB_NAME}" -v ON_ERROR_STOP=1)

schema_psql() {
  PGOPTIONS="-c search_path=${PERF_SCHEMA}" "${psql_base[@]}" "$@"
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
  if [[ "${SCHEMA_CREATED}" == "true" ]]; then
    "${psql_base[@]}" -c "drop schema if exists \"${PERF_SCHEMA}\" cascade" >/dev/null 2>&1
  fi
  if [[ -f "${TMP_DIR}/backend.log" ]]; then
    cp "${TMP_DIR}/backend.log" "${OUTPUT_DIR}/backend.log" >/dev/null 2>&1 || true
  fi
  if [[ ${exit_code} -ne 0 ]]; then
    echo "Performance baseline failed; artifacts retained at ${OUTPUT_DIR}" >&2
    [[ -f "${TMP_DIR}/backend.log" ]] && tail -n 160 "${TMP_DIR}/backend.log" >&2
  else
    echo "Performance baseline artifacts: ${OUTPUT_DIR}"
  fi
  rm -rf "${TMP_DIR}"
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

echo "Building immutable backend artifact..."
(
  cd "${PROJECT_ROOT}/backend"
  ./gradlew bootJar --no-daemon
)

JAVA_BIN="${JAVA_HOME:-}/bin/java"
if [[ ! -x "${JAVA_BIN}" ]]; then
  JAVA_BIN="$(command -v java)"
fi

"${psql_base[@]}" -c "create schema \"${PERF_SCHEMA}\""
SCHEMA_CREATED=true

start_backend() {
  : >"${TMP_DIR}/backend.log"
  PSY_DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=${PERF_SCHEMA}" \
  PSY_DB_USERNAME="${DB_USERNAME}" \
  PSY_DB_PASSWORD="${DB_PASSWORD}" \
  PSY_FLYWAY_ENABLED=true \
  PSY_SQL_INIT_MODE=never \
  PSY_SCHEDULER_LOCK_ENABLED=false \
  PSY_EXPORT_PENDING_SCAN_DELAY_MS=3600000 \
  PSY_NOTIFICATION_DELIVERY_SCAN_DELAY_MS=3600000 \
  PSY_ASSESSMENT_DRAFT_CLEANUP_SCAN_DELAY_MS=3600000 \
  PSY_ASSESSMENT_TASK_OVERDUE_SCAN_DELAY_MS=3600000 \
  PSY_WARNING_ESCALATION_SCAN_DELAY_MS=3600000 \
  PSY_TRACING_ENABLED=false \
  SERVER_PORT="${BACKEND_PORT}" \
  "${JAVA_BIN}" -jar "${PROJECT_ROOT}/backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar" \
    >"${TMP_DIR}/backend.log" 2>&1 &
  BACKEND_PID=$!
  for _ in $(seq 1 120); do
    if curl --silent --fail "http://127.0.0.1:${BACKEND_PORT}/auth/register/options" >/dev/null; then
      return
    fi
    if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
      echo "Backend exited before becoming ready." >&2
      return 1
    fi
    sleep 1
  done
  echo "Backend readiness timed out." >&2
  return 1
}

start_backend

echo "Applying isolated Flyway schema ${PERF_SCHEMA} and technical fixture..."
PGOPTIONS="-c search_path=${PERF_SCHEMA}" "${psql_base[@]}" \
  -f "${PROJECT_ROOT}/admin-web/e2e/fixtures/seed.sql" >/dev/null

apply_fixture() {
  local start_no=$1
  local target_no=$2
  local live_start_no=$3
  local live_end_no=$4
  schema_psql \
    -v start_no="${start_no}" \
    -v target_no="${target_no}" \
    -v live_start_no="${live_start_no}" \
    -v live_end_no="${live_end_no}" \
    -f "${PROJECT_ROOT}/scripts/sql/performance-fixture.sql" >/dev/null
}

query_value() {
  schema_psql -Atc "$1" | tr -d '\r' | head -n 1
}

TENANT_ID="$(query_value "select id from sys_tenant where tenant_code = 'DEFAULT'")"
SCALE_ID="$(query_value "select id from psy_scale where scale_code = 'E2E_CORE_TECH_FIXTURE' and version_no = 'v1'")"
RESPONDENT_ID="$(query_value "select id from sys_user where username = 'respondent'")"
if [[ -z "${TENANT_ID}" || -z "${SCALE_ID}" || -z "${RESPONDENT_ID}" ]]; then
  echo "Technical fixture did not create the expected tenant, scale, or respondent." >&2
  exit 1
fi

collect_db_resources() {
  local factor=$1
  schema_psql -Atc "
    select json_build_object(
      'factor', '${factor}',
      'database', current_database(),
      'schema', current_schema(),
      'xactCommit', coalesce((select xact_commit from pg_stat_database where datname=current_database()), 0),
      'blocksRead', coalesce((select blks_read from pg_stat_database where datname=current_database()), 0),
      'blocksHit', coalesce((select blks_hit from pg_stat_database where datname=current_database()), 0),
      'tempBytes', coalesce((select temp_bytes from pg_stat_database where datname=current_database()), 0),
      'activeBackends', coalesce((select numbackends from pg_stat_database where datname=current_database()), 0),
      'lockCount', (select count(*) from pg_locks where database = (select oid from pg_database where datname=current_database()))
    )::text
  " >"${OUTPUT_DIR}/db-resources-${factor}.json"
}

collect_actuator_metrics() {
  local factor=$1
  local metrics_dir="${OUTPUT_DIR}/actuator-${factor}"
  mkdir -p "${metrics_dir}"
  python3 "${PROJECT_ROOT}/scripts/performance/collect_actuator.py" \
    --base-url "http://127.0.0.1:${BACKEND_PORT}" \
    --output "${metrics_dir}"
}

collect_explain() {
  local factor=$1
  local target_rows=$2
  local deep_offset=$(( (target_rows - 20) > 0 ? target_rows - 20 : 0 ))
  local explain_dir="${OUTPUT_DIR}/explain-${factor}"
  mkdir -p "${explain_dir}"
  PGOPTIONS="-c search_path=${PERF_SCHEMA}" "${psql_base[@]}" -Atc "
    explain (analyze, buffers, format json)
    select t.id, t.task_name, t.scale_id, s.scale_name, t.status
    from psy_assessment_task t
    join psy_scale s on s.id = t.scale_id
    where t.tenant_id = ${TENANT_ID}
    order by t.id desc
    limit 20 offset ${deep_offset}
  " >"${explain_dir}/task-list.json"
  PGOPTIONS="-c search_path=${PERF_SCHEMA}" "${psql_base[@]}" -Atc "
    explain (analyze, buffers, format json)
    select r.id, r.result_id, sh.user_id, sh.task_id, t.task_name,
           s.scale_name, ar.total_score, ar.risk_level, r.created_at
    from psy_report r
    join psy_assessment_result ar on ar.id = r.result_id
    join psy_assessment_answer_sheet sh on sh.id = ar.answer_sheet_id
    join psy_assessment_task t on t.id = sh.task_id
    join psy_scale s on s.id = sh.scale_id
    where sh.tenant_id = ${TENANT_ID}
    order by r.created_at desc, r.id desc
    limit 20 offset ${deep_offset}
  " >"${explain_dir}/report-list.json"
  PGOPTIONS="-c search_path=${PERF_SCHEMA}" "${psql_base[@]}" -Atc "
    explain (analyze, buffers, format json)
    select id, result_id, warning_level, warning_priority, status, deadline_time, created_at
    from psy_warning_record
    where tenant_id = ${TENANT_ID}
    order by id desc
    limit 20 offset ${deep_offset}
  " >"${explain_dir}/warning-list.json"
}

pgss_state="$("${psql_base[@]}" -Atc "
  select json_build_object(
    'extensionInstalled', exists(select 1 from pg_extension where extname='pg_stat_statements'),
    'sharedPreloadLibraries', (select setting from pg_settings where name='shared_preload_libraries'),
    'trackIoTiming', (select setting from pg_settings where name='track_io_timing')
  )::text
")"
printf '%s\n' "${pgss_state}" >"${OUTPUT_DIR}/pg-stat-statements.json"

measure_factor() {
  local factor=$1
  local target_rows=$2
  local live_start_no=$3
  local live_end_no=$4
  local phase_file="${OUTPUT_DIR}/http-${factor}.json"
  local questions_json live_task_ids deep_page

  apply_fixture "$((factor == 1 ? 1 : TARGET_1X + 1))" "${target_rows}" "${live_start_no}" "${live_end_no}"
  questions_json="$(query_value "
    select json_agg(json_build_object(
      'questionId', question.id,
      'optionId', option.id,
      'optionLabel', option.option_label,
      'scoreValue', option.score_value
    ) order by question.question_no)::text
    from psy_scale_question question
    join lateral (
      select candidate.id, candidate.option_label, candidate.score_value
      from psy_scale_option candidate
      where candidate.question_id = question.id
      order by candidate.sort_no, candidate.id
      limit 1
    ) option on true
    where question.scale_id = ${SCALE_ID}
  ")"
  live_task_ids="$(query_value "
    select coalesce(string_agg(task.id::text, ',' order by task.id), '')
    from psy_assessment_task task
    where task.task_name like 'PERF-LIVE-TASK-%'
      and task.tenant_id = ${TENANT_ID}
      and not exists (
        select 1 from psy_assessment_answer_sheet answer
        where answer.task_id = task.id
          and answer.user_id = ${RESPONDENT_ID}
          and answer.answer_status = 'SUBMITTED'
      )
  ")"
  deep_page=$(( (target_rows + 19) / 20 ))
  python3 "${PROJECT_ROOT}/scripts/performance/measure_http.py" \
    --base-url "http://127.0.0.1:${BACKEND_PORT}" \
    --output "${phase_file}" \
    --factor "${factor}x" \
    --target-rows "${target_rows}" \
    --deep-page "${deep_page}" \
    --respondent-id "${RESPONDENT_ID}" \
    --scale-id "${SCALE_ID}" \
    --questions-json "${questions_json}" \
    --live-task-ids "${live_task_ids}" \
    --requests "${REQUESTS}" \
    --warmups "${WARMUPS}" \
    >"${OUTPUT_DIR}/http-${factor}.stdout.json"
  collect_db_resources "${factor}x"
  collect_actuator_metrics "${factor}x"
  collect_explain "${factor}x" "${target_rows}"
  python3 - "${phase_file}" <<'PY'
import json
import sys
from pathlib import Path

payload = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
errors = [
    (name, case.get("errorCount", 0))
    for name, case in payload.get("cases", {}).items()
    if case.get("errorCount", 0) > 0
]
if errors:
    raise SystemExit(f"performance HTTP case errors: {errors}")
PY
}

echo "Measuring 1x fixture (${TARGET_1X} target tasks)..."
measure_factor 1 "${TARGET_1X}" 1 "${LIVE_PER_PHASE}"

echo "Expanding the same schema to 10x (${TARGET_10X} target tasks)..."
measure_factor 10 "${TARGET_10X}" "$((LIVE_PER_PHASE + 1))" "$((LIVE_PER_PHASE * 2))"

python3 - "${OUTPUT_DIR}" "${PERF_SCHEMA}" "${DB_NAME}" "${TARGET_1X}" "${TARGET_10X}" <<'PY'
import json
import sys
from pathlib import Path

output_dir = Path(sys.argv[1])
schema = sys.argv[2]
database = sys.argv[3]
target_1x = sys.argv[4]
target_10x = sys.argv[5]

phases = [
    json.loads((output_dir / f"http-{factor}.json").read_text(encoding="utf-8"))
    for factor in (1, 10)
]
rows = []
for phase in phases:
    for name, case in phase["cases"].items():
        rows.append(
            f"| {phase['factor']} | {name} | {case.get('sampleCount')} | "
            f"{case.get('p50Ms')} | {case.get('p95Ms')} | {case.get('p99Ms')} | "
            f"{case.get('throughputPerSecond')} | {case.get('errorCount')} |"
        )

summary = f"""# Performance and capacity baseline

- Generated by `scripts/run-performance-baseline.sh`.
- PostgreSQL database: `{database}`, disposable schema: `{schema}` (dropped after the run).
- Target fixture sizes: 1x = `{target_1x}` completed tasks, 10x = `{target_10x}` completed tasks, plus live save/submit tasks.
- The same backend bootJar and the same HTTP Cases were used for both phases.
- Latencies are serial client-observed measurements; they are not a production SLO or capacity claim.
- Android was intentionally out of scope for this run.

## HTTP Case measurements

| Factor | Case | Samples | p50 ms | p95 ms | p99 ms | Serial throughput/s | Errors |
|---|---|---:|---:|---:|---:|---:|---:|
{chr(10).join(rows)}

## PostgreSQL evidence

- `EXPLAIN (ANALYZE, BUFFERS)` JSON is in `explain-1x/` and `explain-10x/`.
- Database resource snapshots are in `db-resources-1x.json` and `db-resources-10x.json`.
- Actuator Hikari/JVM snapshots are in `actuator-1x/` and `actuator-10x/`.
- `pg-stat-statements.json` records extension/preload availability. The script does not change PostgreSQL server configuration.

## Interpretation boundary

These measurements establish a repeatable local comparison and expose query plans. They do not establish production capacity, concurrency throughput, JVM sizing, or an SLO. A production-like run still requires isolated capacity hardware, representative data volume, concurrent load, `pg_stat_statements` enabled at server startup, and an approved performance test window.
"""
(output_dir / "README.md").write_text(summary, encoding="utf-8")
print(summary)
PY

echo "Performance baseline completed successfully."
