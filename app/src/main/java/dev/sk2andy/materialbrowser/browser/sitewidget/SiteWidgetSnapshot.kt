package dev.sk2andy.materialbrowser.browser.sitewidget

import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import java.util.UUID

internal data class SiteWidgetSource(
    val tabId: String,
    val profileId: String,
    val url: String,
    val title: String,
    val navigationGeneration: Int,
    val isPrivate: Boolean = false,
    val engineId: String = "",
    val viewportWidthPx: Int = 0,
    val viewportHeightPx: Int = 0,
    val scrollY: Int = 0,
    val isolationEnabled: Boolean = false,
    val desktopMode: Boolean = false,
)

internal data class SiteWidgetSnapshot(
    val id: String,
    val source: SiteWidgetSource,
    val format: SiteWidgetFormat,
    val crop: SiteWidgetCrop,
    val createdAtMillis: Long,
    val refreshIntervalMinutes: Int = 0,
)

internal object SiteWidgetSnapshotRules {
    const val MAX_BITMAP_DIMENSION = 768
    const val MAX_SNAPSHOTS = 32

    private val snapshotIdPattern = Regex(
        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
    )

    fun normalizedId(value: String): String? {
        if (!snapshotIdPattern.matches(value)) return null
        return runCatching { UUID.fromString(value).toString() }.getOrNull()
    }

    fun sanitize(snapshot: SiteWidgetSnapshot): SiteWidgetSnapshot? {
        val id = normalizedId(snapshot.id) ?: return null
        val source = snapshot.source
        if (
            source.isPrivate || source.navigationGeneration < 0 || snapshot.createdAtMillis <= 0L ||
            !validIdentifier(source.tabId) || !validIdentifier(source.profileId) ||
            source.url.length > MAX_URL_CHARS || SiteWidgetCropRules.sanitize(snapshot.crop) == null
            || !snapshot.format.isValid || !SiteWidgetRefreshRules.isValidInterval(snapshot.refreshIntervalMinutes)
        ) return null
        val hasLegacyGeometry = source.engineId.isEmpty() && source.viewportWidthPx == 0 && source.viewportHeightPx == 0
        if (
            source.scrollY !in 0..10_000_000 ||
            (!hasLegacyGeometry && !SiteWidgetRefreshRules.hasGeometry(source))
        ) return null
        val url = BrowserUriPolicy.normalizeHttpUrl(source.url) ?: return null
        return snapshot.copy(
            id = id,
            source = source.copy(
                url = url,
                title = source.title.filterNot(Char::isISOControl).trim().take(MAX_TITLE_CHARS),
            ),
        )
    }

    private fun validIdentifier(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_IDENTIFIER_CHARS &&
            value.none { it.isWhitespace() || it.isISOControl() }

    private const val MAX_IDENTIFIER_CHARS = 128
    private const val MAX_URL_CHARS = 8_192
    private const val MAX_TITLE_CHARS = 160
}

internal object SiteWidgetRefreshRules {
    val intervalsMinutes = listOf(0, 15, 30, 60, 180, 360)

    fun isValidInterval(minutes: Int): Boolean = minutes in intervalsMinutes

    fun hasGeometry(source: SiteWidgetSource): Boolean =
        source.engineId in setOf("gecko", "system_webview") &&
            source.viewportWidthPx in 1..4_096 && source.viewportHeightPx in 1..8_192 &&
            source.scrollY in 0..10_000_000
}
