#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
E2E_SCHEMA="${PSY_E2E_SCHEMA:-psy_e2e_$(date +%s)_$$}"
E2E_DB_HOST="${PSY_E2E_DB_HOST:-localhost}"
E2E_DB_PORT="${PSY_E2E_DB_PORT:-5432}"
E2E_DB_NAME="${PSY_E2E_DB_NAME:-postgres}"
E2E_DB_USERNAME="${PSY_E2E_DB_USERNAME:-$(id -un)}"
E2E_DB_PASSWORD="${PSY_E2E_DB_PASSWORD:-}"
E2E_BACKEND_PORT="${PSY_E2E_BACKEND_PORT:-8090}"
E2E_WEB_PORT="${PSY_E2E_WEB_PORT:-5173}"
E2E_TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/psy-scale-package-e2e.XXXXXX")"
BACKEND_PID=""
WEB_PID=""

if [[ ! "${E2E_SCHEMA}" =~ ^psy_e2e_[A-Za-z0-9_]+$ ]]; then
  echo "Refusing unsafe E2E schema name: ${E2E_SCHEMA}" >&2
  exit 2
fi

export PGPASSWORD="${E2E_DB_PASSWORD}"

psql_cmd=(psql -h "${E2E_DB_HOST}" -p "${E2E_DB_PORT}" -U "${E2E_DB_USERNAME}" -d "${E2E_DB_NAME}" -v ON_ERROR_STOP=1)

cleanup() {
  local exit_code=$?
  set +e
  if [[ -n "${WEB_PID}" ]]; then
    kill "${WEB_PID}" 2>/dev/null
    wait "${WEB_PID}" 2>/dev/null
  fi
  if [[ -n "${BACKEND_PID}" ]]; then
    kill "${BACKEND_PID}" 2>/dev/null
    wait "${BACKEND_PID}" 2>/dev/null
  fi
  "${psql_cmd[@]}" -c "drop schema if exists \"${E2E_SCHEMA}\" cascade" >/dev/null 2>&1
  if [[ ${exit_code} -ne 0 ]]; then
    echo "Browser E2E failed. Backend log: ${E2E_TMP_DIR}/backend.log" >&2
    echo "Browser E2E failed. Web log: ${E2E_TMP_DIR}/web.log" >&2
    [[ -f "${E2E_TMP_DIR}/backend.log" ]] && tail -n 120 "${E2E_TMP_DIR}/backend.log" >&2
    [[ -f "${E2E_TMP_DIR}/web.log" ]] && tail -n 120 "${E2E_TMP_DIR}/web.log" >&2
  else
    find "${E2E_TMP_DIR}" -type f -delete
    rmdir "${E2E_TMP_DIR}"
  fi
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

"${psql_cmd[@]}" -c "create schema \"${E2E_SCHEMA}\""

(
  cd "${PROJECT_ROOT}/backend"
  ./gradlew bootJar --no-daemon
)

PSY_DB_URL="jdbc:postgresql://${E2E_DB_HOST}:${E2E_DB_PORT}/${E2E_DB_NAME}?currentSchema=${E2E_SCHEMA}" \
PSY_DB_USERNAME="${E2E_DB_USERNAME}" \
PSY_DB_PASSWORD="${E2E_DB_PASSWORD}" \
PSY_FLYWAY_ENABLED=true \
PSY_SQL_INIT_MODE=never \
PSY_SCHEDULER_LOCK_ENABLED=false \
PSY_NOTIFICATION_DELIVERY_SCAN_DELAY_MS=250 \
PSY_TRACING_SAMPLING_PROBABILITY=1.0 \
FLYWAY_POSTGRESQL_TRANSACTIONAL_LOCK=false \
SERVER_PORT="${E2E_BACKEND_PORT}" \
java -jar "${PROJECT_ROOT}/backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar" \
  >"${E2E_TMP_DIR}/backend.log" 2>&1 &
BACKEND_PID=$!

for _ in $(seq 1 90); do
  if curl --silent --fail "http://127.0.0.1:${E2E_BACKEND_PORT}/auth/register/options" >/dev/null; then
    break
  fi
  if ! kill -0 "${BACKEND_PID}" 2>/dev/null; then
    echo "Backend exited before becoming ready." >&2
    exit 1
  fi
  sleep 1
done
curl --silent --fail "http://127.0.0.1:${E2E_BACKEND_PORT}/auth/register/options" >/dev/null

PGOPTIONS="-c search_path=${E2E_SCHEMA}" "${psql_cmd[@]}" -f "${PROJECT_ROOT}/admin-web/e2e/fixtures/seed.sql" >/dev/null

(
  cd "${PROJECT_ROOT}/admin-web"
  exec ./node_modules/.bin/vite --host 127.0.0.1 --port "${E2E_WEB_PORT}"
) >"${E2E_TMP_DIR}/web.log" 2>&1 &
WEB_PID=$!

for _ in $(seq 1 60); do
  if curl --silent --fail "http://127.0.0.1:${E2E_WEB_PORT}/login" >/dev/null; then
    break
  fi
  if ! kill -0 "${WEB_PID}" 2>/dev/null; then
    echo "Web server exited before becoming ready." >&2
    exit 1
  fi
  sleep 1
done
curl --silent --fail "http://127.0.0.1:${E2E_WEB_PORT}/login" >/dev/null

(
  cd "${PROJECT_ROOT}/admin-web"
  PSY_E2E_WEB_URL="http://127.0.0.1:${E2E_WEB_PORT}" \
  PSY_E2E_BACKEND_URL="http://127.0.0.1:${E2E_BACKEND_PORT}" \
  npm run test:e2e
)

PGOPTIONS="-c search_path=${E2E_SCHEMA}" \
  "${psql_cmd[@]}" -f "${PROJECT_ROOT}/admin-web/e2e/fixtures/assert-core-closure.sql"

PGOPTIONS="-c search_path=${E2E_SCHEMA}" \
  "${psql_cmd[@]}" -f "${PROJECT_ROOT}/admin-web/e2e/fixtures/assert-publication-closure.sql"

python3 - "${E2E_TMP_DIR}/backend.log" <<'PY'
import json
import pathlib
import sys

log_path = pathlib.Path(sys.argv[1])
json_records = []
for line_number, line in enumerate(log_path.read_text(encoding="utf-8").splitlines(), start=1):
    if not line.lstrip().startswith("{"):
        continue
    try:
        json_records.append(json.loads(line))
    except json.JSONDecodeError as error:
        raise SystemExit(f"invalid structured backend log at line {line_number}: {error}") from error

if not json_records:
    raise SystemExit("backend emitted no structured JSON log records")
if not any(record.get("service") == "psy-backend" for record in json_records):
    raise SystemExit("structured backend logs are missing the service field")
if not any(record.get("correlation_id") for record in json_records):
    raise SystemExit("structured backend logs are missing a request correlation_id")
linked_records = [
    record for record in json_records
    if record.get("correlation_id") == "e2e-observability-error"
]
if not linked_records:
    raise SystemExit("structured backend logs are missing the E2E correlation record")
if not any(record.get("trace_id") == "11111111111111111111111111111111" for record in linked_records):
    raise SystemExit("W3C traceparent was not propagated into the correlated backend log")
if not all(record.get("span_id") for record in linked_records):
    raise SystemExit("correlated backend trace records are missing span_id")
PY
