package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.browser.ExternalAppFeedback
import dev.sk2andy.materialbrowser.browser.ExternalAppFeedbackStage
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserAppearanceMode
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalAppFeedbackSnackbarInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openInCandyOffersConfirmationAndYesSavesChoice() {
        val hostState = SnackbarHostState()
        var offer by mutableStateOf<ExternalAppFeedback?>(
            ExternalAppFeedback(7L, ExternalAppFeedbackStage.Opened, "github.com", false),
        )
        val opened = mutableListOf<Long>()
        val confirmed = mutableListOf<Long>()
        val dismissed = mutableListOf<Long>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppFeedbackSnackbarEffect(
                    offer = offer,
                    hostState = hostState,
                    onOpenInCandy = { id ->
                        opened += id
                        offer = offer?.copy(stage = ExternalAppFeedbackStage.ConfirmAlwaysInCandy)
                    },
                    onAlwaysOpenInCandy = { id ->
                        confirmed += id
                        offer = null
                    },
                    onDismiss = { id -> dismissed += id },
                )
                SnackbarHost(hostState, snackbar = { BrowserFeedbackSnackbar(it) })
            }
        }

        composeRule.onNodeWithText(text(R.string.external_app_feedback_opened)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.external_app_feedback_open_in_candy)).performClick()
        composeRule.onNodeWithText(text(R.string.external_app_feedback_always)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.external_app_feedback_yes)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(7L), opened)
            assertEquals(listOf(7L), confirmed)
            assertEquals(emptyList<Long>(), dismissed)
            assertNull(offer)
        }
        composeRule.onNodeWithText(text(R.string.external_app_feedback_always)).assertDoesNotExist()
    }

    @Test
    fun cancelDismissesConfirmationWithoutSavingChoice() {
        val hostState = SnackbarHostState()
        var offer by mutableStateOf<ExternalAppFeedback?>(
            ExternalAppFeedback(11L, ExternalAppFeedbackStage.ConfirmAlwaysInCandy, "github.com", false),
        )
        val confirmed = mutableListOf<Long>()
        val dismissed = mutableListOf<Long>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppFeedbackSnackbarEffect(
                    offer = offer,
                    hostState = hostState,
                    onOpenInCandy = {},
                    onAlwaysOpenInCandy = { id -> confirmed += id },
                    onDismiss = { id ->
                        dismissed += id
                        offer = null
                    },
                )
                SnackbarHost(hostState, snackbar = { BrowserFeedbackSnackbar(it) })
            }
        }

        composeRule.onNodeWithText(text(R.string.action_cancel)).performClick()

        composeRule.runOnIdle {
            assertEquals(emptyList<Long>(), confirmed)
            assertEquals(listOf(11L), dismissed)
        }
        composeRule.onNodeWithText(text(R.string.external_app_feedback_always)).assertDoesNotExist()
    }

    @Test
    fun privateConfirmationExplainsSessionScope() {
        val hostState = SnackbarHostState()
        val confirmed = mutableListOf<Long>()
        composeRule.setContent {
            MaterialBrowserTheme {
                ExternalAppFeedbackSnackbarEffect(
                    offer = ExternalAppFeedback(
                        13L,
                        ExternalAppFeedbackStage.ConfirmAlwaysInCandy,
                        "github.com",
                        true,
                    ),
                    hostState = hostState,
                    onOpenInCandy = {},
                    onAlwaysOpenInCandy = { id -> confirmed += id },
                    onDismiss = {},
                )
                SnackbarHost(hostState, snackbar = { BrowserFeedbackSnackbar(it) })
            }
        }

        composeRule.onNodeWithText(text(R.string.external_app_feedback_always_private))
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.external_app_feedback_yes)).performClick()
        composeRule.runOnIdle { assertEquals(listOf(13L), confirmed) }
    }

    @Test
    fun backgroundAndCompositionRemovalPreserveOfferUntilCandyReturns() {
        val hostState = SnackbarHostState()
        lateinit var lifecycleOwner: FeedbackLifecycleOwner
        composeRule.runOnIdle {
            lifecycleOwner = FeedbackLifecycleOwner()
            lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        }
        var showEffect by mutableStateOf(true)
        val offer = ExternalAppFeedback(17L, ExternalAppFeedbackStage.Opened, "github.com", false)
        val dismissed = mutableListOf<Long>()
        composeRule.setContent {
            MaterialBrowserTheme {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                    if (showEffect) {
                        ExternalAppFeedbackSnackbarEffect(
                            offer = offer,
                            hostState = hostState,
                            onOpenInCandy = {},
                            onAlwaysOpenInCandy = {},
                            onDismiss = { id -> dismissed += id },
                        )
                    }
                    SnackbarHost(hostState, snackbar = { BrowserFeedbackSnackbar(it) })
                }
            }
        }
        val message = text(R.string.external_app_feedback_opened)
        composeRule.onNodeWithText(message).assertIsDisplayed()

        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.CREATED }
        composeRule.waitUntil { hostState.currentSnackbarData == null }
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), dismissed) }

        composeRule.runOnIdle { lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.runOnIdle { showEffect = false }
        composeRule.waitUntil { hostState.currentSnackbarData == null }
        composeRule.runOnIdle { assertEquals(emptyList<Long>(), dismissed) }

        composeRule.runOnIdle { showEffect = true }
        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    @Test
    fun confirmationActionsStayVisibleInLightDarkAndLargeText() {
        val hostState = SnackbarHostState()
        var appearanceMode by mutableStateOf(BrowserAppearanceMode.Light)
        var fontScale by mutableStateOf(1f)
        val offer = ExternalAppFeedback(
            19L,
            ExternalAppFeedbackStage.ConfirmAlwaysInCandy,
            "github.com",
            false,
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialBrowserTheme(settings = AppearanceSettings(appearanceMode = appearanceMode)) {
                    Box(Modifier.width(320.dp)) {
                        ExternalAppFeedbackSnackbarEffect(
                            offer = offer,
                            hostState = hostState,
                            onOpenInCandy = {},
                            onAlwaysOpenInCandy = {},
                            onDismiss = {},
                        )
                        SnackbarHost(hostState, snackbar = { BrowserFeedbackSnackbar(it) })
                    }
                }
            }
        }
        val directory = File(
            requireNotNull(composeRule.activity.getExternalFilesDir(null)),
            "external-app-snackbar",
        )
        assertTrue(directory.exists() || directory.mkdirs())
        for (mode in listOf(BrowserAppearanceMode.Light, BrowserAppearanceMode.Dark)) {
            for (scale in listOf(1f, 2f)) {
                composeRule.runOnIdle {
                    appearanceMode = mode
                    fontScale = scale
                }
                composeRule.onNodeWithText(text(R.string.external_app_feedback_always)).assertIsDisplayed()
                composeRule.onNodeWithText(text(R.string.external_app_feedback_yes)).assertIsDisplayed()
                composeRule.onNodeWithText(text(R.string.action_cancel)).assertIsDisplayed()
                composeRule.mainClock.advanceTimeByFrame()
                val screenshot = composeRule.onRoot().captureToImage().asAndroidBitmap()
                File(directory, "${mode.name.lowercase()}_${(scale * 100).toInt()}.png")
                    .outputStream().use { stream ->
                        assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    }
            }
        }
    }

    private fun text(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private class FeedbackLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
    }
}
