package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledConsentActionsTest {
    @Test
    fun `parses strict host scoped reject action`() {
        val actions = BundledConsentActions.parse(
            """
                candy-consent-actions:1
                reject\tplus.web.de\tI3JlbWluZGVy\tweb-de-remind-later
            """.trimIndent().replace("\\t", "\t"),
        )

        assertEquals(
            BundledConsentAction("web-de-remind-later", "plus.web.de", "#reminder"),
            actions.single(),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects executable selector payload`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("button{color:red}".toByteArray())

        BundledConsentActions.parse(
            "candy-consent-actions:1\nreject\texample.com\t$encoded\tunsafe",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects broad action selector`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("button".toByteArray())

        BundledConsentActions.parse(
            "candy-consent-actions:1\nreject\texample.com\t$encoded\tunsafe",
        )
    }

    @Test
    fun `malformed runtime asset falls back to no actions`() {
        assertTrue(BundledConsentActions.parseOrEmpty("broken").isEmpty())
    }
}
