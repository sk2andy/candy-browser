package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSpeechPreparationInstrumentedTest {
    private lateinit var context: IsolatedPreparationContext
    private lateinit var modelIo: ExecutorService
    private val repositories = mutableListOf<ReaderSpeechPreparationRepository>()
    private val controllers = mutableListOf<ReaderSpeechController>()

    @Before
    fun setUp() {
        context = IsolatedPreparationContext(InstrumentationRegistry.getInstrumentation().targetContext)
        modelIo = Executors.newSingleThreadExecutor()
    }

    @After
    fun tearDown() = runBlocking {
        withContext(Dispatchers.Main) {
            controllers.forEach(ReaderSpeechController::close)
            repositories.forEach(ReaderSpeechPreparationRepository::close)
        }
        modelIo.shutdown()
        assertTrue(modelIo.awaitTermination(5, TimeUnit.SECONDS))
        context.clearPreferences()
        context.noBackupFilesDir.deleteRecursively()
        Unit
    }

    @Test
    fun platformAvailabilityCheckNeverOptsInOrDownloads() = runBlocking {
        val current = withContext(Dispatchers.Main) {
            ReaderSpeechPreparationRepository(context).also {
                repositories += it
                it.refresh()
            }
        }
        withTimeout(10_000) {
            withContext(Dispatchers.Main) {
                while (current.state.availability == ReaderSpeechPreparationAvailability.Checking) delay(20)
                assertFalse(current.state.enabled)
                assertFalse(current.state.adultConfirmed)
                assertFalse(current.state.busy)
                assertEquals(0L, current.state.downloadedBytes)
            }
        }
    }

    @Test
    fun disabledPreparationNeverCreatesAnInferenceOrDownload() = runBlocking {
        val backend = BackendState()
        withContext(Dispatchers.Main) {
            val repository = repository(backend)
            repository.refresh()
            assertEquals(ReaderSpeechPreparationAvailability.Available, repository.state.availability)
            assertFalse(repository.state.enabled)
            val result = repository.prepare("Dr. Müller liest.")
            assertEquals("Dr. Müller liest.", result.prepared.text)
            assertFalse(result.fallback)
            assertEquals(0, backend.generations)
            assertEquals(0, backend.downloads)
        }
    }

    @Test
    fun adultConfirmationGuardsActivationAndDownloadAndPersists() = runBlocking {
        val backend = BackendState()
        withContext(Dispatchers.Main) {
            val current = repository(backend)
            current.refresh()
            current.setEnabled(true)
            assertFalse(current.state.enabled)
            backend.availability = ReaderSpeechPreparationAvailability.Downloadable
            current.refresh()
            current.download()
            assertEquals(0, backend.downloads)
            current.confirmAdult()
            assertFalse(current.state.enabled)
            val restored = repository(backend)
            assertTrue(restored.state.adultConfirmed)
            assertFalse(restored.state.enabled)
            restored.refresh()
            restored.download()
            assertTrue(restored.state.enabled)
            assertEquals(1, backend.downloads)
        }
    }

    @Test
    fun supportedModelDownloadsOnlyAfterExplicitActionAndThenEnables() = runBlocking {
        val backend = BackendState(availability = ReaderSpeechPreparationAvailability.Downloadable)
        backend.downloadGate = CompletableDeferred()
        val repository = withContext(Dispatchers.Main) {
            repository(backend).also {
                it.refresh()
                assertEquals(0, backend.downloads)
                assertFalse(it.state.enabled)
                it.setEnabled(true)
                assertFalse(it.state.enabled)
                it.confirmAdult()
                it.download()
                assertEquals(ReaderSpeechPreparationAvailability.Downloading, it.state.availability)
                assertTrue(it.state.busy)
                assertEquals(1234L, it.state.downloadedBytes)
                assertFalse(it.state.enabled)
            }
        }
        withContext(Dispatchers.Main) { backend.downloadGate?.complete(Unit) }
        withTimeout(5_000) { backend.downloadFinished.await() }
        withContext(Dispatchers.Main) {
            yield()
            assertTrue(repository.state.enabled)
            assertFalse(repository.state.busy)
            assertEquals(ReaderSpeechPreparationAvailability.Available, repository.state.availability)
            assertEquals(1, backend.downloads)
            assertEquals(0, backend.generations)
            assertEquals(setOf("enabled", "adult_confirmed"), context.preferenceKeys("reader_speech_preparation"))
        }
    }

    @Test
    fun failedDownloadRemainsDisabledAndCanBeRetried() = runBlocking {
        val backend = BackendState(availability = ReaderSpeechPreparationAvailability.Downloadable)
        backend.downloadFailure = true
        withContext(Dispatchers.Main) {
            val repository = repository(backend)
            repository.refresh()
            repository.confirmAdult()
            repository.download()
            assertTrue(repository.state.error)
            assertFalse(repository.state.enabled)
            assertFalse(repository.state.busy)
            assertEquals(ReaderSpeechPreparationAvailability.Downloadable, repository.state.availability)
            backend.downloadFailure = false
            repository.download()
            assertTrue(repository.state.enabled)
            assertFalse(repository.state.error)
            assertEquals(2, backend.downloads)
        }
    }

    @Test
    fun cancellingDownloadPreventsLateCompletionFromEnablingNano() = runBlocking {
        val backend = BackendState(availability = ReaderSpeechPreparationAvailability.Downloadable)
        backend.downloadGate = CompletableDeferred()
        backend.finishDownloadAfterCancellation = true
        val repository = withContext(Dispatchers.Main) {
            repository(backend).also {
                it.refresh()
                it.confirmAdult()
                it.download()
                it.cancelDownload()
                assertFalse(it.state.enabled)
                assertFalse(it.state.busy)
                backend.downloadGate?.complete(Unit)
            }
        }
        withTimeout(5_000) { backend.downloadFinished.await() }
        withContext(Dispatchers.Main) {
            yield()
            assertFalse(repository.state.enabled)
            assertEquals(0, backend.generations)
        }
    }

    @Test
    fun cancelledDownloadFailureCannotOverwriteNewAvailability() = runBlocking {
        val backend = BackendState(availability = ReaderSpeechPreparationAvailability.Downloadable)
        backend.downloadGate = CompletableDeferred()
        backend.finishDownloadAfterCancellation = true
        backend.lateDownloadFailure = true
        val repository = withContext(Dispatchers.Main) {
            repository(backend).also {
                it.refresh()
                it.confirmAdult()
                it.download()
                it.cancelDownload()
                backend.downloadGate?.complete(Unit)
            }
        }
        withTimeout(5_000) { backend.downloadFinished.await() }
        withContext(Dispatchers.Main) {
            yield()
            assertFalse(repository.state.error)
            assertFalse(repository.state.enabled)
            assertFalse(repository.state.busy)
        }
    }

    @Test
    fun unavailableBackendFallsBackWithoutGeneratingText() = runBlocking {
        val backend = BackendState()
        val repository = enabledRepository(backend)
        withContext(Dispatchers.Main) {
            backend.availability = ReaderSpeechPreparationAvailability.Unavailable
            val result = repository.prepare("Dr. Müller liest.")
            assertEquals("Dr. Müller liest.", result.prepared.text)
            assertTrue(result.fallback)
            assertEquals(0, backend.generations)
        }
    }

    @Test
    fun generationErrorFallsBackToOriginalText() = runBlocking {
        val backend = BackendState()
        backend.generationFailure = true
        val repository = enabledRepository(backend)
        withContext(Dispatchers.Main) {
            val result = repository.prepare("Dr. Müller liest.")
            assertEquals("Dr. Müller liest.", result.prepared.text)
            assertTrue(result.fallback)
            assertEquals(1, backend.generations)
        }
    }

    @Test
    fun timeoutReturnsOriginalTextAndReleasesBackend() = runBlocking {
        val backend = BackendState()
        backend.generationGate = CompletableDeferred()
        val repository = enabledRepository(backend, timeoutMillis = 100)
        withContext(Dispatchers.Main) {
            val result = withTimeout(5_000) { repository.prepare("Dr. Müller liest.") }
            assertEquals("Dr. Müller liest.", result.prepared.text)
            assertTrue(result.fallback)
            assertTrue(backend.generationFinished.isCompleted)
            assertTrue(backend.closedBackends >= 2)
        }
    }

    @Test
    fun backgroundPreparationDoesNotInvokeNano() = runBlocking {
        val backend = BackendState()
        val repository = withContext(Dispatchers.Main) {
            repository(backend, foreground = false).also { it.refresh(); it.confirmAdult(); it.setEnabled(true) }
        }
        withContext(Dispatchers.Main) {
            val result = repository.prepare("Dr. Müller liest.")
            assertEquals("Dr. Müller liest.", result.prepared.text)
            assertTrue(result.fallback)
            assertEquals(0, backend.generations)
        }
    }

    @Test
    fun disablingDuringPreparationDiscardsLateResponse() = runBlocking {
        val backend = BackendState()
        backend.generationGate = CompletableDeferred()
        val repository = enabledRepository(backend)
        val result = async(Dispatchers.Main) { repository.prepare("Dr. Müller liest.") }
        withTimeout(5_000) { backend.generationStarted.await() }
        withContext(Dispatchers.Main) {
            repository.setEnabled(false)
            backend.generationGate?.complete("0:1")
        }
        assertEquals("Dr. Müller liest.", withTimeout(5_000) { result.await() }.prepared.text)
        withContext(Dispatchers.Main) { assertFalse(repository.state.enabled) }
    }

    @Test
    fun cancellingPreparationPropagatesCancellation() = runBlocking {
        val backend = BackendState()
        backend.generationGate = CompletableDeferred()
        val repository = enabledRepository(backend)
        val result = async(Dispatchers.Main) { repository.prepare("Dr. Müller liest.") }
        withTimeout(5_000) { backend.generationStarted.await() }
        result.cancel()
        withTimeout(5_000) { result.join() }
        assertTrue(result.isCancelled)
        assertTrue(backend.generationFinished.isCompleted)
    }

    @Test
    fun stopWhilePreparationPendingCannotStartLateSpeech() = runBlocking {
        val backend = BackendState()
        backend.generationGate = CompletableDeferred()
        backend.finishGenerationAfterCancellation = true
        val preparation = enabledRepository(backend)
        val fixture = controller(preparation)
        withContext(Dispatchers.Main) {
            fixture.controller.play("Dr. Müller liest.")
            fixture.voice.initialize()
        }
        withTimeout(5_000) { backend.generationStarted.await() }
        withContext(Dispatchers.Main) {
            assertTrue(fixture.controller.preparingText)
            fixture.controller.stop()
            backend.generationGate?.complete("0:1")
        }
        withTimeout(5_000) { backend.generationFinished.await() }
        withContext(Dispatchers.Main) {
            yield()
            assertFalse(fixture.controller.preparingText)
            assertEquals(ReaderSpeechStatus.Ready, fixture.controller.state.status)
            assertTrue(fixture.voice.spoken.isEmpty())
        }
    }

    @Test
    fun closeWhilePreparationPendingCannotStartLateSpeech() = runBlocking {
        val backend = BackendState()
        backend.generationGate = CompletableDeferred()
        backend.finishGenerationAfterCancellation = true
        val preparation = enabledRepository(backend)
        val fixture = controller(preparation)
        withContext(Dispatchers.Main) {
            fixture.controller.play("Dr. Müller liest.")
            fixture.voice.initialize()
        }
        withTimeout(5_000) { backend.generationStarted.await() }
        withContext(Dispatchers.Main) {
            fixture.controller.close()
            backend.generationGate?.complete("0:1")
        }
        withTimeout(5_000) { backend.generationFinished.await() }
        withContext(Dispatchers.Main) {
            yield()
            assertEquals(ReaderSpeechStatus.Closed, fixture.controller.state.status)
            assertTrue(fixture.voice.closed)
            assertTrue(fixture.voice.spoken.isEmpty())
        }
    }

    @Test
    fun preparedPlaybackMapsProgressAndReusesPreparationOnResume() = runBlocking {
        val backend = BackendState()
        val preparation = enabledRepository(backend)
        val fixture = controller(preparation)
        withContext(Dispatchers.Main) {
            fixture.controller.play("Dr. Müller liest.")
            fixture.voice.initialize()
        }
        assertEquals("Doktor Müller liest.", withTimeout(5_000) { fixture.voice.played.receive() })
        withContext(Dispatchers.Main) {
            fixture.voice.state = ReaderSpeechState(ReaderSpeechStatus.Speaking, characterOffset = 7)
            assertEquals(4, fixture.controller.state.characterOffset)
            fixture.controller.pause()
            assertEquals(ReaderSpeechStatus.Paused, fixture.controller.state.status)
            fixture.controller.play("Dr. Müller liest.")
            assertEquals(1, backend.generations)
            assertEquals(4, fixture.controller.state.characterOffset)
        }
        assertEquals("Doktor Müller liest.", withTimeout(5_000) { fixture.voice.played.receive() })
    }

    @Test
    fun changingOptInInvalidatesPreparedPlaybackCache() = runBlocking {
        val backend = BackendState()
        val preparation = enabledRepository(backend)
        val fixture = controller(preparation)
        withContext(Dispatchers.Main) {
            fixture.controller.play("Dr. Müller liest.")
            fixture.voice.initialize()
        }
        assertEquals("Doktor Müller liest.", withTimeout(5_000) { fixture.voice.played.receive() })
        withContext(Dispatchers.Main) {
            fixture.controller.pause()
            preparation.setEnabled(false)
            fixture.controller.play("Dr. Müller liest.")
        }
        assertEquals("Dr. Müller liest.", withTimeout(5_000) { fixture.voice.played.receive() })
        withContext(Dispatchers.Main) {
            assertEquals(1, backend.generations)
            fixture.controller.pause()
            preparation.setEnabled(true)
            fixture.controller.play("Dr. Müller liest.")
        }
        assertEquals("Doktor Müller liest.", withTimeout(5_000) { fixture.voice.played.receive() })
        withContext(Dispatchers.Main) { assertEquals(2, backend.generations) }
    }

    private fun repository(
        backend: BackendState,
        timeoutMillis: Long = 6_000,
        foreground: Boolean = true,
    ): ReaderSpeechPreparationRepository = ReaderSpeechPreparationRepository(
        context,
        createBackend = { FakeBackend(backend) },
        isForeground = { foreground },
        preparationTimeoutMillis = timeoutMillis,
    ).also(repositories::add)

    private suspend fun enabledRepository(
        backend: BackendState,
        timeoutMillis: Long = 6_000,
    ): ReaderSpeechPreparationRepository = withContext(Dispatchers.Main) {
        repository(backend, timeoutMillis).also { it.refresh(); it.confirmAdult(); it.setEnabled(true) }
    }

    private suspend fun controller(preparation: ReaderSpeechPreparationRepository): ControllerFixture {
        val model = withContext(Dispatchers.Main) { ReaderSpeechModelRepository(context, modelIo) }
        modelIo.submit {}.get(5, TimeUnit.SECONDS)
        return withContext(Dispatchers.Main) {
            model.chooseSystemVoice()
            lateinit var voice: RecordingSpeech
            val controller = ReaderSpeechController(
                context = context,
                repository = model,
                createAndroidSpeech = { initialized ->
                    RecordingSpeech(initialized).also { voice = it }
                },
                createOfflineSpeech = { error("These tests do not download speech models") },
                preparation = preparation,
            ).also(controllers::add)
            // Create the Android voice without starting inference until its initialization callback.
            controller.play("Dr. Müller liest.")
            ControllerFixture(controller, voice)
        }
    }

    private data class ControllerFixture(val controller: ReaderSpeechController, val voice: RecordingSpeech)

    private class RecordingSpeech(private val initialized: (Boolean) -> Unit) : ReaderSpeech {
        override var state = ReaderSpeechState()
        val spoken = mutableListOf<String>()
        val played = Channel<String>(Channel.UNLIMITED)
        var closed = false

        fun initialize() {
            state = ReaderSpeechState(ReaderSpeechStatus.Ready)
            initialized(true)
        }

        override fun play(content: String) {
            spoken += content
            state = state.copy(status = ReaderSpeechStatus.Speaking)
            played.trySend(content)
        }

        override fun pause() { state = state.copy(status = ReaderSpeechStatus.Paused) }
        override fun stop() { state = ReaderSpeechState(ReaderSpeechStatus.Ready) }
        override fun close() {
            closed = true
            state = ReaderSpeechState(ReaderSpeechStatus.Closed)
        }
    }

    private class BackendState(
        var availability: ReaderSpeechPreparationAvailability = ReaderSpeechPreparationAvailability.Available,
    ) {
        var generations = 0
        var downloads = 0
        var closedBackends = 0
        var generationFailure = false
        var downloadFailure = false
        var finishGenerationAfterCancellation = false
        var finishDownloadAfterCancellation = false
        var lateDownloadFailure = false
        var generationGate: CompletableDeferred<String?>? = null
        var downloadGate: CompletableDeferred<Unit>? = null
        val generationStarted = CompletableDeferred<Unit>()
        val generationFinished = CompletableDeferred<Unit>()
        val downloadFinished = CompletableDeferred<Unit>()
    }

    private class FakeBackend(private val state: BackendState) : ReaderSpeechPreparationBackend {
        override suspend fun availability(): ReaderSpeechPreparationAvailability = state.availability

        override suspend fun download(onProgress: (Long) -> Unit) {
            state.downloads++
            if (state.downloadFailure) throw IOException("Fake unavailable download")
            onProgress(1234)
            if (state.finishDownloadAfterCancellation) {
                withContext(NonCancellable) {
                    state.downloadGate?.await()
                    state.availability = ReaderSpeechPreparationAvailability.Available
                    state.downloadFinished.complete(Unit)
                    if (state.lateDownloadFailure) throw IOException("Late cancelled download failure")
                }
            } else {
                state.downloadGate?.await()
                state.availability = ReaderSpeechPreparationAvailability.Available
                state.downloadFinished.complete(Unit)
            }
        }

        override suspend fun generate(prompt: String): String? {
            state.generations++
            state.generationStarted.complete(Unit)
            try {
                if (state.generationFailure) throw IOException("Fake unavailable inference")
                return if (state.finishGenerationAfterCancellation) {
                    withContext(NonCancellable) { state.generationGate?.await() ?: "0:1" }
                } else {
                    state.generationGate?.await() ?: "0:1"
                }
            } finally {
                state.generationFinished.complete(Unit)
            }
        }

        override fun close() { state.closedBackends++ }
    }

    private class IsolatedPreparationContext(base: Context) : ContextWrapper(base) {
        private val id = UUID.randomUUID().toString()
        private val preferenceNames = mutableSetOf<String>()
        private val directory = File(base.noBackupFilesDir, "nano-test-$id")

        override fun getNoBackupFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            preferenceNames += name
            return super.getSharedPreferences("nano-test-$id-$name", mode)
        }

        fun preferenceKeys(name: String): Set<String> = getSharedPreferences(name, MODE_PRIVATE).all.keys

        fun clearPreferences() {
            preferenceNames.toList().forEach { getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit() }
        }
    }
}
