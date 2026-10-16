package dev.sk2andy.materialbrowser.browser.guest

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestBubbleSessionStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var sandbox: File
    private lateinit var preferencesName: String

    @Before
    fun setUp() {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val testId = UUID.randomUUID().toString()
        sandbox = File(targetContext.cacheDir, "guest-bubble-store-$testId").apply { mkdirs() }
        preferencesName = "guest_bubble_test_$testId"
        context = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getNoBackupFilesDir(): File = File(sandbox, "no_backup").apply { mkdirs() }

            override fun getCacheDir(): File = File(sandbox, "cache").apply { mkdirs() }

            override fun getApplicationInfo(): ApplicationInfo = ApplicationInfo(super.getApplicationInfo()).apply {
                dataDir = sandbox.absolutePath
            }

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(preferencesName, mode)
        }
    }

    @After
    fun tearDown() {
        context.deleteSharedPreferences(preferencesName)
        sandbox.deleteRecursively()
    }

    @Test
    fun activeOwnerLockSurvivesNewStoreAndCannotBeDisabled() {
        val store = GuestBubbleSessionStore(context)
        assertFalse(store.isEnabled())
        assertNull(store.state())
        assertFalse(store.start())
        assertTrue(store.setEnabled(true))
        assertTrue(store.start())
        val original = store.state()
        assertNotNull(original)
        assertEquals(GuestBubblePhase.Active, original?.phase)

        val recreated = GuestBubbleSessionStore(context)
        assertTrue(recreated.isLocked())
        assertEquals(original, recreated.state())
        assertFalse(recreated.start())
        assertFalse(recreated.setEnabled(false))
        assertTrue(recreated.isEnabled())
    }

    @Test
    fun enabledObserverEmitsCurrentStateAndStopsAfterClose() {
        val store = GuestBubbleSessionStore(context)
        val values = CopyOnWriteArrayList<Boolean>()
        val observation = store.observeEnabled(values::add)
        assertEquals(listOf(false), values)

        assertTrue(store.setEnabled(true))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(listOf(false, true), values)
        observation.close()
        assertTrue(store.setEnabled(false))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(listOf(false, true), values)
    }

    @Test
    fun malformedAndInterruptedMarkersKeepOwnerLocked() {
        val directory = File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME).apply { mkdirs() }
        val marker = File(directory, "session")
        marker.writeText("truncated")

        assertEquals(GuestBubbleRules.recoverySession, GuestBubbleSessionStore(context).state())
        assertTrue(GuestBubbleSessionStore(context).isLocked())
        assertTrue(marker.delete())
        File(directory, "session.new").writeText("interrupted")

        assertTrue(GuestBubbleSessionStore(context).isLocked())
        assertFalse(GuestBubbleSessionStore(context).start())
    }

    @Test
    fun backupMarkerRecoversOriginalLock() {
        val directory = File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME).apply { mkdirs() }
        File(directory, "session.bak").writeText(
            "1\nfd10837a-3405-4771-95a0-8b877cab718b\nactive\n-\n-",
        )

        val store = GuestBubbleSessionStore(context)
        assertTrue(store.isLocked())
        assertEquals("fd10837a-3405-4771-95a0-8b877cab718b", store.state()?.id)
    }

    @Test
    fun inaccessibleMarkerDirectoryFailsClosed() {
        File(context.noBackupFilesDir, GuestBubbleRules.SESSION_DIRECTORY_NAME).writeText("blocked")

        val store = GuestBubbleSessionStore(context)
        assertTrue(store.isLocked())
        assertFalse(store.start())
        assertFalse(store.setEnabled(true))
    }

    @Test
    fun ownerProcessCannotClaimGuestOrClearOwnerLock() {
        val store = GuestBubbleSessionStore(context)
        assertTrue(store.setEnabled(true))
        assertTrue(store.start())

        assertFalse(store.claimGuestProcess())
        assertFalse(store.beginClosing())
        assertFalse(store.completeCleanup())
        assertEquals(GuestBubblePhase.Active, store.state()?.phase)
    }
}
