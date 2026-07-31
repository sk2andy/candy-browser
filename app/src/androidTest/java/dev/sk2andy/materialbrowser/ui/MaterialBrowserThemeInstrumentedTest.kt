package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialBrowserThemeInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun translucentSurfaceInheritsThemeAwareContentColor() {
        val expected = AtomicReference<Color>()
        val actual = AtomicReference<Color>()

        composeRule.setContent {
            MaterialBrowserTheme {
                expected.set(MaterialTheme.colorScheme.onSurface)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                ) {
                    actual.set(LocalContentColor.current)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(expected.get(), actual.get())
    }
}
