import PicPacCore
import SwiftUI

/// Opaque sRGB roles from the canonical Form Playground 2.0 contract.
struct FormPalette: Equatable {
    let dark: Bool
    func color(_ darkValue: UInt32, _ lightValue: UInt32) -> Color {
        let value = dark ? darkValue : lightValue
        return Color(.sRGB, red: Double((value >> 16) & 255) / 255,
                     green: Double((value >> 8) & 255) / 255, blue: Double(value & 255) / 255, opacity: 1)
    }
    var canvas: Color { color(0x251F1C, 0xFBF1DE) }
    var surface: Color { color(0x47382F, 0xFFF9EC) }
    var raised: Color { color(0x503E33, 0xFFFCF5) }
    var recess: Color { color(0x342923, 0xEADDC7) }
    var text: Color { color(0xFFF2DB, 0x34291F) }
    var secondary: Color { color(0xDDC5B5, 0x695543) }
    var border: Color { color(0xB59B88, 0x8D755F) }
    var subtle: Color { color(0x796455, 0xC2AF95) }
    var highlight: Color { color(0xFFE5C6, 0xFFFDF7) }
    var shadow: Color { color(0x17120F, 0x9B8872) }
    var x: Color { color(0xFFAC8F, 0x983A24) }
    var xHighlight: Color { color(0xFFD5BC, 0xB44C32) }
    var xEdge: Color { color(0xBB775F, 0x6F2B1C) }
    var o: Color { color(0xBFD680, 0x4C6819) }
    var oHighlight: Color { color(0xDDEBB1, 0x638033) }
    var oEdge: Color { color(0x819748, 0x344C0C) }
    var playerOne: Color { color(0x7ADBD1, 0x00645D) }
    var playerTwo: Color { color(0xBEB9EF, 0x5B4B97) }
    var focus: Color { text }
    var action: Color { x }
    var onAction: Color { color(0x382218, 0xFFF9EC) }
    func symbol(_ symbol: Symbol) -> Color { symbol == .x ? x : o }
    func player(_ player: Player) -> Color { player == .one ? playerOne : playerTwo }
}

private struct FormPaletteKey: EnvironmentKey {
    static let defaultValue = FormPalette(dark: false)
}
private struct FormReducedMotionKey: EnvironmentKey {
    static let defaultValue = false
}
extension EnvironmentValues {
    var formPalette: FormPalette {
        get { self[FormPaletteKey.self] }
        set { self[FormPaletteKey.self] = newValue }
    }
    var formReducedMotion: Bool {
        get { self[FormReducedMotionKey.self] }
        set { self[FormReducedMotionKey.self] = newValue }
    }
}

enum FormType {
    static func display(_ size: CGFloat = 30, semibold: Bool = false) -> Font {
        .custom(semibold ? "Fredoka-SemiBold" : "Fredoka-Medium", size: size, relativeTo: .title)
    }
}

struct FormSurface: ViewModifier {
    @Environment(\.formPalette) private var colors
    var radius: CGFloat = 18
    func body(content: Content) -> some View {
        content.background(LinearGradient(colors: [colors.raised, colors.surface], startPoint: .top, endPoint: .bottom))
            .clipShape(RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(colors.subtle, lineWidth: 0.75))
    }
}

struct FormButtonStyle: ButtonStyle {
    @Environment(\.formPalette) private var colors
    @Environment(\.formReducedMotion) private var reduced
    @Environment(\.isEnabled) private var enabled
    var primary = true
    func makeBody(configuration: Configuration) -> some View {
        let face = enabled ? (primary ? colors.action : colors.surface) : colors.recess
        configuration.label
            .font(.system(.body, weight: .semibold))
            .multilineTextAlignment(.center)
            .foregroundStyle(enabled ? (primary ? colors.onAction : colors.text) : colors.secondary)
            .padding(.horizontal, 20).padding(.vertical, 14)
            .frame(maxWidth: .infinity, minHeight: 52)
            .background(face.overlay(colors.shadow.opacity(configuration.isPressed ? 0.08 : 0)))
            .clipShape(RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(primary ? face : colors.border, lineWidth: 0.75))
            .offset(y: configuration.isPressed && !reduced ? 2 : 0)
            .background(RoundedRectangle(cornerRadius: 14).fill(enabled ? (primary ? colors.xEdge : colors.shadow) : colors.recess).offset(y: 3))
            .contentShape(RoundedRectangle(cornerRadius: 14))
            .animation(reduced ? nil : .timingCurve(0.2, 0.8, 0.2, 1, duration: 0.09), value: configuration.isPressed)
    }
}

struct FormQuietButtonStyle: ButtonStyle {
    @Environment(\.formPalette) private var colors
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(colors.secondary)
            .padding(.horizontal, 10).padding(.vertical, 12)
            .frame(minHeight: 48)
            .background(configuration.isPressed ? colors.surface : .clear, in: RoundedRectangle(cornerRadius: 10))
            .contentShape(Rectangle())
    }
}

struct FormChoice: View {
    @Environment(\.formPalette) private var colors
    let title: String
    let selected: Bool
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 5) {
                if selected { Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).accessibilityHidden(true) }
                Text(title).font(.system(.subheadline, weight: .bold)).fixedSize(horizontal: false, vertical: true)
            }
            .padding(.horizontal, 8).padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .foregroundStyle(selected ? colors.canvas : colors.text)
            .background(selected ? colors.text : colors.recess, in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(colors.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct FormBackHeader: View {
    @Environment(\.formPalette) private var colors
    @ScaledMetric(relativeTo: .title) private var largeSize = 30.0
    @ScaledMetric(relativeTo: .title3) private var smallSize = 20.0
    let title: String
    var subtitle: String? = nil
    var largeTitle = false
    let onBack: () -> Void
    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.backward").font(.system(size: 20, weight: .medium))
                    .frame(width: 48, height: 48).foregroundStyle(colors.text)
                    .background(colors.surface, in: RoundedRectangle(cornerRadius: 14))
                    .overlay(RoundedRectangle(cornerRadius: 14).strokeBorder(colors.subtle, lineWidth: 0.75))
            }.buttonStyle(.plain).accessibilityLabel("Back").accessibilityIdentifier("back")
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.system(size: largeTitle ? largeSize : smallSize, weight: largeTitle ? .heavy : .bold)).accessibilityAddTraits(.isHeader)
                if let subtitle { Text(subtitle).font(.subheadline).foregroundStyle(colors.secondary) }
            }
            Spacer(minLength: 0)
        }
    }
}
