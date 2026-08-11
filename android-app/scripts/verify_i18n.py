#!/usr/bin/env python3
"""Verify Android locale parity and reject source-level UI translation shortcuts."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES_ROOT = ROOT / "app" / "src" / "main" / "res"
SOURCE_ROOT = ROOT / "app" / "src" / "main" / "java"
LOCALES = ("values", "values-ja", "values-zh-rCN")
HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])")
FORBIDDEN_TRANSLATION_MECHANISMS = (
    "localizedUiText",
    "androidJapaneseText",
    "androidEnglishText",
)
UI_LITERAL = re.compile(
    r"\b(?:Text|GradientHeader|MetricCard|QuickActionCard|SectionCard|EmptyHint|"
    r"FullscreenLoading|ErrorFullScreen|StatusPill)\s*\(\s*\"([^\"\n]*)\""
)


def load_strings(folder: str) -> dict[str, str]:
    values: dict[str, str] = {}
    for path in sorted((RES_ROOT / folder).glob("*.xml")):
        root = ET.parse(path).getroot()
        for element in root.findall("string"):
            name = element.attrib["name"]
            if name in values:
                raise ValueError(f"{folder}: duplicate string resource {name!r}")
            values[name] = "".join(element.itertext())
    return values


def placeholders(value: str) -> list[tuple[str, str]]:
    result: list[tuple[str, str]] = []
    implicit_index = 1
    for match in PLACEHOLDER.finditer(value):
        kind = match.group(2)
        if kind == "%":
            continue
        index = match.group(1) or str(implicit_index)
        result.append((index, kind.lower()))
        implicit_index += 1
    return sorted(result)


def main() -> int:
    errors: list[str] = []
    try:
        locale_strings = {locale: load_strings(locale) for locale in LOCALES}
    except (ET.ParseError, ValueError) as exc:
        print(f"Android i18n verification failed: {exc}", file=sys.stderr)
        return 1

    base = locale_strings["values"]
    for locale in LOCALES[1:]:
        localized = locale_strings[locale]
        missing = sorted(set(base) - set(localized))
        extra = sorted(set(localized) - set(base))
        if missing:
            errors.append(f"{locale}: missing keys: {', '.join(missing)}")
        if extra:
            errors.append(f"{locale}: unexpected keys: {', '.join(extra)}")
        for key in sorted(set(base) & set(localized)):
            if placeholders(base[key]) != placeholders(localized[key]):
                errors.append(
                    f"{locale}: placeholder mismatch for {key}: "
                    f"{placeholders(base[key])} != {placeholders(localized[key])}"
                )

    for path in sorted(SOURCE_ROOT.rglob("*.kt")) + sorted(SOURCE_ROOT.rglob("*.java")):
        source = path.read_text(encoding="utf-8")
        relative = path.relative_to(ROOT)
        for marker in FORBIDDEN_TRANSLATION_MECHANISMS:
            if marker in source:
                errors.append(f"{relative}: forbidden translation mechanism {marker!r}")
        for line_no, line in enumerate(source.splitlines(), start=1):
            if HAN.search(line):
                errors.append(f"{relative}:{line_no}: hard-coded Han text outside Android resources")
            for match in UI_LITERAL.finditer(line):
                literal = re.sub(r"\$\{[^}]+}", "", match.group(1))
                if re.search(r"[A-Za-z]", literal):
                    errors.append(f"{relative}:{line_no}: hard-coded user-visible text {match.group(1)!r}")

    if errors:
        print("Android i18n verification failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        "Android i18n verification passed: "
        f"{len(base)} keys across {', '.join(LOCALES)}; "
        "no hard-coded Han UI text or translation maps."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
