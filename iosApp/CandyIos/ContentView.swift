import SwiftUI

struct CandyBrowserChrome: ViewModifier {
    let cornerRadius: CGFloat
    let interactive: Bool

    init(cornerRadius: CGFloat = 24, interactive: Bool = true) {
        self.cornerRadius = cornerRadius
        self.interactive = interactive
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
    }

    @ViewBuilder
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(interactive), in: shape)
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
