package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPresentationRulesTest {
    @Test
    fun `enabled animation shows splash without opening editor`() {
        assertEquals(
            StartupPresentation(showSplash = true, openAddressEditor = false),
            StartupPresentationRules.resolve(
                isColdStart = true,
                isLauncherLaunch = true,
                isStartupAnimationEnabled = true,
                isOnboardingRequired = false,
            ),
        )
    }

    @Test
    fun `disabled animation skips splash and opens editor`() {
        assertEquals(
            StartupPresentation(showSplash = false, openAddressEditor = true),
            StartupPresentationRules.resolve(
                isColdStart = true,
                isLauncherLaunch = true,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = false,
            ),
        )
    }

    @Test
    fun `onboarding keeps editor closed when animation is disabled`() {
        assertEquals(
            StartupPresentation(showSplash = false, openAddressEditor = false),
            StartupPresentationRules.resolve(
                isColdStart = true,
                isLauncherLaunch = true,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = true,
            ),
        )
    }

    @Test
    fun `external launches and recreation do not change startup presentation`() {
        listOf(
            StartupPresentationRules.resolve(
                isColdStart = true,
                isLauncherLaunch = false,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = false,
            ),
            StartupPresentationRules.resolve(
                isColdStart = false,
                isLauncherLaunch = true,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = false,
            ),
        ).forEach { presentation ->
            assertEquals(
                StartupPresentation(showSplash = false, openAddressEditor = false),
                presentation,
            )
        }
    }

    @Test
    fun `later launcher request opens editor only when animation is disabled`() {
        assertTrue(
            StartupPresentationRules.shouldOpenAddressEditor(
                isLauncherLaunch = true,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = false,
            ),
        )
        assertFalse(
            StartupPresentationRules.shouldOpenAddressEditor(
                isLauncherLaunch = false,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = false,
            ),
        )
        assertFalse(
            StartupPresentationRules.shouldOpenAddressEditor(
                isLauncherLaunch = true,
                isStartupAnimationEnabled = false,
                isOnboardingRequired = true,
            ),
        )
    }
}
