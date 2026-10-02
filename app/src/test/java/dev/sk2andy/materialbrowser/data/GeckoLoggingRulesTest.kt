package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeckoLoggingRulesTest {
    @Test
    fun `native modules retain case and normalize whitespace and duplicate levels`() {
        assertTrue(GeckoLoggingRules.isValidModules(" nsHttp : 5 ,cookie:0,nsHttp:3 "))
        assertEquals(
            "nsHttp:3,cookie:0",
            GeckoLoggingRules.normalizedModules(" nsHttp : 5 ,cookie:0,nsHttp:3 "),
        )
        assertEquals(
            "_test-Module.2:4,.module:1,-module:2",
            GeckoLoggingRules.normalizedModules("_test-Module.2:4,.module:1,-module:2"),
        )
    }

    @Test
    fun `malformed modules reject entire configuration and restore safe defaults`() {
        listOf(
            "",
            " ",
            "nsHttp",
            "nsHttp:6",
            "nsHttp:-1",
            "nsHttp:05",
            "nsHttp:1:2",
            "1Http:5",
            "nśHttp:5",
            "nsHttp:5,cookie:7",
            "nsHttp:5,",
            "nsHttp:5,profilerstacks",
            "nsHttp:5\nLOG_FILE:/tmp/private",
        ).forEach { value ->
            assertFalse(value, GeckoLoggingRules.isValidModules(value))
            assertEquals(value, GeckoLoggingRules.DEFAULT_MODULES, GeckoLoggingRules.normalizedModules(value))
        }
    }

    @Test
    fun `module limits accept exact length and count boundaries`() {
        val exactLength = "a".repeat(GeckoLoggingRules.MAX_MODULES_LENGTH - 2) + ":5"
        val exactCount = (1..16).joinToString(",") { "module$it:5" }

        assertTrue(GeckoLoggingRules.isValidModules(exactLength))
        assertFalse(GeckoLoggingRules.isValidModules("a$exactLength"))
        assertTrue(GeckoLoggingRules.isValidModules(exactCount))
        assertFalse(GeckoLoggingRules.isValidModules("$exactCount,module17:5"))
        assertEquals(exactCount, GeckoLoggingRules.normalizedModules(exactCount))
    }

    @Test
    fun `reserved logging configuration preference names cannot enter module settings`() {
        listOf(
            "config.LOG_FILE:5",
            "config.sync:1",
            "config.add_timestamp:1",
            "config.clear_on_startup:0",
            "nsHttp:5,config.LOG_FILE:5",
        ).forEach { value ->
            assertFalse(value, GeckoLoggingRules.isValidModules(value))
            assertEquals(value, GeckoLoggingRules.DEFAULT_MODULES, GeckoLoggingRules.normalizedModules(value))
        }
        assertTrue(GeckoLoggingRules.isValidModules("Config.LOG_FILE:5,configuration.module:5"))
    }

    @Test
    fun `default modules are valid and normalization stays idempotent`() {
        assertTrue(GeckoLoggingRules.isValidModules(GeckoLoggingRules.DEFAULT_MODULES))
        val modules = GeckoLoggingRules.normalizedModules(" nsHttp:5, cookie:3 ")
        assertEquals(modules, GeckoLoggingRules.normalizedModules(modules))
    }
}
