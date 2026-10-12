#!/usr/bin/env python3
"""Print the filtered Stable/Nightly Obtainium setup table used in README.md."""

from dataclasses import dataclass
import json
from urllib.parse import quote


REPOSITORY_URL = "https://github.com/sk2andy/candy-browser"
APPLICATION_ID = "dev.sk2andy.materialbrowser"
VERSION_PATTERN = r"[0-9]+(?:\.[0-9]+){1,2}"


@dataclass(frozen=True)
class Variant:
    label: str
    app_name: str
    package_suffix: str
    asset_suffix: str


VARIANTS = (
    Variant("Standard · universal", "Candy Browser", "", "release"),
    Variant("Standard · ARM64", "Candy Browser", "", "arm64-v8a-release"),
    Variant("FOSS", "Candy FOSS", ".foss", "foss-release"),
    Variant("System WebView", "Candy System WebView", ".systemwebview", "systemwebview-release"),
    Variant("User CA", "Candy CA", ".ca", "ca-release"),
)


def setup_link(variant: Variant, nightly: bool) -> str:
    version_pattern = VERSION_PATTERN + (r"-nightly\.[0-9]{12}" if nightly else "")
    asset_suffix = (
        r"foss(?:-github)?-release" if not nightly and variant.asset_suffix == "foss-release"
        else variant.asset_suffix
    )
    settings = {
        "includePrereleases": nightly,
        "filterReleaseTitlesByRegEx": (
            r"^Candy Browser Nightly " if nightly else rf"^Candy Browser v{VERSION_PATTERN}$"
        ),
        "apkFilterRegEx": rf"^CandyBrowser-v{version_pattern}-{asset_suffix}\.apk$",
        "sortMethodChoice": "date",
    }
    app = {
        "id": APPLICATION_ID + variant.package_suffix + (".nightly" if nightly else ""),
        "url": REPOSITORY_URL,
        "author": "sk2andy",
        "name": variant.app_name + (" Nightly" if nightly else ""),
        "preferredApkIndex": 0,
        "additionalSettings": json.dumps(settings, separators=(",", ":")),
    }
    redirect = "obtainium://app/" + json.dumps(app, separators=(",", ":"))
    return "https://apps.obtainium.imranr.dev/redirect?r=" + quote(redirect, safe="")


def markdown_table() -> str:
    rows = ["| Variant | Stable | Nightly |", "| --- | --- | --- |"]
    for variant in VARIANTS:
        rows.append(
            f"| {variant.label} | [Stable]({setup_link(variant, False)}) "
            f"| [Nightly]({setup_link(variant, True)}) |",
        )
    return "\n".join(rows)


if __name__ == "__main__":
    print(markdown_table())
