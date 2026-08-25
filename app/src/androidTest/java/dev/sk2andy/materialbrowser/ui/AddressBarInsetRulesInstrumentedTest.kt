package dev.sk2andy.materialbrowser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressBarInsetRulesInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun platformResizedAndFullHeightRootsPlaceBarAtSameImeEdge() {
        composeRule.setContent {
            val density = LocalDensity.current
            val fullWindowHeightPx = with(density) { FullWindowHeight.roundToPx() }
            val imeBottomPx = with(density) { ImeHeight.roundToPx() }
            val navigationBottomPx = with(density) { NavigationHeight.roundToPx() }
            val imeInsets = WindowInsets(0, 0, 0, imeBottomPx)
            val navigationInsets = WindowInsets(0, 0, 0, navigationBottomPx)

            Box {
                TestAddressBar(
                    fullWindowHeightPx = fullWindowHeightPx,
                    rootBottomInWindowPx = fullWindowHeightPx,
                    imeInsets = imeInsets,
                    navigationInsets = navigationInsets,
                    tag = FullHeightBarTag,
                    modifier = Modifier.size(width = HostWidth, height = FullWindowHeight),
                )
                TestAddressBar(
                    fullWindowHeightPx = fullWindowHeightPx,
                    rootBottomInWindowPx = with(density) {
                        ResizedWindowHeight.roundToPx()
                    },
                    imeInsets = imeInsets,
                    navigationInsets = navigationInsets,
                    tag = ResizedBarTag,
                    modifier = Modifier
                        .offset(x = HostWidth)
                        .size(width = HostWidth, height = ResizedWindowHeight),
                )
            }
        }

        val fullHeightBottom = composeRule.onNodeWithTag(FullHeightBarTag)
            .fetchSemanticsNode().boundsInRoot.bottom
        val resizedBottom = composeRule.onNodeWithTag(ResizedBarTag)
            .fetchSemanticsNode().boundsInRoot.bottom
        val expectedBottom = with(composeRule.density) {
            (FullWindowHeight - ImeHeight - AddressBarGap).toPx()
        }

        assertEquals(expectedBottom, fullHeightBottom, 1f)
        assertEquals(fullHeightBottom, resizedBottom, 1f)
    }

    @Composable
    private fun TestAddressBar(
        fullWindowHeightPx: Int,
        rootBottomInWindowPx: Int,
        imeInsets: WindowInsets,
        navigationInsets: WindowInsets,
        tag: String,
        modifier: Modifier,
    ) {
        Box(modifier) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .addressBarWindowInsetsPadding(
                        fullWindowHeightPx = fullWindowHeightPx,
                        rootBottomInWindowPx = rootBottomInWindowPx,
                        imeInsets = imeInsets,
                        navigationBarInsets = navigationInsets,
                    )
                    .padding(bottom = AddressBarGap),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = HostWidth, height = AddressBarHeight)
                        .testTag(tag),
                )
            }
        }
    }

    private companion object {
        val FullWindowHeight = 600.dp
        val ResizedWindowHeight = 360.dp
        val ImeHeight = 240.dp
        val NavigationHeight = 24.dp
        val HostWidth = 200.dp
        val AddressBarHeight = 56.dp
        val AddressBarGap = 12.dp
        const val FullHeightBarTag = "full_height_address_bar"
        const val ResizedBarTag = "resized_address_bar"
    }
}
