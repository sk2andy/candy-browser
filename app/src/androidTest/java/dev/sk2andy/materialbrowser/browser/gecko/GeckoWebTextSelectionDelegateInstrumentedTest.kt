package dev.sk2andy.materialbrowser.browser.gecko

import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.annotation.WebTextSelectionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.gecko.EventDispatcher
import org.mozilla.gecko.util.GeckoBundle
import org.mozilla.geckoview.BasicSelectionActionDelegate
import org.mozilla.geckoview.GeckoSession

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoWebTextSelectionDelegateInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun searchAndShareUseSelectedTextWithoutEnablingPrivateHighlights() {
        composeRule.runOnUiThread {
            val actions = mutableListOf<Pair<WebTextSelectionAction, String>>()
            val delegate = GeckoWebTextSelectionDelegate(composeRule.activity, { true }, { false }) { action, text ->
                actions += action to text
            }
            delegate.enableExternalActions(false)
            setSelection(delegate, "example.org/path")
            val menu = PopupMenu(composeRule.activity, View(composeRule.activity)).menu
            val mode = TestActionMode(menu)
            delegate.onPrepareActionMode(mode, menu)
            val search = (0 until menu.size()).map(menu::getItem).single {
                it.title == composeRule.activity.getString(R.string.settings_section_search)
            }
            val share = (0 until menu.size()).map(menu::getItem).single {
                it.title == composeRule.activity.getString(R.string.action_share)
            }
            assertTrue((0 until menu.size()).none {
                menu.getItem(it).title == composeRule.activity.getString(R.string.web_annotation_mark)
            })
            assertTrue(delegate.onActionItemClicked(mode, search))
            assertTrue(delegate.onActionItemClicked(mode, share))
            assertEquals(listOf(
                WebTextSelectionAction.Search to "example.org/path",
                WebTextSelectionAction.Share to "example.org/path",
            ), actions)
        }
    }

    @Test
    fun protectedEmptyOversizedAndDisabledSelectionsHaveNoCandyActions() {
        composeRule.runOnUiThread {
            var enabled = true
            val delegate = GeckoWebTextSelectionDelegate(composeRule.activity, { enabled }, { false }) { _, _ ->
                error("Protected selection must not trigger Candy actions")
            }
            delegate.enableExternalActions(false)
            for ((text, flag) in listOf(
                "secret" to "editable", "secret" to "password", "secret" to "collapsed",
                "   " to "", "a".repeat(8_001) to "",
            )) {
                setSelection(delegate, text, flag)
                val menu = PopupMenu(composeRule.activity, View(composeRule.activity)).menu
                delegate.onPrepareActionMode(TestActionMode(menu), menu)
                val titles = (0 until menu.size()).map { menu.getItem(it).title }
                assertTrue(composeRule.activity.getString(R.string.settings_section_search) !in titles)
                assertTrue(composeRule.activity.getString(R.string.action_share) !in titles)
            }
            enabled = false
            setSelection(delegate, "example.org")
            val menu = PopupMenu(composeRule.activity, View(composeRule.activity)).menu
            delegate.onPrepareActionMode(TestActionMode(menu), menu)
            assertEquals(0, menu.size())
        }
    }

    private fun setSelection(delegate: GeckoWebTextSelectionDelegate, text: String, flag: String = "") {
        val bundle = GeckoBundle().apply {
            putString("selection", text)
            if (flag.isNotEmpty()) putBoolean(flag, true)
        }
        // Gecko constructs selections internally; keep the fixture at its native callback boundary.
        val constructor = GeckoSession.SelectionActionDelegate.Selection::class.java.getDeclaredConstructor(
            GeckoBundle::class.java, Set::class.java, EventDispatcher::class.java,
        ).apply { isAccessible = true }
        val selection = constructor.newInstance(bundle, emptySet<String>(), null)
        BasicSelectionActionDelegate::class.java.getDeclaredField("mSelection").apply {
            isAccessible = true
            set(delegate, selection)
        }
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
}
