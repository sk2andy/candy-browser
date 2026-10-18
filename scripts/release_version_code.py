#!/usr/bin/env python3

"""Validate a stored stable version code or recommend the current UTC minute."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path

from nightly_release import NIGHTLY_TAG, latest_nightly, nightly_version


def latest_nightly_code(release_pages: object) -> int | None:
    release = latest_nightly(release_pages)
    if release is None:
        return None
    stamp = NIGHTLY_TAG.fullmatch(release["tag_name"]).group(1)
    instant = datetime.strptime(stamp, "%Y%m%d%H%M").replace(tzinfo=timezone.utc)
    return nightly_version("0.0", instant)[1]


def validate_version_code(version_code: int, previous_code: int | None) -> int:
    if not 1 <= version_code <= 2_100_000_000:
        raise ValueError("Stable version code must be between 1 and 2100000000.")
    if previous_code is not None and version_code <= previous_code:
        raise ValueError(
            f"Stable version code {version_code} must be greater than latest published "
            f"Nightly version code {previous_code}. Prepare a newer stored version code."
        )
    return version_code


def recommended_version_code(instant: datetime, previous_code: int | None) -> int:
    code = nightly_version("0.0", instant)[1]
    if previous_code is not None and code <= previous_code:
        raise ValueError(
            "Current UTC minute must be newer than latest published Nightly. "
            "Wait for a later minute or correct the clock; do not reserve a future code."
        )
    return code


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--releases", type=Path, required=True)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--now", default=datetime.now(timezone.utc).isoformat())
    arguments = parser.parse_args()
    try:
        previous_code = latest_nightly_code(
            json.loads(arguments.releases.read_text(encoding="utf-8")),
        )
        if arguments.version_code is None:
            instant = datetime.fromisoformat(arguments.now.replace("Z", "+00:00"))
            version_code = recommended_version_code(instant, previous_code)
        else:
            version_code = validate_version_code(arguments.version_code, previous_code)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(version_code)


if __name__ == "__main__":
    main()
