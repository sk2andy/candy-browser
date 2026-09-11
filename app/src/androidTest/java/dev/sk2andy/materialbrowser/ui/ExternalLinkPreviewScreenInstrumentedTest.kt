package dev.sk2andy.materialbrowser.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.ExternalLinkPreviewState
import dev.sk2andy.materialbrowser.browser.SystemWebViewLegacyTestAccess
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.BrowserSurfaceStyle
import dev.sk2andy.materialbrowser.ui.theme.CandyTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLinkPreviewScreenInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var controller: BrowserController? = null

    @After
    fun tearDown() {
        composeRule.runOnIdle {
            controller?.destroy()
            controller = null
            composeRule.activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }

    @Test
    fun previewHostProvidesBackdropToAddressBarAfterFirstAttachment() {
        val observedBackdrop = AtomicReference<CandyChromeBackdropSource?>()
        lateinit var previewState: ExternalLinkPreviewState
        composeRule.runOnIdle {
            val activity = composeRule.activity
            activity.getSharedPreferences(
                BrowserSessionStore.PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
            SystemWebViewLegacyTestAccess.selectEngine(activity)
            val browserController = BrowserController(activity).also { controller = it }
            assertTrue(browserController.openExternalLinkPreview("https://example.invalid/"))
            previewState = requireNotNull(browserController.externalLinkPreviewState)
        }

        composeRule.setContent {
            CandyTheme(
                settings = AppearanceSettings(surfaceStyle = BrowserSurfaceStyle.Frosted),
                chromeSurfaceRenderer = CandyChromeSurfaceRenderer {
                        backdropSource,
                        _,
                        modifier,
                        _,
                        _,
                        _,
                        _,
                        content,
                    ->
                    observedBackdrop.set(backdropSource)
                    Box(modifier = modifier) { content() }
                },
            ) {
                ExternalLinkPreviewScreen(
                    controller = requireNotNull(controller),
                    state = previewState,
                    onReturnToExternalApp = {},
                    onCommitted = {},
                    onTabOverviewPortraitLockChanged = {},
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            observedBackdrop.get() != null
        }

        assertNotNull(observedBackdrop.get())
    }
}
