package dev.sk2andy.materialbrowser.browser.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantSummaryRequestTest {
    @Test
    fun createsRequestForNormalizedWebUrl() {
        val request = AssistantSummaryRequest.create(
            url = " https://example.com/article ",
            title = " Example article ",
            instruction = " Summarize this website in English. ",
        )

        assertEquals("https://example.com/article", request?.url)
        assertEquals("Example article", request?.title)
        assertEquals(
            "Summarize this website in English.\n\nExample article\nhttps://example.com/article",
            request?.prompt,
        )
    }

    @Test
    fun rejectsNonWebUrlAndBlankInstruction() {
        assertNull(
            AssistantSummaryRequest.create(
                url = "about:blank",
                title = "New tab",
                instruction = "Summarize",
            ),
        )
        assertNull(
            AssistantSummaryRequest.create(
                url = "https://example.com",
                title = "Example",
                instruction = "   ",
            ),
        )
    }

    @Test
    fun routesGoogleVoiceAssistantToGeminiShareTarget() {
        assertEquals(
            listOf("com.google.android.apps.bard"),
            AssistantTargetPolicy.deliveryPackages(
                activeVoiceServicePackage = "com.google.android.googlequicksearchbox",
                resolvedAssistActivityPackage = "com.google.android.googlequicksearchbox",
            ),
        )
    }

    @Test
    fun routesOtherAssistantDirectlyAndKeepsDistinctAssistActivityFallback() {
        assertEquals(
            listOf("com.example.assistant", "com.example.assistant.activity"),
            AssistantTargetPolicy.deliveryPackages(
                activeVoiceServicePackage = "com.example.assistant",
                resolvedAssistActivityPackage = "com.example.assistant.activity",
            ),
        )
    }

    @Test
    fun ignoresSystemResolverWhenNoActiveAssistantExists() {
        assertEquals(
            emptyList<String>(),
            AssistantTargetPolicy.deliveryPackages(
                activeVoiceServicePackage = null,
                resolvedAssistActivityPackage = "android",
            ),
        )
    }
}
