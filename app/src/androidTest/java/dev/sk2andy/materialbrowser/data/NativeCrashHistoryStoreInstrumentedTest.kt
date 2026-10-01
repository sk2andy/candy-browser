package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeCrashHistoryStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "native-crash-history-${UUID.randomUUID()}")
    private val preferences = context.getSharedPreferences(
        "native-crash-history-test-${UUID.randomUUID()}", Context.MODE_PRIVATE,
    )
    private val checkpointFile = File(directory, "native-capture.txt")

    @Before
    fun setUp() {
        check(directory.mkdirs())
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        preferences.edit().clear().commit()
    }

    @Test
    fun missingAndCorruptCheckpointsDenyHistoricalCapture() {
        assertEquals(NativeCrashCaptureCheckpoint(), NativeCrashHistoryStore(directory, preferences).snapshot())
        val corrupt = listOf(
            "",
            "unknown-format\n100\n200\n",
            "CandyNativeCapture1\ninvalid\n200\n",
            "CandyNativeCapture1\n-1\n200\n",
            "CandyNativeCapture1\n100\n99\n",
            "CandyNativeCapture1\nblocked\n-1\n",
            "CandyNativeCapture1\n100\n200",
            "CandyNativeCapture1\n100\n200\nprivate-data",
        )

        corrupt.forEach { text ->
            checkpointFile.writeText(text)
            val checkpoint = NativeCrashHistoryStore(directory, preferences).snapshot()
            assertEquals(NativeCrashCaptureCheckpoint(), checkpoint)
            assertFalse(NativeCrashCaptureRules.allows(checkpoint, 201L, beforeMillis = 300L))
        }
    }

    @Test
    fun atomicCheckpointRoundTripsAfterRestartAndInterruptedWriteRollsBack() {
        val store = NativeCrashHistoryStore(directory, preferences)
        assertTrue(store.reset(allowed = true, nowMillis = 100L))
        assertTrue(store.markCollected(150L))
        assertTrue(store.markCollected(120L))
        val expected = NativeCrashCaptureCheckpoint(100L, 150L)

        assertEquals(expected, NativeCrashHistoryStore(directory, preferences).snapshot())
        val atomicFile = AtomicFile(checkpointFile)
        val output = atomicFile.startWrite()
        output.write("interrupted".toByteArray(Charsets.UTF_8))
        atomicFile.failWrite(output)

        assertEquals(expected, NativeCrashHistoryStore(directory, preferences).snapshot())
        assertFalse(File("${checkpointFile.path}.new").exists())
    }

    @Test
    fun blockingBeforeRestartRemainsDurableUntilExplicitReset() {
        val store = NativeCrashHistoryStore(directory, preferences)
        assertTrue(store.reset(allowed = true, nowMillis = 100L))
        assertTrue(store.markCollected(150L))
        assertTrue(store.reset(allowed = false, nowMillis = 200L))

        val restarted = NativeCrashHistoryStore(directory, preferences)
        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), restarted.snapshot())
        assertFalse(NativeCrashCaptureRules.allows(restarted.snapshot(), 201L, beforeMillis = 300L))
        assertTrue(restarted.markCollected(250L))

        assertEquals(
            NativeCrashCaptureCheckpoint(null, 250L),
            NativeCrashHistoryStore(directory, preferences).snapshot(),
        )
    }

    @Test
    fun privacyAndClearResetsPreventOldHistoryResurrectionAfterClockRollback() {
        val store = NativeCrashHistoryStore(directory, preferences)
        assertTrue(store.reset(allowed = true, nowMillis = 100L))
        assertTrue(store.markCollected(150L))
        assertTrue(store.reset(allowed = true, nowMillis = 200L))
        assertTrue(store.reset(allowed = false, nowMillis = 175L))
        val restarted = NativeCrashHistoryStore(directory, preferences)
        assertTrue(restarted.reset(allowed = true, nowMillis = 50L))

        val checkpoint = NativeCrashHistoryStore(directory, preferences).snapshot()
        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), checkpoint)
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 150L, beforeMillis = 300L))
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 200L, beforeMillis = 300L))
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 201L, beforeMillis = 300L))
        assertTrue(restarted.reset(allowed = true, nowMillis = 250L))

        val freshCheckpoint = NativeCrashHistoryStore(directory, preferences).snapshot()
        assertEquals(NativeCrashCaptureCheckpoint(250L, 250L), freshCheckpoint)
        assertFalse(NativeCrashCaptureRules.allows(freshCheckpoint, 250L, beforeMillis = 300L))
        assertTrue(NativeCrashCaptureRules.allows(freshCheckpoint, 251L, beforeMillis = 300L))
    }

    @Test
    fun clearingRemovesDurableCheckpointAndBackupThenRestartDeniesCapture() {
        val store = NativeCrashHistoryStore(directory, preferences)
        assertTrue(store.reset(allowed = true, nowMillis = 100L))
        val backup = File("${checkpointFile.path}.bak")
        backup.writeText(checkpointFile.readText())

        assertTrue(store.clear())

        assertFalse(checkpointFile.exists())
        assertFalse(backup.exists())
        assertNull(store.snapshot().captureSinceMillis)
        assertEquals(
            NativeCrashCaptureCheckpoint(null, 100L),
            NativeCrashHistoryStore(directory, preferences).snapshot(),
        )
        assertFalse(NativeCrashCaptureRules.allows(store.snapshot(), 101L, beforeMillis = 200L))
    }

    @Test
    fun durableRevocationRejectsStaleEligibleFileAndPreservesLaterCutoff() {
        checkpointFile.writeText("CandyNativeCapture1\n100\n150\n")
        assertTrue(
            preferences.edit()
                .putBoolean("native_capture_revoked", true)
                .putLong("native_capture_cutoff", 200L)
                .commit(),
        )

        val checkpoint = NativeCrashHistoryStore(directory, preferences).snapshot()

        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), checkpoint)
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 151L, beforeMillis = 300L))
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 201L, beforeMillis = 300L))
    }

    @Test
    fun failedCheckpointDirectoryWriteKeepsDurableRevocationAfterRestart() {
        val blockedDirectory = File(directory, "blocked-parent")
        blockedDirectory.writeText("prevents directory creation")
        val store = NativeCrashHistoryStore(blockedDirectory, preferences)

        assertFalse(store.reset(allowed = true, nowMillis = 200L))

        assertTrue(preferences.getBoolean("native_capture_revoked", false))
        assertEquals(200L, preferences.getLong("native_capture_cutoff", 0L))
        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), store.snapshot())
        val checkpoint = NativeCrashHistoryStore(blockedDirectory, preferences).snapshot()
        assertEquals(NativeCrashCaptureCheckpoint(null, 200L), checkpoint)
        assertFalse(NativeCrashCaptureRules.allows(checkpoint, 201L, beforeMillis = 300L))
    }
}
