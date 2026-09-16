package dev.sk2andy.materialbrowser.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRuntimeRegistryTest {
    private val id = DownloadRuntimeRegistry.nextTransferId()

    @After
    fun clearEntry() {
        DownloadRuntimeRegistry.completed(id)
    }

    @Test
    fun `runtime controls target encoded media entry and toggle current pause state`() {
        var cancelled = false
        start(
            cancel = { cancelled = true },
            pause = {
                DownloadRuntimeRegistry.paused(id, paused = true, updatedAt = 20L)
                true
            },
            resume = {
                DownloadRuntimeRegistry.paused(id, paused = false, updatedAt = 30L)
                true
            },
        )
        val entry = DownloadRuntimeRegistry.snapshot().single { it.id == -43L }
        assertTrue(entry.supportsPause)
        assertTrue(entry.supportsCancel)
        assertTrue(DownloadRuntimeRegistry.togglePause(entry.id))
        assertEquals(DownloadStatus.Paused, DownloadRuntimeRegistry.snapshot().single { it.id == entry.id }.status)
        assertTrue(DownloadRuntimeRegistry.togglePause(entry.id))
        assertEquals(DownloadStatus.Running, DownloadRuntimeRegistry.snapshot().single { it.id == entry.id }.status)
        assertTrue(DownloadRuntimeRegistry.cancel(entry.id))
        assertTrue(cancelled)
    }

    @Test
    fun `notification controls target active transfer identity`() {
        var cancelled = false
        start(
            cancel = { cancelled = true },
            pause = {
                DownloadRuntimeRegistry.paused(id, paused = true, updatedAt = 20L)
                true
            },
            resume = {
                DownloadRuntimeRegistry.paused(id, paused = false, updatedAt = 30L)
                true
            },
        )

        assertEquals(-43L, requireNotNull(DownloadRuntimeRegistry.entryForTransfer(id)).id)
        assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
        assertEquals(DownloadStatus.Paused, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
        assertTrue(DownloadRuntimeRegistry.togglePauseTransfer(id))
        assertEquals(DownloadStatus.Running, DownloadRuntimeRegistry.entryForTransfer(id)?.status)
        assertTrue(DownloadRuntimeRegistry.cancelTransfer(id))
        assertTrue(cancelled)
    }

    @Test
    fun `terminal and completed transfers reject stale controls`() {
        var cancelled = false
        start(cancel = { cancelled = true })
        DownloadRuntimeRegistry.failed(id, cancelled = false, updatedAt = 20L)
        assertFalse(DownloadRuntimeRegistry.cancel(-43L))
        assertFalse(DownloadRuntimeRegistry.cancelTransfer(id))
        assertFalse(DownloadRuntimeRegistry.togglePause(-43L))
        assertFalse(DownloadRuntimeRegistry.togglePauseTransfer(id))
        assertFalse(cancelled)
        DownloadRuntimeRegistry.completed(id)
        assertFalse(DownloadRuntimeRegistry.cancel(-43L))
    }

    @Test
    fun `transfers without controls do not offer unsupported actions`() {
        start()
        val entry = DownloadRuntimeRegistry.snapshot().single { it.id == -43L }
        assertFalse(entry.supportsPause)
        assertFalse(entry.supportsCancel)
        assertFalse(DownloadRuntimeRegistry.cancel(entry.id))
        assertFalse(DownloadRuntimeRegistry.togglePause(entry.id))
    }

    private fun start(
        cancel: (() -> Unit)? = null,
        pause: (() -> Boolean)? = null,
        resume: (() -> Boolean)? = null,
    ) = DownloadRuntimeRegistry.started(
        id = id,
        name = "download.bin",
        source = "https://example.com/download.bin",
        mime = "application/octet-stream",
        total = 100L,
        startedAt = 10L,
        mediaStoreId = 42L,
        cancel = cancel,
        pause = pause,
        resume = resume,
    )
}
