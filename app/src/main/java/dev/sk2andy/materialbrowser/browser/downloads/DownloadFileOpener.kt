package dev.sk2andy.materialbrowser.browser.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.sk2andy.materialbrowser.R

internal object DownloadFileOpener {
    fun open(context: Context, uri: Uri, mimeType: String) {
        runCatching {
            val mime = mimeType.takeIf(String::isNotBlank)
                ?: context.contentResolver.getType(uri)
                ?: "*/*"
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            )
        }.onFailure {
            Toast.makeText(context, R.string.downloads_open_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
