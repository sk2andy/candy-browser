import json
from pathlib import Path
import re
import unittest
from urllib.parse import parse_qs, urlparse

from obtainium_links import markdown_table


class ObtainiumLinksTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.readme = (Path(__file__).resolve().parents[1] / "README.md").read_text(encoding="utf-8")
        cls.links = re.findall(
            r"\[(Stable|Nightly)]\((https://apps\.obtainium\.imranr\.dev/redirect\?r=[^\s)]+)\)",
            cls.readme,
        )

    def test_readme_has_all_ten_variant_and_channel_choices(self):
        self.assertEqual(10, len(self.links))
        self.assertIn(markdown_table(), self.readme)

    def test_real_readme_links_keep_package_release_and_asset_channels_aligned(self):
        variants = (
            ("release", "", "Candy Browser"),
            ("arm64-v8a-release", "", "Candy Browser"),
            ("foss-release", ".foss", "Candy FOSS"),
            ("systemwebview-release", ".systemwebview", "Candy System WebView"),
            ("ca-release", ".ca", "Candy CA"),
        )
        self.assertEqual(10, len(self.links))
        for index, (channel, url) in enumerate(self.links):
            suffix, package_suffix, name = variants[index // 2]
            nightly = channel == "Nightly"
            with self.subTest(variant=suffix, channel=channel):
                redirect = parse_qs(urlparse(url).query)["r"][0]
                self.assertTrue(redirect.startswith("obtainium://app/"))
                app = json.loads(redirect.removeprefix("obtainium://app/"))
                settings = json.loads(app["additionalSettings"])
                self.assertEqual(
                    "dev.sk2andy.materialbrowser" + package_suffix + (".nightly" if nightly else ""),
                    app["id"],
                )
                self.assertEqual(name + (" Nightly" if nightly else ""), app["name"])
                self.assertEqual("https://github.com/sk2andy/candy-browser", app["url"])
                self.assertEqual(0, app["preferredApkIndex"])
                self.assertIs(nightly, settings["includePrereleases"])
                self.assertEqual("date", settings["sortMethodChoice"])
                self.assertEqual(
                    not nightly and suffix == "foss-release",
                    settings.get("fallbackToOlderReleases", False),
                )

                asset_filter = re.compile(settings["apkFilterRegEx"])
                title_filter = re.compile(settings["filterReleaseTitlesByRegEx"])
                for version in ("0.45", "1.2.3"):
                    for candidate_nightly in (False, True):
                        candidate_version = version + (
                            "-nightly.202610120000" if candidate_nightly else ""
                        )
                        title = (
                            f"Candy Browser Nightly v{candidate_version}" if candidate_nightly
                            else f"Candy Browser v{version}"
                        )
                        self.assertEqual(candidate_nightly == nightly, bool(title_filter.search(title)))
                        for candidate_suffix, _, _ in variants:
                            asset = f"CandyBrowser-v{candidate_version}-{candidate_suffix}.apk"
                            matches = candidate_nightly == nightly and candidate_suffix == suffix
                            self.assertEqual(matches, bool(asset_filter.search(asset)), asset)
                            self.assertIsNone(asset_filter.search(asset + ".sha256"))
                            self.assertIsNone(asset_filter.search("prefix-" + asset))
                        foss_github_asset = f"CandyBrowser-v{candidate_version}-foss-github-release.apk"
                        self.assertEqual(
                            not nightly and not candidate_nightly and suffix == "foss-release",
                            bool(asset_filter.search(foss_github_asset)),
                            foss_github_asset,
                        )

                self.assertIsNone(title_filter.search("Candy Browser v0.45-rc.1"))
                for malformed in (
                    "0.45-nightly.20261012",
                    "0.45-nightly.20261012000000",
                    "0.45-nightly.202610120000-debug",
                    "0.45-rc.1",
                ):
                    self.assertIsNone(asset_filter.search(f"CandyBrowser-v{malformed}-{suffix}.apk"))


if __name__ == "__main__":
    unittest.main()
