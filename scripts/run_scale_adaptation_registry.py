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
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from validate_scale_adaptation_registry import RegistryError, ROOT, validate_registry


DEFAULT_REGISTRY = ROOT / "doc/scale-packages/scale-adaptation-registry.json"
CHECK_TARGETS = {
    "source_package_integrity": "sourcePackage",
    "golden_case_scores": "goldenCases",
    "scoring_trace": "scoringTrace",
    "trilingual_result_content": "trilingualResultContent",
    "report_semantics": "reportSemantics",
    "task_version_lock": "taskVersionLock",
    "historical_result_immutability": "historicalResultImmutability",
    "idempotent_submission": "idempotentSubmission",
    "concurrent_submission": "concurrentSubmission",
    "rescore_history": "rescoreHistory",
}


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def registry_fingerprint(registry: dict[str, Any]) -> str:
    """Hash immutable regression inputs, not mutable last-run bookkeeping."""
    fingerprint = json.loads(json.dumps(registry, ensure_ascii=False))
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
    return {
        "status": "PASS" if completed.returncode == 0 else "FAIL",
        "command": command,
        "exitCode": completed.returncode,
        "stdout": completed.stdout.strip(),
        "stderr": completed.stderr.strip(),
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
        registry_variables = [
            "-v", f"version_no={entry['versionNo']}",
            "-v", f"task_prefix={closure['taskNamePrefix']}",
            "-v", f"expected_total={closure_expected['totalScore']}",
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


def build_report(registry_path: Path, registry: dict[str, Any], mode: str) -> dict[str, Any]:
    run_id = f"REG-{mode.upper()}-{datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')}"
    entries: list[dict[str, Any]] = []

    for entry in registry["scales"]:
        if not entry["runInTechnicalRegression"]:
            continue

        source_result = run_source_validator(entry)
        checks: dict[str, Any] = {
            "sourcePackage": source_result,
            "goldenCases": {"status": "NOT_RUN"},
            "postgres": {"status": "NOT_RUN"},
            "scoringTrace": {"status": "NOT_RUN"},
            "trilingualResultContent": {"status": "NOT_RUN"},
            "reportSemantics": {"status": "NOT_RUN"},
            "taskVersionLock": {"status": "NOT_RUN"},
            "historicalResultImmutability": {"status": "NOT_RUN"},
            "idempotentSubmission": {"status": "NOT_RUN"},
            "concurrentSubmission": {"status": "NOT_RUN"},
            "rescoreHistory": {"status": "NOT_RUN"},
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
            "trilingual_result_content": "trilingualResultContent",
            "report_semantics": "reportSemantics",
            "task_version_lock": "taskVersionLock",
            "historical_result_immutability": "historicalResultImmutability",
            "idempotent_submission": "idempotentSubmission",
            "concurrent_submission": "concurrentSubmission",
            "rescore_history": "rescoreHistory",
        }
        for evidence_key, target_key in evidence_mapping.items():
            evidence_status = checks["postgres"].get("evidenceChecks", {}).get(evidence_key)
            if evidence_status == "PASS":
                checks[target_key] = {"status": "PASS", "source": "postgresEvidenceScript"}

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

    if mode == "source":
        overall = "SOURCE_VALIDATION_PASS" if all(item["status"] == "SOURCE_VALIDATION_PASS" for item in entries) else "SOURCE_VALIDATION_FAIL"
    else:
        statuses = {item["status"] for item in entries}
        if not entries or "FAIL" in statuses:
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
        "overallStatus": overall,
        "scope": {
            "registeredEntries": len(registry["scales"]),
            "technicalRegressionEntries": len(entries),
            "android": "EXCLUDED",
            "clinicalApproval": "NOT_ESTABLISHED_BY_THIS_RUN",
            "postgresIsolation": "CALLER_MUST_PROVIDE_ISOLATED_ENVIRONMENT",
        },
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
