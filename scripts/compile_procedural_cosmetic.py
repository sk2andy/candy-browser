#!/usr/bin/env python3
"""Compile a small declarative uBO procedural-cosmetic subset for Candy Browser."""

from __future__ import annotations

import argparse
import base64
import re
from dataclasses import dataclass
from pathlib import Path

from compile_advanced_filters import canonical_host_pattern
from compile_easylist_cosmetic import is_safe_standard_selector


FORMAT_HEADER = "candy-procedural-cosmetic:1"
HAS_TEXT_REMOVE = re.compile(r"^(.*):has-text\(([^()]*)\):remove\(\)$", re.IGNORECASE)
HAS_TEXT = re.compile(r"^(.*):has-text\(([^()]*)\)$", re.IGNORECASE)
REMOVE = re.compile(r"^(.*):remove\(\)$", re.IGNORECASE)
SAFE_TEXT = re.compile(r"^[\w\s.,:;!?%€$£¥'\"()\-/]{1,128}$", re.UNICODE)


@dataclass(frozen=True, order=True)
class ProceduralRecord:
    action: str
    host_pattern: str
    selector: str
    text: str
    ignore_case: bool


@dataclass
class CompileStats:
    hide_rules: int = 0
    remove_rules: int = 0
    unsupported_rules: int = 0
    conditional_lines: int = 0


def parse_text(raw: str) -> tuple[str, bool] | None:
    value = raw.strip()
    ignore_case = False
    if value.startswith("/"):
        end = value.rfind("/")
        if end <= 0 or value[end + 1 :] not in ("", "i"):
            return None
        ignore_case = value[end + 1 :] == "i"
        value = value[1:end]
        if any(character in value for character in ".[]{}*+?\\^$|"):
            return None
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
        value = value[1:-1]
    if SAFE_TEXT.fullmatch(value) is None:
        return None
    return value, ignore_case


def parse_line(value: str) -> list[ProceduralRecord]:
    marker = value.find("##")
    if marker <= 0 or "#@#" in value:
        return []
    domain_part = value[:marker]
    expression = value[marker + 2 :].strip()
    action: str
    selector: str
    text = ""
    ignore_case = False
    match = HAS_TEXT_REMOVE.fullmatch(expression)
    if match is not None:
        action = "R"
        selector = match.group(1).strip()
        parsed_text = parse_text(match.group(2))
        if parsed_text is None:
            return []
        text, ignore_case = parsed_text
    else:
        match = HAS_TEXT.fullmatch(expression)
        if match is not None:
            action = "H"
            selector = match.group(1).strip()
            parsed_text = parse_text(match.group(2))
            if parsed_text is None:
                return []
            text, ignore_case = parsed_text
        else:
            match = REMOVE.fullmatch(expression)
            if match is None:
                return []
            action = "R"
            selector = match.group(1).strip()
    if not is_safe_standard_selector(selector):
        return []
    hosts: set[str] = set()
    for raw_domain in domain_part.split(","):
        if raw_domain.startswith("~"):
            return []
        host = canonical_host_pattern(raw_domain)
        if host is None:
            return []
        hosts.add(host)
    return [
        ProceduralRecord(action, host, selector, text, ignore_case)
        for host in sorted(hosts)
    ]


def compile_sources(source_paths: list[Path]) -> tuple[list[ProceduralRecord], CompileStats]:
    records: set[ProceduralRecord] = set()
    stats = CompileStats()
    for source_path in source_paths:
        conditional_depth = 0
        for raw in source_path.read_text(encoding="utf-8").splitlines():
            value = raw.strip().lstrip("\ufeff")
            if value.startswith("!#if"):
                conditional_depth += 1
                continue
            if value.startswith("!#endif"):
                conditional_depth = max(conditional_depth - 1, 0)
                continue
            if conditional_depth:
                stats.conditional_lines += 1
                continue
            if ":has-text(" not in value and ":remove()" not in value:
                continue
            parsed = parse_line(value)
            if not parsed:
                stats.unsupported_rules += 1
                continue
            records.update(parsed)
        if conditional_depth:
            raise ValueError(f"Unclosed conditional section in {source_path}")
    ordered = sorted(records)
    stats.hide_rules = sum(record.action == "H" for record in ordered)
    stats.remove_rules = sum(record.action == "R" for record in ordered)
    return ordered, stats


def encode(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii").rstrip("=")


def maximum_bucket(records: list[ProceduralRecord]) -> tuple[str, int]:
    counts: dict[str, int] = {}
    for record in records:
        counts[record.host_pattern] = counts.get(record.host_pattern, 0) + 1
    return max(counts.items(), key=lambda item: (item[1], item[0]), default=("-", 0))


def write_asset(
    output: Path,
    revision: str,
    records: list[ProceduralRecord],
    stats: CompileStats,
) -> None:
    lines = [
        FORMAT_HEADER,
        "# Generated bounded uAssets procedural cosmetic subset. Do not edit by hand.",
        "# Source: https://github.com/uBlockOrigin/uAssets",
        f"# Source revision: {revision}",
        "# License: GPL-3.0; see uassets.LICENSE.txt",
        f"# Rules: {len(records)}",
        f"# Text-hide rules: {stats.hide_rules}",
        f"# Remove rules: {stats.remove_rules}",
        f"# Maximum host bucket: {maximum_bucket(records)[0]} {maximum_bucket(records)[1]}",
        f"# Skipped unsupported rules: {stats.unsupported_rules}",
        f"# Skipped conditional lines: {stats.conditional_lines}",
    ]
    for record in sorted(records):
        lines.append(
            "\t".join(
                (
                    record.action,
                    record.host_pattern,
                    encode(record.selector),
                    encode(record.text) if record.text else "-",
                    "i" if record.ignore_case else "s",
                )
            )
        )
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-file", action="append", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--min-rules", type=int, default=20)
    args = parser.parse_args()
    records, stats = compile_sources(args.source_file)
    if len(records) < args.min_rules:
        raise SystemExit(f"Refusing procedural update: only {len(records)} rules found")
    bucket_name, bucket_size = maximum_bucket(records)
    if len(records) > 5_000 or bucket_size > 64:
        raise SystemExit(
            f"Refusing procedural update: {len(records)} rules / "
            f"bucket {bucket_name} has {bucket_size} rules"
        )
    write_asset(args.output, args.revision, records, stats)
    print(
        f"Wrote {stats.hide_rules} text-hide and {stats.remove_rules} remove rules; "
        f"skipped {stats.unsupported_rules} unsupported"
    )


if __name__ == "__main__":
    main()
