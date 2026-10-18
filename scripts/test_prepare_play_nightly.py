#!/usr/bin/env python3

import argparse
import json
import os
from pathlib import Path
import tempfile
import unittest

from prepare_play_nightly import play_changelogs, prepare, validate_release


class PreparePlayNightlyTest(unittest.TestCase):
    VERSION = "0.45.1-nightly.202610120000"

    def test_uses_shared_nightly_code_and_accepts_source_release_candidate(self):
        for source in ("0.45.1", "0.45.1-rc.2"):
            with self.subTest(source=source):
                body, code = validate_release(self._release(), self.VERSION, source)
                self.assertEqual(self._release()["body"], body)
                self.assertEqual(3_565_440, code)

    def test_rejects_wrong_source_or_noncanonical_or_invalid_timestamp(self):
        for version, source in (
            (self.VERSION, "0.46"),
            ("0.45.1", "0.45.1"),
            ("0.45.1-nightly.202602300000", "0.45.1"),
            ("0.45.1-nightly.202610122460", "0.45.1"),
            ("0.45.1-nightly.202610120000\nnotes=oops", "0.45.1"),
        ):
            with self.subTest(version=version), self.assertRaises(ValueError):
                validate_release(self._release(), version, source)

    def test_requires_exact_published_prerelease_and_matching_body(self):
        for release in (
            None, [], {},
            self._release(isDraft=True),
            self._release(isPrerelease=False),
            self._release(isDraft="false"),
            self._release(tagName="v0.45.1-nightly.202610130000"),
            self._release(body=None),
            self._release(body="# Candy Browser Nightly wrong\n"),
            self._release(body=self._release()["body"] + "x" * 65_536),
        ):
            with self.subTest(release=release), self.assertRaises(ValueError):
                validate_release(release, self.VERSION, "0.45.1")

    def test_localized_play_notes_are_short_and_point_to_exact_release(self):
        changelogs = play_changelogs(self.VERSION, "owner/repo")
        self.assertEqual({"en-US", "de-DE"}, set(changelogs))
        for changelog in changelogs.values():
            self.assertLessEqual(len(changelog), 500)
            self.assertIn(f"https://github.com/owner/repo/releases/tag/v{self.VERSION}", changelog)
        with self.assertRaises(ValueError):
            play_changelogs(self.VERSION, "owner/repo\nunsafe")
        with self.assertRaises(ValueError):
            play_changelogs(self.VERSION, "owner/" + "r" * 500)

    def test_prepare_preserves_exact_notes_and_appends_environment_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            original_directory = Path.cwd()
            os.chdir(directory)
            try:
                Path("gradle.properties").write_text("candy.versionName=0.45.1-rc.2\n", encoding="utf-8")
                release = self._release(body=self._release()["body"] + "\nLast line, no newline.")
                Path("release.json").write_text(json.dumps(release), encoding="utf-8")
                Path("environment").write_text("EXISTING=value\n", encoding="utf-8")
                arguments = argparse.Namespace(
                    release=Path("release.json"), version=self.VERSION, repository="owner/repo",
                    whatsnew_directory=Path("whatsnew"), output=Path("environment"),
                )

                prepare(arguments)

                self.assertEqual(release["body"].encode(), Path(f"release-notes/{self.VERSION}.md").read_bytes())
                self.assertEqual(
                    f"EXISTING=value\nRELEASE_VERSION={self.VERSION}\nRELEASE_VERSION_CODE=3565440\n",
                    Path("environment").read_text(encoding="utf-8"),
                )
                self.assertEqual(2, len(list(Path("whatsnew").glob("whatsnew-*"))))
                self.assertEqual("candy.versionName=0.45.1-rc.2\n", Path("gradle.properties").read_text())
            finally:
                os.chdir(original_directory)

    def _release(self, **overrides):
        return {
            "tagName": f"v{self.VERSION}", "isDraft": False, "isPrerelease": True,
            "body": f"# Candy Browser Nightly {self.VERSION}\n\nExperimental development build.\n",
            **overrides,
        }


if __name__ == "__main__":
    unittest.main()
