package dev.sk2andy.materialbrowser.browser.systemwebview

import android.content.Context
import dev.sk2andy.materialbrowser.browser.gecko.GeckoPrivacyPolicy
import org.json.JSONObject

/** Uses the same CSS policy as Gecko, with System WebView's synchronous native bridge. */
internal object SystemWebViewSafeAreaScript {
    const val BRIDGE_NAME = "CandySystemSafeArea"

    fun installScript(context: Context): String = buildString {
        appendLine(
            """
                (() => {
                  if (globalThis.self !== globalThis.top) return;
                  const bridge = globalThis.$BRIDGE_NAME;
                  if (!bridge) return;
                  if (globalThis.__candyConfigureCssSafeArea) {
                    globalThis.__candyConfigureCssSafeArea();
                    return;
                  }
                  globalThis.CandyContentTopInset = Object.freeze({
                    cssSafeAreaConfiguration: () => JSON.parse(bridge.configuration()),
                    fallbackToNative: (generation, revision, color = null, header = false) =>
                      bridge.fallbackToNative(generation, revision, color, header),
                    statusBarBackdrop: (generation, revision, color) =>
                      bridge.statusBarBackdrop(generation, revision, color),
                  });
            """.trimIndent(),
        )
        listOf("content_safe_area_reddit.js", "content_safe_area_prototype.js").forEach { name ->
            context.assets.open("candy_privacy/$name").bufferedReader(Charsets.UTF_8).use {
                appendLine(it.readText())
            }
        }
        appendLine("})();")
    }

    fun configuration(
        policy: GeckoPrivacyPolicy,
        revision: Long,
        topInsetPx: Int,
        nativeTopInsetPx: Int = 0,
    ): String {
        val settings = policy.geckoSafeAreaSettings.normalized()
        return JSONObject()
            .put("ready", true)
            .put("enabled", settings.enabled)
            .put("cssSafeAreaTopInsetPx", topInsetPx.coerceAtLeast(0))
            .put("nativeTopInsetPx", nativeTopInsetPx.coerceAtLeast(0))
            .put("navigationGeneration", policy.navigationGeneration)
            .put("revision", revision)
            .put("addInsetToNegativeTop", settings.addInsetToNegativeTop)
            .put("recheckAddedElements", settings.recheckAddedElements)
            .put("recheckChangedElements", settings.recheckChangedElements)
            .put("requireInteractionForUpdates", settings.requireInteractionForUpdates)
            .put("recheckOnResize", settings.recheckOnResize)
            .put("interactionWindowMillis", settings.interactionWindowMillis)
            .put("mutationDebounceMillis", settings.mutationDebounceMillis)
            .put("maxElementsPerBatch", settings.maxElementsPerBatch)
            .put("maxBatchDurationMillis", settings.maxBatchDurationMillis)
            .put("maxInitialElements", settings.maxInitialElements)
            .toString()
    }
}
