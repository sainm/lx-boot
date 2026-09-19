#!/usr/bin/env python3
"""Run the API/DB portion of the manual-test procedure."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import harness  # noqa: E402
import checks_account_security  # noqa: E402,F401  (registers cases)
import checks_scoring  # noqa: E402,F401  (registers cases)
import checks_notify_ops  # noqa: E402,F401  (registers cases)
import checks_reports_misc  # noqa: E402,F401  (registers cases)
import checks_remaining3  # noqa: E402,F401  (registers cases)
import checks_answering  # noqa: E402,F401  (registers cases)
import checks_last  # noqa: E402,F401  (registers cases)
import checks_full_coverage  # noqa: E402,F401  (registers the MT-API cases)
import checks_network  # noqa: E402,F401  (registers the MT-NET external-channel cases)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", default="", help="comma separated module prefixes, e.g. AUTH,USER")
    parser.add_argument("--only", default="", help="comma separated case ids")
    args = parser.parse_args()
    modules = [item.strip() for item in args.modules.split(",") if item.strip()]
    only = [item.strip() for item in args.only.split(",") if item.strip()]
    return harness.run_cases(modules, only)


if __name__ == "__main__":
    raise SystemExit(main())
