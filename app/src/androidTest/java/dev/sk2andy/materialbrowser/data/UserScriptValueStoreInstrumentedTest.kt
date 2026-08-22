package dev.sk2andy.materialbrowser.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserScriptValueStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun valuesAreIsolatedByScriptAndClearWithDeletion() {
        val store = UserScriptValueStore(context)
        store.clear("script-a")
        store.clear("script-b")

        assertTrue(store.set("script-a", "theme", "\"dark\""))
        assertTrue(store.set("script-b", "theme", "\"light\""))
        assertEquals(mapOf("theme" to "\"dark\""), store.snapshot("script-a"))
        assertEquals(mapOf("theme" to "\"light\""), store.snapshot("script-b"))

        assertTrue(store.delete("script-a", "theme"))
        assertTrue(store.snapshot("script-a").isEmpty())
        assertFalse(store.snapshot("script-b").isEmpty())
        store.clear("script-b")
    }

    @Test
    fun rejectsInvalidKeysAndOversizedValues() {
        val store = UserScriptValueStore(context)
        store.clear("bounded")

        assertFalse(store.set("bounded", "", "1"))
        assertFalse(store.set("bounded", "invalid", "not-json"))
        assertFalse(
            store.set(
                "bounded",
                "large",
                "x".repeat(UserScriptBridgeContract.MAX_ENCODED_VALUE_BYTES + 1),
            ),
        )
        assertTrue(store.set("bounded", "theme", "\"dark\""))
        assertEquals(mapOf("theme" to "\"dark\""), store.snapshot("bounded"))
        store.clear("bounded")
        assertTrue(store.snapshot("bounded").isEmpty())
    }
}
