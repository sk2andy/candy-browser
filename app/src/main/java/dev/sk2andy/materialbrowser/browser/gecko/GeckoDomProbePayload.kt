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
        if (value.has("readyState")) {
            result.put("readyState", enum(value, "readyState", setOf("loading", "interactive", "complete")))
        }
        if (value.has("activeElementTag")) {
            result.put("activeElementTag", enum(value, "activeElementTag", setOf("HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "IFRAME", "TEXTAREA", "SELECT", "NONE", "OTHER")))
        }
        if (value.has("topRightCandidateIndex")) {
            val index = (value.opt("topRightCandidateIndex") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && it >= 0.0 && it < candidates.length() && it % 1.0 == 0.0 }
            result.put("topRightCandidateIndex", index ?: JSONObject.NULL)
        }
        if (value.has("cssSafeArea")) {
            val configuration = value.optJSONObject("cssSafeArea") ?: return null
            val inset = (configuration.opt("insetPx") as? Number)?.toDouble()
                ?.takeIf { it.isFinite() && it in 0.0..10_000.0 }
            result.put("cssSafeArea", JSONObject().apply {
                for (key in listOf("available", "ready", "enabled")) put(key, configuration.opt(key) == true)
                put("insetPx", inset ?: JSONObject.NULL)
            })
        }
        if (value.has("flowStart")) {
            val flow = value.optJSONArray("flowStart") ?: return null
            if (flow.length() > 8) return null
            result.put("flowStart", JSONArray().apply {
                repeat(flow.length()) { index ->
                    val element = flow.optJSONObject(index) ?: return null
                    put(geometry(element))
                }
            })
        }
        value.optJSONObject("cssSafeAreaDiagnostics")?.let { diagnostics ->
            result.put("cssSafeAreaDiagnostics", JSONObject().apply {
                for (key in listOf("active", "initialized", "bodyPending", "interactionActive", "immediateMutationPending")) {
                    put(key, diagnostics.opt(key) == true)
                }
                for ((key, maximum) in listOf("ownedCount" to 1024, "unknownCount" to 64, "pendingJobCount" to 32, "dirtyRootCount" to 32, "positionedClassifyCount" to 65535)) {
                    val count = (diagnostics.opt(key) as? Number)?.toDouble()
                        ?.takeIf { it.isFinite() && it in 0.0..maximum.toDouble() && it % 1.0 == 0.0 }
                    put(key, count ?: JSONObject.NULL)
                }
                val timestamp = (diagnostics.opt("firstAtMillis") as? Number)?.toDouble()
                    ?.takeIf { it.isFinite() && it in 0.0..10_000_000.0 }
                put("firstAtMillis", timestamp ?: JSONObject.NULL)
                put("firstReadyState", enum(diagnostics, "firstReadyState", setOf("loading", "interactive", "complete", "other")))
                put("lastBodyDecision", enum(diagnostics, "lastBodyDecision", setOf("not-classified", "env-pending", "already-flow-protected", "unsupported-body", "applied", "author-padding", "ownership-limit")))
                put("lastPanelDecision", enum(diagnostics, "lastPanelDecision", setOf("not-classified", "env-pending", "panel-guard-rejected", "author-padding", "no-unsafe-control", "footprints-unproven", "budget-exhausted", "ownership-limit", "postcheck-rejected", "applied")))
                put("lastAbsoluteDecision", enum(diagnostics, "lastAbsoluteDecision", setOf("not-classified", "owner-guard-rejected", "apply-or-postcheck-rejected", "applied")))
                put("lastPositionedDecision", enum(diagnostics, "lastPositionedDecision", setOf("not-classified", "hidden", "body-path", "outer-guard-rejected", "declared-safe", "panel-path", "top-applied", "ownership-limit")))
                for (key in listOf("eligibleAttributeRecordCount", "eligibleAdditionRecordCount", "acceptedImmediateMutationCount", "queuedMutationRootCount", "queuedSemanticRootCount", "visitedSemanticCount", "discoveryRectCount", "ownerQueuedCount")) {
                    val count = (diagnostics.opt(key) as? Number)?.toDouble()
                        ?.takeIf { it.isFinite() && it in 0.0..65535.0 && it % 1.0 == 0.0 }
                    put(key, count ?: JSONObject.NULL)
                }
                for (key in listOf("cssSourceCount", "cssLateSourceCount", "cssRulesVisited", "cssRulesApplied", "cssSecurityErrors", "cssUnsupportedRules", "cssBudgetHits", "cssScrollCancellations")) {
                    val count = (diagnostics.opt(key) as? Number)?.toDouble()
                        ?.takeIf { it.isFinite() && it in 0.0..65535.0 && it % 1.0 == 0.0 }
                    put(key, count ?: JSONObject.NULL)
                }
                put("lastEligibleMutationKind", enum(diagnostics, "lastEligibleMutationKind", setOf("none", "attributes", "additions", "mixed")))
                put("lastQueuedMutationRootKind", enum(diagnostics, "lastQueuedMutationRootKind", setOf("none", "body", "semantic-control", "other-element")))
                put("lastMutationFocusRelation", enum(diagnostics, "lastMutationFocusRelation", setOf("not-semantic", "unrelated", "related")))
                put("lastFootprintRejectionReason", enum(diagnostics, "lastFootprintRejectionReason", setOf("not-scanned", "complete", "opaque-panel", "time-budget", "node-budget", "style-budget", "opaque-descendant", "closed-shadow", "center-unproven", "footprint-budget")))
                for ((key, maximum) in listOf("lastFootprintNodeCount" to 513, "lastFootprintStyleCount" to 65, "lastFootprintControlCount" to 32)) {
                    val count = (diagnostics.opt(key) as? Number)?.toDouble()
                        ?.takeIf { it.isFinite() && it in 0.0..maximum.toDouble() && it % 1.0 == 0.0 }
                    put(key, count ?: JSONObject.NULL)
                }
            })
        }
        return result.toString().takeIf { it.length <= GeckoDomDiagnosticsRules.MAX_PAYLOAD_CHARS }
    }

    private fun geometry(value: JSONObject): JSONObject = numbers(
        value, listOf("x", "y", "width", "height", "top", "paddingTop", "marginTop", "maxBlockSize"),
    ).apply {
        put("tag", enum(value, "tag", setOf("HTML", "BODY", "HEADER", "NAV", "MAIN", "DIV", "BUTTON", "A", "FORM", "INPUT", "SPAN", "IFRAME", "TEXTAREA", "SELECT", "OTHER")))
        put("position", enum(value, "position", setOf("static", "relative", "absolute", "fixed", "sticky", "other")))
        put("transitionPropertyKind", enum(value, "transitionPropertyKind", setOf("none", "paint-only", "geometry-or-unknown")))
        put("pointerEvents", enum(value, "pointerEvents", setOf("auto", "none", "other")))
        for (key in listOf("hasTransform", "hasMovingEffects", "hasAnimationEffects", "hasTransitionEffects", "hasIndividualTransformEffects", "hasOffsetPathEffects", "hasZoomEffects", "hasContainingBlockEffects", "inert", "displayed", "visible")) {
            put(key, value.opt(key) == true)
        }
        for (key in listOf("overflowX", "overflowY")) {
            put(key, enum(value, key, setOf("visible", "hidden", "clip", "scroll", "auto", "overlay", "other")))
        }
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
