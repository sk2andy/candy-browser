package dev.sk2andy.materialbrowser.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.SystemClock
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReaderLibraryStoreInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: ReaderLibraryStore

    @Before
    fun setUp() {
        store = ReaderLibraryStore(context)
        store.clear()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun snapshotSettingsAndProgressRoundTrip() {
        val document = document("https://example.com/article")
        store.updateSettings(
            ReaderSettings(1.3f, ReaderTheme.Paper, ReaderTextAlignment.Justified, ReaderFont.Serif),
            isPrivate = false,
        )
        store.updateProgress(document.sourceUrl, 0.42f, isPrivate = false)
        store.saveSnapshot(document, 0.42f, isPrivate = false, nowMillis = 10L)

        val restored = ReaderLibraryStore(context).load(isPrivate = false)

        assertEquals(
            ReaderSettings(1.3f, ReaderTheme.Paper, ReaderTextAlignment.Justified, ReaderFont.Serif),
            restored.settings,
        )
        assertEquals(0.42f, restored.progressByUrl[document.sourceUrl])
        assertEquals(document, restored.snapshots.single().document)
    }

    @Test
    fun absentAndInvalidThemesDefaultToPaperButExplicitLightAndSystemAreRetained() {
        val file = java.io.File(context.filesDir, "reader_library_v1.json")
        listOf("" to ReaderTheme.Paper, "Unknown" to ReaderTheme.Paper,
            "Light" to ReaderTheme.Light, "System" to ReaderTheme.System).forEach { (name, expected) ->
            file.writeText("""{"version":1,"settings":{"theme":"$name"}}""")
            assertEquals(expected, store.load(isPrivate = false).settings.theme)
        }
        store.updateSettings(ReaderSettings(theme = ReaderTheme.Light), isPrivate = false)
        assertEquals(ReaderTheme.Light, ReaderLibraryStore(context).load(isPrivate = false).settings.theme)
    }

    @Test
    fun oldOrUnknownTypefaceKeepsExistingSettingsAndFallsBackToSans() {
        val file = java.io.File(context.filesDir, "reader_library_v1.json")
        listOf("", ", \"font\": \"UnknownFont\"").forEach { fontField ->
            file.writeText("""{"version":1,"settings":{"fontScale":1.2,"theme":"Paper"$fontField}}""")
            val settings = store.load(isPrivate = false).settings
            assertEquals(ReaderFont.Sans, settings.font)
            assertEquals(ReaderTheme.Paper, settings.theme)
            assertEquals(1.2f, settings.fontScale)
        }
    }

    @Test
    fun privateOperationsNeverCreateOrChangeLibrary() {
        val initial = document("https://example.com/normal")
        store.saveSnapshot(initial, 0.2f, isPrivate = false)

        assertNull(store.saveSnapshot(document("https://private.example"), 0.8f, isPrivate = true))
        store.updateSettings(ReaderSettings(1.6f, ReaderTheme.Night, font = ReaderFont.Serif), isPrivate = true)
        store.updateProgress(initial.sourceUrl, 0.9f, isPrivate = true)
        assertTrue(store.load(isPrivate = true).snapshots.isEmpty())

        val normal = store.load(isPrivate = false)
        assertEquals(1, normal.snapshots.size)
        assertEquals(ReaderSettings(), normal.settings)
        assertTrue(normal.progressByUrl.isEmpty())
    }

    @Test
    fun resavingLibraryArticleCannotRelabelProtectedOrUnknownProvenance() {
        val protected = document("https://example.com/protected")
        store.saveSnapshot(protected, 0f, false, sourceProfileId = "protected-profile")
        store.saveSnapshot(protected, 0.5f, false, sourceProfileId = "public-profile", preserveExistingProvenance = true)
        assertEquals("protected-profile", store.load(false).snapshots.single().sourceProfileId)

        val legacy = document("https://example.com/legacy")
        store.saveSnapshot(legacy, 0f, false)
        store.saveSnapshot(legacy, 0.5f, false, sourceProfileId = "public-profile", preserveExistingProvenance = true)
        assertNull(store.load(false).snapshots.first { it.document.sourceUrl == legacy.sourceUrl }.sourceProfileId)
    }

    @Test
    fun repositorySerializesWritesInSubmissionOrder() {
        val repository = ReaderLibraryRepository.get(context)
        val sourceUrl = "https://example.com/ordered"
        val completed = CountDownLatch(1)

        repository.updateProgress(sourceUrl, 0.1f, isPrivate = false)
        repository.updateProgress(sourceUrl, 0.8f, isPrivate = false)
        repository.awaitIdle(completed::countDown)

        assertTrue(completed.await(10, TimeUnit.SECONDS))
        assertEquals(0.8f, ReaderLibraryStore(context).load(false).progressByUrl[sourceUrl])
    }

    @Test
    fun repositoryContinuesAfterFailedOperation() {
        val repository = ReaderLibraryRepository.get(context)
        val completed = CountDownLatch(1)

        repository.enqueueForTesting { error("expected test failure") }
        repository.awaitIdle(completed::countDown)

        assertTrue(completed.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun separateProviderReaderWaitsForInFlightRepositoryWrite() {
        store.saveSnapshot(document("https://example.com/before"), 0f, false)
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val readStarted = CountDownLatch(1)
        val readFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val result = AtomicReference<ReaderLibraryState?>()
        val writer = ReaderLibraryStore(object : AtomicFile(File(context.filesDir, "reader_library_v1.json")) {
            override fun startWrite(): FileOutputStream {
                val output = super.startWrite()
                writeStarted.countDown()
                check(releaseWrite.await(10, TimeUnit.SECONDS))
                return output
            }
        })
        val writing = Thread {
            runCatching { writer.saveSnapshot(document("https://example.com/after"), 0f, false) }
                .onFailure(failure::set)
        }
        val reading = Thread {
            readStarted.countDown()
            runCatching { result.set(ReaderLibraryStore(context).load(false)) }
                .onFailure(failure::set)
            readFinished.countDown()
        }
        writing.start()
        try {
            assertTrue(writeStarted.await(10, TimeUnit.SECONDS))
            reading.start()
            assertTrue(readStarted.await(10, TimeUnit.SECONDS))
            val deadline = SystemClock.elapsedRealtime() + 5_000L
            while (reading.state != Thread.State.BLOCKED && readFinished.count != 0L && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(10L)
            }
            assertEquals("Separate store must not race AtomicFile.openRead against startWrite", Thread.State.BLOCKED, reading.state)
            assertFalse(readFinished.await(1, TimeUnit.MILLISECONDS))
        } finally {
            releaseWrite.countDown()
            writing.join(10_000L)
            reading.join(10_000L)
        }
        failure.get()?.let { throw AssertionError("Concurrent Reader access failed", it) }
        assertEquals(2, result.get()?.snapshots?.size)
        assertEquals(2, ReaderLibraryStore(context).load(false).snapshots.size)
    }

    @Test
    fun workflowSaveRefusesEvictionAndReturnsOnlyPersistedSnapshots() {
        repeat(ReaderLibraryRules.MAX_SNAPSHOTS) { index ->
            store.saveSnapshot(document("https://example.com/$index"), 0f, false, nowMillis = index.toLong())
        }
        val before = store.load(false)
        assertNull(store.saveSnapshot(
            document("https://example.com/overflow"), 0f, false,
            nowMillis = 100L, allowEviction = false,
        ))
        assertEquals(before, store.load(false))
        val replacement = store.saveSnapshot(
            document("https://example.com/0"), 0f, false,
            nowMillis = 101L, allowEviction = false,
        )
        assertTrue(replacement != null)
        assertEquals(ReaderLibraryRules.MAX_SNAPSHOTS, store.load(false).snapshots.size)
        assertTrue(store.load(false).snapshots.contains(replacement))
    }

    private fun document(url: String) = ReaderDocument(
        title = "Stored article",
        sourceUrl = url,
        siteName = "Example",
        blocks = listOf(
            ReaderBlock(
                ReaderBlockKind.Paragraph,
                "Long local article content that safely round trips without executing any extracted markup or script.",
                links = listOf(ReaderLink("Source", "https://example.com/source")),
            ),
        ),
    )
}
