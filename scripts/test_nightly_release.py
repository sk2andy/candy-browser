#!/usr/bin/env python3

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from nightly_release import latest_nightly, nightly_version, prepare, release_notes


class NightlyReleaseTest(unittest.TestCase):
    def test_selects_latest_published_nightly_across_all_pages(self):
        older = self._release("v0.45.1-nightly.202610110000", "2026-10-11T01:00:00Z")
        newer = self._release("v0.45.1-nightly.202610120000", "2026-10-12T01:00:00Z")
        draft = self._release("v0.45.1-nightly.202610130000", None, draft=True)
        stable = self._release("v0.46", "2026-10-13T02:00:00Z", prerelease=False)

        self.assertEqual(newer, latest_nightly([[draft, stable], [older, newer]]))

    def test_no_nightly_is_valid_for_first_build(self):
        self.assertIsNone(latest_nightly([[]]))
        self.assertIsNone(latest_nightly([[self._release("v0.45", "2026-10-01T00:00:00Z")]]))

    def test_republishing_old_nightly_does_not_hide_newer_source(self):
        older = self._release("v0.45-nightly.202610110000", "2026-10-13T01:00:00Z")
        newer = self._release("v0.45-nightly.202610120000", "2026-10-12T01:00:00Z")
        self.assertEqual(newer, latest_nightly([[older, newer]]))

    def test_invalid_api_response_fails_closed(self):
        for response in ({"message": "API rate limit exceeded"}, [None], [[None]], [[{}]]):
            with self.subTest(response=response), self.assertRaises(ValueError):
                latest_nightly(response)

    def test_invalid_published_nightly_fails_closed(self):
        for release in (
            self._release("v0.45-nightly.202610120000", None),
            self._release("v0.45-nightly.202610120000", "2026-10-12T00:00:00"),
            self._release("v0.45-nightly.202610120000", "bad-date"),
            self._release("v0.45-nightly.202602300000", "2026-10-12T00:00:00Z"),
            self._release("v0.45-nightly.202610120000", "2026-10-12T00:00:00Z", prerelease=False),
        ):
            with self.subTest(release=release), self.assertRaises(ValueError):
                latest_nightly([[release]])

    def test_fixed_cest_time_and_android_version_code_are_utc_based(self):
        version, code = nightly_version("0.45.1", datetime.fromisoformat("2026-10-12T02:00:59+02:00"))
        self.assertEqual("0.45.1-nightly.202610120000", version)
        self.assertEqual(3_565_440, code)
        later = nightly_version("0.46", datetime.fromisoformat("2026-10-13T00:00:00+00:00"))
        self.assertEqual(1440, later[1] - code)

    def test_rejects_unsafe_version_and_naive_time(self):
        for version in ("0.45\nnotes=oops", "0.45-rc..1", "../0.45", "0"):
            with self.subTest(version=version), self.assertRaises(ValueError):
                nightly_version(version, datetime.now(timezone.utc))
        with self.assertRaises(ValueError):
            nightly_version("0.45", datetime(2026, 10, 12))

    def test_builds_nightly_while_main_prepares_a_release_candidate(self):
        version, code = nightly_version(
            "0.46-rc.1", datetime.fromisoformat("2026-10-12T00:00:00+00:00"),
        )
        self.assertEqual("0.46-nightly.202610120000", version)
        self.assertEqual(3_565_440, code)

    def test_notes_escape_commit_markdown_and_bound_large_history(self):
        notes = release_notes(
            "0.45-nightly.202610120000", "a" * 40, "b" * 40, "owner/repo",
            ["fix: ![image](https://evil.example/a.png) ```\x00 # title"]
            + [f"feat: Feature {index} " + "z" * 9000 for index in range(1000)],
        )
        self.assertTrue(notes.startswith("# Candy Browser Nightly "))
        self.assertIn("/compare/" + "b" * 40 + "..." + "a" * 40, notes)
        self.assertNotIn("![image]", notes)
        self.assertNotIn("```", notes)
        self.assertNotIn("\x00", notes)
        self.assertLessEqual(len(notes.encode("utf-8")), 65536)
        self.assertLessEqual(sum(bool(line.strip()) for line in notes.splitlines()), 256)
        self.assertTrue(all(len(line) <= 8192 for line in notes.splitlines()))

    def test_prepare_skips_unchanged_source_and_builds_new_main_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            original_directory = Path.cwd()
            os.chdir(directory)
            try:
                self._git("init", "--quiet")
                self._git("config", "user.email", "test@example.com")
                self._git("config", "user.name", "Test")
                Path("gradle.properties").write_text("candy.versionName=0.45.1\n", encoding="utf-8")
                self._git("add", "gradle.properties")
                self._git("commit", "--quiet", "-m", "feat: Initial browser")
                tag = "v0.45.1-nightly.202610110000"
                self._git("tag", tag)
                previous_sha = self._git("rev-parse", "HEAD")
                arguments = self._arguments(directory, tag)

                prepare(arguments)
                self.assertEqual("build=false\n", arguments.output.read_text(encoding="utf-8"))
                self.assertFalse(Path("release-notes").exists())

                self._git("commit", "--quiet", "--allow-empty", "-m", "fix: Reader crash")
                prepare(arguments)
                values = dict(line.split("=", 1) for line in arguments.output.read_text().splitlines())
                self.assertEqual("true", values["build"])
                self.assertEqual(self._git("rev-parse", "HEAD"), values["sha"])
                self.assertEqual("v0.45.1-nightly.202610120000", values["tag"])
                notes = Path(values["notes"]).read_text(encoding="utf-8")
                self.assertIn("Reader crash", notes)
                self.assertNotIn("Initial browser", notes)
                self.assertIn(previous_sha, notes)
                self.assertEqual("", self._git("diff", "--", "gradle.properties"))
                self.assertEqual(tag, self._git("tag", "--list"))
            finally:
                os.chdir(original_directory)

    def test_missing_published_tag_aborts_instead_of_rebuilding(self):
        with tempfile.TemporaryDirectory() as directory:
            original_directory = Path.cwd()
            os.chdir(directory)
            try:
                self._git("init", "--quiet")
                self._git("config", "user.email", "test@example.com")
                self._git("config", "user.name", "Test")
                self._git("commit", "--quiet", "--allow-empty", "-m", "Initial")
                arguments = self._arguments(directory, "v0.45.1-nightly.202610110000")
                with self.assertRaises(subprocess.CalledProcessError):
                    prepare(arguments)
                self.assertFalse(arguments.output.exists())
            finally:
                os.chdir(original_directory)

    def _release(self, tag, published, draft=False, prerelease=True):
        return {
            "tag_name": tag, "draft": draft, "prerelease": prerelease,
            "published_at": published,
        }

    def _git(self, *arguments):
        return subprocess.run(
            ["git", *arguments], check=True, capture_output=True, text=True,
        ).stdout.strip()

    def _arguments(self, directory, tag):
        releases = Path(directory) / "releases.json"
        releases.write_text(json.dumps([[
            self._release(tag, "2026-10-11T01:00:00Z"),
        ]]), encoding="utf-8")
        return argparse.Namespace(
            releases=releases, output=Path(directory) / "outputs.txt",
            repository="owner/repo", now="2026-10-12T00:00:00Z",
        )


if __name__ == "__main__":
    unittest.main()
