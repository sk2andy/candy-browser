import CandyShared
import SwiftUI
import UIKit

@MainActor
struct CandyNativeBrowserChrome: View {
    @ObservedObject var browser: BrowserViewModel

    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @FocusState private var isAddressFocused: Bool
    @State private var addressDraft = ""
    @State private var isMenuPresented = false
    @State private var rainbowRotation = 0.0
    @GestureState private var parkedTouchActive = false
    @State private var parkedTouchStarted = false
    @State private var parkedInteractionGeneration = 0
    @State private var parkedHoldTask: Task<Void, Never>?
    @State private var parkedHoldRejected = false
    @State private var parkedLatestTranslation = CGSize.zero
    @State private var dockDragOrigin = CGSize.zero
    @State private var dockDragStart: AddressBarDockPosition?
    @State private var dockDragPosition: AddressBarDockPosition?
    @State private var normalAnchorReleased = false
    @State private var normalSnapZoneActive = false
    @State private var anchorResistance = 0.0

    private let horizontalInset: CGFloat = 14
    private let chromeCornerRadius: CGFloat = 20
    private let menuCornerRadius: CGFloat = 30

    var body: some View {
        if isVisible {
            GeometryReader { geometry in
                ZStack(alignment: .bottom) {
                    if isMenuPresented {
                        Color.black.opacity(reduceTransparency ? 0.2 : 0.12)
                            .ignoresSafeArea()
                            .contentShape(Rectangle())
                            .onTapGesture(perform: dismissMenu)
                            .transition(.opacity)
                    }

                    liquidGlassChrome(in: geometry.size)
                        .position(chromeCenter(in: geometry.size))
                }
                .coordinateSpace(name: "candy-native-chrome")
                .animation(chromeAnimation, value: browser.isAddressBarCompact)
                .animation(chromeAnimation, value: browser.addressBarDockPlacement?.edge)
                .animation(chromeAnimation, value: browser.addressBarDockPlacement?.verticalFraction)
                .animation(chromeAnimation, value: isAddressFocused)
                .onChange(of: parkedTouchActive) { _, active in
                    guard !active else { return }
                    let generation = parkedInteractionGeneration
                    // GestureState may reset before onEnded. Let its drop callback finish first;
                    // a new interaction invalidates this cancellation fallback.
                    DispatchQueue.main.async {
                        guard generation == parkedInteractionGeneration,
                              !parkedTouchActive, parkedTouchStarted else { return }
                        cancelParkedInteraction()
                    }
                }
            }
            .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
            .onAppear(perform: synchronizeAddressDraft)
            .onChange(of: browser.address) { _, _ in
                guard !isAddressFocused else { return }
                synchronizeAddressDraft()
            }
            .onChange(of: browser.addressFocusRequest) { _, _ in
                addressDraft = browser.address
                isAddressFocused = true
            }
            .onChange(of: isAddressFocused) { _, focused in
                browser.setAddressBarEditing(focused)
                if focused {
                    addressDraft = browser.address
                } else {
                    synchronizeAddressDraft()
                }
            }
            .onChange(of: reduceMotion) { _, _ in
                guard browser.isLoading else { return }
                startRainbowAnimation()
            }
            .onChange(of: browser.isLoading) { _, isLoading in
                guard isLoading else { return }
                startRainbowAnimation()
            }
            .onDisappear {
                isMenuPresented = false
                isAddressFocused = false
                browser.setAddressBarEditing(false)
                cancelParkedInteraction()
            }
        }
    }

    private var reduceMotion: Bool {
        systemReduceMotion || !browser.appearanceSettings.animationsEnabled
    }

    private var isVisible: Bool {
        !browser.isBrowserMenuVisible &&
            !browser.isTabOverviewVisible &&
            !browser.isSettingsVisible &&
            !browser.isFavoritesVisible &&
            !browser.isHistoryVisible &&
            !browser.isSnoozedTabsVisible &&
            browser.snoozeTab == nil &&
            browser.readerSnapshot == nil &&
            browser.candyTrailTabId == nil
    }

    private var chromeAnimation: Animation? {
        reduceMotion ? nil : .spring(
            response: LiquidGlassPresentationRules.chromeSpringResponse,
            dampingFraction: LiquidGlassPresentationRules.chromeSpringDamping
        )
    }

    private var showsParkedHandle: Bool {
        browser.canParkAddressBar && !isAddressFocused && !isMenuPresented &&
            browser.addressBarDockPlacement != nil
    }

    private var showsCompactBar: Bool {
        browser.canParkAddressBar && !isAddressFocused && !isMenuPresented &&
            !showsParkedHandle && browser.isAddressBarCompact
    }

    private var rememberedDockIsLeft: Bool {
        browser.lastAddressBarDockPlacement.edge == .left
    }

    private func chromeSize(in viewport: CGSize) -> CGSize {
        let availableWidth = max(viewport.width - horizontalInset * 2, 1)
        if showsParkedHandle { return CGSize(width: 52, height: 48) }
        if isMenuPresented {
            return CGSize(width: min(availableWidth, 390), height: min(max(viewport.height - 80, 160), 650))
        }
        if showsCompactBar {
            let textWidth = (displayAddress(browser.address) as NSString).size(
                withAttributes: [.font: UIFont.preferredFont(forTextStyle: .subheadline)]
            ).width
            let width = textWidth + (browser.canParkAddressBar ? 68 : 36)
            return CGSize(width: min(availableWidth, max(152, min(width, 300))), height: 48)
        }
        return CGSize(width: min(availableWidth, 390), height: 56)
    }

    private func dockVerticalTravel(in viewport: CGSize) -> Double {
        max(Double(viewport.height - 48 - 12), 0)
    }

    private func dockOffset(for position: AddressBarDockPosition, in viewport: CGSize) -> AddressBarDockOffset {
        AddressBarDockingBridge.shared.offsetForPosition(
            position: position,
            maxWidth: max(Double(viewport.width - horizontalInset * 2), 0),
            barWidth: 52,
            verticalTravel: dockVerticalTravel(in: viewport)
        )
    }

    private func chromeCenter(in viewport: CGSize) -> CGPoint {
        let size = chromeSize(in: viewport)
        guard showsParkedHandle else {
            return CGPoint(x: viewport.width / 2, y: max(size.height / 2, viewport.height - 6 - size.height / 2))
        }
        let position = dockDragPosition ?? AddressBarDockingBridge.shared.positionForPlacement(
            placement: browser.addressBarDockPlacement
        )
        let offset = dockOffset(for: position, in: viewport)
        return CGPoint(x: viewport.width / 2 + offset.x, y: viewport.height - 30 + offset.y)
    }

    @ViewBuilder
    private func liquidGlassChrome(in viewport: CGSize) -> some View {
        let size = chromeSize(in: viewport)
        Group {
            if reduceTransparency {
                chromeContent(in: viewport)
                    .frame(width: size.width, height: size.height)
                    .background(Color(uiColor: .secondarySystemBackground), in: chromeShape)
            } else if #available(iOS 26.0, *) {
                chromeContent(in: viewport)
                    .frame(width: size.width, height: size.height)
                    .glassEffect(.regular.interactive(), in: chromeShape)
            } else {
                chromeContent(in: viewport)
                    .frame(width: size.width, height: size.height)
                    .modifier(CandyBrowserChrome(cornerRadius: isMenuPresented ? menuCornerRadius : chromeCornerRadius))
            }
        }
        .scaleEffect(
            x: 1 + anchorResistance * 0.06,
            y: 1 + anchorResistance * 0.12,
            anchor: .bottom
        )
    }

    @ViewBuilder
    private func chromeContent(in viewport: CGSize) -> some View {
        if isMenuPresented {
            menuPanel
        } else if showsParkedHandle {
            parkedHandle(in: viewport)
        } else if showsCompactBar {
            compactAddressBar(in: viewport)
        } else {
            addressBar(in: viewport)
        }
    }

    private func compactAddressBar(in viewport: CGSize) -> some View {
        HStack(spacing: 0) {
            if browser.canParkAddressBar && rememberedDockIsLeft { compactParkButton }
            Button {
                withChromeAnimation { browser.restoreAddressBar() }
            } label: {
                Text(displayAddress(browser.address))
                    .font(.subheadline.weight(.medium))
                    .lineLimit(1)
                    .truncationMode(.middle)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Adressleiste einblenden")
            .accessibilityValue(displayAddress(browser.address))
            if browser.canParkAddressBar && !rememberedDockIsLeft { compactParkButton }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 6)
        .contentShape(chromeShape)
        .simultaneousGesture(addressDragGesture(in: viewport))
        .accessibilityIdentifier("address_bar_compact_content")
    }

    private var compactParkButton: some View {
        Button {
            withChromeAnimation { browser.parkAddressBar(onRight: false) }
        } label: {
            Image(systemName: rememberedDockIsLeft ? "chevron.left" : "chevron.right")
                .font(.system(size: 17, weight: .semibold))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Adressleiste am Rand parken")
        .accessibilityHint(rememberedDockIsLeft ? "Am linken Rand" : "Am rechten Rand")
        .accessibilityIdentifier("address_bar_park_action")
    }

    private func parkedHandle(in viewport: CGSize) -> some View {
        let onLeft = (dockDragPosition?.horizontalFraction ??
            AddressBarDockingBridge.shared.positionForPlacement(placement: browser.addressBarDockPlacement).horizontalFraction) < 0
        return Image(systemName: onLeft ? "chevron.right" : "chevron.left")
            .font(.system(size: 20, weight: .semibold))
            .frame(width: 52, height: 48)
            .contentShape(Rectangle())
            .gesture(parkedHandleGesture(in: viewport))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Adressleiste wiederherstellen")
            .accessibilityHint("Tippen zum Einblenden. Halten und ziehen zum Verschieben.")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { restoreParkedAddressBar() }
            .accessibilityAction(named: "Tabs anzeigen") { browser.performTabs(.showoverview) }
            .accessibilityAction(named: "Links parken") { moveParkedHandle(to: .left) }
            .accessibilityAction(named: "Rechts parken") { moveParkedHandle(to: .right) }
            .accessibilityIdentifier("address_bar_edge_tab")
    }

    private func addressBar(in viewport: CGSize) -> some View {
        HStack(spacing: 6) {
            if !isAddressFocused {
                chromeButton(
                    systemName: "square.on.square",
                    label: "Tabs anzeigen",
                    action: { browser.performTabs(.showoverview) }
                ) {
                    Text(browser.tabCountLabel)
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.tint)
                        .minimumScaleFactor(0.7)
                }
            }

            addressField
                .frame(maxWidth: .infinity)

            if isAddressFocused {
                Button("Abbrechen", action: cancelAddressEditing)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.tint)
                    .padding(.horizontal, 6)
                    .frame(minHeight: 44)
                    .accessibilityHint("Schließt die Adresseingabe")
            } else {
                chromeButton(
                    systemName: "plus",
                    label: "Neuer Tab",
                    action: { browser.performTabs(.newtab) }
                )
                .contextMenu {
                    Button("Neuer Tab", systemImage: "plus") {
                        browser.performTabs(.newtab)
                    }
                    Button("Neuer privater Tab", systemImage: "hand.raised") {
                        browser.newPrivateTab()
                    }
                }
                .accessibilityHint("Gedrückt halten für einen privaten Tab")
                chromeButton(
                    systemName: "ellipsis",
                    label: "Mehr Aktionen",
                    action: presentMenu
                )
            }
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(minHeight: 56)
        .overlay { loadingBorder }
        .contentShape(RoundedRectangle(cornerRadius: chromeCornerRadius, style: .continuous))
        .simultaneousGesture(addressDragGesture(in: viewport))
        .accessibilityElement(children: .contain)
    }

    private var selectedTabIsPrivate: Bool {
        browser.tabCards.first(where: { $0.id == browser.selectedTabId })?.isPrivate ?? false
    }

    private var addressField: some View {
        HStack(spacing: 7) {
            Image(systemName: isAddressFocused ? "magnifyingglass" : selectedTabIsPrivate ? "hand.raised.fill" : "globe")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.secondary)
                .accessibilityLabel(selectedTabIsPrivate ? "Privater Tab" : "Website")
                .accessibilityHidden(isAddressFocused)

            TextField("Adresse oder Suche", text: $addressDraft)
                .focused($isAddressFocused)
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .submitLabel(.go)
                .onSubmit(performNavigation)
                .onChange(of: addressDraft) { _, value in
                    guard isAddressFocused, value != browser.address else { return }
                    browser.editAddress(value)
                }

            if isAddressFocused && !addressDraft.isEmpty {
                Button {
                    addressDraft = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.tertiary)
                        .frame(width: 32, height: 40)
                }
                .accessibilityLabel("Eingabe löschen")
            }
        }
        .padding(.leading, 12)
        .padding(.trailing, isAddressFocused ? 4 : 10)
        .frame(minHeight: 44)
        .overlay {
            Capsule()
                .stroke(.white.opacity(reduceTransparency ? 0.2 : 0.12), lineWidth: 0.5)
        }
    }

    @ViewBuilder
    private var loadingBorder: some View {
        if browser.isLoading {
            RoundedRectangle(cornerRadius: chromeCornerRadius, style: .continuous)
                .stroke(
                    AngularGradient(
                        colors: [.pink, .orange, .yellow, .green, .cyan, .blue, .purple, .pink],
                        center: .center,
                        angle: .degrees(rainbowRotation)
                    ),
                    lineWidth: 2
                )
                .padding(0.5)
                .allowsHitTesting(false)
                .onAppear(perform: startRainbowAnimation)
        }
    }

    private var menuPanel: some View {
        VStack(spacing: 0) {
            menuHeader
            Divider().opacity(0.36)
            menuToolbar
                .padding(.horizontal, 10)
                .padding(.vertical, 10)
            Divider().opacity(0.36)
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(menuSections.enumerated()), id: \.offset) { index, section in
                        let items = items(in: section)
                        if !items.isEmpty {
                            menuSection(section, items: items)
                            if index < menuSections.count - 1 {
                                Divider()
                                    .padding(.leading, 52)
                                    .opacity(0.28)
                            }
                        }
                    }
                }
                .padding(.bottom, 8)
            }
            .scrollIndicators(.hidden)
            .frame(maxHeight: 500)
            .transaction { transaction in
                transaction.animation = nil
            }
        }
        .frame(maxWidth: 390)
        .frame(maxHeight: 650)
        .background {
            if reduceTransparency {
                RoundedRectangle(cornerRadius: menuCornerRadius, style: .continuous)
                    .fill(Color(uiColor: .secondarySystemBackground))
            }
        }
        .accessibilityElement(children: .contain)
    }

    private var chromeShape: RoundedRectangle {
        RoundedRectangle(
            cornerRadius: isMenuPresented ? menuCornerRadius : chromeCornerRadius,
            style: .continuous
        )
    }

    private var menuHeader: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Menü")
                    .font(.headline)
                Text(browser.pageTitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Button(action: dismissMenu) {
                Image(systemName: "xmark")
                    .font(.system(size: 14, weight: .bold))
                    .frame(width: 36, height: 36)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Menü schließen")
        }
        .padding(.leading, 18)
        .padding(.trailing, 12)
        .padding(.vertical, 12)
    }

    private var menuToolbar: some View {
        HStack(spacing: 2) {
            ForEach(Array(toolbarItems.enumerated()), id: \.offset) { _, item in
                Button {
                    activate(item)
                } label: {
                    VStack(spacing: 5) {
                        Image(systemName: symbol(for: item))
                            .font(.system(size: 19, weight: .semibold))
                            .frame(height: 23)
                        Text(toolbarLabel(for: item))
                            .font(.caption2)
                            .lineLimit(1)
                            .minimumScaleFactor(0.72)
                    }
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!item.enabled)
                .opacity(item.enabled ? 1 : 0.34)
                .accessibilityLabel(label(for: item))
            }
        }
    }

    private func menuSection(
        _ section: BrowserFeatureMenuSection,
        items: [BrowserFeatureMenuItem]
    ) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title(for: section))
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 18)
                .padding(.top, 13)
                .padding(.bottom, 5)

            ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                if item.kind == .toggle {
                    Toggle(
                        isOn: Binding(
                            get: { checked(item) },
                            set: { _ in activate(item) }
                        )
                    ) {
                        menuRowLabel(item)
                    }
                    .toggleStyle(.switch)
                    .tint(.accentColor)
                    .disabled(!item.enabled)
                    .padding(.leading, 17)
                    .padding(.trailing, 15)
                    .frame(minHeight: 52)
                } else {
                    Button {
                        activate(item)
                    } label: {
                        HStack(spacing: 12) {
                            menuRowLabel(item)
                            Spacer(minLength: 8)
                            if item.kind == .navigation {
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.tertiary)
                            } else if checked(item) {
                                Image(systemName: "checkmark")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(.tint)
                            }
                        }
                        .padding(.horizontal, 17)
                        .frame(minHeight: 52)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(!item.enabled)
                }
                if item.stableId != items.last?.stableId {
                    Divider()
                        .padding(.leading, 52)
                        .opacity(0.22)
                }
            }
        }
        .opacity(items.contains(where: \BrowserFeatureMenuItem.enabled) ? 1 : 0.5)
    }

    private func menuRowLabel(_ item: BrowserFeatureMenuItem) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol(for: item))
                .font(.system(size: 18, weight: .medium))
                .foregroundStyle(color(for: item))
                .frame(width: 23)
            VStack(alignment: .leading, spacing: 2) {
                Text(label(for: item))
                    .font(.body)
                    .foregroundStyle(color(for: item))
                    .lineLimit(2)
                if let supportingText = item.supportingText, !supportingText.isEmpty {
                    Text(supportingText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
        .opacity(item.enabled ? 1 : 0.36)
    }

    private var toolbarItems: [BrowserFeatureMenuItem] {
        browser.menuItems.filter { $0.section == .toolbar }
    }

    private var menuSections: [BrowserFeatureMenuSection] {
        [.page, .toppings, .candy, .browser]
    }

    private func items(in section: BrowserFeatureMenuSection) -> [BrowserFeatureMenuItem] {
        browser.menuItems.filter { $0.section == section }
    }

    private func chromeButton<Content: View>(
        systemName: String,
        label: String,
        action: @escaping () -> Void,
        @ViewBuilder content: () -> Content
    ) -> some View {
        Button(action: action) {
            ZStack {
                Image(systemName: systemName)
                    .font(.system(size: 19, weight: .semibold))
                    .opacity(0)
                content()
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .accessibilityLabel(label)
    }

    private func chromeButton(
        systemName: String,
        label: String,
        action: @escaping () -> Void
    ) -> some View {
        chromeButton(systemName: systemName, label: label, action: action) {
            Image(systemName: systemName)
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(.primary)
        }
    }

    private func addressDragGesture(in viewport: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onEnded { value in
                let predictedDelta = value.predictedEndTranslation.width - value.translation.width
                browser.handleAddressSwipe(
                    horizontal: value.translation.width,
                    vertical: value.translation.height,
                    velocityX: predictedDelta * 10,
                    viewportWidth: max(viewport.width - horizontalInset * 2, 1),
                    isAddressEditing: isAddressFocused
                )
                browser.handleChromeDrag(
                    horizontal: value.translation.width,
                    vertical: value.translation.height,
                    isAddressEditing: isAddressFocused
                )
            }
    }

    private func parkedHandleGesture(in viewport: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("candy-native-chrome"))
            .updating($parkedTouchActive) { _, active, _ in active = true }
            .onChanged { value in
                parkedLatestTranslation = value.translation
                if !parkedTouchStarted {
                    parkedInteractionGeneration += 1
                    parkedTouchStarted = true
                    parkedHoldRejected = false
                    parkedHoldTask = Task { @MainActor in
                        try? await Task.sleep(nanoseconds:
                            UInt64(AddressBarGestureRules.shared.PARKED_REPOSITION_LONG_PRESS_MILLIS) * 1_000_000
                        )
                        guard !Task.isCancelled, !parkedHoldRejected,
                              browser.canParkAddressBar, showsParkedHandle else { return }
                        let start = AddressBarDockingBridge.shared.positionForPlacement(
                            placement: browser.addressBarDockPlacement
                        )
                        dockDragOrigin = parkedLatestTranslation
                        dockDragStart = start
                        dockDragPosition = start
                        normalAnchorReleased = start.verticalFraction != 0
                        normalSnapZoneActive = start.verticalFraction == 0
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    }
                }
                if dockDragStart == nil {
                    if abs(value.translation.width) > 12 || abs(value.translation.height) > 12 {
                        parkedHoldRejected = true
                        parkedHoldTask?.cancel()
                    }
                    return
                }
                updateParkedDrag(translation: value.translation, in: viewport)
            }
            .onEnded { value in
                parkedHoldTask?.cancel()
                if dockDragStart != nil {
                    updateParkedDrag(translation: value.translation, in: viewport)
                }
                if let start = dockDragStart, let position = dockDragPosition {
                    let distance = CGSize(
                        width: value.translation.width - dockDragOrigin.width,
                        height: value.translation.height - dockDragOrigin.height
                    )
                    if abs(distance.width) > 0.01 || abs(distance.height) > 0.01 {
                        let placement = AddressBarDockingBridge.shared.placementAfterDrop(
                            position: position,
                            snapToNormalAnchor: (start.verticalFraction == 0 && !normalAnchorReleased) || normalSnapZoneActive
                        )
                        selectionHaptic()
                        withChromeAnimation {
                            browser.updateAddressBarDockPlacement(placement)
                            resetParkedDrag()
                        }
                    } else {
                        withChromeAnimation { resetParkedDrag() }
                    }
                } else if value.translation.height <= -CGFloat(AddressBarGestureRules.shared.PARKED_OPEN_TABS_THRESHOLD_DP),
                          abs(value.translation.height) > abs(value.translation.width) {
                    cancelParkedInteraction()
                    isAddressFocused = false
                    browser.performTabs(.showoverview)
                } else if abs(value.translation.width) < 12 && abs(value.translation.height) < 12 {
                    restoreParkedAddressBar()
                } else {
                    cancelParkedInteraction()
                }
                parkedTouchStarted = false
            }
    }

    private func updateParkedDrag(translation: CGSize, in viewport: CGSize) {
        guard let start = dockDragStart else { return }
        let dragX = Double(translation.width - dockDragOrigin.width)
        let dragY = Double(translation.height - dockDragOrigin.height)
        if start.verticalFraction == 0 && !normalAnchorReleased &&
            AddressBarDockingBridge.shared.normalAnchorBreakawayReached(dragY: dragY) {
            normalAnchorReleased = true
            selectionHaptic()
        }
        anchorResistance = start.verticalFraction == 0 && !normalAnchorReleased ?
            AddressBarDockingBridge.shared.normalAnchorResistanceProgress(dragY: dragY) : 0
        let edgePosition = AddressBarDockPosition(horizontalFraction: 1, verticalFraction: 0)
        let position = AddressBarDockingBridge.shared.positionAfterDrag(
            startPosition: start,
            dragX: dragX,
            dragY: dragY,
            horizontalTravel: abs(dockOffset(for: edgePosition, in: viewport).x),
            verticalTravel: dockVerticalTravel(in: viewport),
            resistNormalAnchor: start.verticalFraction == 0 && !normalAnchorReleased
        )
        let snap = AddressBarDockingBridge.shared.normalAnchorSnap(
            position: position,
            verticalTravel: dockVerticalTravel(in: viewport),
            enabled: normalAnchorReleased
        )
        if snap.snappedToNormalAnchor && !normalSnapZoneActive { selectionHaptic() }
        normalSnapZoneActive = snap.snappedToNormalAnchor
        dockDragPosition = snap.position
    }

    private func resetParkedDrag() {
        dockDragStart = nil
        dockDragPosition = nil
        anchorResistance = 0
        normalAnchorReleased = false
        normalSnapZoneActive = false
    }

    private func cancelParkedInteraction() {
        parkedInteractionGeneration += 1
        parkedHoldTask?.cancel()
        parkedHoldTask = nil
        parkedTouchStarted = false
        parkedHoldRejected = false
        parkedLatestTranslation = .zero
        withChromeAnimation { resetParkedDrag() }
    }

    private func restoreParkedAddressBar() {
        cancelParkedInteraction()
        selectionHaptic()
        withChromeAnimation { browser.restoreAddressBar() }
    }

    private func moveParkedHandle(to edge: AddressBarDockEdge) {
        cancelParkedInteraction()
        withChromeAnimation {
            browser.updateAddressBarDockPlacement(AddressBarDockPlacement(
                edge: edge,
                verticalFraction: browser.lastAddressBarDockPlacement.verticalFraction
            ))
        }
    }

    private func synchronizeAddressDraft() {
        addressDraft = displayAddress(browser.address)
    }

    private func displayAddress(_ value: String) -> String {
        guard !value.isEmpty else { return "" }
        if let host = URL(string: value)?.host {
            return host.removingPrefix("www.")
        }
        return value
    }

    private func performNavigation() {
        browser.editAddress(addressDraft)
        browser.perform(.navigate)
        isAddressFocused = false
    }

    private func cancelAddressEditing() {
        isAddressFocused = false
        synchronizeAddressDraft()
    }

    private func presentMenu() {
        isAddressFocused = false
        selectionHaptic()
        if browser.menuLayout.presentation == .bottomsheet {
            browser.showBrowserMenu()
        } else {
            withChromeAnimation { isMenuPresented = true }
        }
    }

    private func dismissMenu() {
        withChromeAnimation { isMenuPresented = false }
    }

    private func activate(_ item: BrowserFeatureMenuItem) {
        guard item.enabled else { return }
        selectionHaptic()
        withChromeAnimation { isMenuPresented = false }
        browser.performMenu(item)
    }

    private func withChromeAnimation(_ changes: () -> Void) {
        if reduceMotion {
            changes()
        } else {
            withAnimation(.spring(
                response: LiquidGlassPresentationRules.chromeSpringResponse,
                dampingFraction: LiquidGlassPresentationRules.chromeSpringDamping
            ), changes)
        }
    }

    private func startRainbowAnimation() {
        guard !reduceMotion else {
            var transaction = Transaction(animation: nil)
            transaction.disablesAnimations = true
            withTransaction(transaction) { rainbowRotation = 25 }
            return
        }
        rainbowRotation = 0
        withAnimation(.linear(duration: 1.25).repeatForever(autoreverses: false)) {
            rainbowRotation = 360
        }
    }

    private func selectionHaptic() {
        UISelectionFeedbackGenerator().selectionChanged()
    }

    private func checked(_ item: BrowserFeatureMenuItem) -> Bool {
        item.checked?.boolValue ?? false
    }

    private func toolbarLabel(for item: BrowserFeatureMenuItem) -> String {
        if item.action == .togglefavorite { return "Favorit" }
        if item.action == .togglepinned { return checked(item) ? "Lösen" : "Anheften" }
        return label(for: item)
    }

    private func label(for item: BrowserFeatureMenuItem) -> String {
        if let dynamicLabel = item.dynamicLabel, !dynamicLabel.isEmpty { return dynamicLabel }
        let action = item.action
        if action == .back { return "Zurück" }
        if action == .forward { return "Vor" }
        if action == .reload { return "Neu laden" }
        if action == .stop { return "Laden stoppen" }
        if action == .togglefavorite { return checked(item) ? "Favorit entfernen" : "Favorit hinzufügen" }
        if action == .togglepinned { return checked(item) ? "Tab lösen" : "Tab anheften" }
        if action == .showtabs { return "Tabs" }
        if action == .newtab { return "Neuer Tab" }
        if action == .duplicatetab { return "Tab duplizieren" }
        if action == .closetab { return "Tab schließen" }
        if action == .openreader { return "Lesemodus" }
        if action == .translatepage { return "Übersetzen" }
        if action == .findinpage { return "Auf Seite suchen" }
        if action == .share { return "Teilen" }
        if action == .openexternal { return "Extern öffnen" }
        if action == .print { return "Drucken" }
        if action == .togglecookiebannerremoval { return "Cookie-Banner entfernen" }
        if action == .toggleforceverticalscrolling { return "Vertikales Scrollen erzwingen" }
        if action == .toggleforcepagezooming { return "Seitenzoom erzwingen" }
        if action == .toggleforcesafearea { return "Safe Area erzwingen" }
        if action == .togglealwaysblockpopups { return "Pop-ups immer blockieren" }
        if action == .toggledesktopview { return "Desktop-Website" }
        if action == .toggledomainmute { return checked(item) ? "Domain-Ton aktivieren" : "Domain stummschalten" }
        if action == .opencandytrail { return "Candy Trail" }
        if action == .addsitecapsule { return "Site Capsule hinzufügen" }
        if action == .summarize { return "Zusammenfassen" }
        if action == .snoozetab { return "Tab schlummern" }
        if action == .dockaddressbar { return "Adressleiste andocken" }
        if action == .openfavorites { return "Favoriten" }
        if action == .openhistory { return "Verlauf" }
        if action == .opensnoozedtabs { return "Schlummernde Tabs" }
        if action == .opensettings { return "Einstellungen" }
        if action == .invoketoppingcommand { return item.stableId }
        return item.stableId
    }

    private func symbol(for item: BrowserFeatureMenuItem) -> String {
        let action = item.action
        if action == .back { return "chevron.left" }
        if action == .forward { return "chevron.right" }
        if action == .reload { return "arrow.clockwise" }
        if action == .stop { return "xmark" }
        if action == .togglefavorite { return checked(item) ? "star.fill" : "star" }
        if action == .togglepinned { return checked(item) ? "pin.fill" : "pin" }
        if action == .showtabs { return "square.on.square" }
        if action == .newtab { return "plus" }
        if action == .duplicatetab { return "doc.on.doc" }
        if action == .closetab { return "xmark.circle" }
        if action == .openreader { return "book.closed" }
        if action == .translatepage { return "character.book.closed" }
        if action == .findinpage { return "magnifyingglass" }
        if action == .share { return "square.and.arrow.up" }
        if action == .openexternal { return "arrow.up.right.square" }
        if action == .print { return "printer" }
        if action == .togglecookiebannerremoval { return "hand.raised" }
        if action == .toggleforceverticalscrolling { return "arrow.up.and.down" }
        if action == .toggleforcepagezooming { return "plus.magnifyingglass" }
        if action == .toggleforcesafearea { return "rectangle.inset.filled" }
        if action == .togglealwaysblockpopups { return "macwindow.badge.xmark" }
        if action == .toggledesktopview { return "desktopcomputer" }
        if action == .toggledomainmute { return checked(item) ? "speaker.wave.2" : "speaker.slash" }
        if action == .opencandytrail { return "point.topleft.down.curvedto.point.bottomright.up" }
        if action == .addsitecapsule { return "shippingbox" }
        if action == .summarize { return "text.alignleft" }
        if action == .snoozetab { return "moon.zzz" }
        if action == .dockaddressbar { return "dock.rectangle" }
        if action == .openfavorites { return "star.square" }
        if action == .openhistory { return "clock.arrow.circlepath" }
        if action == .opensnoozedtabs { return "tray.full" }
        if action == .opensettings { return "gearshape" }
        return "wand.and.stars"
    }

    private func color(for item: BrowserFeatureMenuItem) -> Color {
        item.action == .closetab ? .red : .primary
    }

    private func title(for section: BrowserFeatureMenuSection) -> String {
        if section == .page { return "SEITE" }
        if section == .toppings { return "TOPPINGS" }
        if section == .candy { return "CANDY" }
        return "BROWSER"
    }
}

private extension String {
    func removingPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}
