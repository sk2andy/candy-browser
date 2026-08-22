#!/usr/bin/env python3

import hashlib
import tempfile
import unittest
from pathlib import Path

from compile_hagezi_hosts import (
    compile_source,
    is_covered,
    load_existing_hosts,
    write_asset,
)


PROJECT_DIR = Path(__file__).resolve().parents[1]
PINNED_ASSET = PROJECT_DIR / "app/src/main/assets/hagezi_blocked_hosts.txt"
PINNED_REVISION = "4952c89a3ee5e87e173cef9c6c21a17345dfdc24"
PINNED_SHA256 = "9d9737fb46df0b9a5cdf08a8497c45a4ac8f0d106b3e45269098f65b89e24df0"


def fixture(*rules: str, declared: int | None = None) -> bytes:
    count = len(rules) if declared is None else declared
    return (
        "[Adblock Plus]\n"
        "! Title: Fixture\n"
        f"! Number of entries: {count}\n"
        + "".join(f"{rule}\n" for rule in rules)
    ).encode()


class HageziHostCompilerTest(unittest.TestCase):
    def compile(self, source: bytes):
        return compile_source(source, hashlib.sha256(source).hexdigest())

    def test_normalizes_and_sorts_exact_host_rules(self):
        compiled = self.compile(fixture("||b.example^", "||a.example^"))

        self.assertEqual(["a.example", "b.example"], compiled.hosts)
        self.assertEqual(2, compiled.source_hosts)
        self.assertEqual(0, compiled.excluded_hosts)

    def test_excludes_hosts_already_covered_by_existing_suffixes(self):
        source = fixture(
            "||already.example^",
            "||cdn.already.example^",
            "||new.example^",
        )
        compiled = compile_source(
            source,
            hashlib.sha256(source).hexdigest(),
            {"already.example"},
        )

        self.assertEqual(["new.example"], compiled.hosts)
        self.assertEqual(3, compiled.source_hosts)
        self.assertEqual(2, compiled.excluded_hosts)

    def test_loads_existing_generated_assets_and_rejects_invalid_hosts(self):
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.txt"
            second = Path(directory) / "second.txt"
            first.write_text("# Header\nalready.example\n", encoding="utf-8")
            second.write_text("! Header\nalready.example\nother.example\n", encoding="utf-8")

            self.assertEqual(
                {"already.example", "other.example"},
                load_existing_hosts([first, second]),
            )
            second.write_text("not a host\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Invalid existing host"):
                load_existing_hosts([second])

    def test_rejects_hash_mismatch_unsupported_rules_and_duplicates(self):
        source = fixture("||a.example^")
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            compile_source(source, "0" * 64)
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            self.compile(fixture("/generic-ad.js"))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            self.compile(fixture("||a.example^", "||a.example^"))

    def test_rejects_declared_count_drift_and_invalid_hosts(self):
        with self.assertRaisesRegex(ValueError, "declared 2 entries but parsed 1"):
            self.compile(fixture("||a.example^", declared=2))
        with self.assertRaisesRegex(ValueError, "Unsupported"):
            self.compile(fixture("||bad..example^"))

    def test_output_is_deterministic_and_records_provenance(self):
        source = fixture("||b.example^", "||a.example^")
        compiled = self.compile(source)
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.txt"
            second = Path(directory) / "second.txt"
            write_asset(first, "revision", compiled, ["existing.txt"])
            write_asset(second, "revision", compiled, ["existing.txt"])

            self.assertEqual(first.read_bytes(), second.read_bytes())
            output = first.read_text()
            self.assertIn("# Source revision: revision", output)
            self.assertIn(f"# Source SHA-256: {hashlib.sha256(source).hexdigest()}", output)
            self.assertIn("# Excluded by existing Candy assets: existing.txt", output)
            self.assertIn("# Source hosts: 2", output)
            self.assertIn("# Excluded already covered: 0", output)
            self.assertIn("# Generated delta hosts: 2", output)

    def test_checked_in_asset_is_bounded_sorted_unique_and_pinned(self):
        lines = PINNED_ASSET.read_text(encoding="utf-8").splitlines()
        hosts = [line for line in lines if line and not line.startswith("#")]

        self.assertIn(f"# Source revision: {PINNED_REVISION}", lines)
        self.assertIn(f"# Source SHA-256: {PINNED_SHA256}", lines)
        self.assertIn("# Source hosts: 223971", lines)
        self.assertIn("# Excluded already covered: 57922", lines)
        self.assertIn("# Generated delta hosts: 166049", lines)
        self.assertEqual(166_049, len(hosts))
        self.assertEqual(sorted(hosts), hosts)
        self.assertEqual(len(set(hosts)), len(hosts))
        self.assertLessEqual(PINNED_ASSET.stat().st_size, 3_500_000)

        existing = load_existing_hosts(
            [
                PROJECT_DIR / "app/src/main/assets/easylist_blocked_hosts.txt",
                PROJECT_DIR / "app/src/main/assets/blocked_hosts.txt",
                PROJECT_DIR / "app/src/main/assets/uassets_blocked_hosts.txt",
            ]
        )
        self.assertFalse(any(is_covered(host, existing) for host in hosts))


if __name__ == "__main__":
    unittest.main()
