import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

from generate_gecko_default_extensions import load_catalog, validate_xpi, verify_local_assets


class GeckoDefaultExtensionGeneratorTest(unittest.TestCase):
    def test_validates_bundled_signed_xpi_identity_and_hash(self):
        xpi = self._xpi()
        extension = self._extension(xpi)

        validate_xpi(xpi, extension)

    def test_rejects_hash_drift(self):
        xpi = self._xpi()
        extension = self._extension(xpi)
        extension["sha256"] = "0" * 64

        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            validate_xpi(xpi, extension)

    def test_rejects_manifest_identity_drift(self):
        xpi = self._xpi(extension_id="other@example.com")
        extension = self._extension(xpi)

        with self.assertRaisesRegex(ValueError, "extension id mismatch"):
            validate_xpi(xpi, extension)

    def test_rejects_duplicate_catalog_ids(self):
        xpi = self._xpi()
        extension = self._extension(xpi)
        catalog = {"schemaVersion": 1, "extensions": [extension, extension]}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps(catalog), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "duplicate extension id"):
                load_catalog(path)

    def test_rejects_mutable_bundled_source_tag(self):
        xpi = self._xpi()
        extension = self._extension(xpi)
        extension["sourceCodeUrl"] = "https://github.com/example/fixture/tree/v1.0"
        catalog = {"schemaVersion": 1, "extensions": [extension]}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps(catalog), encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "immutable Git commit URL"):
                load_catalog(path)

    def test_remote_extension_is_verified_but_not_packaged(self):
        xpi = self._xpi()
        bundled = self._extension(xpi)
        remote = dict(bundled)
        remote.update(
            {
                "id": "remote@example.com",
                "name": "Remote",
                "delivery": "remote",
                "installUri": bundled["sourceUrl"],
            },
        )
        remote.pop("assetPath")
        catalog = {"schemaVersion": 1, "extensions": [bundled, remote]}
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            catalog_path = assets / "gecko_default_extensions" / "catalog.json"
            catalog_path.parent.mkdir(parents=True)
            catalog_path.write_text(json.dumps(catalog), encoding="utf-8")
            (assets / bundled["assetPath"]).write_bytes(xpi)

            verify_local_assets(catalog_path, assets)

            self.assertFalse((assets / "gecko_default_extensions" / "remote.xpi").exists())

    def _xpi(self, extension_id="fixture@example.com", version="1.0"):
        output = io.BytesIO()
        with ZipFile(output, "w") as archive:
            archive.writestr(
                "manifest.json",
                json.dumps(
                    {
                        "version": version,
                        "browser_specific_settings": {"gecko": {"id": extension_id}},
                    },
                ),
            )
            archive.writestr("LICENSE", "GPL-3.0-only")
            archive.writestr("META-INF/cose.sig", "signed")
            archive.writestr("META-INF/mozilla.rsa", "signed")
            archive.writestr("META-INF/mozilla.sf", "signed")
        return output.getvalue()

    def _extension(self, xpi):
        return {
            "id": "fixture@example.com",
            "name": "Fixture",
            "version": "1.0",
            "delivery": "bundled",
            "installUri": (
                "resource://android/assets/gecko_default_extensions/fixture-1.0.xpi"
            ),
            "assetPath": "gecko_default_extensions/fixture-1.0.xpi",
            "sourceUrl": (
                "https://addons.mozilla.org/firefox/downloads/file/1/fixture-1.0.xpi"
            ),
            "sourceCodeUrl": (
                "https://github.com/example/fixture/tree/"
                "0123456789abcdef0123456789abcdef01234567"
            ),
            "license": "GPL-3.0-only",
            "sha256": hashlib.sha256(xpi).hexdigest(),
            "size": len(xpi),
        }


if __name__ == "__main__":
    unittest.main()
