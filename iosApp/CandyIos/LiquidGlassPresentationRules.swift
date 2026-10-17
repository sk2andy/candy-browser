enum LiquidGlassPresentationRules {
    static let minimumSystemMajorVersion = 26
    static let chromeSpringResponse = 0.46
    static let chromeSpringDamping = 0.78
    static let mergeSpacing: Double = 12
    static let addressTintOpacity = 0.12
    static let menuTintOpacity = 0.22
    static let edgeSofteningLocation = 0.055
    static let edgeClearLocation = 0.14

    static func usesNativeGlass(
        systemMajorVersion: Int,
        reduceTransparency: Bool = false
    ) -> Bool {
        !reduceTransparency && systemMajorVersion >= minimumSystemMajorVersion
    }
}
