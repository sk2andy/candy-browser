package dev.sk2andy.materialbrowser.browser.downloads

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.content.ContextCompat
import dev.sk2andy.materialbrowser.data.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

internal data class DownloadCompletion(
    val fileName: String,
    val mimeType: String,
    val uri: Uri,
)

/** Foreground completion feedback only; private filenames and URIs are never persisted or replayed. */
internal object DownloadCompletionEvents {
    private val streamed = MutableSharedFlow<DownloadCompletion>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun completed(completion: DownloadCompletion) {
        streamed.tryEmit(completion)
    }

    fun observe(context: Context): Flow<DownloadCompletion> = channelFlow {
        val appContext = context.applicationContext
        val repository = DownloadRepository(appContext)
        launch { streamed.collect { send(it) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id < 0L) return
                launch(Dispatchers.IO) {
                    val entry = repository.completedSystemDownload(id) ?: return@launch
                    val uri = runCatching { repository.contentUri(entry) }.getOrNull() ?: return@launch
                    send(DownloadCompletion(entry.name, entry.mime, uri))
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }
}
