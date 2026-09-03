package dev.sk2andy.materialbrowser.sync

import org.json.JSONObject
import org.json.JSONTokener

internal fun parseStrictJsonObject(raw: String): JSONObject {
    val tokener = JSONTokener(raw)
    val value = tokener.nextValue() as? JSONObject
        ?: throw IllegalArgumentException("Expected a JSON object")
    require(tokener.nextClean() == '\u0000') { "Unexpected trailing JSON content" }
    return value
}
