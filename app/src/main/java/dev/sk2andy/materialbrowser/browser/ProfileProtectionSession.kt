package dev.sk2andy.materialbrowser.browser

/** Process-memory unlock state. Process death deliberately locks every protected profile. */
internal object ProfileProtectionSession {
    private val unlockedProfileIds = mutableSetOf<String>()
    private var backgroundedAtElapsedRealtime: Long? = null

    @Synchronized
    fun isUnlocked(profileId: String): Boolean = profileId in unlockedProfileIds

    @Synchronized
    fun isAccessible(profile: BrowserProfile?): Boolean =
        profile != null && (profile.protection == null || profile.id in unlockedProfileIds)

    @Synchronized
    fun unlock(profileId: String) {
        unlockedProfileIds += profileId
    }

    @Synchronized
    fun lock(profileIds: Collection<String>) {
        unlockedProfileIds -= profileIds.toSet()
    }

    @Synchronized
    fun forget(profileId: String) {
        unlockedProfileIds -= profileId
    }

    @Synchronized
    fun markBackgrounded(elapsedRealtime: Long) {
        backgroundedAtElapsedRealtime = elapsedRealtime
    }

    @Synchronized
    fun consumeBackgroundElapsed(nowElapsedRealtime: Long): Long? {
        val startedAt = backgroundedAtElapsedRealtime ?: return null
        backgroundedAtElapsedRealtime = null
        return (nowElapsedRealtime - startedAt).coerceAtLeast(0L)
    }
}
