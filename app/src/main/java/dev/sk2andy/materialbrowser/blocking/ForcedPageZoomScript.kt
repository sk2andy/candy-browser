package dev.sk2andy.materialbrowser.blocking

internal object ForcedPageZoomScript {
    private const val CLEANUP_KEY = "__materialBrowserForcePageZoomCleanup"

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
              let observer = null;
              let applying = false;
              let stopped = false;
              let startListener = null;
              const forceViewport = () => {
                if (applying || stopped) return;
                applying = true;
                document.querySelectorAll('meta[name]').forEach(viewport => {
                  if (viewport.getAttribute('name')?.toLowerCase() !== 'viewport') return;
                  const directives = (viewport.getAttribute('content') || '')
                    .replace(/\s*=\s*/g, '=')
                    .split(/[,;\s]+/)
                    .map(directive => directive.trim())
                    .filter(Boolean)
                    .filter(directive => {
                      const key = directive.split('=', 1)[0].toLowerCase();
                      return !['user-scalable', 'minimum-scale', 'maximum-scale'].includes(key);
                    });
                  directives.push('user-scalable=yes', 'maximum-scale=10');
                  const content = directives.join(', ');
                  if (viewport.getAttribute('content') !== content) {
                    viewport.setAttribute('content', content);
                  }
                });
                applying = false;
              };
              const start = () => {
                startListener = null;
                if (stopped || !document.documentElement) return;
                observer = new MutationObserver(forceViewport);
                observer.observe(document.documentElement, {
                  attributes: true,
                  attributeFilter: ['name', 'content'],
                  childList: true,
                  subtree: true
                });
                forceViewport();
              };
              window.$CLEANUP_KEY = () => {
                stopped = true;
                observer?.disconnect();
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
