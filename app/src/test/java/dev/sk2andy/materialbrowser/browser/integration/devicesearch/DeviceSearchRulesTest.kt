package dev.sk2andy.materialbrowser.browser.integration.devicesearch

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.data.FavoriteEntry
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSearchRulesTest {
    private val profile = BrowserProfile("candy", "🍬")
    private val favorite = FavoriteEntry("https://example.com/bookmark", "Bookmark", 1L)
    private val snapshot = ReaderSnapshot(
        id = "saved-article",
        document = ReaderDocument("Article", "https://example.com/article", "Example", emptyList()),
        progress = 0f,
        savedAtMillis = 1L,
        sourceProfileId = profile.id,
    )

    @Test
    fun `system publication requires enabled setting and individual selection`() {
        val settings = DeviceSearchSettings()
        val items = candidates(settings)

        assertEquals(2, items.size)
        assertTrue(DeviceSearchRules.published(settings, items).isEmpty())
        assertTrue(DeviceSearchRules.published(settings.copy(enabled = true), items).isEmpty())
        assertEquals(
            listOf("r:saved-article"),
            DeviceSearchRules.published(settings.copy(enabled = true, selectedIds = setOf("r:saved-article")), items)
                .map(DeviceSearchItem::id),
        )
    }

    @Test
    fun `private legacy missing protected and synced article owners are excluded`() {
        assertTrue(candidates(snapshots = listOf(snapshot.copy(isPrivate = true))).none { it.kind == DeviceSearchKind.Article })
        assertTrue(candidates(snapshots = listOf(snapshot.copy(sourceProfileId = null))).none { it.kind == DeviceSearchKind.Article })
        assertTrue(candidates(profiles = emptyList()).isEmpty())
        assertTrue(candidates(profiles = listOf(profile.copy(
            protection = ProfileProtection(ProfileLockTrigger.AppBackgrounded),
        ))).isEmpty())
        assertTrue(candidates(profiles = listOf(profile.copy(syncedDeviceId = "other-device"))).isEmpty())
    }

    @Test
    fun `hidden profiles and non web bookmark sources cannot enter index`() {
        val work = BrowserProfile("work", "📬")
        val items = DeviceSearchRules.candidates(
            settings = DeviceSearchSettings(bookmarkProfileId = work.id),
            favorites = listOf(favorite.copy(url = "javascript:alert(1)")),
            snapshots = listOf(snapshot.copy(sourceProfileId = work.id)),
            profiles = listOf(profile, work),
            profilesEnabled = false,
        )
        assertTrue(items.isEmpty())
        assertFalse(DeviceSearchRules.validId("r:../secret"))
        assertFalse(DeviceSearchRules.validId("https://example.com"))
    }

    private fun candidates(
        settings: DeviceSearchSettings = DeviceSearchSettings(),
        snapshots: List<ReaderSnapshot> = listOf(snapshot),
        profiles: List<BrowserProfile> = listOf(profile),
    ) = DeviceSearchRules.candidates(settings, listOf(favorite), snapshots, profiles, profilesEnabled = true)
}
