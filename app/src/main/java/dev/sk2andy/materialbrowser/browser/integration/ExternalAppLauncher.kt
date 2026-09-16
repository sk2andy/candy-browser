package dev.sk2andy.materialbrowser.browser.integration

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

sealed interface ExternalLaunchResult {
    data object Launched : ExternalLaunchResult
    data class OpenInBrowser(val url: String) : ExternalLaunchResult
    data object Unsupported : ExternalLaunchResult
}

class ExternalAppLauncher(
    private val context: Context,
    private val canResolveExternalActivity: (Intent) -> Boolean = { target ->
        val resolved = context.packageManager.resolveActivity(
            target,
            PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER,
        )
        val resolvedPackage = resolved?.activityInfo?.packageName
        resolvedPackage != null &&
            resolvedPackage != context.packageName &&
            resolvedPackage != ANDROID_FRAMEWORK_PACKAGE &&
            (target.`package` != null || resolved.filter?.countDataAuthorities()?.let { it > 0 } == true)
    },
) {
    internal fun webTargetUrl(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme == "http" || scheme == "https") {
            return BrowserUriPolicy.normalizeHttpUrl(uri.toString())
        }
        if (scheme != "intent") return null
        val parsed = parseIntentUri(uri) ?: return null
        return BrowserUriPolicy.normalizeHttpUrl(parsed.dataString)
    }

    fun openWebUrlExternally(url: String): ExternalLaunchResult {
        val normalized = BrowserUriPolicy.normalizeHttpUrl(url)
            ?: return ExternalLaunchResult.Unsupported
        return launchDirect(webIntent(normalized), fallbackUrl = null)
    }

    fun canOpenWebUrlExternally(url: String): Boolean {
        val normalized = BrowserUriPolicy.normalizeHttpUrl(url) ?: return false
        return canResolveExternalActivity(webIntent(normalized))
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
        val parsed = parseIntentUri(uri) ?: return ExternalLaunchResult.Unsupported
        val fallbackUrl = parsed.getStringExtra("browser_fallback_url")
        val data = parsed.data ?: return fallback(fallbackUrl)
        val scheme = data.scheme?.lowercase()
        val isWebLink = scheme == "http" || scheme == "https"
        if (isWebLink) {
            if (BrowserUriPolicy.normalizeHttpUrl(data.toString()) == null) return fallback(fallbackUrl)
        } else if (!BrowserUriPolicy.canOpenExternally(scheme)) {
            return fallback(fallbackUrl)
        }
        if (parsed.`package` == context.packageName) return fallback(fallbackUrl)

        val safeIntent = Intent(Intent.ACTION_VIEW, data)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .apply { parsed.`package`?.let(::setPackage) }
        if (isWebLink) {
            safeIntent.addFlags(
                Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT,
            )
        }
        return launchDirect(safeIntent, fallbackUrl)
    }

    private fun launchDirect(target: Intent, fallbackUrl: String?): ExternalLaunchResult {
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(target)
            ExternalLaunchResult.Launched
        } catch (_: ActivityNotFoundException) {
            fallback(fallbackUrl)
        } catch (_: SecurityException) {
            fallback(fallbackUrl)
        }
    }

    private fun webIntent(normalizedUrl: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .apply {
                if (GooglePlayAppLinkRules.shouldOpenInPlayStore(normalizedUrl)) {
                    setPackage(GOOGLE_PLAY_PACKAGE)
                } else {
                    addFlags(
                        Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or
                            Intent.FLAG_ACTIVITY_REQUIRE_DEFAULT,
                    )
                }
            }

    private fun fallback(url: String?): ExternalLaunchResult =
        BrowserUriPolicy.normalizeHttpUrl(url)
            ?.let(ExternalLaunchResult::OpenInBrowser)
            ?: ExternalLaunchResult.Unsupported

    private fun parseIntentUri(uri: Uri): Intent? = runCatching {
        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
    }.getOrNull()

    private companion object {
        const val ANDROID_FRAMEWORK_PACKAGE = "android"
        const val GOOGLE_PLAY_PACKAGE = "com.android.vending"
    }
}
