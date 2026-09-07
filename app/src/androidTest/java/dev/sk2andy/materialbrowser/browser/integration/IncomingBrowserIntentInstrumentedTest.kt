package dev.sk2andy.materialbrowser.browser.integration

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingBrowserIntentInstrumentedTest {
    @Test
    fun viewIntentAcceptsNormalizedWebUrl() {
        val request = IncomingBrowserIntent.from(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/article")),
        )

        assertEquals("https://example.com/article", request?.url)
    }

    @Test
    fun sendIntentAcceptsPlainAndHtmlWebUrlText() {
        listOf("text/plain", "text/html").forEach { mimeType ->
            val request = IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_TEXT, " https://example.com/shared ")
                },
            )

            assertEquals("https://example.com/shared", request?.url)
        }
    }

    @Test
    fun sendIntentBoundsSharedTextBeforeParsing() {
        val maximumLengthUrl = "https://example.com/" + "a".repeat(32_748)
        val overLimitUrl = maximumLengthUrl + "a"

        assertEquals(
            maximumLengthUrl,
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, maximumLengthUrl)
                },
            )?.url,
        )
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, overLimitUrl)
                },
            ),
        )
    }

    @Test
    fun sendIntentRejectsNonWebMalformedAndAmbiguousText() {
        listOf(
            "javascript:alert(1)",
            "https:///missing-host",
            "Read https://example.com/article",
            "https://example.com/one https://example.com/two",
            "<a href=\"https://example.com/article\">Article</a>",
        ).forEach { sharedText ->
            assertNull(
                IncomingBrowserIntent.from(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, sharedText)
                    },
                ),
            )
        }
    }

    @Test
    fun sendIntentRejectsMissingOrUnsupportedPayloadContract() {
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                },
            ),
        )
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_TEXT, "https://example.com/shared")
                },
            ),
        )
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "https://example.com/shared")
                },
            ),
        )
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(
                        Intent.EXTRA_HTML_TEXT,
                        "<a href=\"https://example.com/shared\">Article</a>",
                    )
                },
            ),
        )
        assertNull(
            IncomingBrowserIntent.from(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, 42)
                },
            ),
        )
    }
}
