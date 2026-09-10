import Foundation

struct BrowserFavoriteItem: Hashable, Identifiable {
    let address: String

    var id: String { address }

    var title: String {
        BrowserFavoritesRules.displayTitle(for: address)
    }
}

enum BrowserFavoritesRules {
    static func items(from addresses: Set<String>) -> [BrowserFavoriteItem] {
        addresses
            .map(BrowserFavoriteItem.init(address:))
            .sorted { lhs, rhs in
                let titleComparison = lhs.title.localizedCaseInsensitiveCompare(rhs.title)
                if titleComparison == .orderedSame {
                    return lhs.address.localizedCaseInsensitiveCompare(rhs.address) == .orderedAscending
                }
                return titleComparison == .orderedAscending
            }
    }

    static func filtered(
        _ items: [BrowserFavoriteItem],
        query: String
    ) -> [BrowserFavoriteItem] {
        let value = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else {
            return items
        }
        return items.filter { item in
            item.title.localizedStandardContains(value) ||
                item.address.localizedStandardContains(value)
        }
    }

    static func displayTitle(for address: String) -> String {
        guard let host = URL(string: address)?.host, !host.isEmpty else {
            return address
        }
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }
}
