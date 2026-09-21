package dev.sk2andy.materialbrowser.browser.gecko

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

@RunWith(AndroidJUnit4::class)
class GeckoInlineVideoOpenRequestInstrumentedTest {
    @Test
    fun nativeHostReplaysAcknowledgedOpenAfterCurrentPolicyAndCandidateInEitherOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val runtime = GeckoRuntimeOwner.getOrCreate(context)
            val host = field(runtime, "privacyHost") as GeckoViewPrivacyHostRuntime
            listOf(false, true).forEach { candidateFirst ->
                val opens = mutableListOf<GeckoInlineVideoOpenRequest>()
                val events = mutableListOf<String>()
                val session = GeckoSession()
                val binding = host.bind(
                    session = session,
                    policy = policy,
                    sink = GeckoPrivacyEventSink {},
                    onScrollMetrics = {},
                    onMainFrameResponse = {},
                    onInlineVideoState = { events += "state" },
                    onInlineVideoOpenRequest = { opens += it; events += "open" },
                    onInlineVideoGestureHaptic = {},
                    onBound = {},
                    onFailure = {},
                )
                try {
                    val nativeBinding = (field(host, "bindings") as Map<*, *>).values.single {
                        it != null && field(it, "session") === session
                    }!!
                    // Drive the real host receiver atomically on main, without racing Gecko's
                    // asynchronous transport. This isolated binding never navigates a page.
                    nativeBinding.javaClass.getDeclaredField("handshake").apply {
                        isAccessible = true
                        set(nativeBinding, GeckoPrivacyBindingHandshake(bootstrapStarted = true, sessionBound = true))
                    }
                    val publish = host.javaClass.getDeclaredMethod(
                        "publish", nativeBinding.javaClass, GeckoPrivacyPolicy::class.java, Function0::class.java,
                    ).apply { isAccessible = true }
                    repeat(3) { publish.invoke(host, nativeBinding, policy, null) }
                    val token = field(nativeBinding, "token") as String
                    fun deliver(type: String, revision: Int) {
                        val message = JSONObject()
                            .put("type", type)
                            .put("protocolVersion", CandyPrivacyHostContract.PROTOCOL_VERSION)
                            .put("token", token)
                            .put("revision", revision)
                            .put("navigationGeneration", 2)
                            .put("mode", "button_fullscreen")
                            .put("expected", true)
                            .put("active", true)
                            .put("playing", true)
                            .put("presented", false)
                            .put("videoWidth", 1280)
                            .put("videoHeight", 720)
                            .put("documentNonce", "a".repeat(32))
                            .put("elementNonce", "b".repeat(32))
                        host.javaClass.getDeclaredMethod(
                            "onPortMessage", Any::class.java, WebExtension.Port::class.java,
                        ).apply { isAccessible = true }.invoke(host, message, field(host, "port"))
                    }
                    deliver("policy-ready", 3)
                    deliver("inline-video-state", 3)
                    publish.invoke(host, nativeBinding, policy.copy(topInsetPx = 0), null)
                    deliver("inline-video-open-request", 3)
                    assertEquals(emptyList<GeckoInlineVideoOpenRequest>(), opens)
                    events.clear()

                    deliver(if (candidateFirst) "inline-video-state" else "policy-ready", 4)
                    assertEquals(emptyList<GeckoInlineVideoOpenRequest>(), opens)
                    deliver(if (candidateFirst) "policy-ready" else "inline-video-state", 4)

                    assertEquals(
                        listOf(GeckoInlineVideoOpenRequest(
                            GeckoInlineVideoIdentity("a".repeat(32), "b".repeat(32)),
                            navigationGeneration = 2,
                        )),
                        opens,
                    )
                    assertEquals(listOf("state", "open"), events)
                    deliver("policy-ready", 4)
                    deliver("inline-video-state", 4)
                    assertEquals(1, opens.size)
                } finally {
                    binding.close()
                }
            }
        }
    }

    private fun field(owner: Any, name: String): Any? =
        owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)

    private companion object {
        val policy = GeckoPrivacyPolicy.Disabled.copy(
            navigationGeneration = 2,
            inlineMediaPlayerEnabled = true,
            inlineMediaPlayerMode = "button_fullscreen",
            topInsetPx = 24,
        )
    }
}
