#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from compile_advanced_filters import compile_sources, maximum_bucket, write_asset


PROJECT_DIR = Path(__file__).resolve().parents[1]
PINNED_SOURCE = PROJECT_DIR / "app/src/main/assets/uassets_advanced_filters_source.txt"
PINNED_ASSET = PROJECT_DIR / "app/src/main/assets/uassets_advanced_filters.txt"
REVISION = "05bc031ad40c2270223f068f052970201ca1bf14"


class AdvancedFilterCompilerTest(unittest.TestCase):
    def compile(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "filters.txt"
            path.write_text(source, encoding="utf-8")
            return compile_sources([path])

    def test_compiles_host_paths_wildcards_popup_and_scopes(self):
        records, stats = self.compile(
            """
            ||ads.example/assets/*/ad.js^$domain=news.example,3p
            ||popup.example^$popup,domain=stream.example
            *$popup,domain=movies.example,3p
            @@||popup.example/account/*$popup,domain=stream.example
            """
        )

        self.assertEqual(1, stats.request_rules)
        self.assertEqual(3, stats.popup_rules)
        self.assertEqual(1, stats.allow_rules)
        self.assertEqual(
            "|/assets/*/ad.js^",
            next(record.url_pattern for record in records if record.scope == "N"),
        )

    def test_rejects_unscoped_generic_typed_regex_and_redirect_rules(self):
        records, stats = self.compile(
            """
            /tracking/*
            *$popup
            ||ads.example/file.js$script
            /tracker[0-9]+/
            ||analytics.example/a.js$redirect=noop.js
            """
        )

        self.assertEqual([], records)
        self.assertEqual(5, stats.unsupported_rules)

    def test_badfilter_disables_exact_rule_across_order(self):
        records, stats = self.compile(
            """
            ||disabled.example/path*$domain=news.example,3p
            ||kept.example/path*
            ||disabled.example/path*$3p,badfilter,domain=news.example
            """
        )

        self.assertEqual(1, len(records))
        self.assertEqual("kept.example", records[0].target_host)
        self.assertEqual(1, stats.badfilter_rules)

    def test_output_is_deterministic_and_declares_counts(self):
        records, stats = self.compile(
            "||popup.example^$popup\n||ads.example/path*\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            first = Path(directory) / "first.txt"
            second = Path(directory) / "second.txt"
            write_asset(first, "revision", records, stats)
            write_asset(second, "revision", list(reversed(records)), stats)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertIn("# Rules: 2", first.read_text(encoding="utf-8"))

    def test_maps_only_allowlisted_window_open_scriptlet_to_native_popup_rule(self):
        records, stats = self.compile(
            """
            stream.example,mirror.example##+js(nowoif)
            unsafe.example##+js(set, adblock, false)
            """
        )

        self.assertEqual(1, stats.popup_rules)
        self.assertEqual(("mirror.example", "stream.example"), records[0].page_hosts)
        self.assertEqual("*", records[0].target_host)
        self.assertEqual("W", records[0].behavior)

    def test_pinned_snapshot_matches_checked_in_asset(self):
        records, stats = compile_sources([PINNED_SOURCE])
        self.assertEqual(747, stats.request_rules)
        self.assertEqual(703, stats.popup_rules)
        self.assertEqual(89, stats.allow_rules)
        self.assertEqual(("target:st.pussyspace.com", 13), maximum_bucket(records))
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "advanced.txt"
            write_asset(output, REVISION, records, stats)
            self.assertEqual(PINNED_ASSET.read_bytes(), output.read_bytes())


if __name__ == "__main__":
    unittest.main()
