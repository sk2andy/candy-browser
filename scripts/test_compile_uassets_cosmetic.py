#!/usr/bin/env python3

import base64
import tempfile
import unittest
from pathlib import Path

from compile_easylist_cosmetic import (
    compile_sources,
    maximum_pattern_load,
    write_asset,
)


PROJECT_DIR = Path(__file__).resolve().parents[1]
SOURCE = PROJECT_DIR / "app/src/main/assets/uassets_filters_source.txt"
ASSET = PROJECT_DIR / "app/src/main/assets/uassets_cosmetic_rules.txt"
REVISION = "05bc031ad40c2270223f068f052970201ca1bf14"


class UassetsCosmeticCompilerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.records, cls.stats = compile_sources([SOURCE])

    def test_pinned_snapshot_counts(self):
        self.assertEqual(2_002, self.stats.hide_rules)
        self.assertEqual(50, self.stats.exception_rules)
        self.assertEqual(51, self.stats.generic_rules)
        self.assertEqual(2_679, self.stats.unsupported_rules)
        self.assertEqual(89, self.stats.conditional_lines)
        self.assertEqual(1_124, len({record[1] for record in self.records}))
        self.assertEqual(("pornhub.*", 25, 620), maximum_pattern_load(self.records))

    def test_representative_localized_rules_and_procedural_exclusions(self):
        selectors = {
            (action, host, selector)
            for action, host, _, selector in self.records
        }

        self.assertIn(("H", "bild.de", ".ad-wrapper"), selectors)
        self.assertIn(("H", "elmundo.es", ".Bloque-anuncios"), selectors)
        self.assertIn(("H", "t-online.de", "#T-Shopping"), selectors)
        self.assertIn(
            ("H", "reddit.com", 'div[class][data-before-content="Werbung"]:not([id])'),
            selectors,
        )
        self.assertFalse(
            any(
                token in selector
                for _, _, selector in selectors
                for token in (":matches-css", ":matches-attr", ":remove-attr")
            ),
        )

    def test_generated_asset_matches_checked_in_asset(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "uassets_cosmetic_rules.txt"

            write_asset(output, REVISION, self.records, self.stats, "uassets")

            self.assertEqual(ASSET.read_bytes(), output.read_bytes())

    def test_asset_rows_decode_to_compiled_records(self):
        rows = []
        for line in ASSET.read_text(encoding="utf-8").splitlines():
            if not line or line.startswith("#") or line.startswith("candy-"):
                continue
            action, host, exclusions, encoded = line.split("\t")
            selector = base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)).decode()
            rows.append((action, host, () if exclusions == "-" else tuple(exclusions.split(",")), selector))

        self.assertEqual(self.records, rows)


if __name__ == "__main__":
    unittest.main()
