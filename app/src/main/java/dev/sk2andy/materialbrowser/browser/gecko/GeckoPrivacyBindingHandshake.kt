package dev.sk2andy.materialbrowser.browser.gecko

internal data class GeckoPrivacyBindingHandshake(
    val publishedRevision: Long = 0,
    val acknowledgedRevision: Long = 0,
    val bootstrapStarted: Boolean = false,
    val authenticatedBindingRevisions: Set<Long> = emptySet(),
    val sessionBound: Boolean = false,
) {
    val isCurrentPolicyAcknowledged: Boolean
        get() = publishedRevision > 0 && acknowledgedRevision == publishedRevision

    val isReady: Boolean
        get() = isCurrentPolicyAcknowledged && sessionBound
}

internal data class GeckoPrivacyBindingTransition(
    val state: GeckoPrivacyBindingHandshake,
    val accepted: Boolean,
    val startBootstrap: Boolean = false,
)

internal object GeckoPrivacyBindingHandshakeRules {
    fun publish(state: GeckoPrivacyBindingHandshake): GeckoPrivacyBindingHandshake = state.copy(
        publishedRevision = state.publishedRevision + 1,
    )

    fun acknowledgePolicy(
        state: GeckoPrivacyBindingHandshake,
        revision: Long,
    ): GeckoPrivacyBindingTransition {
        if (revision !in 1..state.publishedRevision) {
            return GeckoPrivacyBindingTransition(state = state, accepted = false)
        }
        val shouldStartBootstrap = !state.bootstrapStarted
        return GeckoPrivacyBindingTransition(
            state = state.copy(
                acknowledgedRevision = maxOf(state.acknowledgedRevision, revision),
                bootstrapStarted = true,
            ),
            accepted = true,
            startBootstrap = shouldStartBootstrap,
        )
    }

    fun authenticateSession(
        state: GeckoPrivacyBindingHandshake,
        revision: Long,
    ): GeckoPrivacyBindingTransition {
        val accepted = state.bootstrapStarted && revision in 1..state.publishedRevision
        return GeckoPrivacyBindingTransition(
            state = if (accepted) {
                state.copy(
                    authenticatedBindingRevisions =
                        state.authenticatedBindingRevisions + revision,
                )
            } else {
                state
            },
            accepted = accepted,
        )
    }

    fun bindSession(
        state: GeckoPrivacyBindingHandshake,
        revision: Long,
    ): GeckoPrivacyBindingTransition {
        val accepted = revision in state.authenticatedBindingRevisions
        return GeckoPrivacyBindingTransition(
            state = if (accepted) {
                state.copy(
                    authenticatedBindingRevisions =
                        state.authenticatedBindingRevisions - revision,
                    sessionBound = true,
                )
            } else {
                state
            },
            accepted = accepted,
        )
    }
}
