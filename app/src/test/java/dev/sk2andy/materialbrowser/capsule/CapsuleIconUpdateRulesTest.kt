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
                hasCustomIcon = false,
                hasRenderedIcon = true,
                customizationChanged = false,
            ),
        )
        assertEquals(
            CapsuleIconMode.ProfileFallback,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = false,
                hasCustomIcon = false,
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
                hasCustomIcon = false,
                hasRenderedIcon = false,
                customizationChanged = true,
            ),
        )
        assertEquals(
            CapsuleIconMode.ProfileFallback,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Favicon,
                hasSourceFavicon = false,
                hasCustomIcon = false,
                hasRenderedIcon = false,
                customizationChanged = false,
            ),
        )
    }

    @Test
    fun `custom mode requires a stored or submitted custom icon`() {
        assertEquals(
            CapsuleIconMode.Custom,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Custom,
                hasSourceFavicon = true,
                hasCustomIcon = true,
                hasRenderedIcon = false,
                customizationChanged = true,
            ),
        )
        assertEquals(
            CapsuleIconMode.ProfileFallback,
            CapsuleIconUpdateRules.resolveMode(
                requestedMode = CapsuleIconMode.Custom,
                hasSourceFavicon = true,
                hasCustomIcon = false,
                hasRenderedIcon = true,
                customizationChanged = false,
            ),
        )
    }
}
