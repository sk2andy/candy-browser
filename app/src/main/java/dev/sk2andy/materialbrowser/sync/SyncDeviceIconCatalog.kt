package dev.sk2andy.materialbrowser.sync

import java.io.InputStream
import org.json.JSONArray
import org.json.JSONObject

data class SyncDeviceIconCatalog(
    val icons: List<SyncDeviceIconDefinition>,
) {
    init {
        require(icons.isNotEmpty())
        require(icons.map(SyncDeviceIconDefinition::id).distinct().size == icons.size)
    }

    fun contains(id: String): Boolean = icons.any { it.id == id }

    companion object {
        fun decode(input: InputStream): SyncDeviceIconCatalog =
            input.bufferedReader(Charsets.UTF_8).use { decode(it.readText()) }

        fun decode(raw: String): SyncDeviceIconCatalog {
            val root = parseStrictJsonObject(raw)
            root.requireExactKeys("schemaVersion", "icons")
            require(root.strictInt("schemaVersion") == 1)
            val values = root.get("icons") as? JSONArray ?: throw IllegalArgumentException("Invalid icon catalog")
            require(values.length() in 1..128)
            return SyncDeviceIconCatalog(
                icons = buildList {
                    repeat(values.length()) { index ->
                        val value = values.get(index) as? JSONObject
                            ?: throw IllegalArgumentException("Invalid icon entry")
                        value.requireExactKeys("id", "emoji", "label")
                        val icon = SyncDeviceIconDefinition(
                            id = value.strictString("id", 1, 48),
                            emoji = value.strictString("emoji", 1, 16),
                            label = value.strictString("label", 1, 80),
                        )
                        require(icon.id.matches(CATALOG_ID))
                        add(icon)
                    }
                },
            )
        }
    }
}

object SyncDeviceIconRules {
    fun requireValid(descriptor: SyncDeviceIconDescriptor) {
        require(descriptor.catalogId.matches(CATALOG_ID))
        require(descriptor.accentHue in 0..359)
    }

    fun defaultForAndroid(fingerprint: String): SyncDeviceIconDescriptor {
        val fingerprintBytes = SyncBase64.decode(fingerprint, expectedBytes = 32)
        val hueSeed = fingerprintBytes[0].toInt().and(0xff) shl 8 or
            fingerprintBytes[1].toInt().and(0xff)
        return SyncDeviceIconDescriptor(catalogId = "phone", accentHue = hueSeed % 360)
    }
}

private val CATALOG_ID = Regex("[a-z][a-z0-9-]{0,31}")

internal fun JSONObject.requireExactKeys(vararg expected: String) {
    val actual = keys().asSequence().toSet()
    require(actual == expected.toSet()) { "Unexpected JSON fields" }
}

internal fun JSONObject.strictString(name: String, minimum: Int = 1, maximum: Int = 4_096): String {
    val value = get(name) as? String ?: throw IllegalArgumentException("Invalid $name")
    require(value.length in minimum..maximum)
    return value
}

internal fun JSONObject.strictInt(name: String): Int {
    val value = get(name)
    require(value is Int || value is Long)
    return (value as Number).toLong().also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
}
