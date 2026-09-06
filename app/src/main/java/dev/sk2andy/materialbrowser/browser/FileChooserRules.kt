package dev.sk2andy.materialbrowser.browser

import android.content.Intent
import android.provider.MediaStore
import java.net.URI

data class FileChooserIdentity(
    val tabId: String,
    val navigationGeneration: Int,
)

data class FileChooserState(
    val selectedTabId: String,
    val navigationGeneration: Int?,
    val tabExists: Boolean,
    val isActivityResumed: Boolean,
)

object FileChooserRules {
    const val MAX_SELECTED_FILES = 100

    fun isCurrent(identity: FileChooserIdentity, state: FileChooserState): Boolean =
        state.tabExists &&
            state.isActivityResumed &&
            state.selectedTabId == identity.tabId &&
            state.navigationGeneration == identity.navigationGeneration

    fun sanitizedUris(
        values: List<String?>,
        allowMultiple: Boolean,
    ): List<String> {
        val limit = if (allowMultiple) MAX_SELECTED_FILES else 1
        return values.asSequence()
            .mapNotNull { value -> value?.trim()?.takeIf(String::isNotEmpty) }
            .filter { value ->
                value.length <= 8_192 &&
                    value.none { it.code <= 0x20 || it.code == 0x7f } &&
                    isStructuredContentUri(value)
            }
            .distinct()
            .take(limit)
            .toList()
    }

    fun acceptsMimeType(actualMimeType: String?, acceptTypes: Array<String>): Boolean {
        val actual = actualMimeType?.trim()?.lowercase()?.takeIf { it.contains('/') }
            ?: return false
        val accepted = acceptTypes
            .asSequence()
            .flatMap { value -> value.split(',').asSequence() }
            .map(String::trim)
            .map(String::lowercase)
            .filter { value -> value.contains('/') }
            .toList()
        if (accepted.isEmpty() || "*/*" in accepted) return true
        val actualCategory = actual.substringBefore('/')
        return accepted.any { expected ->
            expected == actual || expected == "$actualCategory/*"
        }
    }

    internal fun captureAction(
        capture: BrowserEngineFileCapture,
        mimeTypes: List<String>,
    ): String? {
        if (capture == BrowserEngineFileCapture.None) return null
        val accepted = mimeTypes
            .asSequence()
            .flatMap { value -> value.split(',').asSequence() }
            .map(String::trim)
            .map(String::lowercase)
            .toSet()
        return when {
            accepted.any { value -> value == "image/*" || value.startsWith("image/") } ->
                MediaStore.ACTION_IMAGE_CAPTURE
            accepted.any { value -> value == "video/*" || value.startsWith("video/") } ->
                MediaStore.ACTION_VIDEO_CAPTURE
            else -> null
        }
    }

    private fun isStructuredContentUri(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("content", ignoreCase = true) &&
            !uri.rawAuthority.isNullOrBlank() &&
            uri.userInfo == null
    }.getOrDefault(false)
}

internal class FileChooserResultDelivery<T>(private val callback: (T) -> Unit) {
    private var completed = false

    fun complete(value: T): Boolean {
        if (completed) return false
        completed = true
        callback(value)
        return true
    }
}
