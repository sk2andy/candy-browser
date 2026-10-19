package dev.sk2andy.materialbrowser.ui

import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class AvailableFirefoxExtension(
    val id: String,
    val name: String,
    val summary: String,
    val xpiUrl: String,
)

internal sealed interface FirefoxExtensionSearchOutcome {
    data class Results(val entries: List<AvailableFirefoxExtension>) : FirefoxExtensionSearchOutcome
    data object Failed : FirefoxExtensionSearchOutcome
}

internal object FirefoxExtensionSearchRules {
    const val MAX_QUERY_LENGTH = 100

    fun normalizeQuery(rawQuery: String): String? = rawQuery.trim()
        .take(MAX_QUERY_LENGTH)
        .takeIf(String::isNotBlank)

    fun isCurrentQuery(requestQuery: String, currentInput: String): Boolean =
        requestQuery == normalizeQuery(currentInput)

    fun available(
        results: List<AvailableFirefoxExtension>,
        installedIds: Set<String>,
    ): List<AvailableFirefoxExtension> {
        val installed = installedIds.mapTo(mutableSetOf()) { it.lowercase() }
        return results.filterNot { it.id.lowercase() in installed }
    }

    fun responseEntries(body: String): List<AvailableFirefoxExtension> {
        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val addon = results.optJSONObject(index) ?: return@mapNotNull null
            val version = addon.optJSONObject("current_version") ?: return@mapNotNull null
            val file = version.optJSONObject("file")
                ?: version.optJSONArray("files")?.optJSONObject(0)
                ?: return@mapNotNull null
            val xpiUrl = file.optString("url").orEmpty()
            if (!xpiUrl.startsWith("https://addons.mozilla.org/")) {
                return@mapNotNull null
            }
            val name = addon.localizedField("name")
            if (name.isBlank()) return@mapNotNull null
            AvailableFirefoxExtension(
                id = addon.optString("guid").takeIf(String::isNotBlank)
                    ?: addon.optString("slug").takeIf(String::isNotBlank)
                    ?: return@mapNotNull null,
                name = name,
                summary = addon.localizedField("summary"),
                xpiUrl = xpiUrl,
            )
        }
    }

    fun searchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        return "https://addons.mozilla.org/api/v5/addons/search/?q=$encoded&app=android&type=extension&page_size=10"
    }

    suspend fun execute(
        query: String,
        request: suspend (String) -> List<AvailableFirefoxExtension>,
    ): FirefoxExtensionSearchOutcome = try {
        FirefoxExtensionSearchOutcome.Results(request(query))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        FirefoxExtensionSearchOutcome.Failed
    }

    private fun JSONObject.localizedField(field: String): String {
        val value = opt(field)
        if (value is String) return value
        if (value !is JSONObject) return ""
        return value.optString("en-US").takeIf(String::isNotBlank)
            ?: value.optString("en-us").takeIf(String::isNotBlank)
            ?: value.keys().asSequence().map(value::optString).firstOrNull(String::isNotBlank)
            .orEmpty()
    }
}

internal fun searchFirefoxAddons(query: String): List<AvailableFirefoxExtension> {
    val connection = (URL(FirefoxExtensionSearchRules.searchUrl(query))
        .openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "CandyBrowser")
    }
    try {
        if (connection.responseCode !in 200..299) error("AMO search request failed")
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        return FirefoxExtensionSearchRules.responseEntries(response)
    } finally {
        connection.disconnect()
    }
}
