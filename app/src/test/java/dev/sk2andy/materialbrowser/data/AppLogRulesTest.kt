package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogRulesTest {
    @Test
    fun `diagnostic events use stable names and UTC timestamps`() {
        assertEquals(
            "1970-01-01T00:00:00Z BrowserStarted\n",
            AppLogRules.render(AppLogEvent.BrowserStarted, 0L),
        )
    }

    @Test
    fun `exception messages and page input stay absent across causes`() {
        val cause = IllegalArgumentException("https://private.example/path?token=secret")
        cause.stackTrace = emptyArray()
        val error = IllegalStateException("password=private-input", cause)
        error.stackTrace = emptyArray()

        val rendered = AppLogRules.render(AppLogEvent.UncaughtException, 0L, error)

        assertEquals(
            "1970-01-01T00:00:00Z UncaughtException\n" +
                "  exception java.lang.IllegalStateException\n" +
                "  exception java.lang.IllegalArgumentException\n",
            rendered,
        )
        assertFalse(rendered.contains("private"))
        assertFalse(rendered.contains("secret"))
    }

    @Test
    fun `valid code frames exclude forged filenames and reject page input in code names`() {
        val error = IllegalStateException("hidden message")
        error.stackTrace = arrayOf(
            StackTraceElement(
                "dev.sk2andy.materialbrowser.browser.BrowserController",
                "start",
                "https://private.example/path?token=secret",
                42,
            ),
            StackTraceElement("dev.example.Controller", "<init>", "/private/profile.json", 3),
            StackTraceElement("https://private.example", "load", "Browser.kt", 4),
            StackTraceElement("dev.example.Controller", "https://private.example", "Browser.kt", 5),
            StackTraceElement("dev.example.Controller\nsecret", "load", "Browser.kt", 6),
        )

        val rendered = AppLogRules.render(AppLogEvent.UncaughtException, 0L, error)

        assertEquals(
            "1970-01-01T00:00:00Z UncaughtException\n" +
                "  exception java.lang.IllegalStateException\n" +
                "    at dev.sk2andy.materialbrowser.browser.BrowserController.start:42\n" +
                "    at dev.example.Controller.<init>:3\n",
            rendered,
        )
    }

    @Test
    fun `cyclic causes appear once and stop without looping`() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        first.stackTrace = emptyArray()
        second.stackTrace = emptyArray()
        first.initCause(second)
        second.initCause(first)

        assertEquals(
            "1970-01-01T00:00:00Z UncaughtException\n" +
                "  exception java.lang.IllegalStateException\n" +
                "  exception java.lang.IllegalArgumentException\n",
            AppLogRules.render(AppLogEvent.UncaughtException, 0L, first),
        )
    }

    @Test
    fun `cause depth and stack frames stay bounded`() {
        val errors = List(6) { IllegalStateException("hidden cause $it") }
        errors.forEachIndexed { index, error ->
            error.stackTrace = Array(60) { frame ->
                StackTraceElement("Code", "run", "private.txt", frame)
            }
            if (index < errors.lastIndex) error.initCause(errors[index + 1])
        }

        val rendered = AppLogRules.render(AppLogEvent.UncaughtException, 0L, errors.first())

        assertEquals(4, rendered.lineSequence().count { it.startsWith("  exception ") })
        assertEquals(160, rendered.lineSequence().count { it.startsWith("    at ") })
        assertFalse(rendered.contains("Code.run:40"))
        assertTrue(rendered.toByteArray(Charsets.UTF_8).size <= AppLogRules.MAX_RECORD_BYTES)
    }
}
