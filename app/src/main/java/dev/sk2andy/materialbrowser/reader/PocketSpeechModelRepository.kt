package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal data class PocketSpeechLanguageState(
    val ready: Boolean = false,
    val busy: Boolean = false,
    val progress: Float? = null,
    val error: Boolean = false,
)

internal data class PocketSpeechModelState(
    val loaded: Boolean = false,
    val languages: Map<String, PocketSpeechLanguageState> = emptyMap(),
)

/** Owns only downloaded public models. The voice choice stays in ReaderSpeechModelRepository. */
internal class PocketSpeechModelRepository(
    context: Context,
    private val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "pocket-model-files") },
    private val directory: File = File(context.noBackupFilesDir, "pocket-speech-models"),
    private val languages: List<PocketSpeechLanguage> = PocketSpeechCatalog.languages,
) {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val catalog = languages.associateBy(PocketSpeechLanguage::code)
    private val locks = languages.associate { it.code to Any() }
    private val operations = mutableMapOf<String, PocketSpeechDownloadCancellation>()

    var state by mutableStateOf(PocketSpeechModelState())
        private set

    init {
        io.execute {
            val restored = languages.associate { language ->
                language.code to synchronized(lock(language.code)) {
                    val model = files(language)
                    val cleaned = model.clearIncomplete()
                    PocketSpeechLanguageState(ready = model.isReady(), error = !cleaned)
                }
            }
            main.post { update(PocketSpeechModelState(loaded = true, languages = restored)) }
        }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    fun download(code: String) {
        val language = catalog[code] ?: return
        val current = state.languages[code] ?: return
        if (!state.loaded || current.busy || current.ready) return
        val operation = PocketSpeechDownloadCancellation()
        operations[code] = operation
        updateLanguage(code, current.copy(busy = true, progress = 0f, error = false))
        io.execute {
            var previousPercent = -1
            val ready = synchronized(lock(code)) {
                files(language).download(operation) { progress ->
                    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
                    if (percent != previousPercent) {
                        previousPercent = percent
                        main.post {
                            if (operations[code] === operation && !operation.isCancelled) {
                                updateLanguage(code, state.languages.getValue(code).copy(progress = percent / 100f))
                            }
                        }
                    }
                }
            }
            main.post {
                if (operations[code] === operation) {
                    operations.remove(code)
                    updateLanguage(code, PocketSpeechLanguageState(ready = ready, error = !ready && !operation.isCancelled))
                }
            }
        }
    }

    fun cancelDownload(code: String) {
        val current = state.languages[code] ?: return
        if (current.progress == null) return
        val operation = operations[code] ?: return
        operation.cancel()
        // Retain identity until cleanup completes, so a late successful install stays visible.
        updateLanguage(code, current.copy(busy = false, progress = null, error = false))
    }

    fun delete(code: String) {
        val language = catalog[code] ?: return
        if (!state.loaded) return
        operations.remove(code)?.cancel()
        val operation = PocketSpeechDownloadCancellation()
        operations[code] = operation
        // Publish first: active clients stop playback before deletion waits for native sessions.
        updateLanguage(code, PocketSpeechLanguageState(busy = true))
        io.execute {
            val result = synchronized(lock(code)) {
                val model = files(language)
                val deleted = model.delete()
                deleted to model.isReady()
            }
            main.post {
                if (operations[code] === operation) {
                    operations.remove(code)
                    updateLanguage(code, PocketSpeechLanguageState(ready = result.second, error = !result.first))
                }
            }
        }
    }

    /** Keep native session creation, inference and close inside this block. */
    fun <T> withModel(code: String, block: (File) -> T): T {
        val language = requireNotNull(catalog[code]) { "Unsupported Pocket speech language" }
        return synchronized(lock(code)) {
            check(files(language).isReady()) { "Pocket speech model is unavailable" }
            block(File(directory, code))
        }
    }

    private fun lock(code: String): Any = locks.getValue(code)

    private fun files(language: PocketSpeechLanguage): PocketSpeechModelFiles =
        PocketSpeechModelFiles(File(directory, language.code), language)

    private fun updateLanguage(code: String, value: PocketSpeechLanguageState) {
        update(state.copy(languages = state.languages + (code to value)))
    }

    private fun update(value: PocketSpeechModelState) {
        state = value
        listeners.forEach { it() }
    }

    companion object {
        @Volatile private var instance: PocketSpeechModelRepository? = null

        fun get(context: Context): PocketSpeechModelRepository = instance ?: synchronized(this) {
            instance ?: PocketSpeechModelRepository(context.applicationContext).also { instance = it }
        }
    }
}
