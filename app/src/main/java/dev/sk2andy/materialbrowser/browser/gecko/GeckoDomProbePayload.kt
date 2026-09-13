package dev.sk2andy.materialbrowser.browser.gecko

import org.json.JSONArray
import org.json.JSONObject

/** Allowlist at the native boundary; author-controlled DOM metadata never enters the export. */
internal object GeckoDomProbePayload {
    fun sanitize(value: JSONObject): String? {
        if (value.optInt("version", -1) != 1) return null
        val env = value.optJSONObject("env") ?: return null
        val viewport = value.optJSONObject("viewport") ?: return null
        val html = value.optJSONObject("html") ?: return null
        val candidates = value.optJSONArray("candidates") ?: return null
        if (candidates.length() > 16) return null
        val result = JSONObject()
            .put("version", 1)
            .put("env", numbers(env, listOf("top", "right", "bottom", "left")))
            .put("viewport", numbers(viewport, listOf("width", "height", "density", "scrollY", "scale", "offsetTop")))
            .put("viewportFit", enum(value, "viewportFit", setOf("cover", "contain", "auto", "unset")))
            .put("darkPreferred", value.opt("darkPreferred") == true)
            .put("html", geometry(html))
            .put("body", value.optJSONObject("body")?.let(::geometry) ?: JSONObject.NULL)
            .put("googleDialog", value.optJSONObject("googleDialog")?.let(::geometry) ?: JSONObject.NULL)
            .put("candidates", JSONArray().apply {
                repeat(candidates.length()) { index ->
                    val candidate = candidates.optJSONObject(index) ?: return null
                    put(geometry(candidate))
                }
            })
        return result.toString().takeIf { it.length <= GeckoDomDiagnosticsRules.MAX_PAYLOAD_CHARS }
    }

    private fun geometry(value: JSONObject): JSONObject = numbers(
        value, listOf("x", "y", "width", "height", "top", "paddingTop", "marginTop", "maxBlockSize"),
    ).apply {
        put("tag", enum(value, "tag", setOf("HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "OTHER")))
        put("position", enum(value, "position", setOf("static", "relative", "absolute", "fixed", "sticky", "other")))
        for (key in listOf("hasTransform", "displayed", "visible")) put(key, value.opt(key) == true)
    }

    private fun numbers(value: JSONObject, keys: List<String>): JSONObject = JSONObject().apply {
        for (key in keys) {
            val number = (value.opt(key) as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && kotlin.math.abs(it) <= 10_000_000 }
            put(key, number ?: JSONObject.NULL)
        }
    }

    private fun enum(value: JSONObject, key: String, allowed: Set<String>): String =
        value.optString(key).takeIf { it in allowed } ?: "other"
}
