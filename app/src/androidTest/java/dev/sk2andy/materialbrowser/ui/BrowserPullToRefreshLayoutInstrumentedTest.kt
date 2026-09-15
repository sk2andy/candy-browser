package dev.sk2andy.materialbrowser.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPullToRefreshLayoutInstrumentedTest {
    @Test
    fun hostWrapsExistingContentContainerWhenRefreshIsEnabled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = StatusBarStaticOverlayHost(
            context = context,
            pullToRefreshEnabled = true,
        )

        val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
        assertSame(host.contentContainer, refreshLayout.contentView)
        assertSame(refreshLayout, host.contentContainer.parent)
    }

    @Test
    fun updateControlsGestureRefreshStateAndScrollAdmission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val host = StatusBarStaticOverlayHost(
            context = context,
            pullToRefreshEnabled = true,
        )
        val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout

        host.updatePullToRefresh(
            enabled = true,
            refreshing = true,
            indicatorColor = android.graphics.Color.RED,
            indicatorContainerColor = android.graphics.Color.WHITE,
            canChildScrollUp = { false },
            onRefresh = { true },
        )

        assertTrue(refreshLayout.isEnabled)
        assertTrue(refreshLayout.isRefreshing)
        assertFalse(refreshLayout.canChildScrollUp())

        host.updatePullToRefresh(
            enabled = false,
            refreshing = true,
            indicatorColor = android.graphics.Color.RED,
            indicatorContainerColor = android.graphics.Color.WHITE,
            canChildScrollUp = { true },
            onRefresh = { false },
        )

        assertFalse(refreshLayout.isEnabled)
        assertFalse(refreshLayout.isRefreshing)
        assertTrue(refreshLayout.canChildScrollUp())
    }
}
