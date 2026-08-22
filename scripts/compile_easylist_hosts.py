#!/usr/bin/env python3
"""Compile Candy's host-safe EasyList/EasyPrivacy network subset."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path

from compile_easylist_cosmetic import template_sources


BLOCK_HEADER = "# Generated EasyList/EasyPrivacy host rules. Do not edit by hand."
ALLOW_HEADER = (
    "# Generated EasyList/EasyPrivacy domain-scoped allow exceptions. Do not edit by hand."
)
SUPPORTED_BLOCK_OPTIONS = {"3p", "third-party"}
ASCII_ALNUM = frozenset("abcdefghijklmnopqrstuvwxyz0123456789")


@dataclass
class CompileStats:
    source_lines: int = 0
    blocked_hosts: int = 0
    allowed_pairs: int = 0
    unsupported_lines: int = 0
    conditional_lines: int = 0
    badfilter_lines: int = 0


def canonical_filter(value: str) -> str:
    pattern, separator, raw_options = value.rpartition("$")
    if not separator:
        return value
    options = sorted(option for option in raw_options.split(",") if option)
    return pattern + ("$" + ",".join(options) if options else "")


def is_host(value: str) -> bool:
    labels = value.lower().strip(".").split(".")
    return (
        len(labels) >= 2
        and len(value) <= 253
        and all(
            label
            and len(label) <= 63
            and label[0] in ASCII_ALNUM
            and label[-1] in ASCII_ALNUM
            and all(character in ASCII_ALNUM or character == "-" for character in label)
            for label in labels
        )
        and not all(character.isdigit() or character == "." for character in value)
    )


def active_lines(source_paths: list[Path], stats: CompileStats) -> list[str]:
    lines: list[str] = []
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
            lines.append(value)
        if conditional_depth:
            raise ValueError(f"Unclosed conditional section in {source_path}")
    return lines


def badfilter_targets(lines: list[str]) -> set[str]:
    targets: set[str] = set()
    for value in lines:
        pattern, separator, raw_options = value.rpartition("$")
        if not separator:
            continue
        options = raw_options.split(",")
        if "badfilter" not in options:
            continue
        retained = [option for option in options if option != "badfilter"]
        targets.add(canonical_filter(pattern + ("$" + ",".join(retained) if retained else "")))
    return targets


def anchored_host(pattern: str) -> str | None:
    if not pattern.startswith("||") or not pattern.endswith("^"):
        return None
    host = pattern[2:-1].lower().strip(".")
    return host if is_host(host) else None


def compile_sources(
    source_paths: list[Path],
) -> tuple[list[str], list[tuple[str, str]], CompileStats]:
    stats = CompileStats()
    lines = active_lines(source_paths, stats)
    disabled = badfilter_targets(lines)
    blocked: set[str] = set()
    raw_allows: list[tuple[str, list[str]]] = []

    for value in lines:
        if not value or value.startswith(("!", "[")):
            continue
        if any(marker in value for marker in ("##", "#@#", "#?#", "#$#", "#%#")):
            continue
        stats.source_lines += 1
        if canonical_filter(value) in disabled:
            continue
        pattern, separator, raw_options = value.rpartition("$")
        if not separator:
            pattern = value
            raw_options = ""
        options = [option for option in raw_options.split(",") if option]
        if "badfilter" in options:
            stats.badfilter_lines += 1
            continue

        is_allow = pattern.startswith("@@")
        host = anchored_host(pattern.removeprefix("@@"))
        if host is None:
            stats.unsupported_lines += 1
            continue
        if not is_allow:
            if set(options).issubset(SUPPORTED_BLOCK_OPTIONS):
                blocked.add(host)
            else:
                stats.unsupported_lines += 1
            continue
        raw_allows.append((host, options))

    allowed: set[tuple[str, str]] = set()
    for request_host, options in raw_allows:
        if request_host not in blocked:
            continue
        if not options:
            allowed.add((request_host, "*"))
            continue
        domain_options = [
            option.split("=", 1)[1]
            for option in options
            if option.startswith(("domain=", "from="))
        ]
        if len(domain_options) != 1:
            continue
        raw_domains = domain_options[0].split("|")
        if any(domain.startswith("~") or "*" in domain for domain in raw_domains):
            continue
        domains = [domain.lower().strip(".") for domain in raw_domains]
        if not domains or any(not is_host(domain) for domain in domains):
            continue
        allowed.update((request_host, domain) for domain in domains)

    ordered_blocked = sorted(blocked)
    ordered_allowed = sorted(allowed)
    stats.blocked_hosts = len(ordered_blocked)
    stats.allowed_pairs = len(ordered_allowed)
    return ordered_blocked, ordered_allowed, stats


def write_assets(
    blocked_output: Path,
    allowed_output: Path,
    revision: str,
    blocked_hosts: list[str],
    allowed_pairs: list[tuple[str, str]],
    stats: CompileStats,
) -> None:
    block_lines = [
        BLOCK_HEADER,
        "# Source: https://github.com/easylist/easylist",
        f"# Source revision: {revision}",
        "# Source graph: easylist.template + easyprivacy.template",
        "# License: CC BY-SA 3.0 or later; see content_filter.LICENSE.txt",
        f"# Generated hosts: {stats.blocked_hosts}",
        f"# Skipped unsupported network lines: {stats.unsupported_lines}",
        f"# Skipped conditional lines: {stats.conditional_lines}",
        *blocked_hosts,
    ]
    allow_lines = [
        ALLOW_HEADER,
        "# Format: request-host<TAB>page-host",
        "# Source: https://github.com/easylist/easylist",
        f"# Source revision: {revision}",
        "# Source graph: easylist.template + easyprivacy.template",
        "# License: CC BY-SA 3.0 or later; see content_filter.LICENSE.txt",
        f"# Generated host pairs: {stats.allowed_pairs}",
        *(f"{request_host}\t{page_host}" for request_host, page_host in allowed_pairs),
    ]
    blocked_output.write_text("\n".join(block_lines) + "\n", encoding="utf-8")
    allowed_output.write_text("\n".join(allow_lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--template", action="append", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--blocked-output", type=Path, required=True)
    parser.add_argument("--allowed-output", type=Path, required=True)
    parser.add_argument("--min-hosts", type=int, default=90_000)
    parser.add_argument("--max-hosts", type=int, default=130_000)
    parser.add_argument("--min-allowed-pairs", type=int, default=50)
    parser.add_argument("--max-allowed-pairs", type=int, default=2_000)
    parser.add_argument("--max-blocked-bytes", type=int, default=3_500_000)
    args = parser.parse_args()

    sources = template_sources(args.source_root, args.template)
    blocked, allowed, stats = compile_sources(sources)
    if not args.min_hosts <= len(blocked) <= args.max_hosts:
        raise SystemExit(f"Unexpected EasyList host count: {len(blocked)}")
    if not args.min_allowed_pairs <= len(allowed) <= args.max_allowed_pairs:
        raise SystemExit(f"Unexpected EasyList allow-pair count: {len(allowed)}")
    write_assets(
        args.blocked_output,
        args.allowed_output,
        args.revision,
        blocked,
        allowed,
        stats,
    )
    if args.blocked_output.stat().st_size > args.max_blocked_bytes:
        args.blocked_output.unlink(missing_ok=True)
        args.allowed_output.unlink(missing_ok=True)
        raise SystemExit("Generated EasyList host asset exceeds byte budget")
    print(f"Wrote {len(blocked)} hosts and {len(allowed)} allow pairs")


if __name__ == "__main__":
    main()
