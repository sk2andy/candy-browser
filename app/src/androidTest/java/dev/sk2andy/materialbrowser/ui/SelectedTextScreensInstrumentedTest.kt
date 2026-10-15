package dev.sk2andy.materialbrowser.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.reader.ReaderSpeech
import dev.sk2andy.materialbrowser.reader.ReaderSpeechState
import dev.sk2andy.materialbrowser.reader.ReaderSpeechStatus
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectedTextScreensInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun researchWaitsForExplicitProfileChoice() {
        val selected = AtomicReference<String?>()
        composeRule.setContent {
            MaterialTheme {
                SelectedTextResearchScreen(
                    text = "selected phrase",
                    profiles = listOf(BrowserProfile("personal", "🍬"), BrowserProfile("work", "💼")),
                    onResearch = selected::set,
                    onDismiss = {},
                )
            }
        }

        assertEquals(null, selected.get())
        composeRule.onNodeWithTag(SelectedTextTestTags.profile("work")).performClick()
        assertEquals("work", selected.get())
    }

    @Test
    fun speechUsesExistingControllerContractAndStopsWithoutRestarting() {
        val speech = FakeSpeech()
        composeRule.setContent {
            MaterialTheme { SelectedTextSpeechScreen("selected phrase", speech, onDismiss = {}) }
        }
        composeRule.waitForIdle()
        assertEquals(listOf("selected phrase"), speech.played)
        composeRule.onNodeWithTag(SelectedTextTestTags.PlayPause).performClick()
        assertEquals(ReaderSpeechStatus.Paused, speech.state.status)
        composeRule.onNodeWithTag(SelectedTextTestTags.PlayPause).performClick()
        composeRule.onNodeWithTag(SelectedTextTestTags.Stop).performClick()
        composeRule.waitForIdle()

        assertEquals(ReaderSpeechStatus.Ready, speech.state.status)
        assertEquals(2, speech.played.size)
    }

    @Test
    fun backgroundSpeechCannotAutoStartWhileEngineBecomesReady() {
        val speech = FakeSpeech()
        val allowed = mutableStateOf(false)
        composeRule.setContent {
            MaterialTheme {
                SelectedTextSpeechScreen("selected phrase", speech, onDismiss = {}, playbackAllowed = allowed.value)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SelectedTextTestTags.PlayPause).assertIsNotEnabled()
        assertEquals(emptyList<String>(), speech.played)
        composeRule.runOnIdle { allowed.value = true }
        composeRule.waitForIdle()
        assertEquals(listOf("selected phrase"), speech.played)
    }

    private class FakeSpeech : ReaderSpeech {
        override var state by mutableStateOf(ReaderSpeechState(ReaderSpeechStatus.Ready))
            private set
        val played = mutableListOf<String>()
        override fun play(content: String) {
            played += content
            state = ReaderSpeechState(ReaderSpeechStatus.Speaking)
        }
        override fun pause() { state = state.copy(status = ReaderSpeechStatus.Paused) }
        override fun stop() { state = ReaderSpeechState(ReaderSpeechStatus.Ready) }
        override fun close() { state = ReaderSpeechState(ReaderSpeechStatus.Closed) }
    }
}
