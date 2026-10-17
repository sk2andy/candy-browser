import Foundation

@main
enum BrowserLocalProfileRestoreTests {
    static func main() {
        var updates: [BrowserLocalProfileRestore.Record] = []
        var creations: [BrowserLocalProfileRestore.Record] = []
        func restore(_ values: [[String: Any]]) {
            updates = []
            creations = []
            BrowserLocalProfileRestore.apply(
                values, defaultProfileId: "candy",
                updateDefault: { updates.append($0) },
                create: { creations.append($0) }
            )
        }

        restore([
            ["id": "candy", "emoji": "🔒", "isolationEnabled": true],
            ["id": "work", "emoji": "💼", "isolationEnabled": true],
        ])
        expect(updates == [.init(id: "candy", emoji: "🔒", isolationEnabled: true)],
               "default profile restores saved isolation and icon through update")
        expect(creations == [.init(id: "work", emoji: "💼", isolationEnabled: true)],
               "additional profile preserves its metadata through create")
        expect(!creations.contains { $0.id == "candy" },
               "existing default profile is never recreated")

        restore([
            ["id": "candy", "emoji": "🔒", "isolationEnabled": true],
            ["id": "candy", "emoji": "🍬", "isolationEnabled": false],
            ["id": "work", "emoji": "💼"],
            ["id": "work", "emoji": "🏠", "isolationEnabled": true],
        ])
        expect(updates.count == 1 && updates[0].isolationEnabled,
               "duplicate default record cannot undo restored isolation")
        expect(creations.count == 1 && creations[0].emoji == "💼",
               "duplicate additional profile follows first-record semantics")
        expect(!creations[0].isolationEnabled,
               "legacy record without isolation remains non-isolated")

        restore([
            ["id": "candy", "emoji": 42, "isolationEnabled": false],
            ["emoji": "🍬"],
            ["id": "candy", "emoji": "🔒", "isolationEnabled": true],
        ])
        expect(updates == [.init(id: "candy", emoji: "🔒", isolationEnabled: true)],
               "malformed records neither apply nor shadow the valid default")
        expect(creations.isEmpty, "malformed native records do not create profiles")

        restore([])
        expect(updates.isEmpty && creations.isEmpty,
               "missing preference leaves Kotlin defaults unchanged")
        print("BrowserLocalProfileRestoreTests: 9 passed")
    }

    private static func expect(_ condition: Bool, _ message: String) {
        guard condition else { fatalError("Failed: \(message)") }
    }
}
