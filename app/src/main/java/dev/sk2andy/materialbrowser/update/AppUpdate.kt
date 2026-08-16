package dev.sk2andy.materialbrowser.update

import java.net.URI

data class AvailableAppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val fileName: String,
) {
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
    UserCa("user-ca-release"),
}

internal object AppUpdateRules {
    fun findAvailableUpdate(
        currentVersionName: String,
        release: GitHubReleaseMetadata,
        channel: AppReleaseChannel = AppReleaseChannel.Standard,
    ): AvailableAppUpdate? {
        if (release.draft || release.prerelease) return null
        val currentVersion = AppVersion.parse(currentVersionName) ?: return null
        val releaseVersion = AppVersion.parse(release.tagName) ?: return null
        if (releaseVersion <= currentVersion) return null

        val normalizedTag = release.tagName.removePrefix("v")
        val expectedFileName = "CandyBrowser-v$normalizedTag-${channel.assetSuffix}.apk"
        val asset = release.assets.singleOrNull { candidate ->
            candidate.name == expectedFileName &&
                candidate.contentType == AvailableAppUpdate.APK_MIME_TYPE &&
                isExpectedDownloadUrl(candidate.downloadUrl, normalizedTag, expectedFileName)
        } ?: return null

        return AvailableAppUpdate(
            versionName = normalizedTag,
            downloadUrl = asset.downloadUrl,
            fileName = asset.name,
        )
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
