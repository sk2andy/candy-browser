package dev.sk2andy.materialbrowser.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

internal data class ReaderSpeechPreparationState(
    val availability: ReaderSpeechPreparationAvailability = ReaderSpeechPreparationAvailability.Checking,
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val downloadedBytes: Long = 0,
    val error: Boolean = false,
    val adultConfirmed: Boolean = false,
)

internal data class ReaderSpeechPreparationResult(
    val prepared: PreparedReaderSpeechText,
    val fallback: Boolean = false,
)

/** Only an opt-in preference persists. Prompts, responses and prepared articles stay in memory. */
internal class ReaderSpeechPreparationRepository(
    context: Context,
    private val createBackend: () -> ReaderSpeechPreparationBackend = ::createReaderSpeechPreparationBackend,
    private val isForeground: () -> Boolean = {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val preparationTimeoutMillis: Long = 6_000,
) : AutoCloseable {
    private val preferences = context.getSharedPreferences("reader_speech_preparation", Context.MODE_PRIVATE)
    private val backendLock = Mutex()
    private var statusJob: Job? = null
    private var downloadJob: Job? = null
    private var closed = false
    private var preferenceGeneration = 0L
    private var downloadGeneration = 0L

    val choiceRevision: Long get() = preferenceGeneration

    var state by mutableStateOf(ReaderSpeechPreparationState(
        enabled = storedBoolean("enabled") && storedBoolean("adult_confirmed"),
        adultConfirmed = storedBoolean("adult_confirmed"),
    ))
        private set

    fun confirmAdult() {
        if (closed) return
        preferences.edit().putBoolean("adult_confirmed", true).apply()
        state = state.copy(adultConfirmed = true)
    }

    fun refresh() {
        if (closed || statusJob?.isActive == true || downloadJob?.isActive == true) return
        statusJob = scope.launch {
            try {
                val availability = withTimeoutOrNull(5_000) {
                    backendLock.withLock { createBackend().use { it.availability() } }
                } ?: ReaderSpeechPreparationAvailability.Unavailable
                currentCoroutineContext().ensureActive()
                if (closed) return@launch
                state = state.copy(availability = availability)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (closed) return@launch
                state = state.copy(availability = ReaderSpeechPreparationAvailability.Unavailable)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (closed || (enabled && (!state.adultConfirmed ||
                state.availability != ReaderSpeechPreparationAvailability.Available))
        ) return
        preferenceGeneration++
        preferences.edit().putBoolean("enabled", enabled).apply()
        state = state.copy(enabled = enabled, error = false)
    }

    /** Android owns the shared model. Candy can request download, but cannot delete its files. */
    fun download() {
        if (closed || !state.adultConfirmed || downloadJob?.isActive == true) return
        if (state.availability !in setOf(
                ReaderSpeechPreparationAvailability.Downloadable,
                ReaderSpeechPreparationAvailability.Downloading,
            )
        ) return
        statusJob?.cancel()
        val expectedPreference = preferenceGeneration
        val expectedDownload = ++downloadGeneration
        state = state.copy(
            availability = ReaderSpeechPreparationAvailability.Downloading,
            busy = true,
            downloadedBytes = 0,
            error = false,
        )
        downloadJob = scope.launch {
            try {
                val availability = backendLock.withLock {
                    createBackend().use { backend ->
                        backend.download { bytes ->
                            if (!closed && expectedDownload == downloadGeneration) {
                                state = state.copy(
                                    availability = ReaderSpeechPreparationAvailability.Downloading,
                                    downloadedBytes = bytes.coerceAtLeast(0),
                                )
                            }
                        }
                        backend.availability()
                    }
                }
                currentCoroutineContext().ensureActive()
                if (closed || expectedDownload != downloadGeneration) return@launch
                state = state.copy(
                    availability = availability,
                    busy = false,
                    error = availability != ReaderSpeechPreparationAvailability.Available,
                )
                if (expectedPreference == preferenceGeneration &&
                    availability == ReaderSpeechPreparationAvailability.Available
                ) setEnabled(true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (closed || expectedDownload != downloadGeneration) return@launch
                state = state.copy(
                    availability = ReaderSpeechPreparationAvailability.Downloadable,
                    busy = false,
                    error = true,
                )
            }
        }
    }

    fun cancelDownload() {
        if (closed) return
        downloadGeneration++
        preferenceGeneration++
        downloadJob?.cancel()
        downloadJob = null
        state = state.copy(busy = false, downloadedBytes = 0, error = false)
        // Cancels Candy's request; another app may still be downloading the shared system model.
        refresh()
    }

    suspend fun prepare(text: String): ReaderSpeechPreparationResult {
        val original = ReaderSpeechPreparationRules.unchanged(text)
        if (closed || !state.enabled) return ReaderSpeechPreparationResult(original)
        val expectedPreference = preferenceGeneration
        if (!isForeground() || state.busy) return ReaderSpeechPreparationResult(original, fallback = true)
        val plan = withContext(Dispatchers.Default) { ReaderSpeechPreparationRules.plan(text) }
        if (plan.candidates.isEmpty()) return ReaderSpeechPreparationResult(original)
        val prepared = try {
            withTimeoutOrNull(preparationTimeoutMillis) {
                backendLock.withLock {
                    if (!isForeground()) return@withLock null
                    createBackend().use { backend ->
                        if (backend.availability() != ReaderSpeechPreparationAvailability.Available) {
                            return@use null
                        }
                        val response = backend.generate(plan.prompt) ?: return@use null
                        withContext(Dispatchers.Default) { ReaderSpeechPreparationRules.tryApply(plan, response) }
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (closed || !state.enabled || expectedPreference != preferenceGeneration) {
            return ReaderSpeechPreparationResult(original)
        }
        return ReaderSpeechPreparationResult(prepared ?: original, fallback = prepared == null)
    }

    override fun close() {
        closed = true
        downloadGeneration++
        scope.cancel()
    }

    private fun storedBoolean(key: String): Boolean = try {
        preferences.getBoolean(key, false)
    } catch (_: ClassCastException) {
        false
    }

    companion object {
        @Volatile private var instance: ReaderSpeechPreparationRepository? = null

        fun get(context: Context): ReaderSpeechPreparationRepository = instance ?: synchronized(this) {
            instance ?: ReaderSpeechPreparationRepository(context.applicationContext).also { instance = it }
        }
    }
}
