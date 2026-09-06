package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import eightbitlab.com.blurview.BlurTarget
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserMenuComponentsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickActionUsesConfiguredFrostedBlurAndRemainsClickable() {
        var settings by mutableStateOf(
            AppearanceSettings(
                surfaceStyle = BrowserSurfaceStyle.Frosted,
                frostedBlurPercent = 100,
            ),
        )
        val clicks = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme(settings = settings) {
                var blurTarget by remember { mutableStateOf<BlurTarget?>(null) }
                Box(Modifier.fillMaxSize()) {
                    BrowserContentBlurTarget(
                        enabled = true,
                        onTargetAttached = { blurTarget = it },
                        onTargetReleased = { if (blurTarget === it) blurTarget = null },
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                    MenuToolbarAction(
                        label = "Reload",
                        iconRes = R.drawable.ic_symbol_refresh,
                        blurTarget = blurTarget,
                        onClick = clicks::incrementAndGet,
                        modifier = Modifier.testTag(QUICK_ACTION_TAG),
                    )
                }
            }
        }

        composeRule.onAllNodesWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertCountEquals(1)
        composeRule.onNodeWithTag(QUICK_ACTION_TAG).performClick()
        assertEquals(1, clicks.get())

        composeRule.runOnIdle {
            settings = settings.copy(frostedBlurPercent = 0)
        }
        composeRule.onAllNodesWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertCountEquals(0)
    }

    private companion object {
        const val QUICK_ACTION_TAG = "browser_menu_quick_action"
    }
}
