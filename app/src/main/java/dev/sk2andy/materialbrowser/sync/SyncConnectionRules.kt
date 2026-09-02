package dev.sk2andy.materialbrowser.sync

object SyncConnectionRules {
    fun normalize(
        value: SyncConnectionSettings,
        iconCatalog: SyncDeviceIconCatalog,
    ): SyncConnectionSettings? {
        val endpoint = SyncEndpointRules.normalize(value.endpoint) ?: return null
        val username = value.username.trim()
        val deviceName = value.deviceName.trim()
        if (username.isEmpty() || username.length > 128 || ':' in username) return null
        if (deviceName.isEmpty() || deviceName.length > 80 || deviceName.any(Char::isISOControl)) return null
        val icon = SyncDeviceIconDescriptor(value.iconCatalogId, value.iconAccentHue)
        if (runCatching { SyncDeviceIconRules.requireValid(icon) }.isFailure) return null
        if (!iconCatalog.contains(icon.catalogId)) return null
        return value.copy(endpoint = endpoint, username = username, deviceName = deviceName)
    }
}
