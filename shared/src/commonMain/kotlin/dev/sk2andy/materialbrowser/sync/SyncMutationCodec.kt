package dev.sk2andy.materialbrowser.sync

import dev.sk2andy.materialbrowser.shared.browser.BrowserUrlRules
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object SyncMutationCodec {
    @Throws(Exception::class)
    fun encode(value: SyncPendingMutation): String {
        require(value.mutationId.matches(IDENTIFIER))
        require(value.targetDeviceId.matches(IDENTIFIER))
        val prefix = "{\"schemaVersion\":2,\"mutationId\":${quote(value.mutationId)}," +
            "\"targetDeviceId\":${quote(value.targetDeviceId)},"
        return prefix + when (value) {
            is SyncPendingMutation.Open -> {
                require(!value.isPrivate)
                val tab = requireNotNull(SyncTabRules.outboundTab(value.tab, isPrivate = false))
                "\"type\":\"open\",\"tab\":${encodeTab(tab)}}"
            }
            is SyncPendingMutation.Navigate -> {
                require(value.candyId.matches(CANDY_IDENTIFIER))
                require(value.title.length <= SyncTabRules.MAX_TITLE_LENGTH)
                val url = requireNotNull(BrowserUrlRules.normalizeHttpUrl(value.url)?.value)
                require(url.length <= SyncTabRules.MAX_URL_LENGTH)
                "\"type\":\"navigate\",\"candyId\":${quote(value.candyId)}," +
                    "\"title\":${quote(value.title)},\"url\":${quote(url)}}"
            }
            is SyncPendingMutation.Close -> "\"type\":\"close\",\"candyId\":${quote(value.candyId.also { require(it.matches(CANDY_IDENTIFIER)) })}}"
            is SyncPendingMutation.Reorder -> {
                require(value.orderedCandyIds.size <= SyncTabRules.MAX_TABS)
                require(value.orderedCandyIds.toSet().size == value.orderedCandyIds.size)
                require(value.orderedCandyIds.all { it.matches(CANDY_IDENTIFIER) })
                "\"type\":\"reorder\",\"orderedCandyIds\":" +
                    value.orderedCandyIds.joinToString(prefix = "[", postfix = "]") { quote(it) } + "}"
            }
            is SyncPendingMutation.SetPinned -> "\"type\":\"set-pinned\",\"candyId\":${quote(value.candyId.also { require(it.matches(CANDY_IDENTIFIER)) })},\"pinned\":${value.pinned}}"
        }
    }

    @Throws(Exception::class)
    fun decode(raw: String): SyncPendingMutation {
        require(raw.encodeToByteArray().size <= MAX_MUTATION_BYTES)
        val value = parseStrictJsonObject(raw)
        val mutationId = value.identifier("mutationId")
        val target = value.identifier("targetDeviceId")
        require(value.strictInt("schemaVersion") == 2)
        val mutation = when (value.strictString("type")) {
            "open" -> SyncPendingMutation.Open(mutationId, target, decodeTab(value.requireObject("tab")).also {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "tab")
            })
            "navigate" -> {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "candyId", "title", "url")
                SyncPendingMutation.Navigate(mutationId, target, value.candyIdentifier("candyId"), value.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH), value.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH))
            }
            "close" -> {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "candyId")
                SyncPendingMutation.Close(mutationId, target, value.candyIdentifier("candyId"))
            }
            "reorder" -> {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "orderedCandyIds")
                val ordered = value.requireArray("orderedCandyIds").map(JsonElement::requireString)
                require(ordered.size <= SyncTabRules.MAX_TABS && ordered.toSet().size == ordered.size && ordered.all { it.matches(CANDY_IDENTIFIER) })
                SyncPendingMutation.Reorder(mutationId, target, ordered)
            }
            "set-pinned" -> {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "candyId", "pinned")
                SyncPendingMutation.SetPinned(mutationId, target, value.candyIdentifier("candyId"), value.strictBoolean("pinned"))
            }
            else -> throw IllegalArgumentException("Unknown tab mutation")
        }
        require(encode(mutation) == raw)
        return mutation
    }

    private fun encodeTab(tab: SyncTab): String = "{\"candyId\":${quote(tab.candyId)},\"windowId\":${tab.windowId},\"index\":${tab.index},\"groupId\":${tab.groupId ?: "null"},\"active\":${tab.active},\"pinned\":${tab.pinned},\"title\":${quote(tab.title)},\"url\":${quote(tab.url)}}"

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        var index = 0
        while (index < value.length) {
            when (val character = value[index]) {
                '"' -> append("\\\""); '\\' -> append("\\\\"); '\b' -> append("\\b"); '\u000C' -> append("\\f")
                '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> appendUnicodeEscape(character)
                in '\uD800'..'\uDBFF' -> if (value.getOrNull(index + 1) in '\uDC00'..'\uDFFF') { append(character); append(value[++index]) } else appendUnicodeEscape(character)
                in '\uDC00'..'\uDFFF' -> appendUnicodeEscape(character)
                else -> append(character)
            }
            index++
        }
        append('"')
    }

    private fun StringBuilder.appendUnicodeEscape(character: Char) = append("\\u").append(character.code.toString(16).padStart(4, '0'))

    private fun decodeTab(value: JsonObject): SyncTab {
        value.requireExactKeys("candyId", "windowId", "index", "groupId", "active", "pinned", "title", "url")
        return requireNotNull(SyncTabRules.outboundTab(SyncTab(value.candyIdentifier("candyId"), value.nonNegativeInt("windowId"), value.nonNegativeInt("index"), if (value.isJsonNull("groupId")) null else value.nonNegativeInt("groupId"), value.strictBoolean("active"), value.strictBoolean("pinned"), value.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH), value.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH)), isPrivate = false))
    }

    private fun JsonObject.identifier(name: String) = strictString(name, 1, 128).also { require(it.matches(IDENTIFIER)) }
    private fun JsonObject.candyIdentifier(name: String) = strictString(name, 1, 128).also { require(it.matches(CANDY_IDENTIFIER)) }
    private fun JsonObject.nonNegativeInt(name: String) = strictInt(name).also { require(it >= 0) }

    private val IDENTIFIER = Regex("[A-Za-z0-9_-]+")
    private val CANDY_IDENTIFIER = Regex("[A-Za-z0-9._:-]+")
    private const val MAX_MUTATION_BYTES = 196_608
}
