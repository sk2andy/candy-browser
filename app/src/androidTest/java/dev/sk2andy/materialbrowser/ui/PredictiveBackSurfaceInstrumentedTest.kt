package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PredictiveBackSurfaceInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun progressRevealsDestinationAroundScaledSurfaceAndCancellationRestoresIt() {
        var progress by mutableFloatStateOf(0f)
        composeRule.setContent {
            Box(
                Modifier
                    .size(300.dp)
                    .testTag(CONTAINER_TAG)
                    .background(Color.Magenta),
            ) {
                Box(
                    Modifier
                        .size(200.dp)
                        .predictiveBackSurface(progress = progress, swipeEdgeSign = 1)
                        .background(Color.Cyan),
                )
            }
        }

        val restingPixels = cyanPixels()
        composeRule.runOnIdle { progress = 1f }
        composeRule.waitForIdle()
        val previewPixels = cyanPixels()

        assertEquals(
            restingPixels.count * PredictiveBackMotion.MIN_SCALE * PredictiveBackMotion.MIN_SCALE,
            previewPixels.count.toFloat(),
            restingPixels.count * 0.02f,
        )
        assertTrue(previewPixels.minX > restingPixels.minX)

        composeRule.runOnIdle { progress = 0f }
        composeRule.waitForIdle()
        assertEquals(restingPixels, cyanPixels())
    }

    private fun cyanPixels(): PixelStats {
        val pixels = composeRule.onNodeWithTag(CONTAINER_TAG).captureToImage().toPixelMap()
        var count = 0
        var minX = pixels.width
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red < 0.1f && color.green > 0.9f && color.blue > 0.9f) {
                    count++
                    minX = minOf(minX, x)
                }
            }
        }
        return PixelStats(count = count, minX = minX)
    }

    private data class PixelStats(val count: Int, val minX: Int)

    private companion object {
        const val CONTAINER_TAG = "predictive_back_container"
    }
}
