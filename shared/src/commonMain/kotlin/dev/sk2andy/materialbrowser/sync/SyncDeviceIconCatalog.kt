package dev.sk2andy.materialbrowser.sync

data class SyncDeviceIconCatalog(val icons: List<SyncDeviceIconDefinition>) {
    init { require(icons.isNotEmpty()); require(icons.map(SyncDeviceIconDefinition::id).distinct().size == icons.size) }
    fun contains(id: String): Boolean = icons.any { it.id == id }
    companion object {
        @Throws(Exception::class)
        fun decode(raw: String): SyncDeviceIconCatalog {
            val normalized = raw.trim()
            require(normalized.startsWith('{') && normalized.endsWith('}'))
            val version = Regex("\\\"schemaVersion\\\"\\s*:\\s*(\\d+)").find(normalized)?.groupValues?.get(1)?.toIntOrNull()
            require(version == 1)
            val icons = ICON.findAll(normalized).map { match ->
                SyncDeviceIconDefinition(match.groupValues[1], match.groupValues[2], match.groupValues[3]).also { icon ->
                    require(icon.id.matches(CATALOG_ID)); require(icon.emoji.length in 1..16); require(icon.label.length in 1..80)
                }
            }.toList()
            require(icons.size in 1..128)
            return SyncDeviceIconCatalog(icons)
        }
    }
}

object SyncDeviceIconRules {
    fun requireValid(descriptor: SyncDeviceIconDescriptor) { require(descriptor.catalogId.matches(CATALOG_ID)); require(descriptor.accentHue in 0..359) }
    fun defaultForAndroid(fingerprint: String): SyncDeviceIconDescriptor {
        val bytes = SyncBase64.decode(fingerprint, expectedBytes = 32)
        val hueSeed = bytes[0].toInt().and(0xff) shl 8 or bytes[1].toInt().and(0xff)
        return SyncDeviceIconDescriptor("phone", hueSeed % 360)
    }
}

private val CATALOG_ID = Regex("[a-z][a-z0-9-]{0,31}")
private val ICON = Regex("\\{\\s*\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"emoji\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"label\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*\\}")
