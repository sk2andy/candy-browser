package dev.sk2andy.materialbrowser.data

object SnoozePurposeRules {
    const val MAX_LENGTH = 240

    fun normalize(value: String): String = value
        .take(MAX_LENGTH * 4)
        .map { character -> if (character.isWhitespace() || character.isISOControl()) ' ' else character }
        .joinToString("")
        .trim()
        .replace(Regex(" +"), " ")
        .take(MAX_LENGTH)
        .trimEnd()
}
