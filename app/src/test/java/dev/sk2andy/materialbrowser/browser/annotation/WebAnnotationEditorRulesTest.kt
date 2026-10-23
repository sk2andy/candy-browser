package dev.sk2andy.materialbrowser.browser.annotation

import org.junit.Assert.assertEquals
import org.junit.Test

class WebAnnotationEditorRulesTest {
    @Test
    fun `inline formats surround selected text and retain its selection`() {
        assertEquals(
            WebAnnotationEdit("A **saved** thought", 4, 9),
            WebAnnotationEditorRules.apply("A saved thought", 2, 7, WebAnnotationFormat.Bold),
        )
        assertEquals(
            WebAnnotationEdit("A *saved* thought", 3, 8),
            WebAnnotationEditorRules.apply("A saved thought", 2, 7, WebAnnotationFormat.Italic),
        )
        assertEquals(
            WebAnnotationEdit("A `saved` thought", 3, 8),
            WebAnnotationEditorRules.apply("A saved thought", 2, 7, WebAnnotationFormat.Code),
        )
    }

    @Test
    fun `empty inline selection leaves cursor between markers without placeholders`() {
        assertEquals(WebAnnotationEdit("****", 2, 2), WebAnnotationEditorRules.apply("", 0, 0, WebAnnotationFormat.Bold))
        assertEquals(WebAnnotationEdit("a**b", 2, 2), WebAnnotationEditorRules.apply("ab", 1, 1, WebAnnotationFormat.Italic))
        assertEquals(WebAnnotationEdit("``", 1, 1), WebAnnotationEditorRules.apply("", 0, 0, WebAnnotationFormat.Code))
    }

    @Test
    fun `link selects editable destination with or without a label selection`() {
        assertEquals(
            WebAnnotationEdit("See [source](https://).", 13, 21),
            WebAnnotationEditorRules.apply("See source.", 4, 10, WebAnnotationFormat.Link),
        )
        assertEquals(
            WebAnnotationEdit("[](https://)", 3, 11),
            WebAnnotationEditorRules.apply("", 0, 0, WebAnnotationFormat.Link),
        )
    }

    @Test
    fun `headings prefix touched lines and keep existing content selected`() {
        assertEquals(
            WebAnnotationEdit("First\n## Second\nLast", 11, 14),
            WebAnnotationEditorRules.apply("First\nSecond\nLast", 8, 11, WebAnnotationFormat.Heading),
        )
        assertEquals(
            WebAnnotationEdit("## One\n## Two", 3, 13),
            WebAnnotationEditorRules.apply("One\nTwo", 0, 7, WebAnnotationFormat.Heading),
        )
    }

    @Test
    fun `list prefixes selected lines but excludes next line at selection boundary`() {
        assertEquals(
            WebAnnotationEdit("- One\n- Two\nThree", 2, 12),
            WebAnnotationEditorRules.apply("One\nTwo\nThree", 0, 8, WebAnnotationFormat.BulletList),
        )
        assertEquals(
            WebAnnotationEdit("One\n- Two\n- Three", 8, 17),
            WebAnnotationEditorRules.apply("One\nTwo\nThree", 6, 13, WebAnnotationFormat.BulletList),
        )
    }

    @Test
    fun `line formats work in empty trailing lines and preserve line endings`() {
        assertEquals(WebAnnotationEdit("## ", 3, 3), WebAnnotationEditorRules.apply("", 0, 0, WebAnnotationFormat.Heading))
        assertEquals(WebAnnotationEdit("One\n- ", 6, 6), WebAnnotationEditorRules.apply("One\n", 4, 4, WebAnnotationFormat.BulletList))
        assertEquals(
            WebAnnotationEdit("- One\r\n- Two", 2, 12),
            WebAnnotationEditorRules.apply("One\r\nTwo", 0, 8, WebAnnotationFormat.BulletList),
        )
    }

    @Test
    fun `reversed selections keep direction and invalid offsets are clamped`() {
        assertEquals(
            WebAnnotationEdit("**text**", 6, 2),
            WebAnnotationEditorRules.apply("text", 4, 0, WebAnnotationFormat.Bold),
        )
        assertEquals(
            WebAnnotationEdit("- One\n- Two", 11, 2),
            WebAnnotationEditorRules.apply("One\nTwo", 7, 0, WebAnnotationFormat.BulletList),
        )
        assertEquals(
            WebAnnotationEdit("*text*", 1, 5),
            WebAnnotationEditorRules.apply("text", Int.MIN_VALUE, Int.MAX_VALUE, WebAnnotationFormat.Italic),
        )
    }

    @Test
    fun `formatting respects exact note limit and rejects overflow without partial changes`() {
        val fits = "x".repeat(3_996)
        assertEquals(
            WebAnnotationEdit("**$fits**", 2, 3_998),
            WebAnnotationEditorRules.apply(fits, 0, fits.length, WebAnnotationFormat.Bold),
        )
        val full = "x".repeat(4_000)
        for (format in WebAnnotationFormat.entries) {
            assertEquals(WebAnnotationEdit(full, 4_000, 0), WebAnnotationEditorRules.apply(full, 4_000, 0, format))
        }
        val multiline = "x".repeat(3_995) + "\ny"
        assertEquals(
            WebAnnotationEdit(multiline, 0, 3_997),
            WebAnnotationEditorRules.apply(multiline, 0, multiline.length, WebAnnotationFormat.BulletList),
        )
        val oversized = "x".repeat(4_001)
        assertEquals(WebAnnotationEdit(oversized, 0, 4_001), WebAnnotationEditorRules.apply(oversized, -1, 5_000, WebAnnotationFormat.Bold))
    }
}
