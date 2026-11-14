package dev.sk2andy.materialbrowser.ui

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirefoxExtensionInstallUrlInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installUrlRequestsUriKeyboardWithoutCorrectionOrCapitalization() {
        render()
        openInstallDialog()
        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.InstallUrl).performClick()

        composeRule.runOnIdle {
            val editor = WindowInspector.getGlobalWindowViews()
                .asSequence().flatMap(::descendants)
                .firstOrNull { it.onCheckIsTextEditor() }
            assertNotNull("The focused install URL must expose an input connection", editor)
            val info = EditorInfo()
            assertNotNull(editor!!.onCreateInputConnection(info))
            assertEquals(InputType.TYPE_CLASS_TEXT, info.inputType and InputType.TYPE_MASK_CLASS)
            assertEquals(InputType.TYPE_TEXT_VARIATION_URI, info.inputType and InputType.TYPE_MASK_VARIATION)
            val unwantedFlags = InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_CAP_WORDS or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            assertEquals(0, info.inputType and unwantedFlags)
            assertEquals(EditorInfo.IME_ACTION_GO, info.imeOptions and EditorInfo.IME_MASK_ACTION)
        }
    }

    @Test
    fun cancellationDiscardsUrlAndRetrySubmitsExactPackageAddress() {
        var submittedUrl: String? = null
        render { submittedUrl = it }
        openInstallDialog()
        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.InstallUrl)
            .performTextInput("https://example.org/cancelled.xpi")
        composeRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        composeRule.runOnIdle { assertEquals(null, submittedUrl) }

        openInstallDialog()
        val url = "https://addons.mozilla.org/firefox/downloads/latest/savescreenshot/latest.xpi?source=Test%2FCase"
        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.InstallUrl)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            .performTextInput(url)
        composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.InstallUrl).assertTextEquals(url)
        composeRule.onNodeWithText(context.getString(R.string.gecko_extension_install_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(url, submittedUrl) }
    }

    private fun openInstallDialog() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.gecko_extension_install))
            .performClick()
    }

    private fun render(onInstall: (String) -> Unit = {}) {
        composeRule.setContent {
            MaterialBrowserTheme {
                FirefoxExtensionManagerOverlay(
                    state = GeckoExtensionManagerState(),
                    onInstall = onInstall,
                    onSetEnabled = { _, _ -> },
                    onSetPrivate = { _, _ -> },
                    onUpdate = {},
                    onUninstall = {},
                    onOpenOptionsPage = {},
                    onPermissionDecision = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
        }
    }
}
