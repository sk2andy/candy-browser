package dev.sk2andy.materialbrowser.update

import java.net.URI
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

data class AvailableAppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val fileName: String,
) {
    val releaseNotesUrl: String
        get() = "https://github.com/sk2andy/candy-browser/releases/tag/v$versionName"

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

internal data class GitHubReleaseMetadata(
    val tagName: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<GitHubReleaseAsset>,
)

internal data class GitHubReleaseAsset(
    val name: String,
    val contentType: String,
    val downloadUrl: String,
)

internal enum class AppReleaseChannel(
    val assetSuffix: String,
) {
    Standard("release"),
    SystemWebView("systemwebview-release"),
    UserCa("ca-release"),

    ;

    companion object {
        fun forUserCertificateTrust(enabled: Boolean): AppReleaseChannel =
            if (enabled) UserCa else Standard

        fun forBuild(
            systemWebViewOnly: Boolean,
            trustsUserCertificates: Boolean,
        ): AppReleaseChannel = when {
            systemWebViewOnly -> SystemWebView
            trustsUserCertificates -> UserCa
            else -> Standard
        }
    }
}

internal enum class AppUpdateTrack {
    Stable,
    Nightly,
}

internal object AppUpdateRules {
    fun findAvailableUpdate(
        currentVersionName: String,
        releases: List<GitHubReleaseMetadata>,
        channel: AppReleaseChannel,
        supportedAbis: List<String>,
        track: AppUpdateTrack,
    ): AvailableAppUpdate? {
        val release = when (track) {
            AppUpdateTrack.Stable -> releases.firstOrNull { !it.draft && !it.prerelease }
            AppUpdateTrack.Nightly -> releases.filter { !it.draft && it.prerelease }
                .mapNotNull { release -> nightlyBuildNumber(release.tagName)?.let { it to release } }
                .maxByOrNull { it.first }
                ?.second
        } ?: return null
        return findAvailableUpdate(currentVersionName, release, channel, supportedAbis, track)
    }

    fun findAvailableUpdate(
        currentVersionName: String,
        release: GitHubReleaseMetadata,
        channel: AppReleaseChannel = AppReleaseChannel.Standard,
        supportedAbis: List<String> = emptyList(),
        track: AppUpdateTrack = AppUpdateTrack.Stable,
    ): AvailableAppUpdate? {
        if (release.draft) return null
        when (track) {
            AppUpdateTrack.Stable -> {
                if (release.prerelease || nightlyBuildNumber(release.tagName) != null) return null
                val currentVersion = AppVersion.parse(currentVersionName) ?: return null
                val releaseVersion = AppVersion.parse(release.tagName) ?: return null
                if (releaseVersion <= currentVersion) return null
            }
            AppUpdateTrack.Nightly -> {
                if (!release.prerelease) return null
                val currentBuild = nightlyBuildNumber(currentVersionName) ?: return null
                val releaseBuild = nightlyBuildNumber(release.tagName) ?: return null
                if (releaseBuild <= currentBuild) return null
            }
        }

        val normalizedTag = release.tagName.removePrefix("v")
        val expectedFileNames = buildList {
            if (channel == AppReleaseChannel.Standard && "arm64-v8a" in supportedAbis) {
                add("CandyBrowser-v$normalizedTag-arm64-v8a-release.apk")
            }
            add("CandyBrowser-v$normalizedTag-${channel.assetSuffix}.apk")
        }
        val asset = selectExpectedAsset(
            assets = release.assets,
            expectedFileNames = expectedFileNames,
            versionName = normalizedTag,
        ) ?: return null

        return AvailableAppUpdate(
            versionName = normalizedTag,
            downloadUrl = asset.downloadUrl,
            fileName = asset.name,
        )
    }

    private fun selectExpectedAsset(
        assets: List<GitHubReleaseAsset>,
        expectedFileNames: List<String>,
        versionName: String,
    ): GitHubReleaseAsset? {
        for (expectedFileName in expectedFileNames) {
            val namedAssets = assets.filter { candidate -> candidate.name == expectedFileName }
            if (namedAssets.isEmpty()) continue
            if (namedAssets.size != 1) return null
            val asset = namedAssets.single()
            if (asset.contentType != AvailableAppUpdate.APK_MIME_TYPE) return null
            if (!isExpectedDownloadUrl(asset.downloadUrl, versionName, expectedFileName)) return null
            return asset
        }
        return null
    }

    private fun isExpectedDownloadUrl(
        url: String,
        versionName: String,
        fileName: String,
    ): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals("github.com", ignoreCase = true) &&
            uri.userInfo == null &&
            uri.fragment == null &&
            uri.rawQuery == null &&
            uri.rawPath == "${RELEASE_DOWNLOAD_PATH}v$versionName/$fileName"
    }.getOrDefault(false)

    private const val RELEASE_DOWNLOAD_PATH = "/sk2andy/candy-browser/releases/download/"

    private val nightlyVersionPattern = Regex("^v?\\d+\\.\\d+(?:\\.\\d+)?-nightly\\.(\\d{12})$")
    private val nightlyTimestampFormat = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
        .withResolverStyle(ResolverStyle.STRICT)

    private fun nightlyBuildNumber(versionName: String): Long? {
        if (AppVersion.parse(versionName) == null) return null
        val timestamp = nightlyVersionPattern.matchEntire(versionName)?.groupValues?.get(1)
            ?: return null
        return runCatching {
            LocalDateTime.parse(timestamp, nightlyTimestampFormat)
            timestamp.toLong()
        }.getOrNull()
    }
}

internal data class AppVersion(
    private val major: Long,
    private val minor: Long,
    private val patch: Long,
    private val prerelease: List<String>,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1

        repeat(maxOf(prerelease.size, other.prerelease.size)) { index ->
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            comparePrereleaseIdentifiers(left, right).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    companion object {
        private val pattern = Regex(
            "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$",
        )

        fun parse(value: String): AppVersion? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return AppVersion(
                major = match.groupValues[1].toLongOrNull() ?: return null,
                minor = match.groupValues[2].toLongOrNull() ?: return null,
                patch = match.groupValues[3].ifBlank { "0" }.toLongOrNull() ?: return null,
                prerelease = match.groupValues[4].takeIf(String::isNotBlank)?.split('.').orEmpty(),
            )
        }

        private fun comparePrereleaseIdentifiers(left: String, right: String): Int {
            val leftNumber = left.toLongOrNull()
            val rightNumber = right.toLongOrNull()
            return when {
                leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
