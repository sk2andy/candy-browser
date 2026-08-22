#!/usr/bin/env python3
"""Compile a bounded ABP/uBO URL and popup subset for Candy Browser."""

from __future__ import annotations

import argparse
import base64
import re
from dataclasses import dataclass
from pathlib import Path


FORMAT_HEADER = "candy-advanced-filter:2"
SAFE_LABEL = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
HOST_ANCHORED = re.compile(
    r"^\|\|([a-z0-9-]+(?:\.[a-z0-9-]+)*(?:\.\*)?)(.*)$",
    re.IGNORECASE,
)
NATIVE_POPUP_SCRIPTLET = re.compile(
    r"^([^#]+)##\+js\((?:nowoif|prevent-window-open)\)$",
    re.IGNORECASE,
)
SUPPORTED_PASSIVE_OPTIONS: set[str] = set()
PARTY_OPTIONS = {
    "1p": "1",
    "first-party": "1",
    "3p": "3",
    "third-party": "3",
}


@dataclass(frozen=True, order=True)
class AdvancedRecord:
    action: str
    scope: str
    target_host: str
    url_pattern: str
    page_hosts: tuple[str, ...]
    excluded_page_hosts: tuple[str, ...]
    party: str
    behavior: str = "-"


@dataclass
class CompileStats:
    source_lines: int = 0
    request_rules: int = 0
    popup_rules: int = 0
    popunder_rules: int = 0
    allow_rules: int = 0
    unsupported_rules: int = 0
    conditional_lines: int = 0
    badfilter_rules: int = 0


def canonical_host_pattern(raw: str) -> str | None:
    value = raw.strip().lower().rstrip(".")
    wildcard = value.endswith(".*")
    fixed = value[:-2] if wildcard else value
    if "*" in fixed:
        return None
    try:
        ascii_value = fixed.encode("idna").decode("ascii")
    except UnicodeError:
        return None
    labels = ascii_value.split(".")
    minimum_labels = 1 if wildcard else 2
    if len(labels) < minimum_labels or any(not SAFE_LABEL.fullmatch(label) for label in labels):
        return None
    if all(character.isdigit() or character == "." for character in ascii_value):
        return None
    return f"{ascii_value}.*" if wildcard else ascii_value


def canonical_filter(value: str) -> str:
    option_index = value.rfind("$")
    if option_index < 0:
        return value
    options = sorted(part for part in value[option_index + 1 :].split(",") if part)
    return value[:option_index] + ("$" + ",".join(options) if options else "")


def badfilter_targets(lines: list[str]) -> set[str]:
    targets: set[str] = set()
    for raw in lines:
        value = raw.strip().lstrip("\ufeff")
        option_index = value.rfind("$")
        if option_index < 0:
            continue
        options = value[option_index + 1 :].split(",")
        if "badfilter" not in options:
            continue
        retained = [option for option in options if option != "badfilter"]
        target = value[:option_index]
        if retained:
            target += "$" + ",".join(retained)
        targets.add(canonical_filter(target))
    return targets


def split_options(value: str) -> tuple[str, list[str]]:
    option_index = value.rfind("$")
    if option_index < 0:
        return value, []
    return value[:option_index], value[option_index + 1 :].split(",")


def parse_domains(value: str) -> tuple[tuple[str, ...], tuple[str, ...]] | None:
    positives: set[str] = set()
    negatives: set[str] = set()
    for raw in value.split("|"):
        excluded = raw.startswith("~")
        pattern = canonical_host_pattern(raw[1:] if excluded else raw)
        if pattern is None:
            return None
        (negatives if excluded else positives).add(pattern)
    return tuple(sorted(positives)), tuple(sorted(negatives))


def parse_line(value: str, disabled: set[str]) -> AdvancedRecord | None:
    if canonical_filter(value) in disabled:
        return None
    action = "A" if value.startswith("@@") else "B"
    body = value.removeprefix("@@")
    pattern, options = split_options(body)
    if "badfilter" in options:
        return None
    popup_options = {option for option in options if option in ("popup", "popunder")}
    if len(popup_options) > 1:
        return None
    scope = "P" if "popup" in popup_options else "U" if "popunder" in popup_options else "N"
    accepted = popup_options | {"doc", "document"} if scope in ("P", "U") else set()
    accepted.update(SUPPORTED_PASSIVE_OPTIONS)
    party = "*"
    domain_option: str | None = None
    for option in options:
        if option in PARTY_OPTIONS:
            if party != "*" and party != PARTY_OPTIONS[option]:
                return None
            party = PARTY_OPTIONS[option]
        elif option.startswith(("domain=", "from=")):
            if domain_option is not None:
                return None
            domain_option = option.split("=", 1)[1]
        elif option not in accepted:
            return None
    pages: tuple[str, ...] = ()
    excluded_pages: tuple[str, ...] = ()
    if domain_option is not None:
        parsed_domains = parse_domains(domain_option)
        if parsed_domains is None:
            return None
        pages, excluded_pages = parsed_domains

    target_host = "*"
    url_pattern = pattern
    host_match = HOST_ANCHORED.fullmatch(pattern)
    if host_match:
        target_host = canonical_host_pattern(host_match.group(1)) or ""
        if not target_host:
            return None
        remainder = host_match.group(2)
        if scope == "N" and remainder in ("", "^"):
            return None
        if remainder.startswith(":"):
            return None
        url_pattern = "|" + (remainder or "*")
    elif pattern == "*":
        if not pages:
            return None
    elif pattern.startswith("/"):
        return None
    else:
        return None
    if len(url_pattern) > 512 or url_pattern.count("*") > 8:
        return None
    if (
        not url_pattern
        or "\\" in url_pattern
        or any(ord(character) < 32 for character in url_pattern)
    ):
        return None
    return AdvancedRecord(
        action=action,
        scope=scope,
        target_host=target_host,
        url_pattern=url_pattern,
        page_hosts=pages,
        excluded_page_hosts=excluded_pages,
        party=party,
        behavior="-",
    )


def parse_native_popup_scriptlet(value: str) -> AdvancedRecord | None:
    match = NATIVE_POPUP_SCRIPTLET.fullmatch(value)
    if match is None:
        return None
    parsed_domains = parse_domains(match.group(1).replace(",", "|"))
    if parsed_domains is None or not parsed_domains[0]:
        return None
    pages, excluded_pages = parsed_domains
    return AdvancedRecord(
        action="B",
        scope="P",
        target_host="*",
        url_pattern="*",
        page_hosts=pages,
        excluded_page_hosts=excluded_pages,
        party="*",
        behavior="W",
    )


def compile_sources(source_paths: list[Path]) -> tuple[list[AdvancedRecord], CompileStats]:
    stats = CompileStats()
    active_lines: list[str] = []
    records: set[AdvancedRecord] = set()
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
            active_lines.append(value)
        if conditional_depth:
            raise ValueError(f"Unclosed conditional section in {source_path}")
    disabled = badfilter_targets(active_lines)
    for value in active_lines:
        if not value or value.startswith(("!", "[")):
            continue
        native_popup = parse_native_popup_scriptlet(value)
        if native_popup is not None:
            records.add(native_popup)
            continue
        if any(marker in value for marker in ("##", "#@#", "#?#", "#$#", "#%#")):
            continue
        stats.source_lines += 1
        if "$badfilter" in value or ",badfilter" in value:
            stats.badfilter_rules += 1
            continue
        record = parse_line(value, disabled)
        if record is None:
            stats.unsupported_rules += 1
            continue
        records.add(record)
    ordered = sorted(records)
    stats.request_rules = sum(record.scope == "N" for record in ordered)
    stats.popup_rules = sum(record.scope == "P" for record in ordered)
    stats.popunder_rules = sum(record.scope == "U" for record in ordered)
    stats.allow_rules = sum(record.action == "A" for record in ordered)
    return ordered, stats


def encode(value: str) -> str:
    return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii").rstrip("=")


def maximum_bucket(records: list[AdvancedRecord]) -> tuple[str, int]:
    counts: dict[str, int] = {}
    for record in records:
        if record.target_host != "*":
            key = f"target:{record.target_host}"
            counts[key] = counts.get(key, 0) + 1
        else:
            for page_host in record.page_hosts:
                key = f"page:{page_host}"
                counts[key] = counts.get(key, 0) + 1
    return max(counts.items(), key=lambda item: (item[1], item[0]), default=("-", 0))


def write_asset(
    output: Path,
    revision: str,
    records: list[AdvancedRecord],
    stats: CompileStats,
) -> None:
    lines = [
        FORMAT_HEADER,
        "# Generated uAssets URL-path, wildcard, popup, and popunder subset. Do not edit by hand.",
        "# Source: https://github.com/uBlockOrigin/uAssets",
        f"# Source revision: {revision}",
        "# License: GPL-3.0; see uassets.LICENSE.txt",
        f"# Rules: {len(records)}",
        f"# Request rules: {stats.request_rules}",
        f"# Popup rules: {stats.popup_rules}",
        f"# Popunder rules: {stats.popunder_rules}",
        f"# Allow rules: {stats.allow_rules}",
        f"# Maximum candidate bucket: {maximum_bucket(records)[0]} {maximum_bucket(records)[1]}",
        f"# Skipped unsupported rules: {stats.unsupported_rules}",
        f"# Skipped conditional lines: {stats.conditional_lines}",
    ]
    for record in sorted(records):
        lines.append(
            "\t".join(
                (
                    record.action,
                    record.scope,
                    record.target_host,
                    encode(record.url_pattern),
                    ",".join(record.page_hosts) or "-",
                    ",".join(record.excluded_page_hosts) or "-",
                    record.party,
                    record.behavior,
                )
            )
        )
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-file", action="append", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--min-request-rules", type=int, default=100)
    parser.add_argument("--min-popup-rules", type=int, default=10)
    parser.add_argument("--min-popunder-rules", type=int, default=10)
    args = parser.parse_args()
    records, stats = compile_sources(args.source_file)
    if (
        stats.request_rules < args.min_request_rules
        or stats.popup_rules < args.min_popup_rules
        or stats.popunder_rules < args.min_popunder_rules
    ):
        raise SystemExit(
            "Refusing advanced update: only "
            f"{stats.request_rules} request / {stats.popup_rules} popup / "
            f"{stats.popunder_rules} popunder rules found"
        )
    bucket_name, bucket_size = maximum_bucket(records)
    if len(records) > 5_000 or bucket_size > 64:
        raise SystemExit(
            f"Refusing advanced update: {len(records)} rules / "
            f"bucket {bucket_name} has {bucket_size} rules"
        )
    write_asset(args.output, args.revision, records, stats)
    print(
        f"Wrote {stats.request_rules} request, {stats.popup_rules} popup, and "
        f"{stats.popunder_rules} popunder rules; "
        f"skipped {stats.unsupported_rules} unsupported"
    )


if __name__ == "__main__":
    main()
