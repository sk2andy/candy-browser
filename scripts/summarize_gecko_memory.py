#!/usr/bin/env python3
"""Summarize native about:memory JSON without printing URLs or report paths."""

from __future__ import annotations

import argparse
from collections import defaultdict
import gzip
import json
from pathlib import Path
import re
from typing import Any


MAX_REPORT_BYTES = 512 * 1024 * 1024
BYTE_UNITS = 0
HEAP_KIND = 1
CATEGORIES = (
    "javascript", "images", "graphics", "dom_layout", "bindings", "heap_unclassified", "other",
)
METRICS = (
    "resident", "resident-unique", "resident-fast", "heap-allocated", "heap-overhead", "vsize",
    "js-main-runtime-gc-heap-committed",
)


def load_report(path: Path) -> dict[str, Any]:
    with path.open("rb") as stream:
        compressed = stream.read(2) == b"\x1f\x8b"
    opener = gzip.open if compressed else open
    with opener(path, "rb") as stream:
        data = stream.read(MAX_REPORT_BYTES + 1)
    if len(data) > MAX_REPORT_BYTES:
        raise ValueError("memory report exceeds expanded size limit")
    return validate_report(json.loads(data))


def validate_report(root: Any) -> dict[str, Any]:
    if not isinstance(root, dict) or type(root.get("version")) is not int or root["version"] != 1:
        raise ValueError("unsupported memory report version")
    if type(root.get("hasMozMallocUsableSize")) is not bool:
        raise ValueError("missing hasMozMallocUsableSize boolean")
    if not isinstance(root.get("reports"), list):
        raise ValueError("missing reports array")
    for report in root["reports"]:
        if not isinstance(report, dict):
            raise ValueError("invalid report entry")
        for field in ("process", "path", "description"):
            if not isinstance(report.get(field), str):
                raise ValueError(f"invalid report {field}")
        for field in ("kind", "units", "amount"):
            if type(report.get(field)) is not int:
                raise ValueError(f"invalid report {field}")
        if report["kind"] not in (0, 1, 2) or report["units"] not in (0, 1, 2, 3):
            raise ValueError("unsupported report kind or units")
        if not report["path"]:
            raise ValueError("empty report path")
    return root


def allocation_category(path: str) -> str:
    parts = path.lower().split("/")
    if "heap-unclassified" in parts:
        return "heap_unclassified"
    if any(part == "images" or part.startswith("image(") for part in parts):
        return "images"
    if any(part in ("gfx", "graphics", "canvas", "canvas-2d-pixels", "webgl") or part.startswith("gfx-") for part in parts):
        return "graphics"
    if (
        parts[:2] == ["explicit", "workers"]
        and len(parts) > 3
        and parts[2].startswith("workers(")
        and parts[3].startswith("worker(")
    ):
        return "javascript"
    if any(part.startswith(("js-", "js(")) or part == "js" for part in parts):
        return "javascript"
    if any(part in ("xpconnect", "xpc") for part in parts):
        return "bindings"
    if any(part in ("dom", "layout", "style-sheets", "property-tables") for part in parts):
        return "dom_layout"
    return "other"


def ownership_tags(path: str, process: str) -> list[str]:
    lowered = path.lower()
    tags = []
    if "/cached/" in lowered:
        tags.append("bfcache")
    if "/add-ons/" in lowered or "moz-extension:" in lowered or process.lower().startswith("extension"):
        tags.append("extensions")
    if "orphan-nodes" in lowered:
        tags.append("orphan_dom")
    return tags


def _process_label(process: str, index: int) -> str:
    lowered = process.lower()
    role = "content"
    if not process or lowered.startswith(("main", "parent")):
        role = "main"
    elif lowered.startswith("extension"):
        role = "extension"
    elif lowered.startswith("gpu"):
        role = "gpu"
    elif lowered.startswith("socket"):
        role = "socket"
    elif not lowered.startswith(("web", "content", "isolated")):
        role = "other"
    pid = re.search(r"\bpid (\d+)\b", process)
    return f"{role} (pid {pid.group(1)})" if pid else f"{role} (process {index})"


def _explicit_leaves(reports: dict[str, dict[str, int]]) -> tuple[dict[str, dict[str, int]], int]:
    explicit = {path: report for path, report in reports.items() if report["units"] == BYTE_UNITS and (path == "explicit" or path.startswith("explicit/"))}
    parents = set()
    for path in explicit:
        parts = path.split("/")
        parents.update("/".join(parts[:index]) for index in range(1, len(parts)))
    leaves = {path: report for path, report in explicit.items() if path not in parents}
    return leaves, len(explicit) - len(leaves)


def summarize_report(root: dict[str, Any]) -> dict[str, Any]:
    validate_report(root)
    by_process: dict[str, dict[str, dict[str, int]]] = defaultdict(dict)
    for report in root["reports"]:
        reports = by_process[report["process"]]
        path = report["path"]
        if path in reports:
            if reports[path]["kind"] != report["kind"] or reports[path]["units"] != report["units"]:
                raise ValueError("duplicate path has mismatched kinds or units")
            reports[path]["amount"] += report["amount"]
        else:
            reports[path] = {"amount": report["amount"], "kind": report["kind"], "units": report["units"]}

    processes = []
    for index, (process, reports) in enumerate(sorted(by_process.items()), start=1):
        leaves, ignored_parents = _explicit_leaves(reports)
        categories = dict.fromkeys(CATEGORIES, 0)
        ownership = dict.fromkeys(("bfcache", "extensions", "orphan_dom"), 0)
        heap_reported = 0
        warnings = []
        for path, report in leaves.items():
            categories[allocation_category(path)] += report["amount"]
            if report["kind"] == HEAP_KIND:
                heap_reported += report["amount"]
            for tag in ownership_tags(path, process):
                ownership[tag] += report["amount"]
        heap_unclassified_source = "reported" if "explicit/heap-unclassified" in leaves else "unavailable"
        if leaves and "heap-allocated" in reports and reports["heap-allocated"]["units"] == BYTE_UNITS and "explicit/heap-unclassified" not in leaves:
            categories["heap_unclassified"] = reports["heap-allocated"]["amount"] - heap_reported
            heap_unclassified_source = "derived"
        if categories["heap_unclassified"] < 0:
            warnings.append("Negative heap-unclassified: reporters may be inconsistent or sampled at different times.")
        if ignored_parents:
            warnings.append("Aggregate ancestor paths excluded; incomplete descendant exports may undercount.")
        if not root["hasMozMallocUsableSize"]:
            warnings.append("Malloc usable size unavailable; allocation coverage may be incomplete.")
        if process.lower().startswith("extension") and heap_unclassified_source == "derived":
            ownership["extensions"] += categories["heap_unclassified"]
        processes.append({
            "process": _process_label(process, index),
            "explicit_bytes": sum(categories.values()) if leaves else None,
            "allocation_categories_bytes": categories,
            "ownership_subsets_bytes": ownership,
            "heap_unclassified_source": heap_unclassified_source,
            "metrics_bytes": {metric: reports[metric]["amount"] if metric in reports and reports[metric]["units"] == BYTE_UNITS else None for metric in METRICS},
            "counters": {"ghost_windows": reports["ghost-windows"]["amount"] if "ghost-windows" in reports and reports["ghost-windows"]["units"] == 1 else None},
            "excluded_aggregate_paths": ignored_parents,
            "warnings": warnings,
        })
    return {
        "process_count": len(processes),
        "processes": processes,
        "totals": {
            "explicit_bytes": _complete_sum(processes, lambda process: process["explicit_bytes"]),
            "allocation_categories_bytes": {
                category: _complete_sum(
                    processes,
                    lambda process: process["allocation_categories_bytes"][category]
                    if process["explicit_bytes"] is not None else None,
                )
                for category in CATEGORIES
            },
            "ownership_subsets_bytes": {
                tag: _complete_sum(
                    processes,
                    lambda process: process["ownership_subsets_bytes"][tag]
                    if process["explicit_bytes"] is not None else None,
                )
                for tag in ("bfcache", "extensions", "orphan_dom")
            },
            "metrics_bytes": {metric: _complete_sum(processes, lambda process: process["metrics_bytes"][metric]) for metric in METRICS},
            "counters": {"ghost_windows": _complete_sum(processes, lambda process: process["counters"]["ghost_windows"])},
        },
        "notes": [
            "Allocation categories partition explicit bytes; ownership subsets overlap them and each other. Do not add subsets to allocations.",
            "Other reporter trees overlap explicit and are excluded. XPConnect bindings are Gecko infrastructure, not proof of a Candy JS bridge leak.",
            "JavaScript includes native worker runtime reports. Other is measured allocation outside these selected categories, not synonymous with unclassified heap.",
            "Resident sums include shared pages more than once; they are not Android PSS or physical RAM totals. Do not add resident to explicit.",
            "Missing metrics are null, not zero. BFCache and extension ownership only include paths identifiable in the report.",
            "Anonymized reports may hide extension attribution. Process names and report paths are omitted from this summary.",
            "Native explicit reporters do not inventory the Android ART heap. Use a separate Java heap dump to attribute Kotlin objects, bitmaps, and Candy bridge owners.",
        ],
    }


def _complete_sum(processes: list[dict[str, Any]], value) -> int | None:
    values = [value(process) for process in processes]
    return sum(values) if values and all(amount is not None for amount in values) else None


def compare_reports(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    def delta(left, right):
        if isinstance(left, dict):
            return {key: delta(left[key], right[key]) for key in left}
        return right - left if left is not None and right is not None else None

    return {
        "process_count_before": before["process_count"],
        "process_count_after": after["process_count"],
        "aggregate_delta": delta(before["totals"], after["totals"]),
        "note": "Aggregate snapshot difference, not matched process lifetimes or proof of a leak. Match workload and capture mode before comparing.",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="Native about:memory JSON or JSON.gz report")
    parser.add_argument("--before", type=Path, help="Optional earlier snapshot for aggregate deltas")
    args = parser.parse_args()
    try:
        summary = summarize_report(load_report(args.report))
        if args.before:
            summary["comparison"] = compare_reports(summarize_report(load_report(args.before)), summary)
    except (OSError, ValueError, EOFError) as error:
        parser.exit(2, f"Invalid memory report: {type(error).__name__}\n")
    print(json.dumps(summary, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
