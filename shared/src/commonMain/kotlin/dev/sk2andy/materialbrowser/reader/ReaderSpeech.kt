package dev.sk2andy.materialbrowser.reader

enum class ReaderSpeechStatus { Initializing, Ready, Speaking, Paused, Unavailable, Closed }

data class ReaderSpeechState(
    val status: ReaderSpeechStatus = ReaderSpeechStatus.Initializing,
    val characterOffset: Int = 0,
)

sealed interface ReaderSpeechEvent {
    data object Initialized : ReaderSpeechEvent
    data object InitializationFailed : ReaderSpeechEvent
    data object Play : ReaderSpeechEvent
    data class RangeStarted(val characterOffset: Int) : ReaderSpeechEvent
    data object Pause : ReaderSpeechEvent
    data object Stop : ReaderSpeechEvent
    data object Completed : ReaderSpeechEvent
    data object Close : ReaderSpeechEvent
}

interface ReaderSpeech {
    val state: ReaderSpeechState

    fun play(content: String)

    fun pause()

    fun stop()

    fun close()
}

object ReaderSpeechRules {
    fun currentExcerpt(
        content: String,
        characterOffset: Int,
        maxChars: Int = 140,
    ): String {
        if (content.isBlank()) return ""
        val offset = characterOffset.coerceIn(0, content.lastIndex)
        val boundaries = charArrayOf('\n', '.', '!', '?')
        var start = content.lastIndexOfAny(
            boundaries,
            startIndex = (offset - 1).coerceAtLeast(0),
        ).let { if (it >= 0) it + 1 else 0 }
        while (start < content.length && content[start].isWhitespace()) start++
        val naturalEnd = content.indexOfAny(boundaries, startIndex = offset)
            .let { if (it >= 0) it + 1 else content.length }
        val hardEnd = minOf(start + maxChars.coerceAtLeast(1), naturalEnd)
        val end = if (hardEnd < naturalEnd) {
            content.lastIndexOf(' ', hardEnd).takeIf { it > start } ?: hardEnd
        } else {
            hardEnd
        }
        return content.substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun reduce(
        state: ReaderSpeechState,
        event: ReaderSpeechEvent,
        textLength: Int,
    ): ReaderSpeechState {
        if (state.status == ReaderSpeechStatus.Closed) return state
        return when (event) {
            ReaderSpeechEvent.Initialized -> ReaderSpeechState(ReaderSpeechStatus.Ready)
            ReaderSpeechEvent.InitializationFailed ->
                ReaderSpeechState(ReaderSpeechStatus.Unavailable)
            ReaderSpeechEvent.Play -> if (
                state.status == ReaderSpeechStatus.Ready ||
                state.status == ReaderSpeechStatus.Paused
            ) {
                state.copy(status = ReaderSpeechStatus.Speaking)
            } else {
                state
            }
            is ReaderSpeechEvent.RangeStarted ->
                if (state.status == ReaderSpeechStatus.Speaking) {
                    state.copy(characterOffset = event.characterOffset.coerceIn(0, textLength))
                } else {
                    state
                }
            ReaderSpeechEvent.Pause -> if (state.status == ReaderSpeechStatus.Speaking) {
                state.copy(status = ReaderSpeechStatus.Paused)
            } else {
                state
            }
            ReaderSpeechEvent.Stop,
            ReaderSpeechEvent.Completed,
            -> ReaderSpeechState(ReaderSpeechStatus.Ready)
            ReaderSpeechEvent.Close -> ReaderSpeechState(ReaderSpeechStatus.Closed)
        }
    }
}
