package dev.sk2andy.materialbrowser.ui

import android.appwidget.AppWidgetManager
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.sk2andy.materialbrowser.R
import dev.sk2andy.materialbrowser.SiteWidgetProvider
import dev.sk2andy.materialbrowser.browser.BrowserController
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetCapture
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetFormat
import dev.sk2andy.materialbrowser.browser.sitewidget.SiteWidgetPublisher
import dev.sk2andy.materialbrowser.data.SiteWidgetStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun SiteWidgetEditorHost(controller: BrowserController) {
    val requestId = controller.siteWidgetEditorRequestId
    if (requestId == 0) return
    key(requestId) {
        SiteWidgetEditorRequest(controller, requestId, controller.siteWidgetEditingWidgetId)
    }
}

@Composable
private fun SiteWidgetEditorRequest(controller: BrowserController, requestId: Int, widgetId: Int) {
    val context = LocalContext.current
    val publisher = remember(context) { SiteWidgetPublisher(context) }
    val scope = rememberCoroutineScope()
    var capture by remember(requestId) { mutableStateOf<SiteWidgetCapture?>(null) }
    var captureHandle by remember(requestId) { mutableStateOf<BrowserEnginePreviewCapture?>(null) }
    var started by remember(requestId) { mutableStateOf(false) }
    var saving by remember(requestId) { mutableStateOf(false) }
    var error by remember(requestId) { mutableStateOf<String?>(null) }
    val isLoading = controller.selectedTab.isLoading
    val initialFormat = remember(widgetId) {
        val provider = if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            null
        } else {
            AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId)?.provider
        }
        SiteWidgetFormat.entries.firstOrNull { SiteWidgetProvider.component(context, it) == provider }
            ?: SiteWidgetFormat.FourByThree
    }
    val initialInterval = remember(widgetId) {
        SiteWidgetStore(context).snapshotForWidget(widgetId)?.refreshIntervalMinutes ?: 0
    }
    DisposableEffect(requestId) {
        onDispose { captureHandle?.cancel() }
    }
    LaunchedEffect(requestId, isLoading) {
        if (started || isLoading) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        started = true
        captureHandle = controller.captureSelectedPageForSiteWidget { result ->
            capture = result
            if (result == null) error = context.getString(R.string.site_widget_capture_failed)
        }
    }
    LaunchedEffect(requestId) {
        delay(15_000)
        if (!started) error = context.getString(R.string.site_widget_capture_failed)
    }
    val source = capture
    if (source == null) {
        AlertDialog(
            onDismissRequest = controller::dismissSiteWidgetEditor,
            title = { Text(stringResource(R.string.site_widget_editor_title)) },
            text = { error?.let { Text(it) } ?: CircularProgressIndicator() },
            confirmButton = {
                TextButton(onClick = controller::dismissSiteWidgetEditor) { Text(stringResource(R.string.action_back)) }
            },
        )
        return
    }
    Dialog(
        onDismissRequest = { if (!saving) controller.dismissSiteWidgetEditor() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SiteWidgetCropEditor(
            bitmap = remember(source) { source.bitmap.asImageBitmap() },
            initialFormat = initialFormat,
            initialRefreshIntervalMinutes = initialInterval,
            formatChangeEnabled = widgetId == AppWidgetManager.INVALID_APPWIDGET_ID,
            saving = saving,
            error = error,
            onDismiss = controller::dismissSiteWidgetEditor,
            onSubmit = { format, crop, interval ->
                if (!controller.isCurrentSiteWidgetSource(source.source)) {
                    error = context.getString(R.string.site_widget_capture_failed)
                } else if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !publisher.canPin()) {
                    error = context.getString(R.string.site_widget_pin_unavailable)
                } else {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val snapshot = publisher.prepare(source, format, crop, interval)
                            if (snapshot == null) {
                                error = context.getString(R.string.site_widget_save_failed)
                                return@launch
                            }
                            if (!controller.isCurrentSiteWidgetSource(source.source)) {
                                publisher.discard(snapshot)
                                error = context.getString(R.string.site_widget_capture_failed)
                                return@launch
                            }
                            val accepted = if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                publisher.replace(widgetId, snapshot)
                            } else {
                                val image = publisher.image(snapshot)
                                if (image == null) false else try {
                                    publisher.requestPin(snapshot, image)
                                } finally {
                                    image.recycle()
                                }
                            }
                            if (accepted) {
                                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                                    Toast.makeText(context, R.string.site_widget_pin_requested, Toast.LENGTH_LONG).show()
                                }
                                controller.dismissSiteWidgetEditor()
                            } else {
                                publisher.discard(snapshot)
                                error = context.getString(R.string.site_widget_pin_failed)
                            }
                        } finally {
                            saving = false
                        }
                    }
                }
            },
        )
    }
}
