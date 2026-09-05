import SwiftUI

struct ContentView: View {
    @ObservedObject var browser: BrowserViewModel

    var body: some View {
        BrowserWebView(webView: browser.webView)
            .ignoresSafeArea(edges: .bottom)
            .safeAreaInset(edge: .bottom, spacing: 0) {
                VStack(spacing: 8) {
                    if let error = browser.errorMessage {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                            .lineLimit(2)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    HStack(spacing: 10) {
                        Button {
                            browser.perform(.back)
                        } label: {
                            Image(systemName: "chevron.backward")
                        }
                        .disabled(!browser.canGoBack)

                        Button {
                            browser.perform(.forward)
                        } label: {
                            Image(systemName: "chevron.forward")
                        }
                        .disabled(!browser.canGoForward)

                        TextField("Adresse oder Suche", text: $browser.address)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.URL)
                            .submitLabel(.go)
                            .onSubmit {
                                browser.perform(.navigate)
                            }

                        Button(action: browser.performReloadAction) {
                            Image(systemName: browser.isLoading ? "xmark" : "arrow.clockwise")
                        }

                        Button {
                            browser.perform(.navigate)
                        } label: {
                            Image(systemName: "arrow.right")
                        }
                    }
                    .buttonStyle(.plain)
                    .font(.body.weight(.semibold))
                    .padding(.horizontal, 16)
                    .frame(minHeight: 54)
                    .modifier(CandyBrowserChrome())
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
            }
    }
}

private struct CandyBrowserChrome: ViewModifier {
    private let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: shape)
        } else {
            content
                .background(.ultraThinMaterial, in: shape)
                .overlay {
                    shape.stroke(.white.opacity(0.24), lineWidth: 0.5)
                }
                .shadow(color: .black.opacity(0.12), radius: 14, y: 7)
        }
    }
}
