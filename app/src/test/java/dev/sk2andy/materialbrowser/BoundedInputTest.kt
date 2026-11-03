package dev.sk2andy.materialbrowser

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedInputTest {
    @Test
    fun `reads up to the limit and leaves the rest in the stream`() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))

        assertArrayEquals(byteArrayOf(1, 2, 3), input.readAtMost(3))
        assertEquals(4, input.read())
    }

    @Test
    fun `reads shorter and exactly sized streams and handles zero limit`() {
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readAtMost(3))
        assertArrayEquals(byteArrayOf(1, 2), ByteArrayInputStream(byteArrayOf(1, 2)).readAtMost(2))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf(1)).readAtMost(0))
    }

    @Test
    fun `propagates read failures`() {
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("read failed")
        }

        assertThrows(IOException::class.java) { input.readAtMost(1) }
    }
}
