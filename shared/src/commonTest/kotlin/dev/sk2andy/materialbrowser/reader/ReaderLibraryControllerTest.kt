package dev.sk2andy.materialbrowser.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderLibraryControllerTest {
    @Test
    fun `reader preferences progress links and snapshot survive common codec round trip`() {
        val controller = ReaderLibraryController()
        controller.updateSettings(ReaderSettings(fontScale = 1.4f, theme = ReaderTheme.Night), false)
        controller.updateProgress("https://example.com/article", 0.75f, false)
        controller.saveSnapshot(document(), 0.5f, false, 100, snapshotId(1), "work")
        val encoded = controller.persistenceJson()
        val restored = ReaderLibraryController()
        restored.restore(encoded)
        assertEquals(controller.state, restored.state)
        assertEquals("work", restored.state.snapshots.single().sourceProfileId)
        assertEquals("https://example.com/more", restored.state.snapshots.single().document.blocks.single().links.single().url)
    }

    @Test
    fun `existing Android version one schema decodes without platform JSON types`() {
        val legacy = """{
            "version":1,
            "settings":{"fontScale":1.25,"theme":"Paper","textAlignment":"Justified","font":"Serif"},
            "progress":{"https://example.com/article":0.5},
            "snapshots":[{"id":"00000000-0000-4000-8000-000000000001","progress":0.25,"savedAt":100,"document":{
                "title":"Legacy","sourceUrl":"https://example.com/article","siteName":"Example",
                "blocks":[{"kind":"Paragraph","text":"${"Readable article text. ".repeat(6)}","level":0,"links":[]}]
            }}]
        }"""
        val state = ReaderLibraryCodec.decode(legacy)
        assertEquals(1.25f, state.settings.fontScale)
        assertEquals(ReaderFont.Serif, state.settings.font)
        assertEquals(ReaderTextAlignment.Justified, state.settings.textAlignment)
        assertEquals(0.5f, state.progressByUrl["https://example.com/article"])
        assertEquals("Legacy", state.snapshots.single().document.title)
    }

    @Test
    fun `resaving offline article preserves original profile provenance including legacy unknown owner`() {
        val controller = ReaderLibraryController()
        controller.saveSnapshot(document(), 0f, false, 1, snapshotId(1), "protected")
        controller.saveSnapshot(document(), 0.5f, false, 2, snapshotId(2), "public")
        assertEquals("protected", controller.state.snapshots.single().sourceProfileId)
        val legacy = document("https://legacy.example/")
        controller.saveSnapshot(legacy, 0f, false, 3, snapshotId(3), null)
        controller.saveSnapshot(legacy, 0.5f, false, 4, snapshotId(4), "public")
        assertEquals(null, controller.state.snapshots.first().sourceProfileId)
    }

    @Test
    fun `offline fallback matches canonical article identity and respects private boundary`() {
        val controller = ReaderLibraryController()
        controller.saveSnapshot(document(), 0.4f, false, 1, snapshotId(1), "work")
        assertEquals(snapshotId(1), ReaderLibraryRules.snapshotForSourceUrl(
            controller.state, "https://EXAMPLE.com:443/article#section", false,
        )?.id)
        assertEquals(null, ReaderLibraryRules.snapshotForSourceUrl(controller.state, "https://different.example/", false))
        assertEquals(null, ReaderLibraryRules.snapshotForSourceUrl(controller.state, document().sourceUrl, true))
        assertEquals(null, ReaderLibraryRules.snapshotForSourceUrl(controller.state, "file:///private", false))
        val markedPrivate = controller.state.copy(snapshots = controller.state.snapshots.map { it.copy(isPrivate = true) })
        assertEquals(null, ReaderLibraryRules.snapshotForSourceUrl(markedPrivate, document().sourceUrl, false))
    }

    @Test
    fun `private reader neither sees nor changes persisted library`() {
        val controller = ReaderLibraryController()
        controller.saveSnapshot(document(), 0f, false, 100, snapshotId(1), null)
        val before = controller.persistenceJson()
        controller.updateSettings(ReaderSettings(theme = ReaderTheme.Night), true)
        controller.updateProgress("https://private.example/", 0.5f, true)
        controller.saveSnapshot(document("https://private.example/"), 0.2f, true, 200, snapshotId(2), null)
        controller.deleteSnapshot(snapshotId(1), true)
        assertEquals(ReaderLibraryState(), controller.visibleState(true))
        assertEquals(before, controller.persistenceJson())
        assertFalse(before.contains("private.example"))
    }

    @Test
    fun `malformed future and oversize storage fail to empty library`() {
        listOf(null, "{broken", """{"version":42}""", " ".repeat(ReaderLibraryCodec.MAX_JSON_BYTES + 1)).forEach {
            assertEquals(ReaderLibraryState(), ReaderLibraryCodec.decode(it))
        }
    }

    @Test
    fun `private snapshots and untrusted document links are rejected on restore`() {
        val controller = ReaderLibraryController()
        controller.saveSnapshot(document(), 0.5f, false, 100, snapshotId(1), null)
        val privateJson = controller.persistenceJson().replace("\"isPrivate\":false", "\"isPrivate\":true")
        assertTrue(ReaderLibraryCodec.decode(privateJson).snapshots.isEmpty())
        val invalidId = controller.persistenceJson().replace(snapshotId(1), "../not-a-snapshot")
        assertTrue(ReaderLibraryCodec.decode(invalidId).snapshots.isEmpty())
        val unsafeDocument = document().copy(blocks = listOf(ReaderBlock(
            ReaderBlockKind.Paragraph, "Article text ".repeat(10),
            links = listOf(ReaderLink("Unsafe", "javascript:alert(1)")),
        )))
        controller.saveSnapshot(unsafeDocument, 0f, false, 200, snapshotId(2), null)
        assertTrue(controller.state.snapshots.single().document.blocks.single().links.isEmpty())
    }

    @Test
    fun `nonfinite values stay valid and retention bounds match Android`() {
        val controller = ReaderLibraryController()
        controller.updateSettings(ReaderSettings(fontScale = Float.NaN), false)
        controller.updateProgress("https://example.com/", Float.POSITIVE_INFINITY, false)
        assertEquals(1f, controller.state.settings.fontScale)
        assertEquals(0f, controller.state.progressByUrl["https://example.com/"])
        repeat(501) { controller.updateProgress("https://example.com/$it", 2f, false) }
        assertEquals(500, controller.state.progressByUrl.size)
        repeat(21) { index ->
            controller.saveSnapshot(document("https://example.com/$index"), 2f, false, index.toLong(), snapshotId(index), null)
        }
        assertEquals(20, controller.state.snapshots.size)
        assertEquals(1f, controller.state.snapshots.first().progress)
        assertTrue(controller.persistenceJson().isNotEmpty())
    }

    @Test
    fun `persistence budget evicts oldest documents and updates live library`() {
        val controller = ReaderLibraryController()
        val blocks = List(42) { ReaderBlock(ReaderBlockKind.Paragraph, "x".repeat(12_000)) }
        repeat(20) { index ->
            controller.saveSnapshot(document("https://example.com/$index").copy(blocks = blocks), 0f, false, index.toLong(), snapshotId(index), null)
        }
        val encoded = controller.persistenceJson()
        assertTrue(encoded.encodeToByteArray().size <= ReaderLibraryCodec.MAX_JSON_BYTES)
        assertTrue(controller.state.snapshots.size in 1..19)
        assertEquals(snapshotId(19), controller.state.snapshots.first().id)
        assertEquals(controller.state.snapshots.size, ReaderLibraryCodec.decode(encoded).snapshots.size)
    }

    private fun snapshotId(number: Int): String = "00000000-0000-4000-8000-${number.toString().padStart(12, '0')}"

    private fun document(sourceUrl: String = "https://example.com/article"): ReaderDocument = ReaderDocument(
        title = "Article",
        sourceUrl = sourceUrl,
        siteName = "Example",
        blocks = listOf(ReaderBlock(
            kind = ReaderBlockKind.Paragraph,
            text = "Readable article text. ".repeat(6).trim(),
            links = listOf(ReaderLink("More", "https://example.com/more")),
        )),
    )
}
