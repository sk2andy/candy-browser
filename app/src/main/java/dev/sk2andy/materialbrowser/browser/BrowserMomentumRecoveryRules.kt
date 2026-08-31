package dev.sk2andy.materialbrowser.browser

internal object BrowserMomentumRecoveryRules {
    fun observe(
        previousScrollY: Int,
        currentScrollY: Int,
        direction: Int,
        stalledFrames: Int,
        shadowRunning: Boolean,
        shadowVelocity: Float,
        minimumRecoveryVelocity: Float,
        requiredStalledFrames: Int,
    ): BrowserMomentumWatchdogObservation {
        if (!shadowRunning || shadowVelocity < minimumRecoveryVelocity) {
            return BrowserMomentumWatchdogObservation(
                stalledFrames = stalledFrames,
                decision = BrowserMomentumWatchdogDecision.Stop,
            )
        }
        val updatedStalledFrames = if (
            (currentScrollY - previousScrollY) * direction > 0
        ) {
            0
        } else {
            stalledFrames + 1
        }
        return BrowserMomentumWatchdogObservation(
            stalledFrames = updatedStalledFrames,
            decision = if (updatedStalledFrames >= requiredStalledFrames) {
                BrowserMomentumWatchdogDecision.Recover
            } else {
                BrowserMomentumWatchdogDecision.Continue
            },
        )
    }
}

internal data class BrowserMomentumWatchdogObservation(
    val stalledFrames: Int,
    val decision: BrowserMomentumWatchdogDecision,
)

internal enum class BrowserMomentumWatchdogDecision {
    Continue,
    Recover,
    Stop,
}
