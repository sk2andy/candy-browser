package dev.sk2andy.materialbrowser.reader

/** Samples the article rather than trusting its title or the device's configured language. */
internal object ReaderSpeechLanguageRules {
    private const val WINDOW_CHARACTERS = 3_000
    private const val MIN_LETTERS = 40
    private const val MAX_CHARACTERS = WINDOW_CHARACTERS * 3 + 2

    fun sample(content: String): String? {
        val sample =
            if (content.length <= MAX_CHARACTERS) content
            else {
                val middle = (content.length - WINDOW_CHARACTERS) / 2
                buildString(MAX_CHARACTERS) {
                    append(content, 0, WINDOW_CHARACTERS)
                    append('\n')
                    append(content, middle, middle + WINDOW_CHARACTERS)
                    append('\n')
                    append(content, content.length - WINDOW_CHARACTERS, content.length)
                }
            }
        return sample.takeIf { it.count(Char::isLetter) >= MIN_LETTERS }
    }
}
