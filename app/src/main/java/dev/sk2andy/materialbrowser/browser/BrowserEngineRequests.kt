package dev.sk2andy.materialbrowser.browser

import dev.sk2andy.materialbrowser.browser.permissions.SitePermission
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory

internal data class BrowserEngineDownloadResponse(
    val url: String,
    val contentDisposition: String?,
    val mimeType: String?,
)

internal object BrowserEngineDownloadRules {
    fun request(
        response: BrowserEngineDownloadResponse,
        referrer: String?,
    ): BrowserDownloadRequest? = BrowserDownloadRequestFactory.create(
        url = response.url,
        contentDisposition = response.contentDisposition,
        mimeType = response.mimeType,
        referrer = referrer,
    )
}

internal enum class BrowserEngineFileCapture {
    None,
    Any,
    User,
    Environment,
}

internal data class BrowserEngineFilePromptRequest(
    val mimeTypes: List<String>,
    val allowMultiple: Boolean,
    val capture: BrowserEngineFileCapture,
    val response: BrowserEngineFilePromptResponse,
)

internal fun interface BrowserEngineFilePromptResponse {
    fun complete(uris: List<String>?)
}

internal data class BrowserEngineAndroidPermissionRequest(
    val permissions: Set<String>,
    val response: BrowserEngineBooleanResponse,
)

internal data class BrowserEngineContentPermissionRequest(
    val origin: String,
    val permission: SitePermission,
    val response: BrowserEngineBooleanResponse,
)

internal data class BrowserEngineMediaPermissionRequest(
    val origin: String,
    val permissions: Set<SitePermission>,
    val response: BrowserEnginePermissionSetResponse,
)

internal fun interface BrowserEngineBooleanResponse {
    fun complete(allowed: Boolean)
}

internal fun interface BrowserEnginePermissionSetResponse {
    fun complete(allowed: Set<SitePermission>)
}

internal data class BrowserEngineAuthPromptRequest(
    val uri: String?,
    val realm: String?,
    val onlyPassword: Boolean,
    val isProxy: Boolean,
    val isCrossOriginSubresource: Boolean,
    val response: BrowserEngineAuthPromptResponse,
)

internal interface BrowserEngineAuthPromptResponse {
    fun confirm(username: String, password: String)

    fun dismiss()
}

enum class BrowserWebPromptKind {
    Alert,
    Confirm,
    Text,
    BeforeUnload,
    Repost,
    Choice,
    Color,
    DateTime,
    FolderUpload,
    Share,
}

data class BrowserWebPromptChoice(
    val id: String,
    val label: String,
    val selected: Boolean,
    val disabled: Boolean,
    val separator: Boolean,
)

data class BrowserWebPrompt(
    val id: Long,
    val tabId: String,
    val kind: BrowserWebPromptKind,
    val title: String?,
    val message: String?,
    val defaultValue: String?,
    val choices: List<BrowserWebPromptChoice> = emptyList(),
    val allowMultiple: Boolean = false,
    val shareUri: String? = null,
)

internal data class BrowserEngineWebPromptRequest(
    val kind: BrowserWebPromptKind,
    val title: String?,
    val message: String?,
    val defaultValue: String?,
    val choices: List<BrowserWebPromptChoice> = emptyList(),
    val allowMultiple: Boolean = false,
    val shareUri: String? = null,
    val response: BrowserEngineWebPromptResponse,
)

internal interface BrowserEngineWebPromptResponse {
    fun confirm(value: String? = null)

    fun dismiss()
}

internal object BrowserWebPromptRules {
    fun sanitized(
        id: Long,
        tabId: String,
        kind: BrowserWebPromptKind,
        title: String?,
        message: String?,
        defaultValue: String?,
        choices: List<BrowserWebPromptChoice> = emptyList(),
        allowMultiple: Boolean = false,
        shareUri: String? = null,
    ): BrowserWebPrompt? {
        if (id <= 0 || tabId.isBlank()) return null
        return BrowserWebPrompt(
            id = id,
            tabId = tabId,
            kind = kind,
            title = displayValue(title, MAX_TITLE_LENGTH),
            message = displayValue(message, MAX_MESSAGE_LENGTH),
            defaultValue = defaultValue
                ?.takeIf { value -> value.length <= MAX_INPUT_LENGTH }
                ?.takeIf { value -> value.none(::isUnsafeDisplayCharacter) },
            choices = choices.take(MAX_CHOICES).mapNotNull { choice ->
                val id = choice.id.trim().takeIf { value -> value.isNotEmpty() && value.length <= MAX_INPUT_LENGTH }
                val label = displayValue(choice.label, MAX_TITLE_LENGTH)
                if (id == null || label == null) null else choice.copy(id = id, label = label)
            },
            allowMultiple = allowMultiple,
            shareUri = displayValue(shareUri, MAX_MESSAGE_LENGTH),
        )
    }

    private fun displayValue(value: String?, maximumLength: Int): String? = value
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.takeIf { candidate -> candidate.length <= maximumLength }
        ?.takeIf { candidate -> candidate.none(::isUnsafeDisplayCharacter) }

    private fun isUnsafeDisplayCharacter(character: Char): Boolean =
        character == '\u0000' || when (character.code) {
            in 0x202A..0x202E,
            in 0x2066..0x2069,
            -> true
            else -> false
        }

    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_MESSAGE_LENGTH = 4_096
    const val MAX_INPUT_LENGTH = 4_096
    private const val MAX_CHOICES = 100
}

internal enum class BrowserEngineNavigationTarget {
    Current,
    New,
    None,
}

internal object BrowserEnginePermissionRules {
    fun runtimePermissions(values: Array<out String>?): Set<String>? {
        val candidates = values?.takeIf { it.isNotEmpty() && it.size <= MAX_ANDROID_PERMISSIONS }
            ?: return null
        val normalized = candidates.asSequence()
            .map(String::trim)
            .distinct()
            .toSet()
        return normalized.takeIf { permissions ->
            permissions.isNotEmpty() && permissions.all { value ->
                value.length <= MAX_ANDROID_PERMISSION_LENGTH &&
                    value in ALLOWED_ANDROID_PERMISSIONS &&
                    value.none { character -> character.isWhitespace() || character.isISOControl() }
            }
        }
    }

    fun requestedMediaPermissions(
        hasCamera: Boolean,
        hasMicrophone: Boolean,
        hasUnsupportedVideoSource: Boolean,
        hasUnsupportedAudioSource: Boolean,
    ): Set<SitePermission>? {
        if (hasUnsupportedVideoSource || hasUnsupportedAudioSource) return null
        return buildSet {
            if (hasCamera) add(SitePermission.Camera)
            if (hasMicrophone) add(SitePermission.Microphone)
        }.takeIf(Set<SitePermission>::isNotEmpty)
    }

    private val ALLOWED_ANDROID_PERMISSIONS = setOf(
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
    )
    private const val MAX_ANDROID_PERMISSION_LENGTH = 160
    private const val MAX_ANDROID_PERMISSIONS = 8
}
