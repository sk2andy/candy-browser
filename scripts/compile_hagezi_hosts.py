#!/usr/bin/env python3
"""Validate and normalize Candy's pinned HaGeZi Pro host asset."""

from __future__ import annotations

import argparse
import hashlib
import re
from dataclasses import dataclass
from pathlib import Path


FORMAT_HEADER = "# Generated HaGeZi Pro host rules. Do not edit by hand."
DECLARED_COUNT = re.compile(r"^! Number of entries: ([0-9]+)$")
ASCII_ALNUM = frozenset("abcdefghijklmnopqrstuvwxyz0123456789")


@dataclass(frozen=True)
class CompiledHosts:
    hosts: list[str]
    source_hosts: int
    excluded_hosts: int
    source_sha256: str
    source_bytes: int


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


def is_covered(host: str, existing_hosts: set[str]) -> bool:
    candidate = host
    while True:
        if candidate in existing_hosts:
            return True
        dot = candidate.find(".")
        if dot < 0:
            return False
        candidate = candidate[dot + 1 :]


def load_existing_hosts(paths: list[Path]) -> set[str]:
    hosts: set[str] = set()
    for path in paths:
        for line in path.read_text(encoding="utf-8").splitlines():
            host = line.strip().lower().strip(".")
            if not host or host.startswith(("#", "!")):
                continue
            if not is_host(host):
                raise ValueError(f"Invalid existing host in {path}: {line}")
            hosts.add(host)
    return hosts


def compile_source(
    source: bytes,
    expected_sha256: str,
    existing_hosts: set[str] | None = None,
) -> CompiledHosts:
    actual_sha256 = hashlib.sha256(source).hexdigest()
    if actual_sha256 != expected_sha256.lower():
        raise ValueError(f"HaGeZi source SHA-256 mismatch: {actual_sha256}")
    text = source.decode("utf-8")
    lines = text.splitlines()
    if not lines or lines[0].strip().lstrip("\ufeff") != "[Adblock Plus]":
        raise ValueError("Invalid HaGeZi Adblock header")
    declared_counts = [
        int(match.group(1))
        for line in lines
        if (match := DECLARED_COUNT.fullmatch(line.strip())) is not None
    ]
    if len(declared_counts) != 1:
        raise ValueError("Missing or duplicate HaGeZi entry count")

    hosts: list[str] = []
    unsupported: list[str] = []
    for raw in lines[1:]:
        value = raw.strip()
        if not value or value.startswith("!"):
            continue
        if not value.startswith("||") or not value.endswith("^"):
            unsupported.append(value)
            continue
        host = value[2:-1].lower().strip(".")
        if not is_host(host):
            unsupported.append(value)
            continue
        hosts.append(host)
    if unsupported:
        raise ValueError(f"Unsupported HaGeZi rule: {unsupported[0]}")
    if len(hosts) != declared_counts[0]:
        raise ValueError(
            f"HaGeZi declared {declared_counts[0]} entries but parsed {len(hosts)}"
        )
    if len(set(hosts)) != len(hosts):
        raise ValueError("Duplicate HaGeZi host")
    ordered_hosts = sorted(hosts)
    covered_hosts = existing_hosts or set()
    delta_hosts = [
        host for host in ordered_hosts if not is_covered(host, covered_hosts)
    ]
    return CompiledHosts(
        hosts=delta_hosts,
        source_hosts=len(ordered_hosts),
        excluded_hosts=len(ordered_hosts) - len(delta_hosts),
        source_sha256=actual_sha256,
        source_bytes=len(source),
    )


def write_asset(
    output: Path,
    revision: str,
    compiled: CompiledHosts,
    exclusion_names: list[str] | None = None,
) -> None:
    lines = [
        FORMAT_HEADER,
        (
            "# Source: "
            "https://raw.githubusercontent.com/hagezi/dns-blocklists/"
            f"{revision}/adblock/pro.txt"
        ),
        f"# Source revision: {revision}",
        f"# Source SHA-256: {compiled.source_sha256}",
        "# License: GPL-3.0; see hagezi.LICENSE.txt",
        (
            "# Excluded by existing Candy assets: "
            + ", ".join(exclusion_names or [])
        ),
        f"# Source hosts: {compiled.source_hosts}",
        f"# Excluded already covered: {compiled.excluded_hosts}",
        f"# Generated delta hosts: {len(compiled.hosts)}",
        *compiled.hosts,
    ]
    output.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-file", type=Path, required=True)
    parser.add_argument("--source-sha256", required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--exclude-file", action="append", type=Path, default=[])
    parser.add_argument("--min-source-hosts", type=int, default=210_000)
    parser.add_argument("--max-source-hosts", type=int, default=240_000)
    parser.add_argument("--min-excluded-hosts", type=int, default=50_000)
    parser.add_argument("--max-excluded-hosts", type=int, default=80_000)
    parser.add_argument("--min-delta-hosts", type=int, default=150_000)
    parser.add_argument("--max-delta-hosts", type=int, default=180_000)
    parser.add_argument("--min-source-bytes", type=int, default=4_500_000)
    parser.add_argument("--max-source-bytes", type=int, default=5_500_000)
    parser.add_argument("--max-output-bytes", type=int, default=3_500_000)
    args = parser.parse_args()

    existing_hosts = load_existing_hosts(args.exclude_file)
    compiled = compile_source(
        args.source_file.read_bytes(),
        args.source_sha256,
        existing_hosts,
    )
    if not args.min_source_hosts <= compiled.source_hosts <= args.max_source_hosts:
        raise SystemExit(f"Unexpected HaGeZi source host count: {compiled.source_hosts}")
    if not args.min_excluded_hosts <= compiled.excluded_hosts <= args.max_excluded_hosts:
        raise SystemExit(
            f"Unexpected HaGeZi excluded host count: {compiled.excluded_hosts}"
        )
    if not args.min_delta_hosts <= len(compiled.hosts) <= args.max_delta_hosts:
        raise SystemExit(f"Unexpected HaGeZi delta host count: {len(compiled.hosts)}")
    if not args.min_source_bytes <= compiled.source_bytes <= args.max_source_bytes:
        raise SystemExit(f"Unexpected HaGeZi source size: {compiled.source_bytes}")
    write_asset(
        args.output,
        args.revision,
        compiled,
        [path.name for path in args.exclude_file],
    )
    if args.output.stat().st_size > args.max_output_bytes:
        args.output.unlink(missing_ok=True)
        raise SystemExit("Generated HaGeZi host asset exceeds byte budget")
    print(
        f"Wrote {len(compiled.hosts)} HaGeZi delta hosts "
        f"({compiled.excluded_hosts} already covered)"
    )


if __name__ == "__main__":
    main()
