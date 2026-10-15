#!/usr/bin/env python3

"""Prepare immutable Nightly metadata from published releases and checked-out main."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess


BASE_VERSION = re.compile(
    r"([0-9]+(?:\.[0-9]+){1,2})(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?",
)
NIGHTLY_TAG = re.compile(r"v[0-9]+(?:\.[0-9]+){1,2}-nightly\.([0-9]{12})")
SHA = re.compile(r"[0-9a-f]{40}")
VERSION_EPOCH = datetime(2020, 1, 1, tzinfo=timezone.utc)


def latest_nightly(release_pages: object) -> dict | None:
    if not isinstance(release_pages, list) or not all(
        isinstance(page, list) for page in release_pages
    ):
        raise ValueError("Expected paginated GitHub release arrays.")
    candidates = []
    for page in release_pages:
        for release in page:
            if not isinstance(release, dict):
                raise ValueError("Invalid GitHub release object.")
            tag = release.get("tag_name")
            if not isinstance(tag, str) or not isinstance(release.get("draft"), bool):
                raise ValueError("Invalid GitHub release metadata.")
            tag_match = NIGHTLY_TAG.fullmatch(tag)
            if release["draft"] or tag_match is None:
                continue
            stamp = datetime.strptime(tag_match.group(1), "%Y%m%d%H%M")
            if stamp.strftime("%Y%m%d%H%M") != tag_match.group(1):
                raise ValueError("Nightly tag timestamp is invalid.")
            if release.get("prerelease") is not True:
                raise ValueError("Published Nightly must be a prerelease.")
            published = release.get("published_at")
            if not isinstance(published, str):
                raise ValueError("Published Nightly has no publication time.")
            instant = datetime.fromisoformat(published.replace("Z", "+00:00"))
            if instant.tzinfo is None:
                raise ValueError("Publication time must include a timezone.")
            candidates.append((stamp, tag, release))
    return max(candidates, key=lambda candidate: candidate[:2])[2] if candidates else None


def nightly_version(base: str, instant: datetime) -> tuple[str, int]:
    base_match = BASE_VERSION.fullmatch(base)
    if base_match is None:
        raise ValueError("Nightly base version must be a valid release version.")
    if instant.tzinfo is None:
        raise ValueError("Nightly time must include a timezone.")
    utc = instant.astimezone(timezone.utc)
    code = int((utc - VERSION_EPOCH).total_seconds() // 60)
    if not 1 <= code <= 2_100_000_000:
        raise ValueError("Nightly version code is outside Android limits.")
    return f"{base_match.group(1)}-nightly.{utc:%Y%m%d%H%M}", code


def git(*arguments: str) -> str:
    return subprocess.run(
        ["git", *arguments], check=True, capture_output=True, text=True,
    ).stdout.strip()


def sanitize_summary(subject: str) -> str:
    subject = " ".join("".join(
        character for character in subject if ord(character) >= 32 and ord(character) != 127
    ).split())[:180]
    return re.sub(r"([\\`*_{}\[\]()<>#!|])", r"\\\1", subject)


def release_notes(version: str, sha: str, previous_sha: str | None,
                  repository: str, subjects: list[str]) -> str:
    if SHA.fullmatch(sha) is None or (
        previous_sha is not None and SHA.fullmatch(previous_sha) is None
    ):
        raise ValueError("Release notes require full Git commit hashes.")
    if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository) is None:
        raise ValueError("Repository must be owner/name.")
    lines = [
        f"# Candy Browser Nightly {version}",
        "",
        "Experimental development build from main. Features may change before a stable release.",
        "",
        f"[Source revision](https://github.com/{repository}/commit/{sha})",
    ]
    if previous_sha:
        lines.extend([
            f"[Changes since previous Nightly](https://github.com/{repository}/compare/"
            f"{previous_sha}...{sha})",
        ])
    summaries = []
    for subject in subjects:
        match = re.match(r"^(?:feat|fix|perf)(?:\([^)]*\))?!?:\s*(.+)$", subject)
        if match:
            summary = sanitize_summary(match.group(1))
            if summary and summary not in summaries:
                summaries.append(summary)
    if summaries:
        lines.extend(["", "## Changes", ""])
        lines.extend(f"- {summary}" for summary in summaries[:40])
    return "\n".join(lines) + "\n"


def prepare(arguments: argparse.Namespace) -> None:
    releases = json.loads(arguments.releases.read_text(encoding="utf-8"))
    previous = latest_nightly(releases)
    sha = git("rev-parse", "HEAD^{commit}")
    if SHA.fullmatch(sha) is None:
        raise ValueError("Checked-out main did not resolve to a full commit hash.")
    previous_sha = git("rev-parse", f"refs/tags/{previous['tag_name']}^{{commit}}") if previous else None
    if sha == previous_sha:
        arguments.output.write_text("build=false\n", encoding="utf-8")
        return
    base_versions = re.findall(
        r"^candy\.versionName=(.+)$",
        Path("gradle.properties").read_text(encoding="utf-8"), re.MULTILINE,
    )
    if len(base_versions) != 1:
        raise ValueError("Expected one candy.versionName in gradle.properties.")
    instant = datetime.fromisoformat(arguments.now.replace("Z", "+00:00"))
    version, code = nightly_version(base_versions[0], instant)
    if previous:
        previous_stamp = NIGHTLY_TAG.fullmatch(previous["tag_name"]).group(1)
        if version.rsplit(".", 1)[1] <= previous_stamp:
            raise ValueError("Nightly timestamp must be newer than previous published Nightly.")
    tag = f"v{version}"
    if any(release["tag_name"] == tag for page in releases for release in page):
        raise ValueError(f"Nightly release or draft already exists: {tag}")
    existing = subprocess.run(
        ["git", "show-ref", "--verify", "--quiet", f"refs/tags/{tag}"],
        check=False,
    )
    if existing.returncode == 0:
        raise ValueError(f"Nightly tag already exists: {tag}")
    if existing.returncode != 1:
        raise RuntimeError("Could not check existing Nightly tags.")
    revision = f"{previous_sha}..{sha}" if previous_sha else sha
    subjects = git("log", "-100", "--format=%s", revision).splitlines()
    notes = Path("release-notes") / f"{version}.md"
    notes.parent.mkdir(parents=True, exist_ok=True)
    notes.write_text(
        release_notes(version, sha, previous_sha, arguments.repository, subjects),
        encoding="utf-8",
    )
    arguments.output.write_text(
        f"build=true\nsha={sha}\nversion={version}\nversion_code={code}\n"
        f"tag={tag}\nnotes={notes.as_posix()}\n", encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--releases", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--now", default=datetime.now(timezone.utc).isoformat())
    prepare(parser.parse_args())


if __name__ == "__main__":
    main()
