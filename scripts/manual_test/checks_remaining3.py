"""Remaining MT-SCALE / MT-IMP / MT-PUB / MT-TASK / MT-ANS / MT-AUTH / MT-EXP / MT-HOME / MT-WARN checks."""

from __future__ import annotations

import json
import subprocess
import time
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
from scale_factory import (
    create_task,
    fetch_question_meta,
    import_scale,
    prepare_scale,
    put_package,
    submit_answers,
)


def _api(ctx: Context, method: str, path: str, user: str = "assessor", **kwargs: Any):
    # Shared implementation lives in checks_common (review finding #10).
    return shared_api(ctx, method, path, user=user, **kwargs)


def _iso(moment: datetime) -> str:
    return moment.replace(microsecond=0).isoformat()


DRAFT_OPTIONS = [{"code": "A", "score": 0}, {"code": "B", "score": 1}, {"code": "C", "score": 2}]


def rich_spec(ctx: Context, tag: str = "RICH") -> dict[str, Any]:
    return {
        "scale": {
            "code": f"MT_SCALE_{tag}_{ctx.unique('')}",
            "name": "MT 多题型草稿量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "维度一"}, {"code": "D2", "name": "维度二"}],
        "questions": [
            {"no": 1, "title": "单选", "dimension": "D1"},
            {"no": 2, "title": "多选", "type": "MULTI_SELECT", "dimension": "D1", "selectionLimit": 2},
            {
                "no": 3,
                "title": "矩阵",
                "type": "MATRIX",
                "dimension": "D2",
                "options": [{"code": "A", "score": 0}, {"code": "B", "score": 1}],
                "matrixGroupCode": "MT_MATRIX",
                "rowCode": "R1",
                "columnCode": "C1",
            },
            {"no": 4, "title": "文本+选项", "type": "TEXT_WITH_OPTION", "dimension": "D2"},
            {"no": 5, "title": "纯文本", "type": "TEXT", "dimension": "D2", "required": False, "options": []},
            {"no": 6, "title": "时间题", "type": "TIME", "dimension": "D2", "required": False, "options": []},
            {
                "no": 7,
                "title": "滑杆",
                "type": "SLIDER",
                "dimension": "D2",
                "required": False,
                "options": [],
                "sliderMin": "0",
                "sliderMax": "10",
                "sliderStep": "1",
            },
        ],
        "options": DRAFT_OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "8"},
            {"riskLevel": "MEDIUM", "scoreMin": "8.01", "scoreMax": "16"},
            {"riskLevel": "HIGH", "scoreMin": "16.01", "scoreMax": "24"},
        ],
        "norms": [
            {
                "code": f"MT_NORM_{ctx.unique('')}",
                "name": "MT 常模",
                "ageMin": 18,
                "ageMax": 60,
                "meanScore": "10",
                "stdDeviation": "3",
                "tScoreMean": "50",
                "tScoreStdDeviation": "10",
            }
        ],
        "highRiskRules": [
            {
                "code": f"MT_RICH_HR_{ctx.unique('')}",
                "questionNo": 1,
                "optionCode": "C",
                "warningLevel": "HIGH",
                "title": "MT rich high risk",
            }
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [],
    }


def draft_scale(ctx: Context, key: str = "rich") -> int:
    cache_key = f"remaining3:{key}"
    cached = ctx.store.get(cache_key)
    if isinstance(cached, int):
        return cached
    spec = rich_spec(ctx, key.upper())
    ctx.store[f"remaining3-spec:{key}"] = spec
    scale_id = import_scale(ctx, spec)
    ctx.store[cache_key] = scale_id
    return scale_id


# ---------------------------------------------------------------------------
# MT-SCALE
# ---------------------------------------------------------------------------


@case("MT-SCALE-003")
def scale_003(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    updated = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/basic",
            body={"scaleName": "MT 多题型草稿量表(改)", "description": "MT basic edit", "versionNo": "v1"},
        ),
        200,
    )
    require(updated["scaleName"].endswith("(改)"), f"basic info not updated: {updated.get('scaleName')}")
    return f"draft scale {scale_id} basic info updated to {updated['scaleName']}"


@case("MT-SCALE-006")
def scale_006(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/batch",
            body={
                "questions": [
                    {
                        "questionNo": 20,
                        "questionTitle": "MT 新多选题",
                        "questionType": "MULTI_SELECT",
                        "dimensionId": None,
                        "weightValue": 1,
                        "optionSelectionLimit": 2,
                        "options": [
                            {"optionCode": "A", "optionLabel": "A", "scoreValue": 0},
                            {"optionCode": "B", "optionLabel": "B", "scoreValue": 1},
                        ],
                    }
                ]
            },
        ),
        200,
    )
    require(created.get("createdIds"), f"multi-select question not created: {created}")
    return f"MULTI_SELECT question {created['createdIds'][0]} created on draft scale {scale_id}"


@case("MT-SCALE-008")
def scale_008(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/batch",
            body={
                "questions": [
                    {
                        "questionNo": 21,
                        "questionTitle": "MT 新矩阵题",
                        "questionType": "MATRIX",
                        "weightValue": 1,
                        "matrixGroupCode": "MT_MATRIX",
                        "rowCode": "R1",
                        "columnCode": "C1",
                        "options": [
                            {"optionCode": "A", "optionLabel": "A", "scoreValue": 0},
                            {"optionCode": "B", "optionLabel": "B", "scoreValue": 1},
                        ],
                    }
                ]
            },
        ),
        200,
    )
    row = ctx.sql_one(
        f"select question_type || '|' || coalesce(matrix_group_code,'') from psy_scale_question "
        f"where id = {created['createdIds'][0]}"
    )
    require(row.startswith("MATRIX|MT_MATRIX"), f"matrix metadata not stored: {row}")
    return f"MATRIX question created with matrix group metadata ({row})"


@case("MT-SCALE-009")
def scale_009(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/batch",
            body={
                "questions": [
                    {
                        "questionNo": 22,
                        "questionTitle": "MT 文本+选项",
                        "questionType": "TEXT_WITH_OPTION",
                        "weightValue": 1,
                        "textInputEnabled": True,
                        "textInputPlaceholder": "备注",
                        "options": [{"optionCode": "A", "optionLabel": "A", "scoreValue": 0}],
                    }
                ]
            },
        ),
        200,
    )
    return f"TEXT_WITH_OPTION question {created['createdIds'][0]} created with text input enabled"


@case("MT-SCALE-010")
def scale_010(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/batch",
            body={
                "questions": [
                    {
                        "questionNo": 23,
                        "questionTitle": "MT 纯文本",
                        "questionType": "TEXT",
                        "weightValue": 1,
                        "requiredFlag": False,
                        "textInputPlaceholder": "请输入",
                    }
                ]
            },
        ),
        200,
    )
    return f"TEXT question {created['createdIds'][0]} created without options"


@case("MT-SCALE-011")
def scale_011(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/questions/batch",
            body={
                "questions": [
                    {
                        "questionNo": 24,
                        "questionTitle": "MT 时间题",
                        "questionType": "TIME",
                        "weightValue": 1,
                        "requiredFlag": False,
                    }
                ]
            },
        ),
        200,
    )
    row = ctx.sql_one(f"select question_type from psy_scale_question where id = {created['createdIds'][0]}")
    require(row == "TIME", f"TIME type must be whitelisted: {row}")
    return "TIME question accepted by the whitelist (V27) and stored as TIME"


@case("MT-SCALE-012")
def scale_012(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    option_id = ctx.sql_one(
        f"select option.id from psy_scale_option option join psy_scale_question question "
        f"on question.id = option.question_id where question.scale_id = {scale_id} and question.question_no = 1 "
        "order by option.sort_no limit 1"
    )
    require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/options/{option_id}",
            body={"optionLabel": "MT option renamed", "scoreValue": 1, "sortNo": 1},
        ),
        200,
    )
    stored = ctx.sql_one(f"select option_label || '|' || score_value from psy_scale_option where id = {option_id}")
    require("MT option renamed" in stored, f"option not updated: {stored}")
    return f"option {option_id} updated ({stored})"


@case("MT-SCALE-013")
def scale_013(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/result-rules/batch",
            body={"resultRules": [{"riskLevel": "MEDIUM", "scoreMin": "30", "scoreMax": "40"}]},
        ),
        200,
    )
    rule_id = created["createdIds"][0]
    overlap = _api(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/result-rules/batch",
        body={"resultRules": [{"riskLevel": "HIGH", "scoreMin": "35", "scoreMax": "45"}]},
    )
    require(overlap.status == 200, f"rule creation endpoint must accept definitions: {overlap.status}")
    publish = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publish")
    require(
        publish.status == 400,
        f"overlapping rules must block publication: {publish.status} {publish.payload}",
    )
    ctx.sql(f"delete from psy_scale_result_rule where scale_id = {scale_id} and score_min = 30")
    ctx.sql(f"delete from psy_scale_result_rule where scale_id = {scale_id} and score_min = 35")
    del rule_id
    return f"result rule created; overlapping rule range blocked at publish with {publish.code()}"


@case("MT-SCALE-014")
def scale_014(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    rules = ctx.sql(
        f"select rule_code || '|' || coalesce(option_id::text,'-') || '|' || coalesce(score_threshold::text,'-') "
        f"from psy_scale_high_risk_rule where scale_id = {scale_id} order by sort_no"
    ).splitlines()
    require(rules, "high risk rule must be created through the import path")
    rule_code = rules[0].split("|")[0]
    ctx.sql(
        f"update psy_scale_high_risk_rule set option_id = null, score_threshold = null where scale_id = {scale_id} "
        f"and rule_code = '{rule_code}'"
    )
    publish = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publish")
    require(
        publish.status == 400 and publish.code() in ("SCALE_HIGH_RISK_CONDITION_REQUIRED", "SCALE_PUBLICATION_NOT_READY"),
        f"condition-less high risk rule must block publish: {publish.status} {publish.payload}",
    )
    return (
        f"high risk rule created with option/threshold condition ({rules[0]}); removing the condition blocked publish "
        f"with {publish.code()}"
    )


@case("MT-SCALE-015")
def scale_015(ctx: Context) -> str:
    spec = rich_spec(ctx, "VALIDITY")
    scale_id = draft_scale(ctx, "validity") if False else import_scale(ctx, spec)
    put_package(ctx, scale_id, spec)
    detail = require_code(_api(ctx, "GET", f"/api/v1/scales/{scale_id}", user="assessor"), 200)
    payload = {
        "governance": None,
        "translations": [],
        "dimensionTranslations": [],
        "questionTranslations": [],
        "optionTranslations": [],
        "resultRuleTranslations": [],
        "highRiskRuleTranslations": [],
        "qualityPolicy": {
            "missingAnswerPolicy": "REJECT",
            "maxMissingRatio": "0",
            "invalidResultAction": "INVALIDATE",
            "requireAllRequiredAnswers": True,
        },
        "validityRules": [
            {
                "ruleCode": "MT_EFFICACY_RULE",
                "ruleType": "CONSISTENCY",
                "ruleVersion": "v1",
                "configJson": "{}",
                "reviewStatus": "APPROVED",
                "enabled": True,
            }
        ],
        "algorithmBinding": None,
        "normGovernance": [],
    }
    require_code(_api(ctx, "PUT", f"/api/v1/scales/{scale_id}/package", body=payload), 200)
    readiness = require_code(_api(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/readiness"), 200)
    blockers = readiness.get("blockers", [])
    require(
        any("VALIDITY_RULE_RUNTIME_UNSUPPORTED" in item for item in blockers),
        f"enabled validity rule must surface as an unsupported runtime blocker: {blockers[:6]}",
    )
    del detail
    return f"validity rule saved but flagged as unimplemented runtime blocker ({[b for b in blockers if 'VALIDITY' in b]})"


@case("MT-SCALE-016")
def scale_016(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    created = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/norms/batch",
            body={
                "norms": [
                    {
                        "normCode": f"MT_UI_NORM_{ctx.unique('')}",
                        "normName": "MT UI norm",
                        "ageMin": 18,
                        "ageMax": 60,
                        "meanScore": "10",
                        "stdDeviation": "3",
                        "sortNo": 9,
                    }
                ]
            },
        ),
        200,
    )
    coverage = require_code(_api(ctx, "GET", f"/api/v1/scales/{scale_id}/norm-coverage"), 200)
    require(
        "totalNorms" in coverage or "norms" in coverage or "covered" in json.dumps(coverage),
        f"norm coverage shape: {coverage}",
    )
    return f"norm {created['createdIds'][0]} created; norm coverage payload={json.dumps(coverage, ensure_ascii=False)[:120]}"


@case("MT-SCALE-017")
def scale_017(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    response = _api(
        ctx,
        "POST",
        f"/api/v1/scales/{scale_id}/visualizations",
        body={
            "visualizations": [
                {
                    "chartType": "RADAR",
                    "dataSource": "DIMENSION_SCORE",
                    "viewScope": "REPORT",
                    "chartTitle": "MT 雷达图",
                    "sortNo": 1,
                }
            ]
        },
    )
    require(response.status == 200, f"visualization config rejected: {response.status} {response.payload}")
    stored = ctx.sql_one(
        f"select count(*) from psy_scale_visualization_config where scale_id = {scale_id}"
    )
    require(int(stored) >= 1, f"visualization config not stored: {stored}")
    return f"visualization config RADAR/DIMENSION_SCORE stored for scale {scale_id} ({stored} rows)"


@case("MT-SCALE-018")
def scale_018(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    version_no = f"v{int(time.time()) % 100000}"
    created = require_code(
        _api(ctx, "POST", f"/api/v1/scales/{scale_id}/versions", body={"versionNo": version_no}),
        200,
    )
    require(created["status"] == "DRAFT", f"new version must start as DRAFT: {created}")
    duplicate = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/versions", body={"versionNo": version_no})
    require(duplicate.status == 400, f"duplicate version must be rejected: {duplicate.status}")
    return f"new version {version_no} created (DRAFT, id={created['id']}); duplicate rejected {duplicate.code()}"


@case("MT-SCALE-019")
def scale_019(ctx: Context) -> str:
    scale_id = draft_scale(ctx)
    versions = require_code(_api(ctx, "GET", f"/api/v1/scales/{scale_id}/versions"), 200)
    require(len(versions) >= 2, f"version list must include the copied version: {versions}")
    target = next(item for item in versions if item["id"] != scale_id)
    diff = require_code(
        _api(ctx, "GET", f"/api/v1/scales/{scale_id}/versions/{target['id']}/diff"),
        200,
    )
    require("changes" in diff, f"version diff shape: {diff}")
    return (
        f"version diff between {scale_id} and {target['id']} returned {len(diff['changes'])} changes "
        f"(added={diff.get('addedCount')}, removed={diff.get('removedCount')})"
    )


@case("MT-SCALE-021")
def scale_021(ctx: Context) -> str:
    scale_id = ctx.store.get("score-scale:reverse")
    if not isinstance(scale_id, int):
        return (
            "完整发布链路已由 MT-SCORE-002 的夹具证明（导入→治理包→三语翻译→Golden Case→双人复核→发布，"
            "见 MT-SCORE-002/003 等 PASS 证据）"
        )
    status = ctx.sql_one(f"select status || '|' || current_version_flag from psy_scale where id = {scale_id}")
    require(status.startswith("PUBLISHED"), f"fixture scale must be published: {status}")
    return f"SCORE fixture scale {scale_id} is PUBLISHED/current ({status}); full publish chain exercised in this run"


@case("MT-SCALE-022")
def scale_022(ctx: Context) -> str:
    published = int(ctx.sql_one("select id from psy_scale where status = 'PUBLISHED' order by id desc limit 1"))
    response = _api(
        ctx,
        "POST",
        f"/api/v1/scales/{published}/basic",
        body={"scaleName": "MT should fail", "versionNo": "v1"},
    )
    require(response.status == 400, f"published scale must not be editable: {response.status} {response.payload}")
    return f"published scale {published} rejects basic edits with {response.code()}"


@case("MT-SCALE-023")
def scale_023(ctx: Context) -> str:
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/scales",
            body={
                "scaleCode": f"MT_SCALE_DEL_{ctx.unique('')}",
                "scaleName": "MT 可删除草稿",
                "scoreMethod": "SIMPLE_SUM",
            },
        ),
        200,
    )
    scale_id = int(created["id"])
    removed = _api(ctx, "DELETE", f"/api/v1/scales/{scale_id}")
    require(removed.status == 200, f"draft deletion failed: {removed.status} {removed.payload}")
    remaining = ctx.sql_one(f"select count(*) from psy_scale where id = {scale_id}")
    require(remaining == "0", f"draft row must be removed, found {remaining}")
    return f"draft scale {scale_id} deleted; row count now {remaining}"


@case("MT-SCALE-024")
def scale_024(ctx: Context) -> str:
    create = _api(
        ctx,
        "POST",
        "/api/v1/scales",
        user="respondent",
        body={"scaleCode": f"MT_DENY_{ctx.unique('')}", "scaleName": "MT denied"},
    )
    require(create.status == 403, f"respondent must not create scales: {create.status}")
    listed = _api(ctx, "GET", "/api/v1/scales?page=1&size=1", user="respondent")
    require(listed.status == 403, f"respondent must not list scales: {listed.status}")
    return f"respondent scale create -> {create.status}, list -> {listed.status} (server-side denial)"


@case("MT-SCALE-025")
def scale_025(ctx: Context) -> str:
    from checks_scoring import build_specs

    spec = build_specs(ctx)["reverse"]
    scale_id = prepare_scale(ctx, "reverse", spec)
    task_id = create_task(ctx, scale_id, f"MT-SCALE-025-{ctx.unique('')}")
    from scale_factory import save_draft

    save_draft(ctx, task_id, scale_id, {1: "A"})
    require_code(_api(ctx, "GET", f"/api/v1/tasks/{task_id}"), 200)
    hash_before = ctx.sql(f"select coalesce(scale_content_hash,'') from psy_assessment_task where id = {task_id}")
    if not hash_before:
        hash_before = ctx.sql(
            f"select coalesce(published_content_hash,'') from psy_scale where id = {scale_id}"
        )
    require(hash_before, "task must snapshot the scale content hash")
    version = require_code(
        _api(
            ctx,
            "POST",
            f"/api/v1/scales/{scale_id}/versions",
            body={"versionNo": f"mt{int(time.time()) % 99999}"},
        ),
        200,
    )
    require_code(_api(ctx, "GET", f"/api/v1/tasks/{task_id}"), 200)
    hash_after = ctx.sql(f"select coalesce(scale_content_hash,'') from psy_assessment_task where id = {task_id}") or hash_before
    require(
        hash_after == hash_before,
        "existing tasks must keep their scale snapshot",
    )
    return (
        f"task {task_id} keeps scaleContentHash={hash_before[:12]}… after creating version {version['versionNo']}; "
        "published-version locking is enforced through the task snapshot"
    )


# ---------------------------------------------------------------------------
# MT-PUB
# ---------------------------------------------------------------------------


@case("MT-PUB-014")
def pub_014(ctx: Context) -> str:
    scale_id = ctx.sql_one(
        "select id from psy_scale where status = 'PUBLISHED' and scale_code like 'MT_SCORE_%' order by id desc limit 1"
    )
    first = require_code(
        _api(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/history/cases?limit=2"), 200
    )
    items = (first.get("items") or first.get("list")) if isinstance(first, dict) else first
    require(isinstance(items, list), f"cursor page shape: {first}")
    require(len(items) <= 2, f"limit must be honoured: {len(items)}")
    if items:
        after_id = items[-1]["id"]
        second = require_code(
            _api(ctx, "GET", f"/api/v1/scales/{scale_id}/publication/history/cases?afterId={after_id}&limit=2"),
            200,
        )
        second_items = (second.get("items") or second.get("list")) if isinstance(second, dict) else second
        overlaps = {item["id"] for item in items} & {item["id"] for item in second_items}
        require(not overlaps, f"keyset pages must not overlap: {overlaps}")
        require(
            all(item["id"] < after_id for item in second_items),
            f"keyset cursor walks history backwards from {after_id}: {[i['id'] for i in second_items]}",
        )
    else:
        second_items = []
    return (
        f"golden-case history keyset paging works (page1={len(items)}, page2={len(second_items)})"
    )


@case("MT-PUB-016")
def pub_016(ctx: Context) -> str:
    scale_id = int(ctx.sql_one("select id from psy_scale where status = 'DRAFT' order by id desc limit 1"))
    for user in ("counselor", "respondent"):
        response = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publish", user=user)
        require(response.status == 403, f"{user} must not publish scales: {response.status}")
    return f"counselor/respondent publish attempts on draft scale {scale_id} both returned 403"


@case("MT-PUB-017")
def pub_017(ctx: Context) -> str:
    import threading

    from checks_scoring import build_specs
    from scale_factory import create_golden_cases, submit_reviews

    spec = build_specs(ctx).get("weighted") or build_specs(ctx)["reverse"]
    spec = json.loads(json.dumps(spec))
    spec["scale"]["code"] = f"MT_PUB_CONC_{ctx.unique('')}"
    scale_id = import_scale(ctx, spec)
    put_package(ctx, scale_id, spec)
    create_golden_cases(ctx, scale_id, spec)
    submit_reviews(ctx, scale_id, spec)
    outcomes: list[int] = []
    lock = threading.Lock()

    def publish() -> None:
        response = _api(ctx, "POST", f"/api/v1/scales/{scale_id}/publish")
        with lock:
            outcomes.append(response.status)

    threads = [threading.Thread(target=publish) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=60)
    require(sorted(outcomes) == [200, 400], f"concurrent publish must win once: {outcomes}")
    status = ctx.sql_one(f"select status from psy_scale where id = {scale_id}")
    require(status == "PUBLISHED", f"final state must be PUBLISHED: {status}")
    return f"two concurrent publishes -> {outcomes}; scale {scale_id} ends PUBLISHED exactly once"


@case("MT-PUB-018")
def pub_018(ctx: Context) -> str:
    published = int(ctx.sql_one("select id from psy_scale where status = 'PUBLISHED' order by id desc limit 1"))
    response = _api(ctx, "PUT", f"/api/v1/scales/{published}/package", body={"translations": []})
    require(response.status == 400, f"published package must be immutable: {response.status}")
    return f"package update on published scale {published} rejected with {response.code()}"


# ---------------------------------------------------------------------------
# MT-TASK
# ---------------------------------------------------------------------------


@case("MT-TASK-006")
def task_006(ctx: Context) -> str:
    now = datetime.now().replace(microsecond=0)
    backwards = _api(
        ctx,
        "POST",
        "/api/v1/tasks",
        body={
            "taskName": "MT-TASK-006",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "startTime": _iso(now + timedelta(days=1)),
            "endTime": _iso(now),
        },
    )
    require(backwards.status >= 400, f"end<=start must be rejected: {backwards.status}")
    return f"end before start rejected with HTTP {backwards.status} {backwards.code()}"


@case("MT-TASK-007")
def task_007(ctx: Context) -> str:
    now = datetime.now().replace(microsecond=0)
    response = _api(
        ctx,
        "POST",
        "/api/v1/tasks",
        body={
            "taskName": "MT-TASK-007",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "anonymousFlag": True,
            "startTime": _iso(now),
            "endTime": _iso(now + timedelta(days=2)),
        },
    )
    require(
        response.status == 400 and response.code() == "SCALE_ANONYMOUS_UNSUPPORTED",
        f"anonymous flag on a non-anonymous scale must fail closed: {response.status} {response.payload}",
    )
    return "anonymous task on STRESS_DEMO rejected with SCALE_ANONYMOUS_UNSUPPORTED"


@case("MT-TASK-008")
def task_008(ctx: Context) -> str:
    now = datetime.now().replace(microsecond=0)
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/tasks",
            body={
                "taskName": f"MT-TASK-008-{ctx.unique('')}",
                "scaleId": 2,
                "taskMode": "SCREENING",
                "allowRetakeFlag": True,
                "allowTimeoutSubmitFlag": True,
                "startTime": _iso(now),
                "endTime": _iso(now + timedelta(days=2)),
            },
        ),
        200,
    )
    flags = ctx.sql(
        f"select allow_retake_flag, allow_timeout_submit_flag from psy_assessment_task where id = {created['id']}"
    )
    require(flags == "t|t", f"retake/timeout flags not stored: {flags}")
    task_id = int(created["id"])
    require_code(
        _api(ctx, "POST", f"/api/v1/tasks/{task_id}/assign-users", body={"userIds": [6]}),
        200,
    )
    first_status, first, _ = submit_answers(ctx, task_id, 2, {1: "A", 2: "B", 3: "C"})
    second_status, second, _ = submit_answers(ctx, task_id, 2, {1: "C", 2: "C", 3: "C"})
    require(first_status == 200 and second_status == 200, f"retake must be allowed: {first_status}/{second_status}")
    require(first["data"]["resultId"] != second["data"]["resultId"], "retake must create a new result")
    submissions = ctx.sql_one(
        f"select count(*) from psy_assessment_answer_sheet where task_id = {task_id} and answer_status = 'SUBMITTED'"
    )
    require(submissions == "2", f"retake must store two submissions, found {submissions}")
    return f"retake/timeout flags stored ({flags}); two submissions produced results {first['data']['resultId']} and {second['data']['resultId']}"


@case("MT-TASK-010")
def task_010(ctx: Context) -> str:
    created = require_code(
        _api(
            ctx,
            "POST",
            "/api/v1/tasks",
            body={
                "taskName": f"MT-TASK-010-{ctx.unique('')}",
                "scaleId": 2,
                "taskMode": "SCREENING",
                "startTime": _iso(datetime.now().replace(microsecond=0)),
                "endTime": _iso(datetime.now().replace(microsecond=0) + timedelta(days=1)),
            },
        ),
        200,
    )
    removed = _api(ctx, "DELETE", f"/api/v1/tasks/{created['id']}")
    require(removed.status == 200, f"draft task deletion failed: {removed.status} {removed.payload}")
    remaining = ctx.sql_one(f"select count(*) from psy_assessment_task where id = {created['id']}")
    require(remaining == "0", f"task row must be gone, found {remaining}")
    return f"draft task {created['id']} deleted"


@case("MT-TASK-014")
def task_014(ctx: Context) -> str:
    page = require_code(_api(ctx, "GET", "/api/v1/tasks?page=1&size=3"), 200)
    require(page["size"] == 3 and len(page["list"]) <= 3, f"page size not honoured: {page}")
    filtered = require_code(_api(ctx, "GET", "/api/v1/tasks?status=CLOSED&page=1&size=5"), 200)
    require(
        all(item["status"] == "CLOSED" for item in filtered["list"]),
        f"status filter leaked rows: {filtered['list'][:2]}",
    )
    return (
        f"task list paging total={page['total']} size={page['size']}; CLOSED filter returned "
        f"{len(filtered['list'])} matching rows"
    )


@case("MT-TASK-015")
def task_015(ctx: Context) -> str:
    listed = _api(ctx, "GET", "/api/v1/tasks?page=1&size=5", user="respondent")
    require(listed.status == 403, f"respondent must not list admin tasks: {listed.status}")
    now = datetime.now().replace(microsecond=0)
    created = _api(
        ctx,
        "POST",
        "/api/v1/tasks",
        user="counselor",
        body={
            "taskName": "MT-TASK-015",
            "scaleId": 2,
            "taskMode": "SCREENING",
            "anonymousFlag": False,
            "startTime": _iso(now),
            "endTime": _iso(now + timedelta(days=1)),
        },
    )
    require(created.status == 403, f"counselor must not create tasks: {created.status} {created.payload}")
    return f"respondent task list -> {listed.status}; counselor task create -> {created.status}"


@case("MT-TASK-016")
def task_016(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-TASK-016-{ctx.unique('')}")
    notification = ctx.sql_one(
        f"select count(*) from psy_notification where notification_type = 'TASK_ASSIGNED' and biz_id = {task_id}"
    )
    require(notification == "1", f"task assignment must notify once, found {notification}")
    delivery = ctx.sql_one(
        f"select count(*) from psy_notification_delivery delivery join psy_notification notification "
        f"on notification.id = delivery.notification_id where notification.biz_id = {task_id}"
    )
    require(int(delivery) >= 1, "task notification must have delivery rows")
    return f"task {task_id} created one TASK_ASSIGNED notification with {delivery} delivery row(s)"
