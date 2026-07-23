package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.MainActivity
import dev.sk2andy.materialbrowser.browser.BrowserTab
import dev.sk2andy.materialbrowser.browser.CandyTrail
import dev.sk2andy.materialbrowser.browser.CandyTrailNode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandyTrailScreenInstrumentedTest {
    @Test
    fun graphRendersCurrentNodeAndFallbackFavicon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tab = BrowserTab(
            id = "tab",
            lastAccessedAt = 1L,
            title = "Current tab",
            url = "https://b.example",
        )
        val trail = CandyTrail(
            tabId = tab.id,
            nodes = List(64) { index ->
                node(
                    id = "n$index",
                    parentId = if (index == 0) null else "n${index - 1}",
                    title = "Page $index",
                    url = "https://page$index.example",
                    at = index.toLong(),
                )
            },
            currentNodeId = "n63",
            nextOrdinal = 64L,
        )

        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as MainActivity
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    MaterialBrowserTheme {
                        CandyTrailScreen(
                            tab = tab,
                            trail = trail,
                            favicon = null,
                            sourceBounds = null,
                            predictiveBackProgress = 0f,
                            predictiveBackEdgeSign = 1,
                            onOpenTabActions = {},
                            onSelectNode = {},
                            onDismiss = {},
                        )
                    }
                }
            }
            instrumentation.waitForIdleSync()
            assertTrue("Current node is outside the initial viewport", currentNodeIsVisible())
            instrumentation.runOnMainSync {
                val root = activity.window.decorView
                assertTrue(root.width > 0 && root.height > 0)
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                assertTrue(bitmap.hasVisualVariation())
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
            instrumentation.waitForIdleSync()
        }
    }

    private fun currentNodeIsVisible(): Boolean {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        repeat(40) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            val pending = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
            if (root != null) pending += root
            val matchingNodes = buildList {
                while (pending.isNotEmpty()) {
                    val node = pending.removeFirst()
                    if (
                        node.text?.contains("Page 63") == true ||
                        node.contentDescription?.contains("Page 63") == true
                    ) {
                        add(node)
                    }
                    repeat(node.childCount) { childIndex ->
                        node.getChild(childIndex)?.let(pending::addLast)
                    }
                }
            }
            val visible = matchingNodes.any { node ->
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                bounds.width() > 0 && bounds.height() > 0 &&
                    bounds.left < instrumentation.targetContext.resources.displayMetrics.widthPixels &&
                    bounds.top < instrumentation.targetContext.resources.displayMetrics.heightPixels &&
                    bounds.right > 0 && bounds.bottom > 0
            }
            if (visible) return true
            SystemClock.sleep(50)
        }
        return false
    }

    private fun Bitmap.hasVisualVariation(): Boolean {
        val colors = mutableSetOf<Int>()
        val xStep = (width / 12).coerceAtLeast(1)
        val yStep = (height / 20).coerceAtLeast(1)
        for (x in 0 until width step xStep) {
            for (y in 0 until height step yStep) colors += getPixel(x, y)
        }
        return colors.size >= 4
    }

    private fun node(id: String, parentId: String?, title: String, url: String, at: Long) =
        CandyTrailNode(id, parentId, url, title, at)
}
