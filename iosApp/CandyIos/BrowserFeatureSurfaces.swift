import SwiftUI
import UIKit

struct BrowserSharePayload: Identifiable {
    let id = UUID()
    let title: String
    let url: URL
}

struct BrowserFavoriteRemoval: Equatable, Identifiable {
    let id: UUID
    let item: BrowserFavoriteItem
}

struct BrowserFavoritesSurface: View {
    let items: [BrowserFavoriteItem]
    let pendingRemoval: BrowserFavoriteRemoval?
    let onOpen: (BrowserFavoriteItem) -> Void
    let onDelete: (BrowserFavoriteItem) -> Void
    let onUndo: (UUID) -> Void
    let onDismissRemoval: (UUID) -> Void
    let onDismiss: () -> Void

    @State private var query = ""

    private var visibleItems: [BrowserFavoriteItem] {
        BrowserFavoritesRules.filtered(items, query: query)
    }

    var body: some View {
        NavigationStack {
            Group {
                if items.isEmpty {
                    ContentUnavailableView(
                        "Keine Favoriten",
                        systemImage: "star",
                        description: Text("Markierte Seiten erscheinen hier.")
                    )
                } else if visibleItems.isEmpty {
                    ContentUnavailableView.search(text: query)
                } else {
                    List(visibleItems) { item in
                        favoriteRow(item)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    onDelete(item)
                                } label: {
                                    Label("Löschen", systemImage: "trash")
                                }
                            }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Favoriten")
            .navigationBarTitleDisplayMode(.inline)
            .searchable(
                text: $query,
                placement: .navigationBarDrawer(displayMode: .always),
                prompt: "Favoriten durchsuchen"
            )
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fertig", action: onDismiss)
                        .fontWeight(.semibold)
                }
            }
        }
        .safeAreaInset(edge: .bottom) {
            if let pendingRemoval {
                undoBanner(pendingRemoval)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
            }
        }
        .background(Color(uiColor: .systemBackground))
        .task(id: pendingRemoval?.id) {
            guard let removalId = pendingRemoval?.id else {
                return
            }
            try? await Task.sleep(for: .seconds(5))
            guard !Task.isCancelled else {
                return
            }
            onDismissRemoval(removalId)
        }
    }

    private func favoriteRow(_ item: BrowserFavoriteItem) -> some View {
        HStack(spacing: 12) {
            Button {
                onOpen(item)
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: "star.fill")
                        .foregroundStyle(.yellow)
                        .frame(width: 24)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.title)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text(item.address)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityHint("Öffnet den Favoriten")

            Button(role: .destructive) {
                onDelete(item)
            } label: {
                Image(systemName: "trash")
                    .frame(width: 40, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Favorit löschen")
        }
    }

    private func undoBanner(_ removal: BrowserFavoriteRemoval) -> some View {
        HStack(spacing: 12) {
            Text("Favorit gelöscht")
                .font(.subheadline)
                .lineLimit(1)
            Spacer(minLength: 8)
            Button("Rückgängig") {
                onUndo(removal.id)
            }
            .font(.subheadline.weight(.bold))
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 52)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        .shadow(color: .black.opacity(0.16), radius: 12, y: 5)
        .accessibilityElement(children: .contain)
    }
}

struct BrowserFindBar: View {
    @Binding var query: String
    let resultText: String
    let onSearch: (Bool) -> Void
    let onDismiss: () -> Void
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: 8) {
            TextField("Auf Seite suchen", text: $query)
                .focused($isFocused)
                .submitLabel(.search)
                .onSubmit { onSearch(false) }
                .padding(.horizontal, 12)
                .frame(minHeight: 38)
                .background(.primary.opacity(0.07), in: Capsule())

            Text(resultText)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(minWidth: 54)

            Button { onSearch(true) } label: {
                Image(systemName: "chevron.up").frame(width: 36, height: 44)
            }
            .disabled(query.isEmpty)
            .accessibilityLabel("Vorheriger Treffer")

            Button { onSearch(false) } label: {
                Image(systemName: "chevron.down").frame(width: 36, height: 44)
            }
            .disabled(query.isEmpty)
            .accessibilityLabel("Nächster Treffer")

            Button("Fertig", action: onDismiss)
                .font(.subheadline.weight(.semibold))
        }
        .buttonStyle(.plain)
        .padding(10)
        .modifier(CandyBrowserChrome())
        .padding(.horizontal, 12)
        .onAppear { isFocused = true }
    }
}

struct BrowserActivitySurface: UIViewControllerRepresentable {
    let payload: BrowserSharePayload

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(
            activityItems: [payload.title, payload.url],
            applicationActivities: nil
        )
    }

    func updateUIViewController(
        _ uiViewController: UIActivityViewController,
        context: Context
    ) {}
}
