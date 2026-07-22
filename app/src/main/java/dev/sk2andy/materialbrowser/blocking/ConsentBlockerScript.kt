package dev.sk2andy.materialbrowser.blocking

import java.util.Base64

internal object ConsentBlockerScript {
    // Generic cosmetic rules come from EasyList Cookie. Installation at document start follows
    // AndroidX WebKit guidance and DuckDuckGo's early-script integration, while the small cleanup
    // hook only unlocks scrolling after a known CMP was actually hidden.
    // https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set)
    // https://github.com/duckduckgo/Android/blob/4472de82e610b12689dcd2fc1b8421439020af62/app/src/main/java/com/duckduckgo/app/browser/DuckDuckGoWebView.kt
    private const val STYLE_ID = "material-browser-easylist-cookie-css"
    private const val UNLOCK_FUNCTION = "__materialBrowserUnlockCookieScroll"
    private const val OBSERVER = "__materialBrowserCookieObserver"
    private const val CMP_SELECTORS =
        "#onetrust-consent-sdk,#CybotCookiebotDialog,#didomi-notice,#usercentrics-root," +
            "[data-testid=\"uc-default-wall\"],[id^=\"sp_message_container_\"]," +
            "#iubenda-cs-banner,.osano-cm-window,.qc-cmp2-container,.cmplz-cookiebanner"

    val cleanupScript = "window.$UNLOCK_FUNCTION && window.$UNLOCK_FUNCTION();"
    val removalScript = """
        (() => {
          document.getElementById('$STYLE_ID')?.remove();
          window.$OBSERVER?.disconnect();
          delete window.$OBSERVER;
          delete window.$UNLOCK_FUNCTION;
        })();
    """.trimIndent()

    fun create(cssBytes: ByteArray, pausedHosts: Collection<String> = emptyList()): String {
        val encodedCss = Base64.getEncoder().encodeToString(cssBytes)
        val pausedHostArray = pausedHosts.asSequence()
            .mapNotNull(PrivacyRequestSanitizer::normalizeHost)
            .distinct()
            .sorted()
            .joinToString(prefix = "[", postfix = "]") { host -> "\"$host\"" }
        return """
            (() => {
              if (window.top !== window) return;
              const pausedHosts = $pausedHostArray;
              const pageHost = location.hostname.toLowerCase().replace(/\.$/, '');
              if (pausedHosts.some(host => pageHost === host || pageHost.endsWith('.' + host))) return;
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

              if (!window.$OBSERVER && document.documentElement) {
                let cleanupQueued = false;
                let disconnectScheduled = false;
                const scheduleCleanup = () => {
                  if (cleanupQueued) return;
                  cleanupQueued = true;
                  queueMicrotask(() => {
                    cleanupQueued = false;
                    if (unlockCookieScroll() && !disconnectScheduled) {
                      disconnectScheduled = true;
                      setTimeout(() => {
                        if (window.$OBSERVER === observerHandle) {
                          observerHandle.disconnect();
                          delete window.$OBSERVER;
                        }
                      }, 5000);
                    }
                  });
                };
                const treeObserver = new MutationObserver(scheduleCleanup);
                const lockObserver = new MutationObserver(scheduleCleanup);
                const observerHandle = {
                  disconnect: () => {
                    treeObserver.disconnect();
                    lockObserver.disconnect();
                  }
                };
                window.$OBSERVER = observerHandle;
                treeObserver.observe(document.documentElement, {
                  childList: true,
                  subtree: true
                });
                const observeScrollLock = () => {
                  [document.documentElement, document.body].forEach(element => {
                    if (!element || !window.$OBSERVER) return;
                    lockObserver.observe(element, {
                      attributes: true,
                      attributeFilter: ['class', 'style']
                    });
                  });
                };
                observeScrollLock();
                document.addEventListener('DOMContentLoaded', observeScrollLock, { once: true });
              }
            })();
        """.trimIndent()
    }
}
