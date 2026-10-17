package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.BLANK_URL
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.data.SnoozedTab

object BrowserSnoozeRules {
    fun storedTab(
        id: String,
        address: String,
        title: String,
        profileId: String,
        isPinned: Boolean,
        wakeAtMillis: Long,
        createdAtMillis: Long,
    ): SnoozedTab? {
        if (!validId(id) || !validId(profileId) || wakeAtMillis <= 0 || createdAtMillis < 0) return null
        val url = if (address.isEmpty() || address == BLANK_URL) BLANK_URL else {
            BrowserUrlRules.normalizeHttpUrl(address)?.value ?: return null
        }
        return SnoozedTab(
            tab = BrowserTab(
                id = id,
                lastAccessedAt = createdAtMillis,
                profileId = profileId,
                title = title.take(4_096),
                url = url,
                isPinned = isPinned,
            ),
            wakeAtMillis = wakeAtMillis,
            createdAtMillis = createdAtMillis,
        )
    }

    private fun validId(value: String): Boolean = value.isNotBlank() && value.length <= 256 &&
        value.none { it.code < 32 || it.code == 127 }
}
