package dev.sk2andy.materialbrowser.browser.gecko.webpush

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

internal sealed interface FossWebPushServerMessage {
    data class Hello(val uaid: String, val useWebPush: Boolean) : FossWebPushServerMessage

    data class Register(
        val channelId: String,
        val status: Int,
        val endpoint: String?,
    ) : FossWebPushServerMessage

    data class Unregister(val channelId: String, val status: Int) : FossWebPushServerMessage

    data class Notification(
        val channelId: String,
        val version: JsonElement,
        val data: String?,
        val headers: Map<String, String>,
    ) : FossWebPushServerMessage

    data object Ignored : FossWebPushServerMessage
}

/** JSON framing for Mozilla Autopush's WebSocket protocol. */
internal object FossWebPushProtocol {
    fun hello(uaid: String?, channelIds: List<String>): String = buildJsonObject {
        put("messageType", JsonPrimitive("hello"))
        put("use_webpush", JsonPrimitive(true))
        if (!uaid.isNullOrBlank() && channelIds.isNotEmpty()) put("uaid", JsonPrimitive(uaid))
        put("channelIDs", JsonArray(channelIds.map(::JsonPrimitive)))
    }.toString()

    fun register(channelId: String, appServerKey: ByteArray?): String = buildJsonObject {
        put("messageType", JsonPrimitive("register"))
        put("channelID", JsonPrimitive(channelId))
        appServerKey?.let { key ->
            put("key", JsonPrimitive(Base64.getUrlEncoder().encodeToString(key)))
        }
    }.toString()

    fun unregister(channelId: String): String = buildJsonObject {
        put("messageType", JsonPrimitive("unregister"))
        put("channelID", JsonPrimitive(channelId))
        put("code", JsonPrimitive(200))
    }.toString()

    fun acknowledge(channelId: String, version: JsonElement): String = buildJsonObject {
        put("messageType", JsonPrimitive("ack"))
        put(
            "updates",
            JsonArray(
                listOf(
                    buildJsonObject {
                        put("channelID", JsonPrimitive(channelId))
                        put("version", version)
                        put("code", JsonPrimitive(100))
                    },
                ),
            ),
        )
    }.toString()

    fun decode(raw: String): FossWebPushServerMessage? {
        if (raw.length > MAX_MESSAGE_CHARS) return null
        val value = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return null
        val messageType = (value["messageType"] as? JsonPrimitive)?.content ?: return FossWebPushServerMessage.Ignored
        return when (messageType) {
            "hello" -> {
                val uaid = (value["uaid"] as? JsonPrimitive)?.content ?: return null
                if (uaid.isBlank() || uaid.length > 128 || !UAID_PATTERN.matches(uaid)) return null
                if ((value["status"] as? JsonPrimitive)?.intOrNull != 200) return null
                FossWebPushServerMessage.Hello(
                    uaid = uaid,
                    useWebPush = (value["use_webpush"] as? JsonPrimitive)?.content == "true",
                )
            }
            "register" -> FossWebPushServerMessage.Register(
                channelId = (value["channelID"] as? JsonPrimitive)?.content ?: return null,
                status = (value["status"] as? JsonPrimitive)?.intOrNull ?: return null,
                endpoint = (value["pushEndpoint"] as? JsonPrimitive)?.content,
            )
            "unregister" -> FossWebPushServerMessage.Unregister(
                channelId = (value["channelID"] as? JsonPrimitive)?.content ?: return null,
                status = (value["status"] as? JsonPrimitive)?.intOrNull ?: return null,
            )
            "notification" -> {
                val channelId = (value["channelID"] as? JsonPrimitive)?.content ?: return null
                val version = value["version"] as? JsonPrimitive ?: return null
                val data = (value["data"] as? JsonPrimitive)?.content
                if (data != null && data.length > MAX_DATA_CHARS) return null
                val headers = (value["headers"] as? JsonObject)?.entries?.associate { (key, item) ->
                    val header = item as? JsonPrimitive ?: return null
                    key.lowercase() to header.content
                } ?: emptyMap()
                FossWebPushServerMessage.Notification(channelId, version, data, headers)
            }
            else -> FossWebPushServerMessage.Ignored
        }
    }

    private const val MAX_MESSAGE_CHARS = 128 * 1_024
    private const val MAX_DATA_CHARS = 96 * 1_024
    private val UAID_PATTERN = Regex("[0-9a-fA-F-]{1,128}")
}
