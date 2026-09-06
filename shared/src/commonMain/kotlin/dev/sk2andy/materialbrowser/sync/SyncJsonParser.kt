package dev.sk2andy.materialbrowser.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

internal val syncJson = Json {
    isLenient = false
    allowSpecialFloatingPointValues = false
}

fun parseStrictJsonObject(raw: String): JsonObject =
    syncJson.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalArgumentException("Expected a JSON object")

fun JsonObject.requireExactKeys(vararg expected: String) {
    require(keys == expected.toSet()) { "Unexpected JSON fields" }
}

fun JsonObject.requireObject(name: String): JsonObject =
    get(name) as? JsonObject ?: throw IllegalArgumentException("Invalid $name")

fun JsonObject.requireArray(name: String): JsonArray =
    get(name) as? JsonArray ?: throw IllegalArgumentException("Invalid $name")

fun JsonObject.isJsonNull(name: String): Boolean = get(name) == JsonNull

fun JsonObject.strictString(name: String, minimum: Int = 1, maximum: Int = 4_096): String {
    val value = (get(name) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        ?: throw IllegalArgumentException("Invalid $name")
    require(value.length in minimum..maximum)
    return value
}

fun JsonObject.strictInt(name: String): Int {
    val primitive = get(name) as? JsonPrimitive ?: throw IllegalArgumentException("Invalid $name")
    require(!primitive.isString)
    return primitive.intOrNull ?: throw IllegalArgumentException("Invalid $name")
}

fun JsonObject.strictBoolean(name: String): Boolean {
    val primitive = get(name) as? JsonPrimitive ?: throw IllegalArgumentException("Invalid $name")
    require(!primitive.isString)
    return primitive.booleanOrNull ?: throw IllegalArgumentException("Invalid $name")
}

internal fun JsonObject.jsonString(name: String): String = strictString(name)

internal fun JsonElement.requireString(): String = (this as? JsonPrimitive)
    ?.takeIf(JsonPrimitive::isString)
    ?.content
    ?: throw IllegalArgumentException("Invalid JSON string")
