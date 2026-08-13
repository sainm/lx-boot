#!/usr/bin/env python3
"""Validate the reviewable SCL-90 source package without a database.

The validator checks structural closure only.  It deliberately does not turn
draft translations, norms, rights, or professional review into approval.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "doc" / "scale-packages" / "scl90-v1-source-draft.json"
LOCALES = {"zh-CN", "ja-JP", "en"}
EXPECTED_DIMS = {"SOM", "OCD", "INT", "DEP", "ANX", "HOS", "PHOB", "PAR", "PSY", "OTHER"}


def fail(message: str) -> None:
    raise SystemExit(f"SCL90_SOURCE_PACKAGE_INVALID: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    if not PACKAGE.exists():
        fail(f"missing {PACKAGE}")
    package = json.loads(PACKAGE.read_text(encoding="utf-8"))
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", "format")
    require(package.get("schemaVersion") == 1, "schemaVersion")
    scale = package.get("scale", {})
    require(scale.get("scaleCode") == "SCL90_USER_DRAFT", "scale code")
    require(scale.get("responseScale", {}).get("min") == 0, "response min")
    require(scale.get("responseScale", {}).get("max") == 4, "response max")
    require(scale.get("algorithmBinding", {}).get("algorithmCode") == "SCL90_PROFILE", "algorithm binding")

    translations = package.get("translations", {})
    require(set(translations) == LOCALES, "scale translation locales")
    require(all(translations[locale].get("reviewStatus") == "DRAFT" for locale in LOCALES), "scale translations must remain draft")

    dimensions = package.get("dimensions", [])
    require(len(dimensions) == 10, "dimension count")
    dim_codes = {dimension.get("dimensionCode") for dimension in dimensions}
    require(dim_codes == EXPECTED_DIMS, "dimension codes")
    require(all(set(dimension.get("translations", {})) == LOCALES for dimension in dimensions), "dimension translation matrix")

    questions = package.get("questions", [])
    require(len(questions) == 90, "question count")
    question_nos = [question.get("questionNo") for question in questions]
    require(question_nos == list(range(1, 91)), "question numbers must be 1..90 in order")
    require(all(question.get("dimensionCode") in EXPECTED_DIMS for question in questions), "question dimension references")

    for question in questions:
        translations = question.get("translations", {})
        require(set(translations) == LOCALES, f"question {question['questionNo']} translation locales")
        require(all(translations[locale].get("reviewStatus") == "DRAFT" for locale in LOCALES), f"question {question['questionNo']} translation status")
        options = question.get("options", [])
        require([option.get("code") for option in options] == ["0", "1", "2", "3", "4"], f"question {question['questionNo']} options")
        require([option.get("score") for option in options] == [0, 1, 2, 3, 4], f"question {question['questionNo']} scores")
        for option in options:
            require(set(option.get("translations", {})) == LOCALES, f"question {question['questionNo']} option translation matrix")

    for dimension in dimensions:
        expected = set(dimension.get("questionNos", []))
        actual = {question["questionNo"] for question in questions if question["dimensionCode"] == dimension["dimensionCode"]}
        require(expected == actual, f"dimension {dimension['dimensionCode']} question mapping")

    golden_cases = package.get("goldenCases", [])
    require(len(golden_cases) >= 4, "golden case count")
    case_codes = {case.get("caseCode") for case in golden_cases}
    require({"SCL90_ALL_ZERO", "SCL90_ALL_FOUR", "SCL90_SELF_HARM_SIGNAL", "SCL90_MISSING_REQUIRED"} <= case_codes, "required golden cases")
    for case in golden_cases:
        expected = case.get("expected", {})
        require("valid" in expected, f"golden case {case.get('caseCode')} validity")
        if expected.get("valid"):
            require("totalScore" in expected and "riskLevel" in expected, f"golden case {case.get('caseCode')} expected score")
        else:
            require(bool(expected.get("errorCode")), f"golden case {case.get('caseCode')} error code")

    blockers = package.get("publicationBlockers", [])
    required_blockers = {
        "COPYRIGHT_AUTHORIZATION_PENDING",
        "PROFESSIONAL_REVIEW_PENDING",
        "POPULATION_SPECIFIC_NORMS_PENDING",
        "GLOBAL_RESULT_BANDS_PENDING",
    }
    require(required_blockers <= set(blockers), "external publication blockers")

    high_risk_rules = package.get("highRiskRules", [])
    require(len(high_risk_rules) >= 2, "high-risk rule count")
    for rule in high_risk_rules:
        require(rule.get("questionNo") in {15, 63}, f"high-risk question {rule.get('questionNo')}")
        require(set(rule.get("translations", {})) == LOCALES, f"high-risk rule {rule.get('ruleCode')} translation matrix")
        require(all(rule["translations"][locale].get("reviewStatus") == "DRAFT" for locale in LOCALES), f"high-risk rule {rule.get('ruleCode')} translations must remain draft")

    print("SCL90 source package valid: 90 questions, 10 dimensions, 3 locales, 4+ Golden Cases")
    print("Publication remains blocked: " + ", ".join(blockers))


if __name__ == "__main__":
    try:
        main()
    except json.JSONDecodeError as error:
        fail(f"invalid JSON: {error}")
