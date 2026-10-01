package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashCaptureRulesTest {
    private val packageName = "dev.sk2andy.materialbrowser"

    @Test
    fun `browser process matches exact package and known Gecko child processes`() {
        assertTrue(NativeCrashCaptureRules.isBrowserProcess(packageName, packageName))
        listOf("tab", "isolatedTab").forEach { type ->
            (0..39).forEach { index ->
                assertTrue(
                    NativeCrashCaptureRules.isBrowserProcess(
                        packageName,
                        "$packageName:${type}_disable_art_image_$index",
                    ),
                )
            }
        }
        listOf("gmplugin", "socket", "gpu", "rdd", "utility", "zygoteTab").forEach { type ->
            assertTrue(
                NativeCrashCaptureRules.isBrowserProcess(
                    packageName,
                    "$packageName:${type}_disable_art_image_",
                ),
            )
        }
        assertTrue(NativeCrashCaptureRules.isBrowserProcess(packageName, "$packageName:media"))
    }

    @Test
    fun `other apps and malformed Gecko process suffixes are rejected`() {
        val rejected = listOf(
            "other.app",
            "other.app:tab_disable_art_image_0",
            "${packageName}extra",
            "${packageName}extra:tab_disable_art_image_0",
            "$packageName:",
            "$packageName:other",
            "$packageName:tab_disable_art_image_",
            "$packageName:tab_disable_art_image_00",
            "$packageName:tab_disable_art_image_-1",
            "$packageName:tab_disable_art_image_40",
            "$packageName:isolatedTab_disable_art_image_100",
            "$packageName:gpu_disable_art_image_0",
            "$packageName:media:other",
            "$packageName:tab_disable_art_image_0\n",
            "$packageName:tab_disable_art_image_0extra",
        )

        rejected.forEach { process ->
            assertFalse(process, NativeCrashCaptureRules.isBrowserProcess(packageName, process))
        }
    }

    @Test
    fun `missing and blocked checkpoints deny all historical crashes`() {
        listOf(
            NativeCrashCaptureCheckpoint(),
            NativeCrashCaptureCheckpoint(captureSinceMillis = null, collectedThroughMillis = 200L),
        ).forEach { checkpoint ->
            listOf(-1L, 0L, 100L, 201L).forEach { timestamp ->
                assertFalse(NativeCrashCaptureRules.allows(checkpoint, timestamp, beforeMillis = 300L))
            }
        }
    }

    @Test
    fun `capture cutoff collection watermark and upper timestamp boundary are strict`() {
        val checkpoint = NativeCrashCaptureCheckpoint(
            captureSinceMillis = 100L,
            collectedThroughMillis = 150L,
        )

        listOf(99L, 100L, 101L, 149L, 150L, 200L, 201L).forEach { timestamp ->
            assertFalse(NativeCrashCaptureRules.allows(checkpoint, timestamp, beforeMillis = 200L))
        }
        assertTrue(NativeCrashCaptureRules.allows(checkpoint, 151L, beforeMillis = 200L))
        assertTrue(NativeCrashCaptureRules.allows(checkpoint, 199L, beforeMillis = 200L))
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 151L, beforeMillis = 150L))
        assertFalse(
            NativeCrashCaptureRules.allows(
                NativeCrashCaptureCheckpoint(100L, 0L),
                timestampMillis = 100L,
                beforeMillis = 200L,
            ),
        )
    }

    @Test
    fun `clear and privacy resets retain monotonic cutoff through clock rollback`() {
        val checkpoint = NativeCrashCaptureCheckpoint(100L, 150L)
        val afterClear = NativeCrashCaptureRules.reset(checkpoint, allowed = true, nowMillis = 200L)
        assertEquals(NativeCrashCaptureCheckpoint(200L, 200L), afterClear)
        val privateCheckpoint = NativeCrashCaptureRules.reset(
            afterClear,
            allowed = false,
            nowMillis = 175L,
        )
        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), privateCheckpoint)
        val resumed = NativeCrashCaptureRules.reset(privateCheckpoint, allowed = true, nowMillis = 50L)

        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), resumed)
        assertFalse(NativeCrashCaptureRules.allows(resumed, 199L, beforeMillis = 300L))
        assertFalse(NativeCrashCaptureRules.allows(resumed, 200L, beforeMillis = 300L))
        assertFalse(NativeCrashCaptureRules.allows(resumed, 201L, beforeMillis = 300L))
        val freshReset = NativeCrashCaptureRules.reset(resumed, allowed = true, nowMillis = 250L)
        assertEquals(NativeCrashCaptureCheckpoint(250L, 250L), freshReset)
        assertFalse(NativeCrashCaptureRules.allows(freshReset, 250L, beforeMillis = 300L))
        assertTrue(NativeCrashCaptureRules.allows(freshReset, 251L, beforeMillis = 300L))
    }

    @Test
    fun `reset never lowers prior capture start or uses negative time`() {
        assertEquals(
            NativeCrashCaptureCheckpoint(null, 250L),
            NativeCrashCaptureRules.reset(
                NativeCrashCaptureCheckpoint(250L, 200L),
                allowed = true,
                nowMillis = 150L,
            ),
        )
        assertEquals(
            NativeCrashCaptureCheckpoint(null, 0L),
            NativeCrashCaptureRules.reset(
                NativeCrashCaptureCheckpoint(),
                allowed = true,
                nowMillis = -1L,
            ),
        )
    }
}
