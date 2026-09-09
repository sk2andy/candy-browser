package dev.sk2andy.materialbrowser.browser.gecko

internal enum class GeckoWebAuthnDeliveryDecision {
    Wait,
    Complete,
    Reject,
    Ignore,
}

/** Keeps Activity results behind exact pending-request and resumed-host boundaries. */
internal object GeckoWebAuthnDeliveryRules {
    fun decide(
        hasPendingRequest: Boolean,
        hasActivityResult: Boolean,
        isHostResumed: Boolean,
        isHostClosed: Boolean,
        isPendingRequestCurrent: Boolean,
    ): GeckoWebAuthnDeliveryDecision {
        if (isHostClosed || !hasPendingRequest) return GeckoWebAuthnDeliveryDecision.Ignore
        if (!isPendingRequestCurrent) return GeckoWebAuthnDeliveryDecision.Reject
        if (!hasActivityResult || !isHostResumed) return GeckoWebAuthnDeliveryDecision.Wait
        return GeckoWebAuthnDeliveryDecision.Complete
    }
}
