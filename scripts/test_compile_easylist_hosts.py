#!/usr/bin/env python3

import tempfile
import unittest
from pathlib import Path

from compile_easylist_hosts import compile_sources, template_sources, write_assets


PROJECT_DIR = Path(__file__).resolve().parents[1]
PINNED_BLOCKED_ASSET = PROJECT_DIR / "app/src/main/assets/easylist_blocked_hosts.txt"
PINNED_ALLOWED_ASSET = PROJECT_DIR / "app/src/main/assets/easylist_allowed_host_pairs.txt"


class EasyListHostCompilerTest(unittest.TestCase):
    def compile(self, source: str):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "filters.txt"
            path.write_text(source, encoding="utf-8")
            return compile_sources([path])

    def test_compiles_host_and_third_party_rules_with_scoped_allows(self):
        blocked, allowed, stats = self.compile(
            "||tracker.example^$third-party\n"
            "||ads.example^\n"
            "@@||tracker.example^$script,domain=news.example|video.example\n"
        )

        self.assertEqual(["ads.example", "tracker.example"], blocked)
        self.assertEqual(
            [("tracker.example", "news.example"), ("tracker.example", "video.example")],
            allowed,
        )
        self.assertEqual(2, stats.blocked_hosts)

    def test_compiles_every_file_referenced_by_both_templates(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "easylist.template").write_text(
                "%include easylist:easylist/network.txt%\n",
                encoding="utf-8",
            )
            (root / "easyprivacy.template").write_text(
                "%include easylist:easyprivacy/network.txt%\n",
                encoding="utf-8",
            )
            (root / "easylist").mkdir()
            (root / "easyprivacy").mkdir()
            (root / "easylist/network.txt").write_text(
                "||ads.example^\n",
                encoding="utf-8",
            )
            (root / "easyprivacy/network.txt").write_text(
                "||tracker.example^$third-party\n",
                encoding="utf-8",
            )

            sources = template_sources(
                root,
                ["easylist.template", "easyprivacy.template"],
            )
            blocked, _, _ = compile_sources(sources)

            self.assertEqual(["ads.example", "tracker.example"], blocked)

    def test_rejects_typed_blocks_and_unscoped_or_negative_allows(self):
        blocked, allowed, stats = self.compile(
            "||safe.example^\n"
            "||image.example^$image\n"
            "@@||safe.example^$script\n"
            "@@||safe.example^$domain=~news.example\n"
        )

        self.assertEqual(["safe.example"], blocked)
        self.assertEqual([], allowed)
        self.assertEqual(1, stats.unsupported_lines)

    def test_badfilter_disables_rule_across_order(self):
        blocked, _, stats = self.compile(
            "||disabled.example^$third-party\n"
            "||kept.example^\n"
            "||disabled.example^$badfilter,third-party\n"
        )

        self.assertEqual(["kept.example"], blocked)
        self.assertEqual(1, stats.badfilter_lines)

    def test_output_is_sorted_deterministic_and_declares_source_graph(self):
        blocked, allowed, stats = self.compile(
            "||b.example^\n||a.example^\n@@||a.example^$domain=page.example\n"
        )
        with tempfile.TemporaryDirectory() as directory:
            first_block = Path(directory) / "first-block.txt"
            first_allow = Path(directory) / "first-allow.txt"
            second_block = Path(directory) / "second-block.txt"
            second_allow = Path(directory) / "second-allow.txt"
            write_assets(first_block, first_allow, "revision", blocked, allowed, stats)
            write_assets(second_block, second_allow, "revision", blocked, allowed, stats)

            self.assertEqual(first_block.read_bytes(), second_block.read_bytes())
            self.assertEqual(first_allow.read_bytes(), second_allow.read_bytes())
            self.assertIn("easylist.template + easyprivacy.template", first_block.read_text())

    def test_checked_in_assets_are_bounded_sorted_and_unique(self):
        blocked_lines = PINNED_BLOCKED_ASSET.read_text(encoding="utf-8").splitlines()
        allowed_lines = PINNED_ALLOWED_ASSET.read_text(encoding="utf-8").splitlines()
        blocked = [line for line in blocked_lines if line and not line.startswith("#")]
        allowed = [line for line in allowed_lines if line and not line.startswith("#")]

        self.assertIn("# Generated hosts: 100377", blocked_lines)
        self.assertIn("# Generated host pairs: 115", allowed_lines)
        self.assertEqual(sorted(blocked), blocked)
        self.assertEqual(len(set(blocked)), len(blocked))
        self.assertEqual(sorted(allowed), allowed)
        self.assertEqual(len(set(allowed)), len(allowed))
        self.assertLessEqual(PINNED_BLOCKED_ASSET.stat().st_size, 3_500_000)


if __name__ == "__main__":
    unittest.main()
