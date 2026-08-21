package dev.sk2andy.materialbrowser.blocking

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralCosmeticRulesTest {
    @Test
    fun `parser resolves host suffix and wildcard tld rules`() {
        val rules = ProceduralCosmeticRules.parse(
            asset(
                row("H", "news.example", ".sponsor", "Anzeige", "i"),
                row("R", "stream.*", "#popup-ad", null, "s"),
            ),
        )

        assertEquals(1, rules.matchingRules("https://sub.news.example/story").size)
        assertEquals(1, rules.matchingRules("https://stream.com/watch").size)
        assertTrue(rules.matchingRules("https://safe.example/").isEmpty())
    }

    @Test
    fun `script stays declarative and bounded`() {
        val script = CandyProceduralCosmeticScript.create(
            listOf(
                ProceduralCosmeticRule(
                    action = ProceduralCosmeticAction.Remove,
                    hostPattern = "news.example",
                    selector = ".ad",
                    text = "Sponsored",
                    ignoreCase = true,
                ),
            ),
        )

        assertTrue("MutationObserver" in script)
        assertTrue("slice.call(nodes,0,128)" in script)
        assertTrue("performance.now()+8" in script)
        assertTrue("setTimeout(function(){observer.disconnect();state.timer=null},5000)" in script)
        assertTrue("state.active" in script)
        assertTrue("DOMContentLoaded" in CandyProceduralCosmeticScript.cleanupScript)
        assertTrue("Array.from(w.frames).forEach(clean)" in CandyProceduralCosmeticScript.cleanupScript)
        assertFalse("eval(" in script)
        assertFalse("new Function" in script)
    }

    private fun asset(vararg rows: String): String = buildString {
        appendLine(ProceduralCosmeticRules.HEADER)
        appendLine("# Rules: ${rows.size}")
        rows.forEach(::appendLine)
    }

    private fun row(
        action: String,
        host: String,
        selector: String,
        text: String?,
        mode: String,
    ): String = listOf(
        action,
        host,
        encode(selector),
        text?.let(::encode) ?: "-",
        mode,
    ).joinToString("\t")

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))
}
