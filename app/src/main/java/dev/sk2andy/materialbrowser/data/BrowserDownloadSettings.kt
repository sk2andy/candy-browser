package dev.sk2andy.materialbrowser.data

data class BrowserDownloadSettings(
    val managerMode: DownloadManagerMode = DownloadManagerMode.BuiltIn,
    val externalManagerId: String? = null,
    val shareSessionDataWithOneDm: Boolean = false,
) {
    fun normalized(): BrowserDownloadSettings {
        val safeManagerId = externalManagerId
            ?.takeIf { it.isNotBlank() && it.length <= MAX_EXTERNAL_MANAGER_ID_LENGTH }
            ?.takeIf { value -> value.none(Char::isISOControl) }
        val safeMode = if (managerMode == DownloadManagerMode.External && safeManagerId == null) {
            DownloadManagerMode.BuiltIn
        } else {
            managerMode
        }
        return copy(
            managerMode = safeMode,
            externalManagerId = safeManagerId,
        )
    }

    companion object {
        private const val MAX_EXTERNAL_MANAGER_ID_LENGTH = 512
    }
}

enum class DownloadManagerMode(val stableId: String) {
    BuiltIn("built_in"),
    AskEveryTime("ask_every_time"),
    External("external");

    companion object {
        fun fromStableId(value: String?): DownloadManagerMode =
            entries.firstOrNull { it.stableId == value } ?: BuiltIn
    }
}
