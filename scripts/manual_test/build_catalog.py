#!/usr/bin/env python3
"""Build the harness case catalog from the manual-test procedure documents.

The catalog (``build/reports/manual-test/cases.json``) is what
``run_api_suite.py`` executes.  Every ``| MT-… |`` table row in doc/30 (detail
procedure) and doc/31 (full-coverage procedure) becomes one case entry so the
case set cannot drift from the written procedure.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = [
    ROOT / "doc" / "30-manual-test-procedure.md",
    ROOT / "doc" / "31-manual-test-procedure-full.md",
]
OUT_FILE = ROOT / "build" / "reports" / "manual-test" / "cases.json"

CASE_ROW = re.compile(r"^\|\s*(MT-[A-Z0-9]+-\d+)\s*\|(.*)\|\s*$")
CASE_HEADING = re.compile(r"^#{3,4}\s+(MT-[A-Z0-9]+-\d+)\s+(.*)$")
ENDPOINT = re.compile(r"(GET|POST|PUT|DELETE|PATCH)\s+(`?/[^`|;]+)`?")


def cells(line: str) -> list[str]:
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def build_cases() -> list[dict[str, object]]:
    cases: dict[str, dict[str, object]] = {}
    for source in SOURCES:
        if not source.exists():
            continue
        for line in source.read_text(encoding="utf-8").splitlines():
            match = CASE_ROW.match(line)
            if not match:
                heading = CASE_HEADING.match(line)
                if heading:
                    case_id, title = heading.group(1), heading.group(2).strip()
                    cases.setdefault(
                        case_id,
                        {
                            "id": case_id,
                            "module": case_id.split("-")[1],
                            "title": title[:300],
                            "priority": "",
                            "steps": "见文档小节",
                            "expected": title[:300],
                            "endpoints": [f"{method} {path.strip('`')}" for method, path in ENDPOINT.findall(line)],
                            "source": source.name,
                        },
                    )
                continue
            case_id = match.group(1)
            row = cells(line)
            # row = [id, priority?, ...payload..., expected]
            payload = row[1:]
            title = next((cell for cell in payload if cell and not re.fullmatch(r"P\d", cell)), "")
            expected = payload[-1] if len(payload) > 1 else ""
            steps = payload[-2] if len(payload) > 2 else ""
            endpoints = [f"{method} {path.strip('`')}" for method, path in ENDPOINT.findall(line)]
            module = case_id.split("-")[1]
            cases.setdefault(
                case_id,
                {
                    "id": case_id,
                    "module": module,
                    "title": title[:300],
                    "priority": payload[0] if payload and re.fullmatch(r"P\d", payload[0]) else "",
                    "steps": steps[:500],
                    "expected": expected[:500],
                    "endpoints": endpoints,
                    "source": source.name,
                },
            )
    return sorted(cases.values(), key=lambda item: (str(item["module"]), str(item["id"])))


def main() -> int:
    cases = build_cases()
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    modules: dict[str, int] = {}
    for case in cases:
        modules[str(case["module"])] = modules.get(str(case["module"]), 0) + 1
    print(f"wrote {len(cases)} cases -> {OUT_FILE.relative_to(ROOT)}")
    for module in sorted(modules):
        print(f"  {module}: {modules[module]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
