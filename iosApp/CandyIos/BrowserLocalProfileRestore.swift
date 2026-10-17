import Foundation

/// Replays the native preference envelope; Kotlin validates profile mutations and capacity.
enum BrowserLocalProfileRestore {
    struct Record: Equatable {
        let id: String
        let emoji: String
        let isolationEnabled: Bool
    }

    static func apply(
        _ values: [[String: Any]],
        defaultProfileId: String,
        updateDefault: (Record) -> Void,
        create: (Record) -> Void
    ) {
        var seenIds: Set<String> = []
        for value in values {
            guard let id = value["id"] as? String,
                  let emoji = value["emoji"] as? String,
                  seenIds.insert(id).inserted else { continue }
            let record = Record(
                id: id,
                emoji: emoji,
                isolationEnabled: value["isolationEnabled"] as? Bool ?? false
            )
            if id == defaultProfileId {
                updateDefault(record)
            } else {
                create(record)
            }
        }
    }
}
