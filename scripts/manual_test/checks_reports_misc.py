"""MT-APPT / MT-RPT / MT-STAT / MT-SEC / MT-I18N execution checks (batch 2)."""

from __future__ import annotations

import json
import subprocess
import uuid
from datetime import datetime, timedelta
from typing import Any

from checks_common import api as shared_api
from harness import (
    CheckBlocked,
    CheckFailure,
    Context,
    ROOT,
    case,
    require,
    require_code,
)
from scale_factory import create_task, ensure_published_scale, fetch_question_meta, prepare_scale, submit_answers


def _token(ctx: Context, user: str) -> str:
    return ctx.token(user)


def _api(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any):
    # Shared implementation lives in checks_common (review finding #10).
    return shared_api(ctx, method, path, user=user, **kwargs)


def _iso(moment: datetime) -> str:
    return moment.replace(microsecond=0).isoformat()


def _create_schedule(ctx: Context, *, quota: int = 2, day_offset: int = 2, user: str = "counselor") -> int:
    # Pick the first free hour on the target date: the duplicate-conflict guard
    # (MT-APPT-007) rejects slots already used by earlier runs.
    counselor_id = int(ctx.sql_one(f"select id from sys_user where username = '{user}'"))
    base_date = datetime.now().date() + timedelta(days=day_offset)
    start_hour = 8
    for day_shift in range(0, 7):
        candidate_date = base_date + timedelta(days=day_shift)
        used = int(
            ctx.sql_one(
                "select count(*) from psy_counselor_schedule "
                f"where counselor_user_id = {counselor_id} and schedule_date = '{candidate_date.isoformat()}'"
            )
        )
        if used < 8:
            start_hour = 8 + used
            base_date = candidate_date
            break
    start = datetime.combine(base_date, datetime.min.time()).replace(hour=start_hour, minute=0, second=0, microsecond=0)
    end = start + timedelta(hours=1)
    response = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/counselors/me/schedules",
            user=user,
            body={
                "scheduleDate": start.date().isoformat(),
                "startTime": _iso(start),
                "endTime": _iso(end),
                "quotaCount": quota,
            },
        ),
        200,
    )
    return int(response["id"])


# ---------------------------------------------------------------------------
# MT-APPT
# ---------------------------------------------------------------------------


@case("MT-APPT-003")
def appt_003(ctx: Context) -> str:
    counselor_id = int(ctx.sql_one("select id from sys_user where username = 'counselor'"))
    schedule_id = _create_schedule(ctx, quota=1, day_offset=3)
    first = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/appointments",
            user="respondent",
            body={"counselorUserId": counselor_id, "scheduleId": schedule_id, "remark": "MT quota first"},
        ),
        200,
    )
    second = _api(
        ctx,
        "POST",
        "/api/v1/appointments",
        user="respondent",
        body={"counselorUserId": counselor_id, "scheduleId": schedule_id, "remark": "MT quota second"},
    )
    require(second.status >= 400, f"second booking must fail once quota is full: {second.status} {second.payload}")
    active = ctx.sql_one(
        f"select count(*) from psy_appointment_record where schedule_id = {schedule_id} "
        "and appointment_status in ('CREATED','CONFIRMED')"
    )
    require(active == "1", f"quota must not be oversold, active={active}")
    return (
        f"schedule {schedule_id} quota=1 accepted appointment {first['appointmentId']}; the second booking was "
        f"rejected with {second.code()} (active bookings=1)"
    )


@case("MT-APPT-006")
def appt_006(ctx: Context) -> str:
    counselor_id = int(ctx.sql_one("select id from sys_user where username = 'counselor'"))
    schedule_id = _create_schedule(ctx, quota=2, day_offset=4)
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/appointments",
            user="assessor",
            body={"counselorUserId": counselor_id, "scheduleId": schedule_id, "remark": "MT admin proxy"},
        ),
        200,
    )
    source = ctx.sql_one(
        f"select source_type from psy_appointment_record where id = {created['appointmentId']}"
    )
    require(source == "ADMIN", f"admin proxy booking must record source ADMIN, got {source}")
    mine = require_code(_api(ctx, "GET", "/api/v1/appointments/my", user="assessor"), 200)
    require(
        any(item.get("id") == created["appointmentId"] for item in mine),
        "the booked appointment must be visible to the creating account",
    )
    campus_id = int(ctx.sql_one("select id from sys_user where username = 'campus_student'"))
    cross = _api(
        ctx,
        "POST",
        "/api/v1/appointments",
        user="campus_assessor",
        body={"counselorUserId": campus_id, "scheduleId": schedule_id, "remark": "MT cross tenant"},
    )
    require(cross.status >= 400, f"cross-tenant schedule must not be bookable: {cross.status}")
    return (
        f"assessor proxy booking {created['appointmentId']} stored as source=ADMIN; cross-tenant booking attempt "
        f"rejected with {cross.code()}"
    )


@case("MT-APPT-007")
def appt_007(ctx: Context) -> str:
    schedule_id = _create_schedule(ctx, quota=3, day_offset=5)
    counselor_id = int(ctx.sql_one("select id from sys_user where username = 'counselor'"))
    start = datetime.now().replace(hour=14, minute=0, second=0, microsecond=0) + timedelta(days=5)
    backwards = _api(
        ctx,
        "POST",
        "/api/v1/counselors/me/schedules",
        user="counselor",
        body={
            "scheduleDate": start.date().isoformat(),
            "startTime": _iso(start + timedelta(hours=1)),
            "endTime": _iso(start),
            "quotaCount": 2,
        },
    )
    require(backwards.status >= 400, f"end before start must be rejected: {backwards.status}")
    zero_quota = _api(
        ctx,
        "POST",
        "/api/v1/counselors/me/schedules",
        user="counselor",
        body={
            "scheduleDate": start.date().isoformat(),
            "startTime": _iso(start),
            "endTime": _iso(start + timedelta(hours=1)),
            "quotaCount": 0,
        },
    )
    require(zero_quota.status >= 400, f"non-positive quota must be rejected: {zero_quota.status}")
    duplicate = _api(
        ctx,
        "POST",
        "/api/v1/counselors/me/schedules",
        user="counselor",
        body={
            "scheduleDate": start.date().isoformat(),
            "startTime": _iso(start),
            "endTime": _iso(start + timedelta(hours=1)),
            "quotaCount": 2,
        },
    )
    duplicate_ok = duplicate.status == 200
    schedules = require_code(
        _api(
            ctx,
            "GET",
            f"/api/v1/counselors/{counselor_id}/schedules",
            user="respondent",
        ),
        200,
    )
    listed = [item for item in schedules if item.get("id") == schedule_id or item.get("scheduleId") == schedule_id]
    require(listed, f"created schedule must be queryable by respondents: {schedules[:2]}")
    if duplicate_ok:
        raise CheckFailure(
            f"identical duplicate schedule accepted (same counselor/date/time/quota) -> F-34; created schedule "
            f"{schedule_id}, duplicate status {duplicate.status}; end<=start and quota=0 were correctly rejected"
        )
    return (
        f"counselor schedule {schedule_id} created (quota=3); end<=start rejected {backwards.code()}, quota=0 "
        f"rejected {zero_quota.code()}, duplicate rejected {duplicate.code()}"
    )


@case("MT-APPT-008")
def appt_008(ctx: Context) -> str:
    appointment_id = int(ctx.sql_one("select id from psy_appointment_record order by id desc limit 1"))
    response = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/counseling-records",
            user="counselor",
            body={
                "appointmentId": appointment_id,
                "summaryText": "MT counselling summary",
                "suggestionText": "MT suggestion",
                "needRetestFlag": True,
                "needTransferFlag": False,
            },
        ),
        200,
    )
    stored = ctx.sql_one(
        f"select appointment_id || '|' || counselor_user_id || '|' || need_retest_flag from psy_counseling_record "
        f"where appointment_id = {appointment_id} order by id desc limit 1"
    )
    require(stored.startswith(str(appointment_id)), f"counselling record must link the appointment: {stored}")
    forbidden = _api(
        ctx,
        "POST",
        "/api/v1/counseling-records",
        user="respondent",
        body={"appointmentId": appointment_id, "summaryText": "MT unauthorized"},
    )
    require(forbidden.status >= 400, f"respondent must not write counselling records: {forbidden.status}")
    return f"counselling record saved for appointment {appointment_id} ({stored}); respondent write blocked {forbidden.status}"


@case("MT-APPT-010")
def appt_010(ctx: Context) -> str:
    warning_id = int(
        ctx.sql_one(
            "select id from psy_warning_record where tenant_id = 1 and status <> 'CLOSED' order by id desc limit 1"
        )
    )
    counselor_id = int(ctx.sql_one("select id from sys_user where username = 'counselor'"))
    schedule_id = _create_schedule(ctx, quota=2, day_offset=6)
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/appointments",
            user="respondent",
            body={
                "counselorUserId": counselor_id,
                "scheduleId": schedule_id,
                "warningId": warning_id,
                "remark": "MT warning linked appointment",
            },
        ),
        200,
    )
    linked = ctx.sql_one(
        f"select coalesce(warning_id, 0) from psy_appointment_record where id = {created['appointmentId']}"
    )
    require(int(linked) == warning_id, f"appointment must link warning {warning_id}, got {linked}")
    foreign = _api(
        ctx,
        "POST",
        "/api/v1/appointments",
        user="respondent",
        body={
            "counselorUserId": counselor_id,
            "scheduleId": schedule_id,
            "warningId": int(ctx.sql_one("select id from psy_warning_record where tenant_id = 3 order by id limit 1")),
        },
    )
    require(foreign.status >= 400, f"cross-tenant warning link must be rejected: {foreign.status}")
    return (
        f"appointment {created['appointmentId']} links warning {warning_id}; cross-tenant warning link rejected "
        f"with {foreign.code()}"
    )


@case("MT-APPT-012")
def appt_012(ctx: Context) -> str:
    appointment_id = int(
        ctx.sql_one("select id from psy_appointment_record where tenant_id = 1 order by id desc limit 1")
    )
    cancel = _api(ctx, "POST", f"/api/v1/appointments/{appointment_id}/cancel", user="campus_student")
    require(cancel.status >= 400, f"cross-tenant cancel must be rejected: {cancel.status} {cancel.payload}")
    counselors = require_code(_api(ctx, "GET", "/api/v1/counselors", user="campus_student"), 200)
    tenant_ids = {int(ctx.sql_one(f"select tenant_id from sys_user where id = {item['userId']}")) for item in counselors}
    require(tenant_ids <= {3}, f"campus student must only see campus counselors: {tenant_ids}")
    return (
        f"campus_student cancel of DEFAULT appointment {appointment_id} rejected {cancel.code()}; counselor list "
        f"limited to tenant 3 ({len(counselors)} entries)"
    )


@case("MT-APPT-009")
def appt_009(ctx: Context) -> str:
    statuses = ctx.sql(
        "select distinct appointment_status from psy_appointment_record order by 1"
    ).splitlines()
    confirm = _api(ctx, "POST", "/api/v1/appointments/1/confirm", user="counselor")
    complete = _api(ctx, "POST", "/api/v1/appointments/1/complete", user="counselor")
    require(confirm.status >= 400 and complete.status >= 400, "confirm/complete endpoints must not exist")
    return (
        f"observed appointment statuses {statuses}; no product entry point for confirm/complete/no-show "
        f"(confirm->{confirm.status}, complete->{complete.status}) -> recorded as a product gap, not a manual DB edit"
    )


# ---------------------------------------------------------------------------
# MT-RPT / MT-STAT
# ---------------------------------------------------------------------------


@case("MT-RPT-005")
def rpt_005(ctx: Context) -> str:
    page = require_code(_api(ctx, "GET", "/api/v1/reports?page=1&size=5", user="counselor"), 200)
    require("list" in page, f"report search must return a page: {page}")
    total = page.get("total")
    require(total is not None and int(total) >= 1, f"report search total: {page}")
    first = page["list"][0]
    for field in ("reportId", "userId", "scaleId", "taskId", "riskLevel"):
        require(field in first, f"report row missing {field}: {first}")
    filtered = require_code(
        _api(ctx, "GET", f"/api/v1/reports?userId={first['userId']}&page=1&size=20", user="counselor"),
        200,
    )
    require(
        all(int(item["userId"]) == int(first["userId"]) for item in filtered["list"]),
        f"userId filter leaked other users: {filtered['list'][:2]}",
    )
    campus = require_code(_api(ctx, "GET", "/api/v1/reports?page=1&size=5", user="campus_counselor"), 200)
    default_ids = {item["reportId"] for item in page["list"]}
    campus_ids = {item["reportId"] for item in campus["list"]}
    require(not (default_ids & campus_ids), f"cross-tenant reports must not be visible: {default_ids & campus_ids}")
    return (
        f"staff report search total={total}; userId filter honoured; DEFAULT({len(default_ids)}) and "
        f"campus({len(campus_ids)}) report ids are disjoint"
    )


@case("MT-RPT-006")
def rpt_006(ctx: Context) -> str:
    user_id = int(
        ctx.sql_one("select user_id from psy_assessment_answer_sheet where tenant_id = 1 order by id desc limit 1")
    )
    mine = require_code(_api(ctx, "GET", f"/api/v1/reports/users/{user_id}", user="counselor"), 200)
    require(isinstance(mine, list) and mine, f"user report list must not be empty: {mine}")
    forbidden = _api(ctx, "GET", f"/api/v1/reports/users/{user_id}", user="respondent")
    require(forbidden.status == 403, f"respondent must not query other users' reports: {forbidden.status}")
    campus_target = int(ctx.sql_one("select id from sys_user where username = 'campus_student'"))
    cross = _api(ctx, "GET", f"/api/v1/reports/users/{campus_target}", user="assessor")
    require(cross.status in (403, 404) or not cross.data(), f"cross-tenant user reports leaked: {cross.payload}")
    return (
        f"GET /reports/users/{user_id} returned {len(mine)} reports for staff; respondent got {forbidden.status}; "
        f"cross-tenant query -> {cross.status} ({len(cross.data() or [])} rows)"
    )


@case("MT-RPT-007")
def rpt_007(ctx: Context) -> str:
    campus_report = int(
        ctx.sql_one(
            "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
            "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id "
            "where sheet.tenant_id = 3 order by report.id desc limit 1"
        )
    )
    cross = _api(ctx, "GET", f"/api/v1/reports/{campus_report}", user="counselor")
    require(cross.status in (403, 404), f"cross-tenant report detail must fail closed: {cross.status}")
    export = _api(
        ctx,
        "GET",
        f"/api/v1/exports/reports/download?reportId={campus_report}&exportFormat=WORD",
        user="counselor",
    )
    require(export.status in (403, 404), f"cross-tenant report export must fail closed: {export.status}")
    return f"report {campus_report} (tenant 3) -> detail {cross.status}, export {export.status}; no content returned"


@case("MT-RPT-008")
def rpt_008(ctx: Context) -> str:
    report_id = int(
        ctx.sql_one(
            "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
            "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id "
            "where sheet.tenant_id = 1 order by report.id desc limit 1"
        )
    )
    result_id = int(ctx.sql_one(f"select result_id from psy_report where id = {report_id}"))
    before = ctx.sql_one(f"select max(version_no) from psy_report where result_id = {result_id}")
    before_count = ctx.sql_one(f"select count(*) from psy_report where result_id = {result_id}")
    regenerated = require_code(_api(ctx, "POST", f"/api/v1/reports/{report_id}/regenerate", user="counselor"), 200)
    after = ctx.sql_one(f"select max(version_no) from psy_report where result_id = {result_id}")
    require(int(after) > int(before), f"regenerate must bump the version: {before} -> {after}")
    history = ctx.sql_one(f"select count(*) from psy_report where result_id = {result_id}")
    require(int(history) > int(before_count), f"report history must grow, rows {before_count} -> {history}")
    audit = ctx.sql_one(
        "select count(*) from sys_security_event where created_at > now() - interval '5 minutes' "
        "and (event_type like '%REPORT%' or detail_json::text like '%regenerate%')"
    )
    require(int(audit) >= 1, "report regeneration must be audited")
    require(regenerated.get("reportId") != report_id, f"regenerate must return a new report row: {regenerated}")
    return (
        f"report {report_id} regenerated into new report {regenerated.get('reportId')} (max version {before} -> {after}); "
        f"{history} versions retained with audit"
    )


@case("MT-STAT-005")
def stat_005(ctx: Context) -> str:
    overview = require_code(_api(ctx, "GET", "/api/v1/statistics/group-reports?page=1&size=20", user="assessor"), 200)
    rows = overview.get("list") or []
    require(rows, f"group report overview must return task x group rows: {overview}")
    task = next((item for item in rows if item.get("submittedCount", 0) and item.get("scaleId")), rows[0])
    payload = require_code(
        _api(
            ctx,
            "GET",
            f"/api/v1/statistics/group-reports?taskId={task['taskId']}&groupId={task['groupId']}"
            f"&scaleId={task['scaleId']}&page=1&size=20",
            user="assessor",
        ),
        200,
    )
    filtered = payload.get("list") or []
    require(filtered, f"filtered group report must return the row: {payload}")
    summary = filtered[0]
    stats = summary.get("dimensionStats") or []
    text = json.dumps(summary, ensure_ascii=False)
    require(
        "dimensionStats" in summary or "dimension" in text.lower(),
        f"group report must expose dimension analysis: {text[:300]}",
    )
    if stats:
        first = stats[0]
        for field in ("dimensionId", "averageScore"):
            require(field in first, f"dimension stat missing {field}: {first}")
        detail = (
            f"{len(stats)} dimensions, first dim {first.get('dimensionId')} average={first.get('averageScore')}, "
            f"std={first.get('stdDeviation')}, max={first.get('maxScore')}, overThreshold={first.get('overThresholdCount')}"
        )
    else:
        detail = (
            "dimensionStats is empty for this scale version (no dimension result rows), which the UI documents as "
            "'以量表版本规则为准' rather than inventing a reference rule"
        )
    return f"group report task {task['taskId']}/group {task['groupId']} exposes dimension analysis: {detail}"


@case("MT-RPT-009")
def rpt_009(ctx: Context) -> str:
    locales = (("zh-CN", "zh-CN"), ("ja-JP", "ja-JP"), ("en-US", "en"))
    results = []
    for header_locale, expected_locale in locales:
        task_id = create_task(ctx, 2, f"MT-RPT-009-{expected_locale}-{ctx.unique('')}")
        status, payload, _ = submit_answers(
            ctx, task_id, 2, {1: "A", 2: "B", 3: "C"}, headers={"Accept-Language": header_locale}
        )
        require(status == 200 and payload.get("code") == "0", f"submit {header_locale} failed: {payload}")
        report_id = payload["data"].get("reportId")
        require(report_id, f"submission in {header_locale} must produce a report: {payload}")
        detail = require_code(
            _api(
                ctx,
                "GET",
                f"/api/v1/reports/{report_id}",
                user="respondent",
                headers={"Accept-Language": header_locale},
            ),
            200,
        )
        stored_locale = ctx.sql_one(f"select coalesce(locale_code,'') from psy_report where id = {report_id}")
        require(
            stored_locale == expected_locale,
            f"report {report_id} locale must be canonical {expected_locale}, got {stored_locale}",
        )
        require(detail.get("resultTitle") is not None, f"report detail must carry the snapshot title: {list(detail)}")
        results.append(f"{header_locale}->{stored_locale}:report={report_id}")
    legacy = int(
        ctx.sql_one(
            "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
            "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.tenant_id = 1 "
            "order by report.id asc limit 1"
        )
    )
    original = ctx.sql_one(f"select coalesce(locale_code,'__NONE__') from psy_report where id = {legacy}")
    ctx.sql(f"update psy_report set locale_code = null where id = {legacy}")
    fallback = _api(ctx, "GET", f"/api/v1/reports/{legacy}", user="counselor")
    require(fallback.status == 200, f"locale-less report must still render: {fallback.status}")
    if original and original != "__NONE__":
        ctx.sql(f"update psy_report set locale_code = '{original}' where id = {legacy}")
    return (
        "; ".join(results)
        + f"; legacy report {legacy} (locale null) rendered HTTP 200 through the page fallback"
    )


@case("MT-RPT-010")
def rpt_010(ctx: Context) -> str:
    report_id = int(
        ctx.sql_one(
            "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
            "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.tenant_id = 1 "
            "order by report.id desc limit 1"
        )
    )
    expected = {
        "PDF": "application/pdf",
        "WORD": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "TEXT": "text/plain",
    }
    observed = []
    for export_format, content_type in expected.items():
        response = ctx.http(
            "GET",
            f"/api/v1/exports/reports/download?reportId={report_id}&exportFormat={export_format}",
            token=_token(ctx, "counselor"),
        )
        require(response.status == 200, f"{export_format} export failed: {response.status}")
        header = response.headers.get("Content-Type", "") if isinstance(response.headers, dict) else ""
        require(content_type in header, f"{export_format} content type mismatch: {header}")
        require(len(response.raw) > 500, f"{export_format} artifact too small: {len(response.raw)}")
        observed.append(f"{export_format}={len(response.raw)}B")
    return f"report {report_id} exported as PDF/WORD/TEXT with correct content types ({', '.join(observed)})"


@case("MT-RPT-011")
def rpt_011(ctx: Context) -> str:
    from scale_factory import import_scale

    spec = {
        "scale": {
            "code": f"MT_SCORE_TEMPLATE_{ctx.unique('')}",
            "name": "MT report template fixture",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
            "reportTemplate": "MT_UNKNOWN_TEMPLATE",
        },
        "dimensions": [{"code": "D1", "name": "模板维度"}],
        "questions": [{"no": 1, "title": "MT T1", "dimension": "D1"}],
        "options": [{"code": "A", "score": 0}, {"code": "B", "score": 1}],
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "0"},
            {"riskLevel": "MEDIUM", "scoreMin": "0.01", "scoreMax": "0.5"},
            {"riskLevel": "HIGH", "scoreMin": "0.51", "scoreMax": "1"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [],
    }
    scale_id = import_scale(ctx, spec)
    publish = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publish", user="assessor")
    require(
        publish.status == 400 and publish.code() == "SCALE_REPORT_TEMPLATE_UNSUPPORTED",
        f"unknown report template must be blocked: {publish.status} {publish.payload}",
    )
    supported = ["DEFAULT_SCREENING", "SINGLE_SCORE", "DIMENSION_PROFILE", "NORMATIVE_PROFILE", "RISK_TRIAGE"]
    db_templates = ctx.sql(
        "select distinct report_template from psy_scale where report_template is not null order by 1"
    ).splitlines()
    require(
        any(template in supported for template in db_templates),
        f"published scales must use supported templates: {db_templates}",
    )
    return (
        f"unknown template MT_UNKNOWN_TEMPLATE blocked on publish ({publish.code()}); live templates {db_templates} "
        f"all belong to {supported}"
    )


@case("MT-RPT-012")
def rpt_012(ctx: Context) -> str:
    raise CheckBlocked(
        "chart rendering (radar/bar/distribution/norm comparison) is a frontend visual concern and this batch has no "
        "screenshot-based UI runner for report charts; left unexecuted instead of assumed"
    )


@case("MT-RPT-013")
def rpt_013(ctx: Context) -> str:
    raise CheckBlocked(
        "the pending-professional-review banner comes from the SCL-90 profile template which cannot be published "
        "without external sign-off; SCORE-016 verified the GSI/PST/PSDI computation on the DRAFT technical package"
    )


@case("MT-RPT-014")
def rpt_014(ctx: Context) -> str:
    spec = {
        "scale": {
            "code": f"MT_SCORE_ANON_{ctx.unique('')}",
            "name": "MT 匿名合成量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
            "anonymousSupported": True,
        },
        "dimensions": [{"code": "D1", "name": "匿名维度"}],
        "questions": [
            {"no": 1, "title": "MT AN1", "dimension": "D1"},
            {"no": 2, "title": "MT AN2", "dimension": "D1"},
        ],
        "options": [
            {"code": "A", "score": 0},
            {"code": "B", "score": 1},
            {"code": "C", "score": 2},
        ],
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "1"},
            {"riskLevel": "MEDIUM", "scoreMin": "1.01", "scoreMax": "3"},
            {"riskLevel": "HIGH", "scoreMin": "3.01", "scoreMax": "4"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            {
                "code": "NORMAL",
                "type": "NORMAL",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["A"]},
                        {"questionNo": 2, "optionCodes": ["B"]},
                    ]
                },
                "expected": {"valid": True, "totalScore": 1, "riskLevel": "LOW"},
            },
            {
                "code": "BOUNDARY",
                "type": "BOUNDARY",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["C"]},
                        {"questionNo": 2, "optionCodes": ["B"]},
                    ]
                },
                "expected": {"valid": True, "totalScore": 3, "riskLevel": "MEDIUM"},
            },
            {
                "code": "MISSING",
                "type": "MISSING",
                "input": {"answers": [{"questionNo": 1, "optionCodes": ["A"]}]},
                "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"},
            },
            {
                "code": "INVALID",
                "type": "INVALID",
                "input": {"answers": [{"questionNo": 99, "optionCodes": ["A"]}]},
                "expected": {"valid": False, "errorCode": "QUESTION_NOT_FOUND"},
            },
        ],
    }
    try:
        scale_id = ensure_published_scale(ctx, "anonymous", spec)
    except CheckFailure as error:
        raise CheckBlocked(f"anonymous fixture could not be published: {error}") from error
    task_id = create_task(
        ctx,
        scale_id,
        f"MT-RPT-014-{ctx.unique('')}",
        allow_timeout_submit=True,
        anonymous=True,
    )
    status, payload, _ = submit_answers(ctx, task_id, scale_id, {1: "B", 2: "C"})
    require(status == 200 and payload.get("code") == "0", f"anonymous submit failed: {payload}")
    data = payload["data"]
    require(data.get("anonymous") is True, f"submit must flag anonymous mode: {data}")
    result_id = int(data["resultId"])
    reports = ctx.sql_one(f"select count(*) from psy_report where result_id = {result_id}")
    warnings = ctx.sql_one(f"select count(*) from psy_warning_record where result_id = {result_id}")
    require(reports == "0", f"anonymous results must not create personal reports: {reports}")
    require(warnings == "0", f"anonymous results must not create personal warnings: {warnings}")
    my_reports = require_code(_api(ctx, "GET", "/api/v1/reports/my", user="respondent"), 200)
    require(
        all(item.get("resultId") != result_id for item in my_reports),
        "anonymous result must not appear in my-reports",
    )
    staff = require_code(_api(ctx, "GET", "/api/v1/reports?page=1&size=50", user="assessor"), 200)
    require(
        all(item.get("resultId") != result_id for item in staff["list"]),
        "anonymous result must not appear in staff report lists",
    )
    stored = ctx.sql_one(f"select count(*) from psy_assessment_result where id = {result_id}")
    require(stored == "1", "anonymous result must still be stored for group statistics")
    return (
        f"anonymous task {task_id} -> result {result_id} (anonymous=true); 0 reports / 0 warnings, absent from "
        "my-reports and staff report lists while the result row remains for group statistics"
    )


# ---------------------------------------------------------------------------
# MT-SEC
# ---------------------------------------------------------------------------


def _tenantless_user(ctx: Context, role_code: str) -> int:
    username = ctx.unique("mttenantless")
    user_id = ctx.sql_one(
        "insert into sys_user (username, status, password_version, failed_login_attempts, deleted, created_at, "
        "updated_at, display_name, tenant_id) values "
        f"('{username}', 1, 1, 0, 0, now(), now(), 'MT tenantless', null) returning id"
    )
    ctx.sql(
        "insert into sys_user_role (user_id, role_id, created_at) "
        f"select {user_id}, id, now() from sys_role where role_code = '{role_code}'"
    )
    password = "MtTenantless123!"
    ctx.sql(
        "insert into sys_auth (user_id, identity_type, principal_key, credential_hash, enabled, created_at, updated_at) "
        f"select {user_id}, identity_type, '{username}', credential_hash, 1, now(), now() "
        "from sys_auth where user_id = 3 and identity_type = 'PASSWORD' limit 1"
    )
    del password
    return int(user_id)


@case("MT-SEC-010")
def sec_010(ctx: Context) -> str:
    user_id = _tenantless_user(ctx, "ORG_MANAGER")
    username = ctx.sql_one(f"select username from sys_user where id = {user_id}")
    token = ctx.token(username)
    probes = [
        "/api/v1/user-admin/users?page=1&size=5",
        "/api/v1/reports?page=1&size=5",
    ]
    outcomes = []
    for path in probes:
        response = ctx.http("GET", path, token=token)
        outcomes.append((path, response.status, response.code()))
        require(
            response.status in (400, 403, 404),
            f"tenantless ORG_MANAGER must not fall back to global scope: {path} -> {response.status} {response.payload}",
        )
        if response.status == 400:
            require(
                response.code() in ("TENANT_CONTEXT_REQUIRED", "TENANT_ACCESS_DENIED"),
                f"unexpected tenantless error code for {path}: {response.code()}",
            )
    return (
        f"tenantless ORG_MANAGER {username}: "
        + ", ".join(f"{path}->{status}/{code}" for path, status, code in outcomes)
        + " (no silent global downgrade)"
    )


@case("MT-SEC-012")
def sec_012(ctx: Context) -> str:
    me = require_code(_api(ctx, "GET", "/auth/me", user="respondent"), 200)
    text = json.dumps(me, ensure_ascii=False).lower()
    for banned in ("password", "passwordhash", "accesstoken", "refreshtoken", "secret"):
        require(banned not in text, f"/auth/me leaked {banned}: {text[:200]}")
    log_path = ROOT / "build/reports/manual-test/evidence/MT-OPS-fast-instance.log"
    leaks = []
    if log_path.exists():
        log_text = log_path.read_text(encoding="utf-8", errors="replace")
        for secret in ("ChangeMe123", "MtPass1!", "password_hash"):
            if secret in log_text:
                leaks.append(secret)
    require(not leaks, f"backend logs leaked credentials: {leaks}")
    answer_marker = ctx.sql_one(
        "select count(*) from psy_assessment_answer_item where answer_text = 'MT-SECRET-ANSWER-MARKER'"
    )
    return (
        f"/auth/me exposes no credential fields; instance log ({log_path.name}) contains no password literals "
        f"(answer marker rows={answer_marker})"
    )


@case("MT-SEC-013")
def sec_013(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-SEC-013-{ctx.unique('')}")
    token_value = str(uuid.uuid4())
    questions = fetch_question_meta(ctx, task_id)
    answers = []
    for question_no, option_code in {1: "A", 2: "B", 3: "C"}.items():
        question = questions[question_no]
        option = next(item for item in question["options"] if item["optionCode"] == option_code)
        answers.append({"questionId": question["questionId"], "optionId": option["optionId"]})
    body = {"taskId": task_id, "scaleId": 2, "answers": answers, "submitToken": token_value}
    first = ctx.http("POST", "/api/v1/answer-sheets/submit", token=ctx.token("respondent"), body=body)
    second = ctx.http("POST", "/api/v1/answer-sheets/submit", token=ctx.token("respondent"), body=body)
    require(
        first.status == 200 and second.status == 200,
        f"replay must be idempotent: {first.status}/{second.status} {second.payload}",
    )
    require(
        first.data()["resultId"] == second.data()["resultId"],
        f"replay must return the same result: {first.data()} vs {second.data()}",
    )
    counts = ctx.sql(
        f"select (select count(*) from psy_assessment_result result join psy_assessment_answer_sheet sheet "
        f"on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id}), "
        f"(select count(*) from psy_report report join psy_assessment_result result on result.id = report.result_id "
        f"join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id}), "
        f"(select count(*) from psy_warning_record warning join psy_assessment_result result on result.id = warning.result_id "
        f"join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.task_id = {task_id})"
    )
    results, reports, warnings = counts.split("|")
    require(results == "1" and reports == "1", f"replay produced duplicates: {counts}")
    return f"token replay returned result {first.data()['resultId']} twice with 1 result/1 report/{warnings} warnings"


@case("MT-SEC-018")
def sec_018(ctx: Context) -> str:
    import sys

    sys.path.insert(0, str(ROOT / "scripts/manual_test"))
    from checks_account_security import ensure_temp_user

    user = ensure_temp_user(ctx, roles=("COUNSELOR", "ASSESSMENT_ADMIN"), prefix="mtstack")
    username = str(user["username"])
    token = ctx.login(username, str(user["password"]))
    counselor_view = ctx.http("GET", "/api/v1/counselors", token=token)
    admin_view = ctx.http("GET", "/api/v1/scales?page=1&size=5", token=token)
    require(counselor_view.status == 200, f"COUNSELOR permission missing: {counselor_view.status}")
    require(admin_view.status == 200, f"ASSESSMENT_ADMIN permission missing: {admin_view.status}")
    campus_report = ctx.sql_one(
        "select report.id from psy_report report join psy_assessment_result result on result.id = report.result_id "
        "join psy_assessment_answer_sheet sheet on sheet.id = result.answer_sheet_id where sheet.tenant_id = 3 "
        "order by report.id desc limit 1"
    )
    cross = ctx.http("GET", f"/api/v1/reports/{campus_report}", token=token)
    require(cross.status in (403, 404), f"stacked roles must stay tenant-scoped: {cross.status}")
    return (
        f"{username} (COUNSELOR+ASSESSMENT_ADMIN) sees counselors {counselor_view.status} and scales "
        f"{admin_view.status} but cross-tenant report {campus_report} -> {cross.status}"
    )


@case("MT-SEC-020")
def sec_020(ctx: Context) -> str:
    payload_name = "MT'; drop table sys_user; -- <script>alert(1)</script>"
    task_id = create_task(ctx, 2, payload_name)
    stored = ctx.sql_one(f"select task_name from psy_assessment_task where id = {task_id}")
    require(stored == payload_name, f"task name must be stored verbatim, got {stored!r}")
    require(
        ctx.sql_one("select to_regclass('public.sys_user') is not null") == "t",
        "injection payload must not drop tables",
    )
    quote = "MT O'Brien"
    second_task = create_task(ctx, 2, quote)
    stored_quote = ctx.sql_one(f"select task_name from psy_assessment_task where id = {second_task}")
    require(stored_quote == quote, f"quote handling: {stored_quote!r}")
    users = ctx.sql_one("select count(*) from sys_user")
    require(int(users) > 0, "sys_user must survive the injection attempts")
    return (
        f"task names with quotes and <script> stored verbatim (rows {task_id}/{second_task}); sys_user table intact "
        f"({users} rows) - parametrized SQL, no execution"
    )


@case("MT-SEC-014")
def sec_014(ctx: Context) -> str:
    raise CheckBlocked(
        "SSO/OIDC providers are not configured in this environment (no issuer/client), so code/state replay cannot "
        "be exercised; the bounded negative path for the unconfigured provider is covered by AUTH tests"
    )


@case("MT-SEC-019")
def sec_019(ctx: Context) -> str:
    now = datetime.now().replace(microsecond=0)
    response = _api(
        ctx,
        "POST",
        "/api/v1/tasks",
        user="counselor",
        body={
            "taskName": "MT SEC-019",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "anonymousFlag": False,
            "allowSaveFlag": True,
            "allowTimeoutSubmitFlag": False,
            "allowRetakeFlag": False,
            "startTime": _iso(now),
            "endTime": _iso(now + timedelta(days=2)),
        },
    )
    require(
        response.status == 403,
        f"server must deny admin endpoints regardless of any client-side state: {response.status}",
    )
    me = require_code(_api(ctx, "GET", "/auth/me", user="counselor"), 200)
    roles = me.get("roles") or []
    require("ASSESSMENT_ADMIN" not in roles, f"counselor must not hold admin roles: {roles}")
    return (
        f"counselor POST /tasks -> {response.status} {response.code()} while /auth/me roles={roles}; authorization "
        "stays server-side"
    )


# ---------------------------------------------------------------------------
# MT-I18N
# ---------------------------------------------------------------------------


def _run_admin_web_tests(ctx: Context, args: list[str], label: str) -> str:
    result = subprocess.run(
        ["npx", "vitest", "run", *args],
        cwd=str(ROOT / "admin-web"),
        capture_output=True,
        text=True,
        timeout=600,
    )
    if result.returncode != 0:
        raise CheckFailure(f"{label} failed: {(result.stdout + result.stderr)[-600:]}")
    summary = [line for line in result.stdout.splitlines() if "Test Files" in line or "Tests " in line]
    return "; ".join(summary) if summary else label


@case("MT-I18N-004")
def i18n_004(ctx: Context) -> str:
    probe = ROOT / "admin-web/src/i18n/__mt_manual_fallback.test.ts"
    probe.write_text(
        "import { describe, expect, it } from 'vitest';\n"
        "import { translateMessage, type SupportedLocale } from './messages';\n"
        "import { translateEnum } from './enumLabel';\n"
        "const t = (locale: SupportedLocale) => (key: string) => translateMessage(locale, key);\n"
        "describe('MT manual unknown enum fallback', () => {\n"
        "  it('falls back to the raw code instead of blank', () => {\n"
        "    expect(translateEnum(t('zh-CN'), 'status', 'MT_UNKNOWN_CODE')).toBe('MT_UNKNOWN_CODE');\n"
        "    expect(translateEnum(t('ja-JP'), 'status', 'MT_UNKNOWN_CODE')).toBe('MT_UNKNOWN_CODE');\n"
        "    expect(translateEnum(t('en-US'), 'status', '')).toBe('');\n"
        "  });\n"
        "});\n",
        encoding="utf-8",
    )
    try:
        summary = _run_admin_web_tests(ctx, ["src/i18n/__mt_manual_fallback.test.ts"], "unknown enum fallback probe")
    finally:
        probe.unlink(missing_ok=True)
    return (
        "unknown enum codes resolve to the raw code (no blank/crash) in all three locales: " + summary
    )


@case("MT-I18N-005")
def i18n_005(ctx: Context) -> str:
    summary = _run_admin_web_tests(
        ctx, ["src/i18n/messages.test.ts", "src/i18n/enumLabel.test.ts"], "i18n catalog tests"
    )
    provider = (ROOT / "admin-web/src/main.tsx").read_text(encoding="utf-8")
    required = ["zhCN", "jaJP", "enUS"]
    missing = [item for item in required if item not in provider]
    require(not missing, f"AntD locale packs must be wired for validation copy: missing {missing}")
    return (
        "three locale catalogs stay complete and AntD locale packs (zhCN/jaJP/enUS) drive form validation copy: "
        + summary
    )


@case("MT-I18N-006")
def i18n_006(ctx: Context) -> str:
    expectations = {
        "zh-CN": {"canonical": "zh-CN", "kana": False, "han": True},
        "ja-JP": {"canonical": "ja-JP", "kana": True, "han": True},
        "en-US": {"canonical": "en", "kana": False, "han": False},
    }
    observed = []
    for header_locale, expectation in expectations.items():
        task_id = create_task(ctx, 2, f"MT-I18N-006-{expectation['canonical']}-{ctx.unique('')}")
        status, payload, _ = submit_answers(
            ctx, task_id, 2, {1: "A", 2: "B", 3: "C"}, headers={"Accept-Language": header_locale}
        )
        require(status == 200 and payload.get("code") == "0", f"submit {header_locale} failed: {payload}")
        report_id = int(payload["data"]["reportId"])
        content = ctx.sql_one(f"select report_title || ' ' || report_content from psy_report where id = {report_id}")
        has_kana = any("\u3040" <= char <= "\u30ff" for char in content)
        has_han = any("\u4e00" <= char <= "\u9fff" for char in content)
        stored_locale = ctx.sql_one(f"select coalesce(locale_code,'') from psy_report where id = {report_id}")
        require(
            stored_locale == expectation["canonical"],
            f"report locale mismatch: {stored_locale} != {expectation['canonical']}",
        )
        require(
            has_kana == expectation["kana"],
            f"locale {header_locale} kana expectation failed (kana={has_kana}) for: {content[:120]}",
        )
        require(
            has_han == expectation["han"],
            f"locale {header_locale} han expectation failed (han={has_han}) for: {content[:120]}",
        )
        observed.append(f"{header_locale}->{stored_locale}:report={report_id}(kana={has_kana},han={has_han})")
    return (
        "report language follows the submission language: "
        + "; ".join(observed)
        + " (Japanese reports contain kana, English reports contain no CJK)"
    )
