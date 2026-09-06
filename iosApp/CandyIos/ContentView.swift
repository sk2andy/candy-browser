import CandyShared
import SwiftUI

struct ContentView: View {
    @ObservedObject var browser: BrowserViewModel
    @FocusState private var isAddressEditing: Bool
    @State private var browserViewportWidth: CGFloat = 1

    var body: some View {
        ZStack(alignment: .top) {
            BrowserWebView(webView: browser.activeWebView)
                .id(browser.selectedTabId)
                .ignoresSafeArea(edges: .bottom)

            if browser.isTabOverviewVisible {
                TabOverview(
                    tabs: browser.tabCards,
                    mode: browser.tabOverviewMode,
                    onModeChange: browser.updateTabOverviewMode,
                    onSelect: { tabId in
                        browser.performTabs(.selecttab, tabId: tabId)
                    },
                    onClose: { tabId in
                        browser.performTabs(.closetab, tabId: tabId)
                    },
                    onDismiss: {
                        browser.performTabs(.hideoverview)
                    }
                )
                .transition(.move(edge: .bottom).combined(with: .opacity))
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
                .transition(.move(edge: .top).combined(with: .opacity))
                .zIndex(2)
            }
        }
        .animation(.snappy(duration: 0.28), value: browser.isTabOverviewVisible)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            browserChrome
        }
        .onChange(of: browser.addressFocusRequest) {
            isAddressEditing = true
        }
        .onGeometryChange(for: CGFloat.self) { geometry in
            geometry.size.width
        } action: { width in
            browserViewportWidth = width
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

    private var browserChrome: some View {
        VStack(spacing: 8) {
            if let error = browser.errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            HStack(spacing: 8) {
                Button {
                    isAddressEditing = false
                    browser.performTabs(
                        browser.isTabOverviewVisible ? .hideoverview : .showoverview
                    )
                } label: {
                    Text(browser.tabCountLabel)
                        .font(.caption.weight(.bold))
                        .monospacedDigit()
                        .frame(width: 30, height: 30)
                        .overlay {
                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                .stroke(.primary, lineWidth: 1.5)
                        }
                }
                .accessibilityLabel("Tabs")

                TextField(
                    "Adresse oder Suche",
                    text: Binding(
                        get: { browser.address },
                        set: { value in
                            browser.editAddress(value)
                        }
                    )
                )
                .focused($isAddressEditing)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .submitLabel(.go)
                .onSubmit {
                    browser.perform(.navigate)
                }
                .padding(.horizontal, 12)
                .frame(minHeight: 38)
                .background(.primary.opacity(0.07), in: Capsule())
                .simultaneousGesture(
                    DragGesture(minimumDistance: 8, coordinateSpace: .local)
                        .onEnded { value in
                            browser.handleAddressSwipe(
                                horizontal: value.translation.width,
                                vertical: value.translation.height,
                                velocityX: value.velocity.width,
                                viewportWidth: browserViewportWidth,
                                isAddressEditing: isAddressEditing
                            )
                        }
                )

                Button {
                    browser.performTabs(.newtab)
                } label: {
                    Image(systemName: "plus")
                        .frame(width: 30, height: 30)
                }
                .accessibilityLabel("Neuer Tab")

                Menu {
                    ForEach(browserMenuGroups(browser.menuItems)) { group in
                        Section(group.title) {
                            ForEach(group.items, id: \.stableId) { item in
                                Button {
                                    isAddressEditing = false
                                    browser.performMenu(item.action)
                                } label: {
                                    Label(
                                        browserMenuLabel(item),
                                        systemImage: browserMenuSystemImage(item.action)
                                    )
                                }
                                .disabled(!item.enabled)
                            }
                        }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .rotationEffect(.degrees(90))
                        .frame(width: 30, height: 30)
                }
                .menuOrder(.fixed)
                .accessibilityLabel("Mehr")
            }
            .buttonStyle(.plain)
            .font(.body.weight(.semibold))
            .padding(.horizontal, 12)
            .frame(minHeight: 56)
            .modifier(CandyBrowserChrome())
            .simultaneousGesture(
                DragGesture(minimumDistance: 8, coordinateSpace: .local)
                    .onEnded { value in
                        browser.handleChromeDrag(
                            horizontal: value.translation.width,
                            vertical: value.translation.height,
                            isAddressEditing: isAddressEditing
                        )
                    }
            )
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 8)
    }
}

private struct BrowserMenuGroup: Identifiable {
    let id: String
    let title: String
    let items: [BrowserFeatureMenuItem]
}

private func browserMenuGroups(_ items: [BrowserFeatureMenuItem]) -> [BrowserMenuGroup] {
    [
        BrowserMenuGroup(
            id: "toolbar",
            title: "Werkzeugleiste",
            items: items.filter { $0.section == .toolbar }
        ),
        BrowserMenuGroup(
            id: "page",
            title: "Seite",
            items: items.filter { $0.section == .page }
        ),
    ].filter { !$0.items.isEmpty }
}

private func browserMenuLabel(_ item: BrowserFeatureMenuItem) -> String {
    if let dynamicLabel = item.dynamicLabel, !dynamicLabel.isEmpty {
        return dynamicLabel
    }
    let key = item.labelKey
    if key == .back {
        return "Zurück"
    } else if key == .forward {
        return "Vor"
    } else if key == .reload {
        return "Neu laden"
    } else if key == .stoploading {
        return "Laden stoppen"
    } else if key == .newtab {
        return "Neuer Tab"
    } else if key == .closetab {
        return "Tab schließen"
    } else if key == .addfavorite {
        return "Favorit hinzufügen"
    } else if key == .removefavorite {
        return "Favorit entfernen"
    } else if key == .pintab {
        return "Tab anheften"
    } else if key == .unpintab {
        return "Tab lösen"
    } else if key == .reader {
        return "Lesemodus"
    } else if key == .translate {
        return "Seite übersetzen"
    } else if key == .findinpage {
        return "Auf Seite suchen"
    } else if key == .share {
        return "Teilen"
    } else if key == .openexternal {
        return "Extern öffnen"
    } else if key == .print {
        return "Drucken"
    } else {
        return "Tabs"
    }
}

private func browserMenuSystemImage(_ action: BrowserFeatureMenuAction) -> String {
    if action == .back {
        "chevron.backward"
    } else if action == .forward {
        "chevron.forward"
    } else if action == .reload {
        "arrow.clockwise"
    } else if action == .stop {
        "xmark"
    } else if action == .newtab {
        "plus"
    } else if action == .closetab {
        "xmark.square"
    } else if action == .togglefavorite {
        "star"
    } else if action == .togglepinned {
        "pin"
    } else if action == .openreader {
        "doc.text"
    } else if action == .translatepage {
        "translate"
    } else if action == .findinpage {
        "magnifyingglass"
    } else if action == .share {
        "square.and.arrow.up"
    } else if action == .openexternal {
        "safari"
    } else if action == .print {
        "printer"
    } else {
        "square.on.square"
    }
}

private struct TabOverview: View {
    let tabs: [BrowserTabCard]
    let mode: BrowserTabOverviewMode
    let onModeChange: (BrowserTabOverviewMode) -> Void
    let onSelect: (String) -> Void
    let onClose: (String) -> Void
    let onDismiss: () -> Void
    @State private var centeredTabId: String?

    var body: some View {
        GeometryReader { geometry in
            VStack(spacing: 12) {
                HStack(spacing: 12) {
                    Text("Tabs")
                        .font(.largeTitle.bold())
                    Spacer(minLength: 0)
                    HStack(spacing: 2) {
                        ForEach(BrowserTabOverviewMode.allCases) { candidate in
                            Button {
                                onModeChange(candidate)
                            } label: {
                                Image(systemName: candidate.systemImage)
                                    .frame(width: 44, height: 44)
                                    .background(
                                        mode == candidate ? Color(uiColor: .systemBackground) : .clear,
                                        in: Capsule()
                                    )
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(candidate.title)
                            .accessibilityValue(mode == candidate ? "Ausgewählt" : "")
                        }
                    }
                    .padding(2)
                    .background(.primary.opacity(0.08), in: Capsule())

                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                    }
                    .accessibilityLabel("Tab-Übersicht schließen")
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)

                overviewContent(in: geometry.size)
                    .id(mode)
            }
        }
        .background(.regularMaterial)
        .onAppear {
            centeredTabId = selectedTabId
        }
        .onChange(of: selectedTabId) {
            centeredTabId = selectedTabId
        }
    }

    private var selectedTabId: String? {
        tabs.first(where: \.isSelected)?.id
    }

    @ViewBuilder
    private func overviewContent(in viewport: CGSize) -> some View {
        switch mode {
        case .hero:
            heroOverview(in: viewport)
        case .grid:
            gridOverview(in: viewport)
        case .list:
            listOverview
        }
    }

    private func heroOverview(in viewport: CGSize) -> some View {
        let cardSize = heroCardSize(in: viewport)
        return ScrollView(.vertical) {
            ScrollView(.horizontal) {
                LazyHStack(spacing: 12) {
                    ForEach(tabs) { tab in
                        TabHeroCard(
                            tab: tab,
                            size: cardSize,
                            onSelect: { onSelect(tab.id) },
                            onClose: { onClose(tab.id) }
                        )
                        .id(tab.id)
                        .scrollTransition(.interactive, axis: .horizontal) { content, phase in
                            content
                                .scaleEffect(phase.isIdentity ? 1 : 0.94)
                                .opacity(phase.isIdentity ? 1 : 0.82)
                        }
                    }
                }
                .scrollTargetLayout()
            }
            .frame(height: cardSize.height)
            .contentMargins(
                .horizontal,
                max((viewport.width - cardSize.width) / 2, 18),
                for: .scrollContent
            )
            .scrollIndicators(.hidden)
            .scrollTargetBehavior(.viewAligned(limitBehavior: .always))
            .scrollPosition(id: $centeredTabId, anchor: .center)
        }
        .scrollIndicators(.hidden)
    }

    private func gridOverview(in viewport: CGSize) -> some View {
        let layout = BrowserTabOverviewLayoutRules.shared.grid(
            viewportWidth: Float(viewport.width),
            viewportHeight: Float(viewport.height)
        )
        let spacing = CGFloat(layout.itemSpacing)
        let width = CGFloat(layout.cardWidth)
        let columns = Array(
            repeating: GridItem(.fixed(width), spacing: spacing),
            count: Int(layout.columnCount)
        )
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVGrid(columns: columns, spacing: spacing) {
                    ForEach(tabs) { tab in
                        TabGridCard(
                            tab: tab,
                            width: width,
                            previewAspectRatio: CGFloat(layout.previewAspectRatio),
                            onSelect: { onSelect(tab.id) },
                            onClose: { onClose(tab.id) }
                        )
                        .id(tab.id)
                    }
                }
                .padding(.horizontal, CGFloat(layout.contentPadding))
                .padding(.vertical, 8)
            }
            .scrollIndicators(.hidden)
            .onAppear {
                proxy.scrollTo(selectedTabId, anchor: .center)
            }
        }
    }

    private var listOverview: some View {
        let layout = BrowserTabOverviewLayoutRules.shared.list()
        return ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: CGFloat(layout.itemSpacing)) {
                    ForEach(tabs) { tab in
                        TabListRow(
                            tab: tab,
                            rowHeight: CGFloat(layout.rowHeight),
                            cornerRadius: CGFloat(layout.cornerRadius),
                            onSelect: { onSelect(tab.id) },
                            onClose: { onClose(tab.id) }
                        )
                        .id(tab.id)
                    }
                }
                .padding(.horizontal, CGFloat(layout.horizontalPadding))
                .padding(.vertical, 8)
            }
            .scrollIndicators(.hidden)
            .onAppear {
                proxy.scrollTo(selectedTabId, anchor: .center)
            }
        }
    }

    private func heroCardSize(in viewport: CGSize) -> CGSize {
        let layout = BrowserTabOverviewLayoutRules.shared.heroCard(
            viewportWidth: Float(viewport.width),
            viewportHeight: Float(viewport.height)
        )
        let width = CGFloat(layout.width)
        return CGSize(width: width, height: width / CGFloat(layout.aspectRatio))
    }
}

private struct TabHeroCard: View {
    let tab: BrowserTabCard
    let size: CGSize
    let onSelect: () -> Void
    let onClose: () -> Void

    private let cardShape = RoundedRectangle(cornerRadius: 28, style: .continuous)

    var body: some View {
        ZStack(alignment: .top) {
            Button(action: onSelect) {
                TabPreviewArtwork(tab: tab, cornerRadius: 28)
                    .frame(width: size.width, height: size.height)
                    .contentShape(cardShape)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(accessibilityLabel)
            .accessibilityValue(tab.isSelected ? "Ausgewählt" : "")

            HStack(spacing: 10) {
                Image(systemName: tab.address.isEmpty ? "sparkles" : "globe")
                    .font(.caption.weight(.bold))
                    .frame(width: 28, height: 28)
                    .background(.tint.opacity(0.16), in: RoundedRectangle(cornerRadius: 8))

                TabStateBadges(tab: tab)

                VStack(alignment: .leading, spacing: 2) {
                    Text(tabDisplayTitle(tab))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(tabDisplayAddress(tab))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if !tab.isPinned {
                    Button(role: .destructive, action: onClose) {
                        Image(systemName: "xmark")
                            .font(.caption.weight(.bold))
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Tab schließen")
                }
            }
            .padding(8)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .padding(12)
        }
        .frame(width: size.width, height: size.height)
        .clipShape(cardShape)
        .overlay {
            cardShape.stroke(
                tab.isSelected ? Color.accentColor : Color.primary.opacity(0.12),
                lineWidth: tab.isSelected ? 2 : 0.5
            )
        }
        .shadow(color: .black.opacity(0.16), radius: 18, y: 10)
    }

    private var accessibilityLabel: String {
        "\(tabDisplayTitle(tab)), \(tabDisplayAddress(tab))"
    }
}

private struct TabGridCard: View {
    let tab: BrowserTabCard
    let width: CGFloat
    let previewAspectRatio: CGFloat
    let onSelect: () -> Void
    let onClose: () -> Void

    private let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)

    var body: some View {
        ZStack(alignment: .top) {
            Button(action: onSelect) {
                TabPreviewArtwork(tab: tab, cornerRadius: 22)
                    .frame(width: width, height: width / previewAspectRatio)
                    .contentShape(shape)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(tabDisplayTitle(tab)), \(tabDisplayAddress(tab))")
            .accessibilityValue(tab.isSelected ? "Ausgewählt" : "")

            HStack(spacing: 6) {
                Image(systemName: tab.address.isEmpty ? "sparkles" : "globe")
                    .font(.caption2.weight(.bold))
                    .frame(width: 22, height: 22)
                    .background(.tint.opacity(0.16), in: RoundedRectangle(cornerRadius: 7))
                TabStateBadges(tab: tab)
                VStack(alignment: .leading, spacing: 0) {
                    Text(tabDisplayTitle(tab))
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                    Text(tabDisplayAddress(tab))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !tab.isPinned {
                    Button(role: .destructive, action: onClose) {
                        Image(systemName: "xmark")
                            .font(.caption2.bold())
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Tab schließen")
                }
            }
            .padding(6)
            .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(8)
        }
        .frame(width: width, height: width / previewAspectRatio)
        .clipShape(shape)
        .overlay {
            shape.stroke(
                tab.isSelected ? Color.accentColor : Color.primary.opacity(0.12),
                lineWidth: tab.isSelected ? 2 : 0.5
            )
        }
    }
}

private struct TabListRow: View {
    let tab: BrowserTabCard
    let rowHeight: CGFloat
    let cornerRadius: CGFloat
    let onSelect: () -> Void
    let onClose: () -> Void

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
    }

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onSelect) {
                HStack(spacing: 12) {
                    TabPreviewArtwork(
                        tab: tab,
                        cornerRadius: 12,
                        showsFallbackTitle: false
                    )
                        .frame(width: 48, height: 48)
                    TabStateBadges(tab: tab)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(tabDisplayTitle(tab))
                            .font(.subheadline.weight(tab.isSelected ? .bold : .semibold))
                            .lineLimit(1)
                        Text(tabDisplayAddress(tab))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(tabDisplayTitle(tab)), \(tabDisplayAddress(tab))")
            .accessibilityValue(tab.isSelected ? "Ausgewählt" : "")

            if !tab.isPinned {
                Button(role: .destructive, action: onClose) {
                    Image(systemName: "xmark")
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Tab schließen")
            }
        }
        .padding(.leading, 8)
        .padding(.trailing, 4)
        .frame(height: rowHeight)
        .background(
            tab.isSelected ? Color.accentColor.opacity(0.14) : Color.primary.opacity(0.06),
            in: shape
        )
        .overlay {
            shape.stroke(
                tab.isSelected ? Color.accentColor : Color.primary.opacity(0.1),
                lineWidth: tab.isSelected ? 2 : 0.5
            )
        }
        .accessibilityElement(children: .contain)
    }
}

private struct TabStateBadges: View {
    let tab: BrowserTabCard

    var body: some View {
        HStack(spacing: 2) {
            if tab.isPinned {
                Image(systemName: "pin.fill")
                    .accessibilityLabel("Angeheftet")
            }
            if tab.isFavorite {
                Image(systemName: "star.fill")
                    .accessibilityLabel("Favorit")
            }
        }
        .font(.caption2)
        .foregroundStyle(.tint)
    }
}

private struct TabPreviewArtwork: View {
    let tab: BrowserTabCard
    let cornerRadius: CGFloat
    var showsFallbackTitle = true

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
    }

    @ViewBuilder
    var body: some View {
        if let image = tab.preview {
            TabSnapshotImage(image: image)
                .overlay(alignment: .top) {
                    LinearGradient(
                        colors: [.black.opacity(0.18), .clear],
                        startPoint: .top,
                        endPoint: .center
                    )
                }
                .clipShape(shape)
        } else if tab.address.isEmpty {
            fallbackArtwork(
                symbol: "globe.europe.africa.fill",
                title: "Candy"
            )
        } else {
            fallbackArtwork(
                symbol: "arrow.clockwise",
                title: "Vorschau wird geladen"
            )
        }
    }

    private func fallbackArtwork(symbol: String, title: String) -> some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color.accentColor.opacity(0.24),
                    .purple.opacity(0.12),
                    Color(uiColor: .systemBackground),
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            VStack(spacing: 8) {
                Image(systemName: symbol)
                    .font(.title.weight(.light))
                    .foregroundStyle(.tint)
                if showsFallbackTitle {
                    Text(title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(8)
        }
        .clipShape(shape)
    }
}

private struct TabSnapshotImage: View {
    let image: UIImage

    var body: some View {
        GeometryReader { geometry in
            let sourceWidth = max(image.size.width, 1)
            let sourceHeight = max(image.size.height, 1)
            let crop = BrowserTabOverviewLayoutRules.shared.previewCrop(
                rootWidth: Float(sourceWidth),
                rootHeight: Float(sourceHeight),
                targetWidth: Float(geometry.size.width),
                targetHeight: Float(geometry.size.height),
                cropTopFraction: 0.25
            )
            let scale = geometry.size.width / sourceWidth

            Image(uiImage: image)
                .resizable()
                .frame(
                    width: geometry.size.width,
                    height: sourceHeight * scale,
                    alignment: .top
                )
                .offset(y: -CGFloat(crop.sourceTop) * scale)
        }
        .clipped()
        .accessibilityHidden(true)
    }
}

private func tabDisplayTitle(_ tab: BrowserTabCard) -> String {
    tab.title.isEmpty ? "Neuer Tab" : tab.title
}

private func tabDisplayAddress(_ tab: BrowserTabCard) -> String {
    tab.address.isEmpty ? "Adresse eingeben" : tab.address
}

struct CandyBrowserChrome: ViewModifier {
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
