package dev.sk2andy.materialbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AppLogCrashHandlerTest {
    @Test
    fun `crash is written before original handler receives unchanged failure`() {
        val calls = mutableListOf<String>()
        val thread = Thread.currentThread()
        val failure = IllegalStateException("test")
        val handler = AppLogCrashHandler(
            previousHandler = { receivedThread, receivedError ->
                assertSame(thread, receivedThread)
                assertSame(failure, receivedError)
                calls += "delegate"
            },
            writeCrash = { receivedError ->
                assertSame(failure, receivedError)
                calls += "write"
            },
        )

        handler.uncaughtException(thread, failure)

        assertEquals(listOf("write", "delegate"), calls)
    }

    @Test
    fun `failed logging never prevents original crash handling`() {
        var delegatedError: Throwable? = null
        val failure = IllegalStateException("original")
        val handler = AppLogCrashHandler(
            previousHandler = { _, error -> delegatedError = error },
            writeCrash = { throw IllegalStateException("disk failure") },
        )

        handler.uncaughtException(Thread.currentThread(), failure)

        assertSame(failure, delegatedError)
    }
}
