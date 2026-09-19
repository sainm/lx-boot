"""Account, permission, tenant-isolation and i18n checks (batch 1)."""

from __future__ import annotations

import re
from pathlib import Path

from harness import (
    CheckBlocked,
    CheckFailure,
    Context,
    DEFAULT_PASSWORD,
    ROOT,
    case,
    require,
    require_code,
)


def ensure_temp_user(
    ctx: Context,
    roles: tuple[str, ...] = ("USER",),
    prefix: str = "mttmp",
) -> dict[str, object]:
    key = f"temp:{prefix}:{','.join(roles)}"
    cached = ctx.store.get(key)
    if isinstance(cached, dict):
        return cached
    admin = ctx.token("org_manager")
    username = ctx.unique(prefix)
    password = f"Mt{username[-6:]}Pass1!"
    sequence = int(ctx.store.get("mobile_seq", 0)) + 1
    ctx.store["mobile_seq"] = sequence
    mobile = f"13{int(__import__('time').time() * 1000) % 10**8:08d}{sequence % 10}"
    groups = require_code(ctx.http("GET", "/api/v1/user-admin/groups", token=admin), 200)
    require(isinstance(groups, list) and groups, "org_manager must see at least one group")
    group = next(
        (item for item in groups if item.get("groupCode") == "DEFAULT_GENERAL"),
        groups[0],
    )
    created = require_code(
        ctx.http(
            "POST",
            "/api/v1/user-admin/users",
            token=admin,
            body={
                "username": username,
                "password": password,
                "displayName": "MT Temp User",
                "email": f"{username}@example.local",
                "mobile": mobile,
                "groupId": group["groupId"],
                "roleCodes": list(roles),
            },
        ),
        200,
    )
    record: dict[str, object] = {
        "username": username,
        "password": password,
        "userId": created.get("userId"),
        "groupId": group["groupId"],
    }
    ctx.store[key] = record
    return record


def unlock_user(ctx: Context, username: str) -> None:
    ctx.sql(
        "update sys_user set failed_login_attempts = 0, locked_until = null "
        f"where username = '{username}'"
    )


# --------------------------------------------------------------------------
# AUTH
# --------------------------------------------------------------------------


@case("MT-AUTH-001")
def auth_001(ctx: Context) -> str:
    status, payload = ctx.login_full("assessor")
    require(status == 200 and payload.get("code") == "0", f"login failed: {status} {payload}")
    token = payload["data"]["accessToken"]
    me = require_code(ctx.http("GET", "/auth/me", token=token), 200)
    require(me.get("username") == "assessor", f"unexpected profile: {me}")
    return "assessor login HTTP 200 code=0 and /auth/me returns assessor"


@case("MT-AUTH-002")
def auth_002(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtlock")
    username = str(user["username"])
    for _ in range(5):
        ctx.login_full(username, "WrongPass1!")
    status, payload = ctx.login_full(username, str(user["password"]))
    require(status != 200, f"locked account must not log in: {status} {payload}")
    locked_until = ctx.sql_one(
        f"select coalesce(locked_until::text, '') from sys_user where username = '{username}'"
    )
    require(bool(locked_until), "locked_until must be written after 5 failures")
    unlock_user(ctx, username)
    retry_status, retry_payload = ctx.login_full(username, str(user["password"]))
    require(
        retry_status == 200 and retry_payload.get("code") == "0",
        f"account must log in after unlock: {retry_status} {retry_payload}",
    )
    return f"5 failures locked until {locked_until}; unlock restored login"


@case("MT-AUTH-004")
def auth_004(ctx: Context) -> str:
    ctx.login("assessor")
    refresh = ctx.refresh_token("assessor")
    data = require_code(
        ctx.http("POST", "/auth/token/refresh", body={"refreshToken": refresh}),
        200,
    )
    require(bool(data.get("accessToken")), f"refresh must return accessToken: {data}")
    ctx.put_tokens("assessor", data["accessToken"], data.get("refreshToken"))
    me = require_code(ctx.http("GET", "/auth/me", token=data["accessToken"]), 200)
    require(me.get("username") == "assessor", f"refreshed token must work: {me}")
    return "refresh rotated the token and the new access token works on /auth/me"


@case("MT-AUTH-005")
def auth_005(ctx: Context) -> str:
    ctx.login("enterprise_staff")
    token = ctx.token("enterprise_staff")
    refresh = ctx.refresh_token("enterprise_staff")
    require_code(ctx.http("POST", "/auth/logout", token=token, body={"refreshToken": refresh}), 200)
    after = ctx.http("GET", "/auth/me", token=token)
    require(after.status == 401, f"logout must invalidate the session: {after.status} {after.payload}")
    ctx.login("enterprise_staff")
    return "logout HTTP 200 and the old access token is rejected with 401"


@case("MT-AUTH-007")
def auth_007(ctx: Context) -> str:
    username_status, username_payload = ctx.login_full("assessor")
    require(username_status == 200 and username_payload.get("code") == "0", "username login must work")
    email_status, email_payload = ctx.login_full("assessor@example.local")
    require(
        email_status != 200,
        f"seed principal_key only binds usernames; email login should fail, got {email_status} {email_payload}",
    )
    return f"username login 200; email login rejected with HTTP {email_status} (documented contract gap)"


@case("MT-AUTH-010")
def auth_010(ctx: Context) -> str:
    token = ctx.token("respondent")
    sessions = require_code(ctx.http("GET", "/auth/me/sessions", token=token), 200)
    require(isinstance(sessions, list) and sessions, f"sessions must not be empty: {sessions}")
    current = [item for item in sessions if item.get("current")]
    require(len(current) == 1, f"exactly one current session expected: {sessions}")
    return f"{len(sessions)} session(s) returned with one current session"


@case("MT-AUTH-011")
def auth_011(ctx: Context) -> str:
    token = ctx.token("respondent")
    policy = require_code(ctx.http("GET", "/auth/me/session-policy", token=token), 200)
    require(policy.get("policy") in {"MULTI_DEVICE", "SINGLE_DEVICE"}, f"unexpected policy: {policy}")
    updated = require_code(
        ctx.http("POST", "/auth/me/session-policy", token=token, body={"policy": "MULTI_DEVICE"}),
        200,
    )
    require(updated.get("policy") == "MULTI_DEVICE", f"policy update failed: {updated}")
    return f"policy read/update works (was {policy.get('policy')})"


@case("MT-AUTH-012")
def auth_012(ctx: Context) -> str:
    token_a = ctx.login("campus_student", device_id="mt-session-a")
    token_b = ctx.login("campus_student", device_id="mt-session-b")
    sessions = require_code(ctx.http("GET", "/auth/me/sessions", token=token_b), 200)
    other = next(
        (
            item
            for item in sessions
            if item.get("deviceId") == "mt-session-a" and item.get("status") == "ACTIVE"
        ),
        None,
    )
    if other is None:
        raise CheckFailure(f"session for device mt-session-a not found: {sessions}")
    require_code(
        ctx.http(
            "POST",
            f"/auth/me/sessions/{other['sessionId']}/revoke",
            token=token_b,
        ),
        200,
    )
    revoked_token_status = ctx.http("GET", "/auth/me", token=token_a).status
    require(
        revoked_token_status == 401,
        f"revoking the other session must invalidate token A, got {revoked_token_status}",
    )
    ctx.put_tokens("campus_student", token_b, ctx.refresh_token("campus_student"))
    return f"revoked session {other['sessionId']} and token A was rejected with 401"


@case("MT-AUTH-013")
def auth_013(ctx: Context) -> str:
    ctx.login("campus_counselor", device_id="mt-revoke-a")
    token_b = ctx.login("campus_counselor", device_id="mt-revoke-b")
    ctx.store["session_token_b"] = token_b
    data = require_code(
        ctx.http("POST", "/auth/me/sessions/revoke-others", token=token_b),
        200,
    )
    require(isinstance(data.get("revokedCount"), int), f"revokedCount missing: {data}")
    return f"revoke-others returned revokedCount={data['revokedCount']}"


@case("MT-AUTH-014")
def auth_014(ctx: Context) -> str:
    token_a = ctx.login("campus_manager", device_id="mt-single-a")
    token_b = ctx.login("campus_manager", device_id="mt-single-b")
    data = require_code(
        ctx.http(
            "POST",
            "/auth/me/session-policy",
            token=token_b,
            body={"policy": "SINGLE_DEVICE"},
        ),
        200,
    )
    require(data.get("policy") == "SINGLE_DEVICE", f"single-device policy not stored: {data}")
    old_status = ctx.http("GET", "/auth/me", token=token_a).status
    require(old_status == 401, f"single-device switch must revoke the other session, got {old_status}")
    require_code(
        ctx.http("POST", "/auth/me/session-policy", token=token_b, body={"policy": "MULTI_DEVICE"}),
        200,
    )
    return "SINGLE_DEVICE revoked the other session; policy restored to MULTI_DEVICE"


@case("MT-AUTH-015")
def auth_015(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtpwd")
    username = str(user["username"])
    new_password = f"New{username[-6:]}Pass1!"
    token = ctx.login(username, str(user["password"]))
    require_code(
        ctx.http(
            "POST",
            "/auth/password/change",
            token=token,
            body={"oldPassword": user["password"], "newPassword": new_password},
        ),
        200,
    )
    require(ctx.login_full(username, str(user["password"]))[0] != 200, "old password must fail")
    status, payload = ctx.login_full(username, new_password)
    require(status == 200 and payload.get("code") == "0", f"new password must work: {payload}")
    user["password"] = new_password
    return "password change invalidated the old password and the new password logs in"


@case("MT-AUTH-016")
def auth_016(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtreset")
    username = str(user["username"])
    reset = f"Rst{username[-6:]}Pass1!"
    admin = ctx.token("org_manager")
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/user-admin/users/{user['userId']}/password/reset",
            token=admin,
            body={"newPassword": reset},
        ),
        200,
    )
    status, payload = ctx.login_full(username, reset)
    require(status == 200 and payload.get("code") == "0", f"reset password must work: {payload}")
    user["password"] = reset
    return "admin reset password works and the new password logs in"


@case("MT-AUTH-017")
def auth_017(ctx: Context) -> str:
    data = require_code(ctx.http("GET", "/auth/register/options"), 200)
    require("selfServiceEnabled" in data, f"registration options incomplete: {data}")
    return f"registration options: selfServiceEnabled={data['selfServiceEnabled']}"


@case("MT-AUTH-018")
def auth_018(ctx: Context) -> str:
    options = require_code(ctx.http("GET", "/auth/register/options"), 200)
    if not options.get("selfServiceEnabled"):
        raise CheckBlocked("self-service registration is disabled; requires a restart to enable")
    username = ctx.unique("mtreg")
    require_code(
        ctx.http(
            "POST",
            "/auth/register",
            body={"username": username, "password": "MtPass1!234", "displayName": "MT Register"},
        ),
        200,
    )
    return f"self-service enabled; registered {username}"


@case("MT-AUTH-019")
def auth_019(ctx: Context) -> str:
    options = require_code(ctx.http("GET", "/auth/register/options"), 200)
    if options.get("selfServiceEnabled"):
        raise CheckBlocked(
            "self-service is enabled in this environment; disabling it requires a backend restart"
        )
    username = ctx.unique("mtreg")
    response = ctx.http(
        "POST",
        "/auth/register",
        body={"username": username, "password": "MtPass1!234", "displayName": "MT Register"},
    )
    require(response.status >= 400, f"self-service disabled must reject register: {response.status}")
    return f"self-service disabled; register rejected with HTTP {response.status}"


@case("MT-AUTH-020")
def auth_020(ctx: Context) -> str:
    username = ctx.unique("mtext")
    email = f"{username}@example.local"
    require_code(
        ctx.http(
            "POST",
            "/auth/external-register",
            body={
                "username": username,
                "password": "MtPass1!234",
                "email": email,
                "displayName": "MT External",
            },
        ),
        200,
    )
    status = ctx.sql_one(f"select status from sys_user where username = '{username}'")
    require(status == "3", f"external registration must create PENDING_EMAIL(3), got {status}")
    ctx.store["external_username"] = username
    ctx.store["external_email"] = email
    return f"external registration created {username} with status=3 (PENDING_EMAIL)"


@case("MT-AUTH-021")
def auth_021(ctx: Context) -> str:
    if "external_email" not in ctx.store:
        raise CheckBlocked("MT-AUTH-020 did not run in this session")
    require_code(
        ctx.http(
            "POST",
            "/auth/external-register/resend",
            body={"email": ctx.store["external_email"]},
        ),
        200,
    )
    return "resend activation returned HTTP 200 (mail transport is a no-op without SMTP config)"


@case("MT-AUTH-026")
def auth_026(ctx: Context) -> str:
    response = ctx.http("GET", "/auth/sso/oidc/authorize")
    require(
        response.status >= 400,
        f"unconfigured OIDC must fail closed, got HTTP {response.status}",
    )
    return f"unconfigured OIDC authorize fails with HTTP {response.status}"


@case("MT-AUTH-028")
def auth_028(ctx: Context) -> str:
    wechat = ctx.http(
        "POST",
        "/auth/social/wechat",
        body={"authCode": "mt-invalid-code", "clientId": "mt-web"},
    )
    google = ctx.http(
        "POST",
        "/auth/social/google",
        body={"authCode": "mt-invalid-code", "clientId": "mt-web"},
    )
    if wechat.status == 200 or google.status == 200:
        raise CheckFailure(
            "unconfigured social providers accept arbitrary codes: "
            f"wechat={wechat.status} google={google.status}; "
            "MockWechat/MockGoogle providers are public authentication bypasses"
        )
    return f"both social providers rejected the invalid code (wechat={wechat.status}, google={google.status})"


@case("MT-AUTH-030")
def auth_030(ctx: Context) -> str:
    response = ctx.http("POST", "/auth/qr/scene")
    if response.status == 400:
        raise CheckBlocked(
            "QR login is disabled in this environment (auth-module.qr-login.enabled=false)"
        )
    scene = require_code(response, 200)
    scene_code = scene["sceneCode"]
    token = ctx.token("respondent")
    scanned = require_code(
        ctx.http("POST", "/auth/qr/scan", token=token, body={"sceneCode": scene_code}),
        200,
    )
    require(scanned.get("status") in {"SCANNED", "SCAN"}, f"unexpected scan status: {scanned}")
    confirmed = require_code(
        ctx.http("POST", "/auth/qr/confirm", token=token, body={"sceneCode": scene_code}),
        200,
    )
    require(confirmed.get("status") in {"CONFIRMED", "APPROVED"}, f"unexpected confirm status: {confirmed}")
    consumed = require_code(ctx.http("GET", f"/auth/qr/scene/{scene_code}"), 200)
    auth = consumed.get("auth") or {}
    require(bool(auth.get("accessToken")), f"consumed QR scene must return tokens: {consumed}")
    replay = ctx.http("GET", f"/auth/qr/scene/{scene_code}")
    require(replay.status != 200 or not (replay.data() or {}).get("auth"), "QR scene must be one-time")
    return "QR scene created, scanned, confirmed, consumed with tokens and rejected on replay"


@case("MT-AUTH-031")
def auth_031(ctx: Context) -> str:
    devices = require_code(ctx.http("GET", "/auth/me/devices", token=ctx.token("respondent")), 200)
    require(isinstance(devices, list), f"device list must be an array: {devices}")
    require(
        all("deviceId" in item for item in devices),
        f"device entries must expose deviceId: {devices}",
    )
    return f"{len(devices)} device(s) returned for respondent"


@case("MT-AUTH-032")
def auth_032(ctx: Context) -> str:
    token = ctx.token("respondent")
    device_id = ctx.unique("mt-device-")
    require_code(
        ctx.http(
            "POST",
            "/auth/me/devices",
            token=token,
            body={
                "deviceType": "ANDROID",
                "deviceId": device_id,
                "pushToken": "mt-push-token",
                "appVersion": "mt-1.0",
            },
        ),
        200,
    )
    count = ctx.sql_one(
        f"select count(*) from psy_user_device where device_id = '{device_id}' and active_flag"
    )
    require(count == "1", f"device must be registered once, got count={count}")
    ctx.store["mt_device_id"] = device_id
    return f"device {device_id} registered exactly once"


@case("MT-AUTH-033")
def auth_033(ctx: Context) -> str:
    device_id = ctx.store.get("mt_device_id")
    if not device_id:
        raise CheckBlocked("MT-AUTH-032 did not run in this session")
    require_code(
        ctx.http(
            "POST",
            f"/auth/me/devices/{device_id}/deactivate",
            token=ctx.token("respondent"),
        ),
        200,
    )
    active = ctx.sql_one(
        f"select active_flag from psy_user_device where device_id = '{device_id}'"
    )
    require(active == "f", f"device must be inactive, got {active}")
    return f"device {device_id} deactivated (active_flag=false)"


@case("MT-AUTH-034")
def auth_034(ctx: Context) -> str:
    activities = require_code(
        ctx.http("GET", "/auth/me/login-activities", token=ctx.token("respondent")),
        200,
    )
    require(isinstance(activities, list), f"login activities must be a list: {activities}")
    return f"{len(activities)} login activity row(s) returned"


@case("MT-AUTH-035")
def auth_035(ctx: Context) -> str:
    ctx.login_full("respondent", "WrongPass1!")
    events = require_code(
        ctx.http("GET", "/auth/me/security-events", token=ctx.token("respondent")),
        200,
    )
    require(isinstance(events, list), f"security events must be a list: {events}")
    return f"{len(events)} security event row(s) returned after a failed login"


@case("MT-AUTH-036")
def auth_036(ctx: Context) -> str:
    data = require_code(
        ctx.http("GET", "/auth/login-logs?page=1&size=5", token=ctx.token("org_manager")),
        200,
    )
    rows = data.get("list") if isinstance(data, dict) else data
    require(isinstance(rows, list), f"login logs shape unexpected: {data}")
    return f"org_manager read {len(rows)} login log row(s)"


@case("MT-AUTH-037")
def auth_037(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtkick")
    token = ctx.login(str(user["username"]), str(user["password"]), device_id="mt-kick-a")
    require_code(
        ctx.http(
            "POST",
            f"/auth/users/{user['userId']}/sessions/revoke-all",
            token=ctx.token("org_manager"),
        ),
        200,
    )
    after = ctx.http("GET", "/auth/me", token=token)
    require(after.status == 401, f"revoked-all sessions must reject the token: {after.status}")
    return "admin revoke-all invalidated the target user's access token"


@case("MT-AUTH-038")
def auth_038(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtdev")
    token = ctx.login(str(user["username"]), str(user["password"]))
    device_id = ctx.unique("mt-admin-device-")
    require_code(
        ctx.http(
            "POST",
            "/auth/me/devices",
            token=token,
            body={"deviceType": "ANDROID", "deviceId": device_id, "pushToken": "mt-token"},
        ),
        200,
    )
    require_code(
        ctx.http(
            "POST",
            f"/auth/users/{user['userId']}/devices/{device_id}/deactivate",
            token=ctx.token("org_manager"),
        ),
        200,
    )
    active = ctx.sql_one(
        f"select active_flag from psy_user_device where device_id = '{device_id}'"
    )
    require(active == "f", f"admin deactivate must set active_flag=false, got {active}")
    return f"admin deactivated device {device_id}"


@case("MT-AUTH-039")
def auth_039(ctx: Context) -> str:
    token = ctx.token("sysadmin")
    checks = ["/auth/permissions", "/auth/roles", "/auth/groups", "/auth/tenants"]
    for path in checks:
        require_code(ctx.http("GET", path, token=token), 200)
    return "sysadmin can read permissions, roles, groups and tenants"


@case("MT-AUTH-040")
def auth_040(ctx: Context) -> str:
    for path in ("/auth/me", "/api/v1/my/tasks", "/api/v1/reports/my"):
        response = ctx.http("GET", path)
        require(response.status == 401, f"anonymous {path} must be 401, got {response.status}")
    return "anonymous /auth/me, /api/v1/my/tasks and /api/v1/reports/my all return 401"


# --------------------------------------------------------------------------
# USER
# --------------------------------------------------------------------------


@case("MT-USER-001")
def user_001(ctx: Context) -> str:
    token = ctx.token("org_manager")
    data = require_code(
        ctx.http("GET", "/api/v1/user-admin/users?page=1&size=5", token=token),
        200,
    )
    rows = data.get("list", [])
    require(rows, f"user page must not be empty: {data}")
    tenant_ids = {row.get("tenantId") for row in rows}
    require(len(tenant_ids) == 1, f"org_manager must only see its own tenant: {tenant_ids}")
    return f"{len(rows)} user(s) returned for tenant {tenant_ids.pop()}"


@case("MT-USER-002")
def user_002(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtcreate")
    status, payload = ctx.login_full(str(user["username"]), str(user["password"]))
    require(status == 200 and payload.get("code") == "0", f"created user must log in: {payload}")
    return f"created {user['username']} (userId={user['userId']}) and it can log in"


@case("MT-USER-003")
def user_003(ctx: Context) -> str:
    token = ctx.token("org_manager")
    groups = require_code(ctx.http("GET", "/api/v1/user-admin/groups", token=token), 200)
    group_id = groups[0]["groupId"]
    invalid = ["short1!", "alllower1!", "ALLUPPER1!", "NoNumber!", "NoSpecial1"]
    for index, password in enumerate(invalid):
        response = ctx.http(
            "POST",
            "/api/v1/user-admin/users",
            token=token,
            body={
                "username": ctx.unique(f"mtbad{index}"),
                "password": password,
                "displayName": "MT Bad Password",
                "groupId": group_id,
                "roleCodes": ["USER"],
            },
        )
        require(
            response.status == 400,
            f"invalid password {password!r} must be rejected with 400, got {response.status}",
        )
    return f"all {len(invalid)} invalid passwords rejected with HTTP 400"


@case("MT-USER-004")
def user_004(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtrole")
    token = ctx.token("org_manager")
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/user-admin/users/{user['userId']}/roles",
            token=token,
            body={"roleCodes": ["COUNSELOR"]},
        ),
        200,
    )
    roles = ctx.sql(
        "select r.role_code from sys_user_role ur join sys_role r on r.id = ur.role_id "
        f"where ur.user_id = {user['userId']} order by r.role_code"
    ).splitlines()
    require("COUNSELOR" in roles, f"COUNSELOR role missing after assignment: {roles}")
    return f"roles after assignment: {roles}"


@case("MT-USER-005")
def user_005(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtstatus")
    token = ctx.token("org_manager")
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/user-admin/users/{user['userId']}/status",
            token=token,
            body={"enabled": False},
        ),
        200,
    )
    require(
        ctx.login_full(str(user["username"]), str(user["password"]))[0] != 200,
        "disabled user must not log in",
    )
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/user-admin/users/{user['userId']}/status",
            token=token,
            body={"enabled": True},
        ),
        200,
    )
    status, payload = ctx.login_full(str(user["username"]), str(user["password"]))
    require(status == 200 and payload.get("code") == "0", "re-enabled user must log in")
    return "disable blocked login; enable restored login"


@case("MT-USER-006")
def user_006(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtreset2")
    new_password = f"Urs{str(user['username'])[-6:]}Pass1!"
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/user-admin/users/{user['userId']}/password/reset",
            token=ctx.token("org_manager"),
            body={"newPassword": new_password},
        ),
        200,
    )
    status, payload = ctx.login_full(str(user["username"]), new_password)
    require(status == 200 and payload.get("code") == "0", f"reset password must work: {payload}")
    return "reset password works for the target user"


@case("MT-USER-007")
def user_007(ctx: Context) -> str:
    rows = require_code(
        ctx.http("GET", "/api/v1/user-admin/tenants", token=ctx.token("org_manager")),
        200,
    )
    codes = {row["tenantCode"] for row in rows}
    require("DEFAULT" in codes, f"own tenant missing: {codes}")
    require(
        "CAMPUS_DEMO" not in codes,
        f"tenant-bound org_manager must not list other tenants: {codes}",
    )
    return f"org_manager sees only {codes}"


@case("MT-USER-008")
def user_008(ctx: Context) -> str:
    rows = require_code(
        ctx.http("GET", "/api/v1/user-admin/groups", token=ctx.token("org_manager")),
        200,
    )
    codes = {row["groupCode"] for row in rows}
    require("DEFAULT_GENERAL" in codes, f"seed groups missing: {codes}")
    require(any(row.get("parentId") for row in rows), "expected at least one child group")
    return f"{len(rows)} groups returned incl. parent/child"


@case("MT-USER-009")
def user_009(ctx: Context) -> str:
    rows = require_code(
        ctx.http("GET", "/api/v1/user-admin/roles", token=ctx.token("org_manager")),
        200,
    )
    codes = {row["roleCode"] for row in rows}
    require({"ASSESSMENT_ADMIN", "COUNSELOR"} <= codes, f"roles missing: {codes}")
    return f"{len(rows)} roles returned: {sorted(codes)}"


@case("MT-USER-010")
def user_010(ctx: Context) -> str:
    data = require_code(
        ctx.http(
            "GET",
            "/api/v1/user-admin/users?page=1&size=20&username=campus_student",
            token=ctx.token("org_manager"),
        ),
        200,
    )
    rows = data.get("list", [])
    require(not rows, f"cross-tenant user leaked into DEFAULT list: {rows}")
    return "campus_student is not visible to the DEFAULT tenant org_manager"


@case("MT-USER-011")
def user_011(ctx: Context) -> str:
    token = ctx.token("sysadmin")
    tenant_code = ctx.unique("MTTENANT").upper()
    tenant = require_code(
        ctx.http(
            "POST",
            "/auth/tenants",
            token=token,
            body={"tenantCode": tenant_code, "tenantName": "MT Test Tenant"},
        ),
        200,
    )
    group_code = ctx.unique("MTGROUP").upper()
    require_code(
        ctx.http(
            "POST",
            "/auth/groups",
            token=token,
            body={"groupCode": group_code, "groupName": "MT Test Group"},
        ),
        200,
    )
    require(
        ctx.sql_one(f"select count(*) from sys_group where group_code = '{group_code}'") == "1",
        "created group row missing",
    )
    return f"created tenant {tenant_code} and group {group_code} (left in local dev DB)"


@case("MT-USER-012")
def user_012(ctx: Context) -> str:
    token = ctx.token("sysadmin")
    group_code = ctx.unique("MTGROL").upper()
    require_code(
        ctx.http(
            "POST",
            "/auth/groups",
            token=token,
            body={"groupCode": group_code, "groupName": "MT Role Group"},
        ),
        200,
    )
    group_id = ctx.sql_one(f"select id from sys_group where group_code = '{group_code}'")
    require_code(
        ctx.http(
            "POST",
            f"/auth/groups/{group_id}/roles",
            token=token,
            body={"roleCodes": ["USER"]},
        ),
        200,
    )
    count = ctx.sql_one(f"select count(*) from sys_group_role where group_id = {group_id}")
    require(int(count) >= 1, f"group roles not persisted: {count}")
    return f"group {group_code} assigned USER role"


# --------------------------------------------------------------------------
# SEC
# --------------------------------------------------------------------------


@case("MT-SEC-003")
def sec_003(ctx: Context) -> str:
    probes = [
        ("respondent", "/api/v1/scales"),
        ("respondent", "/api/v1/tasks"),
        ("respondent", "/api/v1/warnings"),
        ("respondent", "/api/v1/user-admin/users"),
        ("counselor", "/api/v1/scales"),
        ("assessor", "/api/v1/user-admin/users"),
        ("org_manager", "/api/v1/scales"),
    ]
    failures = []
    for user, path in probes:
        status = ctx.http("GET", path, token=ctx.token(user)).status
        if status != 403:
            failures.append(f"{user}->{path}={status}")
    extra: list[str] = []
    for path in ("/auth/login-logs", "/auth/security-events"):
        status = ctx.http("GET", path, token=ctx.token("respondent")).status
        if status == 200:
            extra.append(f"respondent->{path}=200")
    require(not failures, f"role negative failures: {failures}")
    if extra:
        raise CheckFailure(
            "audit endpoints are reachable by respondent without @PreAuthorize: " + ", ".join(extra)
        )
    return f"all {len(probes)} role negatives returned 403; audit endpoints denied"


@case("MT-SEC-004")
def sec_004(ctx: Context) -> str:
    scales = require_code(
        ctx.http("GET", "/api/v1/scales?page=1&size=50", token=ctx.token("assessor")),
        200,
    )
    scale_id = scales["list"][0]["id"]
    response = ctx.http("GET", f"/api/v1/scales/{scale_id}", token=ctx.token("campus_assessor"))
    require(response.status == 404, f"cross-tenant scale must be 404, got {response.status}")
    return f"campus_assessor cannot read DEFAULT scale {scale_id} (HTTP 404)"


@case("MT-SEC-005")
def sec_005(ctx: Context) -> str:
    default_task = ctx.sql_one(
        "select t.id from psy_assessment_task t join sys_tenant tenant on tenant.id = t.tenant_id "
        "where tenant.tenant_code = 'DEFAULT' order by t.id limit 1"
    )
    response = ctx.http(
        "GET",
        f"/api/v1/tasks/{default_task}",
        token=ctx.token("campus_assessor"),
    )
    require(response.status == 404, f"cross-tenant task must be 404, got {response.status}")
    return f"campus_assessor cannot read DEFAULT task {default_task} (HTTP 404)"


@case("MT-SEC-006")
def sec_006(ctx: Context) -> str:
    default_report = ctx.sql_one(
        "select p.id from psy_report p join psy_assessment_result r on r.id = p.result_id "
        "join psy_assessment_answer_sheet s on s.id = r.answer_sheet_id "
        "join sys_tenant tenant on tenant.id = s.tenant_id "
        "where tenant.tenant_code = 'DEFAULT' order by p.id limit 1"
    )
    response = ctx.http(
        "GET",
        f"/api/v1/reports/{default_report}",
        token=ctx.token("campus_counselor"),
    )
    require(response.status == 404, f"cross-tenant report must be 404, got {response.status}")
    return f"campus_counselor cannot read DEFAULT report {default_report} (HTTP 404)"


@case("MT-SEC-007")
def sec_007(ctx: Context) -> str:
    warning_id = ctx.sql_one(
        "select w.id from psy_warning_record w join sys_tenant tenant on tenant.id = w.tenant_id "
        "where tenant.tenant_code = 'ENTERPRISE_DEMO' order by w.id limit 1"
    )
    response = ctx.http(
        "POST",
        f"/api/v1/warnings/{warning_id}/claim",
        token=ctx.token("campus_counselor"),
    )
    require(response.status == 404, f"cross-tenant warning claim must be 404, got {response.status}")
    return f"campus_counselor cannot claim ENTERPRISE warning {warning_id} (HTTP 404)"


@case("MT-SEC-008")
def sec_008(ctx: Context) -> str:
    report_id = ctx.sql_one(
        "select p.id from psy_report p join psy_assessment_result r on r.id = p.result_id "
        "join psy_assessment_answer_sheet s on s.id = r.answer_sheet_id "
        "join sys_tenant tenant on tenant.id = s.tenant_id "
        "where tenant.tenant_code = 'DEFAULT' order by p.id limit 1"
    )
    job = require_code(
        ctx.http(
            "POST",
            "/api/v1/exports/reports/jobs",
            token=ctx.token("assessor"),
            body={"reportId": int(report_id), "exportFormat": "TEXT", "desensitized": True},
        ),
        200,
    )
    response = ctx.http(
        "GET",
        f"/api/v1/exports/reports/jobs/{job['jobId']}",
        token=ctx.token("enterprise_manager"),
    )
    require(response.status == 404, f"cross-tenant export job must be 404, got {response.status}")
    return f"created job {job['jobId']}; cross-tenant read returned HTTP 404"


@case("MT-SEC-009")
def sec_009(ctx: Context) -> str:
    raise CheckBlocked(
        "requires a tenantless SYS_ADMIN/SUPER_ADMIN token; seed admins are tenant-bound"
    )


@case("MT-SEC-011")
def sec_011(ctx: Context) -> str:
    for user, path, expected in (
        ("respondent", "/api/v1/scales", 403),
        ("assessor", "/api/v1/does-not-exist", 404),
        (None, "/api/v1/reports/my", 401),
    ):
        token = ctx.token(user) if user else None
        response = ctx.http("GET", path, token=token)
        require(response.status == expected, f"{path} expected {expected}, got {response.status}")
        payload = response.payload
        require(isinstance(payload, dict), f"{path} must return a JSON envelope: {payload}")
        require(
            {"code", "message", "data"} <= set(payload.keys()),
            f"{path} envelope missing fields: {payload}",
        )
        require(
            "Exception" not in str(payload) and "at org." not in str(payload),
            f"{path} leaks a stack trace: {payload}",
        )
    return "401/403/404 responses use {code,message,data} without stack traces"


@case("MT-SEC-015")
def sec_015(ctx: Context) -> str:
    response = ctx.http(
        "POST",
        "/api/v1/notifications/deliveries/1/callbacks",
        body={"status": "SENT"},
    )
    require(response.status in (401, 403), f"anonymous callback must be denied: {response.status}")
    return f"anonymous delivery callback rejected with HTTP {response.status}"


@case("MT-SEC-016")
def sec_016(ctx: Context) -> str:
    user = ensure_temp_user(ctx, prefix="mtbrute")
    username = str(user["username"])
    for _ in range(5):
        ctx.login_full(username, "WrongPass1!")
    locked = ctx.sql_one(
        f"select coalesce(locked_until::text, '') from sys_user where username = '{username}'"
    )
    require(bool(locked), "brute-force lock was not persisted")
    unlock_user(ctx, username)
    return f"brute force locked the account until {locked}"


@case("MT-SEC-017")
def sec_017(ctx: Context) -> str:
    claims = int(
        ctx.sql_one(
            "select count(*) from sys_security_event where event_type = 'PSY_WARNING_CLAIMED'"
        )
    )
    require(claims >= 1, f"warning claim must be audited, found {claims} events")
    return f"{claims} PSY_WARNING_CLAIMED audit event(s) present"


@case("MT-SEC-018")
def sec_018(ctx: Context) -> str:
    user = ensure_temp_user(ctx, roles=("COUNSELOR", "ASSESSMENT_ADMIN"), prefix="mtcombo")
    token = ctx.login(str(user["username"]), str(user["password"]))
    profile = require_code(ctx.http("GET", "/auth/me", token=token), 200)
    roles = set(profile.get("roles") or [])
    require({"COUNSELOR", "ASSESSMENT_ADMIN"} <= roles, f"roles missing: {roles}")
    require_code(ctx.http("GET", "/api/v1/warnings", token=token), 200)
    require_code(ctx.http("GET", "/api/v1/scales", token=token), 200)
    return f"combined roles {sorted(roles)} can access both warning and scale endpoints"


@case("MT-SEC-020")
def sec_020(ctx: Context) -> str:
    token = ctx.token("respondent")
    original = require_code(ctx.http("GET", "/api/v1/my/profile", token=token), 200)
    payload_text = "<script>alert(1)</script>"
    profile_body = {
        "nickname": original.get("nickname"),
        "email": original.get("email"),
        "mobile": original.get("mobile"),
        "avatarUrl": original.get("avatarUrl"),
        "displayName": payload_text,
    }
    require_code(ctx.http("POST", "/api/v1/my/profile", token=token, body=profile_body), 200)
    stored = ctx.sql_one(
        "select display_name from sys_user where username = 'respondent'"
    )
    require(
        stored == payload_text,
        f"payload must be stored literally (parameterised SQL), got {stored!r}",
    )
    profile_body["displayName"] = original.get("displayName") or "Default Respondent"
    require_code(ctx.http("POST", "/api/v1/my/profile", token=token, body=profile_body), 200)
    return "script payload stored literally and restored afterwards"


# --------------------------------------------------------------------------
# I18N (static + API)
# --------------------------------------------------------------------------


def message_keys(path: Path) -> set[str]:
    pattern = re.compile(r"^[^#\s][^=]*=", re.MULTILINE)
    return {match.group(0).split("=", 1)[0].strip() for match in pattern.finditer(path.read_text(encoding="utf-8"))}


@case("MT-I18N-008")
def i18n_008(ctx: Context) -> str:
    files = [
        ROOT / "backend/src/main/resources/i18n/messages.properties",
        ROOT / "backend/src/main/resources/i18n/messages_zh_CN.properties",
        ROOT / "backend/src/main/resources/i18n/messages_ja_JP.properties",
    ]
    keys = [message_keys(path) for path in files]
    sizes = [len(item) for item in keys]
    require(sizes == [494, 494, 494], f"expected 494 keys per catalog, got {sizes}")
    require(keys[0] == keys[1] == keys[2], "backend locale key sets differ")
    return "backend catalogs: 494/494/494 keys with identical key sets"


@case("MT-I18N-007")
def i18n_007(ctx: Context) -> str:
    token = ctx.token("respondent")
    japanese = ctx.http(
        "GET",
        "/api/v1/scales",
        token=token,
        headers={"Accept-Language": "ja-JP"},
    )
    chinese = ctx.http(
        "GET",
        "/api/v1/scales",
        token=token,
        headers={"Accept-Language": "zh-CN"},
    )
    require(japanese.status == 403 and chinese.status == 403, "both probes must be 403")
    ja_message = str(japanese.payload.get("message", ""))
    zh_message = str(chinese.payload.get("message", ""))
    require(any("\u3040" <= ch <= "\u30ff" for ch in ja_message), f"ja message not localized: {ja_message}")
    require(any("\u4e00" <= ch <= "\u9fff" for ch in zh_message), f"zh message not localized: {zh_message}")
    return f"ja='{ja_message}' zh='{zh_message}' with stable code {japanese.code()}"


@case("MT-I18N-003")
def i18n_003(ctx: Context) -> str:
    source = (ROOT / "admin-web/src/i18n/messages.ts").read_text(encoding="utf-8")
    required = [
        "warnings.level.high",
        "warning.priority.P1",
        "warnings.status.pending",
        "scales.questionType.time",
    ]
    missing = [key for key in required if f'"{key}"' not in source]
    require(not missing, f"enum label keys missing: {missing}")
    return f"enum label keys present: {required}"
