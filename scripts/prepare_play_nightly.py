#!/usr/bin/env python3

"""Validate an immutable GitHub Nightly and prepare its Google Play metadata."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re

from nightly_release import NIGHTLY_TAG, nightly_version


def validate_release(release: object, version: str, source_version: str) -> tuple[str, int]:
    tag = f"v{version}"
    match = NIGHTLY_TAG.fullmatch(tag)
    if match is None:
        raise ValueError("Play Nightly requires an immutable timestamped Nightly tag.")
    instant = datetime.strptime(match.group(1), "%Y%m%d%H%M").replace(tzinfo=timezone.utc)
    expected_version, code = nightly_version(source_version, instant)
    if version != expected_version:
        raise ValueError("Nightly version must match the checked-out source base and UTC timestamp.")
    if not isinstance(release, dict) or (
        release.get("tagName") != tag
        or release.get("isDraft") is not False
        or release.get("isPrerelease") is not True
    ):
        raise ValueError("Play Nightly requires the matching published GitHub prerelease.")
    body = release.get("body")
    if not isinstance(body, str) or not body.startswith(f"# Candy Browser Nightly {version}\n"):
        raise ValueError("Nightly release notes must start with the matching version heading.")
    if len(body.encode("utf-8")) > 65_536:
        raise ValueError("Nightly release notes exceed the bundled notes size limit.")
    return body, code


def play_changelogs(version: str, repository: str) -> dict[str, str]:
    if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is None:
        raise ValueError("Repository must be owner/name.")
    link = f"https://github.com/{repository}/releases/tag/v{version}"
    changelogs = {
        "en-US": (
            f"Candy Browser Nightly {version}\n"
            "Experimental development build. Features may change before a stable release.\n"
            f"Changes and source: {link}\n"
        ),
        "de-DE": (
            f"Candy Browser Nightly {version}\n"
            "Experimenteller Entwicklungsstand. Funktionen können sich bis zur stabilen Version ändern.\n"
            f"Änderungen und Quellcode: {link}\n"
        ),
    }
    if any(len(changelog) > 500 for changelog in changelogs.values()):
        raise ValueError("Nightly Play changelogs must not exceed 500 characters.")
    return changelogs


def prepare(arguments: argparse.Namespace) -> None:
    source_versions = re.findall(
        r"^candy\.versionName=(.+)$",
        Path("gradle.properties").read_text(encoding="utf-8"), re.MULTILINE,
    )
    if len(source_versions) != 1:
        raise ValueError("Expected one candy.versionName in gradle.properties.")
    release = json.loads(arguments.release.read_text(encoding="utf-8"))
    body, code = validate_release(release, arguments.version, source_versions[0])
    changelogs = play_changelogs(arguments.version, arguments.repository)
    notes = Path("release-notes") / f"{arguments.version}.md"
    notes.parent.mkdir(parents=True, exist_ok=True)
    notes.write_text(body, encoding="utf-8")
    arguments.whatsnew_directory.mkdir(parents=True, exist_ok=True)
    for locale, changelog in changelogs.items():
        (arguments.whatsnew_directory / f"whatsnew-{locale}").write_text(changelog, encoding="utf-8")
    with arguments.output.open("a", encoding="utf-8") as output:
        output.write(f"RELEASE_VERSION={arguments.version}\nRELEASE_VERSION_CODE={code}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release", type=Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--whatsnew-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    prepare(parser.parse_args())


if __name__ == "__main__":
    main()
