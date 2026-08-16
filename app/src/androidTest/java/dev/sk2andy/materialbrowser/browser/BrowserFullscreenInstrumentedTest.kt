package dev.sk2andy.materialbrowser.browser

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserFullscreenInstrumentedTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

    @Test
    fun chromeClientForwardsFullscreenShowAndHide() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val shownView = AtomicReference<View>()
            val hiddenCount = AtomicInteger()
            val controller = BrowserController(
                activity = activity,
                showFullscreenWebContent = { view, _ ->
                    shownView.set(view)
                    true
                },
                hideFullscreenWebContent = hiddenCount::incrementAndGet,
            )
            val chromeClient = requireNotNull(controller.selectedWebViewForTesting().webChromeClient)
            val customView = View(activity)
            val customViewCallback = WebChromeClient.CustomViewCallback {}

            chromeClient.onShowCustomView(customView, customViewCallback)
            chromeClient.onHideCustomView()

            assertSame(customView, shownView.get())
            assertEquals(1, hiddenCount.get())
            controller.destroy()
        }
    }

    @Test
    fun staleChromeClientCannotReplaceOrHideCurrentFullscreenOwner() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val activeHostCallback = AtomicReference<WebChromeClient.CustomViewCallback>()
            val showCount = AtomicInteger()
            val hideCount = AtomicInteger()
            val dismissCount = AtomicInteger()
            val firstPageDismissCount = AtomicInteger()
            val staleShowDismissCount = AtomicInteger()
            val controller = BrowserController(
                activity = activity,
                showFullscreenWebContent = { _, callback ->
                    activeHostCallback.set(callback)
                    showCount.incrementAndGet()
                    true
                },
                hideFullscreenWebContent = hideCount::incrementAndGet,
                dismissFullscreenWebContent = {
                    dismissCount.incrementAndGet()
                    activeHostCallback.getAndSet(null)?.onCustomViewHidden()
                },
            )
            val firstChromeClient =
                requireNotNull(controller.selectedWebViewForTesting().webChromeClient)
            firstChromeClient.onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback(firstPageDismissCount::incrementAndGet),
            )

            controller.createTab()
            val secondChromeClient =
                requireNotNull(controller.selectedWebViewForTesting().webChromeClient)
            secondChromeClient.onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback {},
            )
            firstChromeClient.onHideCustomView()
            firstChromeClient.onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback(staleShowDismissCount::incrementAndGet),
            )

            assertEquals(1, dismissCount.get())
            assertEquals(1, firstPageDismissCount.get())
            assertEquals(1, staleShowDismissCount.get())
            assertEquals(2, showCount.get())
            assertEquals(0, hideCount.get())

            secondChromeClient.onHideCustomView()
            assertEquals(1, hideCount.get())
            controller.destroy()
        }
    }

    @Test
    fun closingOwnerWebViewDismissesFullscreen() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val activeHostCallback = AtomicReference<WebChromeClient.CustomViewCallback>()
            val dismissCount = AtomicInteger()
            val pageDismissCount = AtomicInteger()
            val controller = BrowserController(
                activity = activity,
                showFullscreenWebContent = { _, callback ->
                    activeHostCallback.set(callback)
                    true
                },
                dismissFullscreenWebContent = {
                    dismissCount.incrementAndGet()
                    activeHostCallback.getAndSet(null)?.onCustomViewHidden()
                },
            )
            val ownerTabId = controller.selectedTabId
            val ownerChromeClient =
                requireNotNull(controller.selectedWebViewForTesting().webChromeClient)
            controller.createBackgroundTab("https://example.com")
            ownerChromeClient.onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback(pageDismissCount::incrementAndGet),
            )

            controller.closeTab(ownerTabId)

            assertEquals(1, dismissCount.get())
            assertEquals(1, pageDismissCount.get())
            controller.destroy()
        }
    }

    @Test
    fun hostDismissalBlocksNextRequestUntilMatchingHideArrives() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val activeHostCallback = AtomicReference<WebChromeClient.CustomViewCallback>()
            val showCount = AtomicInteger()
            val rejectedRequestCount = AtomicInteger()
            val hideCount = AtomicInteger()
            val controller = BrowserController(
                activity = activity,
                showFullscreenWebContent = { _, callback ->
                    activeHostCallback.set(callback)
                    showCount.incrementAndGet()
                    true
                },
                hideFullscreenWebContent = hideCount::incrementAndGet,
            )
            val chromeClient = requireNotNull(controller.selectedWebViewForTesting().webChromeClient)
            chromeClient.onShowCustomView(View(activity), WebChromeClient.CustomViewCallback {})

            activeHostCallback.getAndSet(null)?.onCustomViewHidden()
            chromeClient.onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback(rejectedRequestCount::incrementAndGet),
            )

            assertEquals(1, showCount.get())
            assertEquals(1, rejectedRequestCount.get())
            assertEquals(0, hideCount.get())

            chromeClient.onHideCustomView()
            chromeClient.onShowCustomView(View(activity), WebChromeClient.CustomViewCallback {})
            assertEquals(2, showCount.get())

            chromeClient.onHideCustomView()
            assertEquals(1, hideCount.get())
            controller.destroy()
        }
    }

    @Test
    fun profileSelectionDismissesPreviousFullscreenOwner() {
        activityRule.scenario.onActivity { activity ->
            clearSession(activity)
            val activeHostCallback = AtomicReference<WebChromeClient.CustomViewCallback>()
            val dismissCount = AtomicInteger()
            val pageDismissCount = AtomicInteger()
            val controller = BrowserController(
                activity = activity,
                showFullscreenWebContent = { _, callback ->
                    activeHostCallback.set(callback)
                    true
                },
                dismissFullscreenWebContent = {
                    dismissCount.incrementAndGet()
                    activeHostCallback.getAndSet(null)?.onCustomViewHidden()
                },
            )
            requireNotNull(controller.selectedWebViewForTesting().webChromeClient).onShowCustomView(
                View(activity),
                WebChromeClient.CustomViewCallback(pageDismissCount::incrementAndGet),
            )

            controller.createProfile(emoji = "🍭")

            assertEquals(1, dismissCount.get())
            assertEquals(1, pageDismissCount.get())
            controller.destroy()
        }
    }

    private fun clearSession(activity: ComponentActivity) {
        activity.getSharedPreferences(
            BrowserSessionStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit().clear().commit()
    }
}
