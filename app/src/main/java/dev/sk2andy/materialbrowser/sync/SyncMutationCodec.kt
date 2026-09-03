package dev.sk2andy.materialbrowser.sync

import org.json.JSONObject

object SyncMutationCodec {
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
                val url = requireNotNull(
                    dev.sk2andy.materialbrowser.browser.integration.BrowserUriPolicy
                        .normalizeHttpUrl(value.url),
                )
                require(url.length <= SyncTabRules.MAX_URL_LENGTH)
                "\"type\":\"navigate\",\"candyId\":${quote(value.candyId)}," +
                    "\"title\":${quote(value.title)},\"url\":${quote(url)}}"
            }
            is SyncPendingMutation.Close -> {
                require(value.candyId.matches(CANDY_IDENTIFIER))
                "\"type\":\"close\",\"candyId\":${quote(value.candyId)}}"
            }
            is SyncPendingMutation.Reorder -> {
                require(value.orderedCandyIds.size <= SyncTabRules.MAX_TABS)
                require(value.orderedCandyIds.toSet().size == value.orderedCandyIds.size)
                require(value.orderedCandyIds.all { it.matches(CANDY_IDENTIFIER) })
                "\"type\":\"reorder\",\"orderedCandyIds\":" +
                    value.orderedCandyIds.joinToString(prefix = "[", postfix = "]") { quote(it) } + "}"
            }
            is SyncPendingMutation.SetPinned -> {
                require(value.candyId.matches(CANDY_IDENTIFIER))
                "\"type\":\"set-pinned\",\"candyId\":${quote(value.candyId)}," +
                    "\"pinned\":${value.pinned}}"
            }
        }
    }

    fun decode(raw: String): SyncPendingMutation {
        require(raw.toByteArray(Charsets.UTF_8).size <= MAX_MUTATION_BYTES)
        val value = parseStrictJsonObject(raw)
        val mutationId = value.identifier("mutationId")
        val target = value.identifier("targetDeviceId")
        require(value.strictInt("schemaVersion") == 2)
        val mutation = when (value.strictString("type")) {
            "open" -> {
                value.requireExactKeys("schemaVersion", "mutationId", "targetDeviceId", "type", "tab")
                SyncPendingMutation.Open(mutationId, target, decodeTab(value.getJSONObject("tab")))
            }
            "navigate" -> {
                value.requireExactKeys(
                    "schemaVersion",
                    "mutationId",
                    "targetDeviceId",
                    "type",
                    "candyId",
                    "title",
                    "url",
                )
                SyncPendingMutation.Navigate(
                    mutationId = mutationId,
                    targetDeviceId = target,
                    candyId = value.candyIdentifier("candyId"),
                    title = value.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH),
                    url = value.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH),
                )
            }
            "close" -> {
                value.requireExactKeys(
                    "schemaVersion",
                    "mutationId",
                    "targetDeviceId",
                    "type",
                    "candyId",
                )
                SyncPendingMutation.Close(mutationId, target, value.candyIdentifier("candyId"))
            }
            "reorder" -> {
                value.requireExactKeys(
                    "schemaVersion",
                    "mutationId",
                    "targetDeviceId",
                    "type",
                    "orderedCandyIds",
                )
                val ids = value.getJSONArray("orderedCandyIds")
                require(ids.length() <= SyncTabRules.MAX_TABS)
                val ordered = buildList {
                    repeat(ids.length()) { add(ids.get(it) as? String ?: error("Invalid tab identifier")) }
                }
                require(ordered.toSet().size == ordered.size && ordered.all { it.matches(CANDY_IDENTIFIER) })
                SyncPendingMutation.Reorder(mutationId, target, ordered)
            }
            "set-pinned" -> {
                value.requireExactKeys(
                    "schemaVersion",
                    "mutationId",
                    "targetDeviceId",
                    "type",
                    "candyId",
                    "pinned",
                )
                SyncPendingMutation.SetPinned(
                    mutationId,
                    target,
                    value.candyIdentifier("candyId"),
                    value.strictBoolean("pinned"),
                )
            }
            else -> throw IllegalArgumentException("Unknown tab mutation")
        }
        require(encode(mutation) == raw)
        return mutation
    }

    private fun encodeTab(tab: SyncTab): String = "{" +
        "\"candyId\":${quote(tab.candyId)}," +
        "\"windowId\":${tab.windowId}," +
        "\"index\":${tab.index}," +
        "\"groupId\":${tab.groupId ?: "null"}," +
        "\"active\":${tab.active}," +
        "\"pinned\":${tab.pinned}," +
        "\"title\":${quote(tab.title)}," +
        "\"url\":${quote(tab.url)}}"

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        var index = 0
        while (index < value.length) {
            val character = value[index]
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> appendUnicodeEscape(character)
                in '\uD800'..'\uDBFF' -> {
                    val low = value.getOrNull(index + 1)
                    if (low != null && low in '\uDC00'..'\uDFFF') {
                        append(character)
                        append(low)
                        index++
                    } else {
                        appendUnicodeEscape(character)
                    }
                }
                in '\uDC00'..'\uDFFF' -> appendUnicodeEscape(character)
                else -> append(character)
            }
            index++
        }
        append('"')
    }

    private fun StringBuilder.appendUnicodeEscape(character: Char) {
        append("\\u")
        append(character.code.toString(16).padStart(4, '0'))
    }

    private fun decodeTab(value: JSONObject): SyncTab {
        value.requireExactKeys(
            "candyId",
            "windowId",
            "index",
            "groupId",
            "active",
            "pinned",
            "title",
            "url",
        )
        val tab = SyncTab(
            candyId = value.candyIdentifier("candyId"),
            windowId = value.nonNegativeInt("windowId"),
            index = value.nonNegativeInt("index"),
            groupId = if (value.isNull("groupId")) null else value.nonNegativeInt("groupId"),
            active = value.strictBoolean("active"),
            pinned = value.strictBoolean("pinned"),
            title = value.strictString("title", 0, SyncTabRules.MAX_TITLE_LENGTH),
            url = value.strictString("url", 1, SyncTabRules.MAX_URL_LENGTH),
        )
        return requireNotNull(SyncTabRules.outboundTab(tab, isPrivate = false))
    }

    private fun JSONObject.requireExactKeys(vararg expected: String) {
        require(keys().asSequence().toSet() == expected.toSet())
    }

    private fun JSONObject.identifier(name: String): String = strictString(name, 1, 128).also {
        require(it.matches(IDENTIFIER))
    }

    private fun JSONObject.candyIdentifier(name: String): String = strictString(name, 1, 128).also {
        require(it.matches(CANDY_IDENTIFIER))
    }

    private fun JSONObject.strictString(name: String, minimum: Int = 1, maximum: Int = 128): String =
        (get(name) as? String ?: throw IllegalArgumentException("Invalid $name")).also {
            require(it.length in minimum..maximum)
        }

    private fun JSONObject.strictInt(name: String): Int = get(name) as? Int
        ?: throw IllegalArgumentException("Invalid $name")

    private fun JSONObject.nonNegativeInt(name: String): Int = strictInt(name).also { require(it >= 0) }

    private fun JSONObject.strictBoolean(name: String): Boolean = get(name) as? Boolean
        ?: throw IllegalArgumentException("Invalid $name")

    private val IDENTIFIER = Regex("[A-Za-z0-9_-]+")
    private val CANDY_IDENTIFIER = Regex("[A-Za-z0-9._:-]+")
    private const val MAX_MUTATION_BYTES = 196_608
}
