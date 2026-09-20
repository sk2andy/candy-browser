package dev.sk2andy.materialbrowser.browser.gecko

internal class GeckoInlineVideoOpenRequestGate(
    private val nowMillis: () -> Long,
) {
    private data class PolicyIdentity(
        val navigationGeneration: Int,
        val mode: String,
        val enabled: Boolean,
        val isPrivate: Boolean,
    )

    private data class PendingOpen(
        val request: GeckoInlineVideoOpenRequest,
        val deadlineMillis: Long,
    )

    private var policy: PolicyIdentity? = null
    private var publishedRevision = 0L
    private var acknowledgedRevision = 0L
    private var minimumCompatibleRevision = 1L
    private var candidateRevision = 0L
    private var candidateIdentity: GeckoInlineVideoIdentity? = null
    private var pending: PendingOpen? = null

    val pendingDeadlineMillis: Long?
        get() = pending?.deadlineMillis

    fun invalidatePolicy(next: GeckoPrivacyPolicy, isPrivate: Boolean) {
        if (identity(next, isPrivate) != policy) {
            cancel()
            minimumCompatibleRevision = publishedRevision + 1
        }
    }

    fun publish(next: GeckoPrivacyPolicy, revision: Long, isPrivate: Boolean) {
        val nextIdentity = identity(next, isPrivate)
        if (nextIdentity != policy) {
            cancel()
            minimumCompatibleRevision = revision
        }
        policy = nextIdentity
        publishedRevision = revision
        candidateRevision = 0
        candidateIdentity = null
    }

    fun acknowledge(revision: Long) {
        if (revision in 1..publishedRevision) {
            acknowledgedRevision = maxOf(acknowledgedRevision, revision)
        }
    }

    fun updateCandidate(revision: Long, state: GeckoInlineVideoState) {
        if (revision != publishedRevision) return
        candidateRevision = revision
        candidateIdentity = if (state.isActive) {
            val documentNonce = state.documentNonce
            val elementNonce = state.elementNonce
            if (documentNonce != null && elementNonce != null) {
                GeckoInlineVideoIdentity(documentNonce, elementNonce)
            } else {
                null
            }
        } else {
            null
        }
        if (pending?.request?.identity?.let { it != candidateIdentity } == true) cancel()
    }

    fun accept(
        request: GeckoInlineVideoOpenRequest,
        revision: Long,
        mode: String?,
    ): GeckoInlineVideoOpenRequest? {
        if (!request.expected) cancel()
        val current = policy ?: return null
        if (
            current.isPrivate ||
            !current.enabled ||
            request.navigationGeneration != current.navigationGeneration ||
            revision < minimumCompatibleRevision ||
            (request.expected && mode != current.mode)
        ) return null
        if (pending != null) return null
        if (revision == publishedRevision && acknowledgedRevision == publishedRevision) {
            return request
        }
        if (
            request.expected &&
            acknowledgedRevision > 0 &&
            revision == acknowledgedRevision &&
            revision < publishedRevision &&
            (candidateRevision != publishedRevision || candidateIdentity == request.identity)
        ) {
            pending = PendingOpen(request, deadlineMillis = nowMillis() + OPEN_TIMEOUT_MILLIS)
        }
        return null
    }

    fun takeReady(): GeckoInlineVideoOpenRequest? {
        val open = pending ?: return null
        if (nowMillis() >= open.deadlineMillis) {
            cancel()
            return null
        }
        if (
            acknowledgedRevision != publishedRevision ||
            candidateRevision != publishedRevision ||
            candidateIdentity != open.request.identity
        ) return null
        cancel()
        return open.request
    }

    fun cancel() {
        pending = null
    }

    private fun identity(policy: GeckoPrivacyPolicy, isPrivate: Boolean) = PolicyIdentity(
        navigationGeneration = policy.navigationGeneration,
        mode = policy.inlineMediaPlayerMode,
        enabled = policy.inlineMediaPlayerEnabled,
        isPrivate = isPrivate,
    )

    private companion object {
        const val OPEN_TIMEOUT_MILLIS = 3_000L
    }
}
