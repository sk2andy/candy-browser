package dev.sk2andy.materialbrowser.browser.gecko

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoInlineVideoGestureHapticRulesTest {
    private val identity = GeckoInlineVideoIdentity(
        documentNonce = "a".repeat(32),
        elementNonce = "b".repeat(32),
    )
    private val request = GeckoInlineVideoGestureHaptic(
        identity = identity,
        navigationGeneration = 3,
        phase = GeckoInlineVideoGestureHapticPhase.RubberbandStart,
    )

    @Test
    fun `current presented regular selected video forwards haptic`() {
        assertTrue(shouldForward())
    }

    @Test
    fun `stale private hidden and background haptics are rejected`() {
        assertFalse(shouldForward(isActivityResumed = false))
        assertFalse(shouldForward(isPrivate = true))
        assertFalse(shouldForward(isSelectedTab = false))
        assertFalse(shouldForward(isCurrentSession = false))
        assertFalse(shouldForward(currentNavigationGeneration = 2))
        assertFalse(shouldForward(isInlineVideoPresented = false))
        assertFalse(
            shouldForward(
                currentIdentity = identity.copy(elementNonce = "c".repeat(32)),
            ),
        )
    }

    @Test
    fun `rubberband owner survives only while exact presentation stays current`() {
        assertTrue(ownerRemainsValid())
        assertFalse(ownerRemainsValid(isActivityResumed = false))
        assertFalse(ownerRemainsValid(isSelectedTab = false))
        assertFalse(ownerRemainsValid(isCurrentSession = false))
        assertFalse(ownerRemainsValid(currentNavigationGeneration = 4))
        assertFalse(ownerRemainsValid(isInlineVideoPresented = false))
        assertFalse(
            ownerRemainsValid(
                currentIdentity = identity.copy(elementNonce = "c".repeat(32)),
            ),
        )
    }

    @Test
    fun `session loss invalidates rubberband owner`() {
        assertFalse(ownerRemainsValid(isCurrentSession = false))
    }

    private fun shouldForward(
        isActivityResumed: Boolean = true,
        isPrivate: Boolean = false,
        isSelectedTab: Boolean = true,
        isCurrentSession: Boolean = true,
        currentNavigationGeneration: Int? = 3,
        isInlineVideoPresented: Boolean = true,
        currentIdentity: GeckoInlineVideoIdentity? = identity,
    ): Boolean = GeckoInlineVideoGestureHapticRules.shouldForward(
        isActivityResumed = isActivityResumed,
        isPrivate = isPrivate,
        isSelectedTab = isSelectedTab,
        isCurrentSession = isCurrentSession,
        currentNavigationGeneration = currentNavigationGeneration,
        request = request,
        isInlineVideoPresented = isInlineVideoPresented,
        currentIdentity = currentIdentity,
    )

    private fun ownerRemainsValid(
        isActivityResumed: Boolean = true,
        isSelectedTab: Boolean = true,
        isCurrentSession: Boolean = true,
        currentNavigationGeneration: Int? = 3,
        isInlineVideoPresented: Boolean = true,
        currentIdentity: GeckoInlineVideoIdentity? = identity,
    ): Boolean = GeckoInlineVideoGestureHapticRules.ownerRemainsValid(
        isActivityResumed = isActivityResumed,
        isPrivate = false,
        isSelectedTab = isSelectedTab,
        isCurrentSession = isCurrentSession,
        currentNavigationGeneration = currentNavigationGeneration,
        ownerNavigationGeneration = 3,
        isInlineVideoPresented = isInlineVideoPresented,
        currentIdentity = currentIdentity,
        ownerIdentity = identity,
    )
}
