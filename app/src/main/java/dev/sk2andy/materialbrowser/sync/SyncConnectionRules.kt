package dev.sk2andy.materialbrowser.sync

object SyncConnectionRules {
    fun normalize(
        value: SyncConnectionSettings,
        iconCatalog: SyncDeviceIconCatalog,
    ): SyncConnectionSettings? {
        val endpoint = SyncEndpointRules.normalize(value.endpoint, allowRemoteHttp = true) ?: return null
        val username = value.username.trim()
        val deviceName = value.deviceName.trim()
        val localProfileId = value.localProfileId?.trim()
        if (username.isEmpty() || username.length > 128 || ':' in username) return null
        if (deviceName.isEmpty() || deviceName.length > 80 || deviceName.any(Char::isISOControl)) return null
        if (localProfileId != null && (localProfileId.isEmpty() || localProfileId.length > 128)) return null
        val icon = SyncDeviceIconDescriptor(value.iconCatalogId, value.iconAccentHue)
        if (runCatching { SyncDeviceIconRules.requireValid(icon) }.isFailure) return null
        if (!iconCatalog.contains(icon.catalogId)) return null
        return value.copy(
            endpoint = endpoint,
            username = username,
            deviceName = deviceName,
            localProfileId = localProfileId,
        )
    }
}
