#!/usr/bin/env python3

import os
import re
import subprocess
import unittest
import zipfile
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
APK = Path(
    os.environ.get(
        "CANDY_SYSTEM_WEBVIEW_APK",
        PROJECT_ROOT
        / "app/build/outputs/apk/systemwebview/release/app-systemwebview-release.apk",
    ),
)
EXPECTED_APPLICATION_ID = os.environ.get(
    "CANDY_SYSTEM_WEBVIEW_APPLICATION_ID",
    "dev.sk2andy.materialbrowser.systemwebview",
)
EXPECTED_APP_LABEL = os.environ.get(
    "CANDY_SYSTEM_WEBVIEW_APP_LABEL",
    "Candy System WebView",
)
EXPECTED_VERSION_CODE = os.environ.get("CANDY_SYSTEM_WEBVIEW_VERSION_CODE")
EXPECTED_VERSION_NAME = os.environ.get("CANDY_SYSTEM_WEBVIEW_VERSION_NAME")

FORBIDDEN_ASSET_PREFIXES = (
    "assets/candy_privacy/",
    "assets/gecko_default_extensions/",
)
FORBIDDEN_NATIVE_LIBRARIES = {
    "libfreebl3.so",
    "liblgpllibs.so",
    "libmozavcodec.so",
    "libmozavutil.so",
    "libmozglue.so",
    "libnss3.so",
    "libplugin-container.so",
    "libsoftokn3.so",
    "libxul.so",
}
FORBIDDEN_DEX_MARKERS = (
    b"org/mozilla/geckoview",
    b"Lorg/mozilla/geckoview",
)


def build_tools_version(path: Path) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", path.parent.name))


def android_sdk_root() -> Path:
    configured_root = os.environ.get("ANDROID_HOME") or os.environ.get(
        "ANDROID_SDK_ROOT",
    )
    if configured_root:
        return Path(configured_root)

    local_properties = PROJECT_ROOT / "local.properties"
    if local_properties.is_file():
        for line in local_properties.read_text(encoding="utf-8").splitlines():
            key, separator, value = line.partition("=")
            if separator and key.strip() == "sdk.dir":
                return Path(value.strip().replace(r"\:", ":").replace(r"\\", "\\"))

    default_root = Path.home() / "Library/Android/sdk"
    if default_root.is_dir():
        return default_root

    raise RuntimeError(
        "Android SDK not found via ANDROID_HOME, ANDROID_SDK_ROOT, "
        "local.properties, or the macOS default path.",
    )


def find_aapt2() -> Path:
    sdk_root = android_sdk_root()
    candidates = [
        path
        for path in (sdk_root / "build-tools").glob("*/aapt2")
        if path.is_file()
    ]
    if not candidates:
        raise RuntimeError(f"No aapt2 found below {sdk_root}/build-tools.")
    return max(candidates, key=build_tools_version)


def aapt2_dump(aapt2: Path, *arguments: str) -> str:
    if not APK.is_file():
        raise RuntimeError(f"APK does not exist: {APK}")
    command = [str(aapt2), "dump", *arguments, str(APK)]
    try:
        return subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except subprocess.CalledProcessError as error:
        detail = error.stderr.strip() or error.stdout.strip()
        raise RuntimeError(f"aapt2 failed for {APK}: {detail}") from error


class SystemWebViewApkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.aapt2 = find_aapt2()
        cls.badging = aapt2_dump(cls.aapt2, "badging")
        cls.manifest = aapt2_dump(
            cls.aapt2,
            "xmltree",
            "--file",
            "AndroidManifest.xml",
        )
        cls.archive = zipfile.ZipFile(APK)

    @classmethod
    def tearDownClass(cls):
        cls.archive.close()

    def test_package_label_and_version_match_system_webview_channel(self):
        package = re.search(
            r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",
            self.badging,
            re.MULTILINE,
        )
        self.assertIsNotNone(package)
        self.assertEqual(EXPECTED_APPLICATION_ID, package.group(1))
        if EXPECTED_VERSION_CODE is not None:
            self.assertEqual(EXPECTED_VERSION_CODE, package.group(2))
        if EXPECTED_VERSION_NAME is not None:
            self.assertEqual(EXPECTED_VERSION_NAME, package.group(3))
        self.assertIn(f"application-label:'{EXPECTED_APP_LABEL}'", self.badging)

    def test_gecko_assets_and_native_libraries_are_absent(self):
        names = self.archive.namelist()
        violations = sorted(
            name
            for name in names
            if name.startswith(FORBIDDEN_ASSET_PREFIXES)
            or name.endswith(".xpi")
            or Path(name).name in FORBIDDEN_NATIVE_LIBRARIES
            or name == "assets/omni.ja"
        )
        self.assertEqual([], violations)

    def test_dex_contains_no_geckoview_references(self):
        violations = []
        for name in self.archive.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            content = self.archive.read(name)
            if any(marker in content for marker in FORBIDDEN_DEX_MARKERS):
                violations.append(name)
        self.assertEqual([], violations)

    def test_manifest_contains_no_gecko_provider(self):
        self.assertNotIn("GeckoPerformanceDiagnosticsProvider", self.manifest)

    def test_legal_notices_do_not_claim_gecko_extensions_are_bundled(self):
        notices = self.archive.read("assets/third_party_notices.txt").decode("utf-8")
        self.assertNotIn("Gecko default extensions", notices)
        self.assertNotIn(".xpi", notices.lower())


if __name__ == "__main__":
    unittest.main()
