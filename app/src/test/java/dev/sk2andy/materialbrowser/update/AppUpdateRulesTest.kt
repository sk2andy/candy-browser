package dev.sk2andy.materialbrowser.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRulesTest {
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
    }

    @Test
    fun `finds newer signed release APK`() {
        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9"),
        )

        assertEquals("0.9", update?.versionName)
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
        val userCaAsset = asset("v0.9", suffix = "user-ca-release")

        val update = AppUpdateRules.findAvailableUpdate(
            currentVersionName = "0.8",
            release = release("v0.9", assets = listOf(standardAsset, userCaAsset)),
            channel = AppReleaseChannel.UserCa,
        )

        assertEquals("CandyBrowser-v0.9-user-ca-release.apk", update?.fileName)
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
