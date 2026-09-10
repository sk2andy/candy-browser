import CandyShared
import SwiftUI
import UIKit

@MainActor
struct CandyNativeBrowserChrome: View {
    @ObservedObject var browser: BrowserViewModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @FocusState private var isAddressFocused: Bool
    @Namespace private var glassNamespace
    @State private var addressDraft = ""
    @State private var isMenuPresented = false
    @State private var rainbowRotation = 0.0

    private let horizontalInset: CGFloat = 14
    private let chromeCornerRadius: CGFloat = 20
    private let menuCornerRadius: CGFloat = 30

    var body: some View {
        if isVisible {
            ZStack(alignment: .bottom) {
                if isMenuPresented {
                    Color.black.opacity(reduceTransparency ? 0.2 : 0.12)
                        .ignoresSafeArea()
                        .contentShape(Rectangle())
                        .onTapGesture(perform: dismissMenu)
                        .transition(.opacity)
                }

                VStack(spacing: 0) {
                    Spacer(minLength: 16)
                    liquidGlassChrome
                        .padding(.horizontal, horizontalInset)
                        .padding(.bottom, 6)
                }
            }
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .onAppear(perform: synchronizeAddressDraft)
            .onChange(of: browser.address) { _, _ in
                guard !isAddressFocused else { return }
                synchronizeAddressDraft()
            }
            .onChange(of: browser.addressFocusRequest) { _, _ in
                addressDraft = browser.address
                isAddressFocused = true
            }
            .onChange(of: browser.isLoading) { _, isLoading in
                guard isLoading else { return }
                startRainbowAnimation()
            }
            .onDisappear {
                isMenuPresented = false
                isAddressFocused = false
            }
        }
    }

    private var isVisible: Bool {
        !browser.isTabOverviewVisible &&
            !browser.isSettingsVisible &&
            !browser.isFavoritesVisible &&
            browser.readerSnapshot == nil &&
            browser.candyTrailTabId == nil
    }

    @ViewBuilder
    private var liquidGlassChrome: some View {
        if #available(iOS 26.0, *) {
            ZStack(alignment: .bottom) {
                chromeShape
                    .fill(
                        Color(uiColor: .systemBackground)
                            .opacity(isMenuPresented ? 0.14 : 0.08)
                    )
                    .allowsHitTesting(false)

                NativeLiquidGlassBackdrop(
                    reduceTransparency: reduceTransparency,
                    tintOpacity: CGFloat(
                        isMenuPresented ?
                            LiquidGlassPresentationRules.menuTintOpacity :
                            LiquidGlassPresentationRules.addressTintOpacity
                    )
                )
                    .mask {
                        chromeShape.fill(
                            LinearGradient(
                                stops: [
                                    .init(color: .clear, location: 0),
                                    .init(
                                        color: .white.opacity(0.35),
                                        location: LiquidGlassPresentationRules.edgeSofteningLocation
                                    ),
                                    .init(
                                        color: .white,
                                        location: LiquidGlassPresentationRules.edgeClearLocation
                                    ),
                                    .init(
                                        color: .white,
                                        location: 1 - LiquidGlassPresentationRules.edgeClearLocation
                                    ),
                                    .init(
                                        color: .white.opacity(0.35),
                                        location: 1 - LiquidGlassPresentationRules.edgeSofteningLocation
                                    ),
                                    .init(color: .clear, location: 1),
                                ],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                    }
                    .allowsHitTesting(false)

                chromeShape
                    .stroke(
                        LinearGradient(
                            colors: [.white.opacity(0.42), .white.opacity(0.08)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 0.5
                    )
                    .allowsHitTesting(false)

                Group {
                    if isMenuPresented {
                        menuPanel
                            .transition(
                                .opacity.combined(
                                    with: .scale(scale: 0.975, anchor: .bottomTrailing)
                                )
                            )
                    } else {
                        addressBar
                            .transition(
                                .opacity.combined(
                                    with: .scale(scale: 0.975, anchor: .bottomTrailing)
                                )
                            )
                    }
                }
            }
            .frame(height: isMenuPresented ? menuHeight : 56)
            .frame(maxWidth: 390)
        } else if isMenuPresented {
            menuPanel
                .modifier(CandyBrowserChrome(cornerRadius: menuCornerRadius))
                .matchedGeometryEffect(id: "candy-browser-chrome", in: glassNamespace)
        } else {
            addressBar
                .modifier(CandyBrowserChrome(cornerRadius: chromeCornerRadius))
                .matchedGeometryEffect(id: "candy-browser-chrome", in: glassNamespace)
        }
    }

    private var addressBar: some View {
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
        .simultaneousGesture(addressDragGesture)
        .accessibilityElement(children: .contain)
    }

    private var addressField: some View {
        HStack(spacing: 7) {
            Image(systemName: isAddressFocused ? "magnifyingglass" : "lock.fill")
                .font(.system(size: isAddressFocused ? 15 : 11, weight: .semibold))
                .foregroundStyle(.secondary)

            TextField("Adresse oder Suche", text: $addressDraft)
                .focused($isAddressFocused)
                .font(.system(size: 16, weight: .medium, design: .rounded))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .keyboardType(.URL)
                .submitLabel(.go)
                .onSubmit(performNavigation)
                .onChange(of: isAddressFocused) { _, focused in
                    if focused {
                        addressDraft = browser.address
                    } else {
                        synchronizeAddressDraft()
                    }
                }
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

    private var menuHeight: CGFloat {
        min(UIScreen.main.bounds.height - 180, 650)
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
            .frame(width: 40, height: 44)
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

    private var addressDragGesture: some Gesture {
        DragGesture(minimumDistance: 12)
            .onEnded { value in
                let predictedDelta = value.predictedEndTranslation.width - value.translation.width
                browser.handleAddressSwipe(
                    horizontal: value.translation.width,
                    vertical: value.translation.height,
                    velocityX: predictedDelta * 10,
                    viewportWidth: max(UIScreen.main.bounds.width - horizontalInset * 2, 1),
                    isAddressEditing: isAddressFocused
                )
                browser.handleChromeDrag(
                    horizontal: value.translation.width,
                    vertical: value.translation.height,
                    isAddressEditing: isAddressFocused
                )
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
        withChromeAnimation { isMenuPresented = true }
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
            withAnimation(.spring(response: 0.46, dampingFraction: 0.78), changes)
        }
    }

    private func startRainbowAnimation() {
        guard !reduceMotion else {
            rainbowRotation = 25
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

@available(iOS 26.0, *)
private struct NativeLiquidGlassBackdrop: UIViewRepresentable {
    let reduceTransparency: Bool
    let tintOpacity: CGFloat

    func makeUIView(context: Context) -> UIVisualEffectView {
        let effect = UIGlassEffect(style: reduceTransparency ? .regular : .clear)
        effect.isInteractive = true
        effect.tintColor = reduceTransparency ?
            .secondarySystemBackground :
            UIColor.systemBackground.withAlphaComponent(tintOpacity)
        return UIVisualEffectView(effect: effect)
    }

    func updateUIView(_ view: UIVisualEffectView, context: Context) {
        let effect = UIGlassEffect(style: reduceTransparency ? .regular : .clear)
        effect.isInteractive = true
        effect.tintColor = reduceTransparency ?
            .secondarySystemBackground :
            UIColor.systemBackground.withAlphaComponent(tintOpacity)
        view.effect = effect
    }
}

private extension String {
    func removingPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}
