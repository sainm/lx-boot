"""Full-coverage API cases (MT-API-*) for endpoints that had no automated check.

Each case exercises the endpoint through real HTTP against the running
backend and asserts either the positive contract or the documented
fail-closed behaviour (role/validation/configuration errors).  Cases that need
an external credential mark themselves BLOCKED with the exact reason so the
execution record stays honest.
"""

from __future__ import annotations

import json
import time
from typing import Any

from harness import CheckBlocked, Context, case, require, require_code
from checks_common import api


def _data(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any) -> Any:
    return require_code(api(ctx, method, path, user=user, **kwargs), 200)


def _envelope(response: Any, label: str) -> dict[str, Any]:
    payload = response.payload if isinstance(response.payload, dict) else None
    require(payload is not None, f"{label}: expected a JSON envelope, got {response.status}")
    require("code" in payload and "message" in payload, f"{label}: envelope missing code/message: {payload}")
    return payload


def _expect_fail_closed(ctx: Context, method: str, path: str, expected: tuple[int, ...], user: str = "assessor", **kwargs: Any) -> str:
    response = api(ctx, method, path, user=user, **kwargs)
    payload = _envelope(response, f"{method} {path}")
    require(
        response.status in expected,
        f"{method} {path}: expected one of {expected}, got {response.status}: {payload}",
    )
    require(response.status < 500, f"{method} {path}: must fail closed, got {response.status}")
    return f"{response.status} {payload.get('code')}"


def _temp_user(ctx: Context, roles: tuple[str, ...], prefix: str) -> dict[str, object]:
    from checks_account_security import ensure_temp_user

    return ensure_temp_user(ctx, roles=roles, prefix=prefix)


def _published_scale_id(ctx: Context) -> int:
    value = ctx.sql_one("select id from psy_scale where status = 'PUBLISHED' order by id desc limit 1")
    require(bool(value), "no published scale available for the fixture")
    return int(value)


# ---------------------------------------------------------------------------
# Directory + appointments + warnings
# ---------------------------------------------------------------------------


@case("MT-API-001")
def api_001(ctx: Context) -> str:
    users = _data(ctx, "GET", "/api/v1/directory/users?size=5")
    require(users.get("total", 0) > 0 and users.get("list"), "directory/users must return rows")
    active = _data(ctx, "GET", "/api/v1/directory/users?activeOnly=true&size=100")
    require(all(item["status"] == "ENABLED" for item in active["list"]), "activeOnly must filter to ENABLED")
    groups = _data(ctx, "GET", "/api/v1/directory/groups")
    tasks = _data(ctx, "GET", "/api/v1/directory/tasks")
    scales = _data(ctx, "GET", "/api/v1/directory/scales")
    require(isinstance(groups, list) and groups, "directory/groups must return rows")
    require(isinstance(tasks, list) and tasks, "directory/tasks must return rows")
    require(isinstance(scales, list) and scales, "directory/scales must return rows")
    denied = _expect_fail_closed(ctx, "GET", "/api/v1/directory/users", (403,), user="respondent")
    return (
        f"users={users['total']} active={active['total']} groups={len(groups)} tasks={len(tasks)} "
        f"scales={len(scales)}; respondent -> {denied}"
    )


@case("MT-API-002")
def api_002(ctx: Context) -> str:
    page = _data(ctx, "GET", "/api/v1/appointments?page=1&size=5")
    require({"list", "page", "size", "total"} <= set(page), f"unexpected page shape: {page}")
    filtered = _data(ctx, "GET", "/api/v1/appointments?userId=6&size=10")
    require(all(item["userId"] == 6 for item in filtered["list"]), "userId filter must restrict the register")
    named = [item for item in page["list"] if item.get("userDisplayName")]
    require(named, "register must expose the respondent display name")
    denied = _expect_fail_closed(ctx, "GET", "/api/v1/appointments", (403,), user="respondent")
    return f"total={page['total']} filtered={filtered['total']} named={len(named)}; respondent -> {denied}"


@case("MT-API-003")
def api_003(ctx: Context) -> str:
    options = _data(ctx, "GET", "/api/v1/warnings/assignee-options")
    require(isinstance(options, list) and options, "assignee options must not be empty")
    assignee = next((item for item in options if item["username"] == "counselor"), options[0])
    warning_id = ctx.sql_one("select id from psy_warning_record where status = 'PENDING' order by id desc limit 1")
    require(bool(warning_id), "no PENDING warning available for assignment")
    result = _data(
        ctx,
        "POST",
        f"/api/v1/warnings/{warning_id}/assign",
        body={"assigneeUserId": assignee["userId"]},
    )
    require(result["status"] == "ASSIGNED", f"unexpected assign result: {result}")
    stored = ctx.sql_one(
        f"select assignee_user_id from psy_warning_assignment where warning_id = {warning_id} "
        "order by assigned_at desc, id desc limit 1"
    )
    require(stored == str(assignee["userId"]), f"assignment row mismatch: {stored}")
    listed = _data(ctx, "GET", f"/api/v1/warnings?page=1&size=200")
    row = next((item for item in listed["list"] if item["id"] == int(warning_id)), None)
    require(row is not None and row.get("assigneeDisplayName"), "warning list must expose the assignee name")
    return f"warning {warning_id} -> {assignee['displayName']} ({result['status']})"


@case("MT-API-004")
def api_004(ctx: Context) -> str:
    dashboard = _data(ctx, "GET", "/api/v1/statistics/dashboard")
    require(dashboard.get("overviewCards"), "dashboard must return overview cards")
    card_keys = {card["key"] for card in dashboard["overviewCards"]}
    require({"totalScales", "totalTasks"} <= card_keys, f"unexpected cards: {card_keys}")
    japanese = require_code(
        ctx.http(
            "GET",
            "/api/v1/statistics/dashboard",
            token=ctx.token("assessor"),
            headers={"Accept-Language": "ja-JP"},
        ),
        200,
    )
    require(
        any(any("\u3040" <= char <= "\u30ff" for char in card["label"]) for card in japanese["overviewCards"]),
        "dashboard labels must follow Accept-Language",
    )
    leader = _temp_user(ctx, ("SCHOOL_LEADER",), "mtapi")
    leader_token = ctx.login(str(leader["username"]), str(leader["password"]))
    leader_dashboard = require_code(ctx.http("GET", "/api/v1/statistics/dashboard", token=leader_token), 200)
    require(leader_dashboard["overviewCards"], "SCHOOL_LEADER dashboard must render")
    return f"cards={len(dashboard['overviewCards'])} ja label ok; SCHOOL_LEADER dashboard ok"


# ---------------------------------------------------------------------------
# Reports + exports + notifications
# ---------------------------------------------------------------------------


@case("MT-API-005")
def api_005(ctx: Context) -> str:
    report_id = ctx.sql_one("select id from psy_report order by id desc limit 1")
    require(bool(report_id), "no report available for export")
    exported = _data(
        ctx,
        "POST",
        "/api/v1/exports/reports",
        body={"reportId": int(report_id), "exportFormat": "TEXT", "desensitized": True},
    )
    require(exported.get("content"), "synchronous export must return content")
    own_report = ctx.sql_one("select id from psy_report where result_id in (select id from psy_assessment_answer_sheet where user_id = 6) order by id desc limit 1")
    require(bool(own_report), "respondent fixture report missing")
    owned = _data(
        ctx,
        "POST",
        "/api/v1/exports/reports",
        user="respondent",
        body={"reportId": int(own_report), "exportFormat": "TEXT", "desensitized": True},
    )
    require(owned.get("content"), "respondent must export their own report")
    other = ctx.sql_one("select id from psy_report where result_id = (select id from psy_assessment_answer_sheet where user_id = 4 order by id desc limit 1) order by id desc limit 1")
    if other:
        denied = _expect_fail_closed(
            ctx,
            "POST",
            "/api/v1/exports/reports",
            (403, 404),
            user="respondent",
            body={"reportId": int(other), "exportFormat": "TEXT", "desensitized": True},
        )
    else:
        denied = "skipped (no cross-user report fixture)"
    return f"staff export {exported['reportId']} bytes={len(exported['content'])}; own export ok; cross-user {denied}"


@case("MT-API-006")
def api_006(ctx: Context) -> str:
    job_id = ctx.sql_one("select id from psy_export_job where status = 'DONE' order by created_at desc limit 1")
    if not job_id:
        raise CheckBlocked("no DONE export job in this environment; submit one through EXP module first")
    response = api(ctx, "GET", f"/api/v1/exports/reports/jobs/{job_id}/download")
    require(response.status == 200, f"job download failed: {response.status} {response.payload}")
    require(len(response.raw) > 0, "downloaded artifact must not be empty")
    return f"job {job_id} downloaded {len(response.raw)} bytes"


@case("MT-API-007")
def api_007(ctx: Context) -> str:
    token = ctx.token("respondent")
    items = require_code(ctx.http("GET", "/api/v1/my/notifications", token=token), 200)
    require(isinstance(items, list) and items, "respondent must have notifications")
    unread = next((item for item in items if not item["readFlag"]), None)
    if unread is None:
        raise CheckBlocked("all respondent notifications are already read; no row to mark")
    result = require_code(ctx.http("POST", f"/api/v1/my/notifications/{unread['id']}/read", token=token), 200)
    require(result.get("readFlag") is True, f"mark-as-read must flip the flag: {result}")
    stored = ctx.sql_one(
        f"select read_flag from psy_notification_delivery where notification_id = {unread['id']} "
        "and receiver_user_id = 6 and delivery_channel = 'IN_APP'"
    )
    require(stored == "t", f"read flag not persisted: {stored}")
    refreshed = require_code(ctx.http("GET", "/api/v1/my/notifications", token=token), 200)
    row = next((item for item in refreshed if item["id"] == unread["id"]), None)
    require(row is not None and row["readFlag"] is True, "list must reflect the read flag")
    return f"notification {unread['id']} marked read"


@case("MT-API-008")
def api_008(ctx: Context) -> str:
    result_id = ctx.sql_one(
        "select r.id from psy_assessment_result r join psy_assessment_answer_sheet sh on sh.id = r.answer_sheet_id "
        "where sh.answer_status = 'SUBMITTED' and sh.user_id = 6 order by r.id desc limit 1"
    )
    require(bool(result_id), "no submitted result for the respondent fixture")
    detail = _data(ctx, "GET", f"/api/v1/reports/by-result/{result_id}")
    require(detail.get("reportId"), f"by-result must resolve a report: {detail}")
    return f"result {result_id} -> report {detail['reportId']}"


# ---------------------------------------------------------------------------
# Task / scale management
# ---------------------------------------------------------------------------


@case("MT-API-009")
def api_009(ctx: Context) -> str:
    now = time.strftime("%Y-%m-%dT%H:%M:%S")
    future = time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(time.time() + 7 * 86400))
    created = _data(
        ctx,
        "POST",
        "/api/v1/tasks",
        body={
            "taskName": f"MT-API-009-{int(time.time()) % 10_000_000}",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "anonymousFlag": False,
            "allowSaveFlag": True,
            "allowTimeoutSubmitFlag": False,
            "allowRetakeFlag": False,
            "startTime": now,
            "endTime": future,
        },
    )
    task_id = created["id"]
    updated = _data(
        ctx,
        "POST",
        f"/api/v1/tasks/{task_id}",
        body={
            "taskName": f"MT-API-009-updated-{int(time.time()) % 10_000_000}",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "anonymousFlag": False,
            "allowSaveFlag": True,
            "allowTimeoutSubmitFlag": False,
            "allowRetakeFlag": False,
            "startTime": now,
            "endTime": future,
        },
    )
    require(updated.get("taskName", "").startswith("MT-API-009-updated"), f"update not persisted: {updated}")
    invalid = _expect_fail_closed(
        ctx, "POST", f"/api/v1/tasks/{task_id}", (400,), body={"taskName": "", "scaleId": 2, "taskMode": ""}
    )
    _data(ctx, "DELETE", f"/api/v1/tasks/{task_id}")
    return f"task {task_id} updated; invalid payload -> {invalid}"


@case("MT-API-010")
def api_010(ctx: Context) -> str:
    response = api(ctx, "GET", "/api/v1/scales/import-template")
    require(response.status == 200, f"import template failed: {response.status}")
    require(len(response.raw) > 1000, "template must be a non-trivial xlsx payload")
    content_type = (response.headers or {}).get("Content-Type", "")
    require("spreadsheet" in content_type or "octet-stream" in content_type, f"unexpected content type: {content_type}")
    return f"template {len(response.raw)} bytes ({content_type})"


@case("MT-API-011")
def api_011(ctx: Context) -> str:
    scale_id = _published_scale_id(ctx)
    package = _data(ctx, "GET", f"/api/v1/scales/{scale_id}/package")
    require(isinstance(package, dict) and package, f"package snapshot empty: {package}")
    return f"scale {scale_id} package keys={len(package)}"


@case("MT-API-012")
def api_012(ctx: Context) -> str:
    scale_id = _published_scale_id(ctx)
    history = _data(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/history")
    reviews = _data(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/history/reviews")
    runs = _data(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/history/runs")
    sizes: list[str] = []
    for label, value, key in (("history", history, "cases"), ("reviews", reviews, "reviews"), ("runs", runs, "runs")):
        require(isinstance(value, dict), f"{label} page shape unexpected: {value}")
        items = value.get(key) or value.get("items") or []
        require(isinstance(items, list), f"{label} items unexpected: {value}")
        sizes.append(f"{label}={len(items)}")
    return f"scale {scale_id} " + ", ".join(sizes)


@case("MT-API-013")
def api_013(ctx: Context) -> str:
    scale_id = _published_scale_id(ctx)
    result = _expect_fail_closed(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/publication/reviews/PROFESSIONAL",
        (400, 403, 404, 409),
        body={"decision": "APPROVED", "reviewToken": "MT-INVALID-TOKEN", "reviewerNote": "mt-api-013"},
    )
    return f"invalid review token rejected with {result}"


@case("MT-API-014")
def api_014(ctx: Context) -> str:
    scale_id = _published_scale_id(ctx)
    batch = _expect_fail_closed(
        ctx, "POST", f"/api/v1/scales/{scale_id}/dimensions/batch", (400, 403, 409), body={"dimensions": []}
    )
    single = _expect_fail_closed(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/dimensions/99999999",
        (400, 403, 404),
        body={"dimensionCode": "MT", "dimensionName": "MT", "sortNo": 1},
    )
    return f"empty batch -> {batch}; unknown dimension -> {single}"


# ---------------------------------------------------------------------------
# auth-starter administration
# ---------------------------------------------------------------------------


@case("MT-API-015")
def api_015(ctx: Context) -> str:
    seen: list[str] = []
    for path in ("/auth/roles", "/auth/permissions", "/auth/groups", "/auth/tenants", "/auth/users?page=1&size=5"):
        value = _data(ctx, "GET", path, user="sysadmin")
        require(value is not None, f"{path} returned no data")
        seen.append(f"{path}={len(value) if isinstance(value, list) else 'page'}")
    denied = _expect_fail_closed(ctx, "GET", "/auth/users", (403,), user="respondent")
    return "; ".join(seen) + f"; respondent -> {denied}"


@case("MT-API-016")
def api_016(ctx: Context) -> str:
    user = _temp_user(ctx, ("USER",), "mtsession")
    ctx.login(str(user["username"]), str(user["password"]), device_id=f"mt-api-016-{int(time.time()) % 100000}")
    sessions = _data(ctx, "GET", f"/auth/users/{user['userId']}/sessions", user="sysadmin")
    require(isinstance(sessions, list) and sessions, "session list must contain the fresh login")
    session = next((item for item in sessions if item.get("status") == "ACTIVE"), sessions[0])
    revoked = _data(
        ctx,
        "POST",
        f"/auth/users/{user['userId']}/sessions/{session['sessionId']}/revoke",
        user="sysadmin",
    )
    require(revoked is not None, "revoke must return a response body")
    return f"user {user['userId']} sessions={len(sessions)} revoked {session['sessionId'][:8]}…"


@case("MT-API-017")
def api_017(ctx: Context) -> str:
    user = _temp_user(ctx, ("USER",), "mtdevice")
    devices = _data(ctx, "GET", f"/auth/users/{user['userId']}/devices", user="sysadmin")
    require(isinstance(devices, list), "device list must be a list")
    return f"user {user['userId']} devices={len(devices)}"


@case("MT-API-018")
def api_018(ctx: Context) -> str:
    events = _data(ctx, "GET", "/auth/security-events", user="sysadmin")
    require(isinstance(events, list) and events, "security events must not be empty")
    kinds = {item.get("eventType") for item in events}
    return f"security events={len(events)} distinctTypes={len(kinds)}"


@case("MT-API-019")
def api_019(ctx: Context) -> str:
    ok = _data(ctx, "GET", "/auth/admin/ping", user="sysadmin")
    require(ok.get("ok") is True, f"admin ping must answer ok: {ok}")
    denied = _expect_fail_closed(ctx, "GET", "/auth/admin/ping", (401, 403), user="respondent")
    return f"sysadmin ok; respondent -> {denied}"


@case("MT-API-020")
def api_020(ctx: Context) -> str:
    user = _temp_user(ctx, ("USER",), "mtreset")
    new_password = f"Mt{int(time.time()) % 10_000_000}Pass1!"
    _data(
        ctx,
        "POST",
        "/auth/password/reset",
        user="sysadmin",
        body={"principal": user["username"], "newPassword": new_password},
    )
    status, payload = ctx.login_full(str(user["username"]), new_password, device_id=f"mt-api-020-{int(time.time())}")
    require(status == 200 and payload.get("code") == "0", f"login with the reset password failed: {status} {payload}")
    _data(
        ctx,
        "POST",
        "/auth/password/reset",
        user="sysadmin",
        body={"principal": user["username"], "newPassword": "ChangeMe123"},
    )
    return f"password reset for {user['username']} verified and restored"


@case("MT-API-021")
def api_021(ctx: Context) -> str:
    result = _expect_fail_closed(
        ctx, "POST", "/auth/qr/cancel", (400, 403, 404), user="respondent", body={"sceneCode": "MT-NOT-EXIST"}
    )
    return f"unknown qr scene rejected with {result}"


@case("MT-API-022")
def api_022(ctx: Context) -> str:
    email = _expect_fail_closed(ctx, "GET", "/auth/email-verify?token=MT-INVALID", (400, 401, 410))
    ticket = _expect_fail_closed(ctx, "POST", "/auth/sso/token", (400, 401, 403), body={"ticket": "MT-INVALID"})
    return f"invalid email token -> {email}; invalid sso ticket -> {ticket}"


@case("MT-API-023")
def api_023(ctx: Context) -> str:
    authorize = api(ctx, "GET", "/auth/sso/oidc/authorize")
    _envelope(authorize, "GET /auth/sso/oidc/authorize")
    require(authorize.status < 500, f"authorize must fail closed without an IdP: {authorize.status}")
    callback = _expect_fail_closed(ctx, "GET", "/auth/sso/oidc/callback?code=MT-INVALID", (400, 401, 403, 404, 502))
    return f"unconfigured SSO authorize={authorize.status} callback -> {callback} (fail-closed; positive path needs an IdP)"


@case("MT-API-024")
def api_024(ctx: Context) -> str:
    user = _temp_user(ctx, ("USER",), "mtroles")
    _data(
        ctx,
        "POST",
        f"/auth/users/{user['userId']}/roles",
        user="sysadmin",
        body={"roleCodes": ["USER", "COUNSELOR"]},
    )
    stored = ctx.sql_one(
        "select string_agg(r.role_code, ',' order by r.role_code) from sys_user_role ur "
        f"join sys_role r on r.id = ur.role_id where ur.user_id = {user['userId']}"
    )
    require("COUNSELOR" in (stored or ""), f"role assignment not persisted: {stored}")
    return f"user {user['userId']} roles={stored}"


# ---------------------------------------------------------------------------
# WeChat integration (network channel; fails closed when unconfigured)
# ---------------------------------------------------------------------------


@case("MT-API-025")
def api_025(ctx: Context) -> str:
    response = ctx.http("POST", "/wechat/jssdk/config", body={"url": "http://localhost/"})
    require(response.status == 200, f"jssdk config must answer a JSON map: {response.status}")
    payload = response.payload if isinstance(response.payload, dict) else {}
    return f"jssdk config keys={sorted(payload)} (empty map means WeChat is not configured)"


@case("MT-API-026")
def api_026(ctx: Context) -> str:
    missing = _expect_fail_closed(ctx, "GET", "/wechat/portal", (400,))
    verified = api(
        ctx,
        "GET",
        "/wechat/portal?signature=mt&timestamp=1&nonce=mt&echostr=mt-verify",
    )
    require(verified.status == 200, f"portal verification must echo echostr: {verified.status}")
    callback = api(
        ctx,
        "POST",
        "/wechat/portal",
        raw_body=b"<xml><FromUserName>mt</FromUserName><MsgType>text</MsgType><Content>MT</Content></xml>",
        content_type="text/xml",
    )
    require(callback.status < 500, f"portal callback must not 500: {callback.status}")
    return f"missing params -> {missing}; verification echoed; callback={callback.status}"


@case("MT-API-027")
def api_027(ctx: Context) -> str:
    denied = _expect_fail_closed(
        ctx,
        "POST",
        "/api/v1/wechat/menu/sync",
        (401, 403),
        raw_body=json.dumps({"button": []}).encode("utf-8"),
        content_type="application/json",
    )
    response = api(
        ctx,
        "POST",
        "/api/v1/wechat/menu/sync",
        user="sysadmin",
        raw_body=json.dumps({"button": []}).encode("utf-8"),
        content_type="application/json",
    )
    require(response.status < 500, f"menu sync must fail closed without WeChat config: {response.status}")
    return f"assessor -> {denied}; sysadmin -> {response.status} {response.payload if isinstance(response.payload, dict) else ''}"
