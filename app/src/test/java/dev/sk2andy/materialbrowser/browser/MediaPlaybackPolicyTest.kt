package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlaybackPolicyTest {
    @Test
    fun selectedForegroundTabCanContinuePlaybackAfterUnmute() {
        assertFalse(
            MediaPlaybackPolicy.requiresUserGesture(
                tabId = "selected",
                selectedTabId = "selected",
                isActivityResumed = true,
            ),
        )
    }

    @Test
    fun backgroundTabStillRequiresUserGesture() {
        assertTrue(
            MediaPlaybackPolicy.requiresUserGesture(
                tabId = "background",
                selectedTabId = "selected",
                isActivityResumed = true,
            ),
        )
    }

    @Test
    fun pausedActivityStillRequiresUserGesture() {
        assertTrue(
            MediaPlaybackPolicy.requiresUserGesture(
                tabId = "selected",
                selectedTabId = "selected",
                isActivityResumed = false,
            ),
        )
    }
}
