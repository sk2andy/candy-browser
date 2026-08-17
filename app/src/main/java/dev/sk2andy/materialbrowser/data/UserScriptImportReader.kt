package dev.sk2andy.materialbrowser.data

import android.content.ContentResolver
import android.net.Uri
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptParser
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal sealed interface UserScriptImportResult {
    data class Loaded(val source: String) : UserScriptImportResult
    data object Empty : UserScriptImportResult
    data object TooLarge : UserScriptImportResult
    data object InvalidUtf8 : UserScriptImportResult
    data object Unreadable : UserScriptImportResult
}

internal object UserScriptImportReader {
    fun read(contentResolver: ContentResolver, uri: Uri): UserScriptImportResult = runCatching {
        contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (output.size() + count > UserScriptParser.MAX_SOURCE_BYTES) {
                    return UserScriptImportResult.TooLarge
                }
                output.write(buffer, 0, count)
            }
            if (output.size() == 0) return UserScriptImportResult.Empty
            val bytes = output.toByteArray()
            val source = runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrElse { return UserScriptImportResult.InvalidUtf8 }
            UserScriptImportResult.Loaded(source)
        } ?: UserScriptImportResult.Unreadable
    }.getOrDefault(UserScriptImportResult.Unreadable)

    private const val BUFFER_BYTES = 8 * 1_024
}
