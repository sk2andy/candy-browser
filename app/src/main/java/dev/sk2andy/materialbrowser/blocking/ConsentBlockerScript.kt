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
    private const val ACTION_OBSERVER = "__materialBrowserConsentActionObserver"
    private const val OBSERVER = "__materialBrowserCookieObserver"
    private const val CMP_SELECTORS =
        "#onetrust-consent-sdk,#CybotCookiebotDialog,#didomi-notice,#usercentrics-root," +
            "[data-testid=\"uc-default-wall\"],[id^=\"sp_message_container_\"]," +
            "#iubenda-cs-banner,.osano-cm-window,.qc-cmp2-container,.cmplz-cookiebanner," +
            "#fides-banner-container,#fides-overlay,.fides-modal-overlay," +
            "#BorlabsCookieBox,#didomi-host,#axeptio_overlay,[class^=\"axeptio_widget\"]," +
            "#cmpbox,.cky-consent-container,.cky-overlay"

    val cleanupScript = "window.$UNLOCK_FUNCTION && window.$UNLOCK_FUNCTION();"
    val removalScript = """
        (() => {
          document.getElementById('$STYLE_ID')?.remove();
          window.$ACTION_OBSERVER?.disconnect();
          delete window.$ACTION_OBSERVER;
          window.$OBSERVER?.disconnect();
          delete window.$OBSERVER;
          delete window.$UNLOCK_FUNCTION;
        })();
    """.trimIndent()

    fun create(
        cssBytes: ByteArray,
        pausedHosts: Collection<String> = emptyList(),
        siteRules: Collection<CandyRule> = emptyList(),
        actionRules: Collection<BundledConsentAction> = emptyList(),
    ): String {
        val encodedCss = Base64.getEncoder().encodeToString(cssBytes)
        val pausedHostArray = pausedHosts.asSequence()
            .mapNotNull(PrivacyRequestSanitizer::normalizeHost)
            .distinct()
            .sorted()
            .joinToString(prefix = "[", postfix = "]") { host -> "\"$host\"" }
        val encodedSiteRules = siteRules.asSequence()
            .filter { rule ->
                rule.active && rule.action == CandyRuleAction.Cosmetic &&
                    rule.kind == CandyRuleKind.CosmeticCss
            }
            .mapNotNull { rule ->
                val host = CandyHostCanonicalizer.canonicalHost(rule.firstPartyHost)
                    ?: return@mapNotNull null
                val selector = rule.cosmeticSelector ?: return@mapNotNull null
                host to Base64.getEncoder().encodeToString(selector.toByteArray(Charsets.UTF_8))
            }
            .distinct()
            .sortedWith(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .joinToString(prefix = "[", postfix = "]") { (host, selector) ->
                "{host:\"$host\",selector:\"$selector\"}"
            }
        val encodedActionRules = actionRules.asSequence()
            .map { action ->
                val selector = Base64.getEncoder()
                    .encodeToString(action.selector.toByteArray(Charsets.UTF_8))
                "{host:\"${action.frameHost}\",selector:\"$selector\"}"
            }
            .joinToString(prefix = "[", postfix = "]")
        return """
            (() => {
              const pausedHosts = $pausedHostArray;
              const frameHost = location.hostname.toLowerCase().replace(/\.$/, '');
              const hostMatches = (host, ruleHost) =>
                host === ruleHost || host.endsWith('.' + ruleHost);
              const isTopFrame = window.top === window;
              const scopeHosts = isTopFrame ? [frameHost] : [
                  frameHost,
                  ...Array.from(location.ancestorOrigins || []).map(origin => {
                    try { return new URL(origin).hostname.toLowerCase().replace(/\.$/, ''); }
                    catch (_) { return ''; }
                  }),
                  (() => {
                    try { return new URL(document.referrer).hostname.toLowerCase().replace(/\.$/, ''); }
                    catch (_) { return ''; }
                  })()
                ].filter(Boolean);
              if (pausedHosts.some(host => scopeHosts.some(scope => hostMatches(scope, host)))) return;
              const decodeBase64Utf8 = encoded => {
                const binary = atob(encoded);
                const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
                return new TextDecoder('utf-8').decode(bytes);
              };
              const consentActions = $encodedActionRules
                .filter(rule => frameHost === rule.host);
              const installConsentActionObserver = () => {
                if (!consentActions.length || window.$ACTION_OBSERVER) return;
                let observer = null;
                let retryTimer = null;
                let timeoutTimer = null;
                let startListener = null;
                let clickedSelector = null;
                let attempts = 0;
                let queued = false;
                let stopped = false;
                let observerHandle = null;
                const query = selector => {
                  try { return document.querySelector(selector); }
                  catch (_) { return null; }
                };
                const stop = () => {
                  if (stopped) return;
                  stopped = true;
                  observer?.disconnect();
                  if (retryTimer !== null) clearTimeout(retryTimer);
                  if (timeoutTimer !== null) clearTimeout(timeoutTimer);
                  if (startListener !== null) {
                    document.removeEventListener('DOMContentLoaded', startListener);
                  }
                  if (window.$ACTION_OBSERVER === observerHandle) {
                    delete window.$ACTION_OBSERVER;
                  }
                };
                const attempt = () => {
                  if (stopped) return;
                  if (clickedSelector && !query(clickedSelector)) {
                    stop();
                    return;
                  }
                  if (retryTimer !== null || attempts >= 3) return;
                  const match = consentActions.map(rule => ({
                    selector: decodeBase64Utf8(rule.selector),
                    control: query(decodeBase64Utf8(rule.selector))
                  })).find(candidate => candidate.control?.isConnected &&
                    !candidate.control.disabled &&
                    typeof candidate.control.click === 'function');
                  if (!match) return;
                  clickedSelector = match.selector;
                  attempts += 1;
                  match.control.click();
                  retryTimer = setTimeout(() => {
                    retryTimer = null;
                    attempt();
                  }, 500);
                };
                const start = () => {
                  startListener = null;
                  if (stopped || !document.documentElement) return;
                  observer = new MutationObserver(() => {
                    if (queued || retryTimer !== null) return;
                    queued = true;
                    queueMicrotask(() => {
                      queued = false;
                      attempt();
                    });
                  });
                  observer.observe(document.documentElement, { childList: true, subtree: true });
                  attempt();
                };
                observerHandle = { disconnect: stop };
                window.$ACTION_OBSERVER = observerHandle;
                timeoutTimer = setTimeout(stop, 15000);
                if (document.documentElement) start();
                else {
                  startListener = start;
                  document.addEventListener('DOMContentLoaded', startListener, { once: true });
                }
              };
              installConsentActionObserver();

              if (!isTopFrame) return;
              const pageHost = frameHost;
              const siteRules = $encodedSiteRules;
              const siteSelectors = siteRules
                .filter(rule => hostMatches(pageHost, rule.host))
                .map(rule => decodeBase64Utf8(rule.selector));
              const styleId = '$STYLE_ID';
              if (document.getElementById(styleId)) return;

              const target = document.head || document.documentElement;
              if (!target) return;

              const style = document.createElement('style');
              style.id = styleId;
              style.textContent = decodeBase64Utf8('$encodedCss') +
                (siteSelectors.length
                  ? '\n' + siteSelectors.map(selector => selector +
                    '{display:none!important;height:0!important;visibility:hidden!important}'
                  ).join('\n')
                  : '');
              target.appendChild(style);

              const unlockCookieScroll = () => {
                const selectors = ['$CMP_SELECTORS', ...siteSelectors].filter(Boolean);
                const hiddenBanner = selectors.some(selector => {
                  try {
                    return Array.from(document.querySelectorAll(selector)).some(banner =>
                      getComputedStyle(banner).display === 'none'
                    );
                  } catch (_) {
                    return false;
                  }
                });
                if (!hiddenBanner) return false;

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
