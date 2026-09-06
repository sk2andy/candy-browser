package dev.sk2andy.materialbrowser.shared.browser

import dev.sk2andy.materialbrowser.browser.SearchSettings
import dev.sk2andy.materialbrowser.browser.BLANK_URL

data class BrowserUrl(
    val value: String,
)

enum class AddressResolutionKind {
    WebUrl,
    Search,
    Rejected,
}

data class AddressResolution(
    val kind: AddressResolutionKind,
    val url: BrowserUrl?,
)

object BrowserUrlRules {
    private const val MAX_INPUT_LENGTH = 16_384
    private const val MAX_HOST_LENGTH = 253

    fun resolve(
        input: String,
        searchSettings: SearchSettings = SearchSettings(),
    ): AddressResolution {
        val normalized = input.trim()
        if (normalized.isEmpty() || normalized.length > MAX_INPUT_LENGTH || normalized.hasControlCharacter()) {
            return AddressResolution(AddressResolutionKind.Rejected, null)
        }

        parseHttpUrl(normalized)?.let { url ->
            return AddressResolution(AddressResolutionKind.WebUrl, BrowserUrl(url))
        }

        if (normalized.looksLikeExplicitScheme()) {
            return AddressResolution(AddressResolutionKind.Rejected, null)
        }

        if (normalized.looksLikeHostInput()) {
            parseHttpUrl("https://$normalized")?.let { url ->
                return AddressResolution(AddressResolutionKind.WebUrl, BrowserUrl(url))
            }
        }

        val searchUrl = searchSettings.searchEngine.buildSearchUrl(
            query = normalized,
            searxngInstanceUrl = searchSettings.searxngInstanceUrl,
        )
        return if (searchUrl == BLANK_URL) {
            AddressResolution(AddressResolutionKind.Rejected, null)
        } else {
            AddressResolution(AddressResolutionKind.Search, BrowserUrl(searchUrl))
        }
    }

    fun normalizeHttpUrl(value: String): BrowserUrl? = parseHttpUrl(value)?.let(::BrowserUrl)

    fun encodeSearchQuery(value: String): String = value.percentEncoded()

    private fun parseHttpUrl(value: String): String? {
        if (value.length > MAX_INPUT_LENGTH || value.hasControlCharacter() || value.any(Char::isWhitespace)) {
            return null
        }
        val schemeSeparator = value.indexOf("://")
        if (schemeSeparator < 0) return null
        val scheme = value.substring(0, schemeSeparator).lowercase()
        if (scheme != "http" && scheme != "https") return null

        val authorityStart = schemeSeparator + 3
        val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .takeIf { it >= 0 }
            ?: value.length
        val authority = value.substring(authorityStart, authorityEnd)
        if (authority.isEmpty() || '@' in authority) return null

        val host = authority.hostWithoutPort() ?: return null
        if (!host.isValidWebHost()) return null
        if (!authority.hasValidPort()) return null

        return "$scheme://${authority.lowercase()}${value.substring(authorityEnd)}"
    }

    private fun String.hostWithoutPort(): String? = when {
        startsWith('[') -> substringBefore(']', missingDelimiterValue = "")
            .takeIf(String::isNotEmpty)
            ?.plus(']')
        count { it == ':' } > 1 -> null
        else -> substringBefore(':')
    }

    private fun String.hasValidPort(): Boolean {
        if (startsWith('[')) {
            val closingBracket = indexOf(']')
            if (closingBracket < 0) return false
            if (closingBracket == lastIndex) return true
            if (getOrNull(closingBracket + 1) != ':') return false
            return substring(closingBracket + 2).isValidPort()
        }
        val separator = lastIndexOf(':')
        return separator < 0 || substring(separator + 1).isValidPort()
    }

    private fun String.isValidPort(): Boolean =
        isNotEmpty() && all(Char::isDigit) && toIntOrNull() in 1..65_535

    private fun String.isValidWebHost(): Boolean {
        if (length > MAX_HOST_LENGTH) return false
        if (startsWith('[') && endsWith(']')) {
            val address = substring(1, lastIndex)
            return address.isNotEmpty() && address.all { char ->
                char.isDigit() || char.lowercaseChar() in 'a'..'f' || char == ':' || char == '.'
            }
        }
        if (startsWith('.') || endsWith('.') || ".." in this) return false
        return split('.').all { label ->
            label.isNotEmpty() &&
                label.length <= 63 &&
                label.first() != '-' &&
                label.last() != '-' &&
                label.all { char -> char.isLetterOrDigit() || char == '-' }
        }
    }

    private fun String.looksLikeExplicitScheme(): Boolean {
        val separator = indexOf(':')
        if (separator <= 0) return false
        return substring(0, separator).let { scheme ->
            scheme.first().isLetter() && scheme.drop(1).all { char ->
                char.isLetterOrDigit() || char == '+' || char == '-' || char == '.'
            }
        }
    }

    private fun String.looksLikeHostInput(): Boolean {
        val authority = substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringBefore(':').lowercase()
        return host == "localhost" ||
            host.startsWith('[') ||
            '.' in host ||
            host.split('.').let { parts ->
                parts.size == 4 && parts.all { part ->
                    part.toIntOrNull() in 0..255
                }
            }
    }

    private fun String.hasControlCharacter(): Boolean = any { char ->
        char.code in 0..31 || char.code == 127
    }

    private fun String.percentEncoded(): String = buildString {
        encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xff
            if (
                value in 'a'.code..'z'.code ||
                value in 'A'.code..'Z'.code ||
                value in '0'.code..'9'.code ||
                value == '-'.code ||
                value == '.'.code ||
                value == '_'.code ||
                value == '~'.code
            ) {
                append(value.toChar())
            } else {
                append('%')
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
