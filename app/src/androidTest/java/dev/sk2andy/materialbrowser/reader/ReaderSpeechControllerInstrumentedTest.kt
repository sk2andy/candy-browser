package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSpeechControllerInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val controllers = mutableListOf<ReaderSpeechController>()
    private lateinit var context: IsolatedSpeechContext
    private lateinit var io: ExecutorService
    private lateinit var repository: ReaderSpeechModelRepository
    private lateinit var pocketRepository: PocketSpeechModelRepository

    @Before
    fun setUp() {
        context = IsolatedSpeechContext(instrumentation.targetContext)
        io = Executors.newSingleThreadExecutor()
        onMain {
            repository = ReaderSpeechModelRepository(context, io)
            pocketRepository = PocketSpeechModelRepository(context, io)
        }
        awaitStorage()
    }

    @After
    fun tearDown() {
        onMain { controllers.forEach(ReaderSpeechController::close) }
        io.shutdown()
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        context
            .getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.noBackupFilesDir.deleteRecursively()
    }

    @Test
    fun readerAndSelectedTextShareOneChoiceWithoutStartingBeforeIt() {
        val readerVoices = mutableListOf<RecordingSpeech>()
        val selectedVoices = mutableListOf<RecordingSpeech>()
        onMain {
            val reader = controller(readerVoices)
            val selected = controller(selectedVoices)
            reader.play("Reader article")
            assertTrue(reader.choiceRequested)
            assertTrue(readerVoices.isEmpty())
            reader.chooseSystemVoice()
            assertFalse(reader.choiceRequested)
            assertTrue(repository.state.offered)
            readerVoices.single().initialize()
            assertEquals(listOf("Reader article"), readerVoices.single().spoken)

            selected.play("Selected phrase")
            assertFalse(selected.choiceRequested)
            selectedVoices.single().initialize()
            assertEquals(listOf("Selected phrase"), selectedVoices.single().spoken)
        }
    }

    @Test
    fun queueResumeAndRateSurviveVoiceInitialization() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.setRate(1.5f)
            speech.playFrom("A complete article with a saved listening position.", 0.5f)
            voices.single().initialize()
            assertEquals(1.5f, voices.single().requestedRate)
            assertEquals(0.5f, voices.single().progress, 0.025f)
            assertEquals(
                listOf("A complete article with a saved listening position."),
                voices.single().spoken,
            )
            speech.pause()
            speech.playFrom("A complete article with a saved listening position.", 0.8f)
            assertEquals(0.5f, voices.single().progress, 0.025f)
        }
    }

    @Test
    fun selectedOfflineVoiceNeverSilentlyUsesSystemWhileModelIsMissing() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.setEnabled(true)
            val speech = controller(voices)
            speech.playFrom("An article for the chosen offline voice.", 0.25f)
            assertTrue(speech.choiceRequested)
            assertTrue(voices.isEmpty())
            assertTrue(speech.modelState.enabled)
        }
    }

    @Test
    fun cancelledPlaybackPermissionCannotStartSpeechAfterPause() {
        val voices = mutableListOf<RecordingSpeech>()
        var pendingStart: (() -> Unit)? = null
        onMain {
            repository.chooseSystemVoice()
            val speech =
                ReaderSpeechController(
                        context = context,
                        repository = repository,
                        playbackGate = { pendingStart = it },
                        createAndroidSpeech = { initialized ->
                            RecordingSpeech(initialized).also(voices::add)
                        },
                        createOfflineSpeech = { error("No local model in this test") },
                    )
                    .also(controllers::add)
            speech.playFrom("Do not start after the listening session was paused.", 0.5f)
            voices.single().initialize()
            assertTrue(pendingStart != null)
            speech.pause()
            pendingStart!!.invoke()
            assertTrue(voices.single().spoken.isEmpty())
        }
    }

    @Test
    fun stopDuringSystemInitializationPreventsDelayedPlayback() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.play("Do not read after backgrounding")
            assertEquals(ReaderSpeechStatus.Initializing, speech.state.status)
            speech.stop()
            voices.single().initialize()
            assertTrue(voices.single().spoken.isEmpty())
            assertFalse(speech.choiceRequested)
        }
    }

    @Test
    fun dismissingOrClosingFirstUseClearsPendingTextBeforeModelPreferenceChanges() {
        val dismissedVoices = mutableListOf<RecordingSpeech>()
        val closedVoices = mutableListOf<RecordingSpeech>()
        onMain {
            val dismissed = controller(dismissedVoices)
            val closed = controller(closedVoices)
            dismissed.play("Dismissed private text")
            closed.play("Closed private text")
            dismissed.dismissChoice()
            closed.close()
            repository.chooseSystemVoice()
            assertTrue(dismissedVoices.isEmpty())
            assertTrue(closedVoices.isEmpty())
            assertFalse(dismissed.choiceRequested)
            assertEquals(ReaderSpeechStatus.Closed, closed.state.status)
        }
    }

    @Test
    fun changingVoiceClosesActivePlaybackWithoutRestartingIt() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.play("Active article")
            val system = voices.single()
            system.initialize()
            assertEquals(ReaderSpeechStatus.Speaking, speech.state.status)
            repository.setEnabled(true)
            assertTrue(system.closed)
            assertEquals(ReaderSpeechStatus.Ready, speech.state.status)
            repository.setEnabled(false)
            assertEquals(1, voices.size)
        }
    }

    @Test
    fun initializationFailureOffersRecoveryAndExplicitRetryKeepsArticle() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.play("Retry this article")
            val failed = voices.single()
            failed.fail(initialization = true)
            assertTrue(speech.playbackFailed)
            assertTrue(speech.choiceRequested)
            speech.chooseSystemVoice()
            assertTrue(failed.closed)
            assertFalse(speech.playbackFailed)
            assertFalse(speech.choiceRequested)
            voices.last().initialize()
            assertEquals(listOf("Retry this article"), voices.last().spoken)
        }
    }

    @Test
    fun dismissingPlaybackFailureResetsControlsAndCannotRestartOnPreferenceCallback() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.play("Failed private text")
            val failed = voices.single()
            failed.initialize()
            failed.fail(initialization = false)
            assertTrue(speech.choiceRequested)
            speech.dismissChoice()
            assertTrue(failed.closed)
            assertFalse(speech.playbackFailed)
            assertFalse(speech.choiceRequested)
            assertEquals(ReaderSpeechStatus.Ready, speech.state.status)
            repository.chooseSystemVoice()
            assertEquals(1, voices.size)
        }
    }

    @Test
    fun lateInitializationAfterCloseCannotReplayPendingArticle() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.chooseSystemVoice()
            val speech = controller(voices)
            speech.play("Closed before initialization")
            speech.close()
            voices.single().initialize()
            assertTrue(voices.single().spoken.isEmpty())
            assertEquals(ReaderSpeechStatus.Closed, speech.state.status)
            assertFalse(speech.choiceRequested)
        }
    }

    @Test
    fun voiceChoiceUpdatesPreferencesEvenWhileModelWorkerIsOccupied() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        io.execute {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            onMain {
                repository.setEnabled(true)
                repository.chooseSystemVoice()
                val preferences =
                    context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
                assertTrue(preferences.getBoolean("offered", false))
                assertFalse(preferences.getBoolean("enabled", true))
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun malformedPreferencesRestoreFirstUseInsteadOfBlockingInitialization() {
        context
            .getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
            .edit()
            .putString("offered", "invalid")
            .putString("enabled", "invalid")
            .commit()
        lateinit var restored: ReaderSpeechModelRepository
        onMain { restored = ReaderSpeechModelRepository(context, io) }
        awaitStorage()
        onMain {
            assertTrue(restored.state.loaded)
            assertFalse(restored.state.offered)
            assertFalse(restored.state.enabled)
        }
    }

    @Test
    fun choicePersistsAcrossRepositoryRecreationAndDeleteRemovesPartialAssets() {
        onMain { repository.setEnabled(true) }
        awaitStorage()
        lateinit var restored: ReaderSpeechModelRepository
        onMain { restored = ReaderSpeechModelRepository(context, io) }
        awaitStorage()
        onMain {
            assertTrue(restored.state.offered)
            assertTrue(restored.state.enabled)
            assertFalse(restored.state.ready)
        }
        val modelDirectory = File(context.noBackupFilesDir, "reader-speech-model")
        assertTrue(modelDirectory.mkdirs())
        File(modelDirectory, "interrupted.onnx.part").writeText("partial public model")
        onMain { restored.delete() }
        awaitStorage()
        onMain {
            assertFalse(restored.state.enabled)
            assertFalse(restored.state.ready)
            assertFalse(restored.state.busy)
            assertFalse(restored.state.error)
            assertTrue(restored.state.offered)
        }
        assertFalse(modelDirectory.exists())
        val preferences = context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
        assertFalse(preferences.getBoolean("enabled", true))
        assertTrue(preferences.getBoolean("offered", false))
    }

    @Test
    fun missingUnsupportedAndUnknownPocketLanguagesNeverSilentlySelectAnotherVoice() {
        val voices = mutableListOf<RecordingSpeech>()
        onMain {
            repository.choosePocketVoice()
            for (language in listOf("de", "fr", null)) {
                val speech = controller(voices, identifyLanguage = { language })
                speech.play("An article whose language must match its downloaded voice model.")
                assertTrue(speech.choiceRequested)
                assertEquals(language, speech.pocketLanguage)
                assertTrue(speech.modelState.pocket)
                assertTrue(voices.isEmpty())
                speech.dismissChoice()
            }
        }
    }

    @Test
    fun pauseRejectsNonCancellableLateLanguageDetection() {
        val voices = mutableListOf<RecordingSpeech>()
        lateinit var result: Continuation<String?>
        onMain {
            repository.choosePocketVoice()
            val speech = controller(voices) { suspendCoroutine { result = it } }
            speech.play("Do not resume this article after its language check is paused.")
            assertTrue(speech.identifyingLanguage)
            speech.pause()
            result.resume("de")
            assertFalse(speech.identifyingLanguage)
            assertFalse(speech.choiceRequested)
            assertNull(speech.pocketLanguage)
            assertTrue(voices.isEmpty())
        }
    }

    @Test
    fun closeRejectsNonCancellableLateLanguageDetection() {
        val voices = mutableListOf<RecordingSpeech>()
        lateinit var result: Continuation<String?>
        onMain {
            repository.choosePocketVoice()
            val speech = controller(voices) { suspendCoroutine { result = it } }
            speech.play("Do not retain or replay this private article after closing.")
            assertTrue(speech.identifyingLanguage)
            speech.close()
            result.resume("en")
            assertEquals(ReaderSpeechStatus.Closed, speech.state.status)
            assertFalse(speech.identifyingLanguage)
            assertFalse(speech.choiceRequested)
            assertNull(speech.pocketLanguage)
            assertTrue(voices.isEmpty())
        }
    }

    @Test
    fun changingArticlesRejectsThePreviousLanguageResult() {
        val voices = mutableListOf<RecordingSpeech>()
        val results = mutableListOf<Continuation<String?>>()
        onMain {
            repository.choosePocketVoice()
            val speech = controller(voices) { suspendCoroutine { results += it } }
            speech.play("The first article is replaced while its language is being checked.")
            speech.play("El segundo artículo tiene un idioma diferente y debe utilizar su propio modelo.")
            assertEquals(2, results.size)
            results[0].resume("en")
            assertNull(speech.pocketLanguage)
            assertTrue(speech.identifyingLanguage)
            results[1].resume("es")
            assertEquals("es", speech.pocketLanguage)
            assertFalse(speech.identifyingLanguage)
            assertTrue(speech.choiceRequested)
            assertTrue(voices.isEmpty())
        }
    }

    @Test
    fun choosingSystemDuringLanguageDetectionRejectsItsLateResult() {
        val voices = mutableListOf<RecordingSpeech>()
        lateinit var result: Continuation<String?>
        onMain {
            repository.choosePocketVoice()
            val speech = controller(voices) { suspendCoroutine { result = it } }
            val text = "Use the system voice chosen while the language detection was pending."
            speech.play(text)
            speech.chooseSystemVoice()
            voices.single().initialize()
            result.resume("de")
            assertEquals(listOf(text), voices.single().spoken)
            assertNull(speech.pocketLanguage)
            assertFalse(speech.modelState.pocket)
            assertFalse(speech.identifyingLanguage)
            assertFalse(speech.choiceRequested)
        }
    }

    @Test
    fun pocketChoicePersistsAndDeletingInactiveSupertonicKeepsItSelected() {
        onMain { repository.choosePocketVoice() }
        lateinit var restored: ReaderSpeechModelRepository
        onMain { restored = ReaderSpeechModelRepository(context, io) }
        awaitStorage()
        onMain {
            assertTrue(restored.state.offered)
            assertTrue(restored.state.pocket)
            assertFalse(restored.state.enabled)
            restored.delete()
        }
        awaitStorage()
        onMain {
            assertTrue(restored.state.pocket)
            assertFalse(restored.state.enabled)
            assertFalse(restored.state.ready)
            assertFalse(restored.state.busy)
        }
        assertTrue(context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
            .getBoolean("pocket", false))
    }

    @Test
    fun installedPocketModelsFollowArticleLanguageAndCloseThePreviousVoice() {
        installPocketModelFixtures()
        val voices = mutableListOf<Pair<String, RecordingSpeech>>()
        val german = "Dieser deutsche Artikel verwendet das heruntergeladene deutsche Sprachmodell."
        val english = "This English article uses its own downloaded English speech model."
        onMain {
            repository.choosePocketVoice()
            val speech = pocketController(voices) { if (it == german) "de" else "en" }
            speech.setRate(1.25f)
            speech.playFrom(german, 0.25f)
            assertEquals(listOf("de"), voices.map { it.first })
            assertEquals(listOf(german), voices.single().second.spoken)
            assertEquals(1.25f, voices.single().second.requestedRate)
            assertEquals(0.25f, voices.single().second.progress, 0.02f)
            assertFalse(speech.choiceRequested)

            speech.play(english)
            assertEquals(listOf("de", "en"), voices.map { it.first })
            assertTrue(voices.first().second.closed)
            assertEquals(listOf(english), voices.last().second.spoken)
            assertEquals(0f, voices.last().second.progress, 0f)
            assertFalse(speech.choiceRequested)
            assertEquals(ReaderSpeechStatus.Speaking, speech.state.status)
        }
    }

    @Test
    fun deletingPausedOrStoppedPocketModelCannotResumeAnArticle() {
        installPocketModelFixtures()
        val voices = mutableListOf<Pair<String, RecordingSpeech>>()
        val german = "Dieser deutsche Artikel bleibt nach einer Pause angehalten."
        val english = "This English article stays stopped after its language model is removed."
        lateinit var speech: ReaderSpeechController
        onMain {
            repository.choosePocketVoice()
            speech = pocketController(voices) { if (it == german) "de" else "en" }
            speech.play(german)
            speech.pause()
            assertEquals(ReaderSpeechStatus.Paused, speech.state.status)
            pocketRepository.delete("de")
        }
        awaitStorage()
        onMain {
            assertTrue(voices.single().second.closed)
            assertEquals(listOf(german), voices.single().second.spoken)
            assertFalse(speech.choiceRequested)
            assertEquals(ReaderSpeechStatus.Ready, speech.state.status)
            speech.play(english)
            speech.stop()
            pocketRepository.delete("en")
        }
        awaitStorage()
        onMain {
            assertEquals(listOf("de", "en"), voices.map { it.first })
            assertTrue(voices.last().second.closed)
            assertEquals(listOf(english), voices.last().second.spoken)
            assertFalse(speech.choiceRequested)
            assertEquals(ReaderSpeechStatus.Ready, speech.state.status)
        }
    }

    private fun installPocketModelFixtures() {
        val root = File(context.noBackupFilesDir, "pocket-routing-fixtures")
        val languages = listOf("de", "en").map { code ->
            val model = File(root, "$code/models/model.onnx")
            check(model.parentFile!!.mkdirs())
            model.writeText("abc")
            PocketSpeechLanguage(
                code = code,
                languageTag = if (code == "de") "de-DE" else "en-US",
                voiceId = "fixture",
                archiveName = "$code.zip",
                downloadBytes = 3L,
                sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                files = listOf(PocketSpeechArtifact(
                    name = "models/model.onnx",
                    size = 3L,
                    sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                )),
            )
        }
        onMain { pocketRepository = PocketSpeechModelRepository(context, io, root, languages) }
        awaitStorage()
        onMain { assertTrue(pocketRepository.state.languages.values.all { it.ready }) }
    }

    private fun pocketController(
        voices: MutableList<Pair<String, RecordingSpeech>>,
        identifyLanguage: suspend (String) -> String?,
    ): ReaderSpeechController = ReaderSpeechController(
        context = context,
        repository = repository,
        pocketRepository = pocketRepository,
        identifyLanguage = identifyLanguage,
        createAndroidSpeech = { error("Pocket playback must not use a system voice") },
        createOfflineSpeech = { error("Pocket playback must not use Supertonic") },
        createPocketSpeech = { language ->
            RecordingSpeech {}.also { voice ->
                voice.initialize()
                voices += language to voice
            }
        },
    ).also(controllers::add)

    private fun controller(
        voices: MutableList<RecordingSpeech>,
        identifyLanguage: suspend (String) -> String? = ReaderSpeechLanguageDetector::detect,
    ): ReaderSpeechController =
        ReaderSpeechController(
                context = context,
                repository = repository,
                pocketRepository = pocketRepository,
                identifyLanguage = identifyLanguage,
                createAndroidSpeech = { initialized ->
                    RecordingSpeech(initialized).also(voices::add)
                },
                createOfflineSpeech = { error("No model was downloaded by these tests") },
                createPocketSpeech = { error("No Pocket model was downloaded by these tests") },
            )
            .also(controllers::add)

    private fun awaitStorage() {
        io.submit {}.get(5, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private class RecordingSpeech(private val initialized: (Boolean) -> Unit) :
        ReaderSpeechTransport {
        override var state = ReaderSpeechState()
        val spoken = mutableListOf<String>()
        var closed = false
        var requestedRate = 1f
        var progress = 0f

        fun initialize() {
            state = ReaderSpeechState(ReaderSpeechStatus.Ready)
            initialized(true)
        }

        fun fail(initialization: Boolean) {
            state = ReaderSpeechState(ReaderSpeechStatus.Unavailable)
            if (initialization) initialized(false)
        }

        override fun play(content: String) {
            spoken += content
            state = ReaderSpeechState(ReaderSpeechStatus.Speaking)
        }

        override fun playFrom(content: String, progress: Float) {
            if (state.status == ReaderSpeechStatus.Ready) this.progress = progress
            play(content)
        }

        override fun setRate(rate: Float) {
            this.requestedRate = rate
        }

        override fun pause() {
            state = state.copy(status = ReaderSpeechStatus.Paused)
        }

        override fun stop() {
            state = ReaderSpeechState(ReaderSpeechStatus.Ready)
        }

        override fun close() {
            closed = true
            state = ReaderSpeechState(ReaderSpeechStatus.Closed)
        }
    }

    private class IsolatedSpeechContext(base: Context) : ContextWrapper(base) {
        private val id = UUID.randomUUID().toString()
        private val directory = File(base.noBackupFilesDir, "speech-test-$id")

        override fun getNoBackupFilesDir(): File = directory

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("speech-test-$id-$name", mode)
    }
}
