import SwiftUI
import Translation
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

struct BrowserReaderSurface: View {
    let document: BrowserPageDocument
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(document.title)
                        .font(.largeTitle.bold())
                    if !document.address.isEmpty {
                        Text(document.address)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(document.text)
                        .font(.system(.body, design: .serif))
                        .lineSpacing(6)
                        .textSelection(.enabled)
                }
                .frame(maxWidth: 720, alignment: .leading)
                .padding(24)
            }
            .navigationTitle("Lesemodus")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fertig") { dismiss() }
                }
            }
        }
    }
}

struct BrowserTranslationSurface: View {
    let document: BrowserPageDocument
    @Environment(\.dismiss) private var dismiss
    @State private var presentsTranslation = false

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(document.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(24)
                    .textSelection(.enabled)
            }
            .navigationTitle("Übersetzen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Fertig") { dismiss() }
                }
            }
        }
        .translationPresentation(
            isPresented: $presentsTranslation,
            text: document.text
        )
        .onAppear { presentsTranslation = true }
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
