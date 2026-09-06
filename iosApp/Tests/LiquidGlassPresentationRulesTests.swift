import Foundation

@main
struct LiquidGlassPresentationRulesTests {
    static func main() {
        testNativeGlassStartsOnIos26()
        testContainerUsesStableMergeSpacing()
        testClearGlassKeepsReadableTintAndSoftEdges()
    }

    private static func testNativeGlassStartsOnIos26() {
        precondition(!LiquidGlassPresentationRules.usesNativeGlass(systemMajorVersion: 25))
        precondition(LiquidGlassPresentationRules.usesNativeGlass(systemMajorVersion: 26))
    }

    private static func testContainerUsesStableMergeSpacing() {
        precondition(LiquidGlassPresentationRules.mergeSpacing == 12)
    }

    private static func testClearGlassKeepsReadableTintAndSoftEdges() {
        precondition(LiquidGlassPresentationRules.addressTintOpacity < 0.2)
        precondition(LiquidGlassPresentationRules.menuTintOpacity < 0.3)
        precondition(
            LiquidGlassPresentationRules.edgeSofteningLocation <
                LiquidGlassPresentationRules.edgeClearLocation
        )
        precondition(LiquidGlassPresentationRules.edgeClearLocation < 0.2)
    }
}
