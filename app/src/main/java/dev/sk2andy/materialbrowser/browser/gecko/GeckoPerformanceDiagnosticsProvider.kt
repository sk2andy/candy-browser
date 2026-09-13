package dev.sk2andy.materialbrowser.browser.gecko

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import dev.sk2andy.materialbrowser.BuildConfig
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicLong

/** Shell-only local export; disabled through the manifest in normal APKs. */
class GeckoPerformanceDiagnosticsProvider : ContentProvider() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private data class DomCommand(val id: Long = 0, val status: String = "idle")
    private val domRequestIds = AtomicLong()
    @Volatile
    private var domCommand = DomCommand()

    override fun onCreate(): Boolean {
        context?.let(GeckoPerformanceDiagnostics::initialize)
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        enforceShellPermission()
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS) return snapshot(accepted = false)
        if (arg != null || (extras != null && !extras.isEmpty)) return snapshot(accepted = false)
        if (method !in setOf("start", "stop", "status", "gap", "discard", "dom-probe", "dom-probe-status")) {
            return snapshot(accepted = false)
        }
        val applicationContext = context?.applicationContext ?: return snapshot(accepted = false)
        val domRequestId = if (method == "dom-probe") domRequestIds.incrementAndGet() else 0
        mainHandler.post {
            when (method) {
                "start" -> GeckoPerformanceDiagnostics.start(applicationContext)
                "stop" -> GeckoPerformanceDiagnostics.stop(applicationContext)
                "gap" -> GeckoPerformanceDiagnostics.markGap()
                "discard" -> {
                    GeckoPerformanceDiagnostics.discard(applicationContext)
                    GeckoDomDiagnostics.discard()
                }
                "dom-probe" -> {
                    val outcome = GeckoDomDiagnostics.request(domRequestId)
                    domCommand = DomCommand(domRequestId, if (outcome == "busy") "busy" else "accepted")
                }
            }
            GeckoPerformanceDiagnostics.profilerActive()
        }
        return snapshot(accepted = true).apply {
            if (method == "dom-probe") {
                remove("domPayload")
                putString("domStatus", "queued")
                putLong("domCommandId", domRequestId)
                putString("domCommandStatus", "queued")
            }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforceShellPermission()
        if (!BuildConfig.ENABLE_PERFORMANCE_DIAGNOSTICS ||
            mode != "r" || uri.path != "/gecko-profile.json.gz" ||
            uri.query != null || uri.fragment != null
        ) {
            throw FileNotFoundException("Profile unavailable")
        }
        val applicationContext = context?.applicationContext ?: throw FileNotFoundException("Profile unavailable")
        return GeckoPerformanceDiagnostics.openProfileForRead(applicationContext)
            ?: throw FileNotFoundException("Profile unavailable")
    }

    override fun getType(uri: Uri): String? {
        enforceShellPermission()
        return if (uri.path == "/gecko-profile.json.gz") "application/gzip" else null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        enforceShellPermission()
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        enforceShellPermission()
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        enforceShellPermission()
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        enforceShellPermission()
        return 0
    }

    private fun enforceShellPermission() {
        context?.enforceCallingOrSelfPermission(Manifest.permission.DUMP, "Diagnostic access requires shell permission")
            ?: throw SecurityException("Diagnostic access unavailable")
    }

    private fun snapshot(accepted: Boolean): Bundle = Bundle().apply {
        putBoolean("accepted", accepted)
        putString("status", GeckoPerformanceDiagnostics.status)
        putBoolean("recording", GeckoPerformanceDiagnostics.isRecording)
        putBoolean("profilerActive", GeckoPerformanceDiagnostics.isProfilerActive)
        val dom = GeckoDomDiagnostics.snapshot
        val command = domCommand
        putString("domStatus", dom.status)
        putLong("domCommandId", command.id)
        putString("domCommandStatus", command.status)
        putLong("domResultId", dom.requestId)
        if (command.id == dom.requestId) dom.payload?.let { putString("domPayload", it) }
    }
}
