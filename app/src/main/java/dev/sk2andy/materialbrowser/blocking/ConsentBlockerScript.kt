package dev.sk2andy.materialbrowser.blocking

import java.util.Base64

internal object ConsentBlockerScript {
    private const val STYLE_ID = "material-browser-easylist-cookie-css"
    private const val UNLOCK_FUNCTION = "__materialBrowserUnlockCookieScroll"
    private const val CMP_SELECTORS =
        "#onetrust-consent-sdk,#CybotCookiebotDialog,#didomi-notice,#usercentrics-root," +
            "[data-testid=\"uc-default-wall\"],[id^=\"sp_message_container_\"]," +
            "#iubenda-cs-banner,.osano-cm-window,.qc-cmp2-container,.cmplz-cookiebanner"

    val cleanupScript = "window.$UNLOCK_FUNCTION && window.$UNLOCK_FUNCTION();"
    val removalScript = """
        (() => {
          document.getElementById('$STYLE_ID')?.remove();
          delete window.$UNLOCK_FUNCTION;
        })();
    """.trimIndent()

    fun create(cssBytes: ByteArray): String {
        val encodedCss = Base64.getEncoder().encodeToString(cssBytes)
        return """
            (() => {
              const styleId = '$STYLE_ID';
              if (document.getElementById(styleId)) return;

              const target = document.head || document.documentElement;
              if (!target) return;

              const binaryCss = atob('$encodedCss');
              const cssBytes = Uint8Array.from(binaryCss, character => character.charCodeAt(0));
              const style = document.createElement('style');
              style.id = styleId;
              style.textContent = new TextDecoder('utf-8').decode(cssBytes);
              target.appendChild(style);

              const unlockCookieScroll = () => {
                const banner = document.querySelector('$CMP_SELECTORS');
                if (!banner || getComputedStyle(banner).display !== 'none') return false;

                [document.documentElement, document.body].forEach(element => {
                  if (!element) return;
                  ['overflow', 'overflow-y'].forEach(property => {
                    const value = element.style.getPropertyValue(property).trim();
                    if (value === 'hidden' || value === 'clip') {
                      element.style.removeProperty(property);
                    }
                  });
                });
                return true;
              };
              window.$UNLOCK_FUNCTION = unlockCookieScroll;
              unlockCookieScroll();
            })();
        """.trimIndent()
    }
}
