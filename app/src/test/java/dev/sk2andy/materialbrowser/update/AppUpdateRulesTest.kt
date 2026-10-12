package dev.sk2andy.materialbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRulesTest {
    @Test
    fun `stable installs never offer nightly releases`() {
        for (channel in AppReleaseChannel.entries) {
            for (prerelease in listOf(false, true)) {
                assertNull(
                    AppUpdateRules.findAvailableUpdate(
                        currentVersionName = "0.8",
                        release = nightlyRelease("0.9-nightly.202610120200", channel, prerelease),
                        channel = channel,
                    ),
                )
            }
        }
    }

    @Test
    fun `nightly installs only offer their matching variant`() {
        for (channel in AppReleaseChannel.entries) {
            val tag = "v0.9-nightly.202610120000"
            val assets = AppReleaseChannel.entries.map { asset(tag, it.assetSuffix) }
            val update = AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8-nightly.202610110000",
                release = release(tag, prerelease = true, assets = assets),
                channel = channel,
                track = AppUpdateTrack.Nightly,
            )

            assertEquals("CandyBrowser-$tag-${channel.assetSuffix}.apk", update?.fileName)
            assertEquals(
                "https://github.com/sk2andy/candy-browser/releases/tag/$tag",
                update?.releaseNotesUrl,
            )
        }
    }

    @Test
    fun `nightly ordering follows build time even when base version decreases`() {
        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "1.0-nightly.202610110000",
            releases = listOf(
                release("v2.0"),
                nightlyRelease("1.1-nightly.202610100000"),
                nightlyRelease("0.9-nightly.202610120000"),
                nightlyRelease("3.0-nightly.202610130000").copy(draft = true),
                release("v9.0-rc.1", prerelease = true),
            ),
            channel = AppReleaseChannel.Standard,
            supportedAbis = emptyList(),
            track = AppUpdateTrack.Nightly,
        )

        assertEquals("0.9-nightly.202610120000", update?.versionName)
    }

    @Test
    fun `nightly rejects stable same older and malformed builds`() {
        val current = "0.9-nightly.202610120000"
        val rejectedReleases = listOf(
            release("v1.0"),
            release("v1.0-rc.1", prerelease = true),
            nightlyRelease(current),
            nightlyRelease("1.0-nightly.202610110000"),
            nightlyRelease("1.0-nightly.202613120000"),
            nightlyRelease("1.0-nightly.202602300000"),
            nightlyRelease("1.0-nightly.202610122400"),
            nightlyRelease("1.0-nightly.202610120001", prerelease = false),
            nightlyRelease("1.0-nightly.202610120001").copy(draft = true),
        )
        for (release in rejectedReleases) {
            assertNull(
                AppUpdateRules.findAvailableUpdate(current, release, track = AppUpdateTrack.Nightly),
            )
        }
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                "0.9",
                nightlyRelease("1.0-nightly.202610130000"),
                track = AppUpdateTrack.Nightly,
            ),
        )
    }

    @Test
    fun `nightly keeps ABI selection and rejects mismatched asset paths`() {
        val tag = "v0.9-nightly.202610120000"
        val arm64 = asset(tag, "arm64-v8a-release")
        val valid = release(
            tag,
            prerelease = true,
            assets = listOf(asset(tag, "release"), arm64),
        )
        assertEquals(
            arm64.name,
            AppUpdateRules.findAvailableUpdate(
                "0.9-nightly.202610110000",
                valid,
                supportedAbis = listOf("arm64-v8a"),
                track = AppUpdateTrack.Nightly,
            )?.fileName,
        )
        for (invalidAssets in listOf(
            listOf(arm64.copy(downloadUrl = arm64.downloadUrl.replace(tag, "v0.9"))),
            listOf(arm64.copy(contentType = "application/octet-stream")),
            listOf(arm64, arm64),
            listOf(asset(tag, "ca-release")),
        )) {
            assertNull(
                AppUpdateRules.findAvailableUpdate(
                    "0.9-nightly.202610110000",
                    valid.copy(assets = invalidAssets),
                    supportedAbis = listOf("arm64-v8a"),
                    track = AppUpdateTrack.Nightly,
                ),
            )
        }
    }

    @Test
    fun `certificate trust selects matching release channel`() {
        assertEquals(
            AppReleaseChannel.Standard,
            AppReleaseChannel.forUserCertificateTrust(enabled = false),
        )
        assertEquals(
            AppReleaseChannel.UserCa,
            AppReleaseChannel.forUserCertificateTrust(enabled = true),
        )
        assertEquals(
            AppReleaseChannel.SystemWebView,
            AppReleaseChannel.forBuild(
                systemWebViewOnly = true,
                trustsUserCertificates = false,
            ),
        )
    }

    @Test
    fun `keeps System WebView installs on System WebView release channel`() {
        val standardAsset = asset("v0.9", suffix = "release")
        val systemWebViewAsset = asset("v0.9", suffix = "systemwebview-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(standardAsset, systemWebViewAsset)),
            channel = AppReleaseChannel.SystemWebView,
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals("CandyBrowser-v0.9-systemwebview-release.apk", update?.fileName)
    }

    @Test
    fun `does not downgrade System WebView install to Gecko release assets`() {
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8",
                release = release(
                    "v0.9",
                    assets = listOf(
                        asset("v0.9", suffix = "release"),
                        asset("v0.9", suffix = "arm64-v8a-release"),
                        asset("v0.9", suffix = "ca-release"),
                    ),
                ),
                channel = AppReleaseChannel.SystemWebView,
                supportedAbis = listOf("arm64-v8a"),
            ),
        )
    }

    @Test
    fun `finds newer signed release APK`() {
        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9"),
        )

        assertEquals("0.9", update?.versionName)
        assertEquals("CandyBrowser-v0.9-release.apk", update?.fileName)
        assertEquals(
            "https://github.com/sk2andy/candy-browser/releases/tag/v0.9",
            update?.releaseNotesUrl,
        )
    }

    @Test
    fun `prefers arm64 release on compatible devices`() {
        val universalAsset = asset("v0.9", suffix = "release")
        val arm64Asset = asset("v0.9", suffix = "arm64-v8a-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(universalAsset, arm64Asset)),
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertEquals("CandyBrowser-v0.9-arm64-v8a-release.apk", update?.fileName)
    }

    @Test
    fun `falls back to universal release when arm64 asset is missing`() {
        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9"),
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals("CandyBrowser-v0.9-release.apk", update?.fileName)
    }

    @Test
    fun `rejects ambiguous arm64 release instead of falling back`() {
        val universalAsset = asset("v0.9", suffix = "release")
        val arm64Asset = asset("v0.9", suffix = "arm64-v8a-release")

        assertNull(
            AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8",
                release = release(
                    "v0.9",
                    assets = listOf(arm64Asset, arm64Asset, universalAsset),
                ),
                supportedAbis = listOf("arm64-v8a"),
            ),
        )
    }

    @Test
    fun `rejects invalid arm64 release instead of falling back`() {
        val universalAsset = asset("v0.9", suffix = "release")
        val arm64Asset = asset("v0.9", suffix = "arm64-v8a-release").copy(
            contentType = "application/octet-stream",
        )

        assertNull(
            AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8",
                release = release("v0.9", assets = listOf(arm64Asset, universalAsset)),
                supportedAbis = listOf("arm64-v8a"),
            ),
        )
    }

    @Test
    fun `keeps non arm64 devices on universal release`() {
        val universalAsset = asset("v0.9", suffix = "release")
        val arm64Asset = asset("v0.9", suffix = "arm64-v8a-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(arm64Asset, universalAsset)),
            supportedAbis = listOf("x86_64"),
        )

        assertEquals("CandyBrowser-v0.9-release.apk", update?.fileName)
    }

    @Test
    fun `does not offer same or older release`() {
        assertNull(AppUpdateRules.findAvailableUpdate("0.9", release("v0.9")))
        assertNull(AppUpdateRules.findAvailableUpdate("1.0", release("v0.9")))
    }

    @Test
    fun `keeps user CA installs on user CA release channel`() {
        val standardAsset = asset("v0.9", suffix = "release")
        val userCaAsset = asset("v0.9", suffix = "ca-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(standardAsset, userCaAsset)),
            channel = AppReleaseChannel.UserCa,
        )

        assertEquals("CandyBrowser-v0.9-ca-release.apk", update?.fileName)
    }

    @Test
    fun `keeps user CA installs on universal channel for arm64 devices`() {
        val userCaAsset = asset("v0.9", suffix = "ca-release")
        val arm64Asset = asset("v0.9", suffix = "arm64-v8a-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(arm64Asset, userCaAsset)),
            channel = AppReleaseChannel.UserCa,
            supportedAbis = listOf("arm64-v8a"),
        )

        assertEquals("CandyBrowser-v0.9-ca-release.apk", update?.fileName)
    }

    @Test
    fun `does not downgrade user CA install to standard release`() {
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8",
                release = release("v0.9"),
                channel = AppReleaseChannel.UserCa,
            ),
        )
    }

    @Test
    fun `does not offer legacy shared identity user CA asset`() {
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                currentVersionName = "0.8",
                release = release(
                    "v0.9",
                    assets = listOf(asset("v0.9", suffix = "user-ca-release")),
                ),
                channel = AppReleaseChannel.UserCa,
            ),
        )
    }

    @Test
    fun `orders semantic prerelease versions`() {
        val alpha = requireNotNull(AppVersion.parse("1.0.0-alpha.2"))
        val beta = requireNotNull(AppVersion.parse("1.0.0-beta.1"))
        val stable = requireNotNull(AppVersion.parse("v1.0.0"))

        assertTrue(alpha < beta)
        assertTrue(beta < stable)
        assertTrue(
            requireNotNull(AppVersion.parse("1.0.0-alpha")) <
                requireNotNull(AppVersion.parse("1.0.0-alpha.1")),
        )
        assertEquals(AppVersion.parse("1.2"), AppVersion.parse("v1.2.0"))
    }

    @Test
    fun `rejects draft prerelease and malformed versions`() {
        assertNull(AppUpdateRules.findAvailableUpdate("0.8", release("v0.9", draft = true)))
        assertNull(AppUpdateRules.findAvailableUpdate("0.8", release("v0.9", prerelease = true)))
        assertNull(AppUpdateRules.findAvailableUpdate("unknown", release("v0.9")))
        assertNull(AppUpdateRules.findAvailableUpdate("0.8", release("latest")))
    }

    @Test
    fun `rejects unexpected APK metadata and download hosts`() {
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                "0.8",
                release("v0.9", contentType = "application/octet-stream"),
            ),
        )
        assertNull(
            AppUpdateRules.findAvailableUpdate(
                "0.8",
                release(
                    "v0.9",
                    downloadUrl = "https://example.com/CandyBrowser-v0.9-release.apk",
                ),
            ),
        )
    }

    private fun nightlyRelease(
        version: String,
        channel: AppReleaseChannel = AppReleaseChannel.Standard,
        prerelease: Boolean = true,
    ) = release(
        tag = "v$version",
        prerelease = prerelease,
        assets = listOf(asset("v$version", channel.assetSuffix)),
    )

    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        contentType: String = AvailableAppUpdate.APK_MIME_TYPE,
        downloadUrl: String =
            "https://github.com/sk2andy/candy-browser/releases/download/$tag/" +
                "CandyBrowser-$tag-release.apk",
        assets: List<GitHubReleaseAsset>? = null,
    ) = GitHubReleaseMetadata(
        tagName = tag,
        draft = draft,
        prerelease = prerelease,
        assets = assets ?: listOf(
            GitHubReleaseAsset(
                name = "CandyBrowser-$tag-release.apk",
                contentType = contentType,
                downloadUrl = downloadUrl,
            ),
        ),
    )

    private fun asset(
        tag: String,
        suffix: String,
    ) = GitHubReleaseAsset(
        name = "CandyBrowser-$tag-$suffix.apk",
        contentType = AvailableAppUpdate.APK_MIME_TYPE,
        downloadUrl = "https://github.com/sk2andy/candy-browser/releases/download/$tag/" +
            "CandyBrowser-$tag-$suffix.apk",
    )
}
