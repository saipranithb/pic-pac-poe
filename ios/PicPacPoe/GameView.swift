import PicPacCore
import PicPacPresentation
import SwiftUI
import UIKit

extension GamePresentationState {
    var winningLine: WinningLine? { if case let .win(win) = outcome { win.lines.first } else { nil } }
    var inputEnabled: Bool { stage == .playing && (mode != .picPacAI || activePlayer == .one) }
    var instructionSymbol: Symbol? {
        if let heldSymbol { return heldSymbol }
        if [.aiPlacing, .aiSettling].contains(stage) { return aiMoveSymbol }
        if mode == .classicLocal && stage == .playing { return activePlayer == .one ? .x : .o }
        return nil
    }
    var instructionTitle: String {
        switch stage {
        case .playing: instructionSymbol.map { "Place \($0.rawValue)" } ?? turnLabel()
        case .aiThinking: "Computer is thinking"
        case .aiTargeting: "Square selected"
        case .aiPlacing: "Placing \(aiMoveSymbol?.rawValue ?? "")"
        case .aiSettling: "Move placed"
        case .terminal: "Game over"
        case .revealing: drawLabel ?? "Drawing a piece"
        case .handoff: "Pass to \(activePlayer.label)"
        case .turnStart: turnLabel()
        }
    }
    var instructionDetail: String {
        switch stage {
        case .playing: "Choose any empty square."
        case .turnStart: "A piece comes from the shared bag."
        case .revealing: "Draw first. Then choose a square."
        case .aiThinking: "Choosing where to place \(instructionSymbol?.rawValue ?? "")."
        case .aiTargeting: aiTargetCell.map { "Computer chose row \($0 / 3 + 1), column \($0 % 3 + 1)." } ?? "Computer has chosen a square."
        case .aiPlacing: "Computer is placing its piece."
        case .aiSettling: "Computer's move is on the board."
        case .terminal: "The final position."
        case .handoff: "Pass the phone before revealing."
        }
    }
}

enum GameSpeech {
    static func announcement(from old: TurnStage, to new: TurnStage, state: GamePresentationState, sceneActive: Bool) -> String? {
        guard old != new, sceneActive, [.aiTargeting, .aiPlacing, .aiThinking, .turnStart].contains(new) else { return nil }
        if [.aiTargeting, .aiPlacing].contains(new), let target = state.aiTargetCell {
            return BoardSemantics.cell(target, symbol: state.board[Cell(target)], target: target,
                                       moveSymbol: state.aiMoveSymbol, stage: new)
        }
        return "\(state.instructionTitle). \(state.instructionDetail)"
    }
}

struct GameView: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.dynamicTypeSize) private var typeSize
    let coordinator: GameCoordinator
    @AccessibilityFocusState private var modalFocus: Bool
    @AccessibilityFocusState private var instructionFocus: Bool
    private var state: GamePresentationState { coordinator.state }
    private var modal: Bool { state.stage == .revealing || state.stage == .terminal }

    var body: some View {
        GeometryReader { viewport in
            if state.stage == .handoff {
                handoff.frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                let wide = viewport.size.width >= 760 && typeSize < .xxxLarge
                ZStack {
                    ScrollView {
                        VStack(spacing: 0) {
                            FormBackHeader(title: title, subtitle: state.mode == .picPacAI ? state.difficulty.title : nil, onBack: home)
                                .accessibilitySortPriority(100)
                            playerHeader.padding(.top, 16).padding(.bottom, 20).accessibilitySortPriority(90)
                            if wide {
                                HStack(alignment: .center, spacing: 28) {
                                    board.frame(maxWidth: .infinity)
                                    VStack(spacing: 20) { instructions; if let pic = state.picPac { ProbabilityTray(remainingX: pic.remainingX, remainingO: pic.remainingO, held: state.heldSymbol != nil) } }.frame(maxWidth: .infinity)
                                }
                            } else {
                                instructions.padding(.bottom, 20)
                                board
                                if let pic = state.picPac { ProbabilityTray(remainingX: pic.remainingX, remainingO: pic.remainingO, held: state.heldSymbol != nil).padding(.top, 24) }
                            }
                        }.padding(.horizontal, wide ? 32 : 20).padding(.top, 12).padding(.bottom, 24)
                            .frame(maxWidth: wide ? 1000 : 500).frame(maxWidth: .infinity)
                    }
                    #if DEBUG
                    .defaultScrollAnchor(DebugLaunchConfiguration.scrollAnchor)
                    #endif
                    .clipped()
                    .accessibilityHidden(modal).allowsHitTesting(!modal)
                    if modal {
                        Color.black.opacity(0.6).ignoresSafeArea().accessibilityHidden(true)
                        ViewThatFits(in: .vertical) {
                            modalContent
                            ScrollView { modalContent }.scrollBounceBehavior(.basedOnSize)
                        }
                        .frame(maxWidth: 400).padding(24)
                        .accessibilityElement(children: .contain)
                        .accessibilityAddTraits(.isModal)
                        .accessibilityAction(.escape, home)
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("game-screen")
        .onChange(of: state.stage, initial: true) { old, new in
            modalFocus = [.revealing, .terminal, .handoff].contains(new)
            // Restoring a presentation must not replay its transition speech.
            guard old != new, coordinator.isSceneActive else { return }
            if new == .playing && (old == .revealing || old == .terminal) { instructionFocus = true }
            if let announcement = GameSpeech.announcement(from: old, to: new, state: state, sceneActive: coordinator.isSceneActive) {
                UIAccessibility.post(notification: .announcement, argument: announcement)
            }
        }
    }

    private var title: String {
        switch state.mode { case .classicLocal: "Classic"; case .picPacLocal: "Pic-Pac Local"; case .picPacAI: "Vs Computer"; case nil: "Pic-Pac-Poe" }
    }
    private func home() { Task { await coordinator.goHome() } }
    private var board: some View {
        PhysicalBoard(board: state.board, enabled: state.inputEnabled, winningLine: state.winningLine, target: state.aiTargetCell,
                      moveSymbol: state.aiMoveSymbol, stage: state.stage) { cell in Task { await coordinator.place(at: cell) } }
            .accessibilityElement(children: .contain)
            .accessibilitySortPriority(70)
    }
    private var playerHeader: some View {
        let layout = typeSize >= .xxxLarge ? AnyLayout(VStackLayout(spacing: 12)) : AnyLayout(HStackLayout(spacing: 20))
        return layout {
            ForEach(Player.allCases, id: \.self) { player in
                let active = state.displayedPlayer == player
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 8) {
                        Circle().fill(colors.player(player)).frame(width: 7, height: 7).accessibilityHidden(true)
                        Text(state.actorLabel(for: player)).font(.system(.body, weight: .semibold)).foregroundStyle(active ? colors.text : colors.secondary)
                    }
                    Text(active ? state.turnLabel(for: player) : "Waiting")
                        .font(.system(.caption, weight: .bold)).foregroundStyle(active ? colors.player(player) : colors.secondary)
                        .padding(.top, 3).padding(.bottom, 8)
                    Rectangle().fill(active ? colors.player(player) : colors.subtle).frame(height: active ? 3 : 1)
                }.frame(maxWidth: .infinity, alignment: .leading).accessibilityElement(children: .combine)
            }
        }
    }
    private var instructions: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(state.instructionTitle).font(.system(.title2, weight: .bold)).accessibilityAddTraits(.isHeader)
                Text(state.instructionDetail).font(.subheadline).foregroundStyle(colors.secondary)
            }.frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
                .accessibilityElement(children: .combine).accessibilityFocused($instructionFocus)
            if let symbol = state.instructionSymbol, state.stage != .terminal {
                RecessedPiece(symbol: symbol).frame(width: 62, height: 62)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel([.aiPlacing, .aiSettling].contains(state.stage) ? "Placed \(symbol.rawValue)" : "Piece in hand: \(symbol.rawValue)")
            }
        }.accessibilityElement(children: .contain).accessibilitySortPriority(80).accessibilityIdentifier("turn-status")
    }

    private var handoff: some View {
        ViewThatFits(in: .vertical) {
            handoffContent
            ScrollView { handoffContent }.scrollBounceBehavior(.basedOnSize)
        }.frame(maxWidth: 400).padding(20)
    }
    private var handoffContent: some View {
        VStack(spacing: 20) {
            Text("Pic-Pac Local").font(.system(.subheadline, weight: .bold)).foregroundStyle(colors.secondary)
            Text(state.activePlayer == .one ? "1" : "2").font(.system(.title2, weight: .bold))
                .foregroundStyle(colors.canvas).frame(width: 56, height: 56)
                .background(colors.player(state.activePlayer), in: RoundedRectangle(cornerRadius: 14)).accessibilityHidden(true)
            Text("\(state.activePlayer.label), you're up.").font(FormType.display()).multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader).accessibilityFocused($modalFocus)
            Text("Pass the phone, then tap when they're ready.").font(.body).foregroundStyle(colors.secondary).multilineTextAlignment(.center)
            Button("Ready") { Task { await coordinator.readyForReveal() } }.buttonStyle(FormButtonStyle()).accessibilityIdentifier("ready")
            Button("Home", action: home).buttonStyle(FormButtonStyle(primary: false))
        }.fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .contain).accessibilityIdentifier("local-handoff")
    }
    @ViewBuilder private var modalContent: some View {
        VStack(spacing: 16) {
            if state.stage == .revealing, let symbol = state.heldSymbol {
                Text(state.actorLabel(for: state.activePlayer)).font(.system(.subheadline, weight: .bold)).foregroundStyle(colors.player(state.activePlayer))
                Text(state.drawLabel ?? "").font(FormType.display()).multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader).accessibilityFocused($modalFocus)
                RecessedPiece(symbol: symbol).frame(width: 132, height: 132).accessibilityHidden(true)
                Text(state.mode == .picPacAI && state.activePlayer == .two ? "Computer will choose a square." : "One piece. Your choice of square.")
                    .font(.subheadline).foregroundStyle(colors.secondary).multilineTextAlignment(.center)
            } else {
                Text(state.resultLabel ?? "Draw").font(FormType.display(30, semibold: true)).multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader).accessibilityFocused($modalFocus)
                Text(resultDetail).font(.body).foregroundStyle(colors.secondary).multilineTextAlignment(.center)
                PhysicalBoard(board: state.board, winningLine: state.winningLine, stage: .terminal, miniature: true).frame(width: 188, height: 188)
                Button("Rematch") { Task { await coordinator.rematch() } }.buttonStyle(FormButtonStyle()).accessibilityIdentifier("rematch")
                Button("Home", action: home).buttonStyle(FormButtonStyle(primary: false))
            }
        }.padding(24).frame(maxWidth: .infinity).modifier(FormSurface()).fixedSize(horizontal: false, vertical: true)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier(state.stage == .revealing ? "reveal-dialog" : "result-dialog")
    }
    private var resultDetail: String { if case let .win(win) = state.outcome { "Three \(win.symbol.rawValue)s. One completed line." } else { "No line this time." } }
}

struct ProbabilityTray: View {
    @Environment(\.formPalette) private var colors
    @Environment(\.dynamicTypeSize) private var typeSize
    let remainingX: Int
    let remainingO: Int
    var held = true
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack { Text("Bag").font(.system(.body, weight: .semibold)); Spacer(); Text("Next draw").font(.system(.caption, weight: .bold)).foregroundStyle(colors.secondary) }
            Divider().overlay(colors.subtle).padding(.vertical, 10)
            let layout = typeSize.isAccessibilitySize ? AnyLayout(VStackLayout(alignment: .leading, spacing: 16)) : AnyLayout(HStackLayout(spacing: 16))
            layout { item(.x, count: remainingX); item(.o, count: remainingO) }
            let remaining = remainingX + remainingO
            let countLabel = remaining == 1 ? "1 piece remains" : "\(remaining) pieces remain"
            Text(held ? "\(countLabel) · held piece excluded" : "\(countLabel) in the shared bag")
                .font(.system(.footnote, weight: .medium)).foregroundStyle(colors.secondary).padding(.top, 10)
        }.padding(16).modifier(FormSurface()).accessibilityElement(children: .contain)
            .accessibilitySortPriority(60).accessibilityIdentifier("bag")
    }
    private func item(_ symbol: Symbol, count: Int) -> some View {
        let total = remainingX + remainingO
        let percent = total == 0 ? 0 : Int((Double(count) / Double(total) * 100).rounded())
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 5) { FormPiece(symbol: symbol).frame(width: 28, height: 28); Text("\(symbol.rawValue) ×\(count)").font(.system(.body, weight: .semibold)) }
            Text("\(percent)%").font(.system(.title2, weight: .bold)).monospacedDigit().foregroundStyle(colors.symbol(symbol))
        }.frame(maxWidth: .infinity, alignment: .leading).accessibilityElement(children: .ignore)
            .accessibilityLabel("\(symbol.rawValue), \(count) remaining, \(percent) percent next draw")
    }
}
