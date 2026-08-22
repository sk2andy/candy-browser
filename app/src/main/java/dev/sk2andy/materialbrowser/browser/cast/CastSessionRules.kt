package dev.sk2andy.materialbrowser.browser.cast

internal object CastSessionRules {
    fun shouldPauseLocalAfterLoad(
        succeeded: Boolean,
        expectedGeneration: Long,
        currentGeneration: Long,
        sameSession: Boolean,
        sameClient: Boolean,
        loadedIdentity: CastMediaIdentity,
        currentCandidateIdentity: CastMediaIdentity?,
        loadedSourceUrl: String,
        currentCandidateSourceUrl: String?,
    ): Boolean = succeeded &&
        expectedGeneration == currentGeneration &&
        sameSession &&
        sameClient &&
        loadedIdentity == currentCandidateIdentity &&
        loadedSourceUrl == currentCandidateSourceUrl
}
