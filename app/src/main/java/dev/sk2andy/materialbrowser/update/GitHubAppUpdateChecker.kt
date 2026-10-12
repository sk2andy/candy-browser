package dev.sk2andy.materialbrowser.update

import android.os.Build
import android.util.JsonReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class GitHubAppUpdateChecker {
    suspend fun findAvailableUpdate(
        currentVersionName: String,
        channel: AppReleaseChannel,
        track: AppUpdateTrack = AppUpdateTrack.Stable,
    ): AvailableAppUpdate? =
        withContext(Dispatchers.IO) {
            try {
                ensureActive()
                val releases = when (track) {
                    AppUpdateTrack.Stable -> listOf(fetchReleases(LATEST_RELEASE_URL, track).single())
                    AppUpdateTrack.Nightly -> buildList {
                        for (page in 1..MAX_RELEASE_PAGES) {
                            ensureActive()
                            val releases = fetchReleases("$RELEASES_URL&page=$page", track)
                            addAll(releases)
                            if (releases.size < RELEASES_PER_PAGE) break
                        }
                    }
                }
                ensureActive()
                AppUpdateRules.findAvailableUpdate(
                    currentVersionName = currentVersionName,
                    releases = releases,
                    channel = channel,
                    supportedAbis = Build.SUPPORTED_ABIS.asList(),
                    track = track,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
        }

    private fun fetchReleases(url: String, track: AppUpdateTrack): List<GitHubReleaseMetadata> {
        val connection = URL(url).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            connection.setRequestProperty("User-Agent", USER_AGENT)
            check(connection.responseCode == HttpsURLConnection.HTTP_OK)
            val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use(::readBounded)
            JsonReader(StringReader(json)).use { reader ->
                if (track == AppUpdateTrack.Stable) {
                    listOf(readRelease(reader))
                } else {
                    buildList {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            check(size < RELEASES_PER_PAGE)
                            add(readRelease(reader))
                        }
                        reader.endArray()
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(reader: BufferedReader): String {
        val result = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            check(result.length + count <= MAX_RESPONSE_CHARACTERS)
            result.append(buffer, 0, count)
        }
        check(result.isNotEmpty())
        return result.toString()
    }

    private fun readRelease(reader: JsonReader): GitHubReleaseMetadata {
        var tagName = ""
        var draft = true
        var prerelease = true
        var assets = emptyList<GitHubReleaseAsset>()
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "tag_name" -> tagName = reader.nextString()
                "draft" -> draft = reader.nextBoolean()
                "prerelease" -> prerelease = reader.nextBoolean()
                "assets" -> assets = readAssets(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return GitHubReleaseMetadata(tagName, draft, prerelease, assets)
    }

    private fun readAssets(reader: JsonReader): List<GitHubReleaseAsset> = buildList {
        reader.beginArray()
        while (reader.hasNext()) {
            var name = ""
            var contentType = ""
            var downloadUrl = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "name" -> name = reader.nextString()
                    "content_type" -> contentType = reader.nextString()
                    "browser_download_url" -> downloadUrl = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (size < MAX_ASSETS) add(GitHubReleaseAsset(name, contentType, downloadUrl))
        }
        reader.endArray()
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/sk2andy/candy-browser/releases/latest"
        const val RELEASES_URL =
            "https://api.github.com/repos/sk2andy/candy-browser/releases?per_page=20"
        const val RELEASES_PER_PAGE = 20
        const val MAX_RELEASE_PAGES = 3
        const val GITHUB_API_VERSION = "2026-03-10"
        const val USER_AGENT = "Candy-Browser-Android"
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_CHARACTERS = 2 * 1_024 * 1_024
        const val MAX_ASSETS = 20
    }
}
