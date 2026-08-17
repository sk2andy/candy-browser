package dev.sk2andy.materialbrowser.blocking

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentRequestRulesTest {
    @Test
    fun `inmobi consent runtime is blocked across publisher sites`() {
        assertTrue(
            ConsentRequestRules.shouldBlock(
                isForMainFrame = false,
                cookieBannerRemovalEnabled = true,
                sitePaused = false,
                requestHost = "cmp.inmobi.com",
            ),
        )
        assertTrue(shouldBlock(requestHost = "edge.cmp.inmobi.com"))
    }

    @Test
    fun `main frame site pause and consent opt out remain allowed`() {
        assertFalse(shouldBlock(isForMainFrame = true))
        assertFalse(shouldBlock(sitePaused = true))
        assertFalse(shouldBlock(cookieBannerRemovalEnabled = false))
    }

    @Test
    fun `lookalike and unrelated hosts remain allowed`() {
        assertFalse(shouldBlock(requestHost = "notcmp.inmobi.com.example"))
        assertFalse(shouldBlock(requestHost = "cdn.inmobi.com"))
        assertFalse(shouldBlock(requestHost = null))
    }

    private fun shouldBlock(
        isForMainFrame: Boolean = false,
        cookieBannerRemovalEnabled: Boolean = true,
        sitePaused: Boolean = false,
        requestHost: String? = "cmp.inmobi.com",
    ): Boolean = ConsentRequestRules.shouldBlock(
        isForMainFrame = isForMainFrame,
        cookieBannerRemovalEnabled = cookieBannerRemovalEnabled,
        sitePaused = sitePaused,
        requestHost = requestHost,
    )
}
