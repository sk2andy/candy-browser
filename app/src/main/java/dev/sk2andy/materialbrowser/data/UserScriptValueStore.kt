package dev.sk2andy.materialbrowser.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import dev.sk2andy.materialbrowser.browser.userscript.UserScriptBridgeContract
import java.io.StringReader
import org.json.JSONObject

internal class UserScriptValueStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val cache = mutableMapOf<String, Map<String, String>>()

    @Synchronized
    fun snapshot(scriptId: String): Map<String, String> {
        if (!isValidScriptId(scriptId)) return emptyMap()
        cache[scriptId]?.let { values -> return values }
        val encoded = preferences.getString(scriptId, null) ?: return emptyMap<String, String>().also {
            cache[scriptId] = it
        }
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_SCRIPT_BYTES) {
            preferences.edit().remove(scriptId).apply()
            return emptyMap<String, String>().also { cache[scriptId] = it }
        }
        return decode(encoded).getOrElse {
            preferences.edit().remove(scriptId).apply()
            emptyMap()
        }.also { values -> cache[scriptId] = values }
    }

    @Synchronized
    fun set(scriptId: String, key: String, encodedValue: String): Boolean {
        if (
            !isValidScriptId(scriptId) ||
            !UserScriptBridgeContract.isValidKey(key) ||
            !isCompleteJsonValue(encodedValue) ||
            encodedValue.toByteArray(Charsets.UTF_8).size >
            UserScriptBridgeContract.MAX_ENCODED_VALUE_BYTES
        ) return false
        val values = snapshot(scriptId).toMutableMap()
        if (
            !values.containsKey(key) &&
            values.size >= UserScriptBridgeContract.MAX_VALUES_PER_SCRIPT
        ) return false
        values[key] = encodedValue
        return persist(scriptId, values)
    }

    @Synchronized
    fun delete(scriptId: String, key: String): Boolean {
        if (!isValidScriptId(scriptId) || !UserScriptBridgeContract.isValidKey(key)) return false
        val values = snapshot(scriptId).toMutableMap()
        if (values.remove(key) == null) return true
        return persist(scriptId, values)
    }

    @Synchronized
    fun clear(scriptId: String) {
        if (isValidScriptId(scriptId)) {
            cache.remove(scriptId)
            preferences.edit().remove(scriptId).apply()
        }
    }

    @Synchronized
    fun encodedSnapshot(scriptId: String): String = JSONObject(snapshot(scriptId)).toString()

    private fun persist(scriptId: String, values: Map<String, String>): Boolean {
        if (values.isEmpty()) {
            cache[scriptId] = emptyMap()
            preferences.edit().remove(scriptId).apply()
            return true
        }
        val encoded = JSONObject(values).toString()
        if (encoded.toByteArray(Charsets.UTF_8).size > MAX_SCRIPT_BYTES) return false
        cache[scriptId] = values.toMap()
        preferences.edit().putString(scriptId, encoded).apply()
        return true
    }

    private fun isValidScriptId(value: String): Boolean =
        value.length in 1..MAX_SCRIPT_ID_CHARS && value.none { char ->
            char.isWhitespace() || char.isISOControl()
        }

    private fun decode(encoded: String): Result<Map<String, String>> = runCatching {
        val values = JSONObject(encoded)
        check(values.length() <= UserScriptBridgeContract.MAX_VALUES_PER_SCRIPT)
        buildMap {
            values.keys().forEach { key ->
                check(UserScriptBridgeContract.isValidKey(key))
                val value = values.opt(key) as? String
                    ?: error("Userscript values must use encoded JSON strings")
                check(isCompleteJsonValue(value))
                check(
                    value.toByteArray(Charsets.UTF_8).size <=
                        UserScriptBridgeContract.MAX_ENCODED_VALUE_BYTES,
                )
                put(key, value)
            }
        }
    }

    private fun isCompleteJsonValue(encoded: String): Boolean = runCatching {
        JsonReader(StringReader("[$encoded]")).use { input ->
            input.isLenient = false
            input.beginArray()
            check(input.hasNext())
            input.skipValue()
            check(!input.hasNext())
            input.endArray()
            input.peek() == JsonToken.END_DOCUMENT
        }
    }.getOrDefault(false)

    internal companion object {
        const val PREFERENCES_NAME = "userscript_values"
        const val MAX_SCRIPT_BYTES = UserScriptBridgeContract.MAX_SCRIPT_VALUE_BYTES
        private const val MAX_SCRIPT_ID_CHARS = 128
    }
}
