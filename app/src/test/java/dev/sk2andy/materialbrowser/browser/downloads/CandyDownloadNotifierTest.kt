package dev.sk2andy.materialbrowser.browser.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CandyDownloadNotifierTest {
    @Test
    fun `progress percent clamps transfer bytes and rejects unknown totals`() {
        assertNull(CandyDownloadNotifier.progressPercent(received = 10L, total = -1L))
        assertNull(CandyDownloadNotifier.progressPercent(received = 10L, total = 0L))
        assertEquals(0, CandyDownloadNotifier.progressPercent(received = -10L, total = 200L))
        assertEquals(50, CandyDownloadNotifier.progressPercent(received = 100L, total = 200L))
        assertEquals(100, CandyDownloadNotifier.progressPercent(received = 300L, total = 200L))
    }
}
