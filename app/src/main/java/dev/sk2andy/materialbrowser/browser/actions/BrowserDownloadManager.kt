package dev.sk2andy.materialbrowser.browser.actions

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequestFactory

class BrowserDownloadManager(context: Context) {
    private val applicationContext = context.applicationContext

    fun enqueue(request: BrowserDownloadRequest): DownloadActionResult {
        val safeRequest = BrowserDownloadRequestFactory.create(
            url = request.url,
            contentDisposition = "attachment; filename=\"${request.fileName}\"",
            mimeType = request.mimeType,
            userAgent = request.userAgent,
            cookies = request.cookies,
        ) ?: return DownloadActionResult.Failed(
            applicationContext.getString(R.string.error_download_invalid_address),
        )

        return runCatching {
            val platformRequest = DownloadManager.Request(Uri.parse(safeRequest.url))
                .setMimeType(safeRequest.mimeType)
                .setTitle(safeRequest.fileName)
                .setDescription(
                    Uri.parse(safeRequest.url).host
                        ?: applicationContext.getString(R.string.download_notification_description),
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeRequest.fileName)
            safeRequest.userAgent?.let { platformRequest.addRequestHeader("User-Agent", it) }
            safeRequest.cookies?.let { platformRequest.addRequestHeader("Cookie", it) }
            val manager = applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(platformRequest)
            DownloadActionResult.Enqueued(id, safeRequest.fileName)
        }.getOrElse {
            DownloadActionResult.Failed(
                applicationContext.getString(R.string.error_download_start_failed),
            )
        }
    }
}
