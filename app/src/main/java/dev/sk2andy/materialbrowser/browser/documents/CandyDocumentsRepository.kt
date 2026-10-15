package dev.sk2andy.materialbrowser.browser.documents

import android.content.Context
import android.net.Uri
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.data.AppDataTransferLock
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.DownloadEntry
import dev.sk2andy.materialbrowser.data.DownloadRepository
import dev.sk2andy.materialbrowser.data.DownloadStatus
import dev.sk2andy.materialbrowser.reader.ReaderLibraryStore
import dev.sk2andy.materialbrowser.reader.ReaderSnapshot

/** Revalidates current profile ownership for every picker query and open, including old grants. */
internal class CandyDocumentsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val downloads = DownloadRepository(appContext)

    fun articles(): List<ReaderSnapshot> {
        val allowed = allowedProfiles()
        return ReaderLibraryStore(appContext).load(isPrivate = false).snapshots.filter { snapshot ->
            !snapshot.isPrivate && snapshot.sourceProfileId in allowed
        }
    }

    fun downloads(): List<DownloadEntry> {
        val allowed = allowedProfiles()
        return downloads.snapshot().filter { entry ->
            !entry.isPrivate && entry.sourceProfileId in allowed && entry.status == DownloadStatus.Successful &&
                downloadUri(entry)?.let(::isReadable) == true
        }
    }

    fun downloadUri(entry: DownloadEntry): Uri? = runCatching { downloads.contentUri(entry) }.getOrNull()
        ?.takeIf { uri -> uri.scheme == "content" && uri.authority in setOf("media", "downloads") }

    private fun allowedProfiles(): Set<String> {
        if (AppDataTransferLock.isActive(appContext)) return emptySet()
        return BrowserSessionStore(appContext).loadProfiles().first
            .filter { !it.isSynced && it.protection == null }
            .mapTo(hashSetOf()) { it.id }
    }

    private fun isReadable(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
