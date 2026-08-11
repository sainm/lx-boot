#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_ID="$(date +%Y%m%d%H%M%S)_$$"
RECOVERY_SCHEMA="${PSY_NOTIFICATION_RECOVERY_SCHEMA:-psy_notification_recovery_${RUN_ID}}"
DB_HOST="${PSY_NOTIFICATION_RECOVERY_DB_HOST:-localhost}"
DB_PORT="${PSY_NOTIFICATION_RECOVERY_DB_PORT:-5432}"
DB_NAME="${PSY_NOTIFICATION_RECOVERY_DB_NAME:-postgres}"
DB_USERNAME="${PSY_NOTIFICATION_RECOVERY_DB_USERNAME:-$(id -un)}"
DB_PASSWORD="${PSY_NOTIFICATION_RECOVERY_DB_PASSWORD:-}"
BACKEND_PORT="${PSY_NOTIFICATION_RECOVERY_BACKEND_PORT:-8093}"
GATEWAY_PORT="${PSY_NOTIFICATION_RECOVERY_GATEWAY_PORT:-18093}"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/psy-notification-worker-recovery.XXXXXX")"
TMP_DIR="$(cd "${TMP_DIR}" && pwd -P)"
POST_MARKER="${TMP_DIR}/post-received"
BACKEND_PID=""
GATEWAY_PID=""
DELIVERY_ID=""
SCHEMA_CREATED=false

if [[ ! "${RECOVERY_SCHEMA}" =~ ^psy_notification_recovery_[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe recovery schema name: ${RECOVERY_SCHEMA}" >&2
  exit 2
fi
for port_value in "${BACKEND_PORT}" "${GATEWAY_PORT}"; do
  if [[ ! "${port_value}" =~ ^[0-9]+$ ]] || (( port_value < 1024 || port_value > 65535 )); then
    echo "Unsafe rehearsal port: ${port_value}" >&2
    exit 2
  fi
done
for command_name in psql curl lsof python3 java; do
  command -v "${command_name}" >/dev/null || { echo "Required command not found: ${command_name}" >&2; exit 2; }
done
for port_value in "${BACKEND_PORT}" "${GATEWAY_PORT}"; do
  if lsof -nP -iTCP:"${port_value}" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port ${port_value} is already in use; refusing to reuse an unrelated process." >&2
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

stop_gateway() {
  if [[ -n "${GATEWAY_PID}" ]]; then
    kill "${GATEWAY_PID}" 2>/dev/null || true
    wait "${GATEWAY_PID}" 2>/dev/null || true
    GATEWAY_PID=""
  fi
}

cleanup() {
  local exit_code=$?
  set +e
  stop_backend
  stop_gateway
  if [[ "${SCHEMA_CREATED}" == "true" ]]; then
    "${psql_cmd[@]}" -c "drop schema if exists \"${RECOVERY_SCHEMA}\" cascade" >/dev/null 2>&1
  fi
  if [[ ${exit_code} -eq 0 ]]; then
    find "${TMP_DIR}" -type f -delete
    find "${TMP_DIR}" -depth -type d -empty -delete
  else
    echo "Notification worker recovery rehearsal failed. Evidence retained at ${TMP_DIR}" >&2
    [[ -f "${TMP_DIR}/backend-before-crash.log" ]] && tail -n 100 "${TMP_DIR}/backend-before-crash.log" >&2
    [[ -f "${TMP_DIR}/backend-after-restart.log" ]] && tail -n 100 "${TMP_DIR}/backend-after-restart.log" >&2
  fi
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

start_backend() {
  local log_path=$1
  local sql_init_mode=$2
  local http_push_enabled=$3
  local processing_timeout_seconds=$4

  PSY_DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}?currentSchema=${RECOVERY_SCHEMA}" \
  PSY_DB_USERNAME="${DB_USERNAME}" \
  PSY_DB_PASSWORD="${DB_PASSWORD}" \
  PSY_FLYWAY_ENABLED=true \
  PSY_SQL_INIT_MODE="${sql_init_mode}" \
  PSY_SCHEDULER_LOCK_ENABLED=false \
  PSY_NOTIFICATION_DELIVERY_SCAN_DELAY_MS=250 \
  PSY_NOTIFICATION_DELIVERY_BATCH_SIZE=5 \
  PSY_NOTIFICATION_PROCESSING_TIMEOUT_SECONDS="${processing_timeout_seconds}" \
  PSY_NOTIFICATION_MAX_ATTEMPTS=3 \
  PSY_NOTIFICATION_INITIAL_RETRY_DELAY_SECONDS=1 \
  PSY_NOTIFICATION_MAX_RETRY_DELAY_SECONDS=2 \
  PSY_NOTIFICATION_PUSH_HTTP_ENABLED="${http_push_enabled}" \
  PSY_NOTIFICATION_PUSH_HTTP_ENDPOINT_URL="http://127.0.0.1:${GATEWAY_PORT}/push" \
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

delivery_value() {
  local expression=$1
  PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -Atc \
    "select ${expression} from psy_notification_delivery where id = ${DELIVERY_ID}"
}

wait_for_status() {
  local expected_status=$1
  for _ in $(seq 1 90); do
    if [[ "$(delivery_value delivery_status)" == "${expected_status}" ]]; then
      return
    fi
    sleep 1
  done
  echo "Delivery ${DELIVERY_ID} did not reach ${expected_status}; current=$(delivery_value delivery_status)" >&2
  exit 1
}

echo "Building backend artifact for notification crash/restart rehearsal..."
(
  cd "${PROJECT_ROOT}/backend"
  ./gradlew bootJar --no-daemon
)

"${psql_cmd[@]}" -c "create schema \"${RECOVERY_SCHEMA}\""
SCHEMA_CREATED=true
python3 "${PROJECT_ROOT}/scripts/fixtures/hanging_object_storage.py" \
  --port "${GATEWAY_PORT}" --marker "${POST_MARKER}" >"${TMP_DIR}/gateway.log" 2>&1 &
GATEWAY_PID=$!

start_backend "${TMP_DIR}/backend-before-crash.log" always true 300

DELIVERY_ID="$(PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -qAtc "
  insert into psy_notification_delivery (
    tenant_id, notification_id, receiver_user_id, device_id, push_token_snapshot,
    delivery_channel, delivery_status, created_at, updated_at
  )
  select receiver.tenant_id, notification.id, receiver.id, device.id, device.push_token,
         'PUSH', 'PENDING', current_timestamp, current_timestamp
  from psy_notification notification
  join psy_notification_delivery in_app on in_app.notification_id = notification.id
  join sys_user receiver on receiver.id = in_app.receiver_user_id
  join psy_user_device device on device.user_id = receiver.id and device.active_flag
  where in_app.delivery_channel = 'IN_APP' and device.push_token is not null
  order by notification.id, device.id
  limit 1
  returning id;
")"
if [[ ! "${DELIVERY_ID}" =~ ^[0-9]+$ ]]; then
  echo "Failed to create an isolated push delivery." >&2
  exit 1
fi

for _ in $(seq 1 60); do
  if [[ -f "${POST_MARKER}" ]] && [[ "$(delivery_value delivery_status)" == "PROCESSING" ]]; then
    break
  fi
  sleep 1
done
if [[ ! -f "${POST_MARKER}" ]] || [[ "$(delivery_value delivery_status)" != "PROCESSING" ]]; then
  echo "Notification Worker did not block in the HTTP push gateway." >&2
  exit 1
fi
first_token="$(delivery_value processing_token)"
if [[ -z "${first_token}" ]]; then
  echo "PROCESSING delivery has no lease token." >&2
  exit 1
fi

crashed_pid="${BACKEND_PID}"
kill -9 "${crashed_pid}"
wait "${crashed_pid}" 2>/dev/null || true
BACKEND_PID=""
stop_gateway
echo "Killed notification worker process ${crashed_pid} while push POST was blocked."

sleep 3
start_backend "${TMP_DIR}/backend-after-restart.log" never false 2
wait_for_status SENT

retry_count="$(delivery_value retry_count)"
processing_token="$(delivery_value "coalesce(processing_token, '')")"
row_count="$(PGOPTIONS="-c search_path=${RECOVERY_SCHEMA}" "${psql_cmd[@]}" -Atc "select count(*) from psy_notification_delivery where id = ${DELIVERY_ID}")"
if [[ "${retry_count}" != "1" || -n "${processing_token}" || "${row_count}" != "1" ]]; then
  echo "Recovered delivery invariant failed: retry=${retry_count} token=${processing_token} rows=${row_count}." >&2
  exit 1
fi

echo "Notification worker crash/restart rehearsal passed."
echo "schema=${RECOVERY_SCHEMA} delivery=${DELIVERY_ID} final_status=SENT retry_count=${retry_count} rows=${row_count}"
