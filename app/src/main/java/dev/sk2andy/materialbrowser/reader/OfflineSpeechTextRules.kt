package dev.sk2andy.materialbrowser.reader

import java.text.Normalizer

internal object OfflineSpeechTextRules {
    const val MAX_CHUNK_CHARACTERS = 300

    /** Supertonic's Unicode frontend expects NFKD and a language tag; na detects language locally. */
    fun prepare(text: String): String {
        require(text.length <= MAX_CHUNK_CHARACTERS)
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
        val cleaned = buildString {
            normalized.codePoints().forEach { point ->
                when {
                    point in 0x1F000..0x1FAFF || point in 0x2600..0x27BF -> Unit
                    point > 0xFFFF || Character.isISOControl(point) -> append(' ')
                    else -> append(point.toChar())
                }
            }
        }.replace(Regex("[–‑—]"), "-")
            .replace(Regex("[“”]"), "\"")
            .replace(Regex("[‘’´`]"), "'")
            .replace(Regex("[_\\[\\]|/#<>→←]"), " ")
            .replace(Regex("[♥☆♡©\\\\]"), "")
            .replace(Regex("\\s+"), " ")
            .replace(Regex(" +([,.!?;:])"), "$1")
            .trim()
        if (cleaned.isEmpty()) return ""
        val terminated = if (cleaned.last() in ".!?;:,'\")]}…。」』】〉》›»") cleaned else "$cleaned."
        return "<na>$terminated</na>"
    }
}
