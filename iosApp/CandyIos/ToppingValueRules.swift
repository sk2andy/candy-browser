import Foundation

enum ToppingValueRules {
    static let maximumKeyCharacters = 256
    static let maximumEncodedValueBytes = 16 * 1_024
    static let maximumValuesPerScript = 128
    static let maximumSnapshotBytes = 31 * 1_024

    static func isValidKey(_ value: String) -> Bool {
        (1...maximumKeyCharacters).contains(value.count) &&
            !value.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains)
    }

    static func isValidEncodedValue(_ value: String) -> Bool {
        guard value.utf8.count <= maximumEncodedValueBytes,
              let data = value.data(using: .utf8) else {
            return false
        }
        return (try? JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed])) != nil
    }

    static func snapshot(_ values: [String: String]) -> String? {
        guard values.count <= maximumValuesPerScript,
              values.allSatisfy({ isValidKey($0.key) && isValidEncodedValue($0.value) }),
              let data = try? JSONSerialization.data(withJSONObject: values, options: [.sortedKeys]),
              data.count <= maximumSnapshotBytes else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }
}
