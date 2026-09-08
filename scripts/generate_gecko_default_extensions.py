#!/usr/bin/env python3
"""Download and verify pinned Firefox default-extension XPIs without repacking them."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import tempfile
from typing import Any
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from zipfile import BadZipFile, ZipFile


MAX_ARCHIVE_BYTES = 16 * 1024 * 1024
MAX_EXPANDED_BYTES = 64 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 5_000
SIGNATURE_ENTRIES = {
    "META-INF/cose.sig",
    "META-INF/mozilla.rsa",
    "META-INF/mozilla.sf",
}


def load_catalog(path: Path) -> list[dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    if root.get("schemaVersion") != 1:
        raise ValueError("unsupported catalog schema")
    extensions = root.get("extensions")
    if not isinstance(extensions, list) or not extensions:
        raise ValueError("catalog extensions must be a non-empty list")
    ids: set[str] = set()
    for extension in extensions:
        _validate_catalog_entry(extension)
        extension_id = extension["id"]
        if extension_id in ids:
            raise ValueError(f"duplicate extension id: {extension_id}")
        ids.add(extension_id)
    return extensions


def _validate_catalog_entry(extension: dict[str, Any]) -> None:
    for key in (
        "id",
        "name",
        "version",
        "delivery",
        "installUri",
        "sourceUrl",
        "sourceCodeUrl",
        "license",
        "sha256",
        "size",
    ):
        if key not in extension:
            raise ValueError(f"catalog entry missing {key}")
    for key in ("id", "name", "version", "installUri", "sourceUrl", "sourceCodeUrl"):
        value = extension[key]
        if not isinstance(value, str) or not value or any(ord(character) < 32 for character in value):
            raise ValueError(f"invalid catalog {key}")
    if extension["license"] != "GPL-3.0-only":
        raise ValueError("default extensions must have audited GPL-3.0-only metadata")
    if not isinstance(extension["sha256"], str) or len(extension["sha256"]) != 64:
        raise ValueError("invalid SHA-256")
    if any(character not in "0123456789abcdef" for character in extension["sha256"]):
        raise ValueError("SHA-256 must use lowercase hexadecimal")
    if not isinstance(extension["size"], int) or not 1 <= extension["size"] <= MAX_ARCHIVE_BYTES:
        raise ValueError("invalid archive size")
    source_url = urlparse(extension["sourceUrl"])
    if (
        source_url.scheme != "https"
        or source_url.hostname != "addons.mozilla.org"
        or not source_url.path.startswith("/firefox/downloads/file/")
        or source_url.query
        or source_url.fragment
    ):
        raise ValueError("source URL must be a pinned addons.mozilla.org file URL")
    delivery = extension["delivery"]
    if delivery == "bundled":
        asset_path = extension.get("assetPath")
        if not isinstance(asset_path, str) or not _safe_asset_path(asset_path):
            raise ValueError("bundled extension needs a safe XPI asset path")
        if extension["installUri"] != f"resource://android/assets/{asset_path}":
            raise ValueError("bundled install URI must match asset path")
        source_code_url = urlparse(extension["sourceCodeUrl"])
        if (
            source_code_url.scheme != "https"
            or source_code_url.hostname != "github.com"
            or not re.fullmatch(
                r"/[^/]+/[^/]+/tree/[0-9a-f]{40}",
                source_code_url.path,
            )
            or source_code_url.query
            or source_code_url.fragment
        ):
            raise ValueError("bundled corresponding source must use an immutable Git commit URL")
    elif delivery == "remote":
        if "assetPath" in extension:
            raise ValueError("remote extension cannot declare assetPath")
        if extension["installUri"] != extension["sourceUrl"]:
            raise ValueError("remote install URI must equal pinned source URL")
    else:
        raise ValueError(f"unsupported delivery: {delivery}")


def _safe_asset_path(raw_path: str) -> bool:
    path = PurePosixPath(raw_path)
    return (
        not path.is_absolute()
        and ".." not in path.parts
        and "\\" not in raw_path
        and path.parts[:1] == ("gecko_default_extensions",)
        and path.suffix == ".xpi"
    )


def validate_xpi(data: bytes, extension: dict[str, Any]) -> None:
    if len(data) != extension["size"]:
        raise ValueError(
            f"{extension['name']} size mismatch: expected {extension['size']}, got {len(data)}",
        )
    actual_hash = hashlib.sha256(data).hexdigest()
    if actual_hash != extension["sha256"]:
        raise ValueError(
            f"{extension['name']} SHA-256 mismatch: expected {extension['sha256']}, got {actual_hash}",
        )
    try:
        with tempfile.TemporaryFile() as archive_file:
            archive_file.write(data)
            archive_file.seek(0)
            with ZipFile(archive_file) as archive:
                entries = archive.infolist()
                if not entries or len(entries) > MAX_ARCHIVE_ENTRIES:
                    raise ValueError("XPI archive entry count outside bounds")
                names = [entry.filename for entry in entries]
                if len(names) != len(set(names)):
                    raise ValueError("XPI archive contains duplicate paths")
                if any(not _safe_archive_entry(name) for name in names):
                    raise ValueError("XPI archive contains an unsafe path")
                if sum(entry.file_size for entry in entries) > MAX_EXPANDED_BYTES:
                    raise ValueError("XPI expanded size exceeds limit")
                if archive.testzip() is not None:
                    raise ValueError("XPI archive CRC validation failed")
                if not SIGNATURE_ENTRIES.issubset(names):
                    raise ValueError("XPI lacks Mozilla signature records")
                if not any(PurePosixPath(name).name.lower().startswith("license") for name in names):
                    raise ValueError("XPI lacks a license notice")
                manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    except (BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("invalid XPI archive") from error
    gecko = manifest.get("browser_specific_settings", {}).get("gecko", {})
    legacy_gecko = manifest.get("applications", {}).get("gecko", {})
    extension_id = gecko.get("id") or legacy_gecko.get("id")
    if extension_id != extension["id"]:
        raise ValueError(f"XPI extension id mismatch: {extension_id}")
    if manifest.get("version") != extension["version"]:
        raise ValueError(f"XPI extension version mismatch: {manifest.get('version')}")


def _safe_archive_entry(raw_path: str) -> bool:
    path = PurePosixPath(raw_path)
    return not path.is_absolute() and ".." not in path.parts and "\\" not in raw_path


def verify_local_assets(catalog_path: Path, assets_root: Path) -> None:
    extensions = load_catalog(catalog_path)
    expected_assets: set[Path] = set()
    for extension in extensions:
        if extension["delivery"] != "bundled":
            continue
        asset = assets_root / extension["assetPath"]
        expected_assets.add(asset.resolve())
        if not asset.is_file():
            raise ValueError(f"missing bundled XPI: {asset}")
        validate_xpi(asset.read_bytes(), extension)
    extension_directory = assets_root / "gecko_default_extensions"
    unexpected = {
        path.resolve()
        for path in extension_directory.glob("*.xpi")
        if path.resolve() not in expected_assets
    }
    if unexpected:
        raise ValueError(f"unexpected bundled XPI: {sorted(map(str, unexpected))}")


def download_and_validate(extension: dict[str, Any]) -> bytes:
    request = Request(
        extension["sourceUrl"],
        headers={"User-Agent": "Candy-Browser-default-extension-audit/1"},
    )
    with urlopen(request, timeout=60) as response:
        final_url = urlparse(response.geturl())
        if final_url.scheme != "https" or final_url.hostname != "addons.mozilla.org":
            raise ValueError(f"unexpected download redirect: {response.geturl()}")
        data = response.read(MAX_ARCHIVE_BYTES + 1)
    if len(data) > MAX_ARCHIVE_BYTES:
        raise ValueError("downloaded XPI exceeds size limit")
    validate_xpi(data, extension)
    return data


def refresh_assets(catalog_path: Path, assets_root: Path) -> None:
    extensions = load_catalog(catalog_path)
    downloaded: dict[str, bytes] = {}
    for extension in extensions:
        downloaded[extension["id"]] = download_and_validate(extension)
    for extension in extensions:
        if extension["delivery"] != "bundled":
            continue
        destination = assets_root / extension["assetPath"]
        destination.parent.mkdir(parents=True, exist_ok=True)
        staged_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                prefix=f".{destination.name}.",
                dir=destination.parent,
                delete=False,
            ) as staged:
                staged.write(downloaded[extension["id"]])
                staged.flush()
                os.fsync(staged.fileno())
                staged_path = Path(staged.name)
            os.replace(staged_path, destination)
            staged_path = None
        finally:
            if staged_path is not None:
                staged_path.unlink(missing_ok=True)
    verify_local_assets(catalog_path, assets_root)


def audit_remote_sources(catalog_path: Path) -> None:
    for extension in load_catalog(catalog_path):
        download_and_validate(extension)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "action",
        choices=("verify", "refresh", "audit-remote"),
        nargs="?",
        default="verify",
    )
    parser.add_argument(
        "--catalog",
        type=Path,
        default=Path("app/src/main/assets/gecko_default_extensions/catalog.json"),
    )
    parser.add_argument(
        "--assets-root",
        type=Path,
        default=Path("app/src/main/assets"),
    )
    args = parser.parse_args()
    if args.action == "verify":
        verify_local_assets(args.catalog, args.assets_root)
    elif args.action == "refresh":
        refresh_assets(args.catalog, args.assets_root)
    else:
        audit_remote_sources(args.catalog)
    print(f"Gecko default extension {args.action} passed")


if __name__ == "__main__":
    main()
