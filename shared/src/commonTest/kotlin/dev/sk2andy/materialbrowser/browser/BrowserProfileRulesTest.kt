package dev.sk2andy.materialbrowser.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserProfileRulesTest {
    @Test
    fun `create normalizes icon and uses supported isolation`() {
        val profile = BrowserProfileRules.create(
            draft = BrowserProfileDraft(emoji = "  💼  ", isolationRequested = true),
            profileId = " profile-id ",
            isolationSupported = true,
        )

        assertEquals("profile-id", profile?.id)
        assertEquals("💼", profile?.emoji)
        assertTrue(profile?.isolationEnabled == true)
    }

    @Test
    fun `create disables unsupported isolation and rejects empty values`() {
        val profile = BrowserProfileRules.create(
            draft = BrowserProfileDraft(emoji = "🏠", isolationRequested = true),
            profileId = "home",
            isolationSupported = false,
        )

        assertFalse(requireNotNull(profile).isolationEnabled)
        assertNull(
            BrowserProfileRules.create(
                draft = BrowserProfileDraft(emoji = " ", isolationRequested = false),
                profileId = "home",
                isolationSupported = true,
            ),
        )
    }

    @Test
    fun `local profile can change icon and isolation`() {
        val profile = BrowserProfile(id = "work", emoji = "💼")

        assertEquals("⭐", BrowserProfileRules.updateEmoji(profile, " ⭐ ")?.emoji)
        assertTrue(
            BrowserProfileRules.updateIsolation(
                profile = profile,
                enabled = true,
                isolationSupported = true,
            )?.isolationEnabled == true,
        )
    }

    @Test
    fun `synced profile cannot mutate local icon or isolation`() {
        val profile = BrowserProfile(
            id = "synced:phone",
            emoji = "📱",
            syncedDeviceId = "phone",
        )

        assertNull(BrowserProfileRules.updateEmoji(profile, "⭐"))
        assertNull(
            BrowserProfileRules.updateIsolation(
                profile = profile,
                enabled = true,
                isolationSupported = true,
            ),
        )
    }
}
