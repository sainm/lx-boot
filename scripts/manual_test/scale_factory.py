"""Setup helpers for the MT-SCORE manual cases.

The scoring procedure needs synthetic scales that cover every score method and
quality policy.  This module builds those scales through the *product* import
and publication APIs (never through direct SQL writes) so the scored results
come from a governed, published scale exactly like a real deployment.
"""

from __future__ import annotations

import io
import json
import copy
import time
import uuid
from datetime import datetime, timedelta
from typing import Any

import openpyxl

from harness import CheckBlocked, CheckFailure, Context, require, require_code

SCALE_HEADERS = [
    "scaleCode",
    "scaleName",
    "description",
    "applicableTarget",
    "versionNo",
    "scoreMethod",
    "scoreCoefficient",
    "anonymousSupported",
    "reportTemplate",
    "normStrategy",
    "normDefaultGroup",
    "highRiskWarningEnabled",
]
DIMENSION_HEADERS = ["dimensionCode", "dimensionName", "description", "sortNo"]
QUESTION_HEADERS = [
    "questionNo",
    "questionTitle",
    "questionType",
    "dimensionCode",
    "requiredFlag",
    "reverseScoreFlag",
    "weightValue",
    "sortNo",
    "optionSelectionLimit",
    "sliderMin",
    "sliderMax",
    "sliderStep",
    "textInputEnabled",
    "textInputPlaceholder",
    "matrixGroupCode",
    "rowCode",
    "columnCode",
]
OPTION_HEADERS = [
    "questionNo",
    "optionCode",
    "optionLabel",
    "scoreValue",
    "sortNo",
    "exclusiveFlag",
    "optionGroupCode",
]
RULE_HEADERS = [
    "dimensionCode",
    "riskLevel",
    "scoreMin",
    "scoreMax",
    "resultTitle",
    "resultDescription",
    "suggestionText",
    "sortNo",
    "scoreSource",
    "normCode",
]
NORM_HEADERS = [
    "normCode",
    "normName",
    "dimensionCode",
    "applicableTarget",
    "ageMin",
    "ageMax",
    "gender",
    "orgType",
    "meanScore",
    "stdDeviation",
    "tScoreMean",
    "tScoreStdDeviation",
    "sortNo",
]
HIGH_RISK_HEADERS = [
    "ruleCode",
    "questionNo",
    "optionCode",
    "scoreThreshold",
    "warningLevel",
    "resultTitle",
    "resultDescription",
    "suggestionText",
    "sortNo",
]

LOCALES = ("zh-CN", "ja-JP", "en")
LOCALE_SUFFIX = {"zh-CN": "中文", "ja-JP": "日本語", "en": "English"}


def _bool(value: Any) -> str:
    return "true" if value else "false"


def _sheet(workbook, name: str, headers: list[str], rows: list[list[Any]]) -> None:
    sheet = workbook.create_sheet(name)
    sheet.append(headers)
    for row in rows:
        sheet.append(row)


def build_scale_workbook(spec: dict[str, Any]) -> bytes:
    scale = spec["scale"]
    workbook = openpyxl.Workbook()
    workbook.remove(workbook.active)
    _sheet(
        workbook,
        "scale",
        SCALE_HEADERS,
        [
            [
                scale["code"],
                scale["name"],
                scale.get("description", "MT scoring fixture"),
                scale.get("applicableTarget", ""),
                scale.get("versionNo", "v1"),
                scale["method"],
                scale.get("coefficient", "1"),
                _bool(scale.get("anonymousSupported", False)),
                scale.get("reportTemplate", "DEFAULT_SCREENING"),
                scale.get("normStrategy", "RAW_SCORE"),
                scale.get("normDefaultGroup", ""),
                _bool(scale.get("highRiskEnabled", False)),
            ]
        ],
    )
    _sheet(
        workbook,
        "dimensions",
        DIMENSION_HEADERS,
        [
            [dimension["code"], dimension["name"], dimension.get("description", ""), index + 1]
            for index, dimension in enumerate(spec.get("dimensions", []))
        ],
    )
    _sheet(
        workbook,
        "questions",
        QUESTION_HEADERS,
        [
            [
                question["no"],
                question["title"],
                question.get("type", "SINGLE_CHOICE"),
                question.get("dimension", ""),
                _bool(question.get("required", True)),
                _bool(question.get("reverse", False)),
                question.get("weight", "1"),
                index + 1,
                question.get("selectionLimit", ""),
                question.get("sliderMin", ""),
                question.get("sliderMax", ""),
                question.get("sliderStep", ""),
                _bool(question.get("textInputEnabled", False)),
                question.get("textInputPlaceholder", ""),
                question.get("matrixGroupCode", ""),
                question.get("rowCode", ""),
                question.get("columnCode", ""),
            ]
            for index, question in enumerate(spec.get("questions", []))
        ],
    )
    option_rows: list[list[Any]] = []
    for question in spec.get("questions", []):
        for index, option in enumerate(question.get("options", spec.get("options", []))):
            option_rows.append(
                [
                    question["no"],
                    option["code"],
                    option.get("label", f"Option {option['code']}"),
                    option["score"],
                    index + 1,
                    _bool(option.get("exclusive", False)),
                    "",
                ]
            )
    _sheet(workbook, "options", OPTION_HEADERS, option_rows)
    _sheet(
        workbook,
        "result_rules",
        RULE_HEADERS,
        [
            [
                rule.get("dimension", ""),
                rule["riskLevel"],
                rule["scoreMin"],
                rule["scoreMax"],
                rule.get("title", f"MT {rule['riskLevel']}"),
                rule.get("description", ""),
                rule.get("suggestion", ""),
                index + 1,
                rule.get("scoreSource", "RAW_SCORE"),
                rule.get("normCode", ""),
            ]
            for index, rule in enumerate(spec.get("resultRules", []))
        ],
    )
    _sheet(
        workbook,
        "norms",
        NORM_HEADERS,
        [
            [
                norm["code"],
                norm.get("name", f"MT norm {norm['code']}"),
                norm.get("dimension", ""),
                norm.get("applicableTarget", ""),
                norm.get("ageMin", ""),
                norm.get("ageMax", ""),
                norm.get("gender", ""),
                norm.get("orgType", ""),
                norm.get("meanScore", ""),
                norm.get("stdDeviation", ""),
                norm.get("tScoreMean", ""),
                norm.get("tScoreStdDeviation", ""),
                index + 1,
            ]
            for index, norm in enumerate(spec.get("norms", []))
        ],
    )
    _sheet(
        workbook,
        "high_risk_rules",
        HIGH_RISK_HEADERS,
        [
            [
                rule["code"],
                rule["questionNo"],
                rule.get("optionCode", ""),
                rule.get("scoreThreshold", ""),
                rule["warningLevel"],
                rule.get("title", f"MT {rule['code']}"),
                rule.get("description", ""),
                rule.get("suggestion", ""),
                index + 1,
            ]
            for index, rule in enumerate(spec.get("highRiskRules", []))
        ],
    )
    buffer = io.BytesIO()
    workbook.save(buffer)
    workbook.close()
    return buffer.getvalue()


def _multipart_file(file_name: str, file_bytes: bytes) -> tuple[bytes, str]:
    boundary = "----mt" + uuid.uuid4().hex
    buffer = io.BytesIO()
    buffer.write(
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{file_name}"\r\n'
        "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n".encode()
    )
    buffer.write(file_bytes)
    buffer.write(f"\r\n--{boundary}--\r\n".encode())
    return buffer.getvalue(), f"multipart/form-data; boundary={boundary}"


def import_scale(ctx: Context, spec: dict[str, Any], user: str = "assessor") -> int:
    token = ctx.token(user)
    body, content_type = _multipart_file(f"{spec['scale']['code']}.xlsx", build_scale_workbook(spec))
    parsed = ctx.http(
        "POST",
        "/api/v1/scales/imports/parse?importMode=CREATE_ONLY&draftFlag=true",
        token=token,
        raw_body=body,
        content_type=content_type,
    )
    data = require_code(parsed, 200)
    import_id = data["importId"]
    parse_errors = data.get("errors") or []
    require(
        not parse_errors and int(data.get("errorCount", 0)) == 0,
        f"scale import parse errors: {parse_errors[:3]}",
    )
    detail = require_code(ctx.http("GET", f"/api/v1/scales/imports/{import_id}", token=token), 200)
    errors = [issue for issue in detail.get("errors", []) if issue.get("severity") == "ERROR"]
    require(not errors, f"scale import parse errors: {errors}")
    confirmed = require_code(
        ctx.http(
            "POST",
            f"/api/v1/scales/imports/{import_id}/confirm",
            token=token,
            body={"confirmRemark": "MT-SCORE fixture"},
        ),
        200,
    )
    require(confirmed.get("status") == "SUCCESS", f"import confirm failed: {confirmed}")
    return int(confirmed["scaleId"])


def _translation_text(prefix: str, code: str, locale: str) -> str:
    return f"{prefix}[{LOCALE_SUFFIX[locale]}] {code}"


def build_package_payload(scale: dict[str, Any], spec: dict[str, Any]) -> dict[str, Any]:
    code = scale["scaleCode"]
    quality = spec.get("qualityPolicy", {})
    translations = []
    for locale in LOCALES:
        translations.append(
            {
                "localeCode": locale,
                "scaleName": _translation_text("量表", code, locale),
                "description": f"MT fixture {code}",
                "instructionText": f"MT instruction {locale}",
                "nonDiagnosticText": f"MT non-diagnostic statement {locale}",
                "highRiskActionText": f"MT high-risk action {locale}",
                "helpResourceText": f"MT help resource {locale}",
                "reviewStatus": "APPROVED",
            }
        )
    dimension_translations = [
        {
            "dimensionId": dimension["id"],
            "localeCode": locale,
            "dimensionName": _translation_text("维度", dimension["dimensionCode"], locale),
            "reviewStatus": "APPROVED",
        }
        for dimension in scale["dimensions"]
        for locale in LOCALES
    ]
    question_translations = [
        {
            "questionId": question["id"],
            "localeCode": locale,
            "questionTitle": _translation_text("题目", str(question["questionNo"]), locale),
            "reviewStatus": "APPROVED",
        }
        for question in scale["questions"]
        for locale in LOCALES
    ]
    option_translations = [
        {
            "optionId": option["id"],
            "localeCode": locale,
            "optionLabel": _translation_text("选项", option["optionCode"], locale),
            "reviewStatus": "APPROVED",
        }
        for question in scale["questions"]
        for option in question["options"]
        for locale in LOCALES
    ]
    rule_translations = [
        {
            "resultRuleId": rule["id"],
            "localeCode": locale,
            "resultTitle": _translation_text("结论", rule["riskLevel"], locale),
            "resultDescription": f"MT result description {locale}",
            "suggestionText": f"MT suggestion {locale}",
            "reviewStatus": "APPROVED",
        }
        for rule in scale["resultRules"]
        for locale in LOCALES
    ]
    high_risk_translations = [
        {
            "highRiskRuleId": rule["id"],
            "localeCode": locale,
            "resultTitle": _translation_text("高风险", rule["ruleCode"], locale),
            "resultDescription": f"MT high risk description {locale}",
            "suggestionText": f"MT high risk suggestion {locale}",
            "reviewStatus": "APPROVED",
        }
        for rule in scale["highRiskRules"]
        for locale in LOCALES
    ]
    norm_governance = [
        {
            "normId": norm["id"],
            "sourceReference": "MT-SCORE synthetic norm reference",
            "normVersion": "v1",
            "sampleSize": 1200,
            "regionCode": "CN",
            "languageCode": "zh-CN",
            "reviewStatus": "APPROVED",
        }
        for norm in scale["norms"]
    ]
    return {
        "governance": {
            "sourceTitle": f"MT scoring fixture {code}",
            "publisherName": "MT QA",
            "manualVersion": "v1",
            "citationText": "MT-SCORE synthetic citation",
            "copyrightStatus": "AUTHORIZED",
            "authorizationStatus": "AUTHORIZED",
            "authorizationType": "TEST_FIXTURE",
            "authorizationScope": "manual test",
            "estimatedMinutes": 5,
            "nonDiagnosticStatement": "MT-SCORE fixture, not a clinical instrument",
            "governanceStatus": "APPROVED",
        },
        "translations": translations,
        "dimensionTranslations": dimension_translations,
        "questionTranslations": question_translations,
        "optionTranslations": option_translations,
        "resultRuleTranslations": rule_translations,
        "highRiskRuleTranslations": high_risk_translations,
        "qualityPolicy": {
            "missingAnswerPolicy": quality.get("missingAnswerPolicy", "REJECT"),
            "maxMissingRatio": quality.get("maxMissingRatio", "0"),
            "minimumDurationSeconds": quality.get("minimumDurationSeconds"),
            "maximumDurationSeconds": quality.get("maximumDurationSeconds"),
            "invalidResultAction": quality.get("invalidResultAction", "INVALIDATE"),
            "requireAllRequiredAnswers": quality.get("requireAllRequiredAnswers", True),
        },
        "validityRules": [],
        "algorithmBinding": {
            "algorithmCode": spec.get("algorithmCode", "GENERIC_SCORE_CALCULATOR"),
            "algorithmVersion": spec.get("algorithmVersion", "1"),
            "implementationType": spec.get("implementationType", "BUILTIN"),
            "inputSchemaJson": "{}",
            "outputSchemaJson": "{}",
            "reviewStatus": "APPROVED",
        },
        "normGovernance": norm_governance,
    }


def put_package(ctx: Context, scale_id: int, spec: dict[str, Any], user: str = "assessor") -> None:
    token = ctx.token(user)
    detail = require_code(ctx.http("GET", f"/api/v1/scales/{scale_id}", token=token), 200)
    payload = build_package_payload(detail, spec)
    require_code(ctx.http("PUT", f"/api/v1/scales/{scale_id}/package", token=token, body=payload), 200)


def readiness(ctx: Context, scale_id: int, user: str = "assessor") -> dict[str, Any]:
    return require_code(ctx.http("GET", f"/api/v1/scales/{scale_id}/publication/readiness", token=ctx.token(user)), 200)


def create_golden_cases(ctx: Context, scale_id: int, spec: dict[str, Any]) -> None:
    token = ctx.token("assessor")
    for case in spec.get("goldenCases", []):
        case_id = None
        saved = ctx.http(
            "POST",
            f"/api/v1/scales/{scale_id}/publication/golden-cases",
            token=token,
            body={
                "caseCode": case["code"],
                "caseType": case["type"],
                "sourceReference": case.get("source", "MT-SCORE manual procedure"),
                "input": case["input"],
                "expected": case["expected"],
            },
        )
        data = require_code(saved, 200)
        case_id = data["id"]
        run = require_code(
            ctx.http("POST", f"/api/v1/scales/{scale_id}/publication/golden-cases/{case_id}/run", token=token),
            200,
        )
        require(
            run["passed"],
            f"golden case {case['code']} failed: {run['differences']} actual={json.dumps(run['actual'], ensure_ascii=False)}",
        )
        require_code(
            ctx.http(
                "POST",
                f"/api/v1/scales/{scale_id}/publication/golden-cases/{case_id}/approve",
                token=ctx.token("counselor"),
            ),
            200,
        )


def submit_reviews(ctx: Context, scale_id: int, spec: dict[str, Any]) -> None:
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/scales/{scale_id}/publication/reviews/PROFESSIONAL",
            token=ctx.token("counselor"),
            body={
                "decision": "APPROVED",
                "reviewToken": f"MT-SCORE-PRO-{scale_id}",
                "comment": "MT scoring fixture professional review",
                "qualificationReference": "MT-QUALIFICATION-0001",
                "evidenceReference": "MT-PROFESSIONAL-EVIDENCE-0001",
                "reviewScope": "Synthetic scoring fixture, manual test only",
            },
        ),
        200,
    )
    require_code(
        ctx.http(
            "POST",
            f"/api/v1/scales/{scale_id}/publication/reviews/BUSINESS",
            token=ctx.token("org_manager"),
            body={
                "decision": "APPROVED",
                "reviewToken": f"MT-SCORE-BIZ-{scale_id}",
                "comment": "MT scoring fixture business review",
                "evidenceReference": "MT-BUSINESS-EVIDENCE-0001",
                "reviewScope": "Synthetic scoring fixture, manual test only",
            },
        ),
        200,
    )


def publish_scale(ctx: Context, scale_id: int) -> None:
    token = ctx.token("assessor")
    response = ctx.http("POST", f"/api/v1/scales/{scale_id}/publish", token=token)
    if response.status != 200:
        detail = ctx.http("GET", f"/api/v1/scales/{scale_id}/publication/readiness", token=token)
        blockers = detail.data().get("blockers") if detail.status == 200 else detail.payload
        raise CheckFailure(f"publish failed: {response.payload} readiness={blockers}")


def prepare_scale(ctx: Context, key: str, spec: dict[str, Any]) -> int:
    """Import, govern, evidence and publish a scale once per suite run."""
    cache_key = f"score-scale:{key}"
    cached = ctx.store.get(cache_key)
    if isinstance(cached, int):
        return cached
    code = spec["scale"]["code"]
    try:
        scale_id = import_scale(ctx, spec)
    except CheckFailure as error:
        # A previous attempt in the same run may already have created the DRAFT
        # scale (for example when a golden-case expectation had to be fixed).
        existing = ctx.sql(
            f"select id from psy_scale where scale_code = '{code}' and status = 'DRAFT' order by id desc limit 1"
        )
        if not existing:
            raise
        if "SCALE_CODE_CONFLICT" not in str(error) and "confirm" not in str(error).lower():
            raise
        scale_id = int(existing.splitlines()[0])
    put_package(ctx, scale_id, spec)
    create_golden_cases(ctx, scale_id, spec)
    submit_reviews(ctx, scale_id, spec)
    publish_scale(ctx, scale_id)
    ctx.store[cache_key] = scale_id
    return scale_id


def private_spec(ctx: Context, spec: dict[str, Any], prefix: str) -> dict[str, Any]:
    """Clone a shared fixture spec with a unique scale code.

    Shared specs are cached per run and are imported once by their owning
    module; a case that needs to import the same questionnaire again must use
    its own scale code or the import fails with SCALE_CODE_CONFLICT.
    """
    cloned = copy.deepcopy(spec)
    cloned["scale"] = {**cloned["scale"], "code": f"{prefix}_{ctx.unique('')}"}
    return cloned


def create_task(
    ctx: Context,
    scale_id: int,
    task_name: str,
    user_id: int = 6,
    user: str = "assessor",
    *,
    allow_timeout_submit: bool = False,
    anonymous: bool = False,
) -> int:
    token = ctx.token(user)
    start = (datetime.now() - timedelta(minutes=5)).replace(microsecond=0).isoformat()
    end = (datetime.now() + timedelta(days=7)).replace(microsecond=0).isoformat()
    created = require_code(
        ctx.http(
            "POST",
            "/api/v1/tasks",
            token=token,
            body={
                "taskName": task_name,
                "scaleId": scale_id,
                "taskMode": "SCREENING",
                "anonymousFlag": anonymous,
                "allowSaveFlag": True,
                "allowTimeoutSubmitFlag": allow_timeout_submit,
                "allowRetakeFlag": False,
                "startTime": start,
                "endTime": end,
            },
        ),
        200,
    )
    task_id = int(created["id"])
    require_code(
        ctx.http("POST", f"/api/v1/tasks/{task_id}/assign-users", token=token, body={"userIds": [user_id]}),
        200,
    )
    return task_id


def fetch_question_meta(ctx: Context, task_id: int, user: str = "respondent") -> dict[int, dict[str, Any]]:
    payload = require_code(ctx.http("GET", f"/api/v1/my/tasks/{task_id}/questions", token=ctx.token(user)), 200)
    return {question["questionNo"]: question for question in payload["questions"]}


def submit_answers(
    ctx: Context,
    task_id: int,
    scale_id: int,
    answers: dict[int, str],
    *,
    user: str = "respondent",
    answer_sheet_id: int | None = None,
    version_no: int | None = None,
    submit_token: str | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, Any], Any]:
    questions = fetch_question_meta(ctx, task_id, user)
    payload_answers = []
    for question_no, option_code in answers.items():
        question = questions.get(question_no)
        if question is None:
            raise CheckFailure(f"question {question_no} not present in task {task_id}")
        option = next((item for item in question["options"] if item["optionCode"] == option_code), None)
        if option is None:
            raise CheckFailure(f"option {option_code} not present on question {question_no}")
        payload_answers.append({"questionId": question["questionId"], "optionId": option["optionId"]})
    body: dict[str, Any] = {
        "taskId": task_id,
        "scaleId": scale_id,
        "answers": payload_answers,
        "submitToken": submit_token or str(uuid.uuid4()),
    }
    if answer_sheet_id is not None:
        body["answerSheetId"] = answer_sheet_id
    if version_no is not None:
        body["versionNo"] = version_no
    response = ctx.http(
        "POST",
        "/api/v1/answer-sheets/submit",
        token=ctx.token(user),
        body=body,
        headers=headers,
    )
    return response.status, (response.payload if isinstance(response.payload, dict) else {}), response


def result_row(ctx: Context, result_id: int) -> dict[str, str]:
    raw = ctx.sql(
        "select json_build_object("
        "'totalScore', total_score, 'riskLevel', risk_level, 'highRiskFlag', high_risk_flag, "
        "'highRiskRuleCode', coalesce(high_risk_rule_code, ''), 'normCode', coalesce(norm_code, ''), "
        "'standardScore', coalesce(standard_score, 0), 'zScore', coalesce(z_score, 0), "
        "'tScore', coalesce(t_score, 0), 'qualityStatus', quality_status, "
        "'missingRatio', coalesce(quality_missing_ratio, 0), 'durationSeconds', coalesce(quality_duration_seconds, 0), "
        "'calculationVersion', calculation_version, 'isCurrent', is_current, "
        "'hasTrace', scoring_trace_json is not null, 'issueCodes', coalesce(quality_issue_codes, '')"
        f") from psy_assessment_result where id = {result_id}"
    )
    return json.loads(raw)


def result_dimensions(ctx: Context, result_id: int) -> dict[str, str]:
    raw = ctx.sql(
        "select coalesce(json_object_agg(scale_dimension.dimension_code, result_dimension.dimension_score), '{}'::json) "
        "from psy_assessment_result_dimension result_dimension "
        "join psy_scale_dimension scale_dimension on scale_dimension.id = result_dimension.dimension_id "
        f"where result_dimension.result_id = {result_id}"
    )
    return json.loads(raw)


def scoring_trace(ctx: Context, result_id: int) -> dict[str, Any]:
    raw = ctx.sql(f"select scoring_trace_json::text from psy_assessment_result where id = {result_id}")
    return json.loads(raw)


def set_answer_sheet_started_at(ctx: Context, answer_sheet_id: int, seconds_ago: int) -> None:
    """Test-environment clock control for the duration quality policy."""
    ctx.sql(
        "update psy_assessment_answer_sheet "
        f"set start_time = now() - interval '{int(seconds_ago)} seconds' "
        f"where id = {answer_sheet_id}"
    )


def save_draft(ctx: Context, task_id: int, scale_id: int, answers: dict[int, str], user: str = "respondent") -> dict[str, Any]:
    questions = fetch_question_meta(ctx, task_id, user)
    payload_answers = []
    for question_no, option_code in answers.items():
        question = questions[question_no]
        option = next(item for item in question["options"] if item["optionCode"] == option_code)
        payload_answers.append({"questionId": question["questionId"], "optionId": option["optionId"]})
    return require_code(
        ctx.http(
            "POST",
            "/api/v1/answer-sheets/save",
            token=ctx.token(user),
            body={"taskId": task_id, "scaleId": scale_id, "answers": payload_answers},
        ),
        200,
    )


def ensure_published_scale(ctx: Context, key: str, spec: dict[str, Any]) -> int:
    try:
        return prepare_scale(ctx, key, spec)
    except CheckFailure:
        raise
    except Exception as error:  # noqa: BLE001 - surface setup problems as blocked cases
        raise CheckBlocked(f"score fixture {key} could not be published: {error}") from error
