package dev.sk2andy.materialbrowser.capsule

import org.junit.Assert.assertEquals
import org.junit.Test

class CapsuleIconUpdateRulesTest {
    @Test
    fun `legacy rendered favicon is preserved only while customization stays unchanged`() {
        assertEquals(
            CapsuleIconMode.Favicon,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = false,
                hasRenderedIcon = true,
                customizationChanged = false,
            ),
        )
        assertEquals(
            CapsuleIconMode.ProfileFallback,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = false,
                hasRenderedIcon = true,
                customizationChanged = true,
            ),
        )
    }

    @Test
    fun `favicon mode requires source or unchanged rendered icon`() {
        assertEquals(
            CapsuleIconMode.Favicon,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = true,
                hasRenderedIcon = false,
                customizationChanged = true,
            ),
        )
        assertEquals(
            CapsuleIconMode.ProfileFallback,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = false,
                hasRenderedIcon = false,
                customizationChanged = false,
            ),
        )
    }
}
