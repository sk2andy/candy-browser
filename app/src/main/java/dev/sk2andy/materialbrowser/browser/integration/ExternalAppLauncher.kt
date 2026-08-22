package dev.sk2andy.materialbrowser.browser.integration

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult
    data class OpenInBrowser(val url: String) : ExternalLaunchResult
    data object Unsupported : ExternalLaunchResult
}

class ExternalAppLauncher(private val context: Context) {
    fun openWebUrlExternally(url: String): ExternalLaunchResult {
        val normalized = BrowserUriPolicy.normalizeHttpUrl(url)
            ?: return ExternalLaunchResult.Unsupported
        val target = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
        return launchDirect(target, fallbackUrl = null)
    }

    fun open(
        uri: Uri,
        browserFallbackUrl: String? = null,
    ): ExternalLaunchResult {
        val scheme = uri.scheme?.lowercase() ?: return fallback(browserFallbackUrl)
        if (scheme == "http" || scheme == "https") {
            return BrowserUriPolicy.normalizeHttpUrl(uri.toString())
                ?.let(ExternalLaunchResult::OpenInBrowser)
                ?: ExternalLaunchResult.Unsupported
        }
        if (scheme == "intent") return openIntentUri(uri)
        if (!BrowserUriPolicy.canOpenExternally(scheme)) return fallback(browserFallbackUrl)

        val action = when (scheme) {
            "tel" -> Intent.ACTION_DIAL
            "mailto", "sms", "smsto" -> Intent.ACTION_SENDTO
            else -> Intent.ACTION_VIEW
        }
        val target = Intent(action, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return launchDirect(target, browserFallbackUrl)
    }

    private fun openIntentUri(uri: Uri): ExternalLaunchResult {
        val parsed = runCatching {
            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return ExternalLaunchResult.Unsupported
        val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
        val data = parsed.data ?: return fallback(fallbackUrl)
        val scheme = data.scheme?.lowercase()
        if (!BrowserUriPolicy.canOpenExternally(scheme)) return fallback(fallbackUrl)

        val safeIntent = Intent(Intent.ACTION_VIEW, data)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .apply { parsed.`package`?.let(::setPackage) }
        return launchDirect(safeIntent, fallbackUrl)
    }

    private fun launchDirect(target: Intent, fallbackUrl: String?): ExternalLaunchResult {
        if (context !is Activity) target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(target)
            ExternalLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            fallback(fallbackUrl)
        } catch (_: SecurityException) {
            fallback(fallbackUrl)
        }
    }

    private fun fallback(url: String?): ExternalLaunchResult =
        BrowserUriPolicy.normalizeHttpUrl(url)
            ?.let(ExternalLaunchResult::OpenInBrowser)
            ?: ExternalLaunchResult.Unsupported
}
