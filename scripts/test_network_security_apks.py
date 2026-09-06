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
        PROJECT_ROOT / "app/build/outputs/apk/full/debug/app-full-debug.apk",
    ),
)
USER_CA_APK = Path(
    os.environ.get(
        "CANDY_USER_CA_APK",
        PROJECT_ROOT / "app/build/outputs/apk/full/userCaDebug/app-full-userCaDebug.apk",
    ),
)
FOSS_APK = Path(
    os.environ.get(
        "CANDY_FOSS_APK",
        PROJECT_ROOT / "app/build/outputs/apk/foss/debug/app-foss-debug.apk",
    ),
)
STANDARD_APPLICATION_ID = os.environ.get(
    "CANDY_STANDARD_APPLICATION_ID",
    "dev.sk2andy.materialbrowser.linkpeek",
)
FOSS_APPLICATION_ID = os.environ.get(
    "CANDY_FOSS_APPLICATION_ID",
    "dev.sk2andy.materialbrowser.foss.linkpeek",
)
USER_CA_APPLICATION_ID = os.environ.get(
    "CANDY_USER_CA_APPLICATION_ID",
    "dev.sk2andy.materialbrowser.ca.debug",
)
STANDARD_APP_LABEL = os.environ.get("CANDY_STANDARD_APP_LABEL", "Candy Link Peek")
FOSS_APP_LABEL = os.environ.get("CANDY_FOSS_APP_LABEL", "Candy Link Peek")
USER_CA_APP_LABEL = os.environ.get("CANDY_USER_CA_APP_LABEL", "Candy CA Debug")
STANDARD_LAUNCHER_FOREGROUND = os.environ.get(
    "CANDY_STANDARD_LAUNCHER_FOREGROUND",
    "drawable/ic_launcher_foreground_debug",
)
FOSS_LAUNCHER_FOREGROUND = os.environ.get(
    "CANDY_FOSS_LAUNCHER_FOREGROUND",
    "drawable/ic_launcher_foreground_debug",
)
USER_CA_LAUNCHER_FOREGROUND = os.environ.get(
    "CANDY_USER_CA_LAUNCHER_FOREGROUND",
    "drawable/ic_launcher_foreground_ca",
)
STANDARD_LAUNCHER_MONOCHROME = os.environ.get(
    "CANDY_STANDARD_LAUNCHER_MONOCHROME",
    "drawable/ic_launcher_monochrome_debug",
)
FOSS_LAUNCHER_MONOCHROME = os.environ.get(
    "CANDY_FOSS_LAUNCHER_MONOCHROME",
    "drawable/ic_launcher_monochrome_debug",
)
USER_CA_LAUNCHER_MONOCHROME = os.environ.get(
    "CANDY_USER_CA_LAUNCHER_MONOCHROME",
    "drawable/ic_launcher_monochrome_ca",
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


def resource_names(output: str) -> dict[str, str]:
    return {
        resource_id.lower(): name
        for resource_id, name in re.findall(
            r"resource (0x[0-9a-fA-F]+) ([A-Za-z0-9_]+/[A-Za-z0-9_]+)",
            output,
        )
    }


def resource_files(output: str) -> dict[str, str]:
    return {
        name: file_path
        for name, file_path in re.findall(
            r"resource 0x[0-9a-fA-F]+ ([A-Za-z0-9_]+/[A-Za-z0-9_]+)\s+"
            r"\([^)]*\) \(file\) ([^ ]+) type=[A-Z]+",
            output,
        )
    }


def resource_string_values(output: str) -> dict[str, str]:
    return {
        resource_id.lower(): value
        for resource_id, value in re.findall(
            r"resource (0x[0-9a-fA-F]+) string/[A-Za-z0-9_]+\s+\(\) \"([^\"]*)\"",
            output,
        )
    }


def manifest_package(output: str) -> str:
    match = re.search(r'^\s*A: package="([^"]+)"', output, re.MULTILINE)
    if match is None:
        raise AssertionError("Manifest package missing from aapt2 output.")
    return match.group(1)


def manifest_label(output: str, string_values: dict[str, str]) -> str:
    match = re.search(r':label\([^)]*\)="([^"]+)"', output)
    if match is not None:
        return match.group(1)
    reference = re.search(r":label\([^)]*\)=@(0x[0-9a-fA-F]+)", output)
    if reference is None:
        raise AssertionError("Application label missing from aapt2 output.")
    resource_id = reference.group(1).lower()
    if resource_id not in string_values:
        raise AssertionError(f"Application label value missing for resource: {resource_id}")
    return string_values[resource_id]


def referenced_resource(output: str, attribute: str, resources: dict[str, str]) -> str:
    match = re.search(
        rf":{re.escape(attribute)}\([^)]*\)=@(0x[0-9a-fA-F]+)",
        output,
    )
    if match is None:
        raise AssertionError(f"Resource attribute missing: {attribute}")
    resource_id = match.group(1).lower()
    if resource_id not in resources:
        raise AssertionError(f"Unknown resource ID for {attribute}: {resource_id}")
    return resources[resource_id]


def adaptive_icon_layer(output: str, layer: str, resources: dict[str, str]) -> str:
    match = re.search(
        rf"E: {re.escape(layer)}[^\n]*\n\s+A: .*:drawable\([^)]*\)="
        r"@(0x[0-9a-fA-F]+)",
        output,
    )
    if match is None:
        raise AssertionError(f"Adaptive icon layer missing: {layer}")
    resource_id = match.group(1).lower()
    if resource_id not in resources:
        raise AssertionError(f"Unknown resource ID for {layer}: {resource_id}")
    return resources[resource_id]


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
        cls.foss_manifest = aapt2_dump(
            cls.aapt2,
            FOSS_APK,
            "xmltree",
            "AndroidManifest.xml",
        )
        cls.standard_resource_dump = aapt2_dump(cls.aapt2, STANDARD_APK, "resources")
        cls.foss_resource_dump = aapt2_dump(cls.aapt2, FOSS_APK, "resources")
        cls.user_ca_resource_dump = aapt2_dump(cls.aapt2, USER_CA_APK, "resources")
        cls.standard_resources = xml_resources(cls.standard_resource_dump)
        cls.user_ca_resources = xml_resources(cls.user_ca_resource_dump)
        cls.foss_resources = xml_resources(cls.foss_resource_dump)
        cls.standard_resource_names = resource_names(cls.standard_resource_dump)
        cls.foss_resource_names = resource_names(cls.foss_resource_dump)
        cls.user_ca_resource_names = resource_names(cls.user_ca_resource_dump)
        cls.standard_resource_files = resource_files(cls.standard_resource_dump)
        cls.foss_resource_files = resource_files(cls.foss_resource_dump)
        cls.user_ca_resource_files = resource_files(cls.user_ca_resource_dump)
        cls.standard_string_values = resource_string_values(cls.standard_resource_dump)
        cls.foss_string_values = resource_string_values(cls.foss_resource_dump)
        cls.user_ca_string_values = resource_string_values(cls.user_ca_resource_dump)

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

    def test_foss_apk_trusts_only_system_certificates(self):
        self.assertEqual(
            "network_security_config",
            manifest_network_config(self.foss_manifest, self.foss_resources),
        )
        self.assertNotIn(
            "network_security_config_user_ca",
            [name for name, _ in self.foss_resources.values()],
        )
        config = aapt2_dump(
            self.aapt2,
            FOSS_APK,
            "xmltree",
            xml_resource_file(self.foss_resources, "network_security_config"),
        )
        self.assertIn("cleartextTrafficPermitted=true", config)
        self.assertEqual(["system"], certificate_sources(config))

    def test_channels_use_isolated_package_identities_and_explicit_labels(self):
        self.assertEqual(STANDARD_APPLICATION_ID, manifest_package(self.standard_manifest))
        self.assertEqual(FOSS_APPLICATION_ID, manifest_package(self.foss_manifest))
        self.assertEqual(USER_CA_APPLICATION_ID, manifest_package(self.user_ca_manifest))
        self.assertEqual(
            STANDARD_APP_LABEL,
            manifest_label(self.standard_manifest, self.standard_string_values),
        )
        self.assertEqual(
            FOSS_APP_LABEL,
            manifest_label(self.foss_manifest, self.foss_string_values),
        )
        self.assertEqual(
            USER_CA_APP_LABEL,
            manifest_label(self.user_ca_manifest, self.user_ca_string_values),
        )

    def test_channels_use_distinct_launcher_identity_resources(self):
        manifests_and_resources = (
            (
                STANDARD_APK,
                self.standard_manifest,
                self.standard_resource_names,
                self.standard_resource_files,
                STANDARD_LAUNCHER_FOREGROUND,
                STANDARD_LAUNCHER_MONOCHROME,
            ),
            (
                FOSS_APK,
                self.foss_manifest,
                self.foss_resource_names,
                self.foss_resource_files,
                FOSS_LAUNCHER_FOREGROUND,
                FOSS_LAUNCHER_MONOCHROME,
            ),
            (
                USER_CA_APK,
                self.user_ca_manifest,
                self.user_ca_resource_names,
                self.user_ca_resource_files,
                USER_CA_LAUNCHER_FOREGROUND,
                USER_CA_LAUNCHER_MONOCHROME,
            ),
        )
        for apk, manifest, resources, files, expected_foreground, expected_monochrome in (
            manifests_and_resources
        ):
            for manifest_attribute in ("icon", "roundIcon"):
                launcher_name = referenced_resource(manifest, manifest_attribute, resources)
                if launcher_name not in files:
                    raise AssertionError(f"Launcher resource file missing: {launcher_name}")
                launcher = aapt2_dump(
                    self.aapt2,
                    apk,
                    "xmltree",
                    files[launcher_name],
                )
                self.assertEqual(
                    expected_foreground,
                    adaptive_icon_layer(launcher, "foreground", resources),
                )
                self.assertEqual(
                    expected_monochrome,
                    adaptive_icon_layer(launcher, "monochrome", resources),
                )


if __name__ == "__main__":
    unittest.main()
