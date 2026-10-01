package dev.sk2andy.materialbrowser.data

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCrashTraceRulesTest {
    @Test
    fun `crashed thread exports relative PCs module basenames symbols and build ids`() {
        val trace = message(
            threadEntry(
                42L,
                frame(
                    relativePc = 0x1234L,
                    function = "mozilla::ipc::MessageChannel::OnMessageReceived",
                    offset = 12L,
                    path = "/data/app/private-install/base.apk!lib/arm64-v8a/libxul.so",
                    buildId = "ABCDEF0123456789",
                ),
                frame(relativePc = 0x5678L, function = "abort", offset = 8L),
            ),
            integer(1, 1L),
            integer(6, 42L),
            bytes(10, message(integer(1, 11L), integer(3, 1L))),
        )

        assertEquals(
            "native crash arch=arm64 signal=11 code=1\n" +
                "  #00 pc 0000000000001234 libxul.so " +
                "(mozilla::ipc::MessageChannel::OnMessageReceived+12) " +
                "(BuildId: abcdef0123456789)\n" +
                "  #01 pc 0000000000005678 libc.so (abort+8)\n",
            NativeCrashTraceRules.parse(trace),
        )
    }

    @Test
    fun `causes logs registers raw memory names and noncrashed thread frames stay absent`() {
        val privateText = "https://private.example/path?token=secret"
        val crashedThread = message(
            integer(1, 42L),
            text(2, "private-thread-name"),
            bytes(3, message(text(1, "private-register"), integer(2, 0xfaceL))),
            bytes(4, frame(relativePc = 123L)),
            bytes(5, message(text(2, privateText), text(4, "private-memory-password"))),
            text(7, "private-backtrace-note"),
        )
        val trace = message(
            integer(1, 1L),
            integer(6, 42L),
            text(2, "private-device-fingerprint"),
            text(9, privateText),
            text(14, "private-abort-message"),
            bytes(15, message(text(1, privateText))),
            bytes(16, message(integer(1, 42L), bytes(2, crashedThread))),
            threadEntry(43L, frame(path = "/system/lib64/libprivate.so")),
            bytes(17, message(text(7, "/private/memory-map"))),
            bytes(18, message(text(1, "private-log"), bytes(2, message(text(6, privateText))))),
            bytes(21, message(text(1, "private-detail"), text(2, "private-data"))),
        )

        assertEquals(
            "native crash arch=arm64 signal=0 code=0\n" +
                "  #00 pc 000000000000007b libc.so (abort+0)\n",
            NativeCrashTraceRules.parse(trace),
        )
    }

    @Test
    fun `64 bit relative PC and signed signal code preserve numeric values`() {
        val trace = tombstone(
            frames = arrayOf(frame(relativePc = -1L, offset = Long.MIN_VALUE)),
            signal = message(integer(1, 6L), integer(3, -6L)),
        )

        assertEquals(
            "native crash arch=arm64 signal=6 code=-6\n" +
                "  #00 pc ffffffffffffffff libc.so (abort+9223372036854775808)\n",
            NativeCrashTraceRules.parse(trace),
        )
    }

    @Test
    fun `unknown fields of supported wire types skip without exposing content`() {
        val trace = tombstone(arrayOf(frame())) + message(
            integer(100, 99L),
            varint((101L shl 3) or 1L) + ByteArray(8) { 0xff.toByte() },
            text(102, "private-secret"),
            varint((103L shl 3) or 5L) + ByteArray(4) { 0xff.toByte() },
        )

        assertEquals(
            NativeCrashTraceRules.parse(tombstone(arrayOf(frame()))),
            NativeCrashTraceRules.parse(trace),
        )
    }

    @Test
    fun `unsupported malformed and oversized traces return null`() {
        val valid = tombstone(arrayOf(frame()))
        val invalidInputs = listOf(
            byteArrayOf(),
            ByteArray(NativeCrashTraceRules.MAX_INPUT_BYTES + 1),
            valid + byteArrayOf(0),
            valid + byteArrayOf(0x80.toByte()),
            valid + varint((100L shl 3) or 3L),
            valid + varint((100L shl 3) or 4L),
            valid + varint((100L shl 3) or 6L),
            valid + varint((100L shl 3) or 1L) + byteArrayOf(1),
            valid + varint((100L shl 3) or 5L) + byteArrayOf(1),
            valid + varint((100L shl 3) or 2L) + varint(5L) + byteArrayOf(1),
            valid + varint((100L shl 3) or 0L) + ByteArray(10) { 0xff.toByte() },
            valid + bytes(10, byteArrayOf(0x08, 0x80.toByte())),
            valid + bytes(1, byteArrayOf(1)),
            tombstone(arrayOf(frame()), architecture = 99L),
            message(integer(6, 42L)),
            tombstone(arrayOf()),
        )

        invalidInputs.forEachIndexed { index, input ->
            assertNull("invalid input $index", NativeCrashTraceRules.parse(input))
        }
    }

    @Test
    fun `malicious paths symbols and build ids never enter trace text`() {
        val trace = tombstone(
            arrayOf(
                frame(path = "https://private.example/libprivate.so"),
                frame(path = "/system/lib64/libprivate.so\nsecret"),
                frame(path = "/private/profile.txt"),
                frame(
                    relativePc = 7L,
                    function = "https://private.example/secret",
                    buildId = "private-secret",
                ),
                frame(relativePc = 8L, function = "abort\nprivate-secret"),
            ),
        )

        assertEquals(
            "native crash arch=arm64 signal=0 code=0\n" +
                "  #03 pc 0000000000000007 libc.so\n" +
                "  #04 pc 0000000000000008 libc.so\n",
            NativeCrashTraceRules.parse(trace),
        )
        assertNull(NativeCrashTraceRules.parse(tombstone(arrayOf(frame(path = "private.txt")))))
    }

    @Test
    fun `frame count and output byte size stay bounded without cutting lines`() {
        val shortTrace = requireNotNull(
            NativeCrashTraceRules.parse(tombstone(Array(100) { frame(relativePc = it.toLong()) })),
        )
        assertEquals(64, shortTrace.lineSequence().count { it.startsWith("  #") })
        assertTrue(shortTrace.contains("  #63 pc "))
        assertFalse(shortTrace.contains("  #64 pc "))
        val longTrace = requireNotNull(
            NativeCrashTraceRules.parse(
                tombstone(
                    Array(64) {
                        frame(
                            function = "a".repeat(256),
                            path = "/system/lib64/${"b".repeat(128)}.so",
                            buildId = "c".repeat(128),
                        )
                    },
                ),
            ),
        )

        assertTrue(longTrace.toByteArray(Charsets.UTF_8).size <= AppLogRules.MAX_RECORD_BYTES - 512)
        assertTrue(longTrace.endsWith(")\n"))
        assertTrue(longTrace.lineSequence().count { it.startsWith("  #") } in 1..63)
    }

    @Test
    fun `oversized metadata is dropped and bounded valid module PC stays available`() {
        val trace = tombstone(
            arrayOf(
                frame(function = "a".repeat(257), buildId = "a".repeat(129)),
                frame(path = "/${"p".repeat(4_096)}/libc.so"),
            ),
        )

        assertEquals(
            "native crash arch=arm64 signal=0 code=0\n" +
                "  #00 pc 0000000000000000 libc.so\n",
            NativeCrashTraceRules.parse(trace),
        )
    }

    private fun tombstone(
        frames: Array<ByteArray>,
        signal: ByteArray = byteArrayOf(),
        architecture: Long = 1L,
    ): ByteArray = message(
        integer(1, architecture),
        integer(6, 42L),
        bytes(10, signal),
        threadEntry(42L, *frames),
    )

    private fun threadEntry(id: Long, vararg frames: ByteArray): ByteArray = bytes(
        16,
        message(
            integer(1, id),
            bytes(2, message(integer(1, id), *frames.map { bytes(4, it) }.toTypedArray())),
        ),
    )

    private fun frame(
        relativePc: Long = 0L,
        function: String = "abort",
        offset: Long = 0L,
        path: String = "/system/lib64/libc.so",
        buildId: String = "",
    ): ByteArray = message(
        integer(1, relativePc),
        integer(2, 0xfaceL),
        integer(3, 0xbeefL),
        text(4, function),
        integer(5, offset),
        text(6, path),
        text(8, buildId),
    )

    private fun integer(field: Int, value: Long): ByteArray =
        varint(field.toLong() shl 3) + varint(value)

    private fun text(field: Int, value: String): ByteArray = bytes(field, value.toByteArray(Charsets.UTF_8))

    private fun bytes(field: Int, value: ByteArray): ByteArray =
        varint((field.toLong() shl 3) or 2L) + varint(value.size.toLong()) + value

    private fun message(vararg fields: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        fields.forEach(output::write)
        output.toByteArray()
    }

    private fun varint(value: Long): ByteArray = ByteArrayOutputStream().use { output ->
        var remaining = value
        while ((remaining and -128L) != 0L) {
            output.write(((remaining and 127L) or 128L).toInt())
            remaining = remaining ushr 7
        }
        output.write(remaining.toInt())
        output.toByteArray()
    }
}
