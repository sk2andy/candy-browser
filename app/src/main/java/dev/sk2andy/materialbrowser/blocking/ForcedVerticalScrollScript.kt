package dev.sk2andy.materialbrowser.blocking

internal object ForcedVerticalScrollScript {
    private const val CLEANUP_KEY = "__materialBrowserForceVerticalScrollCleanup"

    val cleanupScript: String = """
        (() => {
          window.$CLEANUP_KEY?.();
          delete window.$CLEANUP_KEY;
        })();
    """.trimIndent()

    fun create(forcedHosts: Collection<String>): String {
        val hosts = forcedHosts.asSequence()
            .mapNotNull(SiteExceptionRules::normalizedException)
            .distinct()
            .sorted()
            .toList()
        if (hosts.isEmpty()) return ""
        val encodedHosts = hosts.joinToString(prefix = "[", postfix = "]") { host ->
            "\"$host\""
        }
        return """
            (() => {
              if (window.top !== window) return;
              const forcedHosts = $encodedHosts;
              const host = location.hostname.toLowerCase().replace(/^\.+|\.+${'$'}/g, '');
              if (!forcedHosts.includes(host)) return;

              window.$CLEANUP_KEY?.();
              let rootObserver = null;
              let bodyObserver = null;
              let observedBody = null;
              let applying = false;
              let stopped = false;
              let startListener = null;
              const forceProperty = (element, property, value) => {
                if (!element) return;
                if (
                  element.style.getPropertyValue(property) !== value ||
                  element.style.getPropertyPriority(property) !== 'important'
                ) {
                  element.style.setProperty(property, value, 'important');
                }
              };
              const forceScroll = () => {
                if (applying || stopped) return;
                applying = true;
                const root = document.documentElement;
                const body = document.body;
                forceProperty(root, 'overflow-y', 'auto');
                forceProperty(root, 'height', 'auto');
                forceProperty(root, 'max-height', 'none');
                forceProperty(body, 'overflow-y', 'auto');
                forceProperty(body, 'position', 'static');
                forceProperty(body, 'top', 'auto');
                forceProperty(body, 'height', 'auto');
                forceProperty(body, 'max-height', 'none');
                applying = false;
              };
              const observeBody = () => {
                  if (!document.body) return false;
                  if (document.body === observedBody) return true;
                  bodyObserver?.disconnect();
                  observedBody = document.body;
                  bodyObserver = new MutationObserver(forceScroll);
                  bodyObserver.observe(observedBody, {
                    attributes: true,
                    attributeFilter: ['class', 'style']
                  });
                  forceScroll();
                  return true;
              };
              const start = () => {
                startListener = null;
                if (stopped || !document.documentElement) return;
                forceScroll();
                const root = document.documentElement;
                rootObserver = new MutationObserver(() => {
                  observeBody();
                  forceScroll();
                });
                rootObserver.observe(root, {
                  attributes: true,
                  attributeFilter: ['class', 'style'],
                  childList: true
                });
                observeBody();
              };
              window.$CLEANUP_KEY = () => {
                stopped = true;
                rootObserver?.disconnect();
                bodyObserver?.disconnect();
                if (startListener) {
                  document.removeEventListener('readystatechange', startListener);
                }
              };
              if (document.documentElement) start();
              else {
                startListener = start;
                document.addEventListener('readystatechange', startListener, { once: true });
              }
            })();
        """.trimIndent()
    }
}
