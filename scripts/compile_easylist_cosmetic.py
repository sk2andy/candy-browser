#!/usr/bin/env python3
"""Compile Candy's supported EasyList/EasyPrivacy cosmetic subset.

Only domain-specific, standard CSS element-hiding rules are emitted. Generic rules, procedural
operators, scriptlets, response-header filters, and malformed domains fail closed.
"""

from __future__ import annotations

import argparse
import base64
import re
from dataclasses import dataclass
from pathlib import Path


FORMAT_HEADER = "candy-easylist-cosmetic:1"
UASSETS_FORMAT_HEADER = "candy-uassets-cosmetic:1"
INCLUDE_PATTERN = re.compile(r"%include easylist:([^%]+)%")
SAFE_LABEL = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
EXTENDED_CSS = (
    "+js(",
    ":has-text(",
    ":matches-css(",
    ":matches-css-before(",
    ":matches-css-after(",
    ":matches-attr(",
    ":matches-path(",
    ":min-text-length(",
    ":others(",
    ":remove(",
    ":remove-attr(",
    ":remove-class(",
    ":style(",
    ":upward(",
    ":watch-attr(",
    ":xpath(",
    ":if(",
    ":if-not(",
    ":-abp-",
)
UNSAFE_CSS = ("{", "}", "<", "@import", "javascript:", "url(", "expression(")
MAX_HIDE_RULES_PER_PATTERN = 256
MAX_SELECTOR_BYTES_PER_PATTERN = 128 * 1_024
MAX_RESOLVED_HIDE_RULES = 256
MAX_RESOLVED_SELECTOR_BYTES = 96 * 1_024


@dataclass
class CompileStats:
    source_lines: int = 0
    hide_rules: int = 0
    exception_rules: int = 0
    generic_rules: int = 0
    unsupported_rules: int = 0
    conditional_lines: int = 0


def canonical_pattern(raw: str) -> str | None:
    value = raw.strip().lower().rstrip(".")
    if value.endswith(".*"):
        fixed = value[:-2]
        labels = fixed.split(".")
        if not labels or any(not SAFE_LABEL.fullmatch(label) for label in labels):
            return None
        return value
    if "*" in value:
        return None
    try:
        ascii_value = value.encode("idna").decode("ascii")
    except UnicodeError:
        return None
    labels = ascii_value.split(".")
    if len(labels) < 2 or len(ascii_value) > 253:
        return None
    if any(not SAFE_LABEL.fullmatch(label) for label in labels):
        return None
    if all(character.isdigit() or character == "." for character in ascii_value):
        return None
    return ascii_value


def is_safe_standard_selector(selector: str) -> bool:
    if not selector or len(selector) > 2_048 or selector.startswith("^"):
        return False
    if any(ord(character) < 32 or ord(character) == 127 for character in selector):
        return False
    lower = selector.lower()
    return not any(token in lower for token in EXTENDED_CSS + UNSAFE_CSS)


def parse_filter_line(line: str, stats: CompileStats) -> list[tuple[str, str, tuple[str, ...], str]]:
    value = line.strip().lstrip("\ufeff")
    if not value or value.startswith("!") or value.startswith("["):
        return []
    stats.source_lines += 1
    delimiters = [
        (index, marker)
        for marker in ("##", "#@#", "#?#", "#$#", "#%#")
        if (index := value.find(marker)) >= 0
    ]
    if not delimiters:
        return []
    marker_index, marker = min(delimiters)
    if marker in ("#?#", "#$#", "#%#"):
        stats.unsupported_rules += 1
        return []
    domain_part = value[:marker_index]
    selector = value[marker_index + len(marker):]
    selector = selector.strip()
    if not is_safe_standard_selector(selector):
        stats.unsupported_rules += 1
        return []
    if not domain_part:
        stats.generic_rules += 1
        return []

    positives: list[str] = []
    negatives: list[str] = []
    for raw_domain in domain_part.split(","):
        excluded = raw_domain.startswith("~")
        pattern = canonical_pattern(raw_domain[1:] if excluded else raw_domain)
        if pattern is None:
            stats.unsupported_rules += 1
            return []
        (negatives if excluded else positives).append(pattern)
    if not positives:
        stats.generic_rules += 1
        return []
    if marker == "#@#" and negatives:
        stats.unsupported_rules += 1
        return []

    action = "A" if marker == "#@#" else "H"
    exclusions = tuple(sorted(set(negatives))) if action == "H" else ()
    return [(action, pattern, exclusions, selector) for pattern in sorted(set(positives))]


def template_sources(source_root: Path, template_names: list[str]) -> list[Path]:
    paths: set[Path] = set()
    for template_name in template_names:
        template = source_root / template_name
        for relative_path in INCLUDE_PATTERN.findall(template.read_text(encoding="utf-8")):
            source_path = source_root / relative_path
            if not source_path.is_file():
                raise ValueError(f"Missing template input: {relative_path}")
            paths.add(source_path)
    return sorted(paths)


def compile_sources(source_paths: list[Path]) -> tuple[list[tuple[str, str, tuple[str, ...], str]], CompileStats]:
    stats = CompileStats()
    records: set[tuple[str, str, tuple[str, ...], str]] = set()
    for source_path in source_paths:
        conditional_depth = 0
        for line in source_path.read_text(encoding="utf-8").splitlines():
            value = line.strip()
            if value.startswith("!#if"):
                conditional_depth += 1
                continue
            if value.startswith("!#endif"):
                conditional_depth = max(conditional_depth - 1, 0)
                continue
            if conditional_depth and value.startswith(("!#else", "!#elif")):
                continue
            if conditional_depth:
                stats.conditional_lines += 1
                continue
            records.update(parse_filter_line(line, stats))
        if conditional_depth:
            raise ValueError(f"Unclosed conditional section in {source_path}")
    ordered = sorted(records, key=lambda record: (record[0], record[1], record[3], record[2]))
    stats.hide_rules = sum(record[0] == "H" for record in ordered)
    stats.exception_rules = len(ordered) - stats.hide_rules
    return ordered, stats


def encode_selector(selector: str) -> str:
    return base64.urlsafe_b64encode(selector.encode("utf-8")).decode("ascii").rstrip("=")


def maximum_pattern_load(
    records: list[tuple[str, str, tuple[str, ...], str]],
) -> tuple[str, int, int]:
    counts: dict[str, int] = {}
    selector_bytes: dict[str, int] = {}
    for action, pattern, _, selector in records:
        if action != "H":
            continue
        counts[pattern] = counts.get(pattern, 0) + 1
        selector_bytes[pattern] = selector_bytes.get(pattern, 0) + len(selector.encode("utf-8"))
    pattern = max(counts, key=lambda value: (counts[value], selector_bytes[value], value))
    return pattern, counts[pattern], selector_bytes[pattern]


def wildcard_matches_representative(host: str, pattern: str) -> bool:
    prefix = pattern.removesuffix("*")
    marker = f".{prefix}"
    if host.startswith(prefix):
        return bool(host.removeprefix(prefix))
    if marker in host:
        return bool(host.split(marker, 1)[1])
    return False


def maximum_resolved_host_load(
    records: list[tuple[str, str, tuple[str, ...], str]],
) -> tuple[str, int, int]:
    exact: dict[str, set[str]] = {}
    wildcard: list[tuple[str, str]] = []
    for action, pattern, _, selector in records:
        if action != "H":
            continue
        if pattern.endswith(".*"):
            wildcard.append((pattern, selector))
        else:
            exact.setdefault(pattern, set()).add(selector)
    candidates = set(exact)
    candidates.update(pattern.replace(".*", ".com") for pattern, _ in wildcard)
    maximum = ("", 0, 0)
    for host in candidates:
        selectors: set[str] = set()
        candidate = host
        while True:
            selectors.update(exact.get(candidate, ()))
            if "." not in candidate:
                break
            candidate = candidate.split(".", 1)[1]
        selectors.update(
            selector
            for pattern, selector in wildcard
            if wildcard_matches_representative(host, pattern)
        )
        load = (host, len(selectors), sum(len(value.encode("utf-8")) for value in selectors))
        if (load[1], load[2], load[0]) > (maximum[1], maximum[2], maximum[0]):
            maximum = load
    return maximum


def write_asset(
    output: Path,
    revision: str,
    records: list[tuple[str, str, tuple[str, ...], str]],
    stats: CompileStats,
    asset_kind: str = "easylist",
) -> None:
    maximum_host, maximum_selectors, _ = maximum_resolved_host_load(records)
    if asset_kind == "uassets":
        header = UASSETS_FORMAT_HEADER
        description = "uAssets domain-specific cosmetic rules"
        source = "https://github.com/uBlockOrigin/uAssets"
        license_line = "GPL-3.0; see uassets.LICENSE.txt"
    else:
        header = FORMAT_HEADER
        description = "EasyList/EasyPrivacy domain-specific cosmetic rules"
        source = "https://github.com/easylist/easylist"
        license_line = "CC BY-SA 3.0 or later; see content_filter.LICENSE.txt"
    lines = [
        header,
        f"# Generated {description}. Do not edit by hand.",
        f"# Source: {source}",
        f"# Source revision: {revision}",
        f"# License: {license_line}",
        f"# Hide rules: {stats.hide_rules}",
        f"# Exception rules: {stats.exception_rules}",
        f"# Maximum hide rules for one pattern: {maximum_pattern_load(records)[1]}",
        f"# Maximum resolved host: {maximum_host}",
        f"# Maximum resolved hide selectors: {maximum_selectors}",
        f"# Skipped generic rules: {stats.generic_rules}",
        f"# Skipped unsupported rules: {stats.unsupported_rules}",
        f"# Skipped conditional lines: {stats.conditional_lines}",
    ]
    for action, pattern, exclusions, selector in records:
        lines.append(
            "\t".join((action, pattern, ",".join(exclusions) or "-", encode_selector(selector)))
        )
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--template", action="append")
    parser.add_argument("--source-file", action="append", type=Path)
    parser.add_argument("--asset-kind", choices=("easylist", "uassets"), default="easylist")
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--min-hide-rules", type=int, default=8_000)
    args = parser.parse_args()

    source_paths = list(args.source_file or [])
    if args.template:
        if args.source_root is None:
            parser.error("--source-root is required with --template")
        source_paths.extend(template_sources(args.source_root, args.template))
    if not source_paths:
        parser.error("at least one --template or --source-file is required")
    records, stats = compile_sources(source_paths)
    if stats.hide_rules < args.min_hide_rules:
        raise SystemExit(
            f"Refusing cosmetic update: only {stats.hide_rules} supported hide rules found"
        )
    max_pattern, max_rules, max_selector_bytes = maximum_pattern_load(records)
    if (
        max_rules > MAX_HIDE_RULES_PER_PATTERN
        or max_selector_bytes > MAX_SELECTOR_BYTES_PER_PATTERN
    ):
        raise SystemExit(
            "Refusing cosmetic update: pattern "
            f"{max_pattern} has {max_rules} hides / {max_selector_bytes} selector bytes"
        )
    max_host, max_resolved_rules, max_resolved_bytes = maximum_resolved_host_load(records)
    if (
        max_resolved_rules > MAX_RESOLVED_HIDE_RULES
        or max_resolved_bytes > MAX_RESOLVED_SELECTOR_BYTES
    ):
        raise SystemExit(
            "Refusing cosmetic update: resolved host "
            f"{max_host} has {max_resolved_rules} hides / {max_resolved_bytes} selector bytes"
        )
    write_asset(args.output, args.revision, records, stats, args.asset_kind)
    print(
        f"Wrote {stats.hide_rules} hides and {stats.exception_rules} exceptions; "
        f"skipped {stats.generic_rules} generic and {stats.unsupported_rules} unsupported rules"
    )


if __name__ == "__main__":
    main()
