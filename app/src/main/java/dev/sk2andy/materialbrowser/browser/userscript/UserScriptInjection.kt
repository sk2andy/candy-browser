package dev.sk2andy.materialbrowser.browser.userscript

import java.security.MessageDigest

internal data class UserScriptInjectionSources(
    val guardSource: String,
    val userSource: String,
)

internal object UserScriptInjection {
    fun sources(script: UserScript): UserScriptInjectionSources? {
        if (!script.enabled || !UserScriptRules.isCanonical(script)) return null
        return buildSources(script)
    }

    internal fun estimatedInjectedBytes(script: UserScript): Long {
        if (!script.enabled || !UserScriptRules.isCanonical(script)) return 0L
        val sources = buildSources(script)
        return sources.guardSource.toByteArray(Charsets.UTF_8).size.toLong() +
            sources.userSource.toByteArray(Charsets.UTF_8).size.toLong()
    }

    fun executionWorldName(scriptId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(scriptId.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "candy.topping.$digest"
    }

    private fun buildSources(script: UserScript): UserScriptInjectionSources {
        val matchPatterns = UserScriptRules.matchJavascriptRegexes(script)
        val includePatterns = UserScriptRules.includeJavascriptRegexes(script)
        val excludePatterns = UserScriptRules.excludeJavascriptRegexes(script)
        val matchArray = matchPatterns.joinToString(prefix = "[", postfix = "]", transform = ::jsString)
        val includeArray = includePatterns.joinToString(prefix = "[", postfix = "]", transform = ::jsString)
        val excludeArray = excludePatterns.joinToString(prefix = "[", postfix = "]", transform = ::jsString)
        val marker = jsString("__candy_userscript_allowed:${script.id}")
        val guardSource = """
            (() => {
                "use strict";
                const __candyUrl = String(window.location.href);
                const __candyMatchUrl = __candyUrl.split("#", 1)[0];
                const __candyTestMatch = (__candyPattern) => new RegExp(__candyPattern).test(__candyMatchUrl);
                const __candyTestFullUrl = (__candyPattern) => new RegExp(__candyPattern).test(__candyUrl);
                const __candyAllowed =
                    window.top === window.self &&
                    (window.location.protocol === "http:" || window.location.protocol === "https:") &&
                    ($matchArray.some(__candyTestMatch) || $includeArray.some(__candyTestFullUrl)) &&
                    !$excludeArray.some(__candyTestFullUrl);
                Object.defineProperty(window, $marker, {
                    value: __candyAllowed,
                    writable: false,
                    configurable: false,
                    enumerable: false,
                });
            })();
        """.trimIndent()
        val userSource = buildString(script.source.length + 96) {
            append("if (this[")
                .append(marker)
                .append("] !== true) throw 0;\n")
                .append(script.source)
        }
        return UserScriptInjectionSources(guardSource = guardSource, userSource = userSource)
    }

    private fun jsString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> if (char.code < 0x20) {
                    append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}
