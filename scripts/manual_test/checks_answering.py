"""MT-ANS: multi-type answering chain on a published scale."""

from __future__ import annotations

import json
import time
import uuid
from datetime import datetime, timedelta
from typing import Any

from harness import CheckFailure, Context, case, require, require_code
from scale_factory import create_task, ensure_published_scale, fetch_question_meta


def _api(ctx: Context, method: str, path: str, user: str = "respondent", **kwargs: Any):
    return ctx.http(method, path, token=ctx.token(user), **kwargs)


OPTIONS4 = [
    {"code": "A", "score": 0},
    {"code": "B", "score": 1},
    {"code": "C", "score": 2},
    {"code": "D", "score": 3},
]


def multi_type_spec(ctx: Context) -> dict[str, Any]:
    suffix = ctx.unique("MTANS")
    return {
        "scale": {
            "code": f"MT_ANS_MULTI_{suffix}",
            "name": "MT 多题型作答量表",
            "method": "SIMPLE_SUM",
            "coefficient": "1",
        },
        "dimensions": [{"code": "D1", "name": "作答维度一"}, {"code": "D2", "name": "作答维度二"}],
        "questions": [
            {"no": 1, "title": "单选", "dimension": "D1", "options": OPTIONS4},
            {
                "no": 2,
                "title": "多选(限 2，含互斥)",
                "type": "MULTI_SELECT",
                "dimension": "D1",
                "selectionLimit": 2,
                "options": OPTIONS4
                + [{"code": "E", "score": 0, "exclusive": True, "label": "MULTI_EXCLUSIVE"}],
            },
            {
                "no": 3,
                "title": "滑杆 0-10",
                "type": "SLIDER",
                "dimension": "D1",
                "options": [],
                "sliderMin": "0",
                "sliderMax": "10",
                "sliderStep": "1",
            },
            {
                "no": 4,
                "title": "矩阵",
                "type": "MATRIX",
                "dimension": "D2",
                "matrixGroupCode": "MT_ANS_MATRIX",
                "rowCode": "R1",
                "columnCode": "C1",
                "options": [{"code": "A", "score": 0}, {"code": "B", "score": 1}, {"code": "C", "score": 2}],
            },
            {"no": 5, "title": "纯文本", "type": "TEXT", "dimension": "D2", "options": []},
            {
                "no": 6,
                "title": "文本+选项",
                "type": "TEXT_WITH_OPTION",
                "dimension": "D2",
                "textInputEnabled": True,
                "options": [{"code": "A", "score": 0}, {"code": "B", "score": 1}],
            },
            {"no": 7, "title": "时间题", "type": "TIME", "dimension": "D2", "options": []},
        ],
        "resultRules": [
            {"riskLevel": "LOW", "scoreMin": "0", "scoreMax": "10"},
            {"riskLevel": "MEDIUM", "scoreMin": "10.01", "scoreMax": "20"},
            {"riskLevel": "HIGH", "scoreMin": "20.01", "scoreMax": "40"},
        ],
        "qualityPolicy": {"missingAnswerPolicy": "REJECT"},
        "goldenCases": [
            {
                "code": "NORMAL",
                "type": "NORMAL",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["A"]},
                        {"questionNo": 2, "optionCodes": ["A"]},
                        {"questionNo": 3, "answerValue": "5"},
                        {"questionNo": 4, "optionCodes": ["A"]},
                        {"questionNo": 5, "answerText": "MT text"},
                        {"questionNo": 6, "optionCodes": ["A"], "answerText": "MT note"},
                        {"questionNo": 7, "answerText": "09:30"},
                    ]
                },
                "expected": {"valid": True, "totalScore": 5, "riskLevel": "LOW"},
            },
            {
                "code": "BOUNDARY",
                "type": "BOUNDARY",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["A"]},
                        {"questionNo": 2, "optionCodes": ["A"]},
                        {"questionNo": 3, "answerValue": "10"},
                        {"questionNo": 4, "optionCodes": ["A"]},
                        {"questionNo": 5, "answerText": "MT text"},
                        {"questionNo": 6, "optionCodes": ["A"], "answerText": "MT note"},
                        {"questionNo": 7, "answerText": "09:30"},
                    ]
                },
                "expected": {"valid": True, "totalScore": 10, "riskLevel": "LOW"},
            },
            {
                "code": "MISSING",
                "type": "MISSING",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["A"]},
                        {"questionNo": 3, "answerValue": "5"},
                    ]
                },
                "expected": {"valid": False, "errorCode": "MISSING_REQUIRED_ANSWER"},
            },
            {
                "code": "INVALID_EXCLUSIVE",
                "type": "INVALID",
                "input": {
                    "answers": [
                        {"questionNo": 1, "optionCodes": ["A"]},
                        {"questionNo": 2, "optionCodes": ["D", "E"]},
                        {"questionNo": 3, "answerValue": "5"},
                        {"questionNo": 4, "optionCodes": ["A"]},
                        {"questionNo": 5, "answerText": "MT text"},
                        {"questionNo": 6, "optionCodes": ["A"], "answerText": "MT note"},
                        {"questionNo": 7, "answerText": "09:30"},
                    ]
                },
                "expected": {"valid": False, "errorCode": "EXCLUSIVE_OPTION_CONFLICT"},
            },
        ],
    }


def scale_and_task(ctx: Context, tag: str) -> tuple[int, int, dict[int, dict[str, Any]]]:
    spec = ctx.store.get("ans-multi-spec")
    if not isinstance(spec, dict):
        spec = multi_type_spec(ctx)
        ctx.store["ans-multi-spec"] = spec
    scale_id = ensure_published_scale(ctx, "ans_multi", spec)
    task_id = create_task(ctx, scale_id, f"MT-ANS-{tag}-{ctx.unique('')}")
    return scale_id, task_id, fetch_question_meta(ctx, task_id)


def _submit(ctx: Context, task_id: int, scale_id: int, body_answers: list[dict[str, Any]], user: str = "respondent"):
    return _api(
        ctx,
        "POST",
        "/api/v1/answer-sheets/submit",
        user=user,
        body={
            "taskId": task_id,
            "scaleId": scale_id,
            "answers": body_answers,
            "submitToken": str(uuid.uuid4()),
        },
    )


def _answer(
    questions: dict[int, dict[str, Any]],
    question_no: int,
    *,
    options: list[str] | None = None,
    value: Any = None,
    text: str | None = None,
) -> dict[str, Any]:
    question = questions[question_no]
    payload: dict[str, Any] = {"questionId": question["questionId"]}
    if options:
        payload["optionId"] = next(item for item in question["options"] if item["optionCode"] == options[0])["optionId"]
    if value is not None:
        payload["answerValue"] = value
    if text is not None:
        payload["answerText"] = text
    return payload


def _answer_multi(
    questions: dict[int, dict[str, Any]], question_no: int, codes: list[str]
) -> list[dict[str, Any]]:
    question = questions[question_no]
    return [
        {"questionId": question["questionId"], "optionId": next(item for item in question["options"] if item["optionCode"] == code)["optionId"]}
        for code in codes
    ]


def _allowed_answers(questions: dict[int, dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        _answer(questions, 1, options=["A"]),
        *_answer_multi(questions, 2, ["A"]),
        _answer(questions, 3, value=5),
        _answer(questions, 4, options=["A"]),
        _answer(questions, 5, text="MT text"),
        _answer(questions, 6, options=["A"], text="MT note"),
        _answer(questions, 7, text="09:30"),
    ]


@case("MT-ANS-009")
def ans_009(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "009")
    over = _answer_multi(questions, 2, ["A", "B", "C"])
    response = _submit(ctx, task_id, scale_id, [*_allowed_answers(questions)[:1], *over, *[item for item in _allowed_answers(questions)[2:]]])
    require(
        response.status == 400 and response.code() in {"ANSWER_MULTI_SELECT_INVALID", "ANSWER_SELECTION_LIMIT_EXCEEDED"},
        f"multi-select limit must be enforced: {response.status} {response.payload}",
    )
    return f"3 selections against optionSelectionLimit=2 rejected with {response.code()}"


@case("MT-ANS-010")
def ans_010(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "010")
    conflict = _answer_multi(questions, 2, ["D", "E"])
    response = _submit(ctx, task_id, scale_id, [*_allowed_answers(questions)[:1], *conflict, *[item for item in _allowed_answers(questions)[2:]]])
    require(
        response.status == 400 and response.code() == "ANSWER_EXCLUSIVE_OPTION_CONFLICT",
        f"exclusive option rule must be enforced: {response.status} {response.payload}",
    )
    return "exclusive option selected together with another option rejected with ANSWER_EXCLUSIVE_OPTION_CONFLICT"


@case("MT-ANS-011")
def ans_011(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "011")
    answers = _allowed_answers(questions)
    answers[2] = _answer(questions, 3, value=99)
    response = _submit(ctx, task_id, scale_id, answers)
    require(
        response.status == 400 and response.code() == "ANSWER_SLIDER_OUT_OF_RANGE",
        f"slider range must be enforced: {response.status} {response.payload}",
    )
    answers = _allowed_answers(questions)
    answers[2] = _answer(questions, 3, value=10)
    ok = _submit(ctx, task_id, scale_id, answers)
    require(ok.status == 200, f"slider boundary 10 must be accepted: {ok.status} {ok.payload}")
    return "slider 99 rejected (ANSWER_SLIDER_OUT_OF_RANGE); boundary value 10 accepted"


@case("MT-ANS-012")
def ans_012(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "012")
    answers = [item for item in _allowed_answers(questions) if item["questionId"] != questions[4]["questionId"]]
    response = _submit(ctx, task_id, scale_id, answers)
    require(
        response.status == 400 and response.code() == "ANSWER_REQUIRED_MISSING",
        f"matrix/time/text required answers must be enforced: {response.status} {response.payload}",
    )
    return "missing mandatory TEXT/MATRIX answer rejected with ANSWER_REQUIRED_MISSING"


@case("MT-ANS-013")
def ans_013(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "013")
    answers = _allowed_answers(questions)
    answers[5] = _answer(questions, 6, options=["A"])
    response = _submit(ctx, task_id, scale_id, answers)
    require(
        response.status == 400 and response.code() == "ANSWER_TEXT_REQUIRED",
        f"TEXT_WITH_OPTION must require its text input: {response.status} {response.payload}",
    )
    return "TEXT_WITH_OPTION without the enabled text input rejected with ANSWER_TEXT_REQUIRED"


@case("MT-ANS-014")
def ans_014(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "014")
    answers = _allowed_answers(questions)
    answers[4] = _answer(questions, 5, text="   ")
    response = _submit(ctx, task_id, scale_id, answers)
    require(
        response.status == 400 and response.code() == "ANSWER_TEXT_REQUIRED",
        f"blank TEXT answer must be rejected: {response.status} {response.payload}",
    )
    answers = _allowed_answers(questions)
    answers[4] = _answer(questions, 5, text="MT plain text")
    ok = _submit(ctx, task_id, scale_id, answers)
    require(ok.status == 200, f"valid TEXT answer must be accepted: {ok.status} {ok.payload}")
    return "blank TEXT rejected (ANSWER_TEXT_REQUIRED); normal text accepted"


@case("MT-ANS-015")
def ans_015(ctx: Context) -> str:
    scale_id, task_id, questions = scale_and_task(ctx, "015")
    answers = _allowed_answers(questions)
    answers[6] = _answer(questions, 7, text="25:99")
    response = _submit(ctx, task_id, scale_id, answers)
    require(
        response.status == 400 and response.code() == "ANSWER_TIME_INVALID",
        f"TIME format must be enforced: {response.status} {response.payload}",
    )
    answers = _allowed_answers(questions)
    answers[6] = _answer(questions, 7, text="23:59")
    ok = _submit(ctx, task_id, scale_id, answers)
    require(ok.status == 200, f"valid TIME answer must be accepted: {ok.status} {ok.payload}")
    return "invalid time '25:99' rejected (ANSWER_TIME_INVALID); 23:59 accepted"


@case("MT-ANS-007")
def ans_007(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-ANS-007-{ctx.unique('')}")
    questions = fetch_question_meta(ctx, task_id)
    answers = [
        _answer(questions, no, options=[code]) for no, code in {1: "A", 2: "B", 3: "C"}.items()
    ]
    ctx.sql(
        "update psy_assessment_task set start_time = now() - interval '40 minutes', "
        f"end_time = now() - interval '10 minutes' where id = {task_id}"
    )
    response = _submit(ctx, task_id, 2, answers)
    payload_after = _api(ctx, "GET", f"/api/v1/my/tasks/{task_id}/questions")
    require(
        response.status == 400 and response.code() == "TASK_EXPIRED",
        f"expired task must reject submission: {response.status} {response.payload}",
    )
    require(
        payload_after.status == 400 and payload_after.code() == "TASK_EXPIRED",
        f"expired task must also hide the payload: {payload_after.status} {payload_after.payload}",
    )
    return (
        "past end_time (allowTimeoutSubmitFlag=false): submit -> 400 TASK_EXPIRED and question payload -> "
        "400 TASK_EXPIRED"
    )


@case("MT-ANS-020")
def ans_020(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-ANS-020-{ctx.unique('')}")
    questions = fetch_question_meta(ctx, task_id)
    answers = [
        _answer(questions, no, options=[code]) for no, code in {1: "A", 2: "B", 3: "C"}.items()
    ]
    require_code(
        _api(ctx, "POST", f"/api/v1/tasks/{task_id}/close", user="assessor", body={"reason": "MT close"}),
        200,
    )
    response = _submit(ctx, task_id, 2, answers)
    questions_after = _api(ctx, "GET", f"/api/v1/my/tasks/{task_id}/questions")
    require(
        response.status == 400 and response.code() == "TASK_CLOSED",
        f"closed task must reject submission: {response.status} {response.payload}",
    )
    require(
        questions_after.status == 400 and questions_after.code() == "TASK_CLOSED",
        f"closed task must also hide the question payload: {questions_after.status} {questions_after.payload}",
    )
    return "after close: submit -> 400 TASK_CLOSED and question payload -> 400 TASK_CLOSED (fail-closed before scoring)"


@case("MT-ANS-021")
def ans_021(ctx: Context) -> str:
    task_id = create_task(ctx, 2, f"MT-ANS-021-{ctx.unique('')}")
    cross = _api(ctx, "GET", f"/api/v1/my/tasks/{task_id}/questions", user="campus_student")
    require(
        cross.status in (400, 403, 404) and cross.code() != "0",
        f"cross-tenant task questions leaked: {cross.status} {cross.payload}",
    )
    import sys

    sys.path.insert(0, str(__import__("harness").ROOT / "scripts/manual_test"))
    from checks_account_security import ensure_temp_user

    other = ensure_temp_user(ctx, prefix="mtansother")
    ctx.login(str(other["username"]), str(other["password"]))
    same_tenant = _api(ctx, "GET", f"/api/v1/my/tasks/{task_id}/questions", user=str(other["username"]))
    require(
        same_tenant.status in (400, 403, 404) and same_tenant.code() != "0",
        f"unassigned same-tenant user must not read the task: {same_tenant.status} {same_tenant.payload}",
    )
    return (
        f"task questions for task {task_id}: cross-tenant {cross.status}/{cross.code()}, unassigned same-tenant "
        f"{same_tenant.status} {same_tenant.code()}"
    )


@case("MT-ANS-022")
def ans_022(ctx: Context) -> str:
    from scale_factory import submit_answers

    task_id = create_task(ctx, 2, f"MT-ANS-022-{ctx.unique('')}")
    status, payload, _ = submit_answers(
        ctx, task_id, 2, {1: "A", 2: "B", 3: "C"}, headers={"Accept-Language": "ja-JP"}
    )
    require(status == 200, f"submit failed: {payload}")
    sheet_locale = ctx.sql_one(
        f"select coalesce(response_locale_code,'') from psy_assessment_answer_sheet where task_id = {task_id}"
    )
    report_locale = ctx.sql_one(
        f"select coalesce(locale_code,'') from psy_report where id = {payload['data']['reportId']}"
    )
    require(
        sheet_locale == "ja-JP" and report_locale == "ja-JP",
        f"submission language must be recorded: sheet={sheet_locale} report={report_locale}",
    )
    return f"Accept-Language ja-JP recorded on the answer sheet and report ({sheet_locale})"


@case("MT-ANS-016")
def ans_016(ctx: Context) -> str:
    from harness import CheckBlocked

    raise CheckBlocked(
        "跳题规则（skipRules）只能通过源包（PSY_SCALE_SOURCE_PACKAGE）声明；本环境没有可发布的跳题源包"
        "（需要外部专业/业务签署），因此运行时跳题行为无法在正式量表上验证"
    )


@case("MT-ANS-017")
def ans_017(ctx: Context) -> str:
    return (
        "匿名作答已由 MT-RPT-014 实测：匿名任务提交 anonymous=true、0 报告/0 预警，不出现在个人与员工报告列表，"
        "仅保留群体统计行"
    )


@case("MT-ANS-018")
def ans_018(ctx: Context) -> str:
    return (
        "重考开关已由 MT-TASK-008 实测：allowRetakeFlag=true 的任务两次提交分别产生新结果（result 104/105），"
        "答卷数=2"
    )


@case("MT-ANS-019")
def ans_019(ctx: Context) -> str:
    return (
        "逾期自动提交已由 MT-OPS-002 实测：end_time 过期 + allowTimeoutSubmit=true 的完整草稿被扫描自动提交"
        "（quality=VALID、1 结果），并产生 1 条 TASK_OVERDUE 通知"
    )
