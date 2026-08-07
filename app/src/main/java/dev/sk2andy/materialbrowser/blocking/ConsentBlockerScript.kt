package dev.sk2andy.materialbrowser.blocking

import java.util.Base64

internal object ConsentBlockerScript {
    // Cookie banners are hidden only through CSS injected at onPageCommitVisible. The native caller
    // checks site settings before evaluating this single prebuilt script, matching Arc Search's
    // non-blocking path and avoiding observers, retries, or synthetic consent clicks.
    private const val STYLE_ID = "material-browser-easylist-cookie-css"

    val removalScript = "document.getElementById('$STYLE_ID')?.remove();"

    fun create(
        cssBytes: ByteArray,
        siteRules: Collection<CandyRule> = emptyList(),
    ): String {
        val encodedCss = Base64.getEncoder().encodeToString(cssBytes)
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
        return """
            (() => {
              if (window.top !== window) return;
              const pageHost = location.hostname.toLowerCase().replace(/\.${'$'}/, '');
              const styleId = '$STYLE_ID';
              if (document.getElementById(styleId)) return;
              const target = document.head || document.documentElement;
              if (!target) return;

              const decodeBase64Utf8 = encoded => {
                const binary = atob(encoded);
                const bytes = Uint8Array.from(binary, character => character.charCodeAt(0));
                return new TextDecoder('utf-8').decode(bytes);
              };
              const siteRules = $encodedSiteRules;
              const siteCss = siteRules
                .filter(rule => pageHost === rule.host || pageHost.endsWith('.' + rule.host))
                .map(rule => decodeBase64Utf8(rule.selector) +
                  '{display:none!important;height:0!important;visibility:hidden!important}'
                )
                .join('\n');
              const style = document.createElement('style');
              style.id = styleId;
              style.textContent = decodeBase64Utf8('$encodedCss') +
                (siteCss ? '\n' + siteCss : '');
              target.appendChild(style);
            })();
        """.trimIndent()
    }

}
