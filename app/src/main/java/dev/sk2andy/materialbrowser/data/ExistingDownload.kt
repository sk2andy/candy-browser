package dev.sk2andy.materialbrowser.data

import android.net.Uri

data class ExistingDownload(
    val uri: Uri,
    val fileName: String,
    val mimeType: String,
)
