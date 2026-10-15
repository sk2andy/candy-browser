package dev.sk2andy.materialbrowser.browser.integration

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProcessTextRulesTest {
    @Test
    fun `selected text is bounded before conversion and strips unsafe controls`() {
        assertEquals("Selected\ntext\tvalue", ProcessTextRules.selectedText(" \u0000Selected\ntext\tvalue\u0007 ", 50))
        assertNull(ProcessTextRules.selectedText(null, 20))
        assertNull(ProcessTextRules.selectedText(" \n\t", 20))
        assertEquals("x".repeat(2_000), ProcessTextRules.selectedText("x".repeat(2_000), ProcessTextRules.MAX_RESEARCH_CHARACTERS))
        assertNull(ProcessTextRules.selectedText("x".repeat(2_001), ProcessTextRules.MAX_RESEARCH_CHARACTERS))
        val oversized = object : CharSequence {
            override val length = 20_001
            override fun get(index: Int): Char = error("Oversized text must not be read")
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = error("Must not copy")
            override fun toString(): String = error("Must not convert")
        }
        assertNull(ProcessTextRules.selectedText(oversized, ProcessTextRules.MAX_SPEECH_CHARACTERS))
    }

    @Test
    fun `research always searches selected text instead of executing URLs or commands`() {
        assertEquals(
            "https://www.google.com/search?q=javascript%3Aalert%281%29",
            ProcessTextRules.researchUrl("javascript:alert(1)", SearchEngine.Google),
        )
        assertEquals(
            "https://www.google.com/search?q=https%3A%2F%2Fexample.com",
            ProcessTextRules.researchUrl("https://example.com", SearchEngine.Google),
        )
        assertNull(ProcessTextRules.researchUrl("query", SearchEngine.SearXNG))
    }

    @Test
    fun `profile choices exclude protected and synced destinations`() {
        val profiles = listOf(
            BrowserProfile("personal", "🍬"),
            BrowserProfile("work", "💼"),
            BrowserProfile("locked", "🔒", protection = ProfileProtection(ProfileLockTrigger.AppClosed)),
            BrowserProfile("remote", "💻", syncedDeviceId = "device"),
        )
        assertEquals(listOf("personal", "work"), ProcessTextRules.eligibleProfiles(profiles, "work", true).map(BrowserProfile::id))
        assertEquals(listOf("work"), ProcessTextRules.eligibleProfiles(profiles, "work", false).map(BrowserProfile::id))
        assertEquals(emptyList<String>(), ProcessTextRules.eligibleProfiles(profiles, "locked", false).map(BrowserProfile::id))
    }

    @Test
    fun `research handoff tokens are single use and expire`() {
        val requests = ProcessTextResearchRequestStore()
        val request = ProcessTextResearchRequest("selected text", "work")
        val token = requests.put(request, 100)!!
        assertEquals(request, requests.take(token, 101))
        assertNull(requests.take(token, 102))
        assertNull(requests.take("untrusted", 102))
        val expired = requests.put(request, 200)!!
        assertNull(requests.take(expired, 60_201))
        val future = requests.put(request, 100_000)!!
        assertNull(requests.take(future, 99_999))
    }

    @Test
    fun `handoff retains bounded requests and rejects malformed input`() {
        val requests = ProcessTextResearchRequestStore()
        val request = ProcessTextResearchRequest("text", "work")
        val tokens = List(9) { requests.put(request, it.toLong())!! }
        assertNull(requests.take(tokens.first(), 10))
        assertEquals(request, requests.take(tokens.last(), 10))
        assertNull(requests.put(request.copy(text = ""), 10))
        assertNull(requests.put(request.copy(profileId = "bad id"), 10))
    }
}
