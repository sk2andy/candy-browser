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
    override fun getAllActions(): Array<String> = super.getAllActions() + arrayOf(HIGHLIGHT, TRANSLATE, READ_ALOUD)

    override fun isActionAvailable(action: String): Boolean {
        val kind = actionKind(action) ?: return super.isActionAvailable(action)
        if (!actionsEnabled() || (action == HIGHLIGHT && !canHighlight())) return false
        val selected = selection ?: return false
        val excluded = GeckoSession.SelectionActionDelegate.FLAG_IS_EDITABLE or
            GeckoSession.SelectionActionDelegate.FLAG_IS_PASSWORD or
            GeckoSession.SelectionActionDelegate.FLAG_IS_COLLAPSED
        return selected.flags and excluded == 0 && selected.text.isNotBlank() &&
            selected.text.length <= kind.maximumCharacters
    }

    override fun prepareAction(action: String, item: MenuItem) {
        when (action) {
            HIGHLIGHT -> item.setTitle(R.string.web_annotation_mark)
            TRANSLATE -> item.setTitle(R.string.web_annotation_translate)
            READ_ALOUD -> item.setTitle(R.string.process_text_read_aloud)
            else -> super.prepareAction(action, item)
        }
    }

    override fun performAction(action: String, item: MenuItem): Boolean {
        val kind = actionKind(action) ?: return super.performAction(action, item)
        if (!isActionAvailable(action)) return false
        val text = selection?.text ?: return false
        mActionMode?.finish()
        onAction(kind, text)
        return true
    }

    private fun actionKind(action: String): WebTextSelectionAction? = when (action) {
        HIGHLIGHT -> WebTextSelectionAction.Highlight
        TRANSLATE -> WebTextSelectionAction.Translate
        READ_ALOUD -> WebTextSelectionAction.ReadAloud
        else -> null
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
        const val READ_ALOUD = "candy.read_aloud"
    }
}
