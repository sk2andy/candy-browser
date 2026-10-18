#!/usr/bin/env python3

from datetime import datetime, timezone
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from release_version_code import (
    latest_nightly_code,
    recommended_version_code,
    validate_version_code,
)


class ReleaseVersionCodeTest(unittest.TestCase):
    def test_derives_latest_code_from_tag_instead_of_publication_time(self):
        older = self._release("202610110000", "2026-10-14T00:00:00Z")
        newer = self._release("202610120000", "2026-10-12T01:00:00Z")
        self.assertEqual(3_565_440, latest_nightly_code([[older], [newer]]))

    def test_no_nightly_allows_valid_stored_code(self):
        self.assertIsNone(latest_nightly_code([[]]))
        self.assertEqual(47000, validate_version_code(47000, None))

    def test_stored_code_must_be_strictly_newer_than_nightly(self):
        for code in (3_565_439, 3_565_440):
            with self.subTest(code=code), self.assertRaisesRegex(ValueError, "greater than"):
                validate_version_code(code, 3_565_440)
        self.assertEqual(3_565_441, validate_version_code(3_565_441, 3_565_440))

    def test_rejects_stored_codes_outside_android_limits(self):
        for code in (-1, 0, 2_100_000_001):
            with self.subTest(code=code), self.assertRaisesRegex(ValueError, "between"):
                validate_version_code(code, None)

    def test_recommends_actual_current_utc_minute(self):
        instant = datetime.fromisoformat("2026-10-12T02:01:59+02:00")
        self.assertEqual(3_565_441, recommended_version_code(instant, 3_565_440))
        self.assertEqual(3_565_441, recommended_version_code(instant, None))

    def test_equal_or_future_published_nightly_does_not_allocate_future_code(self):
        instant = datetime(2026, 10, 12, tzinfo=timezone.utc)
        for previous_code in (3_565_440, 3_565_441):
            with self.subTest(previous_code=previous_code), self.assertRaisesRegex(
                ValueError, "do not reserve a future code",
            ):
                recommended_version_code(instant, previous_code)

    def test_recommendation_rejects_naive_clock(self):
        with self.assertRaisesRegex(ValueError, "timezone"):
            recommended_version_code(datetime(2026, 10, 12), None)

    def test_reuses_release_array_and_timestamp_validation(self):
        for releases in (
            {}, [None], [[None]], [[{}]],
            [[self._release("202602300000")]],
            [[self._release("202610120000", "2026-10-12T00:00:00")]],
        ):
            with self.subTest(releases=releases), self.assertRaises(ValueError):
                latest_nightly_code(releases)

    def test_cli_validates_source_and_prints_recommended_code(self):
        with tempfile.TemporaryDirectory() as directory:
            releases = Path(directory) / "releases.json"
            releases.write_text(json.dumps([[self._release("202610120000")]]), encoding="utf-8")
            for arguments in (
                ("--version-code", "3565441"),
                ("--now", "2026-10-12T00:01:00Z"),
            ):
                with self.subTest(arguments=arguments):
                    result = self._cli(releases, *arguments)
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual("3565441\n", result.stdout)
            for arguments in (
                ("--version-code", "3565440"),
                ("--now", "2026-10-12T00:00:00Z"),
                ("--now", "invalid"),
            ):
                with self.subTest(arguments=arguments):
                    result = self._cli(releases, *arguments)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual("", result.stdout)

    def _cli(self, releases, *arguments):
        return subprocess.run(
            [sys.executable, str(Path(__file__).with_name("release_version_code.py")),
             "--releases", str(releases), *arguments],
            check=False, capture_output=True, text=True,
        )

    def _release(self, stamp, published="2026-10-12T01:00:00Z"):
        return {
            "tag_name": f"v0.47.0-nightly.{stamp}",
            "draft": False,
            "prerelease": True,
            "published_at": published,
        }


if __name__ == "__main__":
    unittest.main()
