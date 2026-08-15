#!/usr/bin/env python3
"""Validate the source-independent generic dimension-recode registry."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "doc/scale-packages/generic-recode-method-registry.json"
EXPECTED = {
    "RECODE_SUM_TO_0_3": {"requiredReferences": []},
    "SLEEP_DURATION_RECODE_0_3": {"requiredReferences": ["startQuestionNo", "endQuestionNo"]},
    "SLEEP_EFFICIENCY_RECODE_0_3": {
        "requiredReferences": ["startQuestionNo", "endQuestionNo", "sleepQuestionNo"]
    },
}
EXPECTED_QUESTION_TYPES = [
    "SINGLE_CHOICE",
    "MULTI_SELECT",
    "MATRIX",
    "TEXT_WITH_OPTION",
    "TEXT",
    "TIME",
    "SLIDER",
]


class RecodeRegistryError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RecodeRegistryError(message)


def repo_file(raw: Any, label: str) -> Path:
    require(isinstance(raw, str) and raw.strip(), f"{label} is required")
    path = (ROOT / raw).resolve()
    try:
        path.relative_to(ROOT)
    except ValueError as error:
        raise RecodeRegistryError(f"{label} escapes repository root") from error
    require(path.is_file(), f"{label} is missing: {raw}")
    return path


def validate(path: Path) -> dict[str, Any]:
    try:
        registry = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RecodeRegistryError(f"cannot read registry: {error}") from error
    require(isinstance(registry, dict), "registry root must be an object")
    require(registry.get("format") == "PSY_GENERIC_RECODE_METHOD_REGISTRY", "format mismatch")
    require(registry.get("schemaVersion") == 1, "schemaVersion mismatch")
    require(registry.get("algorithm") == {
        "code": "GENERIC_SCORE_CALCULATOR",
        "version": "1",
        "implementationType": "BUILTIN",
    }, "algorithm binding mismatch")
    require(registry.get("android") == "EXCLUDED", "Android must remain EXCLUDED")
    require(registry.get("questionTypeCoverage") == EXPECTED_QUESTION_TYPES, "questionTypeCoverage must use the canonical synthetic fixture order")
    boundary = str(registry.get("governanceBoundary", ""))
    for phrase in ("no original instrument", "authorization", "professional", "business acceptance", "does not establish"):
        require(phrase.lower() in boundary.lower(), f"governance boundary is missing {phrase!r}")

    rules = registry.get("rules")
    require(isinstance(rules, list) and len(rules) == len(EXPECTED), "rules must contain exactly the three whitelisted rules")
    codes = [rule.get("ruleCode") for rule in rules if isinstance(rule, dict)]
    require(codes == list(EXPECTED), "rules must be declared in canonical order")
    for index, rule in enumerate(rules):
        require(isinstance(rule, dict), f"rules[{index}] must be an object")
        code = rule.get("ruleCode")
        require(code in EXPECTED, f"rules[{index}].ruleCode is not whitelisted")
        require(isinstance(rule.get("input"), str) and rule["input"].strip(), f"{code}.input is required")
        require(isinstance(rule.get("operation"), str) and rule["operation"].strip(), f"{code}.operation is required")
        require(rule.get("requiredReferences") == EXPECTED[code]["requiredReferences"], f"{code}.requiredReferences mismatch")
        require(rule.get("fixtureScaleCode") == "E2E_RECODE_MATRIX", f"{code}.fixtureScaleCode mismatch")
        require(isinstance(rule.get("fixtureCaseCode"), str) and rule["fixtureCaseCode"].strip(), f"{code}.fixtureCaseCode is required")

    evidence = registry.get("technicalEvidence")
    require(isinstance(evidence, dict), "technicalEvidence is required")
    paths = {field: repo_file(evidence.get(field), f"technicalEvidence.{field}") for field in (
        "playwrightSpec", "postgresEvidenceScript", "kotlinUnitTest", "sourceValidation", "runtimeImplementation"
    )}
    playwright = paths["playwrightSpec"].read_text(encoding="utf-8")
    postgres = paths["postgresEvidenceScript"].read_text(encoding="utf-8")
    kotlin = paths["kotlinUnitTest"].read_text(encoding="utf-8")
    validation = paths["sourceValidation"].read_text(encoding="utf-8")
    implementation = paths["runtimeImplementation"].read_text(encoding="utf-8")
    for code in EXPECTED:
        require(code in playwright, f"Playwright evidence does not exercise {code}")
        require(code in postgres, f"PostgreSQL evidence does not assert {code}")
        require(code in kotlin, f"Kotlin evidence does not cover {code}")
        require(code in validation, f"source validation does not whitelist {code}")
        require(code in implementation, f"runtime implementation does not contain {code}")
    require("RECODE_MATRIX_CHECK|all_recode_rules|PASS" in postgres, "aggregate PostgreSQL marker is missing")
    require("RECODE_RUNTIME_CHECK|all_recode_rules|PASS" in playwright, "aggregate Playwright marker is missing")
    require("RECODE_MATRIX_CHECK|question_types|PASS" in postgres, "question-type PostgreSQL marker is missing")
    require("RECODE_RUNTIME_CHECK|question_types|PASS" in playwright, "question-type Playwright marker is missing")
    require("TIME" in playwright and "answerText" in playwright and "answerValue" in playwright, "TIME and numeric answer paths are not exercised")
    return registry


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registry", nargs="?", type=Path, default=DEFAULT_REGISTRY)
    args = parser.parse_args()
    try:
        registry = validate(args.registry if args.registry.is_absolute() else ROOT / args.registry)
    except (OSError, RecodeRegistryError) as error:
        raise SystemExit(f"GENERIC_RECODE_METHOD_REGISTRY_INVALID: {error}") from error
    print(f"Generic recode method registry valid: {len(registry['rules'])} rules")
    for rule in registry["rules"]:
        print(f"- {rule['ruleCode']}: {rule['operation']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
