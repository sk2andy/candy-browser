package dev.sk2andy.materialbrowser.browser.sitewidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SiteWidgetSnapshotRulesTest {
    @Test
    fun `refresh requires supported bounded viewport and scheduled intervals`() {
        val snapshot = snapshot()
        assertFalse(SiteWidgetRefreshRules.hasGeometry(snapshot.source))
        val source = snapshot.source.copy(engineId = "gecko", viewportWidthPx = 400, viewportHeightPx = 800)
        assertTrue(SiteWidgetRefreshRules.hasGeometry(source))
        listOf(0, 15, 30, 60, 180, 360).forEach { minutes ->
            assertNotNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = source, refreshIntervalMinutes = minutes)))
        }
        listOf(-1, 1, 14, 16, Int.MAX_VALUE).forEach { minutes ->
            assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = source, refreshIntervalMinutes = minutes)))
        }
        listOf(
            source.copy(engineId = "unknown"),
            source.copy(viewportWidthPx = 0),
            source.copy(viewportHeightPx = 8_193),
            source.copy(scrollY = -1),
        ).forEach { invalid -> assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = invalid))) }
    }

    @Test
    fun `normalizes full UUID without accepting abbreviated or path identifiers`() {
        assertEquals(ID, SiteWidgetSnapshotRules.normalizedId(ID.uppercase()))
        listOf("1-1-1-1-1", "../$ID", "$ID.png", " $ID").forEach { id ->
            assertNull(SiteWidgetSnapshotRules.normalizedId(id))
        }
    }

    @Test
    fun `rejects private source and unsafe website addresses`() {
        val snapshot = snapshot()
        assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = snapshot.source.copy(isPrivate = true))))
        listOf("javascript:alert(1)", "https://user:secret@example.com", "file:///private").forEach { url ->
            assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = snapshot.source.copy(url = url))))
        }
    }

    @Test
    fun `rejects stale identity fields and crop outside viewport`() {
        val snapshot = snapshot()
        assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(createdAtMillis = 0)))
        assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = snapshot.source.copy(navigationGeneration = -1))))
        assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(source = snapshot.source.copy(profileId = " "))))
        assertNull(SiteWidgetSnapshotRules.sanitize(snapshot.copy(crop = snapshot.crop.copy(width = Float.NaN))))
    }

    @Test
    fun `bounds display title and keeps regular source identity`() {
        val snapshot = snapshot()
        val safe = SiteWidgetSnapshotRules.sanitize(
            snapshot.copy(source = snapshot.source.copy(title = "\n" + "a".repeat(200))),
        )
        assertEquals("a".repeat(160), safe?.source?.title)
        assertEquals(snapshot.source.tabId, safe?.source?.tabId)
        assertEquals(snapshot.source.url, safe?.source?.url)
    }

    private fun snapshot() = SiteWidgetSnapshot(
        id = ID,
        source = SiteWidgetSource("tab", "candy", "https://example.com/status", "Status", 3),
        format = SiteWidgetFormat.FourByThree,
        crop = SiteWidgetCrop(0f, 0f, 1f, 0.5f),
        createdAtMillis = 1_000L,
    )

    private companion object {
        const val ID = "7b85cd56-2e42-4c32-95f1-982fb3fa6235"
    }
}
