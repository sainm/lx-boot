#!/usr/bin/env python3
"""Validate the non-source candidate capability catalog.

This catalog is intentionally not a scale registry.  It is a machine-readable
map of reusable technical profiles and intake prerequisites; it must never
contain copied questions or turn an INPUT_PENDING candidate into support.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CATALOG = ROOT / "doc" / "scale-packages" / "scale-capability-catalog.json"
PROFILE_STATUSES = {"TECHNICALLY_AVAILABLE", "TECHNICAL_FIXTURE_ONLY", "UNSUPPORTED"}
CANDIDATE_STATUSES = {"INPUT_PENDING", "PARTIALLY_SUPPORTED", "UNSUPPORTED"}
SUPPORTED_TEMPLATES = {"SINGLE_SCORE", "DIMENSION_PROFILE", "NORMATIVE_PROFILE", "RISK_TRIAGE", None}
SUPPORTED_QUESTION_TYPES = {"SINGLE_CHOICE", "MULTI_SELECT", "MATRIX", "TEXT_WITH_OPTION", "TEXT", "TIME", "SLIDER"}
SUPPORTED_ALGORITHMS = {
    "SIMPLE_SUM",
    "REVERSE_SUM",
    "WEIGHTED_SUM",
    "AVERAGE",
    "WEIGHTED_AVERAGE",
    "SCL90_PROFILE",
}
SUPPORTED_RECODES = {"RECODE_SUM_TO_0_3", "SLEEP_DURATION_RECODE_0_3", "SLEEP_EFFICIENCY_RECODE_0_3"}
SUPPORTED_EVIDENCE_CLASSES = {
    "SHARED_ENGINE_CONTRACT",
    "SYNTHETIC_TECHNICAL_FIXTURE",
    "RESTRICTED_PROFILE_CONTRACT",
    "UNSUPPORTED_BOUNDARY",
}
TECHNICAL_CHECKS = {"question_display", "question_flow", "scoring_trace", "result_interpretation", "trilingual_ui", "report_semantics", "task_version_lock", "historical_compatibility"}
FORBIDDEN_SOURCE_FIELDS = {
    "questions",
    "translations",
    "goldenCases",
    "resultRules",
    "highRiskRules",
    "norms",
    "thresholds",
}


class CatalogError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CatalogError(message)


def load(path: Path) -> dict[str, Any]:
    require(path.exists() and path.is_file(), f"catalog is missing: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise CatalogError(f"catalog is not valid JSON: {error}") from error
    require(isinstance(value, dict), "catalog must be a JSON object")
    return value


def validate(path: Path) -> dict[str, Any]:
    catalog = load(path)
    require(catalog.get("format") == "PSY_SCALE_CAPABILITY_CATALOG", "format mismatch")
    require(catalog.get("schemaVersion") == 1, "schemaVersion must be 1")
    require(catalog.get("supportClaim") == "NONE", "catalog must declare supportClaim=NONE")
    boundary = catalog.get("technicalBoundary")
    require(isinstance(boundary, dict), "technicalBoundary is required")
    require(boundary.get("sourceContent") == "EXCLUDED", "technicalBoundary.sourceContent must be EXCLUDED")
    require(boundary.get("syntheticFixtures") == "ALLOWED", "technicalBoundary.syntheticFixtures must be ALLOWED")
    require(boundary.get("formalSupport") == "NOT_CLAIMED", "technicalBoundary.formalSupport must be NOT_CLAIMED")
    require(boundary.get("requiredVersionedScalePackageForExecution") is True, "technicalBoundary must require a versioned ScalePackage")
    require(boundary.get("android") == "OUT_OF_SCOPE" and boundary.get("ci") == "OUT_OF_SCOPE", "Android and CI must remain out of scope")
    profiles = catalog.get("profiles")
    candidates = catalog.get("candidates")
    require(isinstance(profiles, list) and profiles, "profiles must be a non-empty list")
    require(isinstance(candidates, list) and candidates, "candidates must be a non-empty list")

    profile_map: dict[str, dict[str, Any]] = {}
    for index, profile in enumerate(profiles):
        require(isinstance(profile, dict), f"profiles[{index}] must be an object")
        code = profile.get("profileCode")
        require(isinstance(code, str) and code.strip(), f"profiles[{index}].profileCode is required")
        require(code not in profile_map, f"duplicate profileCode: {code}")
        status = profile.get("status")
        require(status in PROFILE_STATUSES, f"profiles[{index}].status is invalid")
        for field in ("questionTypes", "algorithmCandidates", "reportTemplates", "recodeCandidates", "sourceVerificationRequired"):
            value = profile.get(field)
            require(isinstance(value, list) and len(value) == len(set(value)), f"profiles[{index}].{field} must be a unique list")
            require(all(isinstance(item, str) and item.strip() for item in value), f"profiles[{index}].{field} contains an invalid value")
        require(all(template in SUPPORTED_TEMPLATES for template in profile["reportTemplates"]), f"profiles[{index}].reportTemplates contains an unsupported template")
        require(all(item in SUPPORTED_QUESTION_TYPES for item in profile["questionTypes"]), f"profiles[{index}].questionTypes contains an unsupported type")
        require(all(item in SUPPORTED_ALGORITHMS for item in profile["algorithmCandidates"]), f"profiles[{index}].algorithmCandidates contains an unsupported algorithm")
        require(all(item in SUPPORTED_RECODES for item in profile["recodeCandidates"]), f"profiles[{index}].recodeCandidates contains an unsupported recode")
        require(profile.get("evidenceClass") in SUPPORTED_EVIDENCE_CLASSES, f"profiles[{index}].evidenceClass is required")
        checks = profile.get("technicalChecks")
        require(isinstance(checks, list) and len(checks) == len(set(checks)), f"profiles[{index}].technicalChecks must be a unique list")
        require(all(check in TECHNICAL_CHECKS for check in checks), f"profiles[{index}].technicalChecks contains an unsupported check")
        if status == "UNSUPPORTED":
            require(profile["evidenceClass"] == "UNSUPPORTED_BOUNDARY", f"profiles[{index}] unsupported profile must use UNSUPPORTED_BOUNDARY")
            require(not profile["questionTypes"] and not profile["algorithmCandidates"] and not profile["reportTemplates"] and not profile["recodeCandidates"] and not checks, f"profiles[{index}] unsupported profile cannot expose executable capabilities")
        else:
            require(profile["evidenceClass"] != "UNSUPPORTED_BOUNDARY", f"profiles[{index}] executable profile cannot use UNSUPPORTED_BOUNDARY")
            require(set(TECHNICAL_CHECKS).issubset(checks), f"profiles[{index}] must declare all generic technical checks")
        profile_map[code] = profile

    task_ids: set[str] = set()
    scale_codes: set[str] = set()
    for index, candidate in enumerate(candidates):
        require(isinstance(candidate, dict), f"candidates[{index}] must be an object")
        task_id = candidate.get("taskId")
        scale_code = candidate.get("scaleCode")
        require(isinstance(task_id, str) and task_id.strip(), f"candidates[{index}].taskId is required")
        require(isinstance(scale_code, str) and scale_code.strip(), f"candidates[{index}].scaleCode is required")
        require(task_id not in task_ids, f"duplicate taskId: {task_id}")
        require(scale_code not in scale_codes, f"duplicate scaleCode: {scale_code}")
        task_ids.add(task_id)
        scale_codes.add(scale_code)

        status = candidate.get("status")
        require(status in CANDIDATE_STATUSES, f"candidates[{index}].status is invalid")
        profile_code = candidate.get("expectedProfile")
        require(profile_code in profile_map, f"candidates[{index}].expectedProfile is unknown: {profile_code}")
        profile = profile_map[profile_code]
        if status == "UNSUPPORTED":
            require(profile["status"] == "UNSUPPORTED", f"candidates[{index}] unsupported candidate must use an unsupported profile")
            require(candidate.get("expectedReportTemplate") is None, f"candidates[{index}] unsupported candidate cannot declare a report template")
        else:
            require(profile["status"] != "UNSUPPORTED", f"candidates[{index}] requires an unsupported profile")
            require(candidate.get("expectedReportTemplate") in SUPPORTED_TEMPLATES - {None}, f"candidates[{index}].expectedReportTemplate is invalid")

        require("sourcePackage" in candidate and candidate.get("sourcePackage") is None, f"candidates[{index}] must explicitly have sourcePackage=null")
        require(candidate.get("formalSupport") is False, f"candidates[{index}] must declare formalSupport=false")
        require(candidate.get("nextAction") in {"CONTROLLED_SOURCE_PACKAGE_REQUIRED", "DO_NOT_AUTO_IMPORT"}, f"candidates[{index}].nextAction is invalid")
        if status == "UNSUPPORTED":
            require(candidate["nextAction"] == "DO_NOT_AUTO_IMPORT", f"candidates[{index}] unsupported candidate must not be auto-imported")
        else:
            require(candidate["nextAction"] == "CONTROLLED_SOURCE_PACKAGE_REQUIRED", f"candidates[{index}] requires a controlled source package")
        expected_template = candidate.get("expectedReportTemplate")
        if status == "UNSUPPORTED":
            require(expected_template is None, f"candidates[{index}] unsupported candidate must not declare a report template")
        else:
            require(expected_template in profile["reportTemplates"], f"candidates[{index}] expectedReportTemplate is not exposed by expectedProfile")
        for field in ("declaredTechnicalNeedsToVerify", "requiredInputs"):
            value = candidate.get(field)
            require(isinstance(value, list) and value and len(value) == len(set(value)), f"candidates[{index}].{field} must be a non-empty unique list")
            require(all(isinstance(item, str) and item.strip() for item in value), f"candidates[{index}].{field} contains an invalid value")
        for forbidden in FORBIDDEN_SOURCE_FIELDS:
            require(forbidden not in candidate, f"candidates[{index}] contains forbidden source field: {forbidden}")

    return {"profiles": len(profiles), "candidates": len(candidates)}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("catalog", nargs="?", type=Path, default=DEFAULT_CATALOG)
    args = parser.parse_args()
    try:
        result = validate(args.catalog.resolve())
    except CatalogError as error:
        parser.error(str(error))
    print(f"Scale capability catalog valid: {result['profiles']} profiles, {result['candidates']} candidates; formal support claims=0")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
