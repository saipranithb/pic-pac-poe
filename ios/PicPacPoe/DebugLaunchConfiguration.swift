#if DEBUG
import Foundation
import PicPacCore
import PicPacPresentation

/// In-memory simulator evidence; excluded from distribution builds.
@MainActor
enum DebugLaunchConfiguration {
    static func makeCoordinator(arguments: [String] = ProcessInfo.processInfo.arguments) -> GameCoordinator? {
        func value(_ flag: String) -> String? {
            guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else { return nil }
            return arguments[index + 1]
        }
        let scenario = value("-screenshot-scenario")
        guard scenario != nil || arguments.contains("-ui-test-reset") else { return nil }
        do {
            let theme = ThemePreference(rawValue: value("-screenshot-theme") ?? "system") ?? .system
            let canonicalSettingsDark = scenario == "settings" && theme == .dark
            let canonicalSettingsLight = scenario == "settings" && theme == .light
            let settings = AppSettings(
                soundEnabled: canonicalSettingsDark,
                hapticsEnabled: canonicalSettingsDark,
                reducedMotion: arguments.contains("-screenshot-reduced") || canonicalSettingsLight,
                theme: theme
            )
            var snapshot = try snapshot(for: scenario ?? "home")
            if scenario == nil { snapshot.titleEntranceConsumed = false }
            let store = InMemoryLocalStateStore(snapshotData: try PersistenceCodec.encode(snapshot), settingsData: try PersistenceCodec.encode(settings))
            let clock: any PresentationClock = scenario == nil ? SystemPresentationClock() : ScreenshotClock()
            let worker: any AIWorker = scenario == nil ? ProductionAIWorker() : ScreenshotAIWorker()
            return GameCoordinator(clock: clock, aiWorker: worker, store: store, initialSceneIsActive: false, requiresRestorationBeforeCommands: true)
        } catch {
            assertionFailure("Invalid screenshot fixture: \(error)")
            return nil
        }
    }
    static func snapshot(for scenario: String) throws -> RestorationSnapshot {
        var state = GamePresentationState()
        switch scenario {
        case "settings": state.screen = .settings
        case "how-to": state.screen = .howTo
        case "ai-lab": state.screen = .aiLab
        case "human-placement", "local-handoff", "local-reveal":
            let handoff = scenario == "local-handoff"
            let game = try PicPacState(board: .empty, activePlayer: .one, remainingX: handoff ? 5 : 4, remainingO: 5,
                                      phase: handoff ? .awaitingDraw : .awaitingPlacement(held: .x, token: TurnToken(17)), starter: .one, revision: 1)
            state = GamePresentationState(screen: .game, mode: scenario == "human-placement" ? .picPacAI : .picPacLocal, picPac: game,
                                          stage: handoff ? .handoff : scenario == "local-reveal" ? .revealing : .playing, presentationID: 1)
        case "computer-targeting", "computer-settled", "computer-thinking", "computer-reveal", "computer-placement":
            let committed = ["computer-settled", "computer-placement"].contains(scenario)
            let board = try Board(symbols: [.x, nil, .o, .o, .x, nil, .x, nil, committed ? .o : nil])
            let phase: PicPacPhase = committed ? .awaitingDraw : .awaitingPlacement(held: .o, token: TurnToken(22))
            let game = try PicPacState(board: board, activePlayer: committed ? .one : .two, remainingX: 2, remainingO: 2, phase: phase, starter: .one, revision: 1)
            let stage: TurnStage = switch scenario {
            case "computer-settled": .aiSettling
            case "computer-placement": .aiPlacing
            case "computer-thinking": .aiThinking
            case "computer-reveal": .revealing
            default: .aiTargeting
            }
            let targeted = [.aiTargeting, .aiPlacing, .aiSettling].contains(stage)
            state = GamePresentationState(screen: .game, mode: .picPacAI, picPac: game, stage: stage, presentationID: 1,
                                          aiTargetCell: targeted ? 8 : nil, aiMoveSymbol: targeted ? .o : nil)
        case "result":
            let board = try Board(symbols: [.x, .x, .x, .o, nil, nil, nil, nil, nil])
            let outcome = GameOutcome.win(.init(player: .two, symbol: .x, lines: board.winningLines(for: .x)))
            let game = try PicPacState(board: board, activePlayer: .two, remainingX: 2, remainingO: 4, phase: .terminal(outcome: outcome), starter: .one, revision: 1)
            state = GamePresentationState(screen: .game, mode: .picPacAI, picPac: game, stage: .terminal, presentationID: 1, aiTargetCell: 2, aiMoveSymbol: .x)
        case "classic":
            state = GamePresentationState(screen: .game, mode: .classicLocal, classic: ClassicRules.newGame(revision: 1), stage: .playing, presentationID: 1)
        default: break
        }
        return RestorationSnapshot(state: state, revision: state.screen == .game ? 1 : 0, presentationCounter: 1, titleEntranceConsumed: true)
    }
}

private struct ScreenshotClock: PresentationClock {
    func sleep(milliseconds: Int) async throws { try await Task.sleep(for: .seconds(86_400)) }
}

private struct ScreenshotAIWorker: AIWorker {
    func chooseMove(for observation: AiObservation, difficulty: Difficulty) async throws -> Int {
        try await Task.sleep(for: .seconds(86_400))
        throw CancellationError()
    }
}
#endif
