#!/usr/bin/env python3
"""Run registry-driven scale adaptation checks without inventing coverage.

The default mode validates every registered source package.  A source-package
pass is deliberately not a PostgreSQL, Playwright, scoring-trace, or clinical
approval pass.  The optional Playwright mode runs the exact evidence selector
declared by each registry entry against an already-running environment; the
caller remains responsible for starting an isolated PostgreSQL/application
stack and seeding it with the documented fixtures.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from validate_scale_adaptation_registry import RegistryError, ROOT, validate_registry


DEFAULT_REGISTRY = ROOT / "doc/scale-packages/scale-adaptation-registry.json"
CHECK_TARGETS = {
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


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def registry_fingerprint(registry: dict[str, Any]) -> str:
    """Hash immutable regression inputs, not mutable last-run bookkeeping."""
    fingerprint = json.loads(json.dumps(registry, ensure_ascii=False))
    method_registry_path = fingerprint.get("genericScoreMethodRegistry")
    if isinstance(method_registry_path, str) and method_registry_path.strip():
        method_registry_file = (ROOT / method_registry_path).resolve()
        if method_registry_file.is_file():
            method_digest = hashlib.sha256(method_registry_file.read_bytes()).hexdigest()
            fingerprint["genericScoreMethodRegistrySha256"] = method_digest
    recode_registry_path = fingerprint.get("genericRecodeMethodRegistry")
    if isinstance(recode_registry_path, str) and recode_registry_path.strip():
        recode_registry_file = (ROOT / recode_registry_path).resolve()
        if recode_registry_file.is_file():
            recode_digest = hashlib.sha256(recode_registry_file.read_bytes()).hexdigest()
            fingerprint["genericRecodeMethodRegistrySha256"] = recode_digest
    for entry in fingerprint.get("scales", []):
        for evidence_field in (
            "supportStatus",
            "governanceStatus",
            "lastRegistryRegression",
            "lastIndependentTechnicalEvidence",
        ):
            entry.pop(evidence_field, None)
    canonical = json.dumps(
        fingerprint,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def run_source_validator(entry: dict[str, Any]) -> dict[str, Any]:
    validator = ROOT / entry["sourceValidator"]
    validator_arguments = entry.get("sourceValidatorArgs", [])
    command = [sys.executable, str(validator), *validator_arguments]
    completed = subprocess.run(
        command,
        cwd=ROOT,
        env=os.environ.copy(),
        text=True,
        capture_output=True,
        check=False,
    )
    return {
        "status": "PASS" if completed.returncode == 0 else "FAIL",
        "command": [sys.executable, str(validator.relative_to(ROOT)), *validator_arguments],
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip(),
    }


def run_playwright_selector(entry: dict[str, Any]) -> dict[str, Any]:
    selector = entry["runtimeEvidenceSelectors"]
    spec = ROOT / selector["playwrightSpec"]
    if not spec.is_file():
        return {
            "status": "FAIL",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": f"Playwright spec does not exist: {spec.relative_to(ROOT)}",
        }

    # Use the repository's package manager command so the runner works with
    # the same local Playwright installation as the existing E2E scripts.
    command = [
        "npm",
        "exec",
        "--",
        "playwright",
        "test",
        str(spec.relative_to(ROOT / "admin-web")),
        "-g",
        selector["playwrightTitle"],
    ]
    child_environment = os.environ.copy()
    child_environment["PSY_SCALE_REGRESSION_TARGET"] = entry["scaleCode"]
    completed = subprocess.run(
        command,
        cwd=ROOT / "admin-web",
        env=child_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined = f"{completed.stdout}\n{completed.stderr}"
    evidence_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"REGISTRY_RUNTIME_CHECK\|([^|]+)\|([^\s|]+)", combined)
    }
    return {
        "status": "PASS" if completed.returncode == 0 else "FAIL",
        "command": command,
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip(),
        "evidenceChecks": evidence_checks,
    }


def run_postgres_evidence(entry: dict[str, Any]) -> dict[str, Any]:
    required_environment = {
        "PSY_E2E_SCHEMA": os.environ.get("PSY_E2E_SCHEMA"),
        "PSY_E2E_DB_HOST": os.environ.get("PSY_E2E_DB_HOST", "localhost"),
        "PSY_E2E_DB_PORT": os.environ.get("PSY_E2E_DB_PORT", "5432"),
        "PSY_E2E_DB_NAME": os.environ.get("PSY_E2E_DB_NAME", "postgres"),
        "PSY_E2E_DB_USERNAME": os.environ.get("PSY_E2E_DB_USERNAME"),
    }
    missing = [name for name, value in required_environment.items() if not value]
    if missing:
        return {
            "status": "NOT_RUN",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": f"Missing isolated PostgreSQL environment: {', '.join(missing)}",
            "evidenceChecks": {},
        }

    script = ROOT / entry["postgresEvidenceScript"]
    registry_variables: list[str] = []
    closure = entry.get("technicalClosure")
    if isinstance(closure, dict) and closure.get("profile") in {"GENERIC_SINGLE_CHOICE", "SCL90_RESTRICTED_PROFILE"}:
        package = json.loads((ROOT / entry["sourcePackage"]).read_text(encoding="utf-8"))
        closure_case = next(
            case for case in package["goldenCases"]
            if case["caseCode"] == closure["closureGoldenCaseCode"]
        )
        closure_expected = closure_case["expected"]
        score_method = str(package["scale"].get("scoreMethod", "SIMPLE_SUM")).strip().upper()
        dimension_aggregation = str(
            (package.get("scoring") or {}).get("dimensionAggregation", score_method)
        ).strip().upper()
        score_coefficient = package["scale"].get("scoreCoefficient", 1)
        norms = package.get("norms") or {}
        norm_factor_references = norms.get("factorReferenceFromUserText") or {}
        if not isinstance(norm_factor_references, dict):
            norm_factor_references = {}
        result_rule_signatures = sorted(
            [
                {
                    "riskLevel": rule.get("riskLevel"),
                    "scoreMin": rule.get("scoreMin"),
                    "scoreMax": rule.get("scoreMax"),
                    "scoreSource": rule.get("scoreSource", "RAW_SCORE"),
                    "normCode": rule.get("normCode"),
                }
                for rule in package.get("resultRules", [])
                if isinstance(rule, dict)
            ],
            key=lambda rule: (
                str(rule.get("riskLevel")),
                str(rule.get("scoreMin")),
                str(rule.get("scoreMax")),
                str(rule.get("scoreSource")),
                str(rule.get("normCode")),
            ),
        )
        high_risk_rule_codes = sorted(
            rule.get("ruleCode") for rule in package.get("highRiskRules", [])
            if isinstance(rule, dict) and isinstance(rule.get("ruleCode"), str)
        )
        declared_metric_codes = (
            (package.get("scoring") or {}).get("indices", {}).keys()
            if isinstance((package.get("scoring") or {}).get("indices", {}), dict)
            else []
        )
        closure_metric_codes = (
            closure_expected.get("metrics", {}).keys()
            if isinstance(closure_expected.get("metrics", {}), dict)
            else []
        )
        derived_metric_codes = sorted(set(declared_metric_codes) | set(closure_metric_codes))
        registry_variables = [
            "-v", f"version_no={entry['versionNo']}",
            "-v", f"task_prefix={closure['taskNamePrefix']}",
            "-v", f"expected_total={closure_expected['totalScore']}",
            "-v", f"expected_score_method={score_method}",
            "-v", f"expected_dimension_aggregation={dimension_aggregation}",
            "-v", f"expected_score_coefficient={score_coefficient}",
            "-v", "expected_skip_rules_json=" + json.dumps(package.get("skipRules", []), ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            "-v", "expected_result_rule_signatures_json=" + json.dumps(result_rule_signatures, ensure_ascii=False, separators=(",", ":")),
            "-v", "expected_high_risk_rule_codes_json=" + json.dumps(high_risk_rule_codes, ensure_ascii=False, separators=(",", ":")),
            "-v", "expected_derived_metric_codes_json=" + json.dumps(derived_metric_codes, ensure_ascii=False, separators=(",", ":")),
            "-v", f"expected_norm_status={str(norms.get('status', 'UNKNOWN')).strip().upper()}",
            "-v", "expected_norm_codes_json=" + json.dumps(sorted(norm_factor_references.keys()), ensure_ascii=False, separators=(",", ":")),
            "-v", f"expected_risk={closure_expected['riskLevel']}",
            "-v", f"expected_high_risk_rule={closure_expected.get('highRiskRuleCode', '')}",
            "-v", "expected_metrics_json=" + json.dumps(
                closure_expected.get("metrics", {}), ensure_ascii=False, sort_keys=True, separators=(",", ":")
            ),
            "-v", f"question_count={len(package['questions'])}",
            "-v", f"dimension_count={len(package['dimensions'])}",
            "-v", f"golden_count={len(package['goldenCases'])}",
        ]
    command = [
        "psql",
        "-h",
        required_environment["PSY_E2E_DB_HOST"],
        "-p",
        required_environment["PSY_E2E_DB_PORT"],
        "-U",
        required_environment["PSY_E2E_DB_USERNAME"],
        "-d",
        required_environment["PSY_E2E_DB_NAME"],
        "-v",
        "ON_ERROR_STOP=1",
        "-v",
        f"scale_code={entry['scaleCode']}",
        *registry_variables,
        "-f",
        str(script.relative_to(ROOT)),
    ]
    child_environment = os.environ.copy()
    child_environment["PGPASSWORD"] = os.environ.get("PSY_E2E_DB_PASSWORD", "")
    child_environment["PGOPTIONS"] = f"-c search_path={required_environment['PSY_E2E_SCHEMA']}"
    completed = subprocess.run(
        command,
        cwd=ROOT,
        env=child_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined = f"{completed.stdout}\n{completed.stderr}"
    evidence_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"REGISTRY_CHECK\|([^|]+)\|([^\s|]+)", combined)
    }
    return {
        "status": "PASS" if completed.returncode == 0 else "FAIL",
        "command": [value for value in command if value != os.environ.get("PSY_E2E_DB_PASSWORD", "")],
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip(),
        "evidenceChecks": evidence_checks,
    }


def run_generic_score_method_matrix_evidence(method_registry_path: Path) -> dict[str, Any]:
    """Verify the non-registered synthetic method matrix in isolated PostgreSQL.

    The matrix deliberately contains no real instrument content.  It proves
    that the same persisted task/result/report path executes every generic
    score method, while keeping formal registry support limited to source
    packages with their own governance evidence.
    """
    method_registry = json.loads(method_registry_path.read_text(encoding="utf-8"))
    method_codes = [method["methodCode"] for method in method_registry["methods"]]
    method_digest = hashlib.sha256(method_registry_path.read_bytes()).hexdigest()
    required_environment = {
        "PSY_E2E_SCHEMA": os.environ.get("PSY_E2E_SCHEMA"),
        "PSY_E2E_DB_HOST": os.environ.get("PSY_E2E_DB_HOST", "localhost"),
        "PSY_E2E_DB_PORT": os.environ.get("PSY_E2E_DB_PORT", "5432"),
        "PSY_E2E_DB_NAME": os.environ.get("PSY_E2E_DB_NAME", "postgres"),
        "PSY_E2E_DB_USERNAME": os.environ.get("PSY_E2E_DB_USERNAME"),
    }
    missing = [name for name, value in required_environment.items() if not value]
    if missing:
        return {
            "status": "NOT_RUN",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": f"Missing isolated PostgreSQL environment: {', '.join(missing)}",
            "evidenceChecks": {},
        }

    script = ROOT / "admin-web/e2e/fixtures/assert-generic-score-method-matrix.sql"
    command = [
        "psql",
        "-h",
        required_environment["PSY_E2E_DB_HOST"],
        "-p",
        required_environment["PSY_E2E_DB_PORT"],
        "-U",
        required_environment["PSY_E2E_DB_USERNAME"],
        "-d",
        required_environment["PSY_E2E_DB_NAME"],
        "-v",
        "ON_ERROR_STOP=1",
        "-f",
        str(script.relative_to(ROOT)),
    ]
    child_environment = os.environ.copy()
    child_environment["PGPASSWORD"] = os.environ.get("PSY_E2E_DB_PASSWORD", "")
    child_environment["PGOPTIONS"] = f"-c search_path={required_environment['PSY_E2E_SCHEMA']}"
    completed = subprocess.run(
        command,
        cwd=ROOT,
        env=child_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined = f"{completed.stdout}\n{completed.stderr}"
    evidence_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"METHOD_MATRIX_CHECK\|([^|]+)\|([^\s|]+)", combined)
    }
    evidence_passed = (
        evidence_checks.get("all_five_methods") == "PASS"
        and all(evidence_checks.get(f"method_{method_code}") == "PASS" for method_code in method_codes)
    )
    return {
        "status": "PASS" if completed.returncode == 0 and evidence_passed else "FAIL",
        "command": command,
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip() if evidence_passed else (
            f"{completed.stderr.strip()}\n"
            "Missing METHOD_MATRIX_CHECK|all_five_methods|PASS or one of the declared per-method evidence markers."
        ).strip(),
        "evidenceChecks": evidence_checks,
        "methodRegistry": str(method_registry_path.relative_to(ROOT)),
        "methodRegistrySha256": method_digest,
        "declaredMethods": method_codes,
    }


def run_generic_quality_policy_matrix_evidence(method_registry_path: Path) -> dict[str, Any]:
    """Verify every declared missing-answer policy for every generic method.

    This matrix deliberately contains no instrument content.  It closes the
    missing-answer policy contract declared beside the five generic methods by
    checking quality outcome, scoring trace prorate factor, result and report
    rows in the same isolated PostgreSQL schema.
    """
    method_registry = json.loads(method_registry_path.read_text(encoding="utf-8"))
    method_digest = hashlib.sha256(method_registry_path.read_bytes()).hexdigest()
    method_codes = [method["methodCode"] for method in method_registry["methods"]]
    policy_order = ["REJECT", "ALLOW", "PRORATE"]
    declared_policies = [
        policy
        for policy in policy_order
        if any(policy in method.get("missingAnswerPolicies", []) for method in method_registry["methods"])
    ]
    required_environment = {
        "PSY_E2E_SCHEMA": os.environ.get("PSY_E2E_SCHEMA"),
        "PSY_E2E_DB_HOST": os.environ.get("PSY_E2E_DB_HOST", "localhost"),
        "PSY_E2E_DB_PORT": os.environ.get("PSY_E2E_DB_PORT", "5432"),
        "PSY_E2E_DB_NAME": os.environ.get("PSY_E2E_DB_NAME", "postgres"),
        "PSY_E2E_DB_USERNAME": os.environ.get("PSY_E2E_DB_USERNAME"),
    }
    missing = [name for name, value in required_environment.items() if not value]
    if missing:
        return {
            "status": "NOT_RUN",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": f"Missing isolated PostgreSQL environment: {', '.join(missing)}",
            "evidenceChecks": {},
        }

    script = ROOT / "admin-web/e2e/fixtures/assert-generic-quality-policy-matrix.sql"
    command = [
        "psql",
        "-h",
        required_environment["PSY_E2E_DB_HOST"],
        "-p",
        required_environment["PSY_E2E_DB_PORT"],
        "-U",
        required_environment["PSY_E2E_DB_USERNAME"],
        "-d",
        required_environment["PSY_E2E_DB_NAME"],
        "-v",
        "ON_ERROR_STOP=1",
        "-f",
        str(script.relative_to(ROOT)),
    ]
    child_environment = os.environ.copy()
    child_environment["PGPASSWORD"] = os.environ.get("PSY_E2E_DB_PASSWORD", "")
    child_environment["PGOPTIONS"] = f"-c search_path={required_environment['PSY_E2E_SCHEMA']}"
    completed = subprocess.run(
        command,
        cwd=ROOT,
        env=child_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined = f"{completed.stdout}\n{completed.stderr}"
    evidence_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"QUALITY_POLICY_MATRIX_CHECK\|([^|]+)\|([^\s|]+)", combined)
    }
    evidence_passed = (
        evidence_checks.get("all_methods_policies") == "PASS"
        and evidence_checks.get("all_policies") == "PASS"
        and all(evidence_checks.get(f"policy_{policy}") == "PASS" for policy in declared_policies)
        and all(
            evidence_checks.get(f"method_{method_code}_policy_{policy}") == "PASS"
            for method_code in method_codes
            for policy in declared_policies
        )
    )
    return {
        "status": "PASS" if completed.returncode == 0 and evidence_passed else "FAIL",
        "command": command,
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip() if evidence_passed else (
            f"{completed.stderr.strip()}\n"
            "Missing all_methods_policies, per-method/policy markers, or aggregate policy markers."
        ).strip(),
        "evidenceChecks": evidence_checks,
        "methodRegistry": str(method_registry_path.relative_to(ROOT)),
        "methodRegistrySha256": method_digest,
        "declaredMethods": method_codes,
        "declaredPolicies": declared_policies,
    }


def run_generic_recode_method_matrix_evidence(recode_registry_path: Path) -> dict[str, Any]:
    """Run the source-independent dimension/time recode fixture end to end.

    The Playwright phase proves source-package import, Golden Case execution,
    publication/task lock, TIME/SLIDER answer persistence and report
    dimensions.  The PostgreSQL phase independently recomputes the same
    persisted trace and recoded dimensions from the disposable schema.
    """
    recode_registry = json.loads(recode_registry_path.read_text(encoding="utf-8"))
    rule_codes = [rule["ruleCode"] for rule in recode_registry["rules"]]
    question_type_codes = recode_registry.get("questionTypeCoverage", [])
    registry_digest = hashlib.sha256(recode_registry_path.read_bytes()).hexdigest()
    required_environment = {
        "PSY_E2E_SCHEMA": os.environ.get("PSY_E2E_SCHEMA"),
        "PSY_E2E_DB_HOST": os.environ.get("PSY_E2E_DB_HOST", "localhost"),
        "PSY_E2E_DB_PORT": os.environ.get("PSY_E2E_DB_PORT", "5432"),
        "PSY_E2E_DB_NAME": os.environ.get("PSY_E2E_DB_NAME", "postgres"),
        "PSY_E2E_DB_USERNAME": os.environ.get("PSY_E2E_DB_USERNAME"),
        "PSY_E2E_WEB_URL": os.environ.get("PSY_E2E_WEB_URL"),
        "PSY_E2E_BACKEND_URL": os.environ.get("PSY_E2E_BACKEND_URL"),
    }
    missing = [name for name, value in required_environment.items() if not value]
    if missing:
        return {
            "status": "NOT_RUN",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": f"Missing isolated recode-matrix environment: {', '.join(missing)}",
            "evidenceChecks": {},
            "postgres": {"status": "NOT_RUN"},
            "methodRegistry": str(recode_registry_path.relative_to(ROOT)),
            "methodRegistrySha256": registry_digest,
            "declaredRules": rule_codes,
            "declaredQuestionTypes": question_type_codes,
        }

    spec = ROOT / "admin-web/e2e/generic-recode-method-matrix.spec.ts"
    playwright_command = [
        "npm",
        "exec",
        "--",
        "playwright",
        "test",
        str(spec.relative_to(ROOT / "admin-web")),
        "-g",
        "synthetic generic dimension and time recode matrix runs in isolated PostgreSQL",
    ]
    child_environment = os.environ.copy()
    completed_playwright = subprocess.run(
        playwright_command,
        cwd=ROOT / "admin-web",
        env=child_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined_playwright = f"{completed_playwright.stdout}\n{completed_playwright.stderr}"
    runtime_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"RECODE_RUNTIME_CHECK\|([^|]+)\|([^\s|]+)", combined_playwright)
    }
    runtime_passed = (
        completed_playwright.returncode == 0
        and runtime_checks.get("all_recode_rules") == "PASS"
        and runtime_checks.get("question_types") == "PASS"
        and all(runtime_checks.get(f"rule_{code}") == "PASS" for code in rule_codes)
    )
    playwright_result = {
        "status": "PASS" if runtime_passed else "FAIL",
        "command": playwright_command,
        "exitCode": completed_playwright.returncode,
        "stdout": completed_playwright.stdout.strip(),
        "stderr": completed_playwright.stderr.strip(),
        "evidenceChecks": runtime_checks,
    }
    if not runtime_passed:
        return {
            "status": "FAIL",
            "command": playwright_command,
            "exitCode": completed_playwright.returncode,
            "stdout": completed_playwright.stdout.strip(),
            "stderr": (
                f"{completed_playwright.stderr.strip()}\n"
                "Missing RECODE_RUNTIME_CHECK aggregate or per-rule markers."
            ).strip(),
            "evidenceChecks": runtime_checks,
            "playwright": playwright_result,
            "postgres": {"status": "NOT_RUN"},
            "methodRegistry": str(recode_registry_path.relative_to(ROOT)),
            "methodRegistrySha256": registry_digest,
            "declaredRules": rule_codes,
            "declaredQuestionTypes": question_type_codes,
        }

    script = ROOT / "admin-web/e2e/fixtures/assert-generic-recode-method-matrix.sql"
    postgres_command = [
        "psql",
        "-h",
        required_environment["PSY_E2E_DB_HOST"],
        "-p",
        required_environment["PSY_E2E_DB_PORT"],
        "-U",
        required_environment["PSY_E2E_DB_USERNAME"],
        "-d",
        required_environment["PSY_E2E_DB_NAME"],
        "-v",
        "ON_ERROR_STOP=1",
        "-f",
        str(script.relative_to(ROOT)),
    ]
    postgres_environment = os.environ.copy()
    postgres_environment["PGPASSWORD"] = os.environ.get("PSY_E2E_DB_PASSWORD", "")
    postgres_environment["PGOPTIONS"] = f"-c search_path={required_environment['PSY_E2E_SCHEMA']}"
    completed_postgres = subprocess.run(
        postgres_command,
        cwd=ROOT,
        env=postgres_environment,
        text=True,
        capture_output=True,
        check=False,
    )
    combined_postgres = f"{completed_postgres.stdout}\n{completed_postgres.stderr}"
    postgres_checks = {
        match.group(1): match.group(2)
        for match in re.finditer(r"RECODE_MATRIX_CHECK\|([^|]+)\|([^\s|]+)", combined_postgres)
    }
    postgres_passed = (
        completed_postgres.returncode == 0
        and postgres_checks.get("all_recode_rules") == "PASS"
        and postgres_checks.get("question_types") == "PASS"
        and all(postgres_checks.get(f"rule_{code}") == "PASS" for code in rule_codes)
    )
    postgres_result = {
        "status": "PASS" if postgres_passed else "FAIL",
        "command": postgres_command,
        "exitCode": completed_postgres.returncode,
        "stdout": completed_postgres.stdout.strip(),
        "stderr": completed_postgres.stderr.strip(),
        "evidenceChecks": postgres_checks,
    }
    return {
        "status": "PASS" if runtime_passed and postgres_passed else "FAIL",
        "command": playwright_command + ["then", *postgres_command],
        "exitCode": 0 if runtime_passed and postgres_passed else (completed_postgres.returncode or completed_playwright.returncode),
        "stdout": f"{completed_playwright.stdout.strip()}\n{completed_postgres.stdout.strip()}".strip(),
        "stderr": f"{completed_playwright.stderr.strip()}\n{completed_postgres.stderr.strip()}".strip(),
        "evidenceChecks": {**runtime_checks, **postgres_checks},
        "playwright": playwright_result,
        "postgres": postgres_result,
        "methodRegistry": str(recode_registry_path.relative_to(ROOT)),
        "methodRegistrySha256": registry_digest,
        "declaredRules": rule_codes,
        "declaredQuestionTypes": question_type_codes,
    }


def run_export_semantics_evidence() -> dict[str, Any]:
    """Run the shared renderer contract against all controlled report templates.

    This is one shared backend check. Every active ScalePackage must use the
    same ReportDetail-backed TEXT/PDF/Word renderer, while the Playwright
    closure and PostgreSQL assertions still exercise each scale's runtime
    export requests and audit events.
    """
    command = [
        "./gradlew",
        "test",
        "--tests",
        "org.sainm.psy.export.service.ExportServiceTest",
        "--rerun-tasks",
        "--no-daemon",
    ]
    result_file = (
        ROOT
        / "backend/build/test-results/test/TEST-org.sainm.psy.export.service.ExportServiceTest.xml"
    )
    # Do not allow a stale XML result from an earlier Gradle invocation to
    # satisfy this evidence check when the current command fails before
    # writing its report.
    result_file.unlink(missing_ok=True)
    completed = subprocess.run(
        command,
        cwd=ROOT / "backend",
        env=os.environ.copy(),
        text=True,
        capture_output=True,
        check=False,
    )
    test_summary: dict[str, Any] = {}
    if result_file.exists():
        try:
            suite = ET.parse(result_file).getroot()
            test_summary = {
                "tests": int(suite.attrib.get("tests", "0")),
                "skipped": int(suite.attrib.get("skipped", "0")),
                "failures": int(suite.attrib.get("failures", "0")),
                "errors": int(suite.attrib.get("errors", "0")),
            }
        except (OSError, ET.ParseError, ValueError):
            test_summary = {}
    evidence_passed = (
        completed.returncode == 0
        and test_summary.get("tests", 0) == 8
        and test_summary.get("skipped", 1) == 0
        and test_summary.get("failures", 1) == 0
        and test_summary.get("errors", 1) == 0
    )
    evidence_checks = {"export_semantics": "PASS"} if evidence_passed else {}
    failure_suffix = ""
    if not evidence_passed:
        failure_suffix = (
            "Shared ExportServiceTest did not produce the required 8 passing tests; "
            f"summary={json.dumps(test_summary, sort_keys=True)}"
        )
    return {
        "status": "PASS" if evidence_passed else "FAIL",
        "command": command,
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": (f"{completed.stderr.strip()}\n{failure_suffix}").strip(),
        "evidenceChecks": evidence_checks,
        "testResultFile": str(result_file.relative_to(ROOT)),
        "testSummary": test_summary,
        "scope": "shared_report_detail_text_pdf_word_renderers",
    }


def build_report(registry_path: Path, registry: dict[str, Any], mode: str) -> dict[str, Any]:
    run_id = f"REG-{mode.upper()}-{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')}"
    entries: list[dict[str, Any]] = []
    export_semantics = (
        run_export_semantics_evidence()
        if mode == "playwright"
        else {
            "status": "NOT_RUN",
            "command": [],
            "exitCode": None,
            "stdout": "",
            "stderr": "",
            "evidenceChecks": {},
        }
    )

    for entry in registry["scales"]:
        if not entry["runInTechnicalRegression"]:
            continue

        source_result = run_source_validator(entry)
        checks: dict[str, Any] = {
            "sourcePackage": source_result,
            "questionDisplay": {"status": "NOT_RUN"},
            "goldenCases": {"status": "NOT_RUN"},
            "postgres": {"status": "NOT_RUN"},
            "scoringTrace": {"status": "NOT_RUN"},
            "questionSetPath": {"status": "NOT_RUN"},
            "normativeSemantics": {"status": "NOT_RUN"},
            "trilingualResultContent": {"status": "NOT_RUN"},
            "reportSemantics": {"status": "NOT_RUN"},
            "taskVersionLock": {"status": "NOT_RUN"},
            "historicalResultImmutability": {"status": "NOT_RUN"},
            "idempotentSubmission": {"status": "NOT_RUN"},
            "concurrentSubmission": {"status": "NOT_RUN"},
            "rescoreHistory": {"status": "NOT_RUN"},
            "qualityOutcome": {"status": "NOT_RUN"},
            "exportSemantics": export_semantics,
            "securityBoundaries": {"status": "NOT_RUN"},
            "securityAudit": {"status": "NOT_RUN"},
        }

        if mode == "playwright" and source_result["status"] == "PASS":
            checks["playwright"] = run_playwright_selector(entry)
            if checks["playwright"]["status"] == "PASS":
                checks["postgres"] = run_postgres_evidence(entry)
        else:
            checks["playwright"] = {"status": "NOT_RUN"}

        evidence_mapping = {
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
        }
        for evidence_key, target_key in evidence_mapping.items():
            evidence_status = checks["postgres"].get("evidenceChecks", {}).get(evidence_key)
            if evidence_status == "PASS":
                checks[target_key] = {"status": "PASS", "source": "postgresEvidenceScript"}
        runtime_evidence_mapping = {
            "question_set_path": "questionSetPath",
            "question_display": "questionDisplay",
        }
        for evidence_key, target_key in runtime_evidence_mapping.items():
            evidence_status = checks["playwright"].get("evidenceChecks", {}).get(evidence_key)
            if evidence_status == "PASS" and checks[target_key]["status"] != "PASS":
                checks[target_key] = {"status": "PASS", "source": "playwrightSelector"}
        security_status = checks["playwright"].get("evidenceChecks", {}).get("security_boundaries")
        if security_status == "PASS":
            checks["securityBoundaries"] = {"status": "PASS", "source": "playwrightSelector"}
        security_audit_status = checks["postgres"].get("evidenceChecks", {}).get("security_audit")
        if security_audit_status == "PASS":
            checks["securityAudit"] = {"status": "PASS", "source": "postgresEvidenceScript"}

        runtime_statuses = [
            checks["goldenCases"]["status"],
            checks["postgres"]["status"],
            checks["scoringTrace"]["status"],
            checks["trilingualResultContent"]["status"],
            checks["reportSemantics"]["status"],
            checks["taskVersionLock"]["status"],
            checks["historicalResultImmutability"]["status"],
            checks["idempotentSubmission"]["status"],
            checks["concurrentSubmission"]["status"],
            checks["rescoreHistory"]["status"],
            checks["qualityOutcome"]["status"],
            checks["exportSemantics"]["status"],
            checks["questionSetPath"]["status"],
            checks["questionDisplay"]["status"],
            checks["normativeSemantics"]["status"],
            checks["securityBoundaries"]["status"],
            checks["securityAudit"]["status"],
        ]
        required_check_results = {
            check: checks[CHECK_TARGETS[check]]["status"]
            for check in entry["requiredChecks"]
            if check in CHECK_TARGETS
        }
        if mode == "source":
            entry_status = "SOURCE_VALIDATION_PASS" if source_result["status"] == "PASS" else "SOURCE_VALIDATION_FAIL"
        else:
            failed_checks = [check for check, status in required_check_results.items() if status == "FAIL"]
            missing_checks = [check for check, status in required_check_results.items() if status != "PASS"]
            if (
                source_result["status"] != "PASS"
                or checks["playwright"]["status"] != "PASS"
                or checks["postgres"]["status"] == "FAIL"
                or failed_checks
            ):
                entry_status = "FAIL"
            elif missing_checks:
                # A blocked/partial package can be technically exercised only
                # through the checks its source and governance allow. Keep that
                # distinction visible instead of calling an incomplete run PASS.
                entry_status = "PARTIAL"
            else:
                entry_status = "PASS"

        entries.append(
            {
                "taskId": entry["taskId"],
                "scaleCode": entry["scaleCode"],
                "versionNo": entry["versionNo"],
                "sourcePackage": entry["sourcePackage"],
                "sourceSha256": entry["sourceSha256"],
                "algorithm": entry["algorithm"],
                "reportTemplate": entry["reportTemplate"],
                "locales": entry["locales"],
                "expected": entry["expected"],
                "requiredChecks": entry["requiredChecks"],
                "supportStatus": entry["supportStatus"],
                "governanceStatus": entry["governanceStatus"],
                "status": entry_status,
                "checks": checks,
                "requiredCheckResults": required_check_results,
                "missingRequiredChecks": [check for check, status in required_check_results.items() if status != "PASS"],
                "runtimeChecksNotExecuted": runtime_statuses.count("NOT_RUN"),
            }
        )

    method_registry_path = (ROOT / registry["genericScoreMethodRegistry"]).resolve()
    recode_registry_path = (ROOT / registry["genericRecodeMethodRegistry"]).resolve()
    method_matrix = (
        run_generic_score_method_matrix_evidence(method_registry_path)
        if mode == "playwright"
        else {"status": "NOT_RUN", "command": [], "exitCode": None, "stdout": "", "stderr": "", "evidenceChecks": {}}
    )
    quality_policy_matrix = (
        run_generic_quality_policy_matrix_evidence(method_registry_path)
        if mode == "playwright"
        else {"status": "NOT_RUN", "command": [], "exitCode": None, "stdout": "", "stderr": "", "evidenceChecks": {}}
    )
    recode_method_matrix = (
        run_generic_recode_method_matrix_evidence(recode_registry_path)
        if mode == "playwright"
        else {"status": "NOT_RUN", "command": [], "exitCode": None, "stdout": "", "stderr": "", "evidenceChecks": {}}
    )

    if mode == "source":
        overall = "SOURCE_VALIDATION_PASS" if all(item["status"] == "SOURCE_VALIDATION_PASS" for item in entries) else "SOURCE_VALIDATION_FAIL"
    else:
        statuses = {item["status"] for item in entries}
        if (
            not entries
            or "FAIL" in statuses
            or method_matrix["status"] != "PASS"
            or quality_policy_matrix["status"] != "PASS"
            or recode_method_matrix["status"] != "PASS"
        ):
            overall = "FAIL"
        elif "PARTIAL" in statuses:
            overall = "PARTIAL"
        else:
            overall = "PASS"

    return {
        "format": "PSY_SCALE_REGRESSION_REPORT",
        "schemaVersion": 1,
        "runId": run_id,
        "generatedAt": utc_now(),
        "mode": "SOURCE_VALIDATION_ONLY" if mode == "source" else "PLAYWRIGHT_SELECTOR",
        "registry": str(registry_path.relative_to(ROOT)),
        "registrySha256": registry_fingerprint(registry),
        "genericScoreMethodRegistry": str(method_registry_path.relative_to(ROOT)),
        "genericScoreMethodRegistrySha256": hashlib.sha256(method_registry_path.read_bytes()).hexdigest(),
        "genericRecodeMethodRegistry": str(recode_registry_path.relative_to(ROOT)),
        "genericRecodeMethodRegistrySha256": hashlib.sha256(recode_registry_path.read_bytes()).hexdigest(),
        "overallStatus": overall,
        "scope": {
            "registeredEntries": len(registry["scales"]),
            "technicalRegressionEntries": len(entries),
            "android": "EXCLUDED",
            "clinicalApproval": "NOT_ESTABLISHED_BY_THIS_RUN",
            "postgresIsolation": "CALLER_MUST_PROVIDE_ISOLATED_ENVIRONMENT",
            "genericScoreMethodMatrix": "SYNTHETIC_TECHNICAL_FIXTURE_ONLY",
            "genericMissingAnswerPolicyMatrix": "SYNTHETIC_TECHNICAL_FIXTURE_ONLY",
            "genericRecodeMethodMatrix": "SYNTHETIC_TECHNICAL_FIXTURE_ONLY",
        },
        "genericScoreMethodMatrix": method_matrix,
        "genericQualityPolicyMatrix": quality_policy_matrix,
        "genericRecodeMethodMatrix": recode_method_matrix,
        "entries": entries,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    parser.add_argument(
        "--mode",
        choices=("source", "playwright"),
        default="source",
        help="source validates package material only; playwright also runs each declared Playwright selector",
    )
    parser.add_argument("--report", type=Path, help="optional JSON report output path")
    args = parser.parse_args()

    registry_path = args.registry if args.registry.is_absolute() else ROOT / args.registry
    registry_path = registry_path.resolve()
    try:
        registry = validate_registry(registry_path)
        report = build_report(registry_path, registry, args.mode)
    except (OSError, ValueError, RegistryError, subprocess.SubprocessError) as exc:
        print(f"Scale adaptation registry run failed before execution: {exc}", file=sys.stderr)
        return 2

    if args.report:
        report_path = args.report if args.report.is_absolute() else ROOT / args.report
        report_path.parent.mkdir(parents=True, exist_ok=True)
        report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"Scale adaptation registry run {report['runId']}: {report['overallStatus']}")
    print(f"Mode: {report['mode']}; entries: {len(report['entries'])}")
    if args.mode == "playwright":
        print(
            "Generic score-method matrix: "
            f"{report['genericScoreMethodMatrix']['status']}"
        )
        print(
            "Generic missing-answer policy matrix: "
            f"{report['genericQualityPolicyMatrix']['status']}"
        )
        print(
            "Generic dimension/time recode matrix: "
            f"{report['genericRecodeMethodMatrix']['status']}"
        )
    for item in report["entries"]:
        print(
            f"- {item['taskId']} {item['scaleCode']}@{item['versionNo']}: "
            f"{item['status']} (runtime checks not executed: {item['runtimeChecksNotExecuted']})"
        )
    if args.mode == "source":
        print("This is not a PostgreSQL/scoring/Web/report or clinical approval result.")
    elif report["overallStatus"] == "PASS":
        print("All registered required checks passed; PostgreSQL isolation and clinical approval remain caller/governance responsibilities.")
    elif report["overallStatus"] == "PARTIAL":
        print("Some registered packages are partial because required checks are blocked or not applicable; inspect missingRequiredChecks.")
    return 0 if report["overallStatus"] in ("SOURCE_VALIDATION_PASS", "PASS", "PARTIAL") else 1


if __name__ == "__main__":
    raise SystemExit(main())
