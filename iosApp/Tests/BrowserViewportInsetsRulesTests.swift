import Foundation

@main
enum BrowserViewportInsetsRulesTests {
    static func main() {
        expect(inset(safeTop: 62, top: 0, height: 800) == 62, "edge-to-edge viewport clears island")
        expect(inset(safeTop: 62, top: 62, height: 738) == 0, "already-safe viewport gets no second inset")
        expect(inset(safeTop: 62, top: 100, height: 700) == 0, "viewport below safe area gets no inset")
        expect(inset(safeTop: 24, top: 0, height: 400) == 24, "resized window uses current safe area")
        expect(inset(safeTop: 62, top: 0, height: 20) == 20, "occlusion cannot exceed viewport")
        expect(inset(safeTop: 62, top: 0, height: 0) == 0, "unlaid-out viewport has no inset")
        expect(inset(safeTop: .nan, top: 0, height: 800) == 0, "invalid geometry fails closed")

        expect(offset(0, oldInset: 0, newInset: 62) == -62, "initial page remains at document top")
        expect(offset(-62, oldInset: 62, newInset: 24) == -24, "top anchor follows changed safe area")
        expect(offset(320, oldInset: 62, newInset: 24) == 320, "reading position is preserved")
        expect(offset(100, oldInset: 0, newInset: 62) == 100, "restored scroll position is preserved")
        expect(offset(-82, oldInset: 62, newInset: 24) == -82, "overscroll is not forcibly repositioned")
        expect(offset(0, oldInset: 0, newInset: 0) == 0, "zero-top viewport stays unchanged")
        print("BrowserViewportInsetsRulesTests: 13 passed")
    }

    private static func inset(safeTop: Double, top: Double, height: Double) -> Double {
        BrowserViewportInsetsRules.topInset(
            windowSafeAreaTop: safeTop, viewportTop: top, viewportHeight: height
        )
    }

    private static func offset(_ value: Double, oldInset: Double, newInset: Double) -> Double {
        BrowserViewportInsetsRules.topAnchoredOffset(
            currentOffset: value, previousAdjustedInset: oldInset, newInset: newInset
        )
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else { fatalError("Failed: \(message)") }
    }
}
