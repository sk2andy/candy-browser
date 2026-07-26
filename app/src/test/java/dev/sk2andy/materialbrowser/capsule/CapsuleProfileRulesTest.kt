package dev.sk2andy.materialbrowser.capsule

import dev.sk2andy.materialbrowser.browser.BrowserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsuleProfileRulesTest {
    @Test
    fun `profile mapping is stable and isolation follows provider support`() {
        val capsule = capsule("work")
        val profiles = listOf(BrowserProfile("work", "💼", isolationEnabled = true))

        val supported = CapsuleProfileRules.resolve(capsule, profiles, true)!!
        val unsupported = CapsuleProfileRules.resolve(capsule, profiles, false)!!

        assertEquals("work", supported.profileId)
        assertTrue(supported.isolationEnabled)
        assertTrue(supported.webViewProfileName!!.startsWith("candy_profile_v1_"))
        assertFalse(unsupported.isolationEnabled)
        assertNull(unsupported.webViewProfileName)
        assertNull(CapsuleProfileRules.resolve(capsule("missing"), profiles, true))
    }

    private fun capsule(profileId: String) = SiteCapsule(
        id = "04a74ad8-7533-460c-bfbf-a135968940d5",
        name = "Mail",
        startUrl = "https://mail.example",
        profileId = profileId,
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
}
