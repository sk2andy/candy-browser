#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from compile_easylist_cosmetic import (
    CompileStats,
    EASYLIST_V2_FORMAT_HEADER,
    compile_sources,
    maximum_pattern_load,
    maximum_resolved_host_load,
    parse_filter_line,
    write_asset,
)


PROJECT_DIR = Path(__file__).resolve().parents[1]
EASYLIST_ASSET = PROJECT_DIR / "app/src/main/assets/easylist_cosmetic_rules.txt"


class CosmeticCompilerTest(unittest.TestCase):
    def test_checked_in_easylist_v2_snapshot_is_bounded(self):
        text = EASYLIST_ASSET.read_text(encoding="utf-8")

        self.assertEqual(EASYLIST_V2_FORMAT_HEADER, text.splitlines()[0])
        self.assertIn("# Hide rules: 30139", text)
        self.assertIn("# Exception rules: 652", text)
        self.assertIn("# Generic rules: 13642", text)
        self.assertIn("# Generic hide exceptions: 154", text)
        self.assertLessEqual(EASYLIST_ASSET.stat().st_size, 1_250_000)

    def test_parses_domains_exclusions_exceptions_and_standard_has(self):
        stats = CompileStats()

        self.assertEqual(
            [("H", "example.com", ("mail.example.com",), ".ad:has(> span)")],
            parse_filter_line("example.com,~mail.example.com##.ad:has(> span)", stats),
        )
        self.assertEqual(
            [("A", "news.example", (), ".ad")],
            parse_filter_line("news.example#@#.ad", stats),
        )
        self.assertEqual(
            [("H", "amazon.*", (), ".AdHolder")],
            parse_filter_line("amazon.*##.AdHolder", stats),
        )

    def test_skips_generic_extended_and_malformed_rules(self):
        stats = CompileStats()

        self.assertEqual([], parse_filter_line("##.generic-ad", stats))
        self.assertEqual([], parse_filter_line("example.com##div:has-text(Ad)", stats))
        self.assertEqual([], parse_filter_line("bad..example##.ad", stats))
        self.assertEqual(1, stats.skipped_generic_rules)
        self.assertEqual(2, stats.unsupported_rules)

    def test_compiles_bounded_generics_exceptions_and_generic_hide(self):
        stats = CompileStats()

        self.assertEqual(
            [("H", "*", (), ".generic-ad")],
            parse_filter_line("##.generic-ad", stats, include_generics=True),
        )
        self.assertEqual(
            [("A", "news.example", (), ".generic-ad")],
            parse_filter_line("news.example#@#.generic-ad", stats, include_generics=True),
        )
        self.assertEqual(
            [("D", "news.example", (), "-")],
            parse_filter_line(
                "@@||news.example^$generichide",
                stats,
                include_generics=True,
            ),
        )
        self.assertEqual(
            [("D", "stream.*", ("safe.stream.de",), "-")],
            parse_filter_line(
                "@@*$ghide,domain=stream.*|~safe.stream.de",
                stats,
                include_generics=True,
            ),
        )

    def test_rejects_ubo_procedural_operators(self):
        operators = (
            ":matches-css-before(content: /ad/)",
            ":matches-css-after(content: /ad/)",
            ":matches-attr(title=/ad/)",
            ":remove-attr(class)",
            ":remove-class(ad)",
            ":min-text-length(20)",
            ":if(.ad)",
            ":if-not(.organic)",
        )
        stats = CompileStats()

        for operator in operators:
            self.assertEqual([], parse_filter_line(f"example.com##div{operator}", stats))

        self.assertEqual(len(operators), stats.unsupported_rules)

    def test_skips_conditional_sections_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "rules.txt"
            source.write_text(
                "example.com##.outside\n"
                "!#if cap_html_filtering\n"
                "example.com##.inside\n"
                "!#else\n"
                "example.com##.alternate\n"
                "!#endif\n",
                encoding="utf-8",
            )

            records, stats = compile_sources([source])

            self.assertEqual([("H", "example.com", (), ".outside")], records)
            self.assertEqual(2, stats.conditional_lines)

    def test_uses_first_filter_delimiter_not_selector_literal(self):
        stats = CompileStats()

        self.assertEqual(
            [("H", "example.com", (), 'a[href="#@#"]')],
            parse_filter_line('example.com##a[href="#@#"]', stats),
        )

    def test_output_is_deterministic(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "rules.txt"
            source.write_text(
                "b.example,a.example##.ad\na.example#@#.ad\na.example##.ad\n",
                encoding="utf-8",
            )
            records, stats = compile_sources([source])
            first = Path(directory) / "first.txt"
            second = Path(directory) / "second.txt"
            write_asset(first, "abc", records, stats)
            write_asset(second, "abc", records, stats)

            self.assertEqual(first.read_bytes(), second.read_bytes())
            self.assertEqual(2, stats.hide_rules)
            self.assertEqual(1, stats.exception_rules)
            self.assertEqual(("b.example", 1, 3), maximum_pattern_load(records))
            self.assertEqual(("b.example", 1, 3), maximum_resolved_host_load(records))

    def test_easylist_generic_output_uses_v2_and_scoped_load_stays_bounded(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "rules.txt"
            source.write_text(
                "##.generic-ad\n"
                "##div[data-ad]\n"
                "news.example##.scoped-ad\n"
                "@@||quiet.example^$ghide\n",
                encoding="utf-8",
            )
            records, stats = compile_sources([source], include_generics=True)
            output = Path(directory) / "asset.txt"

            write_asset(output, "abc", records, stats)

            self.assertEqual(EASYLIST_V2_FORMAT_HEADER, output.read_text().splitlines()[0])
            self.assertEqual(2, stats.generic_rules)
            self.assertEqual(1, stats.generic_hide_exceptions)
            self.assertEqual(
                ("news.example", 1, len(".scoped-ad")),
                maximum_resolved_host_load(records, include_generics=False),
            )

    def test_ghide_badfilter_disables_matching_rule(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "rules.txt"
            source.write_text(
                "@@||news.example^$ghide\n"
                "@@||news.example^$badfilter,ghide\n",
                encoding="utf-8",
            )

            records, stats = compile_sources([source], include_generics=True)

            self.assertEqual([], records)
            self.assertEqual(0, stats.generic_hide_exceptions)


if __name__ == "__main__":
    unittest.main()
