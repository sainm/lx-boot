#!/usr/bin/env python3
"""Validate WHO-5 source-package structure, not clinical approval."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "doc/scale-packages/who5-v1-source-draft.json"
LOCALES = {"zh-CN", "ja-JP", "en"}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"WHO5_SOURCE_PACKAGE_INVALID: {message}")


def main() -> None:
    package = json.loads(PACKAGE.read_text(encoding="utf-8"))
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", "format")
    require(package.get("schemaVersion") == 1, "schemaVersion")
    scale = package["scale"]
    require(scale["scaleCode"] == "WHO5_WELL_BEING", "scale code")
    require(scale["versionNo"] == "who-2024-open-access-v1", "version")
    require(scale["scoreMethod"] == "SIMPLE_SUM", "score method")
    require(scale["scoreCoefficient"] == 1, "coefficient")
    require(scale["responseScale"] ["min"] == 0 and scale["responseScale"]["max"] == 5, "response scale")
    require(scale["algorithmBinding"] == {"algorithmCode": "GENERIC_SCORE_CALCULATOR", "algorithmVersion": "1", "implementationType": "BUILTIN"}, "generic binding")
    require(set(package["translations"]) == LOCALES, "scale locales")
    require(all(package["translations"][locale].get("nonDiagnosticText") for locale in LOCALES), "non-diagnostic texts")
    require(len(package["dimensions"]) == 1 and package["dimensions"][0]["dimensionCode"] == "WHO5_TOTAL", "dimension")
    questions = package["questions"]
    require(len(questions) == 5 and [question["questionNo"] for question in questions] == list(range(1, 6)), "question set")
    for question in questions:
        require(question["dimensionCode"] == "WHO5_TOTAL" and question["reverseScore"] is False, f"question {question['questionNo']} definition")
        require(set(question["translations"]) == LOCALES, f"question {question['questionNo']} locales")
        require([option["code"] for option in question["options"]] == ["0", "1", "2", "3", "4", "5"], f"question {question['questionNo']} option codes")
        require([option["score"] for option in question["options"]] == list(range(6)), f"question {question['questionNo']} option scores")
        require(all(set(option["translations"]) == LOCALES for option in question["options"]), f"question {question['questionNo']} option locales")
    require(package["scoring"]["indices"] == {"WHO5_PERCENTAGE_SCORE": "raw total score multiplied by 4 (0-100)"}, "declared percentage metric")
    require(package["scoring"]["dimensionAggregation"] == "SIMPLE_SUM", "dimension aggregation")
    require([(rule["scoreMin"], rule["scoreMax"]) for rule in package["resultRules"]] == [(0, 12), (13, 25)], "result bands")
    require(all(set(rule["translations"]) == LOCALES for rule in package["resultRules"]), "result translation matrix")
    cases = {case["caseCode"]: case for case in package["goldenCases"]}
    required = {"WHO5_ALL_ZERO", "WHO5_BOUNDARY_12", "WHO5_CUTOFF_13", "WHO5_ALL_HIGH", "WHO5_MISSING_REQUIRED", "WHO5_INVALID_OPTION"}
    require(required <= set(cases), "Golden Case set")
    require(cases["WHO5_BOUNDARY_12"]["expected"]["totalScore"] == 12, "boundary 12")
    require(cases["WHO5_CUTOFF_13"]["expected"]["totalScore"] == 13, "boundary 13")
    require(package["governance"]["authorizationType"] == "CC-BY-NC-SA-3.0-IGO", "license scope")
    require(package["governance"]["authorizationStatus"] == "AUTHORIZED", "authorization evidence state")
    require(len(package["sourceReferences"]) >= 4, "source references")
    require("PROFESSIONAL_REVIEW_PENDING" in package["publicationBlockers"], "professional blocker")
    require("LICENSE_SCOPE_REVIEW_PENDING" in package["publicationBlockers"], "license blocker")
    print("WHO5 source package valid: 5 questions, 1 dimension, 3 locales, generic percentage metric, 6 Golden Cases")
    print("Publication remains pending: " + ", ".join(package["publicationBlockers"]))


if __name__ == "__main__":
    main()
