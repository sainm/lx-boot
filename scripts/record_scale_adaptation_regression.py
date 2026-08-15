#!/usr/bin/env python3
"""Record a verified full-regression run in the mutable registry bookkeeping.

The immutable registry fingerprint deliberately excludes last-run bookkeeping,
but a current report must still be reflected by every active ScalePackage. This
command is called only after the wrapper has completed its business, core,
publication and observability checks and has removed the temporary schema.
It refuses to update the registry unless the report itself passes the full
machine-readable report verifier, and writes the registry atomically.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from run_scale_adaptation_registry import registry_fingerprint
from validate_scale_adaptation_registry import RegistryError, validate_registry
from verify_scale_adaptation_regression_report import ReportError, verify


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_REGISTRY = ROOT / "doc" / "scale-packages" / "scale-adaptation-registry.json"


class PointerRecordError(Exception):
    """Raised when a report cannot safely update registry bookkeeping."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise PointerRecordError(message)


def utc_date() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def resolve_path(path: Path) -> Path:
    return path if path.is_absolute() else ROOT / path


def record(report_path: Path, registry_path: Path) -> dict[str, Any]:
    report_path = resolve_path(report_path).resolve()
    registry_path = resolve_path(registry_path).resolve()
    require(report_path.is_file(), f"regression report is missing: {report_path}")
    require(registry_path.is_file(), f"registry is missing: {registry_path}")

    # The pre-update verification proves every active entry has a complete PASS
    # report and that the report's immutable input fingerprint is current.
    report = verify(report_path, registry_path)
    run_id = report["runId"]
    require(report["overallStatus"] == "PASS", "only an overall PASS report may update registry pointers")
    active_tasks = {
        entry["taskId"]
        for entry in report["entries"]
        if isinstance(entry, dict) and isinstance(entry.get("taskId"), str)
    }

    registry = validate_registry(registry_path)
    before_fingerprint = registry_fingerprint(registry)
    updated_tasks: list[str] = []
    verified_at = utc_date()
    for entry in registry["scales"]:
        if entry.get("runInTechnicalRegression") is not True:
            continue
        task_id = entry["taskId"]
        require(task_id in active_tasks, f"report does not cover active registry task: {task_id}")
        entry["lastRegistryRegression"] = {
            "runId": run_id,
            "status": "PASS",
        }
        evidence = entry.get("lastIndependentTechnicalEvidence")
        require(isinstance(evidence, dict), f"{task_id}.lastIndependentTechnicalEvidence is required")
        evidence["runId"] = run_id
        evidence["status"] = "PASS"
        evidence["verifiedAt"] = verified_at
        updated_tasks.append(task_id)

    require(updated_tasks and set(updated_tasks) == active_tasks, "registry/report active task scope does not match")
    after_fingerprint = registry_fingerprint(registry)
    require(before_fingerprint == after_fingerprint, "mutable pointer recording changed immutable registry inputs")

    original_mode = registry_path.stat().st_mode & 0o777
    with tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        dir=registry_path.parent,
        prefix=f".{registry_path.name}.",
        suffix=".tmp",
        delete=False,
    ) as temporary:
        temporary_path = Path(temporary.name)
        temporary.write(json.dumps(registry, ensure_ascii=False, indent=2) + "\n")
        temporary.flush()
        os.fsync(temporary.fileno())
    os.chmod(temporary_path, original_mode)
    try:
        os.replace(temporary_path, registry_path)
    finally:
        temporary_path.unlink(missing_ok=True)

    updated_registry = validate_registry(registry_path)
    for entry in updated_registry["scales"]:
        if entry.get("runInTechnicalRegression") is not True:
            continue
        require(entry["lastRegistryRegression"]["runId"] == run_id, f"{entry['taskId']} registry pointer was not recorded")
        require(entry["lastIndependentTechnicalEvidence"]["runId"] == run_id, f"{entry['taskId']} evidence pointer was not recorded")
    require(registry_fingerprint(updated_registry) == before_fingerprint, "post-write registry fingerprint changed")
    return {"runId": run_id, "updatedTasks": updated_tasks, "registrySha256": before_fingerprint}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", required=True, type=Path)
    parser.add_argument("--registry", type=Path, default=DEFAULT_REGISTRY)
    args = parser.parse_args()
    try:
        result = record(args.report, args.registry)
    except (OSError, ValueError, KeyError, RegistryError, ReportError, PointerRecordError) as error:
        print(f"SCALE_ADAPTATION_POINTER_RECORD_FAILED: {error}", file=sys.stderr)
        return 1
    print(
        "SCALE_ADAPTATION_POINTERS_RECORDED|"
        f"run={result['runId']}|active={len(result['updatedTasks'])}|"
        f"registrySha256={result['registrySha256']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
