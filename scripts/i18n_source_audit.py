#!/usr/bin/env python3
"""Static audit for user-visible strings that bypass the i18n catalogs.

Scans ``admin-web/src`` for CJK literals outside the message catalogs and
reports every hit with file/line so the page sweep can confirm the impact at
runtime.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "admin-web" / "src"
SKIP_PARTS = {"i18n", "__tests__"}
SKIP_SUFFIXES = (".test.ts", ".test.tsx", ".d.ts")
CJK = re.compile(r"[\u4e00-\u9fff\u3040-\u30ff]")
LITERAL = re.compile(r"\"[^\"]*[\u4e00-\u9fff\u3040-\u30ff][^\"]*\"|'[^']*[\u4e00-\u9fff\u3040-\u30ff][^']*'")
ALLOWED_PATTERNS = (
    r"\bt\(\s*$",  # literal is the first argument of the translator
    r"\bt\(\s*\"?$",
    r"translateEnum\(",
    r"messageKeys\(",
)


def is_comment(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith(("//", "*", "/*", "<!--"))


def main() -> int:
    findings: list[tuple[str, int, str]] = []
    for path in sorted(SRC.rglob("*")):
        if path.suffix not in {".ts", ".tsx"} or path.name.endswith(SKIP_SUFFIXES):
            continue
        if SKIP_PARTS & set(path.parts):
            continue
        lines = path.read_text(encoding="utf-8").splitlines()
        for number, line in enumerate(lines, start=1):
            if not CJK.search(line) or is_comment(line):
                continue
            for literal in LITERAL.finditer(line):
                prefix = line[: literal.start()]
                # ``t("...")`` puts the translator immediately before the literal.
                if re.search(r"\bt\(\s*$", prefix) or re.search(r"translateEnum\([^)]*$", prefix):
                    continue
                findings.append((str(path.relative_to(ROOT)), number, line.strip()))
                break

    print(f"hardcoded CJK literals: {len(findings)}")
    for path, number, line in findings:
        print(f"  {path}:{number}: {line[:150]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
