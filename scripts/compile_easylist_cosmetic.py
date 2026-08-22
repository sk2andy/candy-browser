#!/usr/bin/env python3
"""Compile Candy's supported EasyList/EasyPrivacy cosmetic subset.

Only standard CSS element-hiding rules are emitted. Procedural operators, scriptlets,
response-header filters, and malformed domains fail closed.
"""

from __future__ import annotations

import argparse
import base64
import re
from dataclasses import dataclass
from pathlib import Path


FORMAT_HEADER = "candy-easylist-cosmetic:1"
EASYLIST_V2_FORMAT_HEADER = "candy-easylist-cosmetic:2"
UASSETS_FORMAT_HEADER = "candy-uassets-cosmetic:2"
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
MAX_GENERIC_HIDE_EXCEPTIONS = 1_500
MAX_WILDCARD_GENERIC_HIDE_EXCEPTIONS = 400
MAX_GENERIC_RULES = 14_500
MAX_COMPLEX_GENERIC_RULES = 600
MAX_EASYLIST_ASSET_BYTES = 1_250_000
SIMPLE_GENERIC_SELECTOR = re.compile(r"^[.#][A-Za-z_][A-Za-z0-9_-]*$")


@dataclass
class CompileStats:
    source_lines: int = 0
    hide_rules: int = 0
    exception_rules: int = 0
    generic_rules: int = 0
    skipped_generic_rules: int = 0
    generic_hide_exceptions: int = 0
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


def parse_generic_hide_exception(
    value: str,
    stats: CompileStats,
) -> list[tuple[str, str, tuple[str, ...], str]]:
    if not value.startswith("@@") or "$" not in value:
        return []
    pattern, raw_options = value[2:].rsplit("$", 1)
    options = raw_options.split(",")
    hide_options = [option for option in options if option in ("ghide", "generichide")]
    if len(hide_options) != 1:
        return []
    remaining = [option for option in options if option not in ("ghide", "generichide")]
    patterns: tuple[str, ...] = ()
    exclusions: tuple[str, ...] = ()
    if pattern.startswith("||") and pattern.endswith("^") and not remaining:
        host = canonical_pattern(pattern[2:-1])
        if host is not None:
            patterns = (host,)
    elif pattern == "*" and len(remaining) == 1 and remaining[0].startswith(("domain=", "from=")):
        domain_value = remaining[0].split("=", 1)[1]
        positives: set[str] = set()
        negatives: set[str] = set()
        for raw_domain in domain_value.split("|"):
            excluded = raw_domain.startswith("~")
            host = canonical_pattern(raw_domain[1:] if excluded else raw_domain)
            if host is None:
                return []
            (negatives if excluded else positives).add(host)
        patterns = tuple(sorted(positives))
        exclusions = tuple(sorted(negatives))
    if not patterns:
        return []
    stats.generic_hide_exceptions += len(patterns)
    return [("D", pattern, exclusions, "-") for pattern in patterns]


def canonical_network_filter(value: str) -> str:
    pattern, separator, raw_options = value.rpartition("$")
    if not separator:
        return value
    options = sorted(option for option in raw_options.split(",") if option)
    return pattern + ("$" + ",".join(options) if options else "")


def badfilter_targets(lines: list[str]) -> set[str]:
    disabled: set[str] = set()
    for value in lines:
        pattern, separator, raw_options = value.rpartition("$")
        if not separator:
            continue
        options = raw_options.split(",")
        if "badfilter" not in options:
            continue
        retained = [option for option in options if option != "badfilter"]
        disabled.add(canonical_network_filter(pattern + "$" + ",".join(retained)))
    return disabled


def parse_filter_line(
    line: str,
    stats: CompileStats,
    include_generics: bool = False,
) -> list[tuple[str, str, tuple[str, ...], str]]:
    value = line.strip().lstrip("\ufeff")
    if not value or value.startswith("!") or value.startswith("["):
        return []
    stats.source_lines += 1
    generic_hide = parse_generic_hide_exception(value, stats) if include_generics else []
    if generic_hide:
        return generic_hide
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
    if not domain_part and not include_generics:
        stats.skipped_generic_rules += 1
        return []

    positives: list[str] = []
    negatives: list[str] = []
    for raw_domain in domain_part.split(",") if domain_part else ():
        excluded = raw_domain.startswith("~")
        pattern = canonical_pattern(raw_domain[1:] if excluded else raw_domain)
        if pattern is None:
            stats.unsupported_rules += 1
            return []
        (negatives if excluded else positives).append(pattern)
    if not positives and not include_generics:
        stats.skipped_generic_rules += 1
        return []
    if marker == "#@#" and negatives:
        stats.unsupported_rules += 1
        return []

    action = "A" if marker == "#@#" else "H"
    exclusions = tuple(sorted(set(negatives))) if action == "H" else ()
    patterns = tuple(sorted(set(positives))) or ("*",)
    return [(action, pattern, exclusions, selector) for pattern in patterns]


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


def compile_sources(
    source_paths: list[Path],
    include_generics: bool = False,
) -> tuple[list[tuple[str, str, tuple[str, ...], str]], CompileStats]:
    stats = CompileStats()
    records: set[tuple[str, str, tuple[str, ...], str]] = set()
    active_lines: list[str] = []
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
            active_lines.append(line.strip().lstrip("\ufeff"))
        if conditional_depth:
            raise ValueError(f"Unclosed conditional section in {source_path}")
    disabled = badfilter_targets(active_lines)
    for line in active_lines:
        if "badfilter" in line.rpartition("$")[2].split(","):
            continue
        if canonical_network_filter(line) in disabled:
            continue
        records.update(parse_filter_line(line, stats, include_generics))
    ordered = sorted(records, key=lambda record: (record[0], record[1], record[3], record[2]))
    stats.hide_rules = sum(record[0] == "H" for record in ordered)
    stats.exception_rules = sum(record[0] == "A" for record in ordered)
    stats.generic_rules = sum(record[1] == "*" for record in ordered)
    stats.generic_hide_exceptions = sum(record[0] == "D" for record in ordered)
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
    include_generics: bool = True,
) -> tuple[str, int, int]:
    exact: dict[str, set[str]] = {}
    wildcard: list[tuple[str, str]] = []
    for action, pattern, _, selector in records:
        if action != "H" or pattern == "*":
            continue
        if pattern.endswith(".*"):
            wildcard.append((pattern, selector))
        else:
            exact.setdefault(pattern, set()).add(selector)
    generic = {
        selector
        for action, pattern, _, selector in records
        if include_generics and action == "H" and pattern == "*"
    }
    candidates = set(exact) or {"example.invalid"}
    candidates.update(pattern.replace(".*", ".com") for pattern, _ in wildcard)
    maximum = ("", 0, 0)
    for host in candidates:
        selectors: set[str] = set(generic)
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
        description = "uAssets scoped and bounded generic cosmetic rules"
        source = "https://github.com/uBlockOrigin/uAssets"
        license_line = "GPL-3.0; see uassets.LICENSE.txt"
    else:
        header = (
            EASYLIST_V2_FORMAT_HEADER
            if stats.generic_rules or stats.generic_hide_exceptions
            else FORMAT_HEADER
        )
        description = "EasyList/EasyPrivacy standard cosmetic rules"
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
        f"# Generic rules: {stats.generic_rules}",
        f"# Generic hide exceptions: {stats.generic_hide_exceptions}",
        f"# Maximum hide rules for one pattern: {maximum_pattern_load(records)[1]}",
        f"# Maximum resolved host: {maximum_host}",
        f"# Maximum resolved hide selectors: {maximum_selectors}",
        f"# Skipped generic rules: {stats.skipped_generic_rules}",
        f"# Skipped unsupported rules: {stats.unsupported_rules}",
        f"# Skipped conditional lines: {stats.conditional_lines}",
    ]
    for action, pattern, exclusions, selector in records:
        lines.append(
            "\t".join(
                (
                    action,
                    pattern,
                    ",".join(exclusions) or "-",
                    "-" if action == "D" else encode_selector(selector),
                )
            )
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
    parser.add_argument("--min-exception-rules", type=int, default=0)
    parser.add_argument("--min-generic-rules", type=int, default=0)
    parser.add_argument("--min-generic-hide-exceptions", type=int, default=0)
    parser.add_argument("--include-generics", action="store_true")
    args = parser.parse_args()

    source_paths = list(args.source_file or [])
    if args.template:
        if args.source_root is None:
            parser.error("--source-root is required with --template")
        source_paths.extend(template_sources(args.source_root, args.template))
    if not source_paths:
        parser.error("at least one --template or --source-file is required")
    records, stats = compile_sources(source_paths, include_generics=args.include_generics)
    if stats.hide_rules < args.min_hide_rules:
        raise SystemExit(
            f"Refusing cosmetic update: only {stats.hide_rules} supported hide rules found"
        )
    if (
        stats.exception_rules < args.min_exception_rules
        or stats.generic_rules < args.min_generic_rules
        or stats.generic_hide_exceptions < args.min_generic_hide_exceptions
    ):
        raise SystemExit(
            "Refusing cosmetic update: only "
            f"{stats.exception_rules} exceptions / {stats.generic_rules} generic / "
            f"{stats.generic_hide_exceptions} generic-hide exceptions found"
        )
    wildcard_generic_hide_exceptions = sum(
        action == "D" and pattern.endswith(".*")
        for action, pattern, _, _ in records
    )
    if (
        stats.generic_hide_exceptions > MAX_GENERIC_HIDE_EXCEPTIONS
        or wildcard_generic_hide_exceptions > MAX_WILDCARD_GENERIC_HIDE_EXCEPTIONS
    ):
        raise SystemExit(
            "Refusing cosmetic update: "
            f"{stats.generic_hide_exceptions} generic-hide exceptions / "
            f"{wildcard_generic_hide_exceptions} wildcard exceptions"
        )
    generic_hides = [
        selector
        for action, pattern, _, selector in records
        if action == "H" and pattern == "*"
    ]
    complex_generic_count = sum(
        SIMPLE_GENERIC_SELECTOR.fullmatch(selector) is None
        for selector in generic_hides
    )
    if (
        len(generic_hides) > MAX_GENERIC_RULES
        or complex_generic_count > MAX_COMPLEX_GENERIC_RULES
    ):
        raise SystemExit(
            "Refusing cosmetic update: "
            f"{len(generic_hides)} generic / {complex_generic_count} complex generic rules"
        )
    scoped_records = [
        record
        for record in records
        if not (record[0] == "H" and record[1] == "*")
    ]
    max_pattern, max_rules, max_selector_bytes = maximum_pattern_load(scoped_records)
    if (
        max_rules > MAX_HIDE_RULES_PER_PATTERN
        or max_selector_bytes > MAX_SELECTOR_BYTES_PER_PATTERN
    ):
        raise SystemExit(
            "Refusing cosmetic update: pattern "
            f"{max_pattern} has {max_rules} hides / {max_selector_bytes} selector bytes"
        )
    max_host, max_resolved_rules, max_resolved_bytes = maximum_resolved_host_load(
        records,
        include_generics=False,
    )
    if (
        max_resolved_rules > MAX_RESOLVED_HIDE_RULES
        or max_resolved_bytes > MAX_RESOLVED_SELECTOR_BYTES
    ):
        raise SystemExit(
            "Refusing cosmetic update: resolved host "
            f"{max_host} has {max_resolved_rules} hides / {max_resolved_bytes} selector bytes"
        )
    write_asset(args.output, args.revision, records, stats, args.asset_kind)
    if args.asset_kind == "easylist" and args.output.stat().st_size > MAX_EASYLIST_ASSET_BYTES:
        args.output.unlink(missing_ok=True)
        raise SystemExit(
            "Refusing cosmetic update: EasyList cosmetic asset exceeds byte limit"
        )
    print(
        f"Wrote {stats.hide_rules} hides and {stats.exception_rules} exceptions; "
        f"compiled {stats.generic_rules} generic, skipped {stats.skipped_generic_rules} generic "
        f"and {stats.unsupported_rules} unsupported rules"
    )


if __name__ == "__main__":
    main()
