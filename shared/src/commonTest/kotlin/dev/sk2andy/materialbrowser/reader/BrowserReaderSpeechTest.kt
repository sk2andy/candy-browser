package dev.sk2andy.materialbrowser.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserReaderSpeechTest {
    @Test
    fun closingRejectsLateNativeStateAndFurtherCommands() {
        val calls = mutableListOf<String>()
        val speech = BrowserReaderSpeech(
            onPlay = { calls += it },
            onPause = { calls += "pause" },
            onStop = { calls += "stop" },
            onClose = { calls += "close" },
        )
        speech.updateState(ReaderSpeechState(ReaderSpeechStatus.Ready))
        speech.play("Article")
        speech.close()
        speech.updateState(ReaderSpeechState(ReaderSpeechStatus.Speaking, 12))
        speech.play("Late article")
        speech.pause()
        speech.stop()
        speech.close()

        assertEquals(listOf("Article", "close"), calls)
        assertEquals(ReaderSpeechState(ReaderSpeechStatus.Closed), speech.state)
    }
}
