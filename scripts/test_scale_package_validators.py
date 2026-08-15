#!/usr/bin/env python3
"""Focused regression tests for the machine-readable scale validators."""

from __future__ import annotations

import copy
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))

import validate_generic_scale_package  # noqa: E402
import validate_scale_adaptation_registry  # noqa: E402


REGISTRY_PATH = ROOT / "doc/scale-packages/scale-adaptation-registry.json"
PHQ9_PATH = ROOT / "doc/scale-packages/phq9-v1-source-draft.json"
SCL90_PATH = ROOT / "doc/scale-packages/scl90-v2-source-technical.json"


class ScalePackageValidatorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8"))
        cls.profile_map = validate_scale_adaptation_registry.capability_profiles()

    def assert_registry_entry_rejected(self, entry: dict) -> None:
        with self.assertRaises(validate_scale_adaptation_registry.RegistryError):
            validate_scale_adaptation_registry.validate_entry(entry, 0, self.profile_map)

    def assert_generic_package_rejected(self, package: dict) -> None:
        with self.assertRaises(SystemExit):
            validate_generic_scale_package.validate(package)

    def test_registry_current_state_and_restricted_profile_are_valid(self) -> None:
        validated = validate_scale_adaptation_registry.validate_registry(REGISTRY_PATH)
        self.assertIn("SCL90_RESTRICTED_PROFILE", self.profile_map)
        self.assertEqual(len(validated["scales"]), 8)

    def test_formal_support_requires_formal_governance(self) -> None:
        entry = copy.deepcopy(self.registry["scales"][0])
        entry["supportStatus"] = "FULLY_SUPPORTED"
        entry["governanceStatus"] = "BLOCKED_EXTERNAL"
        self.assert_registry_entry_rejected(entry)

    def test_registry_rejects_unknown_template_duplicate_check_and_stale_evidence(self) -> None:
        entry = copy.deepcopy(self.registry["scales"][0])

        entry["reportTemplate"] = "NOT_A_SUPPORTED_TEMPLATE"
        self.assert_registry_entry_rejected(entry)

        entry = copy.deepcopy(self.registry["scales"][0])
        entry["requiredChecks"].append("question_display")
        self.assert_registry_entry_rejected(entry)

        entry = copy.deepcopy(self.registry["scales"][0])
        entry["lastIndependentTechnicalEvidence"]["runId"] = "REG-PLAYWRIGHT-STALE"
        self.assert_registry_entry_rejected(entry)

        entry = copy.deepcopy(self.registry["scales"][0])
        entry["lastRegistryRegression"] = {"status": "NOT_RUN", "runId": None}
        self.assert_registry_entry_rejected(entry)

    def test_registry_cross_checks_profile_catalog(self) -> None:
        entry = copy.deepcopy(self.registry["scales"][3])
        entry["technicalClosure"]["profile"] = "SPECIALIZED_NORM_UNSUPPORTED"
        self.assert_registry_entry_rejected(entry)

    def test_generic_validator_rejects_cross_dimension_duplicate(self) -> None:
        package = json.loads(PHQ9_PATH.read_text(encoding="utf-8"))
        package["dimensions"].append(
            {
                "dimensionCode": "DUPLICATE_Q1",
                "questionNos": [1],
                "translations": copy.deepcopy(package["dimensions"][0]["translations"]),
            }
        )
        self.assert_generic_package_rejected(package)

    def test_generic_validator_rejects_unknown_metric_and_template(self) -> None:
        package = json.loads(PHQ9_PATH.read_text(encoding="utf-8"))
        package["scoring"]["indices"] = {"ARBITRARY_METRIC": "not executable"}
        self.assert_generic_package_rejected(package)

        package = json.loads(PHQ9_PATH.read_text(encoding="utf-8"))
        package["scale"]["reportTemplate"] = "NORMATIVE_PROFILE"
        self.assert_generic_package_rejected(package)

    def test_scl90_validator_rejects_missing_quality_metadata(self) -> None:
        package = json.loads(SCL90_PATH.read_text(encoding="utf-8"))
        package["scale"].pop("qualityPolicy")
        with tempfile.TemporaryDirectory(prefix="scl90-validator-") as directory:
            candidate = Path(directory) / "scl90.json"
            candidate.write_text(json.dumps(package, ensure_ascii=False), encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(SCRIPTS / "validate_scl90_source_package.py"), str(candidate)],
                cwd=ROOT,
                capture_output=True,
                text=True,
                check=False,
            )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("quality policy", result.stderr + result.stdout)


if __name__ == "__main__":
    unittest.main()
