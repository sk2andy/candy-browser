package dev.sk2andy.materialbrowser.browser.gecko

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeckoDefaultExtensionDownloadRulesTest {
    @Test
    fun `pinned AMO file URL is accepted`() {
        GeckoDefaultExtensionDownloadRules.requirePinnedAmoUrl(
            "https://addons.mozilla.org/firefox/downloads/file/4637154/cookies-1.1.9.xpi",
        )
    }

    @Test
    fun `redirect policy rejects mutable or foreign URLs`() {
        listOf(
            "http://addons.mozilla.org/firefox/downloads/file/1/addon.xpi",
            "https://example.com/firefox/downloads/file/1/addon.xpi",
            "https://user@addons.mozilla.org/firefox/downloads/file/1/addon.xpi",
            "https://addons.mozilla.org:444/firefox/downloads/file/1/addon.xpi",
            "https://addons.mozilla.org/firefox/downloads/latest/addon.xpi",
            "https://addons.mozilla.org/firefox/downloads/file/1/addon.xpi?next=2",
            "https://addons.mozilla.org/firefox/downloads/file/1/addon.xpi#fragment",
        ).forEach { url ->
            assertThrows(IllegalStateException::class.java) {
                GeckoDefaultExtensionDownloadRules.requirePinnedAmoUrl(url)
            }
        }
    }

    @Test
    fun `download body is copied only when size and hash match`() {
        val body = "signed-xpi".encodeToByteArray()
        val output = ByteArrayOutputStream()

        GeckoDefaultExtensionDownloadRules.copyAndVerify(
            input = ByteArrayInputStream(body),
            output = output,
            expectedSize = body.size.toLong(),
            expectedSha256 = body.sha256(),
        )

        assertArrayEquals(body, output.toByteArray())
    }

    @Test
    fun `download body rejects oversize short and hash mismatch`() {
        val body = "signed-xpi".encodeToByteArray()
        listOf(
            (body.size - 1).toLong() to body.sha256(),
            (body.size + 1).toLong() to body.sha256(),
            body.size.toLong() to "0".repeat(64),
        ).forEach { (size, hash) ->
            assertThrows(IllegalStateException::class.java) {
                GeckoDefaultExtensionDownloadRules.copyAndVerify(
                    input = ByteArrayInputStream(body),
                    output = ByteArrayOutputStream(),
                    expectedSize = size,
                    expectedSha256 = hash,
                )
            }
        }
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
