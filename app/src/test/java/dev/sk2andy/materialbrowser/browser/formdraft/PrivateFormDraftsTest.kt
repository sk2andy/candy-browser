package dev.sk2andy.materialbrowser.browser.formdraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateFormDraftsTest {
    private val now = 2_000_000_000L
    private val draft = FormDraft(
        profileId = "personal",
        url = "https://example.org/reply?id=1",
        fields = listOf(FormDraftField("reply", "Unsent private reply")),
        updatedAt = now,
    )

    @Test
    fun `private recovery remains scoped to tab profile and exact page`() {
        val drafts = PrivateFormDrafts()
        drafts.update("tab", draft, delete = false, now = now)

        assertEquals(draft, drafts.load("tab", "personal", draft.url, now))
        assertNull(drafts.load("other", "personal", draft.url, now))
        assertNull(drafts.load("tab", "work", draft.url, now))
        assertNull(drafts.load("tab", "personal", "https://example.org/reply?id=2", now))
        assertNull(PrivateFormDrafts().load("tab", "personal", draft.url, now))
    }

    @Test
    fun `closing tab deletes private drafts without affecting other tabs`() {
        val drafts = PrivateFormDrafts()
        drafts.update("first", draft, delete = false, now = now)
        drafts.update("second", draft, delete = false, now = now)
        drafts.deleteTab("first")

        assertNull(drafts.load("first", "personal", draft.url, now))
        assertEquals(draft, drafts.load("second", "personal", draft.url, now))
        drafts.deleteProfile("personal")
        assertNull(drafts.load("second", "personal", draft.url, now))
    }

    @Test
    fun `submission expiry and disabling remove recovery text`() {
        val drafts = PrivateFormDrafts()
        drafts.update("tab", draft, delete = false, now = now)
        drafts.update("tab", draft, delete = true, now = now)
        assertNull(drafts.load("tab", "personal", draft.url, now))
        drafts.update("tab", draft, delete = false, now = now)
        assertNull(drafts.load("tab", "personal", draft.url, now + FormDraftRules.LIFETIME_MILLIS))
        drafts.update("tab", draft, delete = false, now = now)
        drafts.clear()
        assertNull(drafts.load("tab", "personal", draft.url, now))
    }

    @Test
    fun `private memory retains at most twenty recent pages`() {
        val drafts = PrivateFormDrafts()
        repeat(21) { index ->
            drafts.update("tab-$index", draft.copy(updatedAt = now + index), delete = false, now = now + index)
        }

        assertNull(drafts.load("tab-0", "personal", draft.url, now + 20))
        assertEquals(draft.copy(updatedAt = now + 1), drafts.load("tab-1", "personal", draft.url, now + 20))
        assertEquals(draft.copy(updatedAt = now + 20), drafts.load("tab-20", "personal", draft.url, now + 20))
    }
}
