package dev.sk2andy.materialbrowser.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.R
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 35)
class ProfileCreationSheetInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun createsProfileWithSelectedIconAndIsolationMode() {
        val createdProfile = AtomicReference<Pair<String, Boolean>?>()
        composeRule.setContent {
            MaterialTheme {
                EmojiPickerSheet(
                    visible = true,
                    creatingProfile = true,
                    isolationSupported = true,
                    selectedEmoji = null,
                    onCreate = { emoji, isolationEnabled ->
                        createdProfile.set(emoji to isolationEnabled)
                    },
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }

        val createButton = composeRule.onNodeWithText(
            context.getString(R.string.action_create_profile),
        )
        createButton.assertIsNotEnabled()

        composeRule.onNodeWithText("💼").performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.settings_profile_isolation_title),
        ).performClick()
        createButton.assertIsEnabled().performClick()

        assertEquals("💼" to true, createdProfile.get())
    }
}
