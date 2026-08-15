#!/usr/bin/env python3
"""Verify a machine-readable full scale-adaptation regression report.

This is a report/artifact gate, not a clinical approval gate.  It validates
that a Playwright-mode report is complete for every active registry entry, that
the immutable registry fingerprint matches, that the synthetic calculation
matrix emitted its explicit PostgreSQL evidence marker, and that every active
entry carries the shared TEXT/PDF/Word renderer test summary.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from run_scale_adaptation_registry import registry_fingerprint
from validate_scale_adaptation_registry import (
    DEFAULT_REGISTRY,
    ROOT,
    RegistryError,
    validate_registry,
)


class ReportError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ReportError(message)


def load_json(path: Path, label: str) -> dict[str, Any]:
    require(path.exists() and path.is_file(), f"{label} is missing: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ReportError(f"{label} is not valid JSON: {error}") from error
    require(isinstance(value, dict), f"{label} must be a JSON object")
    return value


def resolve_report_path(report: Path | None, report_dir: Path | None) -> Path:
    require((report is None) != (report_dir is None), "provide exactly one of report or report-dir")
    if report is not None:
        return report if report.is_absolute() else ROOT / report

    directory = report_dir if report_dir and report_dir.is_absolute() else ROOT / report_dir  # type: ignore[arg-type]
    candidates = list(directory.glob("registry-*.json"))
    require(candidates, f"no registry-*.json report found in {directory}")
    return max(candidates, key=lambda path: (path.stat().st_mtime_ns, path.name))


def verify(report_path: Path, registry_path: Path, require_current_pointers: bool = False) -> dict[str, Any]:
    report = load_json(report_path.resolve(), "regression report")
    registry = validate_registry(registry_path.resolve())

    require(report.get("format") == "PSY_SCALE_REGRESSION_REPORT", "report.format must be PSY_SCALE_REGRESSION_REPORT")
    require(report.get("schemaVersion") == 1, "report.schemaVersion must be 1")
    require(report.get("mode") == "PLAYWRIGHT_SELECTOR", "report.mode must be PLAYWRIGHT_SELECTOR")
    require(isinstance(report.get("runId"), str) and report["runId"].startswith("REG-PLAYWRIGHT-"), "report.runId is invalid")
    require(report.get("overallStatus") == "PASS", "report.overallStatus must be PASS")

    expected_registry = str(registry_path.resolve().relative_to(ROOT)).replace("\\", "/")
    require(report.get("registry") == expected_registry, "report.registry does not point to the verified registry")
    require(report.get("registrySha256") == registry_fingerprint(registry), "report.registrySha256 does not match immutable registry inputs")
    method_registry_raw = registry.get("genericScoreMethodRegistry")
    require(isinstance(method_registry_raw, str) and method_registry_raw.strip(), "registry.genericScoreMethodRegistry is required")
    method_registry_path = (ROOT / method_registry_raw).resolve()
    require(method_registry_path.is_file(), "generic score method registry is missing")
    expected_method_registry = str(method_registry_path.relative_to(ROOT)).replace("\\", "/")
    expected_method_digest = hashlib.sha256(method_registry_path.read_bytes()).hexdigest()
    require(report.get("genericScoreMethodRegistry") == expected_method_registry, "report.genericScoreMethodRegistry is stale")
    require(report.get("genericScoreMethodRegistrySha256") == expected_method_digest, "report.genericScoreMethodRegistrySha256 is stale")
    recode_registry_raw = registry.get("genericRecodeMethodRegistry")
    require(isinstance(recode_registry_raw, str) and recode_registry_raw.strip(), "registry.genericRecodeMethodRegistry is required")
    recode_registry_path = (ROOT / recode_registry_raw).resolve()
    require(recode_registry_path.is_file(), "generic recode method registry is missing")
    expected_recode_registry = str(recode_registry_path.relative_to(ROOT)).replace("\\", "/")
    expected_recode_digest = hashlib.sha256(recode_registry_path.read_bytes()).hexdigest()
    require(report.get("genericRecodeMethodRegistry") == expected_recode_registry, "report.genericRecodeMethodRegistry is stale")
    require(report.get("genericRecodeMethodRegistrySha256") == expected_recode_digest, "report.genericRecodeMethodRegistrySha256 is stale")
    recode_registry = json.loads(recode_registry_path.read_text(encoding="utf-8"))
    expected_recode_codes = [rule["ruleCode"] for rule in recode_registry["rules"]]
    expected_question_types = recode_registry.get("questionTypeCoverage", [])

    scales = registry["scales"]
    active = [entry for entry in scales if entry.get("runInTechnicalRegression") is True]
    scope = report.get("scope")
    require(isinstance(scope, dict), "report.scope is required")
    require(scope.get("registeredEntries") == len(scales), "report.scope.registeredEntries is stale")
    require(scope.get("technicalRegressionEntries") == len(active), "report.scope.technicalRegressionEntries is stale")
    require(scope.get("android") == "EXCLUDED", "Android scope must remain EXCLUDED")
    require(scope.get("clinicalApproval") == "NOT_ESTABLISHED_BY_THIS_RUN", "report may not establish clinical approval")
    require(scope.get("postgresIsolation") == "CALLER_MUST_PROVIDE_ISOLATED_ENVIRONMENT", "PostgreSQL isolation scope is invalid")
    require(scope.get("genericScoreMethodMatrix") == "SYNTHETIC_TECHNICAL_FIXTURE_ONLY", "matrix scope must remain synthetic-only")
    require(scope.get("genericMissingAnswerPolicyMatrix") == "SYNTHETIC_TECHNICAL_FIXTURE_ONLY", "quality-policy matrix scope must remain synthetic-only")
    require(scope.get("genericRecodeMethodMatrix") == "SYNTHETIC_TECHNICAL_FIXTURE_ONLY", "recode matrix scope must remain synthetic-only")
    if require_current_pointers:
        lifecycle = report.get("schemaLifecycle")
        require(isinstance(lifecycle, dict), "report.schemaLifecycle is required for current evidence")
        require(lifecycle.get("createdByWrapper") is True, "report.schemaLifecycle must be wrapper evidence")
        require(lifecycle.get("cleanupStatus") == "PASS", "report.schemaLifecycle.cleanupStatus must be PASS")
        require(lifecycle.get("residualPsyE2eSchemasAfterCleanup") == 0, "report schema cleanup left psy_e2e schemas")
        require(isinstance(lifecycle.get("schema"), str) and lifecycle["schema"].startswith("psy_e2e_"), "report.schemaLifecycle.schema is invalid")

    matrix = report.get("genericScoreMethodMatrix")
    require(isinstance(matrix, dict), "genericScoreMethodMatrix is required")
    require(matrix.get("status") == "PASS", "genericScoreMethodMatrix.status must be PASS")
    require(matrix.get("exitCode") == 0, "genericScoreMethodMatrix.exitCode must be 0")
    matrix_evidence = matrix.get("evidenceChecks")
    require(isinstance(matrix_evidence, dict), "matrix evidenceChecks is required")
    require(matrix_evidence.get("all_five_methods") == "PASS", "matrix evidence marker is missing")
    method_registry = json.loads(method_registry_path.read_text(encoding="utf-8"))
    expected_method_codes = [method["methodCode"] for method in method_registry["methods"]]
    expected_policies = [
        policy
        for policy in ("REJECT", "ALLOW", "PRORATE")
        if any(policy in method.get("missingAnswerPolicies", []) for method in method_registry["methods"])
    ]
    require(matrix.get("methodRegistry") == expected_method_registry, "matrix method registry path is stale")
    require(matrix.get("methodRegistrySha256") == expected_method_digest, "matrix method registry hash is stale")
    require(matrix.get("declaredMethods") == expected_method_codes, "matrix declared method list is stale")
    require(
        all(matrix_evidence.get(f"method_{method_code}") == "PASS" for method_code in expected_method_codes),
        "matrix method evidence markers are incomplete",
    )

    quality_matrix = report.get("genericQualityPolicyMatrix")
    require(isinstance(quality_matrix, dict), "genericQualityPolicyMatrix is required")
    require(quality_matrix.get("status") == "PASS", "genericQualityPolicyMatrix.status must be PASS")
    require(quality_matrix.get("exitCode") == 0, "genericQualityPolicyMatrix.exitCode must be 0")
    quality_evidence = quality_matrix.get("evidenceChecks")
    require(isinstance(quality_evidence, dict), "quality-policy matrix evidenceChecks is required")
    require(quality_evidence.get("all_methods_policies") == "PASS", "quality-policy matrix all_methods_policies marker is missing")
    require(quality_evidence.get("all_policies") == "PASS", "quality-policy matrix all_policies marker is missing")
    require(quality_evidence.get("policy_REJECT") == "PASS", "quality-policy matrix REJECT marker is missing")
    require(quality_evidence.get("policy_ALLOW") == "PASS", "quality-policy matrix ALLOW marker is missing")
    require(quality_evidence.get("policy_PRORATE") == "PASS", "quality-policy matrix PRORATE marker is missing")
    require(quality_matrix.get("methodRegistry") == expected_method_registry, "quality-policy matrix method registry path is stale")
    require(quality_matrix.get("methodRegistrySha256") == expected_method_digest, "quality-policy matrix method registry hash is stale")
    require(quality_matrix.get("declaredMethods") == expected_method_codes, "quality-policy matrix declared method list is stale")
    require(quality_matrix.get("declaredPolicies") == expected_policies, "quality-policy matrix declared policy list is stale")
    require(
        all(
            quality_evidence.get(f"method_{method_code}_policy_{policy}") == "PASS"
            for method_code in expected_method_codes
            for policy in expected_policies
        ),
        "quality-policy matrix per-method/policy evidence markers are incomplete",
    )

    recode_matrix = report.get("genericRecodeMethodMatrix")
    require(isinstance(recode_matrix, dict), "genericRecodeMethodMatrix is required")
    require(recode_matrix.get("status") == "PASS", "genericRecodeMethodMatrix.status must be PASS")
    require(recode_matrix.get("exitCode") == 0, "genericRecodeMethodMatrix.exitCode must be 0")
    recode_evidence = recode_matrix.get("evidenceChecks")
    require(isinstance(recode_evidence, dict), "recode matrix evidenceChecks is required")
    require(recode_evidence.get("all_recode_rules") == "PASS", "recode matrix aggregate marker is missing")
    require(recode_evidence.get("question_types") == "PASS", "recode matrix question-type marker is missing")
    require(
        all(recode_evidence.get(f"rule_{rule_code}") == "PASS" for rule_code in expected_recode_codes),
        "recode matrix rule evidence markers are incomplete",
    )
    require(recode_matrix.get("methodRegistry") == expected_recode_registry, "recode matrix registry path is stale")
    require(recode_matrix.get("methodRegistrySha256") == expected_recode_digest, "recode matrix registry hash is stale")
    require(recode_matrix.get("declaredRules") == expected_recode_codes, "recode matrix declared rule list is stale")
    require(recode_matrix.get("declaredQuestionTypes") == expected_question_types, "recode matrix declared question-type list is stale")
    runtime_recode = recode_matrix.get("playwright")
    postgres_recode = recode_matrix.get("postgres")
    require(isinstance(runtime_recode, dict) and runtime_recode.get("status") == "PASS", "recode matrix Playwright evidence is incomplete")
    require(isinstance(postgres_recode, dict) and postgres_recode.get("status") == "PASS", "recode matrix PostgreSQL evidence is incomplete")

    records = report.get("entries")
    require(isinstance(records, list), "report.entries must be a list")
    by_task = {entry["taskId"]: entry for entry in active}
    require(len(records) == len(active), "report.entries count does not match active registry entries")
    seen: set[str] = set()
    for record in records:
        require(isinstance(record, dict), "report entry must be an object")
        task_id = record.get("taskId")
        require(isinstance(task_id, str) and task_id in by_task, f"report contains unknown or inactive task: {task_id}")
        require(task_id not in seen, f"report contains duplicate task: {task_id}")
        seen.add(task_id)
        expected = by_task[task_id]
        require(record.get("scaleCode") == expected["scaleCode"], f"{task_id}.scaleCode mismatch")
        require(record.get("versionNo") == expected["versionNo"], f"{task_id}.versionNo mismatch")
        require(record.get("sourcePackage") == expected["sourcePackage"], f"{task_id}.sourcePackage mismatch")
        require(record.get("sourceSha256") == expected["sourceSha256"], f"{task_id}.sourceSha256 mismatch")
        require(record.get("supportStatus") == expected["supportStatus"], f"{task_id}.supportStatus mismatch")
        require(record.get("governanceStatus") == expected["governanceStatus"], f"{task_id}.governanceStatus mismatch")
        require(record.get("status") == "PASS", f"{task_id}.status must be PASS")
        require(record.get("runtimeChecksNotExecuted") == 0, f"{task_id} has NOT_RUN runtime checks")
        require(record.get("missingRequiredChecks") == [], f"{task_id} has missing required checks")
        required_results = record.get("requiredCheckResults")
        require(isinstance(required_results, dict), f"{task_id}.requiredCheckResults is required")
        require(set(required_results) == set(expected["requiredChecks"]), f"{task_id}.requiredCheckResults set mismatch")
        require(all(value == "PASS" for value in required_results.values()), f"{task_id} has a non-PASS required check")
        checks = record.get("checks")
        require(isinstance(checks, dict), f"{task_id}.checks is required")
        check_fields = {
            "source_package_integrity": "sourcePackage",
            "question_display": "questionDisplay",
            "golden_case_scores": "goldenCases",
            "scoring_trace": "scoringTrace",
            "question_set_path": "questionSetPath",
            "normative_semantics": "normativeSemantics",
            "trilingual_result_content": "trilingualResultContent",
            "report_semantics": "reportSemantics",
            "task_version_lock": "taskVersionLock",
            "historical_result_immutability": "historicalResultImmutability",
            "idempotent_submission": "idempotentSubmission",
            "concurrent_submission": "concurrentSubmission",
            "rescore_history": "rescoreHistory",
            "quality_outcome": "qualityOutcome",
            "export_semantics": "exportSemantics",
            "security_boundaries": "securityBoundaries",
            "security_audit": "securityAudit",
        }
        for required_check in expected["requiredChecks"]:
            check_field = check_fields[required_check]
            check_value = checks.get(check_field)
            require(isinstance(check_value, dict), f"{task_id}.checks.{check_field} is required")
            require(check_value.get("status") == "PASS", f"{task_id}.checks.{check_field} is not PASS")
            if required_check == "export_semantics":
                require(
                    check_value.get("scope") == "shared_report_detail_text_pdf_word_renderers",
                    f"{task_id}.checks.exportSemantics scope is invalid",
                )
                export_evidence = check_value.get("evidenceChecks")
                require(
                    isinstance(export_evidence, dict)
                    and export_evidence.get("export_semantics") == "PASS",
                    f"{task_id}.checks.exportSemantics evidence marker is missing",
                )
                export_summary = check_value.get("testSummary")
                require(
                    isinstance(export_summary, dict)
                    and export_summary.get("tests", 0) >= 7
                    and export_summary.get("skipped") == 0
                    and export_summary.get("failures") == 0
                    and export_summary.get("errors") == 0,
                    f"{task_id}.checks.exportSemantics test summary is incomplete",
                )
        playwright_check = checks.get("playwright")
        postgres_check = checks.get("postgres")
        require(isinstance(playwright_check, dict), f"{task_id}.checks.playwright is required")
        require(isinstance(postgres_check, dict), f"{task_id}.checks.postgres is required")
        require(playwright_check.get("status") == "PASS", f"{task_id} Playwright check is not PASS")
        require(postgres_check.get("status") == "PASS", f"{task_id} PostgreSQL check is not PASS")

    require(seen == set(by_task), "report does not cover every active registry entry")
    if require_current_pointers:
        for entry in active:
            task_id = entry["taskId"]
            last_run = entry.get("lastRegistryRegression")
            require(
                isinstance(last_run, dict)
                and last_run.get("runId") == report["runId"]
                and last_run.get("status") == "PASS",
                f"{task_id}.lastRegistryRegression is not current for {report['runId']}",
            )
            evidence = entry.get("lastIndependentTechnicalEvidence")
            require(
                isinstance(evidence, dict)
                and evidence.get("runId") == report["runId"]
                and evidence.get("status") == "PASS",
                f"{task_id}.lastIndependentTechnicalEvidence is not current for {report['runId']}",
            )
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", nargs="?", type=Path, help="specific JSON report path")
    parser.add_argument("--report-dir", type=Path, help="directory from which to select the newest registry-*.json report")
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument(
        "--require-current-pointers",
        action="store_true",
        help="also require every active registry pointer to reference this report run",
    )
    args = parser.parse_args()
    try:
        report_path = resolve_report_path(args.report, args.report_dir)
        report = verify(report_path, args.registry, require_current_pointers=args.require_current_pointers)
    except (OSError, ValueError, RegistryError, ReportError) as error:
        print(f"SCALE_ADAPTATION_REPORT_INVALID: {error}", file=sys.stderr)
        return 1
    print(
        "SCALE_ADAPTATION_REPORT_VALID|"
        f"run={report['runId']}|entries={len(report['entries'])}|"
        "matrix=PASS|requiredChecks=PASS"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
