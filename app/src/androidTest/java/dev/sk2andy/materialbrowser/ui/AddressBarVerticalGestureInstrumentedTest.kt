package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarVerticalGestureInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verticalSwipeCoexistsWithHorizontalDragAndTap() {
        val swipes = AtomicInteger()
        val horizontalDrag = AtomicInteger()
        val horizontalDragStops = AtomicInteger()
        val taps = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                Box(
                    modifier = Modifier
                        .size(width = 320.dp, height = 160.dp)
                        .addressBarVerticalGesture(onSwipeUp = swipes::incrementAndGet)
                        .draggable(
                            state = rememberDraggableState { delta ->
                                horizontalDrag.addAndGet(delta.toInt())
                            },
                            orientation = Orientation.Horizontal,
                            onDragStopped = { horizontalDragStops.incrementAndGet() },
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(AddressBarTag)
                            .clickable(onClick = taps::incrementAndGet),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(AddressBarTag).performTouchInput { swipeUp() }
        assertEquals(1, swipes.get())
        composeRule.onNodeWithTag(AddressBarTag).performTouchInput { swipeRight() }
        assertEquals(1, swipes.get())
        composeRule.onNodeWithTag(AddressBarTag).performTouchInput { click() }

        assertEquals(1, swipes.get())
        assertTrue(horizontalDrag.get() > 0)
        assertEquals(1, horizontalDragStops.get())
        assertEquals(1, taps.get())
    }

    private companion object {
        const val AddressBarTag = "address_bar"
    }
}
