#!/usr/bin/env python3
"""Validate the source-independent generic score-method contract."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "doc/scale-packages/generic-score-method-registry.json"
SUPPORTED_METHODS = {
    "SIMPLE_SUM": "SUM",
    "REVERSE_SUM": "SUM",
    "WEIGHTED_SUM": "SUM",
    "AVERAGE": "AVERAGE_OVER_ANSWERED_ITEMS",
    "WEIGHTED_AVERAGE": "WEIGHTED_AVERAGE_OVER_ANSWERED_WEIGHT",
}
REQUIRED_MISSING_POLICIES = {"REJECT", "ALLOW", "PRORATE"}
EXPECTED_MISSING_SEMANTICS = {
    "SIMPLE_SUM": {
        "REJECT": "REJECT_SUBMISSION_WITHOUT_RESULT",
        "ALLOW": "SUM_ANSWERED_VALUES",
        "PRORATE": "SCALE_SUM_TO_FULL_QUESTION_COUNT",
    },
    "REVERSE_SUM": {
        "REJECT": "REJECT_SUBMISSION_WITHOUT_RESULT",
        "ALLOW": "SUM_ANSWERED_VALUES_AFTER_REVERSE_RECODE",
        "PRORATE": "SCALE_SUM_TO_FULL_QUESTION_COUNT_AFTER_REVERSE_RECODE",
    },
    "WEIGHTED_SUM": {
        "REJECT": "REJECT_SUBMISSION_WITHOUT_RESULT",
        "ALLOW": "WEIGHTED_SUM_ANSWERED_VALUES",
        "PRORATE": "SCALE_WEIGHTED_SUM_TO_FULL_WEIGHT_TOTAL",
    },
    "AVERAGE": {
        "REJECT": "REJECT_SUBMISSION_WITHOUT_RESULT",
        "ALLOW": "AVERAGE_OVER_ANSWERED_ITEMS",
        "PRORATE": "AVERAGE_OVER_ANSWERED_ITEMS_NO_ADDITIONAL_FACTOR",
    },
    "WEIGHTED_AVERAGE": {
        "REJECT": "REJECT_SUBMISSION_WITHOUT_RESULT",
        "ALLOW": "WEIGHTED_AVERAGE_OVER_ANSWERED_WEIGHT",
        "PRORATE": "WEIGHTED_AVERAGE_OVER_ANSWERED_WEIGHT_NO_ADDITIONAL_FACTOR",
    },
}


class MethodRegistryError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise MethodRegistryError(message)


def resolve_repo_file(raw: Any, label: str) -> Path:
    require(isinstance(raw, str) and raw.strip(), f"{label} is required")
    candidate = (ROOT / raw).resolve()
    try:
        candidate.relative_to(ROOT)
    except ValueError as error:
        raise MethodRegistryError(f"{label} escapes repository root: {raw}") from error
    require(candidate.is_file(), f"{label} is missing: {raw}")
    return candidate


def load(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"method registry is missing: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise MethodRegistryError(f"method registry is not valid JSON: {error}") from error
    require(isinstance(value, dict), "method registry root must be an object")
    return value


def validate(path: Path = DEFAULT_REGISTRY) -> dict[str, Any]:
    registry = load(path.resolve())
    require(registry.get("format") == "PSY_GENERIC_SCORE_METHOD_REGISTRY", "format mismatch")
    require(registry.get("schemaVersion") == 1, "schemaVersion mismatch")
    algorithm = registry.get("algorithm")
    require(
        algorithm == {
            "code": "GENERIC_SCORE_CALCULATOR",
            "version": "1",
            "implementationType": "BUILTIN",
        },
        "algorithm must be GENERIC_SCORE_CALCULATOR:1 BUILTIN",
    )
    methods = registry.get("methods")
    require(isinstance(methods, list) and methods, "methods must be a non-empty list")
    codes = [item.get("methodCode") for item in methods if isinstance(item, dict)]
    require(len(codes) == len(methods), "each method must be an object")
    require(set(codes) == set(SUPPORTED_METHODS), "methods must exactly match the five calculator methods")
    require(len(codes) == len(set(codes)), "methodCode values must be unique")
    for index, method in enumerate(methods):
        prefix = f"methods[{index}]"
        code = method["methodCode"]
        require(method.get("aggregation") == SUPPORTED_METHODS[code], f"{prefix}.aggregation is invalid")
        require(method.get("effectiveScore"), f"{prefix}.effectiveScore is required")
        require(method.get("coefficientAppliedAfterAggregation") is True, f"{prefix}.coefficientAppliedAfterAggregation must be true")
        require(method.get("weightsApplied") == (code in {"WEIGHTED_SUM", "WEIGHTED_AVERAGE"}), f"{prefix}.weightsApplied is invalid")
        missing = method.get("missingAnswerPolicies")
        require(isinstance(missing, list) and set(missing) == REQUIRED_MISSING_POLICIES, f"{prefix}.missingAnswerPolicies must cover REJECT, ALLOW and PRORATE")
        semantics = method.get("missingAnswerSemantics")
        require(
            semantics == EXPECTED_MISSING_SEMANTICS[code],
            f"{prefix}.missingAnswerSemantics does not match the calculator contract",
        )
        require(isinstance(method.get("fixtureScaleCode"), str) and method["fixtureScaleCode"].strip(), f"{prefix}.fixtureScaleCode is required")

    evidence = registry.get("technicalEvidence")
    require(isinstance(evidence, dict), "technicalEvidence is required")
    evidence_paths = {
        field: resolve_repo_file(evidence.get(field), f"technicalEvidence.{field}")
        for field in (
            "playwrightSpec",
            "postgresEvidenceScript",
            "qualityPolicyPlaywrightSpec",
            "qualityPolicyPostgresEvidenceScript",
            "kotlinUnitTest",
        )
    }
    playwright_text = evidence_paths["playwrightSpec"].read_text(encoding="utf-8")
    postgres_text = evidence_paths["postgresEvidenceScript"].read_text(encoding="utf-8")
    quality_playwright_text = evidence_paths["qualityPolicyPlaywrightSpec"].read_text(encoding="utf-8")
    quality_postgres_text = evidence_paths["qualityPolicyPostgresEvidenceScript"].read_text(encoding="utf-8")
    kotlin_text = evidence_paths["kotlinUnitTest"].read_text(encoding="utf-8")
    require(
        all(policy in quality_playwright_text for policy in ("REJECT", "ALLOW", "PRORATE")),
        "quality-policy Playwright matrix must exercise REJECT, ALLOW and PRORATE",
    )
    require("for (const methodCase of METHODS)" in quality_playwright_text, "quality-policy Playwright matrix must iterate every declared method")
    require("makeQualityPolicyPackage(methodCase.method, policy)" in quality_playwright_text, "quality-policy Playwright matrix must build each method/policy fixture")
    require("QUALITY_POLICY_MATRIX_CHECK|all_methods_policies|PASS" in quality_postgres_text, "quality-policy PostgreSQL matrix must emit the all_methods_policies marker")
    require("QUALITY_POLICY_MATRIX_CHECK|method_%_policy_%|PASS" in quality_postgres_text, "quality-policy PostgreSQL matrix must emit per-method/policy markers")
    require("QUALITY_POLICY_MATRIX_CHECK|policy_REJECT|PASS" in quality_postgres_text, "quality-policy PostgreSQL matrix must emit the REJECT marker")
    require("QUALITY_POLICY_MATRIX_CHECK|all_policies|PASS" in quality_postgres_text, "quality-policy PostgreSQL matrix must emit the all_policies marker")
    require("average methods use answered items for ALLOW and PRORATE" in kotlin_text, "Kotlin evidence must cover average missing-answer semantics")
    require("weighted average uses answered weight for ALLOW and PRORATE" in kotlin_text, "Kotlin evidence must cover weighted-average missing-answer semantics")
    for method in methods:
        code = method["methodCode"]
        fixture = method["fixtureScaleCode"]
        require(code in playwright_text, f"technical Playwright matrix does not declare {code}")
        require(code in postgres_text and fixture in postgres_text, f"technical PostgreSQL matrix does not assert {code}/{fixture}")
    return registry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registry", nargs="?", type=Path, default=DEFAULT_REGISTRY)
    args = parser.parse_args()
    try:
        registry = validate(args.registry if args.registry.is_absolute() else ROOT / args.registry)
    except (OSError, MethodRegistryError) as error:
        raise SystemExit(f"GENERIC_SCORE_METHOD_REGISTRY_INVALID: {error}") from error
    print(f"Generic score method registry valid: {len(registry['methods'])} methods")
    for method in registry["methods"]:
        print(f"- {method['methodCode']}: {method['aggregation']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
