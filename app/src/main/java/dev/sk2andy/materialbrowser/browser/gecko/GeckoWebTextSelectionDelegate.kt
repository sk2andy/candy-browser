package dev.sk2andy.materialbrowser.browser.gecko

import android.app.Activity
import android.view.MenuItem
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.annotation.WebTextSelectionAction
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoSession

/** Extends Gecko's native menu, including external PROCESS_TEXT apps, without rebuilding it. */
internal class GeckoWebTextSelectionDelegate(
    activity: Activity,
    private val actionsEnabled: () -> Boolean,
    private val canHighlight: () -> Boolean,
    private val onAction: (WebTextSelectionAction, String) -> Unit,
) : BasicSelectionActionDelegate(activity), AutoCloseable {
    override fun getAllActions(): Array<String> = super.getAllActions() + arrayOf(HIGHLIGHT, TRANSLATE)

    override fun isActionAvailable(action: String): Boolean {
        if (action != HIGHLIGHT && action != TRANSLATE) return super.isActionAvailable(action)
        if (!actionsEnabled() || (action == HIGHLIGHT && !canHighlight())) return false
        val selected = selection ?: return false
        val excluded = GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE or
            GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD or
            GeckoSession.SelectionActionDelegate.FLAG_IS_COLLAPSED
        return selected.flags and excluded == 0 && selected.text.isNotBlank() && selected.text.length <= 8_000
    }

    override fun prepareAction(action: String, item: MenuItem) {
        when (action) {
            HIGHLIGHT -> item.setTitle(R.string.web_annotation_mark)
            TRANSLATE -> item.setTitle(R.string.web_annotation_translate)
            else -> super.prepareAction(action, item)
        }
    }

    override fun performAction(action: String, item: MenuItem): Boolean {
        val kind = when (action) {
            HIGHLIGHT -> WebTextSelectionAction.Highlight
            TRANSLATE -> WebTextSelectionAction.Translate
            else -> return super.performAction(action, item)
        }
        if (!isActionAvailable(action)) return false
        val text = selection?.text ?: return false
        mActionMode?.finish()
        onAction(kind, text)
        return true
    }

    override fun close() {
        clearSelection()
        mActionMode?.finish()
        mActionMode = null
        mSelection = null
        mSession = null
    }

    private companion object {
        const val HIGHLIGHT = "candy.highlight"
        const val TRANSLATE = "candy.translate"
    }
}
