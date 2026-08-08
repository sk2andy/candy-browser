package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.ui.theme.MaterialBrowserTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DomainMuteMenuItemInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rowAndSwitchSetMuteStateExactlyOnce() {
        val changes = AtomicInteger()
        composeRule.setContent {
            MaterialBrowserTheme {
                var muted by remember { mutableStateOf(false) }
                DomainMuteMenuItem(
                    enabled = true,
                    muted = muted,
                    onMutedChange = { requested ->
                        changes.incrementAndGet()
                        muted = requested
                    },
                )
            }
        }

        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).assertIsOff()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).performClick()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).assertIsOn()

        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).performClick()
        composeRule.onNodeWithTag(DomainMuteMenuTestTags.Item).assertIsOff()
        assertEquals(2, changes.get())
    }
}
