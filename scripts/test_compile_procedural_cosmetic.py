#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from compile_procedural_cosmetic import compile_sources, maximum_bucket, write_asset


PROJECT_DIR = Path(__file__).resolve().parents[1]
PINNED_SOURCE = PROJECT_DIR / "app/src/main/assets/uassets_advanced_filters_source.txt"
PINNED_ASSET = PROJECT_DIR / "app/src/main/assets/uassets_procedural_cosmetic_rules.txt"
REVISION = "05bc031ad40c2270223f068f052970201ca1bf14"


class ProceduralCosmeticCompilerTest(unittest.TestCase):
    def compile(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "filters.txt"
            path.write_text(source, encoding="utf-8")
            return compile_sources([path])

    def test_compiles_bounded_text_and_remove_rules(self):
        records, stats = self.compile(
            """
            news.example##strong:has-text(/Anzeige/i)
            ads.example##[id^="banner"]:remove()
            stream.example##.notice:has-text(Ad:):remove()
            """
        )

        self.assertEqual(1, stats.hide_rules)
        self.assertEqual(2, stats.remove_rules)
        self.assertIn("Anzeige", {record.text for record in records})

    def test_rejects_regex_operators_chained_procedural_and_negative_domains(self):
        records, stats = self.compile(
            """
            news.example##div:has-text(/Ad.+/i)
            news.example##div:has-text(Ad):xpath(..)
            ~safe.example,news.example##.ad:remove()
            """
        )

        self.assertEqual([], records)
        self.assertEqual(3, stats.unsupported_rules)

    def test_output_is_deterministic(self):
        records, stats = self.compile("news.example##.ad:remove()")
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.txt"
            second = Path(directory) / "second.txt"
            write_asset(first, "revision", records, stats)
            write_asset(second, "revision", list(reversed(records)), stats)
            self.assertEqual(first.read_bytes(), second.read_bytes())

    def test_pinned_snapshot_matches_checked_in_asset(self):
        records, stats = compile_sources([PINNED_SOURCE])
        self.assertEqual(156, stats.hide_rules)
        self.assertEqual(76, stats.remove_rules)
        self.assertEqual(("skai.gr", 4), maximum_bucket(records))
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "procedural.txt"
            write_asset(output, REVISION, records, stats)
            self.assertEqual(PINNED_ASSET.read_bytes(), output.read_bytes())


if __name__ == "__main__":
    unittest.main()
