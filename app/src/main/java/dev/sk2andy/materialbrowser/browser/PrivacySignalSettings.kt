package dev.sk2andy.materialbrowser.browser

data class PrivacySignalSettings(
    val doNotTrackEnabled: Boolean = true,
    val globalPrivacyControlEnabled: Boolean = true,
) {
    fun requestHeaders(): Map<String, String> = buildMap {
        if (doNotTrackEnabled) put(DO_NOT_TRACK_HEADER, ENABLED_HEADER_VALUE)
        if (globalPrivacyControlEnabled) put(GLOBAL_PRIVACY_CONTROL_HEADER, ENABLED_HEADER_VALUE)
    }

    companion object {
        const val DO_NOT_TRACK_HEADER = "DNT"
        const val GLOBAL_PRIVACY_CONTROL_HEADER = "Sec-GPC"
        private const val ENABLED_HEADER_VALUE = "1"

        val Default = PrivacySignalSettings()
    }
}

internal object PrivacySignalDocumentScript {
    fun installScript(settings: PrivacySignalSettings): String =
        """
            (() => {
              "use strict";
              const defineSignal = (name, value) => {
                try {
                  Object.defineProperty(navigator, name, {
                    configurable: true,
                    enumerable: true,
                    value,
                    writable: false,
                  });
                } catch (_) {}
              };
              defineSignal(
                "doNotTrack",
                ${if (settings.doNotTrackEnabled) "\"1\"" else "null"},
              );
              defineSignal(
                "globalPrivacyControl",
                ${settings.globalPrivacyControlEnabled},
              );
            })();
        """.trimIndent()
}
