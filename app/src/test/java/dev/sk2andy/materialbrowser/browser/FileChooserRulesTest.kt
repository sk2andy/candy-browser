package dev.sk2andy.materialbrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileChooserRulesTest {
    private val identity = FileChooserIdentity("tab-a", 4)

    @Test
    fun onlyContentUrisAreReturnedAndSingleModeKeepsOne() {
        assertEquals(
            listOf("content://media/image/1"),
            FileChooserRules.sanitizedUris(
                listOf(
                    "file:///data/user/0/secret",
                    "content:///missing-authority",
                    "content://user@media/image/spoofed",
                    "content://media/image/1",
                    "content://media/image/2",
                    "https://example.com/file.jpg",
                ),
                allowMultiple = false,
            ),
        )
    }

    @Test
    fun multipleModeDeduplicatesAndRejectsControlCharacters() {
        assertEquals(
            listOf("content://media/image/1", "content://media/image/2"),
            FileChooserRules.sanitizedUris(
                listOf(
                    "content://media/image/1",
                    "content://media/image/1",
                    "content://media/image/2",
                    "content://media/image/3\nspoofed",
                ),
                allowMultiple = true,
            ),
        )
    }

    @Test
    fun navigationTabSwitchAndLifecycleInvalidateResult() {
        val current = FileChooserState("tab-a", 4, tabExists = true, isActivityResumed = true)
        assertTrue(FileChooserRules.isCurrent(identity, current))
        assertFalse(FileChooserRules.isCurrent(identity, current.copy(navigationGeneration = 5)))
        assertFalse(FileChooserRules.isCurrent(identity, current.copy(selectedTabId = "tab-b")))
        assertFalse(FileChooserRules.isCurrent(identity, current.copy(isActivityResumed = false)))
        assertFalse(FileChooserRules.isCurrent(identity, current.copy(tabExists = false)))
    }

    @Test
    fun mimeTypeMustMatchValidAcceptPatterns() {
        assertTrue(FileChooserRules.acceptsMimeType("image/jpeg", arrayOf("image/*")))
        assertTrue(FileChooserRules.acceptsMimeType("image/jpeg", arrayOf("image/jpeg")))
        assertTrue(FileChooserRules.acceptsMimeType("application/pdf", emptyArray()))
        assertFalse(FileChooserRules.acceptsMimeType("text/plain", arrayOf("image/*")))
        assertFalse(FileChooserRules.acceptsMimeType(null, arrayOf("*/*")))
    }

    @Test
    fun resultDeliveryCompletesExactlyOnce() {
        val delivered = mutableListOf<String?>()
        val delivery = FileChooserResultDelivery<String?> { value -> delivered += value }

        assertTrue(delivery.complete(null))
        assertFalse(delivery.complete("late-result"))
        assertEquals(listOf<String?>(null), delivered)
    }
}
