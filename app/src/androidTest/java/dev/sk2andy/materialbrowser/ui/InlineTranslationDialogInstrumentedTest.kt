package dev.sk2andy.materialbrowser.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationAvailability
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationBackend
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationCoordinator
import dev.sk2andy.materialbrowser.browser.translation.InlineTranslationPair
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.Locale
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineTranslationDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var coordinator: InlineTranslationCoordinator? = null

    @After
    fun closeCoordinator() {
        composeRule.runOnIdle { coordinator?.close() }
    }

    @Test
    fun unavailableServiceShowsChosenProviderAndWaitsForExplicitFallback() {
        val backend = FakeBackend(emptyList())
        var fallbackCount = 0
        showDialog(backend, fallback = { fallbackCount++ })

        composeRule.onNodeWithText(context.getString(R.string.inline_translation_unavailable)).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.inline_translation_fallback, "Yandex"),
        ).assertExists()
        composeRule.onNodeWithTag("inline_translation_apply").assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(0, fallbackCount)
            assertTrue(backend.translations.isEmpty())
        }

        composeRule.onNodeWithTag("inline_translation_fallback").performScrollTo().performClick()

        composeRule.runOnIdle {
            assertEquals(1, fallbackCount)
            assertNull(coordinator?.state)
        }
        composeRule.onNodeWithText(context.getString(R.string.inline_translation_title)).assertDoesNotExist()
    }

    @Test
    fun selectingLanguagePairAppliesTranslationOnlyAfterTranslateClick() {
        val pair = InlineTranslationPair("en", "de")
        val backend = FakeBackend(listOf(pair))
        var applied: JSONObject? = null
        showDialog(backend, apply = { payload, complete ->
            applied = JSONObject(payload)
            complete(true)
        })

        composeRule.onNodeWithTag("inline_translation_apply").assertIsNotEnabled()
        chooseLanguage(R.string.inline_translation_source, "en")
        chooseLanguage(R.string.inline_translation_target, "de")
        composeRule.onNodeWithTag("inline_translation_apply").assertIsEnabled()
        composeRule.runOnIdle {
            assertTrue(backend.translations.isEmpty())
            assertNull(applied)
        }

        composeRule.onNodeWithTag("inline_translation_apply").performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("Hello world" to pair), backend.translations)
            assertEquals("Hello world", applied?.getString("original"))
            assertEquals("Hallo Welt", applied?.getString("replacement"))
            assertEquals("https://example.org/article", applied?.getString("url"))
            assertNull(coordinator?.state)
        }
    }

    @Test
    fun cancelClearsSelectionWithoutTranslationOrFallback() {
        val backend = FakeBackend(listOf(InlineTranslationPair("en", "de")))
        var fallbackCount = 0
        showDialog(backend, fallback = { fallbackCount++ })

        composeRule.onNodeWithText(context.getString(android.R.string.cancel)).performClick()

        composeRule.runOnIdle {
            assertNull(coordinator?.state)
            assertTrue(backend.translations.isEmpty())
            assertEquals(0, fallbackCount)
        }
        composeRule.onNodeWithText("Hello world").assertDoesNotExist()
    }

    private fun showDialog(
        backend: FakeBackend,
        apply: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
        fallback: () -> Unit = {},
    ) {
        val next = InlineTranslationCoordinator(backend)
        coordinator = next
        composeRule.setContent {
            MaterialBrowserTheme {
                InlineTranslationDialog(coordinator = next, providerName = "Yandex")
            }
        }
        composeRule.runOnIdle {
            next.open(
                text = "Hello world",
                url = "https://example.org/article",
                toggleLabel = "Show original",
                isCurrent = { true },
                apply = apply,
                fallback = fallback,
            )
        }
    }

    private fun chooseLanguage(label: Int, language: String) {
        composeRule.onNodeWithText("${context.getString(label)}:", substring = true)
            .performScrollTo().performClick()
        val locale = context.resources.configuration.locales[0]
        composeRule.onNodeWithText(Locale.forLanguageTag(language).getDisplayName(locale)).performClick()
    }

    private class FakeBackend(private val pairs: List<InlineTranslationPair>) : InlineTranslationBackend {
        val translations = mutableListOf<Pair<String, InlineTranslationPair>>()

        override fun availability(onComplete: (InlineTranslationAvailability) -> Unit) {
            onComplete(InlineTranslationAvailability(pairs, null))
        }

        override fun translate(text: String, pair: InlineTranslationPair, onComplete: (String?) -> Unit) {
            translations += text to pair
            onComplete("Hallo Welt")
        }

        override fun cancel() = Unit

        override fun close() = Unit
    }
}
