import Foundation

enum BrowserViewportInsetsRules {
    static func topInset(
        windowSafeAreaTop: Double,
        viewportTop: Double,
        viewportHeight: Double
    ) -> Double {
        guard windowSafeAreaTop.isFinite, viewportTop.isFinite,
              viewportHeight.isFinite, viewportHeight > 0 else { return 0 }
        return min(max(windowSafeAreaTop - viewportTop, 0), viewportHeight)
    }

    static func topAnchoredOffset(
        currentOffset: Double,
        previousAdjustedInset: Double,
        newInset: Double
    ) -> Double {
        guard currentOffset.isFinite, previousAdjustedInset.isFinite, newInset.isFinite,
              abs(currentOffset + previousAdjustedInset) <= 1 else { return currentOffset }
        return -max(newInset, 0)
    }
}
