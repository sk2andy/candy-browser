package dev.sk2andy.materialbrowser.browser.sitewidget

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import dev.sk2andy.materialbrowser.BuildConfig
import dev.sk2andy.materialbrowser.browser.AndroidBrowserEngineKind
import dev.sk2andy.materialbrowser.browser.BrowserProfile
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.ProfileLockTrigger
import dev.sk2andy.materialbrowser.browser.ProfileProtection
import dev.sk2andy.materialbrowser.browser.systemwebview.SystemWebViewBrowserEngineFactory
import dev.sk2andy.materialbrowser.browser.systemwebview.WebViewProfileRules
import dev.sk2andy.materialbrowser.data.BrowserSessionStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SiteWidgetPageRendererInstrumentedTest {
    private val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testId = UUID.randomUUID().toString()
    private val preferenceName = "site-widget-renderer-$testId"
    private val context = object : ContextWrapper(baseContext) {
        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("$preferenceName-$name", mode)
    }
    private val store = BrowserSessionStore(context)
    private val profile = BrowserProfile(id = "widget-render-$testId", emoji = "🍬", isolationEnabled = true)

    @Before
    fun setUp() {
        store.saveProfiles(listOf(profile), profile.id)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(BrowserSessionStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun geckoRefreshLoadsNewPageAndRestoresViewportScrollWithoutActivity() {
        assumeFalse(BuildConfig.SYSTEM_WEBVIEW_ONLY)
        verifyRefresh(AndroidBrowserEngineKind.GeckoView)
    }

    @Test
    fun systemWebViewRefreshLoadsNewPageAndRestoresViewportScrollWithoutActivity() {
        assumeTrue(SystemWebViewBrowserEngineFactory.supportsMultiProfile())
        verifyRefresh(AndroidBrowserEngineKind.SystemWebView)
    }

    @Test
    fun missingProtectedPrivateAndLegacySourcesNeverRequestPage() = runBlocking {
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer {
            requests.incrementAndGet()
            page("#007f99")
        }.use { server ->
            val snapshot = snapshot(server.fixtureUrl("/widget"), AndroidBrowserEngineKind.SystemWebView)
            val renderer = SiteWidgetPageRenderer(context)
            assertNull(renderer.render(snapshot.copy(source = snapshot.source.copy(profileId = "missing"))))
            assertNull(renderer.render(snapshot.copy(source = snapshot.source.copy(isPrivate = true))))
            assertNull(renderer.render(snapshot.copy(source = snapshot.source.copy(
                engineId = "",
                viewportWidthPx = 0,
                viewportHeightPx = 0,
            ))))
            store.saveProfiles(
                listOf(profile.copy(protection = ProfileProtection(ProfileLockTrigger.AppClosed))),
                profile.id,
            )
            assertNull(renderer.render(snapshot))
            assertEquals(0, requests.get())
        }
    }

    @Test
    fun systemWebViewRefreshKeepsAuthenticatedCookiesInsideOriginalProfile() = runBlocking {
        assumeTrue(SystemWebViewBrowserEngineFactory.supportsMultiProfile())
        val other = profile.copy(id = "widget-other-$testId")
        store.saveProfiles(listOf(profile, other), profile.id)
        EdgeToEdgeSiteFixtureServer {
            """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body{margin:0;height:100%;background:#ff0000}</style></head>
                <body><script>document.body.style.background = document.cookie.includes('widget_auth=green') ? '#007f99' : '#2211cc';</script></body></html>
            """.trimIndent()
        }.use { server ->
            val url = server.fixtureUrl("/widget-auth")
            setProfileCookie(profile.id, url, "widget_auth=green; Path=/")
            setProfileCookie(other.id, url, "widget_auth=blue; Path=/")
            val source = snapshot(url, AndroidBrowserEngineKind.SystemWebView, scrollY = 0)
            val renderer = SiteWidgetPageRenderer(context)
            assertPixels(renderer.render(source), Color.rgb(0, 127, 153))
            assertPixels(renderer.render(source.copy(source = source.source.copy(profileId = other.id))), Color.rgb(34, 17, 204))
        }
    }

    private fun verifyRefresh(engine: AndroidBrowserEngineKind) = runBlocking {
        var color = "#007f99"
        val requests = AtomicInteger()
        EdgeToEdgeSiteFixtureServer {
            requests.incrementAndGet()
            page(color)
        }.use { server ->
            val snapshot = snapshot(server.fixtureUrl("/widget-scroll"), engine)
            val renderer = SiteWidgetPageRenderer(context)
            assertPixels(renderer.render(snapshot), Color.rgb(0, 127, 153))
            color = "#2211cc"
            assertPixels(renderer.render(snapshot), Color.rgb(34, 17, 204))
            assertTrue("Each refresh must reload the fixture", requests.get() >= 2)
        }
    }

    private fun assertPixels(bitmap: Bitmap?, expectedColor: Int) {
        assertNotNull("Background page capture did not produce a bitmap", bitmap)
        requireNotNull(bitmap)
        try {
            assertEquals(400, bitmap.width)
            assertEquals(600, bitmap.height)
            val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            assertTrue("Unexpected red channel: $actual", abs(Color.red(actual) - Color.red(expectedColor)) <= 3)
            assertTrue("Unexpected green channel: $actual", abs(Color.green(actual) - Color.green(expectedColor)) <= 3)
            assertTrue("Unexpected blue channel: $actual", abs(Color.blue(actual) - Color.blue(expectedColor)) <= 3)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun setProfileCookie(profileId: String, url: String, cookie: String) =
        withContext(Dispatchers.Main.immediate) {
            val webView = WebView(context)
            try {
                WebViewCompat.setProfile(webView, WebViewProfileRules.isolatedProfileName(profileId))
                suspendCancellableCoroutine<Unit> { continuation ->
                    WebViewCompat.getProfile(webView).cookieManager.setCookie(url, cookie) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            } finally {
                webView.destroy()
            }
        }

    private fun snapshot(url: String, engine: AndroidBrowserEngineKind, scrollY: Int = 1_000) = SiteWidgetSnapshot(
        id = UUID.randomUUID().toString(),
        source = SiteWidgetSource(
            tabId = "widget-source",
            profileId = profile.id,
            url = url,
            title = "Widget render fixture",
            navigationGeneration = 0,
            engineId = engine.stableId,
            viewportWidthPx = 400,
            viewportHeightPx = 600,
            scrollY = scrollY,
            isolationEnabled = true,
        ),
        format = SiteWidgetFormat.FourByThree,
        crop = SiteWidgetCrop(0f, 0f, 1f, 1f),
        createdAtMillis = System.currentTimeMillis(),
    )

    private fun page(color: String): String = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;padding:0;color-scheme:light;}header{height:200px;background:#ff0000}main{height:6000px;background:$color}</style>
        </head><body><header></header><main></main></body></html>
    """.trimIndent()
}
