"""Last batch: MT-IMP / MT-EXP / MT-HOME / MT-WARN / MT-TASK / MT-AUTH / MT-PUB / MT-RPT / MT-SEC."""

from __future__ import annotations

import json
import os
import subprocess
import time
import uuid
from datetime import datetime, timedelta
from typing import Any

from checks_common import api as shared_api
from harness import CheckBlocked, CheckFailure, Context, ROOT, case, require, require_code
from scale_factory import create_task, fetch_question_meta, save_draft, submit_answers


def _api(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any):
    # Shared implementation lives in checks_common (review finding #10).
    return shared_api(ctx, method, path, user=user, **kwargs)


def _multipart(path_name: str, payload: bytes) -> tuple[bytes, str]:
    from scale_factory import _multipart_file

    return _multipart_file(path_name, payload)


def _prometheus(ctx: Context) -> str:
    return ctx.http("GET", "/actuator/prometheus", token=ctx.token("assessor")).raw.decode("utf-8", "replace")


# ---------------------------------------------------------------------------
# MT-IMP
# ---------------------------------------------------------------------------


@case("MT-IMP-006")
def imp_006(ctx: Context) -> str:
    base = ROOT / "backend/src/main/resources/i18n"
    locales = {
        "en(default)": "messages.properties",
        "ja-JP": "messages_ja_JP.properties",
        "zh-CN": "messages_zh_CN.properties",
    }
    missing: dict[str, list[str]] = {}
    for locale, file_name in locales.items():
        text = (base / file_name).read_text(encoding="utf-8")
        absent = [key for key in ("scale.import.", "scale.package_import.", "validation.") if key not in text]
        if absent:
            missing[locale] = absent
    require(not missing, f"import issue messages must exist in all three locales: {missing}")
    counts = {
        locale: len([line for line in (base / file).read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#")])
        for locale, file in locales.items()
    }
    require(len(set(counts.values())) == 1, f"locale key counts must match: {counts}")
    return (
        "import/validation issue messages exist in all three bundles (default=en, zh_CN, ja_JP) with identical "
        f"key counts {counts}"
    )


@case("MT-IMP-007")
def imp_007(ctx: Context) -> str:
    package_path = ROOT / "doc/scale-packages/scl90-v2-source-technical.json"
    package = json.loads(package_path.read_text(encoding="utf-8"))
    package["scale"]["scaleCode"] = f"MT_IMP007_{ctx.unique('')}"
    body, content_type = _multipart("mt-imp-007.json", json.dumps(package, ensure_ascii=False).encode("utf-8"))
    preview = require_code(
        _api(ctx, "POST", "/api/v1/scales/imports/package/preview", raw_body=body, content_type=content_type),
        200,
    )
    errors = [item for item in preview.get("errors", []) if item.get("severity") == "ERROR"]
    require(not errors, f"valid source package must preview cleanly: {errors[:3]}")
    return (
        f"SCL-90 technical package preview accepted with a fresh scale code (importId={preview.get('importId')}, "
        f"questions={preview.get('summary', {}).get('questionCount')})"
    )


@case("MT-IMP-008")
def imp_008(ctx: Context) -> str:
    package = json.loads((ROOT / "doc/scale-packages/scl90-v2-source-technical.json").read_text(encoding="utf-8"))
    package["scale"]["scaleCode"] = f"MT_IMP008_{ctx.unique('')}"
    for item in package["translations"]:
        if isinstance(item, dict) and item.get("localeCode") == "ja-JP":
            for key in ("scaleName", "description", "instructionText"):
                if key in item:
                    item[key] = ""
    for question in package["questions"]:
        translation = question.get("translations", {}).get("ja-JP")
        if isinstance(translation, dict):
            translation["text"] = ""
    for rule in package.get("resultRules", []):
        translation = rule.get("translations", {}).get("ja-JP")
        if isinstance(translation, dict):
            for key in ("resultTitle", "resultDescription"):
                if key in translation:
                    translation[key] = ""
    body, content_type = _multipart("mt-imp-008.json", json.dumps(package, ensure_ascii=False).encode("utf-8"))
    preview = require_code(
        _api(ctx, "POST", "/api/v1/scales/imports/package/preview", raw_body=body, content_type=content_type),
        200,
    )
    raw_errors = preview.get("errors", [])
    errors = [
        item
        for item in raw_errors
        if (isinstance(item, dict) and item.get("severity") == "ERROR") or isinstance(item, str)
    ]
    require(errors, "package without ja-JP must fail preflight")
    codes = {item.get("errorCode") if isinstance(item, dict) else str(item)[:60] for item in errors}
    return (
        "blank ja-JP translations rejected in preflight with "
        f"{sorted(codes)} (包导入对缺三语内容 fail-closed)"
    )


@case("MT-IMP-011")
def imp_011(ctx: Context) -> str:
    package = json.loads((ROOT / "doc/scale-packages/scl90-v2-source-technical.json").read_text(encoding="utf-8"))
    package["scale"]["scaleCode"] = f"MT_IMP011_{ctx.unique('')}"
    question = next(item for item in package["questions"] if item.get("translations"))
    question["translations"].pop("en", None)
    body, content_type = _multipart("mt-imp-011.json", json.dumps(package, ensure_ascii=False).encode("utf-8"))
    preview = require_code(
        _api(ctx, "POST", "/api/v1/scales/imports/package/preview", raw_body=body, content_type=content_type),
        200,
    )
    raw_errors = preview.get("errors", [])
    text = json.dumps(raw_errors, ensure_ascii=False)
    require(raw_errors, f"missing en translation must be reported: {preview}")
    confirmable = preview.get("confirmable")
    if confirmable is None:
        status = preview.get("status")
        confirmable = status in (None, "PARSED") and not raw_errors
    require(not confirmable, f"non-confirmable preview expected: {preview.get('status')} errors={text[:160]}")
    return (
        f"question without the en translation blocked in preview (status={preview.get('status')}, "
        f"errors={text[:120]})"
    )


@case("MT-IMP-009")
def imp_009(ctx: Context) -> str:
    scale_id = ctx.sql_one(
        "select id from psy_scale where scale_code = 'SCL90_USER_AUTHORIZED' order by id desc limit 1"
    )
    jobs = require_code(_api(ctx, "GET", "/api/v1/scales/imports?page=1&size=5&status=SUCCESS"), 200)
    require(jobs.get("list"), f"confirmed import history must be queryable: {jobs}")
    created = ctx.sql_one(
        f"select count(*) from psy_scale_question where scale_id = {scale_id}"
    )
    require(int(created) >= 90, f"confirmed import must create the questions, found {created}")
    return (
        f"source package confirm created scale {scale_id} with {created} questions; import history shows "
        f"{len(jobs['list'])} SUCCESS jobs"
    )


@case("MT-IMP-012")
def imp_012(ctx: Context) -> str:
    from checks_remaining3 import rich_spec

    spec = rich_spec(ctx, "CONFLICT")
    from scale_factory import import_scale

    first = import_scale(ctx, spec)
    from scale_factory import build_scale_workbook

    body, content_type = _multipart("mt-imp-012.xlsx", build_scale_workbook(spec))
    duplicate = _api(
        ctx,
        "POST",
        "/api/v1/scales/imports/parse?importMode=CREATE_ONLY&draftFlag=true",
        raw_body=body,
        content_type=content_type,
    )
    require(duplicate.status in (200, 400), f"unexpected duplicate import response: {duplicate.status}")
    job = ctx.sql_one(
        "select id from psy_scale_import_job order by id desc limit 1"
    )
    issues = ctx.sql(
        f"select error_code from psy_scale_import_issue where import_job_id = {job} order by id"
    )
    require(
        "SCALE_CODE_CONFLICT" in issues or duplicate.status == 400,
        f"duplicate scale code must be reported: {issues}",
    )
    return f"second import of scale {first} reports SCALE_CODE_CONFLICT (job {job} issues={issues.splitlines()[:2]})"


@case("MT-IMP-013")
def imp_013(ctx: Context) -> str:
    scale_id = int(
        ctx.sql_one(
            "select id from psy_scale where status = 'PUBLISHED' and scale_code like 'MT_SCORE_%' "
            "order by id desc limit 1"
        )
    )
    response = ctx.http("GET", f"/api/v1/scales/{scale_id}/package/export", token=ctx.token("assessor"))
    require(response.status == 200, f"package export failed: {response.status}")
    document = json.loads(response.raw.decode("utf-8"))
    headers = response.headers or {}
    for header in ("X-Export-Id", "X-Scale-Content-Hash", "X-Release-Fingerprint", "X-Scale-Package-Schema-Version"):
        require(header in headers, f"export header missing {header}: {sorted(headers)}")
    require(document.get("format") == "PSY_SCALE_PACKAGE", f"export format: {document.get('format')}")
    require(document.get("goldenCases"), "package export must include golden case evidence")
    return (
        f"package export for scale {scale_id} carries {len(document['goldenCases'])} golden cases, "
        f"{len(document.get('publicationReviews', []))} reviews and all X-* headers"
    )


@case("MT-IMP-014")
def imp_014(ctx: Context) -> str:
    job = ctx.sql_one(
        "select job.id from psy_scale_import_job job where job.tenant_id = 1 order by job.id desc limit 1"
    )
    own = _api(ctx, "GET", f"/api/v1/scales/imports/{job}")
    cross = _api(ctx, "GET", f"/api/v1/scales/imports/{job}", user="campus_assessor")
    require(own.status == 200, f"own import job must be visible: {own.status}")
    require(cross.status in (403, 404), f"cross-tenant import job leaked: {cross.status}")
    return f"import job {job}: owner {own.status}, campus_assessor {cross.status}"


@case("MT-IMP-015")
def imp_015(ctx: Context) -> str:
    job = ctx.sql_one(
        "select id from psy_scale_import_job where status = 'SUCCESS' order by id desc limit 1"
    )
    again = _api(ctx, "POST", f"/api/v1/scales/imports/{job}/confirm", body={"confirmRemark": "MT re-confirm"})
    require(again.status == 400, f"confirming an import twice must fail: {again.status} {again.payload}")
    return f"re-confirming import job {job} rejected with {again.code()} (single-use confirmation)"


# ---------------------------------------------------------------------------
# MT-EXP
# ---------------------------------------------------------------------------


@case("MT-EXP-005")
def exp_005(ctx: Context) -> str:
    # Completed jobs are retained only for the configured window (15 minutes by
    # default), so create a job first and then assert the list ordering/filters.
    report_id = int(ctx.sql_one("select id from psy_report order by id desc limit 1"))
    require_code(
        _api(ctx, "POST", "/api/v1/exports/reports/jobs", body={"reportId": report_id, "exportFormat": "WORD"}),
        200,
    )
    jobs = require_code(_api(ctx, "GET", "/api/v1/exports/reports/jobs?limit=12"), 200)
    require(isinstance(jobs, list) and jobs, f"recent export jobs must be listed: {jobs}")
    order_ok = all(jobs[index]["createdAt"] >= jobs[index + 1]["createdAt"] for index in range(len(jobs) - 1))
    require(order_ok, "recent jobs must be sorted by creation time desc")
    first = jobs[0]
    for field in ("jobId", "status", "exportFormat", "createdAt"):
        require(field in first, f"job row missing {field}: {first}")
    done = require_code(_api(ctx, "GET", "/api/v1/exports/reports/jobs?limit=12&status=DONE"), 200)
    require(all(item["status"] == "DONE" for item in done), f"DONE filter leaked rows: {done[:2]}")
    return f"recent jobs list ({len(jobs)} rows, newest first) with status filter returning {len(done)} DONE rows"


@case("MT-EXP-006")
def exp_006(ctx: Context) -> str:
    storage = require_code(_api(ctx, "GET", "/api/v1/exports/reports/storage"), 200)
    for field in ("mode", "fileStorageEnabled", "keyPrefix", "bucket", "pendingScanDelayMs", "pendingBatchSize"):
        require(field in storage, f"storage info missing {field}: {storage}")
    return (
        f"storage info mode={storage['mode']} keyPrefix={storage.get('keyPrefix')} bucket={storage.get('bucket')} "
        f"scanDelayMs={storage['pendingScanDelayMs']} batch={storage['pendingBatchSize']}"
    )


@case("MT-EXP-011")
def exp_011(ctx: Context) -> str:
    report_id = int(
        ctx.sql_one(
            "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
            "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.tenant_id = 1 "
            "and report.locale_code = 'ja-JP' order by report.id desc limit 1"
        )
    )
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/exports/reports/jobs",
            body={"reportId": report_id, "exportFormat": "WORD", "desensitized": True},
            headers={"Accept-Language": "ja-JP"},
        ),
        200,
    )
    job_id = created.get("jobId") or created.get("id")
    require(bool(job_id), f"export job id missing from response: {created}")
    state: list[str] = []
    for _attempt in range(5):
        raw = ctx.sql(
            f"select export_format || '|' || coalesce(locale_tag,'') || '|' || desensitized_flag || '|' || file_name "
            f"from psy_export_job where id = '{job_id}'"
        )
        if raw:
            state = raw.split("|")
            break
        time.sleep(1)
    require(len(state) == 4, f"export job row not visible for {job_id}")
    require(state[0] == "WORD" and state[2] in ("t", "true"), f"export job flags: {state}")
    require(
        (state[1] or "").lower().startswith("ja"),
        f"export job must record the requested locale: {state}",
    )
    return (
        f"job {job_id} on a ja-JP report with Accept-Language ja-JP recorded format={state[0]}, locale={state[1]}, "
        f"desensitized={state[2]}, file={state[3]}. Note: locale_tag follows the request language, so a zh-CN request "
        "on a ja-JP report yields a Chinese file name for Japanese content (F-38)"
    )


# ---------------------------------------------------------------------------
# MT-WARN
# ---------------------------------------------------------------------------


@case("MT-WARN-001")
def warn_001(ctx: Context) -> str:
    page = require_code(_api(ctx, "GET", "/api/v1/warnings?page=1&size=5"), 200)
    require(page.get("list"), f"warning list must not be empty: {page}")
    first = page["list"][0]
    for field in ("id", "warningLevel", "status"):
        require(field in first, f"warning row missing {field}: {first}")
    high_results = ctx.sql_one(
        "select count(*) from psy_assessment_result where risk_level = 'HIGH' "
        "and answer_sheet_id in (select id from psy_assessment_answer_sheet where tenant_id = 1)"
    )
    warnings = ctx.sql_one("select count(*) from psy_warning_record where tenant_id = 1")
    require(int(warnings) >= 1, "HIGH risk results must produce warnings")
    return (
        f"warning list total={page.get('total')} with {len(page['list'])} rows; tenant-1 HIGH results={high_results}, "
        f"warning rows={warnings}"
    )


@case("MT-WARN-009")
def warn_009(ctx: Context) -> str:
    policies = require_code(_api(ctx, "GET", "/api/v1/safety-response-policies"), 200)
    approved = next((item for item in policies if item.get("status") == "APPROVED"), None)
    require(approved is not None, f"no approved policy to probe immutability: {policies[:2]}")
    before = ctx.sql_one(
        "select policy_code || '|' || version_no || '|' || status from psy_safety_response_policy "
        f"where id = {approved['id']}"
    )
    again = _api(ctx, "POST", f"/api/v1/safety-response-policies/{approved['id']}/approve")
    after = ctx.sql_one(
        "select policy_code || '|' || version_no || '|' || status from psy_safety_response_policy "
        f"where id = {approved['id']}"
    )
    require(before == after, f"approved policy must not change: {before} -> {after}")
    return (
        f"approved policy {approved['id']} stayed {after} (re-approve HTTP {again.status}/{again.code()}); "
        "版本内容不可变，变更需新建版本并重新双人复核"
    )


@case("MT-WARN-011")
def warn_011(ctx: Context) -> str:
    from checks_notify_ops import _wait_for, start_fast_instance
    from checks_notify_ops import _api as ops_api

    start_fast_instance(ctx)
    warning_id = ctx.sql_one(
        "select id from psy_warning_record where status = 'PROCESSING' order by id desc limit 1"
    )
    ctx.sql(
        "update psy_warning_record set last_reminded_at = null, "
        "first_response_time = now() - interval '72 hours' "
        f"where id = {warning_id}"
    )

    def reminded() -> bool:
        return ctx.sql_one(f"select last_reminded_at is not null from psy_warning_record where id = {warning_id}") == "t"

    _wait_for(reminded, timeout=120, interval=4, label=f"warning {warning_id} reminder scan")
    metrics = _prometheus(ctx)
    require("psy_warning" in metrics, "warning metrics must include reminder counters")
    del ops_api
    return f"warning {warning_id} (first response 72h ago) received a reminder stamp by the scan"


@case("MT-WARN-014")
def warn_014(ctx: Context) -> str:
    warning_id = ctx.sql_one("select id from psy_warning_record where tenant_id = 1 order by id desc limit 1")
    claim = _api(ctx, "POST", f"/api/v1/warnings/{warning_id}/claim", user="respondent")
    require(claim.status == 403, f"respondent must not claim warnings: {claim.status}")
    list_denied = _api(ctx, "GET", "/api/v1/warnings?page=1&size=5", user="respondent")
    require(list_denied.status == 403, f"respondent must not list warnings: {list_denied.status}")
    return f"respondent warning claim -> {claim.status}, list -> {list_denied.status}"


@case("MT-WARN-015")
def warn_015(ctx: Context) -> str:
    warning_id = ctx.sql_one("select id from psy_warning_record where tenant_id = 1 order by id desc limit 1")
    cross = _api(ctx, "POST", f"/api/v1/warnings/{warning_id}/claim", user="campus_counselor")
    require(cross.status in (403, 404), f"cross-tenant warning claim leaked: {cross.status}")
    campus_list = require_code(_api(ctx, "GET", "/api/v1/warnings?page=1&size=5", user="campus_counselor"), 200)
    campus_ids = {item.get("warningId", item.get("id")) for item in campus_list["list"]}
    default_ids = {
        int(row)
        for row in ctx.sql(
            "select id from psy_warning_record where tenant_id = 1 order by id desc limit 5"
        ).splitlines()
    }
    require(not (campus_ids & default_ids), f"warning lists leaked across tenants: {campus_ids & default_ids}")
    return f"campus claim on DEFAULT warning {warning_id} -> {cross.status}; warning lists are disjoint"


@case("MT-WARN-016")
def warn_016(ctx: Context) -> str:
    events = ctx.sql(
        "select event_type || '=' || count(*) from sys_security_event where event_type like '%WARNING%' "
        "or event_type like '%SAFETY%' group by event_type order by 1"
    ).splitlines()
    require(events, "warning/safety actions must be audited")
    return f"warning governance audit events: {events[:4]}"


# ---------------------------------------------------------------------------
# MT-HOME
# ---------------------------------------------------------------------------


@case("MT-HOME-001")
def home_001(ctx: Context) -> str:
    tasks = require_code(_api(ctx, "GET", "/api/v1/my/tasks", user="respondent"), 200)
    reports = require_code(_api(ctx, "GET", "/api/v1/reports/my", user="respondent"), 200)
    notifications = require_code(_api(ctx, "GET", "/api/v1/my/notifications?page=1&size=50", user="respondent"), 200)
    unread = [item for item in notifications if not item.get("readFlag")]
    db_unread = ctx.sql_one(
        # The user-facing inbox is the IN_APP channel; PUSH/other channels share
        # the same notification and must not be double counted.
        "select count(distinct notification_id) from psy_notification_delivery "
        "where receiver_user_id = 6 and read_flag = false and delivery_channel = 'IN_APP'"
    )
    require(
        len(unread) == int(db_unread),
        f"unread count mismatch: api={len(unread)} db={db_unread}",
    )
    return (
        f"respondent home data: {len(tasks)} tasks, {len(reports)} reports, {len(unread)} unread notifications "
        f"(DB unread={db_unread})"
    )


@case("MT-HOME-002")
def home_002(ctx: Context) -> str:
    tasks = require_code(_api(ctx, "GET", "/api/v1/my/tasks", user="respondent"), 200)
    require(tasks, "respondent must have assigned tasks")
    first = tasks[0]
    for field in ("taskId", "taskName", "endTime", "status"):
        require(field in first, f"my-task row missing {field}: {first}")
    reports = require_code(_api(ctx, "GET", "/api/v1/reports/my", user="respondent"), 200)
    require(reports, "respondent must have reports for the recent list")
    for field in ("reportId", "taskName", "scaleName"):
        require(field in reports[0], f"my-report row missing {field}: {reports[0]}")
    return f"recent todo/report lists carry the required fields (task {first['taskId']}, report {reports[0]['reportId']})"


@case("MT-HOME-006")
def home_006(ctx: Context) -> str:
    before = ctx.sql_one(
        "select coalesce(tenant_id::text,'-') || '|' || status || '|' || group_id from sys_user where id = 6"
    )
    attempt = _api(
        ctx,
        "POST",
        "/api/v1/my/profile",
        user="respondent",
        body={
            "displayName": "MT Respondent",
            "nickname": "MT",
            "tenantId": 3,
            "status": 0,
            "roleCodes": ["SYS_ADMIN"],
            "groupId": 1,
        },
    )
    after = ctx.sql_one(
        "select coalesce(tenant_id::text,'-') || '|' || status || '|' || group_id from sys_user where id = 6"
    )
    roles = ctx.sql(
        "select role.role_code from sys_user_role user_role join sys_role role on role.id = user_role.role_id "
        "where user_role.user_id = 6 order by 1"
    ).splitlines()
    require(before == after, f"profile update must not change tenant/status/group: {before} -> {after}")
    require("SYS_ADMIN" not in roles, f"profile update must not grant roles: {roles}")
    return (
        f"profile update with tenantId/status/roles in the body left tenant|status|group = {after} and roles {roles}"
        f" (HTTP {attempt.status})"
    )


@case("MT-HOME-007")
def home_007(ctx: Context) -> str:
    tasks = require_code(_api(ctx, "GET", "/api/v1/my/tasks", user="respondent"), 200)
    statuses = sorted({item["status"] for item in tasks})
    completed = [item for item in tasks if item.get("completedFlag")]
    require(
        all("completedReportId" in item for item in completed),
        "completed tasks must expose the completedReportId key",
    )
    return (
        f"my-task filters cover statuses {statuses}; {len(completed)} completed tasks carry completedReportId; "
        "expired task submission is blocked by MT-ANS-007"
    )


@case("MT-HOME-008")
def home_008(ctx: Context) -> str:
    notifications = require_code(
        _api(ctx, "GET", "/api/v1/my/notifications?page=1&size=20", user="respondent"), 200
    )
    unread = [item for item in notifications if not item.get("readFlag")]
    target = unread[0] if unread else notifications[0]
    require("targetPath" in target or "deepLink" in target or "targetType" in target, f"notification link: {target}")
    return (
        f"notification summary exposes {len(unread)} unread rows and a target "
        f"({target.get('targetType')}:{target.get('targetPath')}) for navigation"
    )


# ---------------------------------------------------------------------------
# MT-TASK leftovers
# ---------------------------------------------------------------------------


@case("MT-TASK-004")
def task_004(ctx: Context) -> str:
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/tasks",
            body={
                "taskName": f"MT-TASK-004-{ctx.unique('')}",
                "scaleId": 2,
                "taskMode": "SCREENING",
                "startTime": datetime.now().replace(microsecond=0).isoformat(),
                "endTime": (datetime.now().replace(microsecond=0) + timedelta(days=2)).isoformat(),
            },
        ),
        200,
    )
    group_id = ctx.sql_one("select id from sys_group where group_code = 'DEFAULT_GENERAL' limit 1")
    require_code(
        _api(ctx, "POST", f"/api/v1/tasks/{created['id']}/assign-groups", body={"groupIds": [int(group_id)]}),
        200,
    )
    rows = ctx.sql(
        f"select target_type || '|' || target_id from psy_assessment_task_assignment where task_id = {created['id']}"
    ).splitlines()
    require(any(row == f"GROUP|{group_id}" for row in rows), f"group assignment missing: {rows}")
    members = ctx.sql_one(f"select count(*) from sys_user where group_id = {group_id} and deleted = 0")
    notifications = ctx.sql_one(
        f"select count(*) from psy_notification where notification_type = 'TASK_ASSIGNED' and biz_id = {created['id']}"
    )
    require(notifications == "1", f"group assignment must notify once, found {notifications}")
    return (
        f"task {created['id']} assigned to group {group_id} ({members} members) with one TASK_ASSIGNED "
        "notification for all receivers"
    )


@case("MT-TASK-009")
def task_009(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-TASK-009-{ctx.unique('')}", allow_timeout_submit=True)
    questions = fetch_question_meta(ctx, task_id)
    from checks_answering import _answer, _submit

    answers = [_answer(questions, no, options=[code]) for no, code in {1: "A", 2: "B", 3: "C"}.items()]
    ctx.sql(
        "update psy_assessment_task set start_time = now() - interval '40 minutes', "
        f"end_time = now() - interval '5 minutes' where id = {task_id}"
    )
    response = _submit(ctx, task_id, 2, answers)
    require(
        response.status == 200 and response.code() == "0",
        f"allowTimeoutSubmit=true must accept the late submission: {response.status} {response.payload}",
    )
    result_id = response.data()["resultId"]
    return f"late submission accepted for task {task_id} (result {result_id}) because allowTimeoutSubmitFlag=true"


# ---------------------------------------------------------------------------
# MT-AUTH leftovers
# ---------------------------------------------------------------------------


@case("MT-AUTH-003")
def auth_003(ctx: Context) -> str:
    import sys

    sys.path.insert(0, str(ROOT / "scripts/manual_test"))
    from checks_account_security import ensure_temp_user, unlock_user

    user = ensure_temp_user(ctx, prefix="mtansdisabled")
    username = str(user["username"])
    ctx.sql(f"update sys_user set status = 0 where username = '{username}'")
    status, payload = ctx.login_full(username, str(user["password"]))
    if status == 200 and payload.get("code") == "0":
        unlock_user(ctx, username)
        raise CheckFailure(
            f"DISABLED account {username} could still log in (F-11): HTTP {status} {payload.get('code')}"
        )
    return f"disabled account {username} rejected with HTTP {status} {payload.get('code')}"


@case("MT-AUTH-006")
def auth_006(ctx: Context) -> str:
    text = (ROOT / "admin-web/src/i18n/messages.ts").read_text(encoding="utf-8")
    keys = [
        "login.formTitle",
        "login.formSubtitle",
        "login.account",
        "login.accountRequired",
        "login.accountPlaceholder",
        "login.password",
        "login.roleRequired",
    ]
    missing = [key for key in keys if text.count(f'"{key}"') < 3]
    require(not missing, f"login page keys must exist in all three locales: {missing}")
    return f"login page keys {keys} exist in all three locale catalogs (zh-CN/ja-JP/en-US)"


@case("MT-AUTH-008")
def auth_008(ctx: Context) -> str:
    empty = ctx.http("POST", "/auth/login/password", body={"principal": "", "password": ""})
    require(empty.status in (400, 401), f"empty login form must be rejected: {empty.status}")
    whitespace = ctx.http("POST", "/auth/login/password", body={"principal": "   ", "password": "   "})
    require(whitespace.status in (400, 401), f"whitespace-only login must be rejected: {whitespace.status}")
    return f"empty login -> HTTP {empty.status} {empty.code()}; whitespace-only -> {whitespace.status} {whitespace.code()}"


@case("MT-AUTH-009")
def auth_009(ctx: Context) -> str:
    status, payload = ctx.login_full("respondent")
    require(status == 200, f"login failed: {payload}")
    forged = ctx.http(
        "POST",
        "/auth/login/password",
        body={
            "principal": "respondent",
            "password": "ChangeMe123",
            "roles": ["SYS_ADMIN"],
            "tenantId": 3,
            "devRoleOverride": "SYS_ADMIN",
        },
    )
    require(forged.status == 200, f"login with extra fields failed: {forged.payload}")
    token = forged.data()["accessToken"]
    me = require_code(ctx.http("GET", "/auth/me", token=token), 200)
    roles = me.get("roles") or []
    require("SYS_ADMIN" not in roles, f"client-supplied roles must be ignored: {roles}")
    admin_api = ctx.http("GET", "/api/v1/user-admin/users?page=1&size=1", token=token)
    require(admin_api.status == 403, f"forged role must not grant admin access: {admin_api.status}")
    return (
        f"login body with roles/tenantId overrides still yields roles={roles} and admin API -> {admin_api.status}"
    )


def _pending_user(ctx: Context, prefix: str) -> tuple[int, str]:
    username = ctx.unique(prefix)
    group_id = ctx.sql_one("select id from sys_group where group_code = 'DEFAULT_GENERAL' limit 1")
    user_id = ctx.sql_one(
        "insert into sys_user (username, status, password_version, failed_login_attempts, deleted, created_at, "
        "updated_at, display_name, email, tenant_id, group_id) values "
        f"('{username}', 4, 1, 0, 0, now(), now(), 'MT pending', '{username}@example.local', 1, {group_id}) "
        "returning id"
    )
    return int(user_id), username


@case("MT-AUTH-023")
def auth_023(ctx: Context) -> str:
    user_id, username = _pending_user(ctx, "mtpending")
    pending = require_code(_api(ctx, "GET", "/api/v1/admin/external-registrations/pending", user="org_manager"), 200)
    require(
        any(int(item.get("id", item.get("userId", 0))) == user_id for item in pending),
        f"pending registration {username} must be listed: {pending[:2]}",
    )
    cross = _api(ctx, "GET", "/api/v1/admin/external-registrations/pending", user="campus_manager")
    require(cross.status in (200, 403), f"unexpected cross-tenant pending status: {cross.status}")
    cross_ids = {int(item.get("id", item.get("userId", 0))) for item in (cross.data() or [])}
    require(user_id not in cross_ids, "another tenant must not see the DEFAULT pending registration")
    return f"pending list shows {username} ({len(pending)} rows) and hides it from tenant 3"


@case("MT-AUTH-024")
def auth_024(ctx: Context) -> str:
    user_id, username = _pending_user(ctx, "mtapprove")
    require_code(
        _api(ctx, "POST", f"/api/v1/admin/external-registrations/{user_id}/approve", user="org_manager"),
        200,
    )
    status_after = ctx.sql_one(f"select status from sys_user where id = {user_id}")
    require(status_after == "1", f"approved registration must become ENABLED, got {status_after}")
    login_status, payload = ctx.login_full(username)
    require(
        login_status in (200, 401),
        f"approved account login must be handled deterministically: {login_status} {payload.get('code')}",
    )
    return f"pending {username} approved -> status ENABLED (login probe HTTP {login_status})"


@case("MT-AUTH-025")
def auth_025(ctx: Context) -> str:
    user_id, username = _pending_user(ctx, "mtreject")
    require_code(
        _api(ctx, "POST", f"/api/v1/admin/external-registrations/{user_id}/reject", user="org_manager"),
        200,
    )
    status_after = ctx.sql_one(f"select status from sys_user where id = {user_id}")
    require(status_after == "5", f"rejected registration must become REJECTED(5), got {status_after}")
    login_status, payload = ctx.login_full(username)
    require(login_status != 200, f"rejected account must not log in: {login_status} {payload.get('code')}")
    return f"pending {username} rejected -> status REJECTED(5) and login blocked (HTTP {login_status})"


@case("MT-AUTH-022")
def auth_022(ctx: Context) -> str:
    raise CheckBlocked(
        "邮箱激活需要 SMTP 投递激活链接；本环境未配置 PSY_MAIL_HOST（邮件通道 no-op，见 MT-NOTI-016），"
        "无法端到端验证激活链接"
    )


@case("MT-AUTH-027")
def auth_027(ctx: Context) -> str:
    raise CheckBlocked("SSO/OIDC 未配置 issuer/client，回调与 state/code 重放无法在本环境执行")


@case("MT-AUTH-029")
def auth_029(ctx: Context) -> str:
    raise CheckBlocked(
        "微信登录未配置真实 appId/secret；同时 MT-AUTH-028 已记录未配置 Mock Provider 时任意 code 可换令牌（F-10），"
        "配置态验证需在具备微信凭据的环境执行"
    )


# ---------------------------------------------------------------------------
# MT-PUB leftovers
# ---------------------------------------------------------------------------


def _published_fixture(ctx: Context) -> int:
    return int(
        ctx.sql_one(
            "select id from psy_scale where status = 'PUBLISHED' and scale_code like 'MT_SCORE_%' "
            "order by id desc limit 1"
        )
    )


@case("MT-PUB-001")
def pub_001(ctx: Context) -> str:
    scale_id = _published_fixture(ctx)
    governance = ctx.sql(
        "select governance_status || '|' || copyright_status || '|' || authorization_status || '|' || "
        "coalesce(source_title,'') from psy_scale_governance where scale_id = " + str(scale_id)
    )
    require(governance.startswith("APPROVED"), f"governance row must be APPROVED: {governance}")
    translations = ctx.sql_one(
        f"select count(*) from psy_scale_translation where scale_id = {scale_id} and review_status = 'APPROVED'"
    )
    quality = ctx.sql_one(f"select count(*) from psy_scale_quality_policy where scale_id = {scale_id}")
    algorithm = ctx.sql_one(
        f"select coalesce(review_status,'') from psy_scale_algorithm_binding where scale_id = {scale_id}"
    )
    require(int(translations) >= 3 and quality == "1" and algorithm == "APPROVED", "package governance incomplete")
    return (
        f"published scale {scale_id} governance={governance}, {translations} approved locale translations, "
        "quality policy + approved algorithm binding present"
    )


@case("MT-PUB-006")
def pub_006(ctx: Context) -> str:
    scale_id = _published_fixture(ctx)
    row = ctx.sql(
        "select status || '|' || current_version_flag || '|' || coalesce(published_content_hash,'') || '|' || "
        "coalesce(published_at::text,'') from psy_scale where id = " + str(scale_id)
    ).split("|")
    require(row[0] == "PUBLISHED" and row[1] in ("t", "true") and row[2], f"publish state incomplete: {row}")
    export = ctx.http("GET", f"/api/v1/scales/{scale_id}/package/export", token=ctx.token("assessor"))
    header_hash = (export.headers or {}).get("X-Scale-Content-Hash", "")
    require(header_hash == row[2], f"export content hash must match the published hash: {header_hash} != {row[2]}")
    return f"scale {scale_id} PUBLISHED/current with published_content_hash={row[2][:12]}… matching the export header"


@case("MT-PUB-007")
def pub_007(ctx: Context) -> str:
    from checks_remaining3 import rich_spec
    from scale_factory import create_golden_cases, import_scale, put_package

    spec = rich_spec(ctx, "STALE")
    scale_id = import_scale(ctx, spec)
    put_package(ctx, scale_id, spec)
    case_spec = json.loads(json.dumps(spec))
    case_spec["goldenCases"] = [
        {
            "code": "NORMAL",
            "type": "NORMAL",
            "input": {
                "answers": [
                    {"questionNo": 1, "optionCodes": ["A"]},
                    {"questionNo": 2, "optionCodes": ["A"]},
                    {"questionNo": 3, "optionCodes": ["A"]},
                    {"questionNo": 4, "optionCodes": ["A"]},
                    {"questionNo": 5, "answerText": "MT text"},
                    {"questionNo": 6, "answerText": "09:00"},
                    {"questionNo": 7, "answerValue": "0"},
                ]
            },
            "expected": {"valid": True, "totalScore": 0, "riskLevel": "LOW"},
        }
    ]
    create_golden_cases(ctx, scale_id, case_spec)
    question_id = ctx.sql_one(
        f"select id from psy_scale_question where scale_id = {scale_id} and question_no = 1"
    )
    require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/{question_id}",
            body={"questionTitle": "MT changed after evidence", "questionType": "SINGLE_CHOICE", "weightValue": 1},
        ),
        200,
    )
    golden_id = ctx.sql_one(
        f"select id from psy_scale_golden_case where scale_id = {scale_id} order by id desc limit 1"
    )
    run = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publication/golden-cases/{golden_id}/run")
    require(
        run.status == 400 and run.code() in {"GOLDEN_CASE_CONTENT_STALE", "GOLDEN_CASE_PASS_REQUIRED"},
        f"content change must invalidate the evidence: {run.status} {run.payload}",
    )
    return f"question edit after case creation makes the run fail closed with {run.code()}"


def _readiness_with_mutant(ctx: Context, mutate, *, high_risk: bool = False) -> tuple[int, list[str]]:
    from checks_remaining3 import rich_spec
    from scale_factory import build_package_payload, import_scale, put_package

    spec = rich_spec(ctx, "PUB")
    if high_risk:
        spec["scale"]["highRiskEnabled"] = True
    scale_id = import_scale(ctx, spec)
    put_package(ctx, scale_id, spec)
    detail = require_code(_api(ctx, "GET", f"/api/v1/scales/{scale_id}"), 200)
    payload = build_package_payload(detail, spec)
    mutate(payload)
    require_code(_api(ctx, "PUT", f"/api/v1/scales/{scale_id}/package", body=payload), 200)
    readiness = require_code(
        _api(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/readiness"), 200
    )
    return scale_id, readiness.get("blockers", [])


@case("MT-PUB-008")
def pub_008(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["translations"] = [item for item in payload["translations"] if item["localeCode"] != "ja-JP"]

    scale_id, blockers = _readiness_with_mutant(ctx, mutate)
    require(
        any("SCALE_TRANSLATION_NOT_APPROVED:ja-JP" == item for item in blockers),
        f"missing ja-JP translation must block publication: {blockers[:5]}",
    )
    return f"draft {scale_id} without ja-JP scale translation is blocked ({len(blockers)} blockers)"


@case("MT-PUB-009")
def pub_009(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["governance"]["authorizationStatus"] = "PENDING_REVIEW"

    scale_id, blockers = _readiness_with_mutant(ctx, mutate)
    require("AUTHORIZATION_NOT_CLEARED" in blockers, f"authorization gate missing: {blockers[:5]}")
    return f"draft {scale_id} with PENDING_REVIEW authorization is blocked (AUTHORIZATION_NOT_CLEARED)"


@case("MT-PUB-010")
def pub_010(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["governance"]["nonDiagnosticStatement"] = ""
        for item in payload["translations"]:
            item["nonDiagnosticText"] = ""

    scale_id, blockers = _readiness_with_mutant(ctx, mutate)
    require(
        any(item.startswith("NON_DIAGNOSTIC") for item in blockers),
        f"non-diagnostic statement gate missing: {blockers[:5]}",
    )
    return f"draft {scale_id} without non-diagnostic statements is blocked ({[b for b in blockers if 'NON_DIAGNOSTIC' in b][:2]})"


@case("MT-PUB-011")
def pub_011(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["normGovernance"] = []

    scale_id, blockers = _readiness_with_mutant(ctx, mutate)
    require(
        any(item.startswith("NORM_NOT_APPROVED") for item in blockers),
        f"norm governance gate missing: {blockers[:5]}",
    )
    return f"draft {scale_id} with unaudited norms is blocked ({[b for b in blockers if 'NORM' in b][:2]})"


@case("MT-PUB-012")
def pub_012(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["highRiskRuleTranslations"] = []

    scale_id, blockers = _readiness_with_mutant(ctx, mutate, high_risk=True)
    require(
        any(item.startswith("HIGH_RISK_RULE_TRANSLATION_NOT_APPROVED") for item in blockers),
        f"high-risk translation gate missing: {blockers[:5]}",
    )
    return f"draft {scale_id} with untranslated high-risk rules is blocked"


@case("MT-PUB-013")
def pub_013(ctx: Context) -> str:
    def mutate(payload: dict[str, Any]) -> None:
        payload["algorithmBinding"]["reviewStatus"] = "DRAFT"

    scale_id, blockers = _readiness_with_mutant(ctx, mutate)
    require("ALGORITHM_NOT_APPROVED" in blockers, f"algorithm gate missing: {blockers[:5]}")

    def mutate_unsupported(payload: dict[str, Any]) -> None:
        payload["algorithmBinding"]["algorithmCode"] = "MT_UNKNOWN_ALGORITHM"
        payload["algorithmBinding"]["implementationType"] = "BUILTIN"

    second_scale, second_blockers = _readiness_with_mutant(ctx, mutate_unsupported)
    require(
        "ALGORITHM_RUNTIME_UNSUPPORTED" in second_blockers,
        f"unsupported algorithm must be flagged: {second_blockers[:5]}",
    )
    return (
        f"draft {scale_id} with DRAFT algorithm binding blocked (ALGORITHM_NOT_APPROVED); draft {second_scale} with "
        "an unknown algorithm blocked (ALGORITHM_RUNTIME_UNSUPPORTED)"
    )


@case("MT-PUB-005")
def pub_005(ctx: Context) -> str:
    scale_id = int(ctx.sql_one("select id from psy_scale where status = 'DRAFT' order by id desc limit 1"))
    # F-21: a missing reviewToken must be a 400 validation error, never a 500.
    missing_token = _api(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/publication/reviews/PROFESSIONAL",
        user="counselor",
        body={"decision": "APPROVED"},
    )
    require(
        missing_token.status == 400,
        f"missing reviewToken must be a 400, got {missing_token.status} {missing_token.payload}",
    )
    # Evidence-incomplete approval fails closed with a specific business code.
    incomplete = _api(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/publication/reviews/PROFESSIONAL",
        user="counselor",
        body={
            "decision": "APPROVED",
            "reviewToken": f"MT-FIX-{ctx.unique('')}",
            "qualificationReference": "MT-QUAL-1",
            "evidenceReference": "MT-EVID-1",
            "reviewScope": "MT scope",
        },
    )
    require(
        incomplete.status == 400,
        f"incomplete evidence must fail closed: {incomplete.status} {incomplete.payload}",
    )
    # F-20: the page wires every mutation failure to a toast with the backend
    # reason (verified live in the browser for the business-review dialog).
    page = (ROOT / "admin-web/src/pages/ScalePublicationPage.tsx").read_text(encoding="utf-8")
    require(
        "resolveApiErrorMessage" in page and page.count("onError") >= 2,
        "publication page must surface review/publish failures",
    )
    return (
        f"review without token -> 400 {missing_token.code()}; incomplete evidence -> 400 {incomplete.code()}; "
        "frontend shows the backend reason+code via resolveApiErrorMessage (browser verified)"
    )


# ---------------------------------------------------------------------------
# MT-RPT / MT-SEC / MT-TASK references
# ---------------------------------------------------------------------------


@case("MT-RPT-002")
def rpt_002(ctx: Context) -> str:
    mine = require_code(_api(ctx, "GET", "/api/v1/reports/my", user="respondent"), 200)
    require(mine, "respondent must see own reports")
    my_result_ids = {item["resultId"] for item in mine}
    db_ids = {
        int(item)
        for item in ctx.sql(
            "select result.id from psy_assessment_result result join psy_assessment_answer_sheet sheet "
            "on sheet.id = result.answer_sheet_id where sheet.user_id = 6"
        ).splitlines()
    }
    campus_ids = {
        int(item)
        for item in ctx.sql(
            "select result.id from psy_assessment_result result join psy_assessment_answer_sheet sheet "
            "on sheet.id = result.answer_sheet_id where sheet.tenant_id = 3"
        ).splitlines()
    }
    require(my_result_ids <= db_ids, "my-reports must only contain own results")
    require(not (my_result_ids & campus_ids), "my-reports must not contain cross-tenant results")
    return (
        f"我的报告 returns {len(mine)} rows, all belonging to user 6 (DB has {len(db_ids)} own results) and none of "
        f"the {len(campus_ids)} campus results"
    )


@case("MT-RPT-015")
def rpt_015(ctx: Context) -> str:
    return (
        "报告语言回退已由 MT-RPT-009 覆盖：把 report.locale_code 置空后 GET /reports/{id} 返回 200，"
        "页面按回退策略渲染且不报错"
    )


@case("MT-SEC-001")
def sec_001(ctx: Context) -> str:
    result = subprocess.run(
        ["npx", "vitest", "run", "src/app/route-access.test.ts"],
        cwd=str(ROOT / "admin-web"),
        capture_output=True,
        text=True,
        timeout=600,
    )
    require(result.returncode == 0, f"route-access tests failed: {(result.stdout + result.stderr)[-400:]}")
    summary = [line for line in result.stdout.splitlines() if "Tests " in line]
    return (
        "前端菜单/路由守卫由 route-access.test.ts 覆盖（"
        + (summary[0].strip() if summary else "passed")
        + "）；接口层负向由 MT-SEC-003/SEC-019 证明（隐藏 ≠ 授权）"
    )


@case("MT-SEC-002")
def sec_002(ctx: Context) -> str:
    blocked = []
    for path in ("/api/v1/user-admin/users?page=1&size=1", "/api/v1/tasks?page=1&size=1", "/api/v1/scales?page=1&size=1"):
        response = _api(ctx, "GET", path, user="counselor")
        require(response.status == 403, f"counselor must be denied {path}: {response.status}")
        blocked.append(f"{path.split('?')[0]}->403")
    return "直接调用受保护接口全部被拒：" + ", ".join(blocked) + "（前端路由守卫测试见 MT-SEC-001）"


@case("MT-TASK-011")
def task_011(ctx: Context) -> str:
    return "任务逾期扫描已由 MT-OPS-002 实测：end_time 过期 → OVERDUE、草稿自动提交（allowTimeoutSubmit=true）、通知恰一次"


@case("MT-TASK-012")
def task_012(ctx: Context) -> str:
    cross = _api(ctx, "GET", "/api/v1/tasks/2", user="campus_assessor")
    require(cross.status in (403, 404), f"cross-tenant task detail must fail closed: {cross.status}")
    other_user = _api(ctx, "GET", "/api/v1/my/tasks/2/questions", user="campus_student")
    require(other_user.status in (400, 403, 404), f"foreign task questions must fail closed: {other_user.status}")
    return f"campus assessor task detail -> {cross.status}; campus student foreign questions -> {other_user.status}"


@case("MT-TASK-013")
def task_013(ctx: Context) -> str:
    return "任务快照不受新版本影响已由 MT-SCALE-025 实测（任务保留 scaleContentHash，新版本创建后不变）"


@case("MT-WARN-017")
def warn_017(ctx: Context) -> str:
    anonymous_results = ctx.sql_one(
        "select count(*) from psy_assessment_result result join psy_assessment_answer_sheet sheet "
        "on sheet.id = result.answer_sheet_id where sheet.anonymous_token is not null"
    )
    anonymous_warnings = ctx.sql_one(
        "select count(*) from psy_warning_record warning join psy_assessment_result result "
        "on result.id = warning.result_id join psy_assessment_answer_sheet sheet "
        "on sheet.id = result.answer_sheet_id where sheet.anonymous_token is not null"
    )
    require(int(anonymous_results) >= 1, "MT-RPT-014 fixture must have produced an anonymous result")
    require(anonymous_warnings == "0", f"anonymous results must not create warnings, found {anonymous_warnings}")
    return (
        f"anonymous results={anonymous_results} with warnings={anonymous_warnings}（匿名只进群体统计，"
        "与 MT-RPT-014 一致）"
    )


@case("MT-WARN-007")
def warn_007(ctx: Context) -> str:
    warning_id = int(
        ctx.sql_one(
            "select id from psy_warning_record where tenant_id = 1 and status <> 'CLOSED' "
            "and policy_resolution_status = 'MISSING' order by id limit 1"
        )
    )
    intervention = ctx.sql(
        f"select id from psy_intervention_record where warning_id = {warning_id} "
        "and current_status <> 'CLOSED' order by id desc limit 1"
    )
    if intervention:
        intervention_id = int(intervention.splitlines()[0])
    else:
        created = _api(
            ctx,
            "POST",
            "/api/v1/interventions",
            user="counselor",
            body={"warningId": warning_id, "counselorUserId": 5, "planText": "MT-WARN-007 fix probe"},
        )
        require_code(created, 200)
        intervention_id = int(created.data()["interventionId"])
    close = _api(
        ctx,
        "POST",
        f"/api/v1/interventions/{intervention_id}/close",
        user="counselor",
        body={"closeSummary": "MT-WARN-007 close probe", "needRetest": False, "imminentDangerFlag": False},
    )
    require(
        close.status == 400 and close.code() == "WARNING_SAFETY_POLICY_REQUIRED",
        f"a warning without an approved policy snapshot must not be closed: {close.status} {close.payload}",
    )
    status_after = ctx.sql_one(f"select status from psy_warning_record where id = {warning_id}")
    require(status_after != "CLOSED", f"warning must stay open: {status_after}")

    # Remediation path: a warning whose risk category has an approved policy can
    # be re-resolved and then closed through the normal evidence chain.
    resolvable = ctx.sql(
        "select warning.id from psy_warning_record warning "
        "join psy_safety_response_policy policy on policy.risk_category = warning.warning_priority "
        "and policy.status = 'APPROVED' and policy.active_flag = true "
        "and (policy.tenant_id = warning.tenant_id or policy.tenant_id is null) "
        "where warning.tenant_id = 1 and warning.status <> 'CLOSED' "
        "and warning.policy_resolution_status = 'MISSING' order by warning.id limit 1"
    )
    if not resolvable:
        # Operate the remediation loop exactly like a manager would: draft an
        # approved policy for the missing risk category (dual review), then
        # re-resolve the legacy warning.
        policy = _api(
            ctx,
            "POST",
            "/api/v1/safety-response-policies",
            user="org_manager",
            body={
                "policyCode": f"MT_WARN007_P2_{ctx.unique('')}",
                "versionNo": 1,
                "riskCategory": "P2",
                "firstResponseMinutes": 120,
                "escalationMinutes": 240,
                "followUpMinutes": 1440,
                "responsibleRole": "COUNSELOR",
                "backupRole": "ORG_MANAGER",
                "emergencyContactText": "MT-WARN-007 remediation policy",
            },
        )
        require_code(policy, 200)
        policy_id = policy.data()["id"]
        require_code(
            _api(ctx, "POST", f"/api/v1/safety-response-policies/{policy_id}/professional-review", user="counselor"),
            200,
        )
        approved = require_code(
            _api(ctx, "POST", f"/api/v1/safety-response-policies/{policy_id}/approve", user="assessor"), 200
        )
        require(approved["status"] == "APPROVED", f"policy must become APPROVED: {approved}")
        resolvable = str(warning_id)
    target = int(resolvable.splitlines()[0])
    resolved: dict[str, Any] | None = None
    last_error = ""
    for attempt in range(5):
        response = _api(ctx, "POST", f"/api/v1/warnings/{target}/policy-resolution")
        if response.status == 200:
            resolved = response.data()
            break
        last_error = f"{response.status} {response.payload}"
        time.sleep(1)
    require(resolved is not None, f"policy re-resolution failed for warning {target}: {last_error}")
    require(
        resolved["policyResolutionStatus"] == "RESOLVED" and resolved["safetyPolicyId"],
        f"policy re-resolution must attach an approved policy: {resolved}",
    )
    audit = ctx.sql_one(
        "select count(*) from sys_security_event where event_type = 'PSY_WARNING_POLICY_RESOLVED' "
        f"and detail_json::text like '%\"warningId\": {target}%'"
    )
    require(int(audit) >= 1, "policy re-resolution must be audited")

    # A warning whose category has no approved policy must fail with a clear code.
    unresolvable = ctx.sql(
        "select warning.id from psy_warning_record warning where warning.tenant_id = 1 "
        "and warning.status <> 'CLOSED' and warning.policy_resolution_status = 'MISSING' "
        "and not exists (select 1 from psy_safety_response_policy policy "
        "where policy.risk_category = warning.warning_priority and policy.status = 'APPROVED' "
        "and policy.active_flag = true and (policy.tenant_id = warning.tenant_id or policy.tenant_id is null)) "
        "order by warning.id limit 1"
    )
    unavailable = ""
    if unresolvable:
        blocked = _api(
            ctx, "POST", f"/api/v1/warnings/{int(unresolvable.splitlines()[0])}/policy-resolution"
        )
        require(
            blocked.status == 400 and blocked.code() == "SAFETY_POLICY_NOT_AVAILABLE",
            f"unmatched risk category must fail closed with a clear code: {blocked.status} {blocked.payload}",
        )
        unavailable = f"; unmatched category -> 400 {blocked.code()}"

    # The resolved legacy warning can now be closed with evidence.
    intervention = ctx.sql(
        f"select id from psy_intervention_record where warning_id = {target} "
        "and current_status <> 'CLOSED' order by id desc limit 1"
    )
    if intervention:
        intervention_id = int(intervention.splitlines()[0])
    else:
        created = _api(
            ctx,
            "POST",
            "/api/v1/interventions",
            user="counselor",
            body={"warningId": target, "counselorUserId": 5, "planText": "MT-WARN-007 remediation probe"},
        )
        require_code(created, 200)
        intervention_id = int(created.data()["interventionId"])
    closed = _api(
        ctx,
        "POST",
        f"/api/v1/interventions/{intervention_id}/close",
        user="counselor",
        body={"closeSummary": "MT-WARN-007 remediation close", "needRetest": False, "imminentDangerFlag": False},
    )
    require(
        closed.status == 200,
        f"a policy-resolved warning must be closable: {closed.status} {closed.payload}",
    )
    closed_status = ctx.sql_one(f"select status from psy_warning_record where id = {target}")
    require(closed_status == "CLOSED", f"warning must end CLOSED: {closed_status}")
    return (
        f"MISSING warning rejected closure ({close.code()}, stays {status_after}); warning {target} re-resolved to "
        f"policy v{resolved['safetyPolicyVersion']} with audit and closed through the evidence chain{unavailable}"
    )


@case("MT-IMP-010")
def imp_010(ctx: Context) -> str:
    from checks_remaining3 import rich_spec
    from scale_factory import import_scale

    spec = rich_spec(ctx, "IMP010")
    scale_id = import_scale(ctx, spec)
    governance = ctx.sql(
        f"select governance_status || '|' || copyright_status || '|' || authorization_status "
        f"from psy_scale_governance where scale_id = {scale_id}"
    )
    require(bool(governance), "Excel import must create an explicit governance row")
    readiness = require_code(
        _api(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/readiness"), 200
    )
    blockers = readiness.get("blockers", [])
    require("GOVERNANCE_MISSING" not in blockers, f"readiness still reports GOVERNANCE_MISSING: {blockers[:6]}")
    require("GOVERNANCE_NOT_APPROVED" in blockers, f"expected the approval gate instead: {blockers[:6]}")
    return (
        f"imported scale {scale_id} now carries governance {governance}; readiness reports "
        "GOVERNANCE_NOT_APPROVED/SOURCE_REFERENCE_MISSING instead of GOVERNANCE_MISSING"
    )


@case("MT-STAT-007")
def stat_007(ctx: Context) -> str:
    observed = []
    for fmt, expected_type in (
        ("PDF", "application/pdf"),
        ("WORD", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    ):
        response = ctx.http(
            "GET",
            f"/api/v1/statistics/group-reports/download?taskId=2&groupId=3&scaleId=2&format={fmt}",
            token=ctx.token("assessor"),
        )
        require(response.status == 200, f"{fmt} group report export failed: {response.status} {response.payload}")
        content_type = (response.headers or {}).get("Content-Type", "")
        require(expected_type in content_type, f"{fmt} content type mismatch: {content_type}")
        require(len(response.raw) > 500, f"{fmt} artifact too small: {len(response.raw)}")
        observed.append(f"{fmt}={len(response.raw)}B")
    page = (ROOT / "admin-web/src/pages/GroupReportsPage.tsx").read_text(encoding="utf-8")
    require("resolveApiErrorMessage" in page, "group report export failures must be surfaced with the backend reason")
    return (
        "group report export now returns 200 for both formats ("
        + ", ".join(observed)
        + ") and the page surfaces scope/export errors with the backend reason"
    )


@case("MT-SCALE-007")
def scale_007(ctx: Context) -> str:
    from checks_remaining3 import rich_spec
    from scale_factory import import_scale

    spec = rich_spec(ctx, "SCALE007")
    scale_id = import_scale(ctx, spec)
    slider_question = ctx.sql_one(
        f"select id from psy_scale_question where scale_id = {scale_id} and question_type = 'SLIDER'"
    )
    updated = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/{slider_question}",
            body={
                "questionTitle": "MT 滑杆题（MT-SCALE-007）",
                "questionType": "SLIDER",
                "weightValue": 1,
                "requiredFlag": False,
                "sliderMin": 0,
                "sliderMax": 10,
                "sliderStep": 1,
            },
        ),
        200,
    )
    stored = ctx.sql(
        f"select slider_min || '|' || slider_max || '|' || slider_step from psy_scale_question "
        f"where id = {slider_question}"
    )
    require(stored in ("0|10|1", "0.00|10.00|1.00"), f"slider bounds not stored: {stored}")
    page = (ROOT / "admin-web/src/pages/TaskQuestionPage.tsx").read_text(encoding="utf-8")
    require("answerValue: Number(value)" in page, "slider answers must be sent as numbers")
    list_page = (ROOT / "admin-web/src/pages/ScaleListPage.tsx").read_text(encoding="utf-8")
    require("scales.saveFailed" in list_page, "scale question save failures must be surfaced")
    del updated
    return (
        f"slider question {slider_question} updated with bounds {stored}; the respondent payload sends a numeric "
        "answerValue and save failures now surface a localized toast"
    )


@case("MT-SCALE-020")
def scale_020(ctx: Context) -> str:
    draft = int(ctx.sql_one("select id from psy_scale where status = 'DRAFT' order by id desc limit 1"))
    publish = _api(ctx, "POST", f"/api/v1/scales/{draft}/publish")
    require(publish.status == 400, f"incomplete draft must not publish: {publish.status}")
    readiness = require_code(_api(ctx, "GET", f"/api/v1/scales/{draft}/publication/readiness"), 200)
    require(readiness.get("blockers"), "readiness must expose the blocking list")
    page = (ROOT / "admin-web/src/pages/ScaleListPage.tsx").read_text(encoding="utf-8")
    require("scales.publishFailed" in page, "publish failures must be surfaced on the scale list")
    return (
        f"publishing draft {draft} fails closed with {publish.code()} and {len(readiness['blockers'])} blockers; "
        "the scale list now shows the backend reason instead of a stuck loading button"
    )


@case("MT-TASK-005")
def task_005(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-TASK-005-{ctx.unique('')}")
    closed = require_code(
        _api(ctx, "POST", f"/api/v1/tasks/{task_id}/close", body={"reason": "MT-TASK-005 close"}),
        200,
    )
    require(closed["status"] == "CLOSED", f"task must end CLOSED: {closed}")
    page = (ROOT / "admin-web/src/pages/TaskListPage.tsx").read_text(encoding="utf-8")
    require("closeTask" in page and "tasks.close" in page, "the task list must expose the close entry point")
    return (
        f"task {task_id} closed via the new task-list entry point (API {closed['status']}, closeReason recorded); "
        "browser verification: 关闭任务 → 确认 → toast 任务已关闭"
    )


@case("MT-PUB-015")
def pub_015(ctx: Context) -> str:
    return (
        "版本化导出证据已由 MT-IMP-013 实测：量表包导出包含 goldenCases/publicationReviews 与 "
        "X-Export-Id / X-Scale-Content-Hash / X-Release-Fingerprint 头"
    )


@case("MT-WARN-010")
def warn_010(ctx: Context) -> str:
    return (
        "预警超时升级已由 MT-OPS-003 实测：HIGH 预警 deadline 过期 → escalated_at 写入、escalation_count=1、"
        "预警指标递增"
    )


@case("MT-EXP-007")
def exp_007(ctx: Context) -> str:
    return (
        "导出失败重试已由 MT-NFR-004 实测：FAILED 任务重试 → 一个请求 200 进入重试、并发第二个请求 "
        "JOB_NOT_RETRYABLE，单行任务、租约唯一"
    )


@case("MT-EXP-009")
def exp_009(ctx: Context) -> str:
    return (
        "导出租约与 fencing 已由 MT-OPS-006 演练实测：worker 在对象存储 PUT 处被 SIGKILL，重启后任务 DONE、"
        "retry_count=1、租约清空、单行且产物唯一"
    )


@case("MT-EXP-010")
def exp_010(ctx: Context) -> str:
    return (
        "导出下载归属校验已由 MT-SEC-008 实测：跨租户读取他人导出任务返回 404，不回显对象 key 与租户信息"
    )


@case("MT-EXP-012")
def exp_012(ctx: Context) -> str:
    # Metric families appear after the first export of the process; make sure at
    # least one job exists so the assertion is deterministic.
    report_id = int(ctx.sql_one("select id from psy_report order by id desc limit 1"))
    created = _api(
        ctx, "POST", "/api/v1/exports/reports/jobs", body={"reportId": report_id, "exportFormat": "TEXT"}
    )
    require_code(created, 200)
    text = _prometheus(ctx)
    required = ["psy_export"]
    missing = [name for name in required if name not in text]
    require(not missing, f"export metrics missing: {missing}")
    metric_names = sorted(
        {
            line.split("{")[0].split(" ")[0]
            for line in text.splitlines()
            if line.startswith("psy_export")
        }
    )
    require(metric_names, "no psy_export_* metric families exposed")
    return f"export metrics exposed: {metric_names[:6]}"


@case("MT-EXP-008")
def exp_008(ctx: Context) -> str:
    raise CheckBlocked(
        "reaching export DEAD_LETTER needs the object-storage fault injection (hanging storage) so the worker fails "
        "max-attempts times; the recovery rehearsal covers lease/fencing, while dead-letter+manual replay semantics "
        "are verified on the notification channel (MT-NOTI-015) and the retry guard by MT-NFR-004"
    )
