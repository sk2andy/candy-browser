package dev.sk2andy.materialbrowser.browser.formdraft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormDraftRulesTest {
    private val now = 2_000_000_000L
    private fun draft(profile: String = "work", url: String = "https://example.org/reply?id=1#editor") =
        FormDraft(profile, url, listOf(FormDraftField("reply", "My unsent reply\nSecond line")), now)

    @Test
    fun `exact page identity preserves query and fragment`() {
        assertEquals("https://example.org/reply?id=1#editor", FormDraftRules.pageUrl(draft().url))
        listOf("https://user:secret@example.org/", "file:///reply", "javascript:alert(1)", "https://example.org/a b")
            .forEach { assertNull(FormDraftRules.pageUrl(it)) }
    }

    @Test
    fun `expiry and future timestamps fail closed`() {
        assertNotNull(FormDraftRules.sanitize(draft(), now + FormDraftRules.LIFETIME_MILLIS - 1))
        assertNull(FormDraftRules.sanitize(draft(), now + FormDraftRules.LIFETIME_MILLIS))
        assertNull(FormDraftRules.sanitize(draft().copy(updatedAt = now + 1), now))
    }

    @Test
    fun `empty text cannot leave a restorable draft`() {
        assertNull(FormDraftRules.sanitize(draft().copy(fields = listOf(FormDraftField("reply", " \n"))), now))
        assertNotNull(FormDraftRules.sanitize(draft(), now))
    }

    @Test
    fun `duplicate ambiguous keys and oversized values are rejected`() {
        assertNull(FormDraftRules.fields(listOf(FormDraftField("reply", "a"), FormDraftField("reply", "b"))))
        assertNull(FormDraftRules.fields(listOf(FormDraftField("reply", "a".repeat(16_001)))))
        assertNull(FormDraftRules.fields((1..5).map { FormDraftField("reply$it", "a".repeat(16_000)) }))
        assertNull(FormDraftRules.fields(listOf(FormDraftField("reply", "bad\u0000value"))))
    }

    @Test
    fun `pruning separates profiles pages and keeps newest twenty`() {
        val variants = listOf(draft(), draft("personal"), draft(url = "https://example.org/reply?id=2#editor"))
        assertEquals(3, FormDraftRules.prune(variants, now).size)
        val many = (0..25).map { draft(url = "https://example.org/$it").copy(updatedAt = now - it) }
        val kept = FormDraftRules.prune(many, now)
        assertEquals(20, kept.size)
        assertEquals("https://example.org/0", kept.first().url)
        assertEquals("https://example.org/19", kept.last().url)
    }
}
