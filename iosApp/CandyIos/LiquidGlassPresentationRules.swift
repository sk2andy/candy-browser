enum LiquidGlassPresentationRules {
    static let minimumSystemMajorVersion = 26
    static let mergeSpacing: Double = 12
    static let addressTintOpacity = 0.12
    static let menuTintOpacity = 0.22
    static let edgeSofteningLocation = 0.055
    static let edgeClearLocation = 0.14

    static func usesNativeGlass(systemMajorVersion: Int) -> Bool {
        systemMajorVersion >= minimumSystemMajorVersion
    }
}
