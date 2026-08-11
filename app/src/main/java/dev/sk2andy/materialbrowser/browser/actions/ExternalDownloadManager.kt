package dev.sk2andy.materialbrowser.browser.actions

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.annotation.VisibleForTesting
import dev.sk2andy.materialbrowser.data.BrowserDownloadRequest
import dev.sk2andy.materialbrowser.data.BrowserDownloadSettings
import java.util.Locale

enum class ExternalDownloadProtocol(val stableId: String) {
    View("view"),
}

data class ExternalDownloadManagerApp(
    val id: String,
    val packageName: String,
    val activityName: String,
    val label: String,
    val protocol: ExternalDownloadProtocol,
    val isOneDm: Boolean,
)

data class PendingDownloadChoice(
    val request: BrowserDownloadRequest,
    val apps: List<ExternalDownloadManagerApp>,
    val isIncognito: Boolean,
)

sealed interface ExternalDownloadLaunchResult {
    data class Launched(val appName: String) : ExternalDownloadLaunchResult
    data object Unavailable : ExternalDownloadLaunchResult
}

class ExternalDownloadManager(private val context: Context) {
    private val packageManager = context.packageManager

    fun discover(request: BrowserDownloadRequest? = null): List<ExternalDownloadManagerApp> {
        val knownOneDmApps = ONE_DM_PACKAGES.mapNotNull(::oneDmApp)
        val knownOneDmPackages = knownOneDmApps.mapTo(hashSetOf(), ExternalDownloadManagerApp::packageName)
        val mimeTypes = request?.mimeType?.let(::listOf) ?: PROBE_MIME_TYPES
        val genericApps = mimeTypes.asSequence()
            .flatMap { mimeType ->
                query(
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(request?.url?.let(Uri::parse) ?: PROBE_URI, mimeType)
                    },
                ).asSequence()
            }
            .filterNot { it.activityInfo.packageName == context.packageName }
            .filterNot { it.activityInfo.packageName in knownOneDmPackages }
            .filter { it.activityInfo.packageName in GENERIC_MANAGER_PACKAGES }
            .mapNotNull(::genericApp)
            .distinctBy(ExternalDownloadManagerApp::packageName)
            .toList()
        return (knownOneDmApps + genericApps)
            .distinctBy(ExternalDownloadManagerApp::id)
            .sortedWith(
                compareByDescending<ExternalDownloadManagerApp> { it.isOneDm }
                    .thenBy { it.label.lowercase(Locale.getDefault()) },
            )
    }

    fun launch(
        request: BrowserDownloadRequest,
        app: ExternalDownloadManagerApp,
        settings: BrowserDownloadSettings,
        allowSessionData: Boolean,
    ): ExternalDownloadLaunchResult {
        val intent = createIntent(request, app, settings, allowSessionData)
        return try {
            context.startActivity(intent)
            ExternalDownloadLaunchResult.Launched(app.label)
        } catch (_: ActivityNotFoundException) {
            ExternalDownloadLaunchResult.Unavailable
        } catch (_: SecurityException) {
            ExternalDownloadLaunchResult.Unavailable
        }
    }

    @VisibleForTesting
    internal fun createIntent(
        request: BrowserDownloadRequest,
        app: ExternalDownloadManagerApp,
        settings: BrowserDownloadSettings,
        allowSessionData: Boolean,
    ): Intent = if (app.isOneDm) {
            oneDmIntent(request, app, settings, allowSessionData)
        } else {
            Intent(Intent.ACTION_VIEW).apply {
                component = ComponentName(app.packageName, app.activityName)
                setDataAndType(Uri.parse(request.url), request.mimeType)
            }
        }

    private fun oneDmIntent(
        request: BrowserDownloadRequest,
        app: ExternalDownloadManagerApp,
        settings: BrowserDownloadSettings,
        allowSessionData: Boolean,
    ) = Intent(Intent.ACTION_VIEW).apply {
        component = ComponentName(app.packageName, ONE_DM_ACTIVITY)
        data = Uri.parse(request.url)
        putExtra(EXTRA_FILENAME, request.fileName)
        if (settings.shareSessionDataWithOneDm && allowSessionData) {
            request.cookies?.let { putExtra(EXTRA_COOKIES, it) }
            request.userAgent?.let { putExtra(EXTRA_USER_AGENT, it) }
            request.referrer?.let { putExtra(EXTRA_REFERRER, it) }
        }
    }

    private fun oneDmApp(packageName: String): ExternalDownloadManagerApp? {
        val component = ComponentName(packageName, ONE_DM_ACTIVITY)
        val activityInfo = runCatching {
            packageManager.getActivityInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0),
            )
        }.getOrNull()?.takeIf { it.enabled && it.exported } ?: return null
        val label = runCatching { activityInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: packageName
        return app(
            packageName = packageName,
            activityName = ONE_DM_ACTIVITY,
            label = label,
            isOneDm = true,
        )
    }

    private fun genericApp(resolveInfo: ResolveInfo): ExternalDownloadManagerApp? {
        val info = resolveInfo.activityInfo ?: return null
        if (!info.enabled || !info.exported) return null
        val label = runCatching { resolveInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: info.packageName
        return app(
            packageName = info.packageName,
            activityName = info.name,
            label = label,
            isOneDm = false,
        )
    }

    private fun app(
        packageName: String,
        activityName: String,
        label: String,
        isOneDm: Boolean,
    ): ExternalDownloadManagerApp {
        val protocol = ExternalDownloadProtocol.View
        return ExternalDownloadManagerApp(
            id = listOf(protocol.stableId, packageName).joinToString("|"),
            packageName = packageName,
            activityName = activityName,
            label = label,
            protocol = protocol,
            isOneDm = isOneDm,
        )
    }

    private fun query(intent: Intent): List<ResolveInfo> = packageManager.queryIntentActivities(
        intent,
        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
    )

    private companion object {
        val PROBE_URI: Uri = Uri.parse("https://example.com/download")
        val PROBE_MIME_TYPES = listOf(
            "application/octet-stream",
            "application/pdf",
            "application/zip",
            "video/mp4",
        )
        val ONE_DM_PACKAGES = listOf(
            "idm.internet.download.manager.plus",
            "idm.internet.download.manager",
            "idm.internet.download.manager.adm.lite",
        )
        val GENERIC_MANAGER_PACKAGES = setOf(
            "com.dv.adm",
        )
        const val ONE_DM_ACTIVITY = "idm.internet.download.manager.Downloader"
        const val EXTRA_FILENAME = "extra_filename"
        const val EXTRA_COOKIES = "extra_cookies"
        const val EXTRA_USER_AGENT = "extra_useragent"
        const val EXTRA_REFERRER = "extra_referer"
    }
}
