#!/usr/bin/env python3
"""Validate a reusable GENERIC_SINGLE_CHOICE ScalePackage.

This is a structural and deterministic-scoring gate.  It never upgrades draft
translations, clinical interpretation, authorization, or business acceptance.
"""

from __future__ import annotations

import argparse
import json
from decimal import Decimal
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
LOCALES = {"zh-CN", "ja-JP", "en"}
REQUIRED_CASE_TYPES = {"NORMAL", "BOUNDARY", "MISSING", "INVALID"}
SUPPORTED_SCORE_METHODS = {"SIMPLE_SUM", "REVERSE_SUM", "WEIGHTED_SUM", "AVERAGE", "WEIGHTED_AVERAGE"}
SUPPORTED_REPORT_TEMPLATES = {"DEFAULT_SCREENING", "SINGLE_SCORE", "DIMENSION_PROFILE", "RISK_TRIAGE"}
SUPPORTED_INDICES = {
    "WHO5_PERCENTAGE_SCORE": "raw total score multiplied by 4 (0-100)",
}


def fail(message: str) -> None:
    raise SystemExit(f"GENERIC_SCALE_PACKAGE_INVALID: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def nonblank(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def number(value: Any) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def load_package(path: Path) -> dict[str, Any]:
    resolved = path.resolve()
    try:
        resolved.relative_to(ROOT)
    except ValueError:
        fail(f"package escapes repository root: {path}")
    require(resolved.is_file(), f"package is missing: {resolved}")
    try:
        value = json.loads(resolved.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        fail(f"invalid JSON: {error}")
    require(isinstance(value, dict), "package root must be an object")
    return value


def validate_localized_record(record: Any, label: str, fields: tuple[str, ...]) -> None:
    require(isinstance(record, dict) and set(record) == LOCALES, f"{label} must contain exactly {sorted(LOCALES)}")
    for locale, translation in record.items():
        require(isinstance(translation, dict), f"{label}.{locale} must be an object")
        for field in fields:
            require(nonblank(translation.get(field)), f"{label}.{locale}.{field} is required")


def validate(package: dict[str, Any]) -> tuple[str, str, int, int]:
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", "format")
    require(package.get("schemaVersion") == 1, "schemaVersion")

    scale = package.get("scale")
    require(isinstance(scale, dict), "scale must be an object")
    for field in ("scaleCode", "scaleName", "versionNo", "reportTemplate"):
        require(nonblank(scale.get(field)), f"scale.{field} is required")
    require(scale["reportTemplate"] in SUPPORTED_REPORT_TEMPLATES, "generic reportTemplate is unsupported")
    score_method = str(scale.get("scoreMethod", "")).strip().upper()
    require(score_method in SUPPORTED_SCORE_METHODS, "GENERIC_SINGLE_CHOICE supports SIMPLE_SUM, REVERSE_SUM, WEIGHTED_SUM, AVERAGE or WEIGHTED_AVERAGE")
    require(number(scale.get("scoreCoefficient")) and Decimal(str(scale["scoreCoefficient"])) > 0, "scoreCoefficient must be positive")
    require(scale.get("algorithmBinding") == {
        "algorithmCode": "GENERIC_SCORE_CALCULATOR",
        "algorithmVersion": "1",
        "implementationType": "BUILTIN",
    }, "generic algorithm binding")
    scoring = package.get("scoring") or {}
    require(isinstance(scoring, dict), "scoring must be an object when present")
    indices = scoring.get("indices", {})
    require(isinstance(indices, dict), "scoring.indices must be an object")
    require(set(indices) <= set(SUPPORTED_INDICES), "scoring.indices contains an unsupported derived metric")
    for metric_code, metric_definition in indices.items():
        require(
            isinstance(metric_definition, str)
            and metric_definition.strip()
            and metric_definition == SUPPORTED_INDICES[metric_code],
            f"scoring.indices.{metric_code} must use the registered definition",
        )
    dimension_aggregation = str(scoring.get("dimensionAggregation", score_method)).strip().upper()
    require(dimension_aggregation in SUPPORTED_SCORE_METHODS, "generic dimensionAggregation supports SIMPLE_SUM, REVERSE_SUM, WEIGHTED_SUM, AVERAGE or WEIGHTED_AVERAGE")
    response_scale = scale.get("responseScale")
    require(isinstance(response_scale, dict), "scale.responseScale is required")
    require(number(response_scale.get("min")) and number(response_scale.get("max")), "response scale min/max")
    require(response_scale["min"] <= response_scale["max"], "response scale range")
    response_min = Decimal(str(response_scale["min"]))
    response_max = Decimal(str(response_scale["max"]))
    quality = scale.get("qualityPolicy")
    require(isinstance(quality, dict), "quality policy")
    require(quality.get("missingAnswerPolicy") == "REJECT", "generic closure requires REJECT missing policy")
    require(quality.get("maxMissingRatio") == 0, "generic closure requires maxMissingRatio=0")
    require(quality.get("requireAllRequiredAnswers") is True, "generic closure requires every required answer")
    instructions = scale.get("instruction")
    require(isinstance(instructions, dict) and set(instructions) == LOCALES, "instruction locale matrix")
    require(all(nonblank(value) for value in instructions.values()), "instruction text")

    validate_localized_record(
        package.get("translations"),
        "translations",
        ("scaleName", "purposeText", "nonDiagnosticText", "helpResourceText"),
    )

    dimensions = package.get("dimensions")
    require(isinstance(dimensions, list) and dimensions, "dimensions must be non-empty")
    dimension_codes: set[str] = set()
    dimension_questions: dict[str, set[int]] = {}
    question_dimension_owners: dict[int, str] = {}
    for index, dimension in enumerate(dimensions):
        require(isinstance(dimension, dict), f"dimensions[{index}]")
        code = dimension.get("dimensionCode")
        require(nonblank(code) and code not in dimension_codes, f"dimensions[{index}].dimensionCode")
        dimension_codes.add(code)
        question_nos = dimension.get("questionNos")
        require(isinstance(question_nos, list) and question_nos and all(isinstance(value, int) and value > 0 for value in question_nos), f"dimensions[{index}].questionNos")
        require(len(question_nos) == len(set(question_nos)), f"dimensions[{index}] duplicate question number")
        for question_no in question_nos:
            require(
                question_no not in question_dimension_owners,
                f"dimensions[{index}].questionNos question {question_no} is declared in multiple dimensions",
            )
            question_dimension_owners[question_no] = code
        dimension_questions[code] = set(question_nos)
        validate_localized_record(dimension.get("translations"), f"dimensions[{index}].translations", ("name", "description"))

    questions = package.get("questions")
    require(isinstance(questions, list) and questions, "questions must be non-empty")
    require([question.get("questionNo") for question in questions] == list(range(1, len(questions) + 1)), "question numbers must be consecutive from 1")
    # The reusable single-choice profile has no declaration-only branching;
    # a package with skip rules must use a dedicated profile and closure.
    require(package.get("skipRules", []) == [], "GENERIC_SINGLE_CHOICE does not support skipRules")
    seen_dimension_questions: set[int] = set()
    reachable_sums: set[Decimal] = {Decimal("0")}
    reachable_weight_sums: set[Decimal] = {Decimal("0")}
    for index, question in enumerate(questions):
        label = f"questions[{index}]"
        require(isinstance(question, dict), label)
        question_no = question["questionNo"]
        dimension_code = question.get("dimensionCode")
        require(dimension_code in dimension_codes, f"{label}.dimensionCode")
        require(question_no in dimension_questions[dimension_code], f"{label} missing from dimension questionNos")
        seen_dimension_questions.add(question_no)
        require(question.get("questionType") == "SINGLE_CHOICE", f"{label}.questionType")
        require(question.get("required") is True, f"{label}.required")
        require(isinstance(question.get("reverseScore"), bool), f"{label}.reverseScore")
        weight_value = question.get("weightValue", 1)
        require(number(weight_value) and Decimal(str(weight_value)) > 0, f"{label}.weightValue must be positive")
        weight = Decimal(str(weight_value))
        validate_localized_record(question.get("translations"), f"{label}.translations", ("text",))
        options = question.get("options")
        require(isinstance(options, list) and len(options) >= 2, f"{label}.options")
        codes = [option.get("code") for option in options if isinstance(option, dict)]
        require(len(codes) == len(options) and all(nonblank(code) for code in codes), f"{label} option codes")
        require(len(codes) == len(set(codes)), f"{label} duplicate option code")
        for option_index, option in enumerate(options):
            require(number(option.get("score")), f"{label}.options[{option_index}].score")
            option_score = Decimal(str(option["score"]))
            require(response_min <= option_score <= response_max, f"{label}.options[{option_index}].score outside responseScale")
            translations = option.get("translations")
            require(isinstance(translations, dict) and set(translations) == LOCALES, f"{label}.options[{option_index}] locales")
            require(all(nonblank(value) for value in translations.values()), f"{label}.options[{option_index}] labels")
        effective_scores = {
            response_min + response_max - Decimal(str(option["score"]))
            if question["reverseScore"] else Decimal(str(option["score"]))
            for option in options
        }
        effective_scores = {
            score * weight
            if score_method in {"WEIGHTED_SUM", "WEIGHTED_AVERAGE"}
            else score
            for score in effective_scores
        }
        reachable_sums = {subtotal + score for subtotal in reachable_sums for score in effective_scores}
        reachable_weight_sums = {subtotal + weight for subtotal in reachable_weight_sums}
        require(len(reachable_sums) <= 100_000, "reachable score domain is too large for GENERIC_SINGLE_CHOICE validation")
    require(seen_dimension_questions == set(range(1, len(questions) + 1)), "every question must belong to one declared dimension")
    if score_method == "AVERAGE":
        reachable_totals = {total / Decimal(len(questions)) for total in reachable_sums}
    elif score_method == "WEIGHTED_AVERAGE":
        reachable_totals = {
            total / weight_total
            for total in reachable_sums
            for weight_total in reachable_weight_sums
            if weight_total > 0
        }
    else:
        reachable_totals = reachable_sums
    coefficient = Decimal(str(scale["scoreCoefficient"]))
    reachable_totals = {total * coefficient for total in reachable_totals}

    rules = package.get("resultRules")
    require(isinstance(rules, list) and rules, "resultRules must be non-empty")
    rule_codes: set[str] = set()
    ranges: list[tuple[Decimal, Decimal]] = []
    for index, rule in enumerate(rules):
        label = f"resultRules[{index}]"
        require(isinstance(rule, dict), label)
        code = rule.get("ruleCode")
        require(nonblank(code) and code not in rule_codes, f"{label}.ruleCode")
        rule_codes.add(code)
        require(nonblank(rule.get("riskLevel")), f"{label}.riskLevel")
        require(rule.get("scoreSource") == "RAW_SCORE", f"{label}.scoreSource")
        require(number(rule.get("scoreMin")) and number(rule.get("scoreMax")), f"{label} range")
        minimum, maximum = Decimal(str(rule["scoreMin"])), Decimal(str(rule["scoreMax"]))
        require(minimum <= maximum, f"{label} inverted range")
        require(all(maximum < other_min or minimum > other_max for other_min, other_max in ranges), f"{label} overlaps another result range")
        ranges.append((minimum, maximum))
        validate_localized_record(rule.get("translations"), f"{label}.translations", ("resultTitle", "resultDescription", "suggestionText"))
    sorted_ranges = sorted(ranges)
    require(sorted_ranges[0][0] == min(reachable_totals), "result rules must start at the minimum reachable total")
    require(sorted_ranges[-1][1] == max(reachable_totals), "result rules must end at the maximum reachable total")
    for total in reachable_totals:
        matches = sum(1 for minimum, maximum in ranges if minimum <= total <= maximum)
        require(matches == 1, f"reachable total {total} must match exactly one result rule")

    high_risk_rules = package.get("highRiskRules")
    require(isinstance(high_risk_rules, list), "highRiskRules must be a list")
    high_risk_codes: set[str] = set()
    for index, rule in enumerate(high_risk_rules):
        label = f"highRiskRules[{index}]"
        require(isinstance(rule, dict), label)
        code = rule.get("ruleCode")
        require(nonblank(code) and code not in high_risk_codes, f"{label}.ruleCode")
        high_risk_codes.add(code)
        question_no = rule.get("questionNo")
        require(isinstance(question_no, int) and 1 <= question_no <= len(questions), f"{label}.questionNo")
        option_code = rule.get("optionCode")
        threshold = rule.get("scoreThreshold")
        require((nonblank(option_code) ^ number(threshold)), f"{label} must use exactly one optionCode or scoreThreshold")
        question = questions[question_no - 1]
        option_codes = {option.get("code") for option in question["options"]}
        if option_code is not None:
            require(option_code in option_codes, f"{label}.optionCode does not exist on question {question_no}")
        if threshold is not None:
            require(response_min <= Decimal(str(threshold)) <= response_max, f"{label}.scoreThreshold outside responseScale")
        require(nonblank(rule.get("warningLevel")), f"{label}.warningLevel")
        validate_localized_record(
            rule.get("translations"),
            f"{label}.translations",
            ("resultTitle", "resultDescription", "suggestionText"),
        )

    cases = package.get("goldenCases")
    require(isinstance(cases, list) and cases, "goldenCases must be non-empty")
    case_codes: set[str] = set()
    case_types: set[str] = set()
    valid_totals: list[Decimal] = []
    for index, case in enumerate(cases):
        label = f"goldenCases[{index}]"
        require(isinstance(case, dict), label)
        code = case.get("caseCode")
        require(nonblank(code) and code not in case_codes, f"{label}.caseCode")
        case_codes.add(code)
        case_type = case.get("caseType")
        require(nonblank(case_type), f"{label}.caseType")
        case_types.add(case_type)
        require(nonblank(case.get("sourceReference")), f"{label}.sourceReference")
        answers = (case.get("input") or {}).get("answers")
        require(isinstance(answers, list), f"{label}.input.answers")
        expected = case.get("expected")
        require(isinstance(expected, dict) and isinstance(expected.get("valid"), bool), f"{label}.expected")
        if expected["valid"]:
            require(number(expected.get("totalScore")) and nonblank(expected.get("riskLevel")), f"{label} valid expectation")
            require(isinstance(expected.get("metrics", {}), dict), f"{label}.expected.metrics")
            if "highRiskTriggered" in expected:
                require(isinstance(expected["highRiskTriggered"], bool), f"{label}.expected.highRiskTriggered")
            if expected.get("highRiskTriggered") is True:
                require(high_risk_rules, f"{label} expects high risk but package has no highRiskRules")
                require(nonblank(expected.get("highRiskRuleCode")), f"{label}.expected.highRiskRuleCode")
                require(expected["highRiskRuleCode"] in high_risk_codes, f"{label}.expected.highRiskRuleCode is not registered")
            if expected.get("highRiskRuleCode") is not None:
                require(expected.get("highRiskTriggered") is True, f"{label}.expected.highRiskRuleCode requires highRiskTriggered=true")
            valid_totals.append(Decimal(str(expected["totalScore"])))
        else:
            require(nonblank(expected.get("errorCode")), f"{label} invalid expectation needs errorCode")
    require(REQUIRED_CASE_TYPES <= case_types, f"Golden Cases must include {sorted(REQUIRED_CASE_TYPES)}")
    if high_risk_rules:
        require("HIGH_RISK" in case_types, "packages with highRiskRules must include a HIGH_RISK Golden Case")
    require(all(any(minimum <= total <= maximum for minimum, maximum in ranges) for total in valid_totals), "every valid Golden total must match a result range")

    governance = package.get("governance")
    require(isinstance(governance, dict), "governance")
    for field in ("sourceTitle", "publisherName", "authorizationStatus", "authorizationType", "authorizationScope", "targetPopulation", "nonDiagnosticStatement", "reviewStatus"):
        require(nonblank(governance.get(field)), f"governance.{field}")
    require(
        governance.get("copyrightStatus") in {"PENDING_REVIEW", "AUTHORIZED", "PUBLIC_DOMAIN", "RESTRICTED", "EXPIRED", "REJECTED"},
        "governance.copyrightStatus must use a backend-supported state",
    )
    require(
        governance.get("authorizationStatus") in {"PENDING_REVIEW", "AUTHORIZED", "NOT_REQUIRED", "RESTRICTED", "EXPIRED", "REJECTED"},
        "governance.authorizationStatus must use a backend-supported state",
    )
    require(
        governance.get("governanceStatus") in {"DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED"},
        "governance.governanceStatus must use a backend-supported state",
    )
    references = package.get("sourceReferences")
    require(isinstance(references, list) and len(references) >= 2, "sourceReferences")
    for index, reference in enumerate(references):
        require(isinstance(reference, dict), f"sourceReferences[{index}]")
        require(nonblank(reference.get("title")) and nonblank(reference.get("use")), f"sourceReferences[{index}] metadata")
        parsed = urlparse(str(reference.get("url", "")))
        require(parsed.scheme == "https" and bool(parsed.netloc), f"sourceReferences[{index}].url must be HTTPS")
    blockers = package.get("publicationBlockers")
    require(isinstance(blockers, list) and blockers and len(blockers) == len(set(blockers)), "publicationBlockers")
    require("PROFESSIONAL_REVIEW_PENDING" in blockers, "professional review blocker")
    require("BUSINESS_ACCEPTANCE_PENDING" in blockers, "business acceptance blocker")
    return scale["scaleCode"], scale["versionNo"], len(questions), len(cases)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("package", type=Path)
    args = parser.parse_args()
    code, version, question_count, case_count = validate(load_package(args.package))
    print(f"Generic scale package valid: {code}@{version}, {question_count} questions, 3 locales, {case_count} Golden Cases")
    print("Publication remains governed by the package blockers; this validation is technical only.")


if __name__ == "__main__":
    main()
