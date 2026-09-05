package dev.sk2andy.materialbrowser.browser

import java.net.URI

internal object DesktopViewportScript {
    private const val CLEANUP_KEY = "__candyDesktopViewportCleanup"
    private const val DESKTOP_VIEWPORT_WIDTH = 980

    fun allowedOrigins(desktopDomains: Collection<String>): Set<String> = desktopDomains.asSequence()
        .mapNotNull(DesktopSiteRules::normalizedDomain)
        .distinct()
        .sorted()
        .flatMap { domain ->
            sequenceOf(
                "https://$domain",
                "https://*.$domain",
                "http://$domain",
                "http://*.$domain",
            )
        }
        .toCollection(linkedSetOf())

    fun covers(pageUrl: String?, allowedOrigins: Collection<String>): Boolean {
        val uri = runCatching { URI(pageUrl ?: return false) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" }
            ?: return false
        val host = uri.host?.lowercase()?.trim('.')?.takeIf(String::isNotEmpty) ?: return false
        val explicitPort = uri.port.takeIf { port ->
            port >= 0 && !(
                (scheme == "http" && port == 80) ||
                    (scheme == "https" && port == 443)
                )
        }
        val origin = "$scheme://$host${explicitPort?.let { port -> ":$port" }.orEmpty()}"
        if (origin in allowedOrigins) return true
        if (explicitPort != null) return false
        val domain = DesktopSiteRules.domainForUrl(pageUrl) ?: return false
        return "$scheme://$domain" in allowedOrigins ||
            "$scheme://*.$domain" in allowedOrigins
    }

    fun create(desktopDomains: Collection<String>): String {
        val domains = desktopDomains.asSequence()
            .mapNotNull(DesktopSiteRules::normalizedDomain)
            .distinct()
            .sorted()
            .toList()
        if (domains.isEmpty()) return ""
        val encodedDomains = domains.joinToString(prefix = "[", postfix = "]") { domain ->
            "\"$domain\""
        }
        return """
            (() => {
              if (window.top !== window) return;
              const desktopDomains = $encodedDomains;
              const host = location.hostname.toLowerCase().replace(/^\.+|\.+${'$'}/g, '');
              const enabled = desktopDomains.some(domain =>
                host === domain || host.endsWith(`.${'$'}{domain}`)
              );
              if (!enabled) return;

              window.$CLEANUP_KEY?.();
              let observer = null;
              let applying = false;
              let stopped = false;
              const mobileViewportKeys = new Set([
                'height',
                'width',
                'initial-scale',
                'minimum-scale',
                'maximum-scale',
                'user-scalable',
                'target-densitydpi'
              ]);
              const forceDesktopViewport = () => {
                if (applying || stopped) return;
                applying = true;
                document.querySelectorAll('meta[name]').forEach(viewport => {
                  if (viewport.getAttribute('name')?.toLowerCase() !== 'viewport') return;
                  const directives = (viewport.getAttribute('content') || '')
                    .replace(/\s*=\s*/g, '=')
                    .split(/[,;]+/)
                    .map(directive => directive.trim())
                    .filter(Boolean)
                    .filter(directive => {
                      const key = directive.split('=', 1)[0].toLowerCase();
                      return !mobileViewportKeys.has(key);
                    });
                  directives.unshift('width=$DESKTOP_VIEWPORT_WIDTH');
                  directives.push('user-scalable=yes', 'maximum-scale=10');
                  const content = directives.join(', ');
                  if (viewport.getAttribute('content') !== content) {
                    viewport.setAttribute('content', content);
                  }
                });
                applying = false;
              };
              window.$CLEANUP_KEY = () => {
                stopped = true;
                observer?.disconnect();
              };
              observer = new MutationObserver(forceDesktopViewport);
              observer.observe(document, {
                attributes: true,
                attributeFilter: ['name', 'content'],
                childList: true,
                subtree: true
              });
              forceDesktopViewport();
            })();
        """.trimIndent()
    }
}
