#!/usr/bin/env python3
"""Structural validator for the official-use K6 package.

This proves source-package integrity only. It does not claim that the local
organisation has completed professional review or publication approval.
"""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "doc" / "scale-packages" / "k6-v1-source-official-draft.json"
LOCALES = {"zh-CN", "ja-JP", "en"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"K6_SOURCE_PACKAGE_INVALID: {message}")


def main() -> None:
    package = json.loads(PACKAGE.read_text(encoding="utf-8"))
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", "format")
    require(package.get("schemaVersion") == 1, "schema version")
    scale = package["scale"]
    require(scale["scaleCode"] == "K6_OFFICIAL_FREE_USE", "scale code")
    require(scale["algorithmBinding"] == {"algorithmCode": "GENERIC_SCORE_CALCULATOR", "algorithmVersion": "1", "implementationType": "BUILTIN"}, "generic binding")
    require(scale["responseScale"]["min"] == 0 and scale["responseScale"]["max"] == 4, "response range")
    require(set(package["translations"]) == LOCALES, "scale locale matrix")
    require(all(package["translations"][locale].get("nonDiagnosticText") for locale in LOCALES), "localized non-diagnostic statements")
    require(len({package["translations"][locale]["nonDiagnosticText"] for locale in LOCALES}) == 3, "non-diagnostic statements must be localized")
    require(len(package["dimensions"]) == 1 and package["dimensions"][0]["dimensionCode"] == "K6_TOTAL", "dimension")
    questions = package["questions"]
    require(len(questions) == 6 and [question["questionNo"] for question in questions] == list(range(1, 7)), "question set")
    for question in questions:
        require(question["reverseScore"] is True, f"question {question['questionNo']} must recode official response order")
        require(set(question["translations"]) == LOCALES, f"question {question['questionNo']} locales")
        require([option["code"] for option in question["options"]] == ["1", "2", "3", "4", "5"], f"question {question['questionNo']} response order")
        require([option["score"] for option in question["options"]] == [0, 1, 2, 3, 4], f"question {question['questionNo']} raw scores")
    rules = package["resultRules"]
    require([(rule["scoreMin"], rule["scoreMax"]) for rule in rules] == [(0, 12), (13, 24)], "result bands")
    require(all(set(rule["translations"]) == LOCALES for rule in rules), "result translation matrix")
    cases = {case["caseCode"]: case for case in package["goldenCases"]}
    require({"K6_ALL_NONE", "K6_CUTOFF_13", "K6_REVERSE_RECODE", "K6_MISSING_REQUIRED", "K6_INVALID_OPTION"} <= set(cases), "golden case set")
    require(cases["K6_CUTOFF_13"]["expected"]["totalScore"] == 13, "cutoff golden case")
    require(package["governance"]["authorizationStatus"] == "NOT_REQUIRED", "official free-use authorization status")
    require(package["governance"]["copyrightStatus"] == "AUTHORIZED", "rights evidence status")
    require(len(package["sourceReferences"]) >= 5, "official reference set")
    print("K6 source package valid: 6 questions, 1 dimension, 3 locales, generic score, 2 result bands, Golden Cases")
    print("Publication remains pending: " + ", ".join(package["publicationBlockers"]))


if __name__ == "__main__":
    main()
