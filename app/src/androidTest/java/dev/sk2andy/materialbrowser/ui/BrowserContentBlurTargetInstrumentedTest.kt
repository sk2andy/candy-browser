package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import dev.sk2andy.materialbrowser.ui.theme.browserChromeSurfaceTokens
import eightbitlab.com.blurview.BlurTarget
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserContentBlurTargetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composeContentProvidesBackdropAndReleasesItWhenDisabled() {
        var enabled by mutableStateOf(true)
        val attached = AtomicReference<BlurTarget?>()
        val released = AtomicReference<BlurTarget?>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                var target by remember { mutableStateOf<BlurTarget?>(null) }
                Box(Modifier.fillMaxSize()) {
                    BrowserContentBlurTarget(
                        enabled = enabled,
                        onTargetAttached = {
                            attached.set(it)
                            target = it
                        },
                        onTargetReleased = {
                            released.set(it)
                            if (target === it) target = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag(SOURCE_TAG),
                        )
                    }
                    target?.let { blurTarget ->
                        BrowserChromeSurface(
                            blurTarget = blurTarget,
                            tokens = browserChromeSurfaceTokens(),
                            modifier = Modifier.size(160.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text("Frosted")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag(SOURCE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertIsDisplayed()
        val activeTarget = attached.get()

        composeRule.runOnIdle { enabled = false }
        composeRule.onNodeWithTag(SOURCE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BrowserChromeSurfaceTestTags.BackdropBlur)
            .assertDoesNotExist()
        assertSame(activeTarget, released.get())
    }

    @Test
    fun outgoingBlankSourceCannotReleaseNewWebSourceDuringHandoff() {
        var blankVisible by mutableStateOf(true)
        val currentTarget = AtomicReference<BlurTarget?>()
        val webTarget = AtomicReference<BlurTarget?>()

        composeRule.setContent {
            MaterialBrowserTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
            ) {
                Box(Modifier.fillMaxSize()) {
                    BrowserContentBlurTarget(
                        enabled = !blankVisible,
                        onTargetAttached = { target ->
                            webTarget.set(target)
                            currentTarget.set(target)
                        },
                        onTargetReleased = { target ->
                            currentTarget.compareAndSet(target, null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                    BrowserContentBlurTarget(
                        enabled = blankVisible,
                        onTargetAttached = currentTarget::set,
                        onTargetReleased = { target ->
                            currentTarget.compareAndSet(target, null)
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }

        composeRule.runOnIdle { blankVisible = false }
        composeRule.waitForIdle()

        assertSame(webTarget.get(), currentTarget.get())
    }

    private companion object {
        const val SOURCE_TAG = "browser_content_blur_source"
    }
}
