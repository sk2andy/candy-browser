package dev.sk2andy.materialbrowser.browser.userscript

import org.json.JSONObject

internal sealed interface UserScriptBridgeRequest {
    data class SetValue(
        val requestId: Long,
        val key: String,
        val encodedValue: String,
    ) : UserScriptBridgeRequest

    data class DeleteValue(
        val requestId: Long,
        val key: String,
    ) : UserScriptBridgeRequest

    data class RegisterMenu(
        val commandId: String,
        val caption: String,
    ) : UserScriptBridgeRequest

    data class UnregisterMenu(
        val commandId: String,
    ) : UserScriptBridgeRequest

    data class OpenTab(
        val url: String,
        val active: Boolean,
    ) : UserScriptBridgeRequest
}

internal object UserScriptBridgeContract {
    const val BRIDGE_NAME = "__candyUserscriptBridge"
    const val MAX_KEY_CHARS = 256
    const val MAX_ENCODED_VALUE_BYTES = 16 * 1_024
    const val MAX_VALUES_PER_SCRIPT = 128
    const val MAX_SCRIPT_VALUE_BYTES = 32 * 1_024
    const val MAX_SCRIPT_VALUE_PAYLOAD_BYTES = MAX_SCRIPT_VALUE_BYTES - 1_024
    const val MAX_API_MUTATIONS_PER_WINDOW = 96
    const val MAX_MESSAGE_BYTES = MAX_ENCODED_VALUE_BYTES * 2 + 2_048
    const val MAX_COMMAND_ID_CHARS = 128
    const val MAX_COMMAND_CAPTION_CHARS = 120
    const val MAX_OPEN_TAB_URL_CHARS = 8_192

    fun parse(raw: String): UserScriptBridgeRequest? {
        if (raw.toByteArray(Charsets.UTF_8).size !in 1..MAX_MESSAGE_BYTES) return null
        val value = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val type = value.opt("type") as? String ?: return null
        if (type == "register-menu") {
            if (value.length() != 3) return null
            val commandId = (value.opt("commandId") as? String)
                ?.takeIf(::isValidCommandId) ?: return null
            val caption = (value.opt("caption") as? String)
                ?.takeIf(::isValidCaption) ?: return null
            return UserScriptBridgeRequest.RegisterMenu(commandId, caption)
        }
        if (type == "unregister-menu") {
            if (value.length() != 2) return null
            val commandId = (value.opt("commandId") as? String)
                ?.takeIf(::isValidCommandId) ?: return null
            return UserScriptBridgeRequest.UnregisterMenu(commandId)
        }
        if (type == "open-tab") {
            if (value.length() != 3) return null
            val url = (value.opt("url") as? String)?.takeIf { candidate ->
                candidate.length in 1..MAX_OPEN_TAB_URL_CHARS &&
                    candidate.none(Char::isISOControl)
            } ?: return null
            val active = value.opt("active") as? Boolean ?: return null
            return UserScriptBridgeRequest.OpenTab(url, active)
        }
        val requestIdValue = value.opt("id") as? Number ?: return null
        val requestId = requestIdValue.toLong().takeIf { id ->
            id in 1..Int.MAX_VALUE && requestIdValue.toDouble() == id.toDouble()
        } ?: return null
        val key = (value.opt("key") as? String)?.takeIf(::isValidKey) ?: return null
        return when (type) {
            "set-value" -> {
                if (value.length() != 4) return null
                val encodedValue = value.opt("value") as? String ?: return null
                if (encodedValue.toByteArray(Charsets.UTF_8).size > MAX_ENCODED_VALUE_BYTES) {
                    return null
                }
                UserScriptBridgeRequest.SetValue(requestId, key, encodedValue)
            }
            "delete-value" -> {
                if (value.length() != 3) return null
                UserScriptBridgeRequest.DeleteValue(requestId, key)
            }
            else -> null
        }
    }

    fun isValidKey(value: String): Boolean =
        value.length in 1..MAX_KEY_CHARS && value.none { char -> char.isISOControl() }

    fun isValidCommandId(value: String): Boolean =
        value.length in 1..MAX_COMMAND_ID_CHARS && value.none { char -> char.isISOControl() }

    fun isValidCaption(value: String): Boolean =
        value.isNotBlank() &&
            value.length <= MAX_COMMAND_CAPTION_CHARS &&
            value.none { char -> char.isISOControl() }
}
