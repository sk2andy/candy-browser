package dev.sk2andy.materialbrowser.browser.userscript

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object ToppingVerifier {
    fun verify(
        entry: ToppingCatalogEntry,
        bytes: ByteArray,
        updatedAtMillis: Long,
    ): ToppingVerificationResult {
        if (bytes.size > UserScriptParser.MAX_SOURCE_BYTES) {
            return ToppingVerificationResult.InvalidScript(UserScriptRejectionReason.SourceTooLarge)
        }
        val expectedHash = entry.sha256.hexToBytes() ?: return ToppingVerificationResult.IntegrityMismatch
        val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes)
        if (!MessageDigest.isEqual(expectedHash, actualHash)) {
            return ToppingVerificationResult.IntegrityMismatch
        }
        val source = decodeUtf8(bytes) ?: return ToppingVerificationResult.InvalidUtf8
        if (source.startsWith(UTF8_BOM)) return ToppingVerificationResult.MetadataMismatch
        val parsed = UserScriptParser.parse(
            id = ToppingCatalogRules.stableScriptId(entry.id),
            source = source,
            enabled = true,
            updatedAtMillis = updatedAtMillis,
        )
        val script = when (parsed) {
            is UserScriptParseResult.Accepted -> parsed.script
            is UserScriptParseResult.Rejected -> {
                return ToppingVerificationResult.InvalidScript(parsed.reason)
            }
        }
        if (
            script.name != entry.name ||
            script.matchPatterns + script.includePatterns != entry.matches
        ) {
            return ToppingVerificationResult.MetadataMismatch
        }
        return ToppingVerificationResult.Accepted(script)
    }

    internal fun decodeUtf8(bytes: ByteArray): String? = runCatching {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.hexToBytes(): ByteArray? {
        if (!ToppingCatalogRules.isValidSha256(this)) return null
        return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private const val UTF8_BOM = "\uFEFF"
}
