package dev.sk2andy.materialbrowser.browser.systemwebview

import android.graphics.Rect
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.annotation.WebTextSelectionAction
import org.json.JSONTokener

/** Decorates the provider callback; Select all, copy, share and installed apps remain provider-owned. */
internal class WebTextActionModeCallback(
    private val webView: WebView,
    private val delegate: ActionMode.Callback,
    private val documentGeneration: () -> Long,
    private val canHighlight: () -> Boolean = { true },
    private val onAction: (WebTextSelectionAction, String) -> Unit,
) : ActionMode.Callback2() {
    private val generation = documentGeneration()

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean =
        delegate.onCreateActionMode(mode, menu).also { if (it) append(menu) }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
        delegate.onPrepareActionMode(mode, menu).also { append(menu) }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        val kind = when (item.itemId) {
            R.id.web_annotation_mark -> WebTextSelectionAction.Highlight
            R.id.web_annotation_translate -> WebTextSelectionAction.Translate
            else -> return delegate.onActionItemClicked(mode, item)
        }
        if (documentGeneration() != generation) return true
        val url = webView.url
        webView.evaluateJavascript(SELECTION_SCRIPT) { result ->
            val text = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
            if (documentGeneration() == generation && url == webView.url && !text.isNullOrBlank() && text.length <= 8_000) onAction(kind, text)
            mode.finish()
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = delegate.onDestroyActionMode(mode)

    override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
        if (delegate is ActionMode.Callback2) delegate.onGetContentRect(mode, view, outRect)
        else super.onGetContentRect(mode, view, outRect)
    }

    private fun append(menu: Menu) {
        if (menu.findItem(android.R.id.cut) != null || menu.findItem(android.R.id.paste) != null) {
            menu.removeItem(R.id.web_annotation_mark)
            menu.removeItem(R.id.web_annotation_translate)
            return
        }
        if (canHighlight() && menu.findItem(R.id.web_annotation_mark) == null) {
            menu.add(Menu.NONE, R.id.web_annotation_mark, Menu.NONE, R.string.web_annotation_mark)
        }
        if (menu.findItem(R.id.web_annotation_translate) == null) {
            menu.add(Menu.NONE, R.id.web_annotation_translate, Menu.NONE, R.string.web_annotation_translate)
        }
    }

    private companion object {
        val SELECTION_SCRIPT = """
            (() => {
              const s = window.getSelection();
              if (!s || s.isCollapsed || !s.rangeCount) return '';
              const r = s.getRangeAt(0);
              const e = n => n.nodeType === 1 ? n : n.parentElement;
              const unsafe = n => e(n)?.closest('input,textarea,select,[contenteditable]:not([contenteditable="false"])');
              if (unsafe(r.startContainer) || unsafe(r.endContainer) || unsafe(document.activeElement)) return '';
              return s.toString().slice(0, 8001);
            })()
        """.trimIndent()
    }
}
