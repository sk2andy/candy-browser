package dev.sk2andy.materialbrowser.ui

internal interface QrCodeScanner {
    fun startScan(
        onSuccess: (String) -> Unit,
        onCanceled: () -> Unit,
        onFailure: () -> Unit,
    )
}
