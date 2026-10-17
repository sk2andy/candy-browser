package dev.sk2andy.materialbrowser.browser

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

object BrowserWebPromptRules {
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
    private const val MAX_CHOICES = 1_000
}
