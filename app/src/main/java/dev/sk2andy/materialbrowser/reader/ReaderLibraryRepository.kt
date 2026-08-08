package dev.sk2andy.materialbrowser.reader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderLibraryRepository private constructor(context: Context) {
    private val store = ReaderLibraryStore(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (operation in operations) {
                try {
                    operation()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Log.e(TAG, "Reader library operation failed", error)
                }
            }
        }
    }

    fun load(isPrivate: Boolean, onLoaded: (ReaderLibraryState) -> Unit) {
        enqueue {
            val state = store.load(isPrivate)
            onMain { onLoaded(state) }
        }
    }

    fun updateSettings(
        settings: ReaderSettings,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        enqueue {
            store.updateSettings(settings, isPrivate)
            val state = store.load(isPrivate)
            onMain { onUpdated(state) }
        }
    }

    fun updateProgress(sourceUrl: String, progress: Float, isPrivate: Boolean) {
        enqueue { store.updateProgress(sourceUrl, progress, isPrivate) }
    }

    fun saveSnapshot(
        document: ReaderDocument,
        progress: Float,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        enqueue {
            store.saveSnapshot(document, progress, isPrivate)
            val state = store.load(isPrivate)
            onMain { onUpdated(state) }
        }
    }

    fun deleteSnapshot(
        snapshotId: String,
        isPrivate: Boolean,
        onUpdated: (ReaderLibraryState) -> Unit,
    ) {
        enqueue {
            store.deleteSnapshot(snapshotId, isPrivate)
            val state = store.load(isPrivate)
            onMain { onUpdated(state) }
        }
    }

    fun awaitIdle(onIdle: () -> Unit) {
        enqueue { onMain(onIdle) }
    }

    internal fun enqueueForTesting(operation: suspend () -> Unit) {
        enqueue(operation)
    }

    private fun enqueue(operation: suspend () -> Unit) {
        check(operations.trySend(operation).isSuccess) { "Reader library writer unavailable" }
    }

    private suspend fun onMain(block: () -> Unit) {
        withContext(Dispatchers.Main.immediate) { block() }
    }

    companion object {
        private const val TAG = "ReaderLibrary"

        @Volatile
        private var instance: ReaderLibraryRepository? = null

        fun get(context: Context): ReaderLibraryRepository = instance ?: synchronized(this) {
            instance ?: ReaderLibraryRepository(context).also { instance = it }
        }
    }
}
