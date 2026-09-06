package dev.sk2andy.materialbrowser.shared.ui

import dev.sk2andy.materialbrowser.reader.ReaderBlock
import dev.sk2andy.materialbrowser.reader.ReaderBlockKind
import dev.sk2andy.materialbrowser.reader.ReaderDocument
import dev.sk2andy.materialbrowser.reader.ReaderLibraryState
import dev.sk2andy.materialbrowser.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionReaderLibraryDataSourceTest {
    @Test
    fun privateMutationsNeverEnterSessionState() {
        val repository = SessionReaderLibraryDataSource()

        repository.updateSettings(
            settings = ReaderSettings(fontScale = 1.4f),
            isPrivate = true,
            onUpdated = {},
        )
        repository.updateProgress(
            sourceUrl = DOCUMENT.sourceUrl,
            progress = 0.6f,
            isPrivate = true,
        )
        repository.saveSnapshot(
            document = DOCUMENT,
            progress = 0.6f,
            isPrivate = true,
            onUpdated = {},
        )

        val state = repository.loadState(isPrivate = false)

        assertEquals(ReaderLibraryState(), state)
    }

    @Test
    fun publicMutationsRemainAvailableForTheComposeSession() {
        val repository = SessionReaderLibraryDataSource()
        val settings = ReaderSettings(fontScale = 1.4f)

        repository.updateSettings(settings, isPrivate = false, onUpdated = {})
        repository.updateProgress(DOCUMENT.sourceUrl, 0.6f, isPrivate = false)
        repository.saveSnapshot(DOCUMENT, 0.6f, isPrivate = false, onUpdated = {})

        val state = repository.loadState(isPrivate = false)

        assertEquals(settings, state.settings)
        assertEquals(0.6f, state.progressByUrl[DOCUMENT.sourceUrl])
        assertEquals(1, state.snapshots.size)
        assertEquals(DOCUMENT, state.snapshots.single().document)
        assertTrue(state.snapshots.single().id.isNotBlank())
    }

    private fun SessionReaderLibraryDataSource.loadState(isPrivate: Boolean): ReaderLibraryState {
        var loaded: ReaderLibraryState? = null
        load(isPrivate) { loaded = it }
        return requireNotNull(loaded)
    }

    private companion object {
        val DOCUMENT = ReaderDocument(
            title = "Candy Reader",
            sourceUrl = "https://example.com/article",
            siteName = "Example",
            blocks = listOf(
                ReaderBlock(
                    kind = ReaderBlockKind.Paragraph,
                    text = "Readable content",
                ),
            ),
        )
    }
}
