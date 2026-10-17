import CandyShared
import Foundation

enum BrowserAddressBarPreference {
    private static let dockedKey = "candy.address-bar.docked.v1"
    private static let edgeKey = "candy.address-bar.edge.v1"
    private static let heightKey = "candy.address-bar.height.v1"

    static func isDocked(in preferences: UserDefaults) -> Bool {
        preferences.bool(forKey: dockedKey)
    }

    static func rememberedPlacement(from preferences: UserDefaults) -> AddressBarDockPlacement {
        AddressBarDockPlacement(
            edge: AddressBarDockEdge.companion.fromWireValue(value: preferences.string(forKey: edgeKey)),
            verticalFraction: Float(preferences.double(forKey: heightKey))
        ).normalized()
    }

    static func save(
        placement: AddressBarDockPlacement?,
        remembered: AddressBarDockPlacement,
        to preferences: UserDefaults
    ) {
        let normalized = remembered.normalized()
        preferences.set(placement != nil, forKey: dockedKey)
        preferences.set(normalized.edge.wireValue, forKey: edgeKey)
        preferences.set(Double(normalized.verticalFraction), forKey: heightKey)
    }
}
