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
SCL90_QUALITY_POLICY = {
    "missingAnswerPolicy": "REJECT",
    "maxMissingRatio": 0,
    "invalidResultAction": "INVALIDATE",
    "requireAllRequiredAnswers": True,
}
SCL90_INDICES = {
    "GSI": "sum(all answered item scores) / answered item count",
    "PST": "count(answered items with score > 0)",
    "PSDI": "sum(all answered item scores) / PST; 0 when PST is 0",
}


def fail(message: str) -> None:
    raise SystemExit(f"SCL90_SOURCE_PACKAGE_INVALID: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    package_path = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else PACKAGE
    if not package_path.exists():
        fail(f"missing {package_path}")
    package = json.loads(package_path.read_text(encoding="utf-8"))
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", "format")
    require(package.get("schemaVersion") == 1, "schemaVersion")
    scale = package.get("scale", {})
    scale_code = scale.get("scaleCode")
    require(scale_code in {"SCL90_USER_DRAFT", "SCL90_USER_AUTHORIZED"}, "scale code")
    require(scale.get("scaleName"), "scale name")
    require(scale.get("versionNo"), "version number")
    if scale_code == "SCL90_USER_AUTHORIZED":
        require(scale.get("versionNo") == "authorized-profile-v1", "authorized technical version")
    require(scale.get("assessmentMode") == "SELF", "assessment mode")
    require(scale.get("reportTemplate") == "NORMATIVE_PROFILE", "report template")
    require(scale.get("qualityPolicy") == SCL90_QUALITY_POLICY, "quality policy must reject missing required answers")
    require(scale.get("responseScale", {}).get("min") == 0, "response min")
    require(scale.get("responseScale", {}).get("max") == 4, "response max")
    require(len(scale.get("responseScale", {}).get("labels", [])) == 5, "response labels")
    require(scale.get("algorithmBinding", {}).get("algorithmCode") == "SCL90_PROFILE", "algorithm binding")
    scoring = package.get("scoring", {})
    require(scoring.get("canonicalConvention") == "0_TO_4", "canonical scoring convention")
    require(scoring.get("positiveSymptomRule") == "score > 0", "positive symptom rule")
    require(scoring.get("indices") == SCL90_INDICES, "SCL90 derived metric definitions")
    require(scoring.get("dimensionAggregation") == "AVERAGE", "dimension average aggregation")
    require(scoring.get("dimensionRule") == "sum(dimension item scores) / answered item count in dimension", "dimension scoring rule")

    translations = package.get("translations", {})
    require(set(translations) == LOCALES, "scale translation locales")
    require(all(translations[locale].get("nonDiagnosticText") for locale in LOCALES), "localized non-diagnostic statements")
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
    require(package.get("skipRules", []) == [], "SCL90_PROFILE technical closure does not support skipRules")
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
    require(len(golden_cases) >= 5, "golden case count")
    case_codes = {case.get("caseCode") for case in golden_cases}
    require({"SCL90_ALL_ZERO", "SCL90_ALL_FOUR", "SCL90_SELF_HARM_SIGNAL", "SCL90_MISSING_REQUIRED", "SCL90_INVALID_OPTION"} <= case_codes, "required golden cases")
    for case in golden_cases:
        expected = case.get("expected", {})
        require("valid" in expected, f"golden case {case.get('caseCode')} validity")
        if expected.get("valid"):
            require("totalScore" in expected and "riskLevel" in expected, f"golden case {case.get('caseCode')} expected score")
        else:
            require(bool(expected.get("errorCode")), f"golden case {case.get('caseCode')} error code")

    blockers = package.get("publicationBlockers", [])
    required_blockers = (
        {
            "AUTHORIZATION_SCOPE_ARCHIVE_PENDING",
            "PROFESSIONAL_REVIEW_PENDING",
            "TRILINGUAL_TRANSLATION_REVIEW_PENDING",
            "TRANSLATION_RIGHTS_AND_REVIEW_PENDING",
            "POPULATION_SPECIFIC_NORMS_PENDING",
            "CRISIS_RESPONSE_OWNER_AND_SLA_PENDING",
        }
        if scale_code == "SCL90_USER_AUTHORIZED"
        else {
            "COPYRIGHT_AUTHORIZATION_PENDING",
            "PROFESSIONAL_REVIEW_PENDING",
            "POPULATION_SPECIFIC_NORMS_PENDING",
            "GLOBAL_RESULT_BANDS_PENDING",
        }
    )
    require(required_blockers <= set(blockers), "external publication blockers")

    if scale_code == "SCL90_USER_AUTHORIZED":
        governance = package.get("governance", {})
        require(governance.get("copyrightStatus") == "AUTHORIZED", "authorized package copyright status")
        require(governance.get("authorizationStatus") == "AUTHORIZED", "authorized package authorization status")
        result_rules = package.get("resultRules", [])
        require(len(result_rules) == 1, "authorized package profile-only result rule")
        rule = result_rules[0]
        require(
            rule.get("ruleCode") == "SCL90_PROFILE_ONLY"
            and rule.get("dimensionCode") is None
            and rule.get("riskLevel") == "NORMAL"
            and rule.get("scoreMin") == 0
            and rule.get("scoreMax") == 360
            and rule.get("scoreSource") == "RAW_SCORE",
            "authorized package profile-only result rule shape",
        )
        require(set(rule.get("translations", {})) == LOCALES, "authorized result translation locales")
        require(
            all(
                translation.get("resultTitle")
                and translation.get("resultDescription")
                and translation.get("suggestionText")
                for translation in rule.get("translations", {}).values()
            ),
            "authorized result translation content",
        )

    high_risk_rules = package.get("highRiskRules", [])
    require(len(high_risk_rules) >= 2, "high-risk rule count")
    for rule in high_risk_rules:
        require(rule.get("questionNo") in {15, 63}, f"high-risk question {rule.get('questionNo')}")
        require(set(rule.get("translations", {})) == LOCALES, f"high-risk rule {rule.get('ruleCode')} translation matrix")
        require(all(rule["translations"][locale].get("reviewStatus") == "DRAFT" for locale in LOCALES), f"high-risk rule {rule.get('ruleCode')} translations must remain draft")

    print("SCL90 source package valid: 90 questions, 10 dimensions, 3 locales, 5+ Golden Cases")
    print("Publication remains blocked: " + ", ".join(blockers))


if __name__ == "__main__":
    try:
        main()
    except json.JSONDecodeError as error:
        fail(f"invalid JSON: {error}")
