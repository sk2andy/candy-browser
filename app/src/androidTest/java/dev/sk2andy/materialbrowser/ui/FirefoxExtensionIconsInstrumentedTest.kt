package dev.sk2andy.materialbrowser.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtension
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionManagerState
import dev.sk2andy.materialbrowser.browser.gecko.GeckoExtensionSnapshot
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirefoxExtensionIconsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun installedIconsFollowExtensionsAfterInsertion() {
        val cookie = extension("cookie", android.graphics.Color.RED)
        val ublock = extension("ublock", android.graphics.Color.BLUE)
        val darkReader = extension("dark-reader", android.graphics.Color.GREEN)
        val extensions = mutableStateOf(listOf(cookie, ublock))

        composeRule.setContent {
            MaterialBrowserTheme {
                FirefoxExtensionManagerOverlay(
                    state = GeckoExtensionManagerState(snapshot = GeckoExtensionSnapshot(extensions.value)),
                    onInstall = {},
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

        assertIconColor("cookie", android.graphics.Color.RED)
        composeRule.runOnIdle { extensions.value = listOf(darkReader, cookie, ublock) }
        assertIconColor("dark-reader", android.graphics.Color.GREEN)
        assertIconColor("cookie", android.graphics.Color.RED)
        assertIconColor("ublock", android.graphics.Color.BLUE)
    }

    private fun assertIconColor(id: String, expected: Int) {
        val row = composeRule.onNodeWithTag(FirefoxExtensionManagerTestTags.extension(id))
        row.performScrollTo()
        val pixels = row.captureToImage().toPixelMap()
        val x = with(composeRule.density) { 36.dp.roundToPx() }
        val y = with(composeRule.density) { 30.dp.roundToPx() }
        assertEquals("Icon for $id", expected, pixels[x, y].toArgb())
    }

    private fun extension(id: String, color: Int): GeckoExtension = GeckoExtension(
        id = id,
        name = id,
        version = "1",
        enabled = true,
        allowedInPrivateBrowsing = false,
        isBuiltIn = false,
        icon = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(color) },
    )
}
