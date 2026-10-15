package dev.sk2andy.materialbrowser.browser.sitewidget

import android.content.Context
import android.graphics.Bitmap
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.blocking.BundledSitePrivacyDefaults
import dev.sk2andy.materialbrowser.blocking.PrivacyRequestSanitizer
import dev.sk2andy.materialbrowser.blocking.SitePrivacyOverrideRules
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.engine.AndroidBrowserEngineFactory
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineOffscreenSurface
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEnginePreviewCapture
import dev.sk2andy.materialbrowser.browser.gecko.GeckoBrowserEngineSessionFactory
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicyRules
import dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
import dev.sk2andy.materialbrowser.browser.isSynced
import dev.sk2andy.materialbrowser.browser.systemwebview.SystemWebViewBrowserEngineFactory
import dev.sk2andy.materialbrowser.data.AppearanceSettings
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import dev.sk2andy.materialbrowser.data.CandyRuleStore
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEvent
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Reloads one public, local-profile viewport using its original engine and authenticated jar. */
internal class SiteWidgetPageRenderer(context: Context) {
    private val appContext = context.applicationContext

    suspend fun render(snapshot: SiteWidgetSnapshot): Bitmap? {
        val source = SiteWidgetSnapshotRules.sanitize(snapshot)?.source ?: return null
        val engine = AndroidBrowserEngineKind.entries.firstOrNull { it.stableId == source.engineId }
            ?: return null
        if (
            source.viewportWidthPx !in 1..MAX_VIEWPORT_DIMENSION ||
            source.viewportHeightPx !in 1..MAX_VIEWPORT_DIMENSION ||
            source.viewportWidthPx.toLong() * source.viewportHeightPx > MAX_VIEWPORT_PIXELS ||
            BuildConfig.SYSTEM_WEBVIEW_ONLY && engine != AndroidBrowserEngineKind.SystemWebView
        ) return null
        val settings = withContext(Dispatchers.IO) {
            if (!isAllowedProfile(source)) return@withContext null
            val store = BrowserSessionStore(appContext)
            RenderSettings(
                policy = privacyPolicy(store, source, engine),
                appearance = store.loadAppearanceSettings(),
            )
        } ?: return null
        return withContext(Dispatchers.Main.immediate) {
            withTimeoutOrNull(RENDER_TIMEOUT_MILLIS) {
                renderOnMain(source, engine, settings)
            }
        }
    }

    private suspend fun renderOnMain(
        source: SiteWidgetSource,
        engine: AndroidBrowserEngineKind,
        settings: RenderSettings,
    ): Bitmap? {
        var session: AndroidBrowserEngineSessionPort? = null
        var surface: BrowserEngineOffscreenSurface? = null
        var capture: BrowserEnginePreviewCapture? = null
        var captured: Bitmap? = null
        try {
            if (
                engine == AndroidBrowserEngineKind.SystemWebView && source.isolationEnabled &&
                !SystemWebViewBrowserEngineFactory.supportsMultiProfile()
            ) return null
            val factory: AndroidBrowserEngineFactory = if (BuildConfig.SYSTEM_WEBVIEW_ONLY) {
                if (engine != AndroidBrowserEngineKind.SystemWebView) return null
                SystemWebViewBrowserEngineFactory(
                    context = appContext,
                    cleanupPrivateProfiles = false,
                )
            } else when (engine) {
                AndroidBrowserEngineKind.GeckoView -> GeckoBrowserEngineSessionFactory(appContext)
                AndroidBrowserEngineKind.SystemWebView -> SystemWebViewBrowserEngineFactory(
                    context = appContext,
                    cleanupPrivateProfiles = false,
                )
            }
            factory.setBlockThirdPartyCookies(settings.policy.blockThirdPartyCookies)
            factory.setWebContentFontSizeFactor(settings.appearance.webContentFontSizePercent / 100f)
            factory.setForceDarkWebsites(settings.appearance.forceDarkWebsites)
            val navigation = CompletableDeferred<BrowserEngineEvent>()
            val created = factory.create(
                tabId = "site-widget-${UUID.randomUUID()}",
                profileId = source.profileId,
                isolationEnabled = source.isolationEnabled,
                isPrivate = false,
                contentKind = BrowserEngineContentKind.ExternalPreview,
                privacyPolicy = settings.policy,
                eventSink = BrowserEngineEventSink { event ->
                    if (
                        event.type == BrowserEngineEventType.Crashed ||
                        BrowserUriPolicy.normalizeHttpUrl(event.address.orEmpty()) == source.url &&
                        (event.type == BrowserEngineEventType.NavigationCommitted ||
                            event.type == BrowserEngineEventType.NavigationFailed)
                    ) navigation.complete(event)
                },
            )
            session = created
            val offscreen = created.createOffscreenSurface(source.viewportWidthPx, source.viewportHeightPx)
                ?: return null
            surface = offscreen
            created.setAudioMuted(true)
            created.setVideoAutoplayBlocked(true)
            created.setDesktopMode(source.desktopMode)
            created.execute(BrowserEngineCommands.load(source.url))
            val event = navigation.await()
            if (
                event.type != BrowserEngineEventType.NavigationCommitted ||
                (event.httpStatusCode ?: 200) >= 400 ||
                BrowserUriPolicy.normalizeHttpUrl(event.address.orEmpty()) != source.url
            ) return null
            captured = suspendCancellableCoroutine { continuation ->
                capture = offscreen.capture(source.scrollY) { bitmap ->
                    if (continuation.isActive) {
                        continuation.resume(bitmap) { _, value, _ -> value?.recycle() }
                    } else {
                        bitmap?.recycle()
                    }
                }
                if (capture == null && continuation.isActive) continuation.resume(null)
                continuation.invokeOnCancellation { capture?.cancel() }
            }
            if (!withContext(Dispatchers.IO) { isAllowedProfile(source) }) return null
            return captured.also { captured = null }
        } catch (_: RuntimeException) {
            // Engine startup, unsupported providers and renderer crashes retain the previous image.
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            return null
        } finally {
            captured?.takeUnless(Bitmap::isRecycled)?.recycle()
            capture?.cancel()
            try {
                surface?.close()
            } finally {
                session?.execute(BrowserEngineCommands.close())
            }
        }
    }

    private fun isAllowedProfile(source: SiteWidgetSource): Boolean =
        BrowserSessionStore(appContext).loadProfiles().first.any { profile ->
            profile.id == source.profileId && !profile.isSynced && profile.protection == null &&
                profile.isolationEnabled == source.isolationEnabled
        }

    private fun privacyPolicy(
        store: BrowserSessionStore,
        source: SiteWidgetSource,
        engine: AndroidBrowserEngineKind,
    ): GeckoPrivacyPolicy {
        val host = PrivacyRequestSanitizer.webHost(source.url)
        val blocker = store.loadBlockerSettings()
        val signals = store.loadPrivacySignalSettings()
        val pausedHosts = store.loadPermanentSiteExceptions()[source.profileId].orEmpty()
        val paused = host in pausedHosts
        val overrides = store.loadSitePrivacyOverrides()[source.profileId]?.get(host)
        val bundled = BundledSitePrivacyDefaults.load(appContext)
        val policy = GeckoPrivacyPolicyRules.extensionOwnedAdFilteringWithCandyCookieDefaults(
            pageHost = host,
            pausedHosts = pausedHosts,
            hideCookieConsent = blocker.hideCookieConsent && !paused,
            cookieBannerRemovalDisabled = SitePrivacyOverrideRules.cookieBannerRemovalDisabled(
                overrides,
                bundled.cookieBannerRemovalDisabled(host),
            ),
            blockThirdPartyCookies = blocker.blockThirdPartyCookies,
            allowThirdPartyCookiesForSite = paused ||
                SitePrivacyOverrideRules.thirdPartyLoginAllowed(overrides) ||
                SitePrivacyOverrideRules.captchaCompatibilityAllowed(overrides),
            doNotTrackEnabled = signals.doNotTrackEnabled,
            globalPrivacyControlEnabled = signals.globalPrivacyControlEnabled,
            animationsEnabled = false,
        )
        return if (engine == AndroidBrowserEngineKind.GeckoView) policy else policy.copy(
            blockAdsAndTrackers = blocker.blockAdsAndTrackers && !paused,
            candyRules = CandyRuleStore(appContext).load().filter { rule ->
                rule.active && (rule.profileId == null || rule.profileId == source.profileId)
            },
        )
    }

    private data class RenderSettings(
        val policy: GeckoPrivacyPolicy,
        val appearance: AppearanceSettings,
    )

    private companion object {
        const val RENDER_TIMEOUT_MILLIS = 20_000L
        const val MAX_VIEWPORT_DIMENSION = 4_096
        const val MAX_VIEWPORT_PIXELS = 8_000_000L
    }
}
