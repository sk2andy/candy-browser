package dev.sk2andy.materialbrowser.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserPullToRefreshLayoutInstrumentedTest {
    @Test
    fun hostWrapsExistingContentContainerWhenRefreshIsEnabled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )

            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            assertSame(host.contentContainer, refreshLayout.contentView)
            assertSame(refreshLayout, host.contentContainer.parent)
        }
    }

    @Test
    fun updateControlsGestureRefreshStateAndScrollAdmission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )
            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout

            host.updatePullToRefresh(
                enabled = true,
                refreshing = true,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = 0,
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
                indicatorTopInsetPx = 0,
                canChildScrollUp = { true },
                onRefresh = { false },
            )

            assertFalse(refreshLayout.isEnabled)
            assertFalse(refreshLayout.isRefreshing)
            assertTrue(refreshLayout.canChildScrollUp())
        }
    }

    @Test
    fun indicatorOffsetsBelowTopSafeArea() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val host = StatusBarStaticOverlayHost(
                context = instrumentation.targetContext,
                pullToRefreshEnabled = true,
            )
            val refreshLayout = host.getChildAt(0) as BrowserPullToRefreshLayout
            val defaultStartOffsetPx = refreshLayout.progressViewStartOffset
            val defaultEndOffsetPx = refreshLayout.progressViewEndOffset
            val topSafeAreaPx = 96

            host.updatePullToRefresh(
                enabled = true,
                refreshing = false,
                indicatorColor = android.graphics.Color.RED,
                indicatorContainerColor = android.graphics.Color.WHITE,
                indicatorTopInsetPx = topSafeAreaPx,
                canChildScrollUp = { false },
                onRefresh = { true },
            )

            assertEquals(
                defaultStartOffsetPx + topSafeAreaPx,
                refreshLayout.progressViewStartOffset,
            )
            assertEquals(
                defaultStartOffsetPx + defaultEndOffsetPx + topSafeAreaPx,
                refreshLayout.progressViewEndOffset,
            )
            assertTrue(refreshLayout.progressViewEndOffset >= topSafeAreaPx)
        }
    }
}
