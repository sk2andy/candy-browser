package dev.sk2andy.materialbrowser.browser.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAnnotationRulesTest {
    private val annotation = WebAnnotation(
        id = "saved-quote", profileId = "work", url = "https://example.org/article#paragraph",
        title = "Research", quote = "Original quote stays unchanged", note = "Compare return policies", createdAt = 100L,
    )

    @Test
    fun `canonical page drops anchor while original quote survives unchanged`() {
        val value = requireNotNull(WebAnnotationRules.sanitize(annotation))
        assertEquals("https://example.org/article", value.url)
        assertEquals("Original quote stays unchanged", value.quote)
        assertEquals(value, WebAnnotationRules.sanitize(value))
    }

    @Test
    fun `notes and quotes searchable only in requested profiles`() {
        val saved = listOf(annotation)
        assertEquals("saved-quote", annotation.id)
        assertEquals("https://example.org/article#paragraph", WebAnnotationRules.search(saved, setOf("work"), "return policies", 2).single().url)
        assertTrue(WebAnnotationRules.search(saved, setOf("personal"), "return policies", 2).isEmpty())
        assertTrue(WebAnnotationRules.search(saved, setOf("work"), "missing words", 2).isEmpty())
        assertTrue(WebAnnotationRules.search(saved, setOf("work"), "a", 2).isEmpty())
    }

    @Test
    fun `untrusted URLs oversized selections and invalid identities rejected`() {
        assertNull(WebAnnotationRules.sanitize(annotation.copy(url = "javascript:alert(1)")))
        assertNull(WebAnnotationRules.sanitize(annotation.copy(quote = "x".repeat(8_001))))
        assertNull(WebAnnotationRules.sanitize(annotation.copy(note = "x".repeat(4_001))))
        assertNull(WebAnnotationRules.sanitize(annotation.copy(profileId = "")))
        assertNull(WebAnnotationRules.sanitize(annotation.copy(createdAt = -1)))
        assertNull(WebAnnotationRules.sanitize(annotation.copy(quote = " ")))
    }
}
