package dev.sk2andy.materialbrowser.browser

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

internal object EdgeToEdgeSiteMatrix {
    val requestedSites = listOf(
        Site("YouTube", Layout.Fixed, focusedSearch = true),
        Site("Google", Layout.Absolute, focusedSearch = true),
        Site("ESPN", Layout.Sticky),
        Site("New York Times", Layout.Flow),
        Site("CNN", Layout.Sticky),
        Site("Reddit", Layout.CoverWithoutSafeArea),
        Site("Facebook", Layout.Fixed),
        Site("IKEA", Layout.Sticky),
        Site("GitHub", Layout.Flow),
        Site("Discord", Layout.Fixed),
        Site("Instagram", Layout.CoverWithSafeArea),
    )

    val additionalSites = listOf(
        Site("Vimeo", Layout.LateSticky),
        Site("Wikipedia", Layout.Flow),
        Site("Stack Overflow", Layout.Sticky),
        Site("DuckDuckGo", Layout.Absolute, focusedSearch = true),
    )

    val allSites = requestedSites + additionalSites
    val focusedSearchSites = allSites.filter(Site::focusedSearch)
    fun readyTitle(site: Site): String = "Candy site safe: ${site.name}"

    fun html(site: Site): String {
        val cases = "{name:${site.name.jsQuoted()},layout:${site.layout.name.jsQuoted()}," +
            "focusedSearch:${site.focusedSearch}}"
        return """
            <!doctype html>
            <html>
              <head>
                <title>Preparing Candy site matrix</title>
                <meta id="viewport" name="viewport" content="width=device-width,initial-scale=1">
                <style>
                  html, body { margin: 0; min-height: 250vh; }
                  #header {
                    box-sizing: border-box; width: 100%; height: 64px;
                    background: #fff; position: relative;
                  }
                  #header.fixed { position: fixed; inset: 0 0 auto 0; }
                  #header.sticky { position: sticky; top: 0; }
                  #header.absolute { position: absolute; inset: 0 0 auto 0; }
                  #header.transformed {
                    position: fixed; inset: 0 0 auto 0; transform: translateZ(0);
                  }
                  #header.cover-without-safe-area {
                    position: fixed; inset: 0 0 auto 0; transform: translateZ(0);
                  }
                  #header.late-sticky { position: sticky; top: 0; margin-top: 180px; }
                  #header.safe-area {
                    position: fixed; inset: 0 0 auto 0; padding-top: env(safe-area-inset-top);
                  }
                  #search {
                    box-sizing: border-box; position: absolute; left: 72px; top: 12px;
                    width: calc(100% - 84px); height: 40px;
                  }
                  #focused-search { position: fixed; inset: 0 0 auto 0; height: 64px; background: #fff; }
                  main { padding-top: 96px; height: 2400px; }
                </style>
              </head>
              <body></body>
              <script>
                (() => {
                  const cases = [$cases];
                  const results = [];
                  const frame = () => new Promise((resolve) => requestAnimationFrame(resolve));
                  const settleCandyLayout = async () => {
                    for (
                      let attempt = 0;
                      attempt < 120 &&
                        typeof globalThis.__candyReconcileContentTopInset !== 'function';
                      attempt++
                    ) {
                      await frame();
                    }
                    for (let pass = 0; pass < 4; pass++) await frame();
                  };
                  const settleScrollLayout = async (site) => {
                    await settleCandyLayout();
                    if (site.layout !== 'LateSticky') return;
                    await new Promise((resolve) => setTimeout(resolve, 500));
                    for (let pass = 0; pass < 4; pass++) await frame();
                  };
                  const headerClass = (layout) => ({
                    Fixed: 'fixed',
                    Sticky: 'sticky',
                    Absolute: 'absolute',
                    TransformedFixed: 'transformed',
                    CoverWithoutSafeArea: 'cover-without-safe-area',
                    LateSticky: 'late-sticky',
                    CoverWithSafeArea: 'safe-area',
                    Flow: ''
                  })[layout];
                  const renderSite = (site) => {
                    const viewport = document.querySelector('#viewport');
                    viewport.content = [
                      'CoverWithoutSafeArea',
                      'CoverWithSafeArea'
                    ].includes(site.layout)
                      ? 'width=device-width,initial-scale=1,viewport-fit=cover'
                      : 'width=device-width,initial-scale=1';
                    document.body.innerHTML = `
                      <header id="header" class="${'$'}{headerClass(site.layout)}">
                        <button type="button">Menu</button>
                        <input id="search" aria-label="${'$'}{site.name} search">
                      </header>
                      <form id="focused-search" hidden>
                        <input id="focused-query" aria-label="Focused ${'$'}{site.name} search">
                        <button type="button">Close</button>
                      </form>
                      <main>${'$'}{site.name}</main>`;
                    const search = document.querySelector('#search');
                    search.addEventListener('focus', async () => {
                      document.querySelector('#header').hidden = true;
                      document.querySelector('#focused-search').hidden = false;
                      const focusedQuery = document.querySelector('#focused-query');
                      focusedQuery.focus({ preventScroll: true });
                      await settleCandyLayout();
                      const requiredTop = Number.parseFloat(
                        document.documentElement.style.getPropertyValue(
                          '--candy-browser-content-top-inset'
                        )
                      ) || 0;
                      const focusedTop = document.querySelector('#focused-search')
                        .getBoundingClientRect().top;
                      const queryTop = focusedQuery.getBoundingClientRect().top;
                      const safe = requiredTop > 0 &&
                        focusedTop >= requiredTop - 0.5 &&
                        queryTop >= requiredTop - 0.5;
                      document.title = safe
                        ? `Candy focused search safe: ${'$'}{site.name}`
                        : `Candy focused search failed: ${'$'}{site.name}`;
                    }, { once: true });
                  };
                  globalThis.__candyShowFocusedSearchProfile = (name) => {
                    const site = cases.find((candidate) =>
                      candidate.name === name && candidate.focusedSearch
                    );
                    if (!site) return false;
                    renderSite(site);
                    settleCandyLayout().then(() => {
                      document.title = `Candy focused search ready: ${'$'}{site.name}`;
                    });
                    return true;
                  };
                  addEventListener('hashchange', () => {
                    globalThis.__candyShowFocusedSearchProfile(
                      decodeURIComponent(location.hash.slice(1))
                    );
                  });
                  const run = async () => {
                    for (const site of cases) {
                      renderSite(site);
                      const search = document.querySelector('#search');
                      await settleCandyLayout();
                      const candyTopInset = Number.parseFloat(
                        document.documentElement.style.getPropertyValue(
                          '--candy-browser-content-top-inset'
                        )
                      ) || 0;
                      const beforeFocusTop = document.querySelector('#header')
                        .getBoundingClientRect().top;
                      const header = document.querySelector('#header');
                      const ownedOffsetBeforeVisibilityChange = header.style.getPropertyValue(
                        '--candy-browser-owned-top-inset-offset'
                      );
                      if (site.name === 'Vimeo') {
                        header.style.display = 'none';
                        await settleCandyLayout();
                        header.style.display = '';
                        await settleCandyLayout();
                      }
                      const ownedOffsetAfterVisibilityChange = header.style.getPropertyValue(
                        '--candy-browser-owned-top-inset-offset'
                      );
                      const offsetStableAcrossVisibilityChange = site.name !== 'Vimeo' ||
                        ownedOffsetBeforeVisibilityChange === ownedOffsetAfterVisibilityChange;
                      scrollTo(0, 480);
                      await settleScrollLayout(site);
                      const topWhileScrolled = document.querySelector('#header')
                        .getBoundingClientRect().top;
                      scrollTo(0, 0);
                      await settleScrollLayout(site);
                      const afterScrollTop = document.querySelector('#header')
                        .getBoundingClientRect().top;
                      if (site.focusedSearch) {
                        search.focus({ preventScroll: true });
                        search.dispatchEvent(new FocusEvent('focus'));
                        search.dispatchEvent(new InputEvent('input', {
                          bubbles: true,
                          inputType: 'insertText',
                          data: 'c'
                        }));
                        await settleCandyLayout();
                      }
                      const protectedElement = site.focusedSearch
                        ? document.querySelector('#focused-search')
                        : document.querySelector('#header');
                      const protectedTop = protectedElement.getBoundingClientRect().top;
                      const protectedOwned = protectedElement.getAttribute(
                        'data-candy-browser-top-inset-offset'
                      );
                      const protectedOffset = protectedElement.style.getPropertyValue(
                        '--candy-browser-owned-top-inset-offset'
                      );
                      const safeAreaPaddingTop = Number.parseFloat(
                        getComputedStyle(document.querySelector('#header')).paddingTop
                      ) || 0;
                      const focusedSearch = document.querySelector('#focused-search');
                      const focusedQuery = document.querySelector('#focused-query');
                      const focused = !site.focusedSearch || !focusedSearch.hidden;
                      const focusedQueryTop = focusedQuery.getBoundingClientRect().top;
                      const candyCompatibilityApplied = Boolean(
                        document.querySelector('style[data-candy-browser-owned="true"]')
                      );
                      const usesEngineSafeArea = site.layout === 'CoverWithSafeArea' &&
                        !candyCompatibilityApplied && safeAreaPaddingTop > 0;
                      const protectionModeValid = usesEngineSafeArea ||
                        (candyCompatibilityApplied && candyTopInset > 0);
                      const ownedProtectionValid = site.layout === 'Flow' ||
                        protectedOwned === 'true' &&
                        protectedOffset.endsWith('px');
                      const requiredTop = usesEngineSafeArea ? 0 : candyTopInset;
                      results.push({
                        name: site.name,
                        beforeFocusTop,
                        topWhileScrolled,
                        afterScrollTop,
                        protectedTop,
                        protectedOwned,
                        protectedOffset,
                        safeAreaPaddingTop,
                        focused,
                        candyTopInset,
                        candyCompatibilityApplied,
                        protectionModeValid,
                        failureReason: document.documentElement.getAttribute(
                          'data-candy-browser-top-inset-failure'
                        ),
                        offsetStableAcrossVisibilityChange,
                        passed: beforeFocusTop >= requiredTop - 0.5 &&
                          afterScrollTop >= requiredTop - 0.5 &&
                          (['Flow', 'Absolute'].includes(site.layout) ||
                            topWhileScrolled >= requiredTop - 0.5) &&
                          protectedTop >= requiredTop - 0.5 &&
                          (!site.focusedSearch || focusedQueryTop >= requiredTop - 0.5) &&
                          (!site.focusedSearch ||
                            Math.abs(protectedTop - beforeFocusTop) <= 0.5) &&
                          focused && protectionModeValid && ownedProtectionValid &&
                          offsetStableAcrossVisibilityChange
                      });
                    }
                    globalThis.__candySiteMatrix = {
                      results,
                      passed: results.every((result) => result.passed),
                      focusedCount: results.filter((result) =>
                        ['YouTube', 'Google', 'DuckDuckGo'].includes(result.name) && result.focused
                      ).length
                    };
                    document.title = globalThis.__candySiteMatrix.passed
                      ? ${readyTitle(site).jsQuoted()}
                      : 'Candy site matrix failed: ' + JSON.stringify(
                          results.filter((result) => !result.passed)
                        );
                  };
                  const focusedProfile = decodeURIComponent(location.hash.slice(1));
                  if (!focusedProfile || !globalThis.__candyShowFocusedSearchProfile(focusedProfile)) {
                    run();
                  }
                })();
              </script>
            </html>
        """.trimIndent()
    }

    internal data class Site(
        val name: String,
        val layout: Layout,
        val focusedSearch: Boolean = false,
    )

    internal enum class Layout {
        Flow,
        Fixed,
        Sticky,
        Absolute,
        TransformedFixed,
        CoverWithoutSafeArea,
        LateSticky,
        CoverWithSafeArea,
    }

    private fun String.jsQuoted(): String =
        replace("\\", "\\\\").replace("'", "\\'").let { "'$it'" }
}

internal class EdgeToEdgeSiteFixtureServer : Closeable {
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val thread = Thread(::serve, "edge-to-edge-site-matrix").apply {
        isDaemon = true
        start()
    }
    val documentRequestCount = AtomicInteger()
    val url = siteUrl(EdgeToEdgeSiteMatrix.allSites.first())

    fun siteUrl(site: EdgeToEdgeSiteMatrix.Site): String =
        "http://127.0.0.1:${server.localPort}/site-matrix?site=" +
            URLEncoder.encode(site.name, StandardCharsets.UTF_8.name())

    private fun serve() {
        while (!server.isClosed) {
            try {
                server.accept().use { connection ->
                    val reader = connection.getInputStream().bufferedReader()
                    val requestLine = reader.readLine().orEmpty()
                    val requestTarget = requestLine.split(' ').getOrNull(1).orEmpty()
                    if (requestTarget.startsWith("/site-matrix")) {
                        documentRequestCount.incrementAndGet()
                    }
                    while (true) {
                        val header = reader.readLine() ?: break
                        if (header.isEmpty()) break
                    }
                    val requestedName = requestTarget.substringAfter("site=", "")
                        .substringBefore('&')
                        .let { encoded ->
                            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                        }
                    val site = EdgeToEdgeSiteMatrix.allSites.firstOrNull { candidate ->
                        candidate.name == requestedName
                    } ?: EdgeToEdgeSiteMatrix.allSites.first()
                    val body = EdgeToEdgeSiteMatrix.html(site).toByteArray()
                    connection.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\n".toByteArray())
                        write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                        write("Cache-Control: no-store\r\n".toByteArray())
                        write("Content-Length: ${body.size}\r\n".toByteArray())
                        write("Connection: close\r\n\r\n".toByteArray())
                        write(body)
                        flush()
                    }
                }
            } catch (error: SocketException) {
                if (server.isClosed) return
                // Browser engines may cancel speculative or superseded document requests.
            }
        }
    }

    override fun close() {
        server.close()
        thread.join(2_000L)
    }
}
