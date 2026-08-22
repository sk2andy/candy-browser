package dev.sk2andy.materialbrowser.browser.cast

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSessionRulesTest {
    @Test
    fun `successful current load may pause exact local endpoint`() {
        val identity = identity()

        assertTrue(
            CastSessionRules.shouldPauseLocalAfterLoad(
                succeeded = true,
                expectedGeneration = 4L,
                currentGeneration = 4L,
                sameSession = true,
                sameClient = true,
                loadedIdentity = identity,
                currentCandidateIdentity = identity,
                loadedSourceUrl = "https://media.example/video.mp4",
                currentCandidateSourceUrl = "https://media.example/video.mp4",
            ),
        )
    }

    @Test
    fun `disconnect replacement and candidate invalidation reject late load`() {
        val identity = identity()
        val replacement = identity.copy(documentId = "replacement")

        assertFalse(eligible(identity, currentGeneration = 5L, currentIdentity = identity))
        assertFalse(eligible(identity, sameSession = false, currentIdentity = identity))
        assertFalse(eligible(identity, sameClient = false, currentIdentity = identity))
        assertFalse(eligible(identity, currentIdentity = replacement))
        assertFalse(eligible(identity, currentIdentity = null))
        assertFalse(
            eligible(
                identity,
                currentIdentity = identity,
                currentSourceUrl = "https://media.example/replacement.mp4",
            ),
        )
    }

    private fun eligible(
        identity: CastMediaIdentity,
        currentGeneration: Long = 4L,
        sameSession: Boolean = true,
        sameClient: Boolean = true,
        currentIdentity: CastMediaIdentity?,
        currentSourceUrl: String? = "https://media.example/video.mp4",
    ): Boolean = CastSessionRules.shouldPauseLocalAfterLoad(
        succeeded = true,
        expectedGeneration = 4L,
        currentGeneration = currentGeneration,
        sameSession = sameSession,
        sameClient = sameClient,
        loadedIdentity = identity,
        currentCandidateIdentity = currentIdentity,
        loadedSourceUrl = "https://media.example/video.mp4",
        currentCandidateSourceUrl = currentSourceUrl,
    )

    private fun identity(): CastMediaIdentity = CastMediaIdentity(
        tabId = "tab",
        navigationGeneration = 2,
        documentId = "document",
        mediaId = "media",
        origin = "https://media.example",
    )
}
