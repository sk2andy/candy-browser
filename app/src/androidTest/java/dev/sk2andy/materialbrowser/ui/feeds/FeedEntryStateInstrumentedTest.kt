package dev.sk2andy.materialbrowser.ui.feeds

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedEntryStateInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun normalCollectionAndBrowserReturnEntrySurviveRecreation() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var visible: MutableState<FeedsEntry?>
        lateinit var returning: MutableState<FeedsEntry?>
        restoration.setContent {
            visible = rememberFeedsEntryState("profile", "tab", false)
            returning = rememberFeedsEntryState("profile", "tab", false)
        }
        val entry = FeedsEntry(discoveryUrl = "https://publisher.example/feed", queueRequestId = 42)
        val returnEntry = FeedsEntry(libraryOnly = true)
        composeRule.runOnIdle {
            visible.value = entry
            returning.value = returnEntry
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            assertEquals(entry, visible.value)
            assertEquals(returnEntry, returning.value)
        }
    }

    @Test
    fun privateCollectionEntryAndDiscoveryAddressAreNotRestored() {
        val restoration = StateRestorationTester(composeRule)
        lateinit var entry: MutableState<FeedsEntry?>
        restoration.setContent { entry = rememberFeedsEntryState("profile", "tab", true) }
        composeRule.runOnIdle {
            entry.value = FeedsEntry(discoveryUrl = "https://private.example/secret")
        }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { assertNull(entry.value) }
    }

    @Test
    fun switchingProfileTabOrPrivacyClearsCollectionNavigation() {
        val profile = mutableStateOf("first-profile")
        val tab = mutableStateOf("first-tab")
        val private = mutableStateOf(false)
        lateinit var entry: MutableState<FeedsEntry?>
        composeRule.setContent {
            entry = rememberFeedsEntryState(profile.value, tab.value, private.value)
        }
        composeRule.runOnIdle {
            entry.value = FeedsEntry()
            profile.value = "second-profile"
        }
        composeRule.runOnIdle {
            assertNull(entry.value)
            entry.value = FeedsEntry()
            tab.value = "second-tab"
        }
        composeRule.runOnIdle {
            assertNull(entry.value)
            entry.value = FeedsEntry()
            private.value = true
        }
        composeRule.runOnIdle { assertNull(entry.value) }
    }
}
