package dev.sk2andy.materialbrowser.browser.gecko

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoSelectionLifecycleInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun releasedAndClosedSessionsDoNotRetainActivitySelectionDelegate() {
        composeRule.runOnUiThread {
            val session = GeckoRuntimeOwner.getOrCreate(composeRule.activity).createSession(
                profileId = "selection-lifecycle",
                isPrivate = false,
                privacyPolicy = GeckoPrivacyPolicy.Disabled,
            )
            try {
                val firstView = session.createView(composeRule.activity)
                val nativeSession = requireNotNull(findGeckoView(firstView)?.session)
                val firstDelegate = nativeSession.selectionActionDelegate
                assertTrue(firstDelegate is GeckoWebTextSelectionDelegate)
                session.releaseView(firstView)
                assertNull(nativeSession.selectionActionDelegate)

                session.createView(composeRule.activity)
                assertNotNull(nativeSession.selectionActionDelegate)
                assertNotSame(firstDelegate, nativeSession.selectionActionDelegate)
                session.close()
                assertNull(nativeSession.selectionActionDelegate)
            } finally {
                session.close()
            }
        }
    }

    private fun findGeckoView(view: View): GeckoView? {
        if (view is GeckoView) return view
        val group = view as? ViewGroup ?: return null
        for (index in 0 until group.childCount) {
            findGeckoView(group.getChildAt(index))?.let { return it }
        }
        return null
    }
}
