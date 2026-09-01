package dev.sk2andy.materialbrowser.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
internal fun rememberQrCodeScanner(): QrCodeScanner {
    val context = LocalContext.current
    return remember(context) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        object : QrCodeScanner {
            override fun startScan(
                onSuccess: (String) -> Unit,
                onCanceled: () -> Unit,
                onFailure: () -> Unit,
            ) {
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        onSuccess(barcode.rawValue?.trim().orEmpty())
                    }
                    .addOnCanceledListener(onCanceled)
                    .addOnFailureListener { onFailure() }
            }
        }
    }
}
