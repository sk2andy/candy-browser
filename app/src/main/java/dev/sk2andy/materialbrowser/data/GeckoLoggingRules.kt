package dev.sk2andy.materialbrowser.data

/** Bounds and validation for Gecko's native module-level logging preferences. */
internal object GeckoLoggingRules {
    const val DEFAULT_MODULES =
        "nsHttp:5,nsSocketTransport:5,nsHostResolver:5,cookie:5,console:5"
    const val MAX_MODULES_LENGTH = 1_024
    const val MAX_MODULE_COUNT = 16
    const val MAX_EXPORT_BYTES = 8 * 1024 * 1024
    const val MAX_RETAINED_SEGMENTS = 4
    const val DIRECTORY_NAME = "gecko_logs"

    private val moduleNamePattern = Regex("[A-Za-z_.-][A-Za-z0-9_.-]*")

    fun isValidModules(value: String): Boolean = parseModules(value) != null

    fun normalizedModules(value: String): String = parseModules(value)
        ?.joinToString(",") { (name, level) -> "$name:$level" }
        ?: DEFAULT_MODULES

    private fun parseModules(value: String): List<Pair<String, Int>>? {
        if (value.isBlank() || value.length > MAX_MODULES_LENGTH) return null
        val entries = value.split(',')
        if (entries.size > MAX_MODULE_COUNT) return null
        val modules = linkedMapOf<String, Int>()
        entries.forEach { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return null
            val name = parts[0].trim()
            val level = parts[1].trim()
            if (!moduleNamePattern.matches(name) || name.startsWith("config.") ||
                level.length != 1 || level[0] !in '0'..'5'
            ) {
                return null
            }
            modules[name] = level[0] - '0'
        }
        return modules.map { (name, level) -> name to level }
    }
}
