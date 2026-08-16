#!/usr/bin/env python3

import os
import re
import subprocess
import unittest
from pathlib import Path


PROJECT_ROOT = Path(__file__).resolve().parent.parent
STANDARD_APK = Path(
    os.environ.get(
        "CANDY_STANDARD_APK",
        PROJECT_ROOT / "app/build/outputs/apk/debug/app-debug.apk",
    ),
)
USER_CA_APK = Path(
    os.environ.get(
        "CANDY_USER_CA_APK",
        PROJECT_ROOT / "app/build/outputs/apk/userCaDebug/app-userCaDebug.apk",
    ),
)


def build_tools_version(path: Path) -> tuple[int, ...]:
    return tuple(int(part) for part in re.findall(r"\d+", path.parent.name))


def find_aapt2() -> Path:
    sdk_root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk_root:
        raise RuntimeError("ANDROID_HOME or ANDROID_SDK_ROOT must point to an Android SDK.")
    candidates = [
        path
        for path in (Path(sdk_root) / "build-tools").glob("*/aapt2")
        if path.is_file()
    ]
    if not candidates:
        raise RuntimeError(f"No aapt2 found below {sdk_root}/build-tools.")
    return max(candidates, key=build_tools_version)


def aapt2_dump(aapt2: Path, apk: Path, subcommand: str, resource: str | None = None) -> str:
    if not apk.is_file():
        raise RuntimeError(f"APK does not exist: {apk}")
    command = [str(aapt2), "dump", subcommand]
    if resource is not None:
        command.extend(("--file", resource))
    command.append(str(apk))
    try:
        return subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except subprocess.CalledProcessError as error:
        detail = error.stderr.strip() or error.stdout.strip()
        raise RuntimeError(f"aapt2 failed for {apk}: {detail}") from error


def xml_resources(output: str) -> dict[str, tuple[str, str]]:
    return {
        resource_id.lower(): (name, file_path)
        for resource_id, name, file_path in re.findall(
            r"resource (0x[0-9a-fA-F]+) xml/([A-Za-z0-9_]+)\s+"
            r"\(\) \(file\) ([^ ]+) type=XML",
            output,
        )
    }


def manifest_package(output: str) -> str:
    match = re.search(r'^\s*A: package="([^"]+)"', output, re.MULTILINE)
    if match is None:
        raise AssertionError("Manifest package missing from aapt2 output.")
    return match.group(1)


def manifest_label(output: str) -> str:
    match = re.search(r':label\([^)]*\)="([^"]+)"', output)
    if match is None:
        raise AssertionError("Resolved application label missing from aapt2 output.")
    return match.group(1)


def manifest_network_config(
    output: str,
    resources: dict[str, tuple[str, str]],
) -> str:
    match = re.search(r":networkSecurityConfig\([^)]*\)=@(0x[0-9a-fA-F]+)", output)
    if match is None:
        raise AssertionError("Manifest networkSecurityConfig missing from aapt2 output.")
    resource_id = match.group(1).lower()
    if resource_id not in resources:
        raise AssertionError(f"Unknown network security resource ID: {resource_id}")
    return resources[resource_id][0]


def xml_resource_file(resources: dict[str, tuple[str, str]], name: str) -> str:
    matches = [
        file_path
        for resource_name, file_path in resources.values()
        if resource_name == name
    ]
    if len(matches) != 1:
        raise AssertionError(f"Expected one xml/{name} resource, found {len(matches)}.")
    return matches[0]


def certificate_sources(output: str) -> list[str]:
    return re.findall(r'^\s*A: src="([^"]+)"', output, re.MULTILINE)


class NetworkSecurityApkTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.aapt2 = find_aapt2()
        cls.standard_manifest = aapt2_dump(
            cls.aapt2,
            STANDARD_APK,
            "xmltree",
            "AndroidManifest.xml",
        )
        cls.user_ca_manifest = aapt2_dump(
            cls.aapt2,
            USER_CA_APK,
            "xmltree",
            "AndroidManifest.xml",
        )
        cls.standard_resources = xml_resources(
            aapt2_dump(cls.aapt2, STANDARD_APK, "resources"),
        )
        cls.user_ca_resources = xml_resources(
            aapt2_dump(cls.aapt2, USER_CA_APK, "resources"),
        )

    def test_standard_apk_trusts_only_system_certificates(self):
        self.assertEqual(
            "network_security_config",
            manifest_network_config(self.standard_manifest, self.standard_resources),
        )
        self.assertNotIn(
            "network_security_config_user_ca",
            [name for name, _ in self.standard_resources.values()],
        )
        config = aapt2_dump(
            self.aapt2,
            STANDARD_APK,
            "xmltree",
            xml_resource_file(self.standard_resources, "network_security_config"),
        )
        self.assertIn("cleartextTrafficPermitted=true", config)
        self.assertEqual(["system"], certificate_sources(config))

    def test_user_ca_apk_trusts_system_and_user_certificates(self):
        self.assertEqual(
            "network_security_config_user_ca",
            manifest_network_config(self.user_ca_manifest, self.user_ca_resources),
        )
        config = aapt2_dump(
            self.aapt2,
            USER_CA_APK,
            "xmltree",
            xml_resource_file(self.user_ca_resources, "network_security_config_user_ca"),
        )
        self.assertIn("cleartextTrafficPermitted=true", config)
        self.assertEqual(["system", "user"], certificate_sources(config))

    def test_channels_share_package_identity_and_user_ca_label_is_explicit(self):
        self.assertEqual(
            manifest_package(self.standard_manifest),
            manifest_package(self.user_ca_manifest),
        )
        self.assertIn("User CA", manifest_label(self.user_ca_manifest))


if __name__ == "__main__":
    unittest.main()
