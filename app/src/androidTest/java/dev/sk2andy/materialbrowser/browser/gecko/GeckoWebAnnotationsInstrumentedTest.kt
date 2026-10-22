package dev.sk2andy.materialbrowser.browser.gecko

import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class GeckoWebAnnotationsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun trustedSavedHighlightTapReachesDocumentBoundSessionListener() {
        EdgeToEdgeSiteFixtureServer {
            """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0"><p style="position:absolute;top:80px;left:20px;margin:0;font:20px/30px sans-serif">Saved Gecko quote</p></body></html>"""
        }.use { server ->
            val url = server.fixtureUrl("/gecko-annotations")
            val loaded = CountDownLatch(1)
            val clicked = AtomicReference<String?>()
            lateinit var session: GeckoBrowserSession
            lateinit var view: View
            lateinit var host: FrameLayout
            composeRule.runOnIdle {
                session = GeckoRuntimeOwner.getOrCreate(composeRule.activity).createSession(
                    profileId = "gecko-annotation-taps", isPrivate = false, privacyPolicy = GeckoPrivacyPolicy.Disabled,
                )
                session.bindToppingSession("gecko-annotation-taps", BrowserEngineContentKind.RegularTab)
                session.setWebAnnotationClickListener(clicked::set)
                session.setStateListener { state ->
                    if (state.url == url && state.lastNavigationSucceeded == true) loaded.countDown()
                }
                view = session.createView(composeRule.activity)
                host = FrameLayout(composeRule.activity)
                composeRule.activity.addContentView(host, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                ))
                host.addView(view)
                session.setActive(true)
                assertTrue(session.loadUrl(url))
            }
            try {
                assertTrue(loaded.await(20, TimeUnit.SECONDS))
                composeRule.waitUntil(5_000) { view.isAttachedToWindow && view.width > 0 && view.height > 0 }
                val applied = CountDownLatch(1)
                val result = AtomicReference(false)
                composeRule.runOnIdle {
                    session.executeWebTextOperation(JSONObject().put("operation", "highlights").put("url", url)
                        .put("annotations", JSONArray().put(JSONObject().put("id", "gecko-quote").put("quote", "Saved Gecko quote"))).toString()) {
                        result.set(it)
                        applied.countDown()
                    }
                }
                assertTrue(applied.await(10, TimeUnit.SECONDS))
                assertTrue(result.get())
                val location = IntArray(2)
                var density = 1f
                composeRule.runOnIdle {
                    view.getLocationOnScreen(location)
                    density = view.resources.displayMetrics.density
                }
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle()
                assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(
                    location[0] + (50 * density).toInt(), location[1] + (95 * density).toInt(),
                ))
                composeRule.waitUntil(10_000) { clicked.get() == "gecko-quote" }
                assertEquals("gecko-quote", clicked.get())
            } finally {
                composeRule.runOnIdle {
                    session.setActive(false)
                    host.removeAllViews()
                    session.releaseView(view)
                    session.close()
                }
            }
        }
    }
}
