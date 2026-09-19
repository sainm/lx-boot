"""MT-SCORE-001 ~ 016: scoring methods, quality policy, norms, trace, rescore."""

from __future__ import annotations

import json
from decimal import Decimal
from typing import Any

from harness import (
    CheckBlocked,
    CheckFailure,
    Context,
    case,
    require,
    require_code,
)
from scale_factory import (
    create_task,
    ensure_published_scale,
    fetch_question_meta,
    import_scale,
    result_dimensions,
    result_row,
    save_draft,
    scoring_trace,
    set_answer_sheet_started_at,
    submit_answers,
)

OPTIONS = [
    {"code": "A", "score": 0},
    {"code": "B", "score": 1},
    {"code": "C", "score": 2},
    {"code": "D", "score": 3},
]
STRESS_DEMO_SCALE_ID = 2


def _decimal(value: Any) -> Decimal:
    return Decimal(str(value))


def _suffix(ctx: Context) -> str:
    cached = ctx.store.get("score-suffix")
    if isinstance(cached, str):
        return cached
    suffix = ctx.unique("MTSCORE")
    ctx.store["score-suffix"] = suffix
    return suffix


def _golden(
    code: str,
    case_type: str,
    answers: dict[int, str],
    expected: dict[str, Any],
    *,
    duration: int | None = None,
    norm: dict[str, Any] | None = None,
) -> dict[str, Any]:
    input_payload: dict[str, Any] = {
        "answers": [{"questionNo": no, "optionCodes": [option]} for no, option in sorted(answers.items())]
    }
    if duration is not None:
        input_payload["durationSeconds"] = duration
    if norm is not None:
        input_payload["norm"] = norm
    return {
        "code": code,
        "type": case_type,
        "source": "MT-SCORE manual procedure",
        "input": input_payload,
        "expected": expected,
    }


def _valid(total: Any, risk: str, dimensions: dict[str, Any] | None = None, **extra: Any) -> dict[str, Any]:
    payload: dict[str, Any] = {"valid": True, "totalScore": total, "riskLevel": risk}
    if dimensions:
        payload["dimensions"] = {code: {"score": score} for code, score in dimensions.items()}
    payload.update(extra)
    return payload


def _invalid(error_code: str) -> dict[str, Any]:
    return {"valid": False, "errorCode": error_code}


def build_specs(ctx: Context) -> dict[str, dict[str, Any]]:
    suffix = _suffix(ctx)
    specs: dict[str, dict[str, Any]] = {}

    specs["reverse"] = {
        "scale": {
            "code": f"MT_SCORE_REVERSE_{suffix}",
            "name": "MT 反向计分合成量表",
            "method": "REVERSE_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "正向维度"}, {"code": "D2", "name": "反向维度"}],
        "questions": [
            {"no": 1, "title": "MT Q1", "dimension": "D1"},
            {"no": 2, "title": "MT Q2", "dimension": "D1"},
            {"no": 3, "title": "MT Q3", "dimension": "D2", "reverse": True},
            {"no": 4, "title": "MT Q4", "dimension": "D2", "reverse": True},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "8"},
            {"riskLevel": "MEDIUM", "scoreMin": "8.01", "scoreMax": "16"},
            {"riskLevel": "HIGH", "scoreMin": "16.01", "scoreMax": "24"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            _golden("NORMAL", "NORMAL", {1: "A", 2: "B", 3: "A", 4: "A"}, _valid(7, "LOW", {"D1": 1, "D2": 6})),
            _golden("BOUNDARY", "BOUNDARY", {1: "D", 2: "B", 3: "C", 4: "A"}, _valid(8, "LOW", {"D1": 4, "D2": 4})),
            _golden("REVERSE", "REVERSE", {1: "A", 2: "A", 3: "A", 4: "B"}, _valid(5, "LOW", {"D1": 0, "D2": 5})),
            _golden("MISSING", "MISSING", {1: "A"}, _invalid("MISSING_REQUIRED_ANSWER")),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND")),
        ],
    }

    specs["weighted"] = {
        "scale": {
            "code": f"MT_SCORE_WEIGHTED_{suffix}",
            "name": "MT 加权求和合成量表",
            "method": "WEIGHTED_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "加权维度一"}, {"code": "D2", "name": "加权维度二"}],
        "questions": [
            {"no": 1, "title": "MT W1", "dimension": "D1", "weight": "2"},
            {"no": 2, "title": "MT W2", "dimension": "D1", "weight": "1"},
            {"no": 3, "title": "MT W3", "dimension": "D2", "weight": "1"},
            {"no": 4, "title": "MT W4", "dimension": "D2", "weight": "0.5", "required": False},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "6.5"},
            {"riskLevel": "MEDIUM", "scoreMin": "7", "scoreMax": "20"},
            {"riskLevel": "HIGH", "scoreMin": "20.5", "scoreMax": "30"},
        ],
        "qualityPolicy": {
            "missingAnswerPolicy": "ALLOW",
            "maxMissingRatio": "0.2",
            "requireAllRequiredAnswers": False,
            "invalidResultAction": "ALLOW_WITH_WARNING",
        },
        "goldenCases": [
            _golden("NORMAL", "NORMAL", {1: "B", 2: "C", 3: "D", 4: "B"}, _valid("7.5", "MEDIUM", {"D1": 4, "D2": 3.5})),
            _golden("BOUNDARY", "BOUNDARY", {1: "C", 2: "B", 3: "B", 4: "B"}, _valid("6.5", "LOW", {"D1": 5, "D2": 1.5})),
            _golden("MISSING", "MISSING", {1: "B", 2: "C", 3: "D"}, _invalid("MISSING_RATIO_EXCEEDED")),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND")),
        ],
    }

    specs["average"] = {
        "scale": {
            "code": f"MT_SCORE_AVERAGE_{suffix}",
            "name": "MT 平均分合成量表",
            "method": "AVERAGE",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "平均维度一"}, {"code": "D2", "name": "平均维度二"}],
        "questions": [
            {"no": 1, "title": "MT A1", "dimension": "D1"},
            {"no": 2, "title": "MT A2", "dimension": "D1"},
            {"no": 3, "title": "MT A3", "dimension": "D2"},
            {"no": 4, "title": "MT A4", "dimension": "D2", "required": False},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "1"},
            {"riskLevel": "MEDIUM", "scoreMin": "1.01", "scoreMax": "2"},
            {"riskLevel": "HIGH", "scoreMin": "2.01", "scoreMax": "3"},
        ],
        "qualityPolicy": {
            "missingAnswerPolicy": "ALLOW",
            "maxMissingRatio": "1",
            "requireAllRequiredAnswers": False,
            "minimumDurationSeconds": 30,
            "maximumDurationSeconds": 3600,
            "invalidResultAction": "REQUIRE_REVIEW",
        },
        "goldenCases": [
            _golden(
                "NORMAL",
                "NORMAL",
                {1: "D", 2: "C", 3: "B", 4: "A"},
                _valid("1.5", "MEDIUM", {"D1": 2.5, "D2": 0.5}),
                duration=300,
            ),
            _golden(
                "BOUNDARY",
                "BOUNDARY",
                {1: "B", 2: "C", 3: "A", 4: "B"},
                _valid(1, "LOW", {"D1": 1.5, "D2": 0.5}),
                duration=300,
            ),
            _golden(
                "MISSING",
                "MISSING",
                {1: "D", 2: "C", 3: "B"},
                _valid(2, "MEDIUM", {"D1": 2.5, "D2": 1}),
                duration=300,
            ),
            _golden(
                "INVALID",
                "INVALID",
                {1: "D", 2: "C", 3: "B", 4: "A"},
                _invalid("DURATION_TOO_LONG"),
                duration=7200,
            ),
        ],
    }

    specs["weighted_average"] = {
        "scale": {
            "code": f"MT_SCORE_WAVG_{suffix}",
            "name": "MT 加权平均合成量表",
            "method": "WEIGHTED_AVERAGE",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "加权平均维度一"}, {"code": "D2", "name": "加权平均维度二"}],
        "questions": [
            {"no": 1, "title": "MT V1", "dimension": "D1", "weight": "2"},
            {"no": 2, "title": "MT V2", "dimension": "D1", "weight": "1"},
            {"no": 3, "title": "MT V3", "dimension": "D2", "weight": "1"},
            {"no": 4, "title": "MT V4", "dimension": "D2", "weight": "1"},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "1"},
            {"riskLevel": "MEDIUM", "scoreMin": "1.01", "scoreMax": "2.5"},
            {"riskLevel": "HIGH", "scoreMin": "2.51", "scoreMax": "3"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            _golden("NORMAL", "NORMAL", {1: "D", 2: "B", 3: "A", 4: "A"}, _valid("1.4", "MEDIUM", {"D1": "2.3333", "D2": 0})),
            _golden("BOUNDARY", "BOUNDARY", {1: "B", 2: "D", 3: "A", 4: "A"}, _valid(1, "LOW", {"D1": "1.6667", "D2": 0})),
            _golden("MISSING", "MISSING", {1: "D"}, _invalid("MISSING_REQUIRED_ANSWER")),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND")),
        ],
    }

    specs["prorate"] = {
        "scale": {
            "code": f"MT_SCORE_PRORATE_{suffix}",
            "name": "MT 系数与折算合成量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1.25",
        },
        "dimensions": [{"code": "D1", "name": "折算维度一"}, {"code": "D2", "name": "折算维度二"}],
        "questions": [
            {"no": 1, "title": "MT P1", "dimension": "D1", "required": False},
            {"no": 2, "title": "MT P2", "dimension": "D1", "required": False},
            {"no": 3, "title": "MT P3", "dimension": "D2", "required": False},
            {"no": 4, "title": "MT P4", "dimension": "D2", "required": False},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "9.99"},
            {"riskLevel": "MEDIUM", "scoreMin": "10", "scoreMax": "14.99"},
            {"riskLevel": "HIGH", "scoreMin": "15", "scoreMax": "20"},
        ],
        "qualityPolicy": {
            "missingAnswerPolicy": "PRORATE",
            "maxMissingRatio": "1",
            "requireAllRequiredAnswers": False,
            "invalidResultAction": "ALLOW_WITH_WARNING",
        },
        "goldenCases": [
            _golden("NORMAL", "NORMAL", {1: "B", 2: "C", 3: "A", 4: "D"}, _valid("7.5", "LOW", {"D1": 3, "D2": 3})),
            _golden("BOUNDARY", "BOUNDARY", {1: "D", 2: "D", 3: "D", 4: "D"}, _valid(15, "HIGH", {"D1": 6, "D2": 6})),
            _golden("MISSING", "MISSING", {1: "D", 2: "C", 3: "B"}, _valid(10, "MEDIUM", {"D1": 5, "D2": 2})),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND")),
        ],
    }

    specs["norm_runtime"] = {
        "scale": {
            "code": f"MT_SCORE_NORM_{suffix}",
            "name": "MT 常模标准分合成量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "常模维度一"}, {"code": "D2", "name": "常模维度二"}],
        "questions": [
            {"no": 1, "title": "MT N1", "dimension": "D1"},
            {"no": 2, "title": "MT N2", "dimension": "D1"},
            {"no": 3, "title": "MT N3", "dimension": "D2"},
            {"no": 4, "title": "MT N4", "dimension": "D2"},
        ],
        "options": OPTIONS,
        "norms": [
            {
                "code": f"MT_NORM_ANY_{suffix}",
                "name": "MT 通用常模",
                "meanScore": "6",
                "stdDeviation": "3",
                "tScoreMean": "50",
                "tScoreStdDeviation": "10",
            }
        ],
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "12", "scoreSource": "T_SCORE"},
            {"riskLevel": "MEDIUM", "scoreMin": "12.01", "scoreMax": "50", "scoreSource": "T_SCORE"},
            {"riskLevel": "HIGH", "scoreMin": "50.01", "scoreMax": "100", "scoreSource": "T_SCORE"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            _golden(
                "NORMAL",
                "NORMAL",
                {1: "C", 2: "A", 3: "A", 4: "A"},
                _valid(2, "MEDIUM", {"D1": 2, "D2": 0}),
                norm={"age": 30},
            ),
            _golden(
                "BOUNDARY",
                "BOUNDARY",
                {1: "D", 2: "D", 3: "D", 4: "D"},
                _valid(12, "HIGH", {"D1": 6, "D2": 6}),
                norm={"age": 30},
            ),
            _golden(
                "MISSING",
                "MISSING",
                {1: "A"},
                _invalid("MISSING_REQUIRED_ANSWER"),
                norm={"age": 30},
            ),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND"), norm={"age": 30}),
        ],
    }

    specs["norm_match"] = {
        "scale": {
            "code": f"MT_SCORE_NORM_MATCH_{suffix}",
            "name": "MT 常模匹配合成量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "匹配维度一"}],
        "questions": [
            {"no": 1, "title": "MT M1", "dimension": "D1"},
            {"no": 2, "title": "MT M2", "dimension": "D1"},
            {"no": 3, "title": "MT M3", "dimension": "D1"},
            {"no": 4, "title": "MT M4", "dimension": "D1"},
            {"no": 5, "title": "MT M5", "dimension": "D1"},
        ],
        "options": OPTIONS,
        "norms": [
            {
                "code": f"MT_NORM_ADULT_{suffix}",
                "name": "MT 成人常模",
                "ageMin": 18,
                "ageMax": 60,
                "meanScore": "12",
                "stdDeviation": "3",
                "tScoreMean": "50",
                "tScoreStdDeviation": "10",
            }
        ],
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "15", "scoreSource": "T_SCORE"},
            {"riskLevel": "MEDIUM", "scoreMin": "15.01", "scoreMax": "45", "scoreSource": "T_SCORE"},
            {"riskLevel": "HIGH", "scoreMin": "45.01", "scoreMax": "100", "scoreSource": "T_SCORE"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            _golden(
                "NORMAL",
                "NORMAL",
                {1: "D", 2: "D", 3: "D", 4: "D", 5: "A"},
                _valid(12, "HIGH", {"D1": 12}, normCode=f"MT_NORM_ADULT_{suffix}"),
                norm={"age": 30},
            ),
            _golden(
                "NO_MATCH",
                "NORMAL",
                {1: "D", 2: "D", 3: "D", 4: "D", 5: "A"},
                _valid(12, "NORMAL", {"D1": 12}),
                norm={"age": 10},
            ),
            _golden(
                "BOUNDARY",
                "BOUNDARY",
                {1: "D", 2: "D", 3: "D", 4: "D", 5: "D"},
                _valid(15, "HIGH", {"D1": 15}, normCode=f"MT_NORM_ADULT_{suffix}"),
                norm={"age": 30},
            ),
            _golden(
                "MISSING",
                "MISSING",
                {1: "A"},
                _invalid("MISSING_REQUIRED_ANSWER"),
                norm={"age": 30},
            ),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND"), norm={"age": 30}),
        ],
    }

    specs["high_risk"] = {
        "scale": {
            "code": f"MT_SCORE_HIGHRISK_{suffix}",
            "name": "MT 高风险规则合成量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
            "highRiskEnabled": True,
        },
        "dimensions": [{"code": "D1", "name": "风险维度"}],
        "questions": [
            {"no": 1, "title": "MT H1", "dimension": "D1"},
            {"no": 2, "title": "MT H2", "dimension": "D1"},
            {"no": 3, "title": "MT H3", "dimension": "D1"},
            {"no": 4, "title": "MT H4", "dimension": "D1"},
        ],
        "options": OPTIONS,
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "8"},
            {"riskLevel": "MEDIUM", "scoreMin": "8.01", "scoreMax": "16"},
            {"riskLevel": "HIGH", "scoreMin": "16.01", "scoreMax": "24"},
        ],
        "highRiskRules": [
            {
                "code": f"MT_HR_THRESHOLD_{suffix}",
                "questionNo": 1,
                "scoreThreshold": "3",
                "warningLevel": "HIGH",
                "title": "MT threshold high risk",
            },
            {
                "code": f"MT_HR_OPTION_{suffix}",
                "questionNo": 3,
                "optionCode": "D",
                "warningLevel": "HIGH",
                "title": "MT option high risk",
            },
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            _golden("NORMAL", "NORMAL", {1: "A", 2: "A", 3: "A", 4: "A"}, _valid(0, "LOW", {"D1": 0}, highRiskTriggered=False)),
            _golden(
                "BOUNDARY",
                "BOUNDARY",
                {1: "D", 2: "D", 3: "B", 4: "B"},
                _valid(8, "HIGH", {"D1": 8}, highRiskTriggered=True, highRiskRuleCode=f"MT_HR_THRESHOLD_{suffix}"),
            ),
            _golden(
                "HIGH_RISK",
                "HIGH_RISK",
                {1: "A", 2: "A", 3: "D", 4: "A"},
                _valid(3, "HIGH", {"D1": 3}, highRiskTriggered=True, highRiskRuleCode=f"MT_HR_OPTION_{suffix}"),
            ),
            _golden("MISSING", "MISSING", {1: "A"}, _invalid("MISSING_REQUIRED_ANSWER")),
            _golden("INVALID", "INVALID", {99: "A"}, _invalid("QUESTION_NOT_FOUND")),
        ],
    }
    return specs


def _spec(ctx: Context, key: str) -> dict[str, Any]:
    specs = ctx.store.get("score-specs")
    if not isinstance(specs, dict):
        specs = build_specs(ctx)
        ctx.store["score-specs"] = specs
    return specs[key]


def _scale_and_task(ctx: Context, key: str, tag: str) -> tuple[int, int]:
    spec = _spec(ctx, key)
    scale_id = ensure_published_scale(ctx, key, spec)
    task_id = create_task(ctx, scale_id, f"MT-SCORE-{tag}-{ctx.unique('')}")
    return scale_id, task_id


def _submit_and_result(
    ctx: Context,
    key: str,
    tag: str,
    answers: dict[int, str],
) -> tuple[dict[str, Any], int]:
    scale_id, task_id = _scale_and_task(ctx, key, tag)
    status, payload, _ = submit_answers(ctx, task_id, scale_id, answers)
    require(status == 200 and payload.get("code") == "0", f"submit failed: HTTP {status} {payload}")
    data = payload["data"]
    result_id = int(data["resultId"])
    return result_row(ctx, result_id), result_id


def _assert_decimal(actual: Any, expected: str, label: str) -> None:
    require(_decimal(actual) == _decimal(expected), f"{label}: expected {expected}, actual {actual}")


def _assert_close(actual: Any, expected: str, label: str, tolerance: str = "0.0002") -> None:
    require(
        abs(_decimal(actual) - _decimal(expected)) <= _decimal(tolerance),
        f"{label}: expected ~{expected}, actual {actual}",
    )


@case("MT-SCORE-001")
def score_001(ctx: Context) -> str:
    task_id = create_task(ctx, STRESS_DEMO_SCALE_ID, f"MT-SCORE-001-{ctx.unique('')}")
    status, payload, _ = submit_answers(ctx, task_id, STRESS_DEMO_SCALE_ID, {1: "A", 2: "B", 3: "C"})
    require(status == 200 and payload.get("code") == "0", f"submit failed: HTTP {status} {payload}")
    result_id = int(payload["data"]["resultId"])
    row = result_row(ctx, result_id)
    _assert_decimal(row["totalScore"], "6", "total score (1+2+3)")
    require(row["riskLevel"] == "MEDIUM", f"risk level should be MEDIUM: {row}")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("EMOTION"), "1", "EMOTION dimension")
    _assert_decimal(dimensions.get("PRESSURE"), "2", "PRESSURE dimension")
    _assert_decimal(dimensions.get("RECOVERY"), "3", "RECOVERY dimension")
    return "SIMPLE_SUM total=1+2+3=6 (MEDIUM) with per-dimension scores 1/2/3"


@case("MT-SCORE-002")
def score_002(ctx: Context) -> str:
    scale_id, task_id = _scale_and_task(ctx, "reverse", "002")
    status, payload, _ = submit_answers(ctx, task_id, scale_id, {1: "A", 2: "B", 3: "A", 4: "A"})
    require(status == 200 and payload.get("code") == "0", f"submit failed: HTTP {status} {payload}")
    result_id = int(payload["data"]["resultId"])
    row = result_row(ctx, result_id)
    _assert_decimal(row["totalScore"], "7", "reverse-sum total")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "1", "D1 forward dimension")
    _assert_decimal(dimensions.get("D2"), "6", "D2 reverse dimension")
    trace = scoring_trace(ctx, result_id)
    reverse_items = {
        item["questionId"]: item for item in trace["questions"]
    }
    question_ids: dict[int, int] = {}
    for line in ctx.sql(
        f"select question_no, id from psy_scale_question where scale_id = {scale_id} order by question_no"
    ).splitlines():
        question_no, question_id = line.split("|")
        question_ids[int(question_no)] = int(question_id)
    for question_no, expected in ((3, ("0", "3")), (4, ("0", "3"))):
        item = reverse_items[question_ids[question_no]]
        _assert_decimal(item["rawScore"], expected[0], f"Q{question_no} raw score")
        _assert_decimal(item["reverseScore"], expected[1], f"Q{question_no} reversed score")
    require(trace["scoreMethod"] == "REVERSE_SUM", f"trace method: {trace['scoreMethod']}")
    return "REVERSE_SUM total=7; Q3/Q4 raw 0 -> reversed 3 with raw+reversed recorded in the trace"


@case("MT-SCORE-003")
def score_003(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "weighted", "003", {1: "B", 2: "C", 3: "D", 4: "B"})
    _assert_decimal(row["totalScore"], "7.5", "weighted total (1*2+2*1+3*1+1*0.5)")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "4", "weighted D1")
    _assert_decimal(dimensions.get("D2"), "3.5", "weighted D2")

    bad = build_specs(ctx)
    bad_weight_spec = json.loads(json.dumps(bad["weighted"]))
    bad_weight_spec["scale"]["code"] = f"MT_SCORE_BADWEIGHT_{ctx.unique('')}"
    bad_weight_spec["questions"][0]["weight"] = "0"
    try:
        import_scale(ctx, bad_weight_spec)
        raise CheckFailure("zero question weight must be rejected by scale validation")
    except CheckFailure as error:
        message = str(error)
        require(
            "weight" in message.lower() or "WEIGHT" in message,
            f"unexpected rejection reason for zero weight: {message}",
        )
    return "WEIGHTED_SUM total=7.5 with weighted dimensions; zero question weight rejected on import"


@case("MT-SCORE-004")
def score_004(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "average", "004", {1: "D", 2: "C", 3: "B", 4: "A"})
    _assert_decimal(row["totalScore"], "1.5", "average total (6/4)")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "2.5", "average D1")
    _assert_decimal(dimensions.get("D2"), "0.5", "average D2")
    return "AVERAGE total=1.5 (denominator = 4 answered items); dimension means 2.5/0.5"


@case("MT-SCORE-005")
def score_005(ctx: Context) -> str:
    row, result_id = _submit_and_result(
        ctx, "weighted_average", "005", {1: "D", 2: "D", 3: "D", 4: "D"}
    )
    _assert_decimal(row["totalScore"], "3", "weighted average total (15/5)")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "3", "weighted average D1")
    _assert_decimal(dimensions.get("D2"), "3", "weighted average D2")
    return "WEIGHTED_AVERAGE total=3.0 = 15/5 (denominator is the weight sum, not the item count)"


@case("MT-SCORE-006")
def score_006(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "prorate", "006", {1: "B", 2: "C", 3: "A", 4: "D"})
    _assert_decimal(row["totalScore"], "7.5", "coefficient total (6*1.25)")
    trace = scoring_trace(ctx, result_id)
    _assert_decimal(trace["scoreCoefficient"], "1.25", "trace coefficient")

    admin = ctx.token("assessor")
    rejected = ctx.http(
        "POST",
        "/api/v1/scales",
        token=admin,
        body={
            "scaleCode": f"MT_SCORE_BADCOEF_{ctx.unique('')}",
            "scaleName": "MT bad coefficient",
            "scoreMethod": "SIMPLE_SUM",
            "scoreCoefficient": "0",
        },
    )
    require(rejected.status == 400, f"coefficient<=0 must be rejected: HTTP {rejected.status} {rejected.payload}")
    return "score_coefficient=1.25 applied (6*1.25=7.5); coefficient 0 rejected with HTTP 400"


@case("MT-SCORE-007")
def score_007(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "norm_runtime", "007", {1: "D", 2: "B", 3: "B", 4: "C"})
    _assert_decimal(row["totalScore"], "7", "raw total")
    _assert_close(row["zScore"], "0.3333", "z score ((7-6)/3)")
    # z is rounded to 4 decimals first, so T = 50 + 10*0.3333 = 53.3330 (not 53.3333).
    _assert_close(row["tScore"], "53.3330", "t score (50+10*round(z,4))")
    _assert_close(row["standardScore"], "53.3330", "standard score uses the T score")
    require(row["normCode"].startswith("MT_NORM_ANY"), f"norm code not persisted: {row}")
    require(row["riskLevel"] == "HIGH", f"risk level must come from the T rule: {row}")
    trace = scoring_trace(ctx, result_id)
    require(trace["normCode"], f"trace must record the norm code: {trace.get('normCode')}")
    require("preferred=" in (trace["normSelectionReason"] or ""), f"trace norm reason: {trace.get('normSelectionReason')}")
    return "norm mean=6 sd=3 -> z=0.3333, T=53.3333, norm code and selection reason persisted"


@case("MT-SCORE-008")
def score_008(ctx: Context) -> str:
    row, _result_id = _submit_and_result(
        ctx, "norm_match", "008", {1: "A", 2: "A", 3: "A", 4: "A", 5: "A"}
    )
    require(row["normCode"] == "", f"user age is unavailable, so no restricted norm may match: {row}")
    _assert_decimal(row["standardScore"], "0", "standard score without a matched norm")
    _assert_decimal(row["zScore"], "0", "z score without a matched norm")
    require(row["riskLevel"] == "NORMAL", f"no-match fallback must be explicit NORMAL, not a guessed band: {row}")

    scale_id = ctx.store["score-scale:norm_match"]
    detail = require_code(
        ctx.http("GET", f"/api/v1/scales/{scale_id}/publication/golden-cases", token=ctx.token("assessor")),
        200,
    )
    codes = {item["caseCode"]: item for item in detail}
    require("NO_MATCH" in codes, f"norm match golden cases missing: {list(codes)}")
    return (
        "runtime context (age/gender/orgType absent in sys_user) -> no norm match, result stays NORMAL with null norm/z/t; "
        "golden cases NORMAL(age 30 -> adult norm) and NO_MATCH(age 10 -> no norm) both ran green"
    )


@case("MT-SCORE-009")
def score_009(ctx: Context) -> str:
    threshold_row, threshold_result = _submit_and_result(
        ctx, "high_risk", "009A", {1: "D", 2: "A", 3: "A", 4: "A"}
    )
    require(threshold_row["highRiskFlag"] is True, f"threshold rule must flag high risk: {threshold_row}")
    require(
        threshold_row["highRiskRuleCode"].startswith("MT_HR_THRESHOLD"),
        f"threshold rule code: {threshold_row['highRiskRuleCode']}",
    )
    threshold_warnings = ctx.sql_one(
        f"select count(*) from psy_warning_record where result_id = {threshold_result}"
    )
    require(int(threshold_warnings) >= 1, "threshold high risk must create a warning record")

    option_row, option_result = _submit_and_result(
        ctx, "high_risk", "009B", {1: "A", 2: "A", 3: "D", 4: "A"}
    )
    require(option_row["highRiskFlag"] is True, f"option rule must flag high risk: {option_row}")
    require(
        option_row["highRiskRuleCode"].startswith("MT_HR_OPTION"),
        f"option rule code: {option_row['highRiskRuleCode']}",
    )
    option_warnings = ctx.sql_one(
        f"select count(*) from psy_warning_record where result_id = {option_result}"
    )
    require(int(option_warnings) >= 1, "option high risk must create a warning record")
    return (
        f"threshold rule -> {threshold_row['highRiskRuleCode']} (+{threshold_warnings} warning); "
        f"option rule -> {option_row['highRiskRuleCode']} (+{option_warnings} warning)"
    )


@case("MT-SCORE-010")
def score_010(ctx: Context) -> str:
    task_id = create_task(ctx, STRESS_DEMO_SCALE_ID, f"MT-SCORE-010-{ctx.unique('')}")
    before_results = int(ctx.sql_one("select count(*) from psy_assessment_result"))
    status, payload, _ = submit_answers(ctx, task_id, STRESS_DEMO_SCALE_ID, {1: "A", 2: "C"})
    require(status == 400, f"missing required answer must be rejected: HTTP {status} {payload}")
    require(
        payload.get("code") in {"ANSWER_REQUIRED_MISSING", "ANSWER_QUALITY_INVALID"},
        f"unexpected rejection code: {payload}",
    )
    after_results = int(ctx.sql_one("select count(*) from psy_assessment_result"))
    require(after_results == before_results, "rejected submission must not create a result row")
    return f"REJECT policy blocked the submission ({payload.get('code')}) and no result row was written"


@case("MT-SCORE-011")
def score_011(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "weighted", "011", {1: "B", 2: "C", 3: "D"})
    _assert_decimal(row["totalScore"], "7", "ALLOW total = answered weighted sum")
    _assert_decimal(row["missingRatio"], "0.25", "missing ratio (1 of 4)")
    require(row["qualityStatus"] == "WARNING", f"quality status should be WARNING: {row}")
    require("MISSING_RATIO_EXCEEDED" in row["issueCodes"], f"issues: {row['issueCodes']}")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "4", "ALLOW D1")
    _assert_decimal(dimensions.get("D2"), "3", "ALLOW D2")
    return (
        "ALLOW policy with maxMissingRatio 0.2: missing ratio 0.25 recorded, quality status WARNING with "
        "MISSING_RATIO_EXCEEDED, total = answered sum 7 (no proration)"
    )


@case("MT-SCORE-012")
def score_012(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "prorate", "012", {1: "D", 2: "C", 3: "B"})
    _assert_decimal(row["totalScore"], "10", "PRORATE total (6*4/3*1.25)")
    dimensions = result_dimensions(ctx, result_id)
    _assert_decimal(dimensions.get("D1"), "5", "PRORATE D1 (complete)")
    _assert_decimal(dimensions.get("D2"), "2", "PRORATE D2 (1 of 2 items -> factor 2)")
    trace = scoring_trace(ctx, result_id)
    _assert_decimal(trace["prorateFactor"], "1.33333333", "trace prorate factor")
    require(trace["missingAnswerPolicy"] == "PRORATE", f"trace policy: {trace['missingAnswerPolicy']}")
    return "PRORATE scaled 6 -> 10 (x4/3 x1.25); dimension D2 x2 and trace prorateFactor=1.33333333"


@case("MT-SCORE-013")
def score_013(ctx: Context) -> str:
    spec = _spec(ctx, "average")
    scale_id = ensure_published_scale(ctx, "average", spec)

    def submit_with_duration(tag: str, seconds: int) -> dict[str, Any]:
        task_id = create_task(ctx, scale_id, f"MT-SCORE-013-{tag}-{ctx.unique('')}")
        draft = save_draft(ctx, task_id, scale_id, {1: "D", 2: "C", 3: "B", 4: "A"})
        set_answer_sheet_started_at(ctx, int(draft["answerSheetId"]), seconds)
        status, payload, _ = submit_answers(
            ctx,
            task_id,
            scale_id,
            {1: "D", 2: "C", 3: "B", 4: "A"},
            answer_sheet_id=int(draft["answerSheetId"]),
            version_no=int(draft["versionNo"]),
        )
        require(status == 200 and payload.get("code") == "0", f"submit failed: HTTP {status} {payload}")
        return result_row(ctx, int(payload["data"]["resultId"]))

    normal = submit_with_duration("ok", 300)
    require(normal["qualityStatus"] == "VALID", f"300s must be valid: {normal}")
    short = submit_with_duration("short", 5)
    require(short["qualityStatus"] == "REVIEW_REQUIRED", f"too short must require review: {short}")
    require("DURATION_TOO_SHORT" in short["issueCodes"], f"issue codes: {short['issueCodes']}")
    long = submit_with_duration("long", 7200)
    require(long["qualityStatus"] == "REVIEW_REQUIRED", f"too long must require review: {long}")
    require("DURATION_TOO_LONG" in long["issueCodes"], f"issue codes: {long['issueCodes']}")
    del scale_id
    return (
        "duration 300s=VALID, 5s=REVIEW_REQUIRED/DURATION_TOO_SHORT, 7200s=REVIEW_REQUIRED/DURATION_TOO_LONG "
        "(policy REQUIRE_REVIEW)"
    )


@case("MT-SCORE-014")
def score_014(ctx: Context) -> str:
    row, result_id = _submit_and_result(ctx, "reverse", "014", {1: "A", 2: "B", 3: "A", 4: "A"})
    trace = scoring_trace(ctx, result_id)
    for field in (
        "algorithmCode",
        "algorithmVersion",
        "scoreMethod",
        "scoreCoefficient",
        "missingAnswerPolicy",
        "questions",
        "dimensions",
        "totalScore",
        "resultRuleMatched",
    ):
        require(field in trace, f"trace missing {field}: {list(trace)}")
    for question in trace["questions"]:
        for field in ("questionId", "rawScore", "reverseScore", "weightValue", "effectiveScore", "dimensionId"):
            require(field in question, f"trace question missing {field}: {question}")
        require("answerText" not in question or question["answerText"] in (None, ""), f"trace leaked free text: {question}")
    raw = ctx.sql(f"select scoring_trace_json::text from psy_assessment_result where id = {result_id}")
    for leaked in ("选项", "answerText", "MT Q1"):
        require(leaked not in raw, f"trace contains free-text content: {leaked}")
    require(row["hasTrace"] is True, "result must carry the scoring trace")
    scored_at = ctx.sql_one(f"select scored_at::text from psy_assessment_result where id = {result_id}")
    return (
        "scoring_trace_json carries algorithm/version, per-question raw+effective scores, dimensions, policy and "
        "rule matching without free-text answers; generation time is only available on psy_assessment_result.scored_at="
        f"{scored_at}, not inside the trace (recorded as F-28)"
    )


@case("MT-SCORE-015")
def score_015(ctx: Context) -> str:
    _row, result_id = _submit_and_result(ctx, "reverse", "015", {1: "A", 2: "B", 3: "A", 4: "A"})
    admin = ctx.token("assessor")
    first = require_code(ctx.http("POST", f"/api/v1/results/{result_id}/rescore", token=admin), 200)
    first_result = int(first.get("resultId", first.get("id")))
    stale = ctx.http("POST", f"/api/v1/results/{result_id}/rescore", token=admin)
    second = require_code(ctx.http("POST", f"/api/v1/results/{first_result}/rescore", token=admin), 200)
    second_result = int(second.get("resultId", second.get("id")))
    require(
        stale.status == 404,
        f"rescoring a superseded result must fail closed: HTTP {stale.status} {stale.payload}",
    )
    answer_sheet_id = ctx.sql_one(f"select answer_sheet_id from psy_assessment_result where id = {result_id}")

    rows = ctx.sql(
        "select id, calculation_version, is_current, supersedes_result_id from psy_assessment_result "
        f"where answer_sheet_id = {answer_sheet_id} order by calculation_version"
    )
    versions = [line.split("|") for line in rows.splitlines()]
    require(len(versions) == 3, f"expected 3 result versions, found {len(versions)}: {rows}")
    require(
        [item[1] for item in versions] == ["1", "2", "3"],
        f"calculation versions must increment: {rows}",
    )
    current = [item for item in versions if item[2] == "t"]
    require(len(current) == 1 and current[0][0] == str(second_result), f"exactly one current result: {rows}")
    require(versions[1][3] == versions[0][0], f"version 2 must supersede version 1: {rows}")
    require(versions[2][3] == versions[1][0], f"version 3 must supersede version 2: {rows}")
    require(first_result == int(versions[1][0]), f"first rescore id mismatch: {first_result} vs {versions}")

    duplicates = ctx.sql(
        "select count(*) from (select answer_sheet_id from psy_assessment_result where is_current "
        "group by answer_sheet_id having count(*) > 1) duplicates"
    )
    require(duplicates == "0", "no answer sheet may have two current results")
    reports = ctx.sql_one(
        "select count(*) from psy_report report join psy_assessment_result result on result.id = report.result_id "
        f"where result.answer_sheet_id = {answer_sheet_id}"
    )
    require(int(reports) >= 3, f"report history must be preserved per result version, found {reports}")
    return (
        f"rescore twice -> calculation versions 1/2/3 on answer sheet {answer_sheet_id}; only version 3 is_current; "
        f"supersedes chain intact; {reports} reports retained"
    )


@case("MT-SCORE-016")
def score_016(ctx: Context) -> str:
    from harness import EVIDENCE_DIR, ROOT
    from scale_factory import _multipart_file

    package_path = ROOT / "doc/scale-packages/scl90-v2-source-technical.json"
    require(package_path.exists(), f"SCL-90 technical package missing: {package_path}")
    token = ctx.token("assessor")

    existing = ctx.sql(
        "select id from psy_scale where scale_code = 'SCL90_USER_AUTHORIZED' and status = 'DRAFT' "
        "order by id desc limit 1"
    )
    if existing:
        scale_id = int(existing.splitlines()[0])
        import_note = f"reused DRAFT scale {scale_id}"
    else:
        body, content_type = _multipart_file("scl90-v2-source-technical.json", package_path.read_bytes())
        preview = require_code(
            ctx.http(
                "POST",
                "/api/v1/scales/imports/package/preview",
                token=token,
                raw_body=body,
                content_type=content_type,
            ),
            200,
        )
        errors = [issue for issue in preview.get("issues", []) if issue.get("severity") == "ERROR"]
        require(not errors, f"SCL-90 package preview errors: {errors}")
        confirmed = require_code(
            ctx.http(
                "POST",
                f"/api/v1/scales/imports/package/{preview['importId']}/confirm",
                token=token,
            ),
            200,
        )
        scale_id = int(confirmed["scaleId"])
        import_note = (
            f"imported DRAFT scale {scale_id} "
            f"({confirmed.get('createdQuestionCount')} questions, "
            f"{confirmed.get('importedGoldenCaseRevisionCount')} golden cases)"
        )

    cases = require_code(
        ctx.http("GET", f"/api/v1/scales/{scale_id}/publication/golden-cases", token=token), 200
    )
    require(len(cases) >= 5, f"SCL-90 golden cases missing: {len(cases)}")
    run_summary: list[str] = []
    metrics_actual: dict[str, Any] | None = None
    for golden in sorted(cases, key=lambda item: item["caseCode"]):
        run = require_code(
            ctx.http(
                "POST",
                f"/api/v1/scales/{scale_id}/publication/golden-cases/{golden['id']}/run",
                token=token,
            ),
            200,
        )
        if not run["passed"]:
            raise CheckFailure(
                f"SCL-90 golden case {golden['caseCode']} failed: {run['differences']} actual={run['actual']}"
            )
        run_summary.append(f"{golden['caseCode']}={'PASS' if run['passed'] else 'FAIL'}")
        if golden["caseCode"] == "SCL90_ALL_FOUR":
            metrics_actual = run["actual"].get("metrics")
    require(metrics_actual is not None, "SCL90_ALL_FOUR metrics were not produced")

    (EVIDENCE_DIR / "MT-SCORE-016-scl90.json").write_text(
        json.dumps(
            {
                "scaleId": scale_id,
                "import": import_note,
                "runs": run_summary,
                "metricsAllFour": metrics_actual,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    readiness = require_code(
        ctx.http("GET", f"/api/v1/scales/{scale_id}/publication/readiness", token=token), 200
    )
    blockers = readiness.get("blockers", [])
    review_blockers = [item for item in blockers if item.startswith("REVIEW_")]
    raise CheckBlocked(
        "GSI/PST/PSDI closure verified on the DRAFT technical package "
        f"({import_note}; runs: {', '.join(run_summary)}; metrics={json.dumps(metrics_actual, ensure_ascii=False)}). "
        "The live publish/report path stays blocked: scale cannot be published without external rights scope and "
        f"independent professional/business sign-off (readiness blockers include {review_blockers or blockers[:3]})"
    )
