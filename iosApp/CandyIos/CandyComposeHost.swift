import CandyShared
import SwiftUI
import UIKit

struct CandyComposeHost: View {
    @ObservedObject var browser: BrowserViewModel

    var body: some View {
        ZStack(alignment: .top) {
            CandyComposeControllerHost(browser: browser)
                .ignoresSafeArea(edges: .top)

            if let error = browser.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal, 12)
            }

            if browser.isFindVisible {
                BrowserFindBar(
                    query: Binding(
                        get: { browser.findQuery },
                        set: { browser.updateFindQuery($0) }
                    ),
                    resultText: browser.findResultText,
                    onSearch: browser.searchInPage,
                    onDismiss: browser.dismissFind
                )
                .padding(.top, 8)
            }
        }
        .sheet(
            item: Binding(
                get: { browser.readerDocument },
                set: { if $0 == nil { browser.dismissReader() } }
            )
        ) { document in
            BrowserReaderSurface(document: document)
        }
        .sheet(
            item: Binding(
                get: { browser.translationDocument },
                set: { if $0 == nil { browser.dismissTranslation() } }
            )
        ) { document in
            BrowserTranslationSurface(document: document)
        }
        .sheet(
            item: Binding(
                get: { browser.sharePayload },
                set: { if $0 == nil { browser.dismissShare() } }
            )
        ) { payload in
            BrowserActivitySurface(payload: payload)
        }
    }
}

private struct CandyComposeControllerHost: UIViewControllerRepresentable {
    @ObservedObject var browser: BrowserViewModel

    func makeCoordinator() -> Coordinator {
        Coordinator(browser: browser)
    }

    func makeUIViewController(context: Context) -> UIViewController {
        CandyComposeControllerFactory().create(
            state: context.coordinator.state,
            actionSink: context.coordinator
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        context.coordinator.state.updateViewport(value: browser.activeWebView)
        browser.tabCards.forEach { tab in
            context.coordinator.state.updatePreview(tabId: tab.id, value: tab.preview)
        }
        context.coordinator.state.retainPreviews(tabIds: browser.tabCards.map(\.id))
        context.coordinator.state.updateSnapshot(value: snapshot)
    }

    private var snapshot: BrowserViewportSnapshot {
        BrowserViewportSnapshot(
            address: browser.address,
            pageTitle: browser.pageTitle,
            tabCountLabel: browser.tabCountLabel,
            canGoBack: browser.canGoBack,
            canGoForward: browser.canGoForward,
            isLoading: browser.isLoading,
            menuItems: browser.menuItems,
            tabs: browser.tabCards.map { tab in
                BrowserViewportTab(
                    id: tab.id,
                    title: tab.title,
                    address: tab.address,
                    isSelected: tab.isSelected,
                    isFavorite: tab.isFavorite,
                    isPinned: tab.isPinned
                )
            },
            isTabOverviewVisible: browser.isTabOverviewVisible,
            tabOverviewMode: browser.tabOverviewMode.sharedMode,
            addressFocusRequest: browser.addressFocusRequest
        )
    }

    @MainActor
    final class Coordinator: NSObject, @preconcurrency BrowserViewportActionSink {
        let state: IosBrowserViewportState
        private let browser: BrowserViewModel

        init(browser: BrowserViewModel) {
            self.browser = browser
            state = IosBrowserViewportState(
                initialViewport: browser.activeWebView,
                initialSnapshot: BrowserViewportSnapshot(
                    address: browser.address,
                    pageTitle: browser.pageTitle,
                    tabCountLabel: browser.tabCountLabel,
                    canGoBack: browser.canGoBack,
                    canGoForward: browser.canGoForward,
                    isLoading: browser.isLoading,
                    menuItems: browser.menuItems,
                    tabs: browser.tabCards.map { tab in
                        BrowserViewportTab(
                            id: tab.id,
                            title: tab.title,
                            address: tab.address,
                            isSelected: tab.isSelected,
                            isFavorite: tab.isFavorite,
                            isPinned: tab.isPinned
                        )
                    },
                    isTabOverviewVisible: browser.isTabOverviewVisible,
                    tabOverviewMode: browser.tabOverviewMode.sharedMode,
                    addressFocusRequest: browser.addressFocusRequest
                )
            )
        }

        func addressChanged(value: String) {
            browser.editAddress(value)
        }

        func perform(action: CandyBrowserUiAction) {
            if action == .navigate {
                browser.perform(.navigate)
            } else if action == .back {
                browser.perform(.back)
            } else if action == .forward {
                browser.perform(.forward)
            } else if action == .reload {
                browser.perform(.reload)
            } else if action == .stop {
                browser.perform(.stop)
            } else if action == .newtab {
                browser.performTabs(.newtab)
            } else if action == .showtabs {
                browser.performTabs(.showoverview)
            }
        }

        func performMenu(action: BrowserFeatureMenuAction) {
            browser.performMenu(action)
        }

        func addressDragged(
            horizontal: Double,
            vertical: Double,
            velocityX: Double,
            viewportWidth: Double,
            isAddressEditing: Bool
        ) {
            browser.handleAddressSwipe(
                horizontal: horizontal,
                vertical: vertical,
                velocityX: velocityX,
                viewportWidth: viewportWidth,
                isAddressEditing: isAddressEditing
            )
            browser.handleChromeDrag(
                horizontal: horizontal,
                vertical: vertical,
                isAddressEditing: isAddressEditing
            )
        }

        func selectTab(tabId: String) {
            browser.performTabs(.selecttab, tabId: tabId)
        }

        func closeTab(tabId: String) {
            browser.performTabs(.closetab, tabId: tabId)
        }

        func hideTabOverview() {
            browser.performTabs(.hideoverview)
        }

        func changeTabOverviewMode(mode: CandyTabOverviewMode) {
            if mode == .hero {
                browser.updateTabOverviewMode(.hero)
            } else if mode == .grid {
                browser.updateTabOverviewMode(.grid)
            } else {
                browser.updateTabOverviewMode(.list)
            }
        }
    }
}

private extension BrowserTabOverviewMode {
    var sharedMode: CandyTabOverviewMode {
        switch self {
        case .hero: .hero
        case .grid: .grid
        case .list: .list
        }
    }
}
