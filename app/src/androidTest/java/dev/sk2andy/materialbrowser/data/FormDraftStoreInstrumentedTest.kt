package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraft
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftField
import dev.sk2andy.materialbrowser.browser.formdraft.FormDraftRules
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormDraftStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = FormDraftStore(context)
    private val now = System.currentTimeMillis()
    private val draft = FormDraft("work", "https://example.org/reply?id=1#editor",
        listOf(FormDraftField("reply", "Unsent private prose 92751")), now)

    private val settings = BrowserSessionStore(context)
    private var wasEnabled = false
    @Before fun setUp() {
        wasEnabled = settings.loadFormDraftSavingEnabled()
        settings.saveFormDraftSavingEnabled(true)
        assertTrue(store.clear())
    }
    @After fun tearDown() { store.clear(); settings.saveFormDraftSavingEnabled(wasEnabled) }

    @Test
    fun encryptedRoundTripIsProfileAndExactPageScoped() {
        assertTrue(store.save(draft, isPrivate = false, now = now))
        assertEquals(draft, FormDraftStore(context).load("work", draft.url, now))
        assertNull(store.load("personal", draft.url, now))
        assertNull(store.load("work", "https://example.org/reply?id=2#editor", now))
        assertNull(store.load("work", "https://example.org/reply?id=1#other", now))
        val content = File(context.noBackupFilesDir, "form_drafts/drafts.enc").readText()
        assertFalse(content.contains("Unsent private prose"))
        assertFalse(content.contains("example.org"))
        assertFalse(AppDataArchiveRules.isAllowedArchiveRelativePath("no_backup/form_drafts/drafts.enc"))
        assertFalse(AppDataArchiveRules.shouldExportRelativePath("no_backup/form_drafts/drafts.enc.bak"))
    }

    @Test
    fun privateDraftsExpiryAndCorruptionNeverRestore() {
        assertFalse(store.save(draft, isPrivate = true, now = now))
        assertNull(store.load("work", draft.url, now))
        assertTrue(store.save(draft, isPrivate = false, now = now))
        assertNull(store.load("work", draft.url, now + FormDraftRules.LIFETIME_MILLIS))
        assertTrue(store.save(draft, isPrivate = false, now = now))
        File(context.noBackupFilesDir, "form_drafts/drafts.enc").writeText("corrupt")
        assertNull(store.load("work", draft.url, now))
    }

    @Test
    fun profileDeletionAndClearRemoveStoredDrafts() {
        assertTrue(store.save(draft, isPrivate = false, now = now))
        assertTrue(store.save(draft.copy(profileId = "personal"), isPrivate = false, now = now))
        assertTrue(store.deleteProfile("work", now))
        assertNull(store.load("work", draft.url, now))
        assertEquals(draft.copy(profileId = "personal"), store.load("personal", draft.url, now))
        assertTrue(store.clear())
        assertNull(store.load("personal", draft.url, now))
    }
}
