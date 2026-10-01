package dev.sk2andy.materialbrowser.browser.gecko

import java.net.URI
import java.util.Base64

internal data class GeckoHttpsOnlyErrorPageStrings(
    val language: String,
    val title: String,
    val protection: String,
    val explanation: String,
    val risk: String,
    val back: String,
    val retry: String,
    val options: String,
    val exception: String,
    val openHttp: String,
    val lifetime: String,
    val unavailable: String,
)

/** Local Gecko error document; only Gecko's HTTPS-only error may expose its session exception. */
internal object GeckoHttpsOnlyErrorPage {
    const val BACK_ACTION_MESSAGE = "candy-https-warning:back"
    const val RETRY_ACTION_MESSAGE = "candy-https-warning:retry"

    fun uri(
        url: String,
        strings: GeckoHttpsOnlyErrorPageStrings,
    ): String = "data:text/html;charset=utf-8;base64," + Base64.getEncoder().encodeToString(
        html(url, strings).toByteArray(Charsets.UTF_8),
    )

    fun html(
        url: String,
        strings: GeckoHttpsOnlyErrorPageStrings,
    ): String {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return """
            <!doctype html>
            <html lang="${escape(strings.language)}" dir="auto">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <meta name="color-scheme" content="light dark">
              <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
              <title>${escape(strings.title)}</title>
              <style>
                :root { color-scheme:light dark; --bg:#fff8fb; --fg:#2d1722; --muted:#6e5c66;
                  --surface:#f6edf2; --primary:#8f275c; --on-primary:#fff; --purple:#7457d7;
                  --purple-soft:#f0e8ff; --pink-soft:#ffe2ec; --outline:#d7c4ce;
                  --amber:#765224; --amber-bg:#fbefd9; }
                @media(prefers-color-scheme:dark) { :root { --bg:#171217; --fg:#f5e8ee;
                  --muted:#c8b6c0; --surface:#292128; --primary:#ffadd0; --on-primary:#501532;
                  --purple:#ceb9ff; --purple-soft:#312445; --pink-soft:#482739;
                  --outline:#5b4753; --amber:#f0cf9d; --amber-bg:#332b21; } }
                * { box-sizing:border-box; }
                body { margin:0; background:var(--bg); color:var(--fg);
                  font:14px/1.55 Roboto,system-ui,sans-serif; }
                header { display:flex; justify-content:flex-end; align-items:center; gap:6px;
                  padding:calc(24px + env(safe-area-inset-top,0px)) 24px 0;
                  max-width:440px; margin:auto; color:var(--muted); font-size:12px; }
                svg { width:18px; height:18px; fill:none; stroke:currentColor;
                  stroke-width:1.7; stroke-linecap:round; stroke-linejoin:round; flex-shrink:0; }
                header svg { width:15px; height:15px; }
                main { max-width:390px; margin:auto; padding:38px 24px 96px; text-align:center; }
                .symbol { position:relative; width:86px; height:86px; margin:0 auto 28px;
                  display:grid; place-items:center; border-radius:30px; transform:rotate(-8deg);
                  background:linear-gradient(145deg,var(--pink-soft),var(--purple-soft)); }
                .symbol > svg { width:36px; height:36px; color:var(--purple); transform:rotate(8deg); }
                .symbol .alert { position:absolute; right:-5px; bottom:-5px; width:30px;
                  height:30px; border-radius:12px; display:grid; place-items:center;
                  background:var(--amber-bg); color:var(--amber); border:3px solid var(--bg);
                  transform:rotate(8deg); }
                .alert svg { width:16px; height:16px; }
                h1 { margin:0; font-size:27px; line-height:1.17; font-weight:600;
                  letter-spacing:-.6px; text-wrap:balance; }
                .host { display:inline-flex; align-items:center; gap:7px; max-width:100%;
                  margin:18px 0 16px; background:var(--surface); border-radius:28px;
                  padding:7px 12px; font-size:13px; overflow-wrap:anywhere; }
                .host svg { width:14px; height:14px; color:var(--muted); }
                p { margin:0; }
                .explanation { color:var(--muted); text-wrap:pretty; }
                .risk { display:flex; align-items:flex-start; gap:11px; margin-top:20px;
                  padding:14px 15px; background:var(--amber-bg); color:var(--amber);
                  border-radius:17px; text-align:start; font-size:12px; line-height:1.5; }
                .risk svg { margin-top:1px; }
                .actions { display:grid; gap:10px; margin-top:24px; }
                button { font:500 14px/1.4 Roboto,system-ui,sans-serif; cursor:pointer;
                  min-height:48px; border-radius:28px; border:1px solid var(--outline);
                  padding:13px 16px; background:transparent; color:var(--primary);
                  display:flex; align-items:center; justify-content:center; gap:9px; width:100%; }
                button:focus-visible { outline:3px solid var(--purple);
                  outline-offset:3px; }
                button:disabled { opacity:.6; cursor:default; }
                .primary { background:var(--primary); color:var(--on-primary); border-color:transparent; }
                .more-options { margin-top:10px; }
                .options { border:0; min-height:44px; padding:13px 4px;
                  display:flex; justify-content:center; align-items:center; gap:6px;
                  font-size:12px; font-weight:400; color:var(--muted); }
                .options svg { width:14px; height:14px; }
                .options[aria-expanded="true"] svg { transform:rotate(180deg); }
                .exception { background:var(--surface); border-radius:17px; padding:15px;
                  text-align:start; color:var(--muted); font-size:12px; }
                .http { border:0; padding:10px 0; justify-content:space-between; text-align:start; }
                .lifetime { font-size:11px; }
                #unavailable { color:var(--amber); margin-top:12px; font-size:12px; }
                @media(min-width:700px) { main { padding-top:64px; } }
                @media(max-width:350px) { h1 { font-size:25px; } main { padding-inline:21px; } }
              </style>
            </head>
            <body>
              <header><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 3 4 6v6c0 4 8 9 8 9s8-5 8-9V6z"/><path d="m8 12 3 3 5-6"/></svg>${escape(strings.protection)}</header>
              <main>
                <div class="symbol" aria-hidden="true">
                  <svg aria-hidden="true" viewBox="0 0 24 24"><rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V6a4 4 0 0 1 7-2.6M12 14v3"/></svg>
                  <span class="alert"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="m12 3 10 18H2zM12 9v5M12 17h.01"/></svg></span>
                </div>
                <h1>${escape(strings.title)}</h1>
                <div class="host"><svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><ellipse cx="12" cy="12" rx="4" ry="9"/><path d="M3 12h18"/></svg><bdi>${escape(host)}</bdi></div>
                <p class="explanation">${escape(strings.explanation)}</p>
                <div class="risk"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12"/><circle cx="12" cy="12" r="3"/></svg><p>${escape(strings.risk)}</p></div>
                <div class="actions">
                  <button class="primary" id="back" type="button"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M19 12H5m6-6-6 6 6 6"/></svg>${escape(strings.back)}</button>
                  <button id="retry" type="button"><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M20 7v5h-5M20 12a8 8 0 1 0-2 6M20 7l-3-3"/></svg>${escape(strings.retry)}</button>
                </div>
                <section class="more-options">
                  <button class="options" id="options" type="button" aria-expanded="false" aria-controls="http-options">${escape(strings.options)}<svg aria-hidden="true" viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg></button>
                  <div class="exception" id="http-options" hidden><p>${escape(strings.exception)}</p>
                    <button class="http" id="open-http" type="button">${escape(strings.openHttp)}<svg aria-hidden="true" viewBox="0 0 24 24"><path d="M7 17 17 7M7 7h10v10"/></svg></button>
                    <p class="lifetime">${escape(strings.lifetime)}</p>
                  </div>
                </section>
                <p id="unavailable" role="alert" hidden>${escape(strings.unavailable)}</p>
              </main>
              <script>
                document.getElementById('options').addEventListener('click', function() {
                  var expanded = this.getAttribute('aria-expanded') !== 'true';
                  this.setAttribute('aria-expanded', String(expanded));
                  document.getElementById('http-options').hidden = !expanded;
                });
                document.getElementById('back').addEventListener('click', function() {
                  this.disabled = true;
                  alert('$BACK_ACTION_MESSAGE');
                });
                document.getElementById('retry').addEventListener('click', function() {
                  this.disabled = true;
                  alert('$RETRY_ACTION_MESSAGE');
                });
                document.getElementById('open-http').addEventListener('click', function() {
                  if (typeof document.reloadWithHttpsOnlyException !== 'function') {
                    document.getElementById('unavailable').hidden = false;
                    return;
                  }
                  try {
                    document.reloadWithHttpsOnlyException();
                    this.disabled = true;
                  } catch (error) {
                    document.getElementById('unavailable').hidden = false;
                  }
                });
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
