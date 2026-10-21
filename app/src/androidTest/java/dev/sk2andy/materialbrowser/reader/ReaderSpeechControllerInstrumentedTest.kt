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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Before
    fun setUp() {
        context = IsolatedSpeechContext(instrumentation.targetContext)
        io = Executors.newSingleThreadExecutor()
        onMain { repository = ReaderSpeechModelRepository(context, io) }
        awaitStorage()
    }

    @After
    fun tearDown() {
        onMain { controllers.forEach(ReaderSpeechController::close) }
        io.shutdown()
        assertTrue(io.awaitTermination(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE).edit().clear().commit()
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
        io.execute { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        try {
            onMain {
                repository.setEnabled(true)
                repository.chooseSystemVoice()
                val preferences = context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
                assertTrue(preferences.getBoolean("offered", false))
                assertFalse(preferences.getBoolean("enabled", true))
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun malformedPreferencesRestoreFirstUseInsteadOfBlockingInitialization() {
        context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE).edit()
            .putString("offered", "invalid").putString("enabled", "invalid").commit()
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

    private fun controller(voices: MutableList<RecordingSpeech>): ReaderSpeechController =
        ReaderSpeechController(
            context = context,
            repository = repository,
            createAndroidSpeech = { initialized -> RecordingSpeech(initialized).also(voices::add) },
            createOfflineSpeech = { error("No model was downloaded by these tests") },
        ).also(controllers::add)

    private fun awaitStorage() {
        io.submit {}.get(5, TimeUnit.SECONDS)
        instrumentation.waitForIdleSync()
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private class RecordingSpeech(private val initialized: (Boolean) -> Unit) : ReaderSpeech {
        override var state = ReaderSpeechState()
        val spoken = mutableListOf<String>()
        var closed = false

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

        override fun pause() { state = state.copy(status = ReaderSpeechStatus.Paused) }
        override fun stop() { state = ReaderSpeechState(ReaderSpeechStatus.Ready) }
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
