package dev.sk2andy.materialbrowser.browser.userscript

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal object ToppingCatalogParser {
    const val SCHEMA_VERSION = 1
    const val MAX_MANIFEST_BYTES = 256 * 1_024
    const val MAX_TOPPINGS = UserScriptParser.MAX_SCRIPTS

    fun parse(json: String): ToppingCatalogParseResult {
        if (json.isEmpty()) return rejected(ToppingCatalogRejectionReason.Empty)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            return rejected(ToppingCatalogRejectionReason.TooLarge)
        }
        if (JsonDuplicateKeyDetector.hasDuplicateObjectKey(json)) {
            return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        }

        return runCatching { parseRoot(json) }
            .getOrElse { rejected(ToppingCatalogRejectionReason.MalformedJson) }
    }

    private fun parseRoot(json: String): ToppingCatalogParseResult {
        val tokener = JSONTokener(json)
        val root = tokener.nextValue() as? JSONObject
            ?: return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        if (tokener.nextClean() != '\u0000') {
            return rejected(ToppingCatalogRejectionReason.MalformedJson)
        }
        if (!root.hasExactly(ROOT_KEYS)) {
            return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        }
        val schemaVersion = root.strictInt("schemaVersion")
            ?: return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        if (schemaVersion != SCHEMA_VERSION) {
            return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        }
        val values = root.opt("toppings") as? JSONArray
            ?: return rejected(ToppingCatalogRejectionReason.InvalidSchema)
        if (values.length() > MAX_TOPPINGS) {
            return rejected(ToppingCatalogRejectionReason.TooManyToppings)
        }

        val toppings = ArrayList<ToppingCatalogEntry>(values.length())
        val ids = mutableSetOf<String>()
        val sources = mutableSetOf<String>()
        for (index in 0 until values.length()) {
            val value = values.opt(index) as? JSONObject
                ?: return rejected(ToppingCatalogRejectionReason.InvalidEntry)
            val entry = parseEntry(value)
                ?: return rejected(ToppingCatalogRejectionReason.InvalidEntry)
            if (!ids.add(entry.id) || !sources.add(entry.source)) {
                return rejected(ToppingCatalogRejectionReason.DuplicateEntry)
            }
            toppings += entry
        }
        return ToppingCatalogParseResult.Accepted(
            ToppingCatalog(
                schemaVersion = schemaVersion,
                toppings = toppings,
            ),
        )
    }

    private fun parseEntry(value: JSONObject): ToppingCatalogEntry? {
        if (!value.hasExactly(ENTRY_KEYS)) return null
        val id = value.strictString("id")?.takeIf(ToppingCatalogRules::isValidId) ?: return null
        val name = value.strictString("name")
            ?.takeIf { text -> isBoundedText(text, UserScriptParser.MAX_NAME_CHARS) }
            ?: return null
        val description = value.strictString("description")
            ?.takeIf { text -> isBoundedText(text, MAX_DESCRIPTION_CHARS) }
            ?: return null
        val author = value.strictString("author")
            ?.takeIf { text -> isBoundedText(text, MAX_AUTHOR_CHARS) }
            ?: return null
        val license = value.strictString("license")
            ?.takeIf(ToppingCatalogRules::isValidLicense)
            ?: return null
        val version = value.strictString("version")
            ?.takeIf(ToppingCatalogRules::isValidVersion)
            ?: return null
        val source = value.strictString("source")
            ?.takeIf { path -> path == ToppingCatalogRules.sourcePath(id) }
            ?: return null
        val sha256 = value.strictString("sha256")
            ?.takeIf(ToppingCatalogRules::isValidSha256)
            ?: return null
        val matches = parseMatches(value.opt("matches") as? JSONArray ?: return null) ?: return null

        return ToppingCatalogEntry(
            id = id,
            name = name,
            description = description,
            author = author,
            license = license,
            version = version,
            source = source,
            matches = matches,
            sha256 = sha256,
        )
    }

    private fun parseMatches(values: JSONArray): List<String>? {
        if (values.length() !in 1..MAX_MATCHES) return null
        val matches = buildList {
            for (index in 0 until values.length()) {
                val pattern = values.opt(index) as? String ?: return null
                if (!ToppingCatalogRules.isValidMatch(pattern)) return null
                add(pattern)
            }
        }
        return matches
    }

    private fun isBoundedText(value: String, maxChars: Int): Boolean =
        value.isNotBlank() &&
            value.length <= maxChars &&
            value.none(Char::isISOControl)

    private fun JSONObject.hasExactly(expected: Set<String>): Boolean =
        keys().asSequence().toSet() == expected

    private fun JSONObject.strictString(key: String): String? = opt(key) as? String

    private fun JSONObject.strictInt(key: String): Int? = opt(key) as? Int

    private fun rejected(reason: ToppingCatalogRejectionReason) =
        ToppingCatalogParseResult.Rejected(reason)

    private const val MAX_DESCRIPTION_CHARS = 240
    private const val MAX_AUTHOR_CHARS = 120
    private const val MAX_MATCHES = UserScriptParser.MAX_PATTERNS_PER_KIND * 2
    private val ROOT_KEYS = setOf("schemaVersion", "toppings")
    private val ENTRY_KEYS = setOf(
        "id",
        "name",
        "description",
        "author",
        "license",
        "version",
        "source",
        "matches",
        "sha256",
    )
}

internal object ToppingCatalogRules {
    fun isValidId(value: String): Boolean = value.length <= MAX_ID_CHARS && ID.matches(value)

    fun sourcePath(id: String): String = "toppings/$id.user.js"

    fun stableScriptId(id: String): String = "topping.$id"

    fun isValidVersion(value: String): Boolean =
        value.length <= MAX_VERSION_CHARS && SEMANTIC_VERSION.matches(value)

    fun isValidLicense(value: String): Boolean = LICENSE.matches(value)

    fun isValidSha256(value: String): Boolean = SHA256.matches(value)

    fun isValidMatch(value: String): Boolean {
        if (value == "<all_urls>") return false
        val match = UserScriptParser.parseMatchPattern(value)
        if (match != null) return '*' !in match.host
        val include = UserScriptParser.parseGlobPattern(value)
        return include != null && '*' !in include.authority
    }

    private const val MAX_ID_CHARS = 64
    private const val MAX_VERSION_CHARS = 64
    private val ID = Regex("""^[a-z0-9]+(?:-[a-z0-9]+)*$""")
    private val LICENSE = Regex("""^[A-Za-z0-9][A-Za-z0-9.+-]{0,63}$""")
    private val SHA256 = Regex("""^[0-9a-f]{64}$""")
    private val SEMANTIC_VERSION = Regex(
        """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
            """(?:-((?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*)""" +
            """(?:\.(?:0|[1-9]\d*|\d*[A-Za-z-][0-9A-Za-z-]*))*))?""" +
            """(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""",
    )
}

private object JsonDuplicateKeyDetector {
    fun hasDuplicateObjectKey(json: String): Boolean {
        val contexts = ArrayDeque<JsonContext>()
        var index = 0
        while (index < json.length) {
            when (json[index]) {
                '{' -> contexts.addLast(ObjectContext())
                '[' -> contexts.addLast(ArrayContext)
                '}', ']' -> if (contexts.isNotEmpty()) contexts.removeLast()
                ',' -> (contexts.lastOrNull() as? ObjectContext)?.expectingKey = true
                '"' -> {
                    val token = readString(json, index) ?: return false
                    val context = contexts.lastOrNull() as? ObjectContext
                    if (context?.expectingKey == true) {
                        if (!context.keys.add(token.value)) return true
                        context.expectingKey = false
                    }
                    index = token.endIndex
                }
                else -> Unit
            }
            index++
        }
        return false
    }

    private fun readString(json: String, startIndex: Int): StringToken? {
        val value = StringBuilder()
        var index = startIndex + 1
        while (index < json.length) {
            when (val char = json[index]) {
                '"' -> return StringToken(value.toString(), index)
                '\\' -> {
                    index++
                    if (index >= json.length) return null
                    when (val escaped = json[index]) {
                        '"', '\\', '/' -> value.append(escaped)
                        'b' -> value.append('\b')
                        'f' -> value.append('\u000C')
                        'n' -> value.append('\n')
                        'r' -> value.append('\r')
                        't' -> value.append('\t')
                        'u' -> {
                            if (index + 4 >= json.length) return null
                            val codePoint = json.substring(index + 1, index + 5).toIntOrNull(16)
                                ?: return null
                            value.append(codePoint.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                }
                else -> value.append(char)
            }
            index++
        }
        return null
    }

    private data class ObjectContext(
        val keys: MutableSet<String> = mutableSetOf(),
        var expectingKey: Boolean = true,
    ) : JsonContext

    private data object ArrayContext : JsonContext

    private sealed interface JsonContext

    private data class StringToken(
        val value: String,
        val endIndex: Int,
    )
}
