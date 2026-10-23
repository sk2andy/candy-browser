package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.reader.PocketSpeechLanguageState
import dev.sk2andy.materialbrowser.reader.PocketSpeechModelState
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PocketSpeechOptionsInstrumentedTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun languagesDownloadIndependentlyAndCanBeCancelledOrRemoved() {
        val downloads = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val state = mutableStateOf(PocketSpeechModelState(loaded = true))
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PocketSpeechOptions(
                        state = state.value,
                        selected = false,
                        onEnabledChanged = {},
                        onDownload = downloads::add,
                        onCancel = cancelled::add,
                        onDelete = removed::add,
                    )
                }
            }
        }
        composeRule.runOnIdle { assertEquals(emptyList<String>(), downloads) }
        composeRule.onNodeWithTag("reader_pocket_download_de").performScrollTo().performClick()
        composeRule.onNodeWithTag("reader_pocket_download_en").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("de", "en"), downloads)
            state.value = state.value.copy(languages = mapOf(
                "de" to PocketSpeechLanguageState(busy = true, progress = 0.4f),
                "en" to PocketSpeechLanguageState(ready = true),
            ))
        }
        composeRule.onNodeWithTag("reader_pocket_cancel_de").performScrollTo().performClick()
        composeRule.onNodeWithTag("reader_pocket_delete_en").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("de"), cancelled)
            assertEquals(listOf("en"), removed)
        }
    }

    @Test
    fun uncertainLanguageOffersExplicitInstalledLanguageWithoutAutoDownload() {
        val used = mutableListOf<String>()
        var downloads = 0
        composeRule.setContent {
            MaterialBrowserTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PocketSpeechOptions(
                        state = PocketSpeechModelState(loaded = true, languages = mapOf(
                            "de" to PocketSpeechLanguageState(ready = true),
                        )),
                        selected = true,
                        onEnabledChanged = {},
                        onDownload = { downloads++ },
                        onCancel = {},
                        onDelete = {},
                        onUseLanguage = used::add,
                    )
                }
            }
        }
        composeRule.onNodeWithTag("reader_pocket_article_language").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pocket_use_de").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("de"), used)
            assertEquals(0, downloads)
        }
    }
}
