import SwiftUI
import UIKit

struct BrowserSharePayload: Identifiable {
    let id = UUID()
    let title: String
    let url: URL
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
