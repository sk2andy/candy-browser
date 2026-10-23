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

internal data class ReaderSpeechModelState(
    val loaded: Boolean = false,
    val offered: Boolean = false,
    val enabled: Boolean = false,
    val ready: Boolean = false,
    val pocket: Boolean = false,
    val busy: Boolean = false,
    val progress: Float? = null,
    val error: Boolean = false,
)

/** Stores only the app-wide voice choice and public model assets, never speech text or audio. */
internal class ReaderSpeechModelRepository(
    context: Context,
    private val io: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "reader-model-files") },
) {
    private val preferences = context.getSharedPreferences("reader_speech_model", Context.MODE_PRIVATE)
    private val files = OfflineSpeechModelFiles(File(context.noBackupFilesDir, "reader-speech-model"))
    private val main = Handler(Looper.getMainLooper())
    private val modelLock = Any()
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    var state by mutableStateOf(ReaderSpeechModelState())
        private set

    init {
        io.execute {
            val offered = preferenceEnabled("offered")
            val enabled = preferenceEnabled("enabled")
            val pocket = preferenceEnabled("pocket")
            val ready = synchronized(modelLock) { files.isReady() }
            main.post {
                update(state.copy(
                    loaded = true,
                    offered = offered,
                    enabled = enabled && !pocket,
                    ready = ready,
                    pocket = pocket,
                ))
            }
        }
    }

    fun addListener(listener: () -> Unit) { listeners += listener }
    fun removeListener(listener: () -> Unit) { listeners -= listener }

    fun chooseSystemVoice() = setEnabled(false)

    fun choosePocketVoice() {
        if (!state.loaded) return
        preferences.edit()
            .putBoolean("offered", true)
            .putBoolean("enabled", false)
            .putBoolean("pocket", true)
            .apply()
        update(state.copy(offered = true, enabled = false, pocket = true, error = false))
    }

    fun setEnabled(enabled: Boolean) {
        if (!state.loaded) return
        preferences.edit()
            .putBoolean("offered", true)
            .putBoolean("enabled", enabled)
            .putBoolean("pocket", false)
            .apply()
        update(state.copy(offered = true, enabled = enabled, pocket = false, error = false))
    }

    fun download() {
        if (!state.loaded || state.busy) return
        setEnabled(true)
        if (state.ready) return
        update(state.copy(busy = true, progress = 0f, error = false))
        io.execute {
            var previousPercent = -1
            val ready = synchronized(modelLock) {
                files.download { progress ->
                    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
                    if (percent != previousPercent) {
                        previousPercent = percent
                        main.post { update(state.copy(progress = percent / 100f)) }
                    }
                }
            }
            main.post { update(state.copy(ready = ready, busy = false, progress = null, error = !ready)) }
        }
    }

    fun delete() {
        if (!state.loaded || state.busy) return
        if (!state.pocket) setEnabled(false)
        update(state.copy(busy = true, ready = false, error = false))
        io.execute {
            val deleted = synchronized(modelLock) { files.delete() }
            val ready = synchronized(modelLock) { files.isReady() }
            main.post { update(state.copy(busy = false, ready = ready, error = !deleted)) }
        }
    }

    /** File deletion waits until the cancelled playback has released its native sessions. */
    fun <T> withEngine(block: (OfflineSpeechEngine) -> T): T = synchronized(modelLock) {
        OfflineSpeechEngine(files).use(block)
    }

    private fun preferenceEnabled(key: String): Boolean = try {
        preferences.getBoolean(key, false)
    } catch (_: ClassCastException) {
        false
    }

    private fun update(value: ReaderSpeechModelState) {
        state = value
        listeners.forEach { it() }
    }

    companion object {
        @Volatile private var instance: ReaderSpeechModelRepository? = null

        fun get(context: Context): ReaderSpeechModelRepository = instance ?: synchronized(this) {
            instance ?: ReaderSpeechModelRepository(context.applicationContext).also { instance = it }
        }
    }
}
