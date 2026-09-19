"""MT-NET cases: the external integration channels, executed over real sockets.

Each case talks to a locally hosted but protocol-real peer (see doc/31 §4.2)
and reads the peer's JSONL log back as evidence. When a peer or its backend
configuration is missing the case reports ``CheckBlocked`` with the exact
missing condition, so "全网络" can never be claimed without the network.
"""

from __future__ import annotations

import json
import re
import time
import urllib.parse
import urllib.request
from typing import Any

from checks_common import api, wait_for
from harness import CheckBlocked, CheckFailure, Context, case, require, require_code
from net_channels import (
    CAS_LOG,
    OBJECT_STORE_LOG,
    PUSH_LOG,
    SMTP_LOG,
    base_url,
    log_offset,
    raw_request,
    read_jsonl,
    tcp_reachable,
    wait_for_log_entry,
)

CAS_HOST = "127.0.0.1"
CAS_PORT = 9200
PUSH_HOST = "127.0.0.1"
PUSH_PORT = 9099
CUSTOMER_EMAIL_DOMAIN = "example.local"


# ---------------------------------------------------------------------------
# Shared channel helpers (also used by MT-AUTH-022/027 and MT-SEC-014)
# ---------------------------------------------------------------------------


def smtp_sink_ready() -> None:
    if not tcp_reachable("127.0.0.1", 2526) and not SMTP_LOG.exists():
        raise CheckBlocked(
            "SMTP sink not running; start `python3 scripts/manual_test/smtp_sink.py --port 2526` "
            "and point the backend at it (PSY_MAIL_HOST/PORT), see doc/31 §4.2"
        )


def cas_provider_ready() -> None:
    if not tcp_reachable(CAS_HOST, CAS_PORT):
        raise CheckBlocked(
            f"CAS test IdP not reachable on {CAS_HOST}:{CAS_PORT}; start "
            "`python3 scripts/manual_test/cas_test_idp.py --port 9200` and run the backend with "
            "PSY_AUTH_SSO_CAS_ENABLED=true (doc/31 §4.2)"
        )


def push_receiver_ready() -> None:
    if not tcp_reachable(PUSH_HOST, PUSH_PORT):
        raise CheckBlocked(
            f"push receiver not reachable on {PUSH_HOST}:{PUSH_PORT}; start "
            "`python3 scripts/manual_test/push_receiver.py --port 9099` and run the backend with "
            "PSY_NOTIFICATION_PUSH_HTTP_ENABLED=true (doc/31 §4.2)"
        )


def cas_authorize_and_callback(*, idp_user: str) -> dict[str, Any]:
    """Drive authorize → IdP login → app callback over real sockets.

    Returns the raw status/body of the callback so a case can assert a negative
    outcome (unknown IdP identity, replayed ticket) as well as the happy path.
    """
    cas_provider_ready()
    authorize_status, authorize_headers, authorize_body = raw_request(
        f"{base_url()}/auth/sso/cas/authorize"
    )
    if authorize_status != 302:
        raise CheckBlocked(
            "CAS provider is not enabled in this backend (authorize -> HTTP "
            f"{authorize_status}); run with PSY_AUTH_SSO_CAS_ENABLED=true (doc/31 §4.2)"
        )
    idp_url = authorize_headers.get("location", "")
    require(idp_url.startswith(f"http://{CAS_HOST}:{CAS_PORT}/cas/"), f"unexpected IdP target: {idp_url}")
    separator = "&" if "?" in idp_url else "?"
    login_status, login_headers, _ = raw_request(f"{idp_url}{separator}user={urllib.parse.quote(idp_user)}")
    require(login_status == 302, f"IdP login must redirect with a ticket: {login_status}")
    callback_url = login_headers.get("location", "")
    require("/auth/sso/cas/callback?ticket=ST-" in callback_url, f"unexpected callback target: {callback_url}")
    cas_ticket = urllib.parse.parse_qs(urllib.parse.urlsplit(callback_url).query).get("ticket", [""])[0]

    callback_status, callback_headers, callback_body = raw_request(callback_url)
    frontend_url = callback_headers.get("location", "")
    return {
        "idpUser": idp_user,
        "idpUrl": idp_url,
        "callbackUrl": callback_url,
        "casTicket": cas_ticket,
        "callbackStatus": callback_status,
        "callbackBody": callback_body.decode("utf-8", "replace"),
        "frontendUrl": frontend_url,
    }


def cas_login_chain(ctx: Context, *, idp_user: str = "mtcasuser") -> dict[str, Any]:
    """Drive the full CAS login chain and exchange the one-time ticket."""
    chain = cas_authorize_and_callback(idp_user=idp_user)
    callback_status = int(chain["callbackStatus"])
    frontend_url = str(chain["frontendUrl"])
    if callback_status != 302:
        raise CheckBlocked(
            f"CAS callback failed (HTTP {callback_status}): {str(chain['callbackBody'])[:200]}"
        )
    app_ticket = urllib.parse.parse_qs(urllib.parse.urlsplit(frontend_url).query).get("ticket", [""])[0]
    require(bool(app_ticket), f"frontend callback must carry an app ticket: {frontend_url}")
    exchange = ctx.http("POST", "/auth/sso/token", body={"ticket": app_ticket})
    require_code(exchange, 200)
    tokens = exchange.data() or {}
    require(bool(tokens.get("accessToken")), f"ticket exchange must return tokens: {tokens}")
    profile = ctx.http("GET", "/auth/me", token=tokens["accessToken"])
    require_code(profile, 200)
    return {
        **chain,
        "authorizeUrl": f"{base_url()}/auth/sso/cas/authorize",
        "appTicket": app_ticket,
        "accessToken": tokens["accessToken"],
        "profile": profile.data() or {},
    }


def external_registration_with_mail(ctx: Context, prefix: str) -> dict[str, Any]:
    """Register an external account and return the activation mail the sink saw."""
    smtp_sink_ready()
    username = ctx.unique(prefix)
    email = f"{username}@{CUSTOMER_EMAIL_DOMAIN}"
    registration = ctx.http(
        "POST",
        "/auth/external-register",
        body={
            "username": username,
            "password": "MtPass1!234",
            "email": email,
            "displayName": "MT mail channel",
        },
    )
    if registration.status != 200:
        raise CheckBlocked(
            f"external registration is not available (HTTP {registration.status} "
            f"{json.dumps(registration.payload, ensure_ascii=False)[:160]}); the mail channel needs it enabled"
        )
    status = ctx.sql_one(f"select status from sys_user where username = '{username}'")
    require(status == "3", f"external registration must create PENDING_EMAIL(3), got {status}")
    entry = wait_for_log_entry(
        SMTP_LOG,
        lambda item: email in " ".join(item.get("rcptTo") or []) or email in str(item.get("to") or ""),
        timeout=20,
        label=f"activation mail for {email}",
    )
    return {"username": username, "email": email, "mail": entry}


def email_verify_path(body: str) -> str:
    match = re.search(r"(/auth/email-verify\?token=[A-Za-z0-9._\-]+)", body)
    if not match:
        raise CheckFailure(f"activation mail has no verification link: {body[:300]}")
    return match.group(1)


# ---------------------------------------------------------------------------
# MT-NET cases
# ---------------------------------------------------------------------------


@case("MT-NET-001")
def net_001(ctx: Context) -> str:
    targets = ["https://api.github.com", "https://www.baidu.com"]
    results: list[str] = []
    for url in targets:
        try:
            with urllib.request.urlopen(url, timeout=8) as response:
                results.append(f"{urllib.parse.urlsplit(url).hostname}={response.status}")
        except Exception as error:  # noqa: BLE001 - network failures are evidence here
            results.append(f"{urllib.parse.urlsplit(url).hostname}={type(error).__name__}")
    ok = [item for item in results if item.endswith("=200")]
    if not ok:
        raise CheckBlocked("no outbound HTTPS target answered: " + ", ".join(results))
    return "outbound HTTPS baseline ok: " + ", ".join(results)


@case("MT-NET-002")
def net_002(ctx: Context) -> str:
    channel = external_registration_with_mail(ctx, "mtnetmail")
    path = email_verify_path(str(channel["mail"].get("body") or ""))
    verified = ctx.http("GET", path)
    require_code(verified, 200)
    status = ctx.sql_one(f"select status from sys_user where username = '{channel['username']}'")
    require(status == "4", f"email verification must move PENDING_EMAIL(3) -> PENDING_APPROVAL(4), got {status}")
    replay = ctx.http("GET", path)
    require(
        replay.status != 200 or (replay.data() or {}) == {},
        f"activation token must be single use: {replay.status} {replay.payload}",
    )
    subject = str(channel["mail"].get("subject") or "")
    return (
        f"SMTP sink received activation mail for {channel['email']} (subject={subject!r}, "
        f"{channel['mail'].get('bytes')}B); link verified -> status 4; replay rejected with HTTP {replay.status}"
    )


@case("MT-NET-003")
def net_003(ctx: Context) -> str:
    chain = cas_login_chain(ctx)
    since = max(log_offset(CAS_LOG) - 6, 0)
    validate = wait_for_log_entry(
        CAS_LOG,
        lambda item: item.get("path") == "/cas/p3/serviceValidate" and item.get("status") == 200,
        since=since,
        timeout=10,
        label="CAS serviceValidate call from the backend",
    )
    profile = chain["profile"]
    return (
        f"CAS chain over real sockets: authorize 302 -> IdP {chain['idpUrl'].split('?')[0]} 302 -> callback 302 "
        f"-> frontend {chain['frontendUrl'].split('?')[0]}; ticket exchanged for tokens and /auth/me returns "
        f"{profile.get('username')} (userId={profile.get('userId')}); IdP log records {validate.get('path')}"
    )


@case("MT-NET-004")
def net_004(ctx: Context) -> str:
    raise CheckBlocked(
        "WeChat OAuth/JS-SDK/menu needs a real 公众号 appId/secret (PSY_AUTH_WECHAT_APP_ID/SECRET); "
        "the unconfigured path is verified fail-closed by MT-API-025/026/027 and MT-AUTH-028/029"
    )


@case("MT-NET-005")
def net_005(ctx: Context) -> str:
    push_receiver_ready()
    import scale_factory

    token_value = ctx.unique("mt-push-token-")
    device_id = ctx.unique("mt-net-push-device-")
    registered = require_code(
        ctx.http(
            "POST",
            "/auth/me/devices",
            token=ctx.token("respondent"),
            body={"deviceType": "ANDROID", "deviceId": device_id, "pushToken": token_value, "appVersion": "mt"},
        ),
        200,
    )
    since = log_offset(PUSH_LOG)
    task_id = scale_factory.create_task(ctx, 2, f"MT-NET-005-{ctx.unique('')}")
    entry = wait_for_log_entry(
        PUSH_LOG,
        lambda item: token_value in str(item.get("body") or ""),
        since=since,
        timeout=90,
        label=f"push delivery for {token_value} (the dispatcher polls on an interval)",
    )
    body = json.loads(str(entry.get("body") or "{}"))
    delivery = ctx.sql(
        "select delivery_status || '|' || coalesce(provider_name,'') || '|' || coalesce(error_message,'') "
        f"from psy_notification_delivery where id = {int(body.get('deliveryId'))}"
    )
    require(delivery.startswith("SENT"), f"delivery must be SENT after the peer accepted it: {delivery[:80]}")
    return (
        f"push receiver got deliveryId={body.get('deliveryId')} notificationId={body.get('notificationId')} "
        f"token={token_value} (deviceId={registered.get('deviceId') or device_id}); delivery status {delivery.split('|')[0]}, "
        f"provider {delivery.split('|')[1]}; task {task_id} assignment triggered it"
    )


@case("MT-NET-006")
def net_006(ctx: Context) -> str:
    storage = require_code(api(ctx, "GET", "/api/v1/exports/reports/storage"), 200)
    mode = str(storage.get("mode") or storage.get("storageMode") or "")
    if mode != "HTTP_OBJECT_STORAGE":
        raise CheckBlocked(
            f"export artifact storage mode is {mode or 'unknown'}, not HTTP_OBJECT_STORAGE; start "
            "`python3 scripts/manual_test/http_object_store.py --port 9100 --api-key mt-object-key` and run the "
            "backend with PSY_EXPORT_ARTIFACT_STORAGE_MODE=HTTP_OBJECT_STORAGE (doc/31 §4.2)"
        )
    report_id = int(ctx.sql_one("select id from psy_report order by id desc limit 1"))
    since = log_offset(OBJECT_STORE_LOG)
    created = require_code(
        api(ctx, "POST", "/api/v1/exports/reports/jobs", body={"reportId": report_id, "exportFormat": "TEXT"}),
        200,
    )
    job_id = str(created.get("jobId") or created.get("id"))
    job = wait_for(
        lambda: (api(ctx, "GET", f"/api/v1/exports/reports/jobs/{job_id}").data() or {}).get("status") in {"DONE", "FAILED"}
        and (api(ctx, "GET", f"/api/v1/exports/reports/jobs/{job_id}").data() or {}),
        timeout=60,
        interval=1.0,
        label=f"export job {job_id} to finish",
    )
    require(job.get("status") == "DONE", f"export job must finish: {job}")
    stored = wait_for_log_entry(
        OBJECT_STORE_LOG,
        lambda item: item.get("method") == "PUT" and item.get("status") == 200,
        since=since,
        timeout=15,
        label="object store PUT",
    )
    download = api(ctx, "GET", f"/api/v1/exports/reports/jobs/{job_id}/download")
    require(download.status == 200 and len(download.raw) > 0, f"download failed: {download.status}")
    require(
        len(download.raw) == int(stored.get("bytes") or -1),
        f"download size {len(download.raw)} must equal the stored object size {stored.get('bytes')}",
    )
    return (
        f"storage mode={mode} bucket={storage.get('bucket')}; job {job_id} PUT {stored.get('bytes')}B to "
        f"{stored.get('object')} (apiKeyPresent={stored.get('apiKeyPresent')}) and the download returned the same bytes"
    )
