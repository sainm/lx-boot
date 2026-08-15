#!/usr/bin/env python3
"""Validate the machine-readable scale regression registry.

The registry is an execution input, not a clinical approval record.  This
validator deliberately checks hashes and package metadata so a regression run
cannot silently use a different source artifact than the one recorded in the
task tracker.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path
from typing import Any

from validate_generic_score_method_registry import (
    MethodRegistryError,
    validate as validate_generic_score_method_registry,
)
from validate_generic_recode_method_registry import (
    RecodeRegistryError,
    validate as validate_generic_recode_method_registry,
)
from validate_scale_capability_catalog import (
    CatalogError,
    DEFAULT_CATALOG,
    validate as validate_scale_capability_catalog,
)


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "doc" / "scale-packages" / "scale-adaptation-registry.json"
REQUIRED_LOCALES = {"zh-CN", "ja-JP", "en"}
SUPPORTED_TEMPLATES = {
    "DEFAULT_SCREENING",
    "SINGLE_SCORE",
    "DIMENSION_PROFILE",
    "NORMATIVE_PROFILE",
    "RISK_TRIAGE",
}
SUPPORT_STATUSES = {
    "NOT_STARTED",
    "INPUT_PENDING",
    "IN_PROGRESS",
    "PARTIALLY_SUPPORTED",
    "TECHNICALLY_VERIFIED",
    "BLOCKED_EXTERNAL",
    "FORMALLY_APPROVED",
    "FULLY_SUPPORTED",
    "REGRESSION_FAILED",
    "UNSUPPORTED",
}
GOVERNANCE_STATUSES = {
    "DRAFT",
    "PENDING_REVIEW",
    "BLOCKED_EXTERNAL",
    "FORMALLY_APPROVED",
    "FULLY_SUPPORTED",
}
REGRESSION_STATUSES = {"NOT_RUN", "PASS", "PARTIAL", "FAIL"}
FORMAL_SUPPORT_STATUSES = {"FORMALLY_APPROVED", "FULLY_SUPPORTED"}
TECHNICAL_EVIDENCE_STATUSES = {"TECHNICALLY_VERIFIED", *FORMAL_SUPPORT_STATUSES}
SUPPORTED_REQUIRED_CHECKS = {
    "source_package_integrity",
    "question_display",
    "golden_case_scores",
    "scoring_trace",
    "question_set_path",
    "normative_semantics",
    "trilingual_result_content",
    "report_semantics",
    "task_version_lock",
    "historical_result_immutability",
    "idempotent_submission",
    "concurrent_submission",
    "rescore_history",
    "quality_outcome",
    "export_semantics",
    "security_boundaries",
    "security_audit",
}
SHA256 = re.compile(r"^[0-9a-f]{64}$")


class RegistryError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RegistryError(message)


def load_json(path: Path, label: str) -> dict[str, Any]:
    require(path.exists() and path.is_file(), f"{label} is missing: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise RegistryError(f"{label} is not valid JSON: {error}") from error
    require(isinstance(value, dict), f"{label} must be a JSON object")
    return value


def relative_source_path(raw_path: Any) -> Path:
    require(isinstance(raw_path, str) and raw_path.strip(), "sourcePackage must be a non-empty path")
    candidate = (ROOT / raw_path).resolve()
    try:
        candidate.relative_to(ROOT)
    except ValueError as error:
        raise RegistryError(f"sourcePackage escapes repository root: {raw_path}") from error
    return candidate


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_sha256(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def capability_profiles() -> dict[str, dict[str, Any]]:
    try:
        validate_scale_capability_catalog(DEFAULT_CATALOG)
    except CatalogError as error:
        raise RegistryError(f"scale capability catalog is invalid: {error}") from error
    catalog = load_json(DEFAULT_CATALOG, "scale capability catalog")
    profiles = catalog.get("profiles", [])
    return {profile["profileCode"]: profile for profile in profiles}


def validate_entry(
    entry: dict[str, Any],
    index: int,
    profile_map: dict[str, dict[str, Any]] | None = None,
) -> None:
    prefix = f"scales[{index}]"
    for field in ("taskId", "scaleCode", "versionNo", "reportTemplate", "supportStatus", "governanceStatus"):
        require(isinstance(entry.get(field), str) and entry[field].strip(), f"{prefix}.{field} is required")
    require(entry["reportTemplate"] in SUPPORTED_TEMPLATES, f"{prefix}.reportTemplate is unsupported")

    source = relative_source_path(entry.get("sourcePackage"))
    source_validator = relative_source_path(entry.get("sourceValidator"))
    require(source_validator.exists() and source_validator.is_file(), f"{prefix}.sourceValidator is missing")
    source_validator_args = entry.get("sourceValidatorArgs", [])
    require(
        isinstance(source_validator_args, list)
        and all(isinstance(value, str) and value.strip() for value in source_validator_args),
        f"{prefix}.sourceValidatorArgs must be a string list",
    )
    for argument in source_validator_args:
        if argument.startswith("-"):
            continue
        argument_path = relative_source_path(argument)
        require(argument_path.exists() and argument_path.is_file(), f"{prefix}.sourceValidatorArgs path is missing: {argument}")
    postgres_evidence = relative_source_path(entry.get("postgresEvidenceScript"))
    require(postgres_evidence.exists() and postgres_evidence.is_file(), f"{prefix}.postgresEvidenceScript is missing")
    recorded_hash = entry.get("sourceSha256")
    require(isinstance(recorded_hash, str) and SHA256.fullmatch(recorded_hash), f"{prefix}.sourceSha256 must be lowercase SHA-256")
    require(sha256(source) == recorded_hash, f"{prefix}.sourceSha256 does not match {entry['sourcePackage']}")

    package = load_json(source, f"{prefix}.sourcePackage")
    require(package.get("format") == "PSY_SCALE_SOURCE_PACKAGE", f"{prefix} source format mismatch")
    require(package.get("schemaVersion") == 1, f"{prefix} source schema mismatch")
    scale = package.get("scale")
    require(isinstance(scale, dict), f"{prefix}.source.scale must be an object")
    require(scale.get("scaleCode") == entry["scaleCode"], f"{prefix}.scaleCode does not match source package")
    require(scale.get("versionNo") == entry["versionNo"], f"{prefix}.versionNo does not match source package")
    require(scale.get("reportTemplate") == entry["reportTemplate"], f"{prefix}.reportTemplate does not match source package")

    algorithm = entry.get("algorithm")
    source_algorithm = scale.get("algorithmBinding")
    require(isinstance(algorithm, dict) and isinstance(source_algorithm, dict), f"{prefix}.algorithm is required")
    require(
        algorithm == {
            "code": source_algorithm.get("algorithmCode"),
            "version": source_algorithm.get("algorithmVersion"),
            "implementationType": source_algorithm.get("implementationType"),
        },
        f"{prefix}.algorithm does not match source package",
    )

    locales = entry.get("locales")
    require(isinstance(locales, list) and set(locales) == REQUIRED_LOCALES and len(locales) == len(set(locales)), f"{prefix}.locales must contain zh-CN, ja-JP and en once")
    require(set(package.get("translations", {})) == set(locales), f"{prefix}.locales do not match source translations")

    golden_codes = entry.get("goldenCases")
    source_cases = package.get("goldenCases")
    require(isinstance(golden_codes, list) and golden_codes and len(golden_codes) == len(set(golden_codes)), f"{prefix}.goldenCases must be a unique non-empty list")
    require(isinstance(source_cases, list), f"{prefix} source goldenCases must be a list")
    source_codes = [case.get("caseCode") for case in source_cases]
    require(len(source_codes) == len(set(source_codes)), f"{prefix} source Golden Case codes are duplicated")
    require(set(golden_codes) == set(source_codes), f"{prefix}.goldenCases do not match source package")

    expected = entry.get("expected")
    require(isinstance(expected, dict), f"{prefix}.expected is required")
    source_result_codes = [rule.get("ruleCode") for rule in package.get("resultRules", [])]
    source_metric_codes = list((package.get("scoring") or {}).get("indices", {}).keys())
    source_high_risk_codes = [rule.get("ruleCode") for rule in package.get("highRiskRules", [])]
    for field, source_values in (
        ("resultRuleCodes", source_result_codes),
        ("derivedMetricCodes", source_metric_codes),
        ("highRiskRuleCodes", source_high_risk_codes),
    ):
        recorded_values = expected.get(field)
        require(isinstance(recorded_values, list) and all(isinstance(value, str) and value.strip() for value in recorded_values), f"{prefix}.expected.{field} is invalid")
        require(sorted(recorded_values) == sorted(source_values), f"{prefix}.expected.{field} does not match source package")
    recorded_golden_hashes = expected.get("goldenCaseExpectationsSha256")
    require(isinstance(recorded_golden_hashes, dict), f"{prefix}.expected.goldenCaseExpectationsSha256 is required")
    require(set(recorded_golden_hashes) == set(source_codes), f"{prefix}.expected Golden Case hash set does not match source package")
    for case in source_cases:
        case_code = case.get("caseCode")
        recorded_case_hash = recorded_golden_hashes.get(case_code)
        require(isinstance(recorded_case_hash, str) and SHA256.fullmatch(recorded_case_hash), f"{prefix}.expected Golden Case hash is invalid for {case_code}")
        require(recorded_case_hash == canonical_sha256(case.get("expected")), f"{prefix}.expected Golden Case hash does not match {case_code}")

    require(entry["supportStatus"] in SUPPORT_STATUSES, f"{prefix}.supportStatus is invalid")
    require(entry["governanceStatus"] in GOVERNANCE_STATUSES, f"{prefix}.governanceStatus is invalid")
    support_is_formal = entry["supportStatus"] in FORMAL_SUPPORT_STATUSES
    governance_is_formal = entry["governanceStatus"] in FORMAL_SUPPORT_STATUSES
    require(
        support_is_formal == governance_is_formal,
        f"{prefix}.supportStatus/governanceStatus cannot claim formal support while the other state is external or draft",
    )
    require(isinstance(entry.get("runInTechnicalRegression"), bool), f"{prefix}.runInTechnicalRegression must be boolean")
    selectors = entry.get("runtimeEvidenceSelectors")
    require(isinstance(selectors, dict), f"{prefix}.runtimeEvidenceSelectors is required")
    for field in ("playwrightSpec", "playwrightTitle"):
        require(isinstance(selectors.get(field), str) and selectors[field].strip(), f"{prefix}.runtimeEvidenceSelectors.{field} is required")
    playwright_spec = relative_source_path(selectors["playwrightSpec"])
    require(playwright_spec.exists() and playwright_spec.is_file(), f"{prefix}.runtimeEvidenceSelectors.playwrightSpec is missing")
    technical_closure = entry.get("technicalClosure")
    if technical_closure is not None:
        require(isinstance(technical_closure, dict), f"{prefix}.technicalClosure must be an object")
        closure_profile = technical_closure.get("profile")
        profiles = profile_map if profile_map is not None else capability_profiles()
        require(
            closure_profile in profiles,
            f"{prefix}.technicalClosure.profile is unsupported",
        )
        profile = profiles[closure_profile]
        require(profile.get("status") != "UNSUPPORTED", f"{prefix}.technicalClosure.profile is marked UNSUPPORTED")
        require(
            entry["reportTemplate"] in profile.get("reportTemplates", []),
            f"{prefix}.reportTemplate is not exposed by technicalClosure.profile",
        )
        closure_case_code = technical_closure.get("closureGoldenCaseCode")
        require(
            isinstance(closure_case_code, str) and closure_case_code in source_codes,
            f"{prefix}.technicalClosure.closureGoldenCaseCode must reference a Golden Case",
        )
        closure_case = next(case for case in source_cases if case.get("caseCode") == closure_case_code)
        closure_expected = closure_case.get("expected") or {}
        require(closure_expected.get("valid") is True, f"{prefix}.technicalClosure Golden Case must be valid")
        closure_total = closure_expected.get("totalScore")
        require(
            isinstance(closure_total, (int, float, str))
            and str(closure_total).strip()
            and float(closure_total) == float(closure_total),
            f"{prefix}.technicalClosure Golden Case needs totalScore",
        )
        require(isinstance(closure_expected.get("riskLevel"), str), f"{prefix}.technicalClosure Golden Case needs riskLevel")
        if closure_profile == "GENERIC_SINGLE_CHOICE":
            require(
                algorithm.get("code") == "GENERIC_SCORE_CALCULATOR" and algorithm.get("version") == "1",
                f"{prefix}.GENERIC_SINGLE_CHOICE requires GENERIC_SCORE_CALCULATOR:1",
            )
            require(
                all(question.get("questionType") == "SINGLE_CHOICE" for question in package.get("questions", [])),
                f"{prefix}.GENERIC_SINGLE_CHOICE only supports single-choice questions",
            )
            require(
                entry.get("sourceValidator") == "scripts/validate_generic_scale_package.py"
                and source_validator_args == [entry["sourcePackage"]],
                f"{prefix}.GENERIC_SINGLE_CHOICE must use the reusable source validator with its source package argument",
            )
            require(
                entry.get("postgresEvidenceScript") == "admin-web/e2e/fixtures/assert-generic-scale-registry-closure.sql",
                f"{prefix}.GENERIC_SINGLE_CHOICE must use the reusable PostgreSQL closure evidence",
            )
        elif closure_profile == "SCL90_RESTRICTED_PROFILE":
            require(
                algorithm.get("code") == "SCL90_PROFILE" and algorithm.get("version") == "1"
                and algorithm.get("implementationType") == "RESTRICTED_EXTENSION",
                f"{prefix}.SCL90_RESTRICTED_PROFILE requires SCL90_PROFILE:1 restricted extension",
            )
            require(
                entry.get("sourceValidator") == "scripts/validate_scl90_source_package.py"
                and source_validator_args == [entry["sourcePackage"]],
                f"{prefix}.SCL90_RESTRICTED_PROFILE must use the SCL-90 source validator with its source package argument",
            )
            require(
                entry.get("postgresEvidenceScript") == "admin-web/e2e/fixtures/assert-scl90-registry-closure.sql",
                f"{prefix}.SCL90_RESTRICTED_PROFILE must use the dedicated PostgreSQL closure evidence",
            )
        else:
            require(False, f"{prefix}.technicalClosure.profile has no registered validator binding")
        require(
            isinstance(technical_closure.get("taskNamePrefix"), str) and technical_closure["taskNamePrefix"].strip(),
            f"{prefix}.technicalClosure.taskNamePrefix is required",
        )
    if entry["supportStatus"] in TECHNICAL_EVIDENCE_STATUSES:
        require(technical_closure is not None, f"{prefix}.{entry['supportStatus']} requires technicalClosure")
    required_checks = entry.get("requiredChecks")
    require(isinstance(required_checks, list) and required_checks and all(isinstance(value, str) and value for value in required_checks), f"{prefix}.requiredChecks is invalid")
    require(len(required_checks) == len(set(required_checks)), f"{prefix}.requiredChecks must be unique")
    require(set(required_checks) <= SUPPORTED_REQUIRED_CHECKS, f"{prefix}.requiredChecks contains an unsupported check")
    if entry.get("runInTechnicalRegression"):
        require(
            set(required_checks) == SUPPORTED_REQUIRED_CHECKS,
            f"{prefix}.requiredChecks must include every supported regression check",
        )

    last_run = entry.get("lastRegistryRegression")
    require(isinstance(last_run, dict), f"{prefix}.lastRegistryRegression is required")
    require(last_run.get("status") in REGRESSION_STATUSES, f"{prefix}.lastRegistryRegression.status is invalid")
    if last_run.get("status") == "NOT_RUN":
        require(last_run.get("runId") is None, f"{prefix}.NOT_RUN must not claim a runId")
    else:
        require(isinstance(last_run.get("runId"), str) and last_run["runId"].strip(), f"{prefix}.completed regression needs runId")

    # A registry status is an assertion about the evidence, not a free-form
    # label.  Do not allow a package to claim technical or full support while
    # its latest recorded full regression is partial, failed, or absent.
    if entry["supportStatus"] in TECHNICAL_EVIDENCE_STATUSES:
        require(
            last_run.get("status") == "PASS",
            f"{prefix}.{entry['supportStatus']} requires lastRegistryRegression.status=PASS",
        )

    evidence = entry.get("lastIndependentTechnicalEvidence")
    if evidence is not None:
        require(isinstance(evidence, dict), f"{prefix}.lastIndependentTechnicalEvidence must be an object")
        require(isinstance(evidence.get("runId"), str) and evidence["runId"].strip(), f"{prefix}.technical evidence needs runId")
        require(evidence.get("status") == "PASS", f"{prefix}.technical evidence must be PASS or omitted until verified")
        require(isinstance(evidence.get("scope"), str) and evidence["scope"].strip(), f"{prefix}.technical evidence scope is required")
        require(isinstance(evidence.get("verifiedAt"), str) and evidence["verifiedAt"].strip(), f"{prefix}.technical evidence date is required")
    if entry["supportStatus"] in TECHNICAL_EVIDENCE_STATUSES:
        require(evidence is not None, f"{prefix}.{entry['supportStatus']} requires independent technical evidence")
    if evidence is not None:
        require(
            evidence.get("runId") == last_run.get("runId"),
            f"{prefix}.lastIndependentTechnicalEvidence.runId must match lastRegistryRegression.runId",
        )


def validate_registry(path: Path) -> dict[str, Any]:
    registry = load_json(path, "registry")
    require(registry.get("format") == "PSY_SCALE_ADAPTATION_REGISTRY", "registry format mismatch")
    require(registry.get("schemaVersion") == 1, "registry schemaVersion mismatch")
    method_registry_raw = registry.get("genericScoreMethodRegistry")
    require(
        isinstance(method_registry_raw, str) and method_registry_raw.strip(),
        "genericScoreMethodRegistry is required",
    )
    method_registry_path = relative_source_path(method_registry_raw)
    try:
        validate_generic_score_method_registry(method_registry_path)
    except MethodRegistryError as error:
        raise RegistryError(f"generic score method registry is invalid: {error}") from error
    recode_registry_raw = registry.get("genericRecodeMethodRegistry")
    require(
        isinstance(recode_registry_raw, str) and recode_registry_raw.strip(),
        "genericRecodeMethodRegistry is required",
    )
    recode_registry_path = relative_source_path(recode_registry_raw)
    try:
        validate_generic_recode_method_registry(recode_registry_path)
    except RecodeRegistryError as error:
        raise RegistryError(f"generic recode method registry is invalid: {error}") from error
    profiles = capability_profiles()
    scales = registry.get("scales")
    require(isinstance(scales, list) and scales, "registry.scales must be a non-empty list")
    task_ids = [entry.get("taskId") for entry in scales if isinstance(entry, dict)]
    scale_keys = [(entry.get("scaleCode"), entry.get("versionNo")) for entry in scales if isinstance(entry, dict)]
    require(len(task_ids) == len(set(task_ids)), "registry taskId values must be unique")
    require(len(scale_keys) == len(set(scale_keys)), "registry scaleCode/version values must be unique")
    for index, entry in enumerate(scales):
        require(isinstance(entry, dict), f"scales[{index}] must be an object")
        validate_entry(entry, index, profiles)
    return registry


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("registry", nargs="?", type=Path, default=DEFAULT_REGISTRY)
    args = parser.parse_args()
    try:
        registry = validate_registry(args.registry.resolve())
    except RegistryError as error:
        raise SystemExit(f"SCALE_ADAPTATION_REGISTRY_INVALID: {error}") from error
    print(f"Scale adaptation registry valid: {len(registry['scales'])} packages")
    for entry in registry["scales"]:
        print(
            f"- {entry['taskId']} {entry['scaleCode']}@{entry['versionNo']}: "
            f"{len(entry['goldenCases'])} Golden Cases, {entry['supportStatus']}, "
            f"governance={entry['governanceStatus']}, registryRegression={entry['lastRegistryRegression']['status']}"
        )


if __name__ == "__main__":
    main()
