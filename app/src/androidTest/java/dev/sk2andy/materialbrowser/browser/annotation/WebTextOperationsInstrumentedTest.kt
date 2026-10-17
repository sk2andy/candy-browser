package dev.sk2andy.materialbrowser.browser.annotation

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.systemwebview.WebTextActionModeCallback
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class WebTextOperationsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun nativeSelectionActionsAndContentRectRemainDelegated() {
        composeRule.runOnUiThread {
            val view = WebView(composeRule.activity)
            try {
                val menu = PopupMenu(composeRule.activity, view).menu
                val mode = TestActionMode(menu)
                var selected = 0
                var destroyed = false
                val native = object : ActionMode.Callback2() {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(0, android.R.id.selectAll, 0, "Select all")
                        menu.add(0, 7351, 0, "Ask ChatGPT")
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = true
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        selected = item.itemId
                        return true
                    }
                    override fun onDestroyActionMode(mode: ActionMode) { destroyed = true }
                    override fun onGetContentRect(mode: ActionMode, view: View, rect: Rect) { rect.set(1, 2, 30, 40) }
                }
                val callback = WebTextActionModeCallback(view, native, { 1L }) { _, _ -> error("Unexpected Candy action") }
                assertTrue(callback.onCreateActionMode(mode, menu))
                callback.onPrepareActionMode(mode, menu)
                assertEquals(4, menu.size())
                assertTrue(callback.onActionItemClicked(mode, menu.findItem(android.R.id.selectAll)))
                assertEquals(android.R.id.selectAll, selected)
                assertTrue(callback.onActionItemClicked(mode, menu.findItem(7351)))
                assertEquals(7351, selected)
                assertTrue(menu.findItem(R.id.web_annotation_mark) != null)
                assertTrue(menu.findItem(R.id.web_annotation_translate) != null)
                val rect = Rect()
                callback.onGetContentRect(mode, view, rect)
                assertEquals(Rect(1, 2, 30, 40), rect)
                callback.onDestroyActionMode(mode)
                assertTrue(destroyed)
            } finally {
                view.destroy()
            }
        }
    }

    @Test
    fun highlightsUseExactVisibleQuoteAndTranslationTogglesWithoutEditingInputs() {
        withPage { view ->
            assertEquals("true", evaluate(view, operation("highlights", "annotations" to JSONArray().put(JSONObject().put("quote", "Visible original quote")))))
            assertEquals("1", evaluate(view, "CSS.highlights.get('candy-saved').size"))
            assertEquals("false", evaluate(view, operation("translate", "original" to "Editable secret", "replacement" to "Changed")))
            assertEquals("false", evaluate(view, operation("translate", "original" to "Duplicate", "replacement" to "Changed")))
            assertEquals("false", evaluate(view, operation("translate", "original" to "Text changed", "replacement" to "Changed")))
            assertEquals("true", evaluate(view, operation("translate", "original" to "Visible original quote", "replacement" to "Sichtbares Originalzitat")))
            assertEquals("\"Sichtbares Originalzitat\"", evaluate(view, "document.querySelector('[data-candy-translation]').textContent"))
            awaitVisualState(view)
            val position = JSONArray(evaluate(view, """
                (() => {
                  const target = document.querySelector('[data-candy-translation]');
                  const rect = Array.from(target.getClientRects()).find(r => r.width > 0 && r.height > 0);
                  if (!rect) throw new Error('Translation has no visible line rectangle');
                  const x = rect.left + rect.width / 2;
                  const y = rect.top + rect.height / 2;
                  if (!target.contains(document.elementFromPoint(x, y))) {
                    throw new Error('Translation click point is obscured');
                  }
                  return [x, y, window.devicePixelRatio];
                })()
            """.trimIndent()))
            val location = IntArray(2)
            composeRule.runOnUiThread { view.getLocationOnScreen(location) }
            assertTrue(
                "Trusted translation tap was not injected",
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(
                    location[0] + (position.getDouble(0) * position.getDouble(2)).toInt(),
                    location[1] + (position.getDouble(1) * position.getDouble(2)).toInt(),
                ),
            )
            composeRule.waitUntil(5_000) {
                evaluate(view, "document.querySelector('[data-candy-translation]').textContent") == "\"Visible original quote\""
            }
            assertEquals("\"Editable secret\"", evaluate(view, "document.querySelector('textarea').value"))
        }
    }

    @Test
    fun staleUrlAndHiddenAncestorQuotesCannotModifyPage() {
        withPage { view ->
            assertEquals("false", evaluate(view, operation("translate", "original" to "Only hidden quote", "replacement" to "Changed")))
            val payload = JSONObject().put("operation", "translate").put("url", "https://other.example/")
                .put("original", "Visible original quote").put("replacement", "Changed")
            assertEquals("false", evaluate(view, "CandyWebTextOperations.apply($payload)"))
            assertEquals("0", evaluate(view, "document.querySelectorAll('[data-candy-translation]').length"))
        }
    }

    private fun operation(type: String, vararg fields: Pair<String, Any>): String {
        val payload = JSONObject().put("operation", type).put("url", PAGE_URL)
        fields.forEach { (key, value) -> payload.put(key, value) }
        return "CandyWebTextOperations.apply($payload)"
    }

    private fun withPage(block: (WebView) -> Unit) {
        lateinit var view: WebView
        val loaded = CountDownLatch(1)
        composeRule.runOnUiThread {
            view = WebView(composeRule.activity)
            view.settings.javaScriptEnabled = true
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) { loaded.countDown() }
            }
            composeRule.activity.setContentView(view)
            view.loadDataWithBaseURL(PAGE_URL, """
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="padding:80px 16px 32px"><p>Visible <b>original</b> quote</p><p>Duplicate</p><p>Duplicate</p>
                <div style="display:none"><span>Only hidden quote</span><span>Visible original quote</span></div>
                <textarea>Editable secret</textarea><div contenteditable="true">Editable secret</div></body></html>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        try {
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            val script = composeRule.activity.assets.open("web_text_operations.js").bufferedReader().use { it.readText() }
            evaluate(view, script)
            block(view)
        } finally {
            composeRule.runOnUiThread { view.destroy() }
        }
    }

    private fun awaitVisualState(view: WebView) {
        val presented = CountDownLatch(1)
        composeRule.runOnUiThread {
            view.postVisualStateCallback(1L, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    presented.countDown()
                }
            })
        }
        assertTrue("WebView did not present translated content", presented.await(5, TimeUnit.SECONDS))
    }

    private fun evaluate(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val finished = CountDownLatch(1)
        composeRule.runOnUiThread {
            view.evaluateJavascript(script) { result.set(it); finished.countDown() }
        }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private inner class TestActionMode(private val items: Menu) : ActionMode() {
        override fun setTitle(title: CharSequence?) = Unit
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(subtitle: CharSequence?) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun setCustomView(view: View?) = Unit
        override fun invalidate() = Unit
        override fun finish() = Unit
        override fun getMenu(): Menu = items
        override fun getTitle(): CharSequence = ""
        override fun getSubtitle(): CharSequence = ""
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater = MenuInflater(composeRule.activity)
    }

    private companion object { const val PAGE_URL = "https://example.org/article" }
}
