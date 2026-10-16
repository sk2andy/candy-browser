package dev.sk2andy.materialbrowser.browser.systemwebview

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.engine.BrowserEngineContentKind
import dev.sk2andy.materialbrowser.browser.gecko.AndroidBrowserEngineSessionPort
import dev.sk2andy.materialbrowser.browser.gecko.BrowserEngineEventSink
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineCommands
import dev.sk2andy.materialbrowser.shared.browser.BrowserEngineEventType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class SystemWebViewPageTextInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun visiblePageTextExtractsRawJsonWithoutFormNavigationOrHiddenContent() {
        withLoadedSession(isPrivate = false, kind = BrowserEngineContentKind.RegularTab) { session, url ->
            val payload = JSONObject(requireNotNull(extract(session)))
            assertEquals(url, payload.getString("sourceUrl"))
            assertEquals("Constellations Fixture", payload.getString("title"))
            val text = payload.getString("text")
            assertTrue(text.contains("Visible article paragraph"))
            assertFalse(text.contains("Secret"))
            assertTrue(text.length <= 64_000)
        }
    }

    @Test
    fun visiblePageTextRejectsPrivateAndPreviewSessions() {
        for ((isPrivate, kind) in listOf(
            true to BrowserEngineContentKind.RegularTab,
            false to BrowserEngineContentKind.LinkPeek,
            false to BrowserEngineContentKind.ExternalPreview,
        )) {
            withLoadedSession(isPrivate, kind) { session, _ ->
                assertNull(extract(session))
            }
        }
    }

    @Test
    fun committedPreviewCanExtractSameDocumentAfterPromotion() {
        withLoadedSession(isPrivate = false, kind = BrowserEngineContentKind.ExternalPreview) { session, url ->
            assertNull(extract(session))
            composeRule.runOnIdle { session.promoteToRegularTab() }

            val payload = JSONObject(requireNotNull(extract(session)))
            assertEquals(url, payload.getString("sourceUrl"))
            assertTrue(payload.getString("text").contains("Visible article paragraph"))
        }
    }

    private fun extract(session: AndroidBrowserEngineSessionPort): String? {
        val finished = CountDownLatch(1)
        val result = AtomicReference<String?>()
        composeRule.runOnIdle {
            session.extractPageText {
                result.set(it)
                finished.countDown()
            }
        }
        assertTrue("Page text extraction did not complete", finished.await(10, TimeUnit.SECONDS))
        return result.get()
    }

    private fun withLoadedSession(
        isPrivate: Boolean,
        kind: BrowserEngineContentKind,
        action: (AndroidBrowserEngineSessionPort, String) -> Unit,
    ) {
        EdgeToEdgeSiteFixtureServer {
            """
                <!doctype html><html><head><title>Constellations Fixture</title></head><body>
                <nav>Secret navigation</nav>
                <form><textarea>Secret draft</textarea></form>
                <div contenteditable>Secret editable text</div>
                <p hidden>Secret hidden paragraph</p>
                <div style="opacity:0"><span>Secret opacity ancestor</span></div>
                <div style="content-visibility:hidden"><span>Secret content visibility ancestor</span></div>
                <div contenteditable="true"><span contenteditable="false">Secret editable descendant</span></div>
                <article><h1>Constellations</h1><p>Visible article paragraph.</p></article>
                </body></html>
            """.trimIndent()
        }.use { server ->
            val url = server.fixtureUrl("/constellations")
            val loaded = CountDownLatch(1)
            lateinit var factory: SystemWebViewBrowserEngineFactory
            lateinit var session: AndroidBrowserEngineSessionPort
            lateinit var host: FrameLayout
            composeRule.runOnIdle {
                factory = SystemWebViewBrowserEngineFactory(composeRule.activity)
                session = factory.create(
                    tabId = "page-text-${kind.name}",
                    profileId = "page-text-${kind.name}",
                    isPrivate = isPrivate,
                    contentKind = kind,
                    eventSink = BrowserEngineEventSink { event ->
                        if (event.type == BrowserEngineEventType.NavigationCommitted && event.address == url) {
                            loaded.countDown()
                        }
                    },
                )
                host = FrameLayout(composeRule.activity)
                composeRule.activity.addContentView(
                    host,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                host.addView(session.createView(composeRule.activity))
                session.execute(BrowserEngineCommands.load(url))
            }
            try {
                assertTrue("System WebView fixture did not load", loaded.await(20, TimeUnit.SECONDS))
                action(session, url)
            } finally {
                composeRule.runOnIdle {
                    host.removeAllViews()
                    session.execute(BrowserEngineCommands.close())
                    factory.shutdown()
                }
            }
        }
    }
}
