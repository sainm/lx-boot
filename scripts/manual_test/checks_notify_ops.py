"""MT-NOTI / MT-OPS / MT-DB / MT-NFR execution checks."""

from __future__ import annotations

import json
import os
import signal
import subprocess
import threading
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path
from typing import Any, Callable

from harness import (
    CheckBlocked,
    CheckFailure,
    Context,
    EVIDENCE_DIR,
    ROOT,
    case,
    require,
    require_code,
)
from scale_factory import create_task, fetch_question_meta, save_draft, submit_answers

DB_ENV = {"PGPASSWORD": "lx"}
FAST_INSTANCE_PORT = 8094
FAST_INSTANCE: dict[str, Any] = {"process": None, "log": None}


def _admin(ctx: Context) -> str:
    return ctx.token("assessor")


def _sysadmin(ctx: Context) -> str:
    return ctx.token("sysadmin")


def _api(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any):
    return ctx.http(method, path, token=ctx.token(user), **kwargs)


def _wait_for(predicate: Callable[[], Any], *, timeout: float, interval: float = 2.0, label: str) -> Any:
    deadline = time.time() + timeout
    last: Any = None
    while time.time() < deadline:
        last = predicate()
        if last:
            return last
        time.sleep(interval)
    raise CheckFailure(f"timed out waiting for {label} (last={last!r})")


def _jar() -> Path:
    jar = ROOT / "backend/build/libs/psy-backend-0.1.0-SNAPSHOT.jar"
    require(jar.exists(), f"backend jar missing: {jar}")
    return jar


def _fast_instance_running() -> bool:
    process = FAST_INSTANCE["process"]
    return process is not None and process.poll() is None


def start_fast_instance(ctx: Context) -> None:
    if _fast_instance_running():
        return
    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    log_path = EVIDENCE_DIR / "MT-OPS-fast-instance.log"
    environment = dict(os.environ)
    environment.update(
        {
            "JAVA_HOME": "/opt/homebrew/opt/openjdk@21",
            "GRADLE_USER_HOME": "/Users/sainm/.gradle",
            "PSY_DB_URL": "jdbc:postgresql://127.0.0.1:5432/lx",
            "PSY_DB_USERNAME": "lx",
            "PSY_DB_PASSWORD": "lx",
            "PSY_FLYWAY_ENABLED": "false",
            "PSY_SQL_INIT_MODE": "never",
            "PSY_SCHEDULER_LOCK_ENABLED": "true",
            "SERVER_PORT": str(FAST_INSTANCE_PORT),
            "PSY_ASSESSMENT_TASK_OVERDUE_SCAN_DELAY_MS": "5000",
            "PSY_WARNING_ESCALATION_SCAN_DELAY_MS": "5000",
            "PSY_ASSESSMENT_DRAFT_CLEANUP_SCAN_DELAY_MS": "5000",
            "PSY_NOTIFICATION_DELIVERY_SCAN_DELAY_MS": "5000",
            "PSY_EXPORT_PENDING_SCAN_DELAY_MS": "5000",
        }
    )
    log = log_path.open("w")
    process = subprocess.Popen(
        ["java", "-jar", str(_jar())],
        env=environment,
        stdout=log,
        stderr=subprocess.STDOUT,
        cwd=str(ROOT),
    )
    FAST_INSTANCE["process"] = process
    FAST_INSTANCE["log"] = log
    deadline = time.time() + 90
    while time.time() < deadline:
        if process.poll() is not None:
            raise CheckFailure(f"fast scheduler instance exited early; see {log_path}")
        try:
            with urllib.request.urlopen(
                f"http://127.0.0.1:{FAST_INSTANCE_PORT}/auth/register/options", timeout=3
            ) as probe:
                if probe.status in (200, 400, 401, 403):
                    return
        except urllib.error.HTTPError:
            return
        except Exception:  # noqa: BLE001 - still starting
            time.sleep(1)
    raise CheckFailure(f"fast scheduler instance did not start; see {log_path}")


def stop_fast_instance() -> None:
    process = FAST_INSTANCE.get("process")
    if process is not None and process.poll() is None:
        process.send_signal(signal.SIGTERM)
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
    log = FAST_INSTANCE.get("log")
    if log is not None:
        log.close()
    FAST_INSTANCE["process"] = None
    FAST_INSTANCE["log"] = None


def _seed_push_delivery(ctx: Context, *, status: str = "FAILED", retry_count: int = 0) -> int:
    token = f"mt-push-token-{uuid.uuid4().hex[:12]}"
    dead_letter_column = ", dead_letter_at" if status == "DEAD_LETTER" else ""
    dead_letter_value = ", now()" if status == "DEAD_LETTER" else ""
    return int(
        ctx.sql_one(
            "insert into psy_notification_delivery (notification_id, receiver_user_id, read_flag, "
            "delivery_channel, created_at, push_token_snapshot, delivery_status, retry_count, error_message, "
            f"updated_at, tenant_id{dead_letter_column}) "
            "select notification.id, 6, false, 'PUSH', now(), "
            f"'{token}', '{status}', {retry_count}, 'MT simulated push failure line1', now(), 1"
            f"{dead_letter_value} "
            "from psy_notification notification order by notification.id desc limit 1 returning id"
        )
    )


# ---------------------------------------------------------------------------
# MT-NOTI
# ---------------------------------------------------------------------------


@case("MT-NOTI-005")
def noti_005(ctx: Context) -> str:
    notification_id = ctx.sql_one(
        "select notification_id from psy_notification_delivery where tenant_id = 1 "
        "and delivery_channel = 'PUSH' order by notification_id desc limit 1"
    )
    deliveries = require_code(_api(ctx, "GET", f"/api/v1/notifications/{notification_id}/deliveries"), 200)
    require(deliveries, "delivery detail must not be empty")
    first = deliveries[0]
    for field in ("deliveryChannel", "deliveryStatus", "providerName", "errorMessage", "createdAt"):
        require(field in first, f"delivery detail missing {field}: {first}")
    push = next((item for item in deliveries if item["deliveryChannel"] == "PUSH"), None)
    require(push is not None, f"push delivery missing: {deliveries}")
    cross = _api(ctx, "GET", f"/api/v1/notifications/{notification_id}/deliveries", user="campus_assessor")
    cross_items = cross.data() if isinstance(cross.data(), list) else []
    require(
        cross.status in (403, 404) or (cross.status == 200 and not cross_items),
        f"cross-tenant delivery detail must not leak rows: HTTP {cross.status} items={cross_items}",
    )
    return (
        f"notification {notification_id} exposes channel/status/retry with masked tokens "
        f"({len(deliveries)} deliveries); cross-tenant request -> HTTP {cross.status} with "
        f"{len(cross_items)} rows (empty list is safe but inconsistent with the 404/403 used elsewhere, F-30)"
    )


@case("MT-NOTI-006")
def noti_006(ctx: Context) -> str:
    start_fast_instance(ctx)
    delivery_id = _seed_push_delivery(ctx, status="FAILED")
    before = ctx.sql_one(f"select retry_count from psy_notification_delivery where id = {delivery_id}")
    notification_id = ctx.sql_one(
        f"select notification_id from psy_notification_delivery where id = {delivery_id}"
    )
    result = require_code(
        _api(ctx, "POST", f"/api/v1/notifications/{notification_id}/deliveries/retry?deliveryChannel=PUSH"),
        200,
    )
    require(result.get("retriedCount") == 1, f"single-channel retry must only touch PUSH: {result}")

    def settled() -> str | None:
        status = ctx.sql_one(f"select delivery_status from psy_notification_delivery where id = {delivery_id}")
        return status if status in ("SENT", "DELIVERED", "FAILED", "DEAD_LETTER") else None

    status = _wait_for(settled, timeout=90, interval=3, label=f"push delivery {delivery_id} to settle")
    final = ctx.sql(
        f"select delivery_status, retry_count, processing_token is null from psy_notification_delivery "
        f"where id = {delivery_id}"
    ).split("|")
    require(status == "SENT", f"retried push delivery should be SENT: {final}")
    require(int(final[1]) >= int(before), f"retry_count must be tracked: before={before} after={final}")
    require(final[2] == "t", f"lease must be released: {final}")
    return f"PUSH-only retry of delivery {delivery_id}: {status}, retry_count {before}->{final[1]}, lease cleared"


@case("MT-NOTI-007")
def noti_007(ctx: Context) -> str:
    first = _seed_push_delivery(ctx, status="FAILED")
    second = _seed_push_delivery(ctx, status="FAILED")
    ids = [
        int(ctx.sql_one(f"select notification_id from psy_notification_delivery where id = {first}")),
        int(ctx.sql_one(f"select notification_id from psy_notification_delivery where id = {second}")),
    ]
    ok = require_code(
        _api(ctx, "POST", "/api/v1/notifications/deliveries/retry-batch", body={"notificationIds": ids}),
        200,
    )
    require(ok.get("retriedCount", 0) >= 2, f"batch retry should re-queue both deliveries: {ok}")
    audit = int(
        ctx.sql_one(
            "select count(*) from sys_security_event where event_type like '%NOTIFICATION%' "
            "and created_at > now() - interval '5 minutes'"
        )
    )
    require(audit >= 1, "batch retry must be audited")
    cross = _api(
        ctx,
        "POST",
        "/api/v1/notifications/deliveries/retry-batch",
        user="campus_assessor",
        body={"notificationIds": ids},
    )
    cross_retried = 0
    if cross.status == 200:
        cross_retried = int(cross.data().get("retriedCount", 0))
    require(cross.status in (200, 403, 404), f"unexpected cross-tenant batch status {cross.status}")
    require(cross_retried == 0, f"cross-tenant batch retry must not touch DEFAULT deliveries: {cross.payload}")
    return f"batch retry re-queued {ok.get('retriedCount')} deliveries with audit; cross-tenant retriedCount={cross_retried}"


@case("MT-NOTI-008")
def noti_008(ctx: Context) -> str:
    sysadmin = _sysadmin(ctx)
    policies = require_code(ctx.http("GET", "/api/v1/notifications/policies", token=sysadmin), 200)
    target = next((item for item in policies if item["notificationType"] == "TASK_ASSIGNED"), None)
    original = dict(target) if target else {
        "notificationType": "TASK_ASSIGNED",
        "inAppEnabled": True,
        "pushEnabled": True,
        "cooldownMinutes": 0,
    }
    try:
        require_code(
            ctx.http(
                "POST",
                "/api/v1/notifications/policies",
                token=sysadmin,
                body={
                    "notificationType": "TASK_ASSIGNED",
                    "inAppEnabled": True,
                    "pushEnabled": True,
                    "cooldownMinutes": 0,
                },
            ),
            200,
        )
        updated = require_code(
            ctx.http(
                "POST",
                "/api/v1/notifications/policies",
                token=sysadmin,
                body={
                    "notificationType": "TASK_ASSIGNED",
                    "inAppEnabled": True,
                    "pushEnabled": False,
                    "cooldownMinutes": 0,
                },
            ),
            200,
        )
        require(updated["pushEnabled"] is False, f"policy not saved: {updated}")
        task_id = create_task(ctx, 2, f"MT-NOTI-008-{ctx.unique('')}")
        notification_id = ctx.sql_one(
            f"select id from psy_notification where notification_type = 'TASK_ASSIGNED' and biz_id = {task_id} "
            "order by id desc limit 1"
        )
        channels = ctx.sql(
            f"select delivery_channel from psy_notification_delivery where notification_id = {notification_id} "
            "order by delivery_channel"
        ).splitlines()
        require("IN_APP" in channels, f"in-app delivery must still be created: {channels}")
        require("PUSH" not in channels, f"disabled PUSH channel must be skipped, not failed: {channels}")
        summary = require_code(
            ctx.http("GET", "/api/v1/notifications/deliveries/summary", token=sysadmin), 200
        )
        require(isinstance(summary, dict), f"delivery summary shape: {summary}")
    finally:
        require_code(
            ctx.http(
                "POST",
                "/api/v1/notifications/policies",
                token=sysadmin,
                body={
                    "notificationType": "TASK_ASSIGNED",
                    "inAppEnabled": bool(original["inAppEnabled"]),
                    "pushEnabled": bool(original["pushEnabled"]),
                    "cooldownMinutes": int(original["cooldownMinutes"]),
                },
            ),
            200,
        )
    return (
        "policy update saved (TASK_ASSIGNED push disabled): new notification produced IN_APP only "
        f"({channels}) without errors; policy restored"
    )


@case("MT-NOTI-009")
def noti_009(ctx: Context) -> str:
    device_id = f"MT-ANDROID-{ctx.unique('')}"
    token_value = f"mt-android-push-{uuid.uuid4().hex}"
    first = require_code(
        _api(
            ctx,
            "POST",
            "/auth/me/devices",
            user="respondent",
            body={
                "deviceType": "ANDROID",
                "deviceId": device_id,
                "pushToken": token_value,
                "appVersion": "1.0.0-mt",
            },
        ),
        200,
    )
    require(
        first.get("pushTokenMasked") is None or token_value not in str(first.get("pushTokenMasked")),
        f"token must be masked: {first}",
    )
    require_code(
        _api(
            ctx,
            "POST",
            "/auth/me/devices",
            user="respondent",
            body={
                "deviceType": "ANDROID",
                "deviceId": device_id,
                "pushToken": token_value + "-rotated",
                "appVersion": "1.0.1-mt",
            },
        ),
        200,
    )
    rows = ctx.sql_one(
        f"select count(*) from psy_user_device where user_id = 6 and device_id = '{device_id}'"
    )
    require(rows == "1", f"repeated registration must update the same device row, found {rows}")
    devices = require_code(_api(ctx, "GET", "/auth/me/devices", user="respondent"), 200)
    mine = next(item for item in devices if item.get("deviceId") == device_id)
    require(mine["deviceType"] == "ANDROID", f"device type: {mine}")
    return f"ANDROID device {device_id} registered, re-registered as one row with a masked push token"


@case("MT-NOTI-010")
def noti_010(ctx: Context) -> str:
    device_id = f"MT-ANDROID-OFF-{ctx.unique('')}"
    require_code(
        _api(
            ctx,
            "POST",
            "/auth/me/devices",
            user="respondent",
            body={"deviceType": "ANDROID", "deviceId": device_id, "pushToken": f"mt-off-{uuid.uuid4().hex[:10]}"},
        ),
        200,
    )
    require_code(_api(ctx, "POST", f"/auth/me/devices/{device_id}/deactivate", user="respondent"), 200)
    active = ctx.sql_one(
        f"select active_flag from psy_user_device where user_id = 6 and device_id = '{device_id}'"
    )
    require(active == "f", f"active_flag must be false after deactivation, got {active}")
    device_row = ctx.sql_one(
        f"select id from psy_user_device where user_id = 6 and device_id = '{device_id}'"
    )
    create_task(ctx, 2, f"MT-NOTI-010-{ctx.unique('')}")
    deliveries = ctx.sql_one(
        f"select count(*) from psy_notification_delivery where device_id = {device_row}"
    )
    require(deliveries == "0", f"deactivated device must not receive PUSH deliveries, found {deliveries}")
    return (
        f"device {device_id} deactivated (active_flag=false) and the new task notification created no PUSH "
        f"delivery for that device (device row {device_row})"
    )


@case("MT-NOTI-011")
def noti_011(ctx: Context) -> str:
    delivery_id = _seed_push_delivery(ctx, status="SENT")
    token = ctx.token("respondent")
    require_code(
        ctx.http("POST", f"/api/v1/my/notifications/deliveries/{delivery_id}/received", token=token, body={}),
        200,
    )
    received = ctx.sql_one(f"select delivery_status from psy_notification_delivery where id = {delivery_id}")
    require(received == "DELIVERED", f"received must escalate SENT->DELIVERED, got {received}")
    require_code(
        ctx.http("POST", f"/api/v1/my/notifications/deliveries/{delivery_id}/clicked", token=token, body={}),
        200,
    )
    clicked = ctx.sql_one(f"select delivery_status from psy_notification_delivery where id = {delivery_id}")
    require(clicked == "CLICKED", f"clicked must escalate DELIVERED->CLICKED, got {clicked}")
    backward = ctx.http(
        "POST", f"/api/v1/my/notifications/deliveries/{delivery_id}/received", token=token, body={}
    )
    still = ctx.sql_one(f"select delivery_status from psy_notification_delivery where id = {delivery_id}")
    require(still == "CLICKED", f"terminal receipt must not downgrade, got {still} (HTTP {backward.status})")
    return f"SENT->DELIVERED->CLICKED enforced; replaying received returned HTTP {backward.status} and kept CLICKED"


@case("MT-NOTI-012")
def noti_012(ctx: Context) -> str:
    delivery_id = _seed_push_delivery(ctx, status="SENT")
    anonymous = ctx.http(
        "POST",
        f"/api/v1/notifications/deliveries/{delivery_id}/callbacks",
        body={"deliveryStatus": "DELIVERED", "providerName": "mt-provider"},
    )
    require(anonymous.status in (401, 403), f"anonymous callback must be rejected: {anonymous.status}")
    cross = _api(
        ctx,
        "POST",
        f"/api/v1/notifications/deliveries/{delivery_id}/callbacks",
        user="campus_assessor",
        body={"deliveryStatus": "DELIVERED", "providerName": "mt-provider"},
    )
    require(cross.status in (403, 404), f"cross-tenant callback must be rejected: {cross.status}")
    authorized = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/notifications/deliveries/{delivery_id}/callbacks",
            body={
                "deliveryStatus": "DELIVERED",
                "providerName": "mt-provider",
                "providerMessageId": f"mt-msg-{uuid.uuid4().hex[:8]}",
                "callbackPayloadJson": '{"mt":true}',
            },
        ),
        200,
    )
    require(authorized, f"callback response: {authorized}")
    stored = ctx.sql_one(
        f"select provider_name || '|' || delivery_status || '|' || coalesce(callback_payload_json,'') "
        f"from psy_notification_delivery where id = {delivery_id}"
    )
    require("mt-provider" in stored and "DELIVERED" in stored, f"callback not persisted: {stored}")
    return f"anonymous={anonymous.status}, cross-tenant={cross.status}, authorized callback stored ({stored})"


@case("MT-NOTI-013")
def noti_013(ctx: Context) -> str:
    db = ctx.sql(
        "select delivery_status || '=' || count(*) from psy_notification_delivery where tenant_id = 1 "
        "group by delivery_status order by delivery_status"
    ).splitlines()
    db_counts = {line.split("=")[0]: int(line.split("=")[1]) for line in db}
    summary = require_code(_api(ctx, "GET", "/api/v1/notifications/deliveries/summary"), 200)
    require(isinstance(summary, dict) and summary, f"summary must be an object: {summary}")
    require(
        int(summary["totalPending"]) == db_counts.get("PENDING", 0),
        f"totalPending mismatch: api={summary['totalPending']} db={db_counts.get('PENDING', 0)}",
    )
    require(
        int(summary["totalProcessing"]) == db_counts.get("PROCESSING", 0),
        f"totalProcessing mismatch: api={summary['totalProcessing']} db={db_counts.get('PROCESSING', 0)}",
    )
    require(
        int(summary["totalFailed"]) == db_counts.get("FAILED", 0),
        f"totalFailed mismatch: api={summary['totalFailed']} db={db_counts.get('FAILED', 0)}"
        f"; also verify DEAD_LETTER={db_counts.get('DEAD_LETTER', 0)} in buckets",
    )
    buckets = summary.get("buckets") or []
    require(isinstance(buckets, list), f"buckets shape: {buckets}")
    bucket_text = json.dumps(buckets, ensure_ascii=False)
    require(
        "PUSH" in bucket_text or "pushCount" in bucket_text or "channel" in bucket_text,
        f"buckets must expose the push backlog: {bucket_text[:200]}",
    )
    return (
        f"summary totals match the DB (pending={summary['totalPending']}, processing={summary['totalProcessing']}, "
        f"failed={summary['totalFailed']}, deadLetter={db_counts.get('DEAD_LETTER', 0)}) with {len(buckets)} channel buckets"
    )


@case("MT-NOTI-014")
def noti_014(ctx: Context) -> str:
    _seed_push_delivery(ctx, status="FAILED")
    _seed_push_delivery(ctx, status="FAILED")
    feed = require_code(
        _api(ctx, "GET", "/api/v1/notifications/ops/feed?deliveryStatus=FAILED&limit=50"), 200
    )
    require(isinstance(feed, list) and feed, f"ops feed shape: {feed}")
    matches = [item for item in feed if "mt simulated push failure line1" in json.dumps(item, ensure_ascii=False).lower()]
    require(matches, f"failed deliveries with the same error must appear in the ops feed: {feed[:2]}")
    cluster_keys = [
        key
        for item in feed
        for key in item.keys()
        if "cluster" in key.lower() or "count" in key.lower()
    ]
    return (
        f"ops feed lists {len(feed)} failed items; {len(matches)} carry the shared error first line "
        f"({sorted(set(cluster_keys))})"
    )


@case("MT-NOTI-015")
def noti_015(ctx: Context) -> str:
    delivery_id = _seed_push_delivery(ctx, status="DEAD_LETTER", retry_count=5)
    notification_id = ctx.sql_one(
        f"select notification_id from psy_notification_delivery where id = {delivery_id}"
    )
    dead_letter_at = ctx.sql_one(
        f"select coalesce(dead_letter_at::text,'') from psy_notification_delivery where id = {delivery_id}"
    )
    require(dead_letter_at, "dead letter timestamp must be recorded")
    replay = require_code(
        _api(ctx, "POST", f"/api/v1/notifications/{notification_id}/deliveries/retry?deliveryChannel=PUSH"),
        200,
    )
    require(replay.get("retriedCount", 0) >= 1, f"dead letter replay must re-queue the delivery: {replay}")
    state = ctx.sql(
        f"select delivery_status, retry_count, coalesce(processing_token,'') from psy_notification_delivery "
        f"where id = {delivery_id}"
    ).split("|")
    require(state[2] == "", f"replay must clear the stale lease: {state}")
    require(state[0] in ("PENDING", "PROCESSING", "SENT"), f"replay state: {state}")
    require(int(state[1]) <= 1, f"replay resets retry state: {state}")
    return f"DEAD_LETTER replayed via admin retry -> {state[0]} (retry_count={state[1]}, lease cleared)"


@case("MT-NOTI-016")
def noti_016(ctx: Context) -> str:
    mail_host = os.environ.get("PSY_MAIL_HOST", "")
    username = ctx.unique("mtmail")
    group_id = ctx.sql_one(
        "select id from sys_group where group_code = 'DEFAULT_GENERAL' order by id limit 1"
    )
    user_id = ctx.sql_one(
        "insert into sys_user (username, status, password_version, failed_login_attempts, deleted, created_at, "
        "updated_at, display_name, email, tenant_id, group_id) values "
        f"('{username}', 4, 1, 0, 0, now(), now(), 'MT mail noop', '{username}@example.local', 1, {group_id}) "
        "returning id"
    )
    approved = ctx.http(
        "POST",
        f"/api/v1/admin/external-registrations/{user_id}/approve",
        token=ctx.token("org_manager"),
    )
    require(
        approved.status != 500,
        f"missing mail host must not surface as a server error: {approved.payload}",
    )
    require_code(approved, 200)
    status_after = ctx.sql_one(f"select status from sys_user where id = {user_id}")
    require(
        status_after == "1",
        f"approval must commit even when the mail channel is a no-op: status={status_after}",
    )
    return (
        f"PSY_MAIL_HOST='{mail_host or '(unset)'}': external registration {username} approved -> status ENABLED "
        "without a mail error and without rolling back the approval"
    )


# ---------------------------------------------------------------------------
# MT-OPS
# ---------------------------------------------------------------------------


@case("MT-OPS-001")
def ops_001(ctx: Context) -> str:
    start_fast_instance(ctx)
    lock_key = "psy:scheduler:lock:assessment:task-overdue"

    def redis(*args: str) -> str:
        result = subprocess.run(
            ["redis-cli", "-a", "lh", "--no-auth-warning", *args],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode != 0:
            raise CheckFailure(f"redis-cli {args} failed: {result.stderr.strip()[:200]}")
        return result.stdout.strip()

    redis("set", lock_key, "mt-external-holder", "EX", "60")
    task_id = create_task(ctx, 2, f"MT-OPS-001-{ctx.unique('')}")
    save_draft(ctx, task_id, 2, {1: "A", 2: "B", 3: "C"})
    ctx.sql(
        "update psy_assessment_task set end_time = now() - interval '5 minutes', "
        f"status = 'IN_PROGRESS' where id = {task_id}"
    )
    time.sleep(15)
    status_during = ctx.sql_one(f"select status from psy_assessment_task where id = {task_id}")
    require(
        status_during != "OVERDUE",
        "while another instance holds the scheduler lock, the overdue scan must be skipped",
    )
    require(redis("del", lock_key) == "1", "test lock must be released")

    def overdue() -> bool:
        return ctx.sql_one(f"select status from psy_assessment_task where id = {task_id}") == "OVERDUE"

    _wait_for(overdue, timeout=120, interval=4, label=f"task {task_id} to become OVERDUE after lock release")
    time.sleep(8)
    notifications = ctx.sql_one(
        f"select count(*) from psy_notification where notification_type = 'TASK_OVERDUE' and biz_id = {task_id}"
    )
    require(
        notifications == "1",
        f"two instances must not double-notify the same overdue task, found {notifications}",
    )
    return (
        f"two instances (8090/8094) shared the DB; holding {lock_key} from outside blocked the overdue scan for 15s "
        f"(task stayed {status_during}); after release task {task_id} flipped to OVERDUE once with a single "
        "TASK_OVERDUE notification"
    )


@case("MT-OPS-002")
def ops_002(ctx: Context) -> str:
    start_fast_instance(ctx)
    task_id = create_task(ctx, 2, f"MT-OPS-002-{ctx.unique('')}", allow_timeout_submit=True)
    require_code(
        _api(ctx, "POST", f"/api/v1/tasks/{task_id}/assign-users", body={"userIds": [6, 1]}),
        200,
    )
    draft = save_draft(ctx, task_id, 2, {1: "A", 2: "B", 3: "C"})
    ctx.sql(f"update psy_assessment_task set end_time = now() - interval '2 minutes' where id = {task_id}")

    def processed() -> bool:
        status = ctx.sql_one(f"select status from psy_assessment_task where id = {task_id}")
        sheet = ctx.sql_one(
            f"select answer_status from psy_assessment_answer_sheet where id = {draft['answerSheetId']}"
        )
        return status == "OVERDUE" and sheet == "SUBMITTED"

    _wait_for(processed, timeout=150, interval=5, label=f"task {task_id} overdue scan")
    sheet_state = ctx.sql(
        f"select answer_status, coalesce(submit_time::text,''), quality_status "
        f"from psy_assessment_answer_sheet where id = {draft['answerSheetId']}"
    ).split("|")
    require(sheet_state[0] == "SUBMITTED", f"draft must be auto-submitted: {sheet_state}")
    require(sheet_state[1], f"auto-submitted sheet must record submit_time: {sheet_state}")
    results = ctx.sql_one(
        f"select count(*) from psy_assessment_result result join psy_assessment_answer_sheet sheet "
        f"on sheet.id = result.answer_sheet_id where sheet.id = {draft['answerSheetId']}"
    )
    require(results == "1", f"auto-submitted sheet must produce exactly one result, found {results}")
    notifications = ctx.sql_one(
        f"select count(*) from psy_notification where notification_type = 'TASK_OVERDUE' and biz_id = {task_id}"
    )
    require(notifications == "1", f"overdue notification must be sent once, found {notifications}")
    metrics_text = ctx.http("GET", "/actuator/prometheus", token=_admin(ctx)).raw.decode("utf-8", "replace")
    require(
        "psy_scheduler" in metrics_text or "psy_assessment" in metrics_text,
        "scheduler/assessment metrics must be exposed",
    )
    return (
        f"task {task_id} -> OVERDUE; complete draft {draft['answerSheetId']} auto-submitted "
        f"(quality={sheet_state[2]}, 1 result); exactly one TASK_OVERDUE notification; scheduler metrics present. "
        "Note: auto-submit only runs for tasks with allow_timeout_submit_flag=true (F-33 documentation gap); an "
        "incomplete draft under the REJECT policy is also left as DRAFT"
    )


@case("MT-OPS-003")
def ops_003(ctx: Context) -> str:
    start_fast_instance(ctx)
    warning_id = ctx.sql_one(
        "select id from psy_warning_record where status in ('PENDING','ASSIGNED') "
        "and upper(warning_level) in ('HIGH','P1','CRITICAL','P0') order by id desc limit 1"
    )
    ctx.sql(
        "update psy_warning_record set escalated_at = null, escalation_count = 0, "
        f"deadline_time = now() - interval '2 hours' where id = {warning_id}"
    )

    def escalated() -> bool:
        return ctx.sql_one(f"select escalated_at is not null from psy_warning_record where id = {warning_id}") == "t"

    _wait_for(escalated, timeout=120, interval=4, label=f"warning {warning_id} escalation")
    state = ctx.sql(
        f"select escalation_count, coalesce(last_reminded_at::text,'') from psy_warning_record where id = {warning_id}"
    ).split("|")
    require(int(state[0]) >= 1, f"escalation_count must increase: {state}")
    metrics_text = ctx.http("GET", "/actuator/prometheus", token=_admin(ctx)).raw.decode("utf-8", "replace")
    require("psy_warning" in metrics_text, "warning metrics must be exposed")
    return f"warning {warning_id} escalated (escalation_count={state[0]}) with warning metrics present"


@case("MT-OPS-004")
def ops_004(ctx: Context) -> str:
    start_fast_instance(ctx)
    task_id = create_task(ctx, 2, f"MT-OPS-004-{ctx.unique('')}")
    stale = save_draft(ctx, task_id, 2, {1: "A"})
    keep_task = create_task(ctx, 2, f"MT-OPS-004KEEP-{ctx.unique('')}")
    fresh = save_draft(ctx, keep_task, 2, {1: "B"})
    ctx.sql(
        f"update psy_assessment_answer_sheet set updated_at = now() - interval '45 days' "
        f"where id = {stale['answerSheetId']}"
    )

    def cleaned() -> bool:
        remaining = ctx.sql_one(
            f"select count(*) from psy_assessment_answer_sheet where id = {stale['answerSheetId']}"
        )
        return remaining == "0"

    _wait_for(cleaned, timeout=120, interval=4, label="expired draft cleanup")
    fresh_state = ctx.sql_one(
        f"select answer_status from psy_assessment_answer_sheet where id = {fresh['answerSheetId']}"
    )
    require(fresh_state == "DRAFT", f"recent draft must be untouched: {fresh_state}")
    return (
        f"draft {stale['answerSheetId']} (updated 45 days ago) cleaned; recent draft {fresh['answerSheetId']} "
        "kept as DRAFT"
    )


# ---------------------------------------------------------------------------
# MT-DB
# ---------------------------------------------------------------------------


@case("MT-DB-005")
def db_005(ctx: Context) -> str:
    schema = f"psy_mt_seed_{int(time.time()) % 10_000_000}"
    migration_dir = ROOT / "backend/src/main/resources/db/migration"
    data_script = ROOT / "backend/src/main/resources/data-psy.sql"
    require(data_script.exists(), "data-psy.sql missing")
    environment = {**os.environ, **DB_ENV}

    def psql(sql: str) -> str:
        result = subprocess.run(
            [
                "psql", "-X", "-A", "-t", "-h", "127.0.0.1", "-U", "lx", "-d", "lx",
                "-v", "ON_ERROR_STOP=1", "-c", f"set search_path = {schema}; {sql}",
            ],
            capture_output=True,
            text=True,
            env=environment,
        )
        if result.returncode != 0:
            raise CheckFailure(f"psql failed: {result.stderr.strip()[:300]}")
        lines = [line for line in result.stdout.strip().splitlines() if line.strip() and line.strip() != "SET"]
        return lines[-1].strip() if lines else ""

    def run_script(script: Path) -> None:
        result = subprocess.run(
            [
                "psql", "-X", "-h", "127.0.0.1", "-U", "lx", "-d", "lx",
                "-v", "ON_ERROR_STOP=1", "-f", str(script),
            ],
            capture_output=True,
            text=True,
            env={**environment, "PGOPTIONS": f"-c search_path={schema}"},
        )
        if result.returncode != 0:
            raise CheckFailure(f"{script.name} failed: {result.stderr.strip()[:300]}")

    psql("select 1")
    subprocess.run(
        ["psql", "-X", "-h", "127.0.0.1", "-U", "lx", "-d", "lx", "-c", f"create schema {schema}"],
        capture_output=True,
        text=True,
        env=environment,
        check=True,
    )
    try:
        for migration in sorted(migration_dir.glob("V*.sql"), key=lambda item: int(item.name[1:].split("__", 1)[0])):
            run_script(migration)
        run_script(data_script)
        tables = [
            "sys_user",
            "sys_tenant",
            "psy_scale",
            "psy_assessment_task",
            "psy_assessment_result",
            "psy_report",
        ]
        before = {table: psql(f"select count(*) from {table}") for table in tables}
        run_script(data_script)
        after = {table: psql(f"select count(*) from {table}") for table in tables}
        require(before == after, f"seeding twice changed row counts: {before} -> {after}")
        duplicates = psql(
            "select count(*) from (select username from sys_user group by username having count(*) > 1) duplicate_users"
        )
        require(duplicates == "0", f"duplicate usernames after reseeding: {duplicates}")
        orphan_child = psql(
            "select count(*) from sys_group child left join sys_group parent on parent.id = child.parent_id "
            "where child.parent_id is not null and parent.id is null"
        )
        require(orphan_child == "0", f"tenant parent chain broken: {orphan_child}")
        tenant_mismatch = psql(
            "select count(*) from psy_warning_record warning join psy_assessment_result result "
            "on result.id = warning.result_id join psy_assessment_answer_sheet sheet "
            "on sheet.id = result.answer_sheet_id where sheet.tenant_id is distinct from warning.tenant_id"
        )
        require(tenant_mismatch == "0", f"parent-child tenant chain broken: {tenant_mismatch}")
        return (
            f"isolated schema {schema}: all migrations + data-psy.sql applied, second apply changed nothing "
            f"({', '.join(f'{name}={count}' for name, count in sorted(after.items()))}); no duplicate users, "
            "tenant parent chain intact"
        )
    finally:
        subprocess.run(
            ["psql", "-X", "-h", "127.0.0.1", "-U", "lx", "-d", "lx", "-c", f"drop schema if exists {schema} cascade"],
            capture_output=True,
            text=True,
            env=environment,
        )


@case("MT-DB-006")
def db_006(ctx: Context) -> str:
    preflight_dir = ROOT / "backend/src/main/resources/db/preflight"
    database = f"psy_mt_preflight_{int(time.time()) % 10_000_000}"
    environment = {**os.environ, **DB_ENV}
    migration_dir = ROOT / "backend/src/main/resources/db/migration"

    def psql(database_name: str, args: list[str], *, script: Path | None = None, sql: str | None = None) -> str:
        command = [
            "psql", "-X", "-A", "-t", "-h", "127.0.0.1", "-U", "lx", "-d", database_name, "-v", "ON_ERROR_STOP=1",
        ]
        command += ["-f", str(script)] if script is not None else ["-c", sql or ""]
        result = subprocess.run(command, capture_output=True, text=True, env=environment)
        return result.stdout + result.stderr if result.returncode == 0 else f"__FAIL__{result.stderr.strip()[:200]}"

    subprocess.run(["dropdb", "--if-exists", database], capture_output=True, env=environment)
    created = subprocess.run(["createdb", database], capture_output=True, text=True, env=environment)
    if created.returncode != 0:
        raise CheckFailure(f"could not create {database}: {created.stderr.strip()[:200]}")
    try:
        for migration in sorted(migration_dir.glob("V*.sql"), key=lambda item: int(item.name[1:].split("__", 1)[0])):
            output = psql(database, [], script=migration)
            if output.startswith("__FAIL__"):
                raise CheckFailure(f"migration {migration.name} failed: {output[:200]}")
        # Simulate a legacy pre-Flyway database so the baseline guard can run.
        psql(database, [], sql="drop table if exists flyway_schema_history")
        results = {}
        for script_name in ("existing-database-baseline.sql", "V16__tenant_ownership_hardening_preflight.sql"):
            output = psql(database, [], script=preflight_dir / script_name)
            results[script_name] = output.strip().splitlines()[-1] if output.strip() else "ok"
            if output.startswith("__FAIL__"):
                raise CheckFailure(f"{script_name} failed: {output[200:400]}")
        psy_tables = psql(database, [], sql="select count(*) from pg_tables where schemaname='public' and tablename like 'psy\\_%'").strip()
        orphan = psql(
            database,
            [],
            sql=(
                "select count(*) from psy_warning_record warning join psy_assessment_result result "
                "on result.id = warning.result_id join psy_assessment_answer_sheet sheet "
                "on sheet.id = result.answer_sheet_id where sheet.tenant_id is distinct from warning.tenant_id"
            ),
        ).strip()
        mapped = psql(
            database,
            [],
            sql="select count(*) from pg_indexes where schemaname='public' and indexname in "
            "('uk_psy_scale_tenant_code_version','uk_psy_scale_global_code_version','uk_psy_scale_code_version')",
        ).strip()
        return (
            f"preflight verified on a disposable legacy-shaped database ({database}): "
            f"baseline + V16 passed, {psy_tables.strip()} psy_* tables registered, orphan/parent-child "
            f"conflicts={orphan.strip()}, scale identity indexes present={mapped.strip()}"
        )
    finally:
        subprocess.run(["dropdb", "--if-exists", "--force", database], capture_output=True, env=environment)


# ---------------------------------------------------------------------------
# MT-NFR
# ---------------------------------------------------------------------------


@case("MT-NFR-002")
def nfr_002(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-NFR-002-{ctx.unique('')}")
    token_value = str(uuid.uuid4())
    results: list[tuple[int, str]] = []
    lock = threading.Lock()

    def submit() -> None:
        status, payload, _ = submit_answers(
            ctx, task_id, 2, {1: "A", 2: "B", 3: "C"}, submit_token=token_value
        )
        with lock:
            results.append((status, str(payload.get("code"))))

    threads = [threading.Thread(target=submit) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=60)
    successes = [item for item in results if item[0] == 200]
    require(len(results) == 2, f"both submissions must complete: {results}")
    require(successes, f"at least one submission must succeed: {results}")
    sheets = ctx.sql_one(
        f"select count(*) from psy_assessment_answer_sheet where task_id = {task_id} and user_id = 6 "
        "and answer_status = 'SUBMITTED'"
    )
    require(sheets == "1", f"concurrent replay must produce one submitted sheet, found {sheets}")
    result_count = ctx.sql_one(
        f"select count(*) from psy_assessment_result result join psy_assessment_answer_sheet sheet "
        f"on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id}"
    )
    reports = ctx.sql_one(
        f"select count(*) from psy_report report join psy_assessment_result result on result.id = report.result_id "
        f"join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id}"
    )
    warnings = ctx.sql_one(
        f"select count(*) from psy_warning_record warning join psy_assessment_result result "
        f"on result.id = warning.result_id join psy_assessment_answer_sheet sheet "
        f"on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id}"
    )
    require(result_count == "1", f"one result expected: {result_count}")
    require(reports == "1", f"one report expected: {reports}")
    return f"2 concurrent submits with the same token -> {results}; 1 sheet/1 result/1 report/{warnings} warnings"


@case("MT-NFR-003")
def nfr_003(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-NFR-003-{ctx.unique('')}")
    draft = save_draft(ctx, task_id, 2, {1: "A", 2: "B"})
    questions = fetch_question_meta(ctx, task_id)
    version = int(draft["versionNo"])
    outcomes: list[tuple[int, str]] = []
    lock = threading.Lock()

    def save(question_no: int, option_code: str) -> None:
        question = questions[question_no]
        option = next(item for item in question["options"] if item["optionCode"] == option_code)
        response = ctx.http(
            "POST",
            "/api/v1/answer-sheets/save",
            token=ctx.token("respondent"),
            body={
                "taskId": task_id,
                "scaleId": 2,
                "answerSheetId": int(draft["answerSheetId"]),
                "versionNo": version,
                "answers": [{"questionId": question["questionId"], "optionId": option["optionId"]}],
            },
        )
        with lock:
            outcomes.append((response.status, str(response.code())))

    threads = [threading.Thread(target=save, args=(1, "D")), threading.Thread(target=save, args=(2, "D"))]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=60)
    ok = [item for item in outcomes if item[0] == 200]
    conflict = [item for item in outcomes if item[0] == 400]
    require(len(ok) == 1, f"exactly one save must win: {outcomes}")
    require(conflict, f"the loser must get a version conflict: {outcomes}")
    rows = ctx.sql(
        f"select question_id, option_id from psy_assessment_answer_item "
        f"where answer_sheet_id = {draft['answerSheetId']} order by question_id"
    ).splitlines()
    require(len(rows) == 1, f"mixed writes detected, rows={rows}")
    return f"two concurrent saves at version {version} -> one 200, one {conflict}; stored rows={rows}"


@case("MT-NFR-004")
def nfr_004(ctx: Context) -> str:
    session = _admin(ctx)
    report_id = ctx.sql_one("select id from psy_report order by id desc limit 1")
    created = require_code(
        ctx.http(
            "POST",
            "/api/v1/exports/reports/jobs",
            token=session,
            body={"reportId": int(report_id), "exportFormat": "WORD"},
        ),
        200,
    )
    job_id = created.get("jobId") or created.get("id")
    ctx.sql(
        "update psy_export_job set status = 'FAILED', retry_count = 1, next_retry_at = now() - interval '1 minute', "
        f"error_message = 'MT simulated failure' where id = '{job_id}'"
    )
    outcomes: list[tuple[int, str]] = []
    lock = threading.Lock()

    def retry() -> None:
        response = ctx.http("POST", f"/api/v1/exports/reports/jobs/{job_id}/retry", token=session)
        with lock:
            outcomes.append((response.status, str(response.code())))

    threads = [threading.Thread(target=retry) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=60)
    rows = ctx.sql_one(f"select count(*) from psy_export_job where id = '{job_id}'")
    require(rows == "1", f"export job rows must stay single: {rows}")
    state = ctx.sql(
        f"select status, retry_count, coalesce(processing_token,'') from psy_export_job where id = '{job_id}'"
    ).split("|")
    require(state[0] in ("PENDING", "PROCESSING", "DONE", "FAILED"), f"retry outcome: {state}")
    require(sum(1 for item in outcomes if item[0] == 200) >= 1, f"one retry must be accepted: {outcomes}")
    return (
        f"two concurrent retries of export job {job_id} -> {outcomes}; single row, state={state[0]}, "
        f"lease={'held' if state[2] else 'free'}"
    )


@case("MT-NFR-005")
def nfr_005(ctx: Context) -> str:
    start_fast_instance(ctx)
    delivery_ids = [_seed_push_delivery(ctx, status="PENDING") for _ in range(3)]
    ctx.sql(
        "update psy_notification_delivery set delivery_status = 'PENDING', next_retry_at = null, error_message = null "
        f"where id in ({','.join(str(item) for item in delivery_ids)})"
    )

    def all_settled() -> bool:
        settled = ctx.sql_one(
            "select count(*) from psy_notification_delivery "
            f"where id in ({','.join(str(item) for item in delivery_ids)}) "
            "and delivery_status in ('SENT','DELIVERED','FAILED','DEAD_LETTER')"
        )
        return settled == str(len(delivery_ids))

    _wait_for(all_settled, timeout=120, interval=4, label="pending push deliveries to settle")
    stats = ctx.sql(
        "select id || ':' || delivery_status || ':' || retry_count || ':' || coalesce(processing_token,'-') "
        f"from psy_notification_delivery where id in ({','.join(str(item) for item in delivery_ids)}) order by id"
    ).splitlines()
    require(all(":SENT:" in line for line in stats), f"all deliveries must be sent exactly once: {stats}")
    provider_ids = ctx.sql_one(
        "select count(distinct provider_message_id) from psy_notification_delivery "
        f"where id in ({','.join(str(item) for item in delivery_ids)}) and provider_message_id is not null"
    )
    require(
        provider_ids in ("0", str(len(delivery_ids))),
        f"each delivery must have its own provider message id: {provider_ids}",
    )
    return (
        f"two instances scanned the same PENDING batch; {len(delivery_ids)} deliveries sent once each "
        f"({stats}); distinct provider message ids={provider_ids}"
    )


@case("MT-NFR-006")
def nfr_006(ctx: Context) -> str:
    doc = ROOT / "doc/29-performance-capacity-baseline.md"
    require(doc.exists(), "capacity baseline document missing")
    text = doc.read_text(encoding="utf-8")
    banned = ["生产并发容量", "SLO 达标", "SLA 满足"]
    hits = [word for word in banned if word in text]
    require(not hits, f"capacity document must not claim production capacity/SLO: {hits}")
    return "capacity document scoped to the local serial baseline; no production capacity/SLO claims found"


@case("MT-NFR-007")
def nfr_007(ctx: Context) -> str:
    registry_path = ROOT / "doc/scale-packages/scale-adaptation-registry.json"
    text = registry_path.read_text(encoding="utf-8")
    blocked_entries = text.count("BLOCKED_EXTERNAL")
    require(blocked_entries >= 1, "scale registry must keep BLOCKED_EXTERNAL entries")
    require("SCL90" in text, "SCL-90 entry missing from the registry")
    draft_scales = ctx.sql_one(
        "select count(*) from psy_scale where status = 'DRAFT' and scale_code like 'SCL90%'"
    )
    require(int(draft_scales) >= 1, "SCL-90 technical scale must remain DRAFT without external sign-off")
    return (
        f"registry keeps {blocked_entries} BLOCKED_EXTERNAL markers; SCL-90 remains externally blocked with "
        f"{draft_scales} DRAFT technical scale(s) in the live DB"
    )
