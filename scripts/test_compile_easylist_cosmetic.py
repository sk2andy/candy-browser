#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from compile_easylist_cosmetic import (
    CompileStats,
    compile_sources,
    maximum_pattern_load,
    maximum_resolved_host_load,
    parse_filter_line,
    write_asset,
)


class CosmeticCompilerTest(unittest.TestCase):
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
        self.assertEqual(1, stats.generic_rules)
        self.assertEqual(2, stats.unsupported_rules)

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


if __name__ == "__main__":
    unittest.main()
