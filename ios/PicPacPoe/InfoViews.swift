import PicPacCore
import PicPacPresentation
import SwiftUI

struct InfoPage<Content: View>: View {
    let title: String
    var subtitle: String? = nil
    let coordinator: GameCoordinator
    @ViewBuilder let content: () -> Content
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                FormBackHeader(title: title, subtitle: subtitle, largeTitle: true) { Task { await coordinator.goHome() } }
                    .padding(.bottom, 28)
                content()
            }.padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 24)
                .frame(maxWidth: 660).frame(maxWidth: .infinity)
        }
        #if DEBUG
        .defaultScrollAnchor(DebugLaunchConfiguration.scrollAnchor)
        #endif
        .clipped()
    }
}

struct HowToPlayView: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.dynamicTypeSize) private var typeSize
    let coordinator: GameCoordinator
    var body: some View {
        InfoPage(title: "How to play", coordinator: coordinator) {
            heading("Pic-Pac")
            Text("There are five Xs and five Os in the bag.").foregroundStyle(colors.secondary).padding(.top, 6)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(["Draw one.", "See what you got.", "Put it in any empty square.", "Make three Xs or three Os in a row."].enumerated()), id: \.offset) { index, text in
                    HStack(alignment: .top, spacing: 0) {
                        Text("\(index + 1).").fontWeight(.bold).monospacedDigit()
                            .foregroundStyle(colors.secondary)
                            .fixedSize(horizontal: true, vertical: true)
                            .padding(.trailing, 6).frame(minWidth: 34, alignment: .leading)
                        Text(text).frame(maxWidth: .infinity, alignment: .leading)
                    }.padding(.vertical, 8).accessibilityElement(children: .combine)
                        .accessibilityIdentifier("tutorial-step-\(index + 1)")
                }
            }.padding(.top, 16)
            heading("You're not X. You're not O.").padding(.top, 24)
            Text("Either player can place either symbol. You win when the piece you place finishes the line.").padding(.top, 8)
            VStack(alignment: .leading, spacing: 0) {
                Text("After drawing X").font(.system(.body, weight: .semibold))
                Text("Next draw · 9 pieces in the bag").font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 4)
                let layout = typeSize >= .xxxLarge ? AnyLayout(VStackLayout(alignment: .leading, spacing: 16)) : AnyLayout(HStackLayout(spacing: 16))
                layout { exampleOdds(.x, count: 4, percent: 44); exampleOdds(.o, count: 5, percent: 56) }.padding(.top, 16)
            }.padding(16).modifier(FormSurface()).padding(.top, 22)
            Text("The odds change as pieces leave the bag. Tapping faster doesn't change the draw.")
                .font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 12)
            Divider().overlay(colors.subtle).padding(.top, 28).padding(.bottom, 24)
            heading("Classic")
            Text("Regular tic-tac-toe. Player 1 is X; Player 2 is O.").foregroundStyle(colors.secondary).padding(.top, 8)
        }.accessibilityIdentifier("how-to-screen")
    }
    private func heading(_ text: String) -> some View { Text(text).font(.system(.title3, weight: .bold)).accessibilityAddTraits(.isHeader) }
    private func exampleOdds(_ symbol: Symbol, count: Int, percent: Int) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) { FormPiece(symbol: symbol).frame(width: 28, height: 28); Text("\(symbol.rawValue) ×\(count)").font(.system(.body, weight: .semibold)) }
            Text("\(percent)%").font(.system(.title, weight: .heavy)).foregroundStyle(colors.symbol(symbol)).monospacedDigit()
        }.frame(maxWidth: .infinity, alignment: .leading).accessibilityElement(children: .ignore)
            .accessibilityLabel("\(symbol.rawValue), \(count) remaining, \(percent) percent next draw")
    }
}

struct SettingsView: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.dynamicTypeSize) private var typeSize
    let coordinator: GameCoordinator
    var body: some View {
        InfoPage(title: "Settings", coordinator: coordinator) {
            heading("Feedback")
            setting("Sound", detail: "Small tones for draws, moves, and results.", key: \.soundEnabled, id: "setting-sound")
            Divider().overlay(colors.subtle)
            setting("Haptics", detail: "Vibrate on draws, moves, and results.", key: \.hapticsEnabled, id: "setting-haptics")
            Divider().overlay(colors.subtle)
            setting("Reduced motion", detail: "Keep every turn readable without extra movement.", key: \.reducedMotion, id: "setting-motion")
            heading("Theme").padding(.top, 28)
            let layout = typeSize >= .xxxLarge ? AnyLayout(VStackLayout(spacing: 8)) : AnyLayout(HStackLayout(spacing: 8))
            layout {
                ForEach(ThemePreference.allCases, id: \.self) { theme in
                    FormChoice(title: theme.rawValue.capitalized, selected: coordinator.settings.theme == theme) {
                        var settings = coordinator.settings; settings.theme = theme
                        Task { await coordinator.updateSettings(settings) }
                    }.accessibilityIdentifier("theme-\(theme.rawValue)")
                }
            }.padding(.top, 12)
            Divider().overlay(colors.subtle).padding(.top, 28)
            Text("Game data stays on this device. No account, ads, analytics, or networked play.")
                .font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 20)
            Link(destination: URL(string: "https://saipranith.dev/picpacpoe/privacy")!) {
                Text("Privacy policy")
                    .font(.subheadline.weight(.semibold)).foregroundStyle(colors.action)
                    .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
            }
                .padding(.top, 8)
                .accessibilityIdentifier("privacy-policy-link")
        }.accessibilityIdentifier("settings-screen")
    }
    private func heading(_ text: String) -> some View { Text(text).font(.system(.body, weight: .semibold)).foregroundStyle(colors.secondary).accessibilityAddTraits(.isHeader) }
    private func setting(_ title: String, detail: String, key: WritableKeyPath<AppSettings, Bool>, id: String) -> some View {
        Toggle(isOn: Binding(get: { coordinator.settings[keyPath: key] }, set: { value in
            var settings = coordinator.settings; settings[keyPath: key] = value
            Task { await coordinator.updateSettings(settings) }
        })) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(.title3, weight: .bold))
                Text(detail).font(.subheadline).foregroundStyle(colors.secondary)
            }.fixedSize(horizontal: false, vertical: true)
        }.tint(colors.action).padding(.vertical, 16).frame(minHeight: 64)
            .accessibilityIdentifier(id)
    }
}

struct AILabView: View {
    @Environment(\.formPalette) private var colors
    let coordinator: GameCoordinator
    var body: some View {
        InfoPage(title: "AI Lab", subtitle: "The other opponents live here.", coordinator: coordinator) {
            Text("Each opponent sees the board, the piece in hand, and the bag counts. None can peek at the next draw.").foregroundStyle(colors.secondary)
            heading("How they play").padding(.top, 24)
            algorithm("Random", "Chooses any legal square.")
            algorithm("Heuristic", "Checks wins, threats, and the changing bag.")
            algorithm("Expectiminimax", "Medium searches four plies. Hard solves the full game.")
            heading("Try an experiment").padding(.top, 20)
            algorithm("MCTS", "Samples decisions and draws on a fixed budget.")
            Button("Play MCTS") { Task { await coordinator.startPicPacAI(difficulty: .mcts) } }
                .buttonStyle(FormButtonStyle()).padding(.top, 8).accessibilityIdentifier("play-mcts")
            Text("2,000 simulations per move.").font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 8)
            algorithm("Q-learning", "A small table learned from seeded self-play.").padding(.top, 18)
            Button("Play Q-learning") { Task { await coordinator.startPicPacAI(difficulty: .qLearning) } }
                .buttonStyle(FormButtonStyle(primary: false)).padding(.top, 8).accessibilityIdentifier("play-qlearning")
            Text("92.3% agreement with exact play on the fixed 1,000-state sample.").font(.subheadline).foregroundStyle(colors.secondary).padding(.top, 8)
            Divider().overlay(colors.subtle).padding(.top, 24)
            Text("Hard uses exact search because this game is small enough to solve. MCTS and Q-learning are here to compare notes.").padding(.top, 20)
        }.accessibilityIdentifier("ai-lab-screen")
    }
    private func heading(_ text: String) -> some View { Text(text).font(.system(.body, weight: .semibold)).foregroundStyle(colors.secondary).accessibilityAddTraits(.isHeader) }
    private func algorithm(_ title: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.system(.title3, weight: .bold)).accessibilityAddTraits(.isHeader)
            Text(detail).font(.subheadline).foregroundStyle(colors.secondary)
        }.padding(.top, 16).padding(.bottom, 4)
    }
}
