package dev.sk2andy.materialbrowser.browser.gecko

import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sk2andy.materialbrowser.browser.BrowserViewportRect
import dev.sk2andy.materialbrowser.browser.EdgeToEdgeSiteFixtureServer
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeMode
import dev.sk2andy.materialbrowser.browser.TextInputOcclusionProbeResult
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeckoPageControlOcclusionInstrumentedTest {
    @Test
    fun bottomControlsOnLongPagesAreDetectedWithoutFocusOrKeyboard() {
        val title = AtomicReference<String?>(null)
        EdgeToEdgeSiteFixtureServer { target -> fixtureHtml(target.substringAfterLast('/')) }.use { server ->
            ActivityScenario.launch(GeckoScrollTestActivity::class.java).use { scenario ->
                lateinit var session: GeckoBrowserSession
                lateinit var view: View
                scenario.onActivity { activity ->
                    session = GeckoRuntimeOwner.getOrCreate(activity).createSession(
                        profileId = "page-control-${UUID.randomUUID()}",
                        isPrivate = false,
                        privacyPolicy = GeckoPrivacyPolicy.Disabled,
                    )
                    session.bindExtensionTab("page-control-${UUID.randomUUID()}", 1)
                    session.setStateListener { state -> title.set(state.title) }
                    view = session.createView(activity)
                    activity.setContentView(view)
                    session.setActive(true)
                }
                try {
                    for ((fixture, expected) in listOf(
                        "shadow-input" to TextInputOcclusionProbeResult.Occluded,
                        "fixed-navigation" to TextInputOcclusionProbeResult.Occluded,
                        "sticky-navigation" to TextInputOcclusionProbeResult.Occluded,
                        "chat-frame" to TextInputOcclusionProbeResult.Occluded,
                        "disabled-navigation" to TextInputOcclusionProbeResult.NoFocusedTextInput,
                        "covered-input" to TextInputOcclusionProbeResult.NoFocusedTextInput,
                        "decorative-footer" to TextInputOcclusionProbeResult.NoFocusedTextInput,
                    )) {
                        scenario.onActivity {
                            assertTrue(session.loadUrl(server.fixtureUrl("/site-matrix/$fixture")))
                        }
                        val deadline = SystemClock.elapsedRealtime() + 30_000L
                        while (title.get() != "Ready $fixture" && SystemClock.elapsedRealtime() < deadline) {
                            SystemClock.sleep(50L)
                        }
                        assertEquals("Fixture ready", "Ready $fixture", title.get())
                        var result = probe(scenario, session)
                        while (result != expected && SystemClock.elapsedRealtime() < deadline) {
                            SystemClock.sleep(100L)
                            result = probe(scenario, session)
                        }
                        assertEquals(fixture, expected, result)
                    }
                } finally {
                    scenario.onActivity {
                        session.releaseView(view)
                        session.setActive(false)
                        session.close()
                    }
                }
            }
        }
    }

    private fun probe(
        scenario: ActivityScenario<GeckoScrollTestActivity>,
        session: GeckoBrowserSession,
    ): TextInputOcclusionProbeResult {
        val result = AtomicReference<TextInputOcclusionProbeResult>()
        val completed = CountDownLatch(1)
        scenario.onActivity {
            session.probeTextInputOcclusion(
                viewportRect = BrowserViewportRect(
                    leftFraction = 0.05f,
                    topFraction = 0.8f,
                    rightFraction = 0.95f,
                    bottomFraction = 0.98f,
                ),
                mode = TextInputOcclusionProbeMode.AllEditors,
            ) { value ->
                result.set(value)
                completed.countDown()
            }
        }
        assertTrue("Extension probe response", completed.await(10, TimeUnit.SECONDS))
        return requireNotNull(result.get())
    }

    private fun fixtureHtml(fixture: String): String {
        val bottomStyle = "position:fixed;bottom:20px;left:10%;width:80%;height:80px"
        val content = when (fixture) {
            "shadow-input" -> """
                <div id="host"></div>
                <script>
                  let parent = document.getElementById('host').attachShadow({mode:'open'});
                  for (let depth = 0; depth < 30; depth += 1) {
                    const child = document.createElement('div');
                    parent.appendChild(child);
                    parent = child;
                  }
                  const editor = document.createElement('div');
                  editor.contentEditable = 'true';
                  editor.style = '$bottomStyle';
                  editor.textContent = 'Ask anything';
                  parent.appendChild(editor);
                </script>
            """.trimIndent()
            "fixed-navigation" -> "<nav style='$bottomStyle'><a href='/inbox' style='display:block;height:80px'>Inbox</a></nav>"
            "sticky-navigation" -> """
                <div style="height:calc(100vh - 100px)"></div>
                <nav style="position:sticky;bottom:20px;height:80px">
                  <button style="width:100%;height:80px">Inbox</button>
                </nav>
            """.trimIndent()
            "chat-frame" -> "<iframe style='$bottomStyle' srcdoc='<button>Chat</button>'></iframe>"
            "disabled-navigation" -> "<nav style='$bottomStyle'><button disabled style='width:100%;height:80px'>Inbox</button></nav>"
            "covered-input" -> "<textarea style='$bottomStyle'></textarea><div style='$bottomStyle;background:white;z-index:99'></div>"
            else -> "<footer style='$bottomStyle'>Copyright</footer>"
        }
        return """
            <!doctype html>
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0">
              $content
              <main style="height:300vh"></main>
              <script>
                requestAnimationFrame(() => requestAnimationFrame(() => document.title='Ready $fixture'));
              </script>
            </body></html>
        """.trimIndent()
    }
}
