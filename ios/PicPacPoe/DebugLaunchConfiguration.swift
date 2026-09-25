#if DEBUG
import Foundation
import PicPacCore
import PicPacPresentation
import SwiftUI

/// In-memory simulator evidence; excluded from distribution builds.
@MainActor
enum DebugLaunchConfiguration {
    static var scrollAnchor: UnitPoint {
        ProcessInfo.processInfo.arguments.contains("-snapshot-scroll-bottom") ? .bottom : .top
    }
    static func makeCoordinator(arguments: [String] = ProcessInfo.processInfo.arguments) -> GameCoordinator? {
        let scenario = value(after: "-screenshot-scenario", in: arguments)
        if let session = value(after: "-ui-test-session", in: arguments) {
            return makeLiveTestCoordinator(session: session, arguments: arguments)
        }
        guard scenario != nil || arguments.contains("-ui-test-reset") else { return nil }
        do {
            let theme = ThemePreference(rawValue: value(after: "-screenshot-theme", in: arguments) ?? "system") ?? .system
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

    /// UI tests use the real atomic file adapter and production workers. A
    /// dedicated namespace makes relaunch evidence durable without touching a
    /// person's normal match. Reset is explicit and confined to this namespace.
    private static func makeLiveTestCoordinator(session: String, arguments: [String]) -> GameCoordinator? {
        guard !session.isEmpty, session.count <= 100,
              session.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "-") }) else { return nil }
        do {
            let root = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let directory = root.appendingPathComponent("PicPacPoeUITests", isDirectory: true).appendingPathComponent(session, isDirectory: true)
            if arguments.contains("-ui-test-reset"), FileManager.default.fileExists(atPath: directory.path) {
                try FileManager.default.removeItem(at: directory)
            }
            if arguments.contains("-ui-test-reset"), let scenario = value(after: "-ui-test-initial-scenario", in: arguments) {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                try PersistenceCodec.encode(snapshot(for: scenario)).write(to: directory.appendingPathComponent("current-match.json"), options: .atomic)
            }
            let multiplier = min(20, max(1, Int(value(after: "-ui-test-clock-multiplier", in: arguments) ?? "1") ?? 1))
            return GameCoordinator(clock: LiveUITestClock(multiplier: multiplier), aiWorker: ProductionAIWorker(),
                                   store: FileLocalStateStore(directoryURL: directory), initialSceneIsActive: false,
                                   requiresRestorationBeforeCommands: true)
        } catch {
            assertionFailure("Invalid UI test session: \(error)")
            return nil
        }
    }

    /// Signals the host capture tool only after restoration has produced a
    /// renderable screen. The marker lives in the app's temporary container
    /// and is never created in production builds.
    static func signalScreenshotReady(arguments: [String] = ProcessInfo.processInfo.arguments) {
        guard value(after: "-screenshot-scenario", in: arguments) != nil,
              let token = value(after: "-screenshot-ready-token", in: arguments),
              !token.isEmpty,
              token.allSatisfy({ $0.isASCII && ($0.isLetter || $0.isNumber || "._-".contains($0)) })
        else { return }

        let marker = FileManager.default.temporaryDirectory
            .appendingPathComponent("picpac-screenshot-ready-\(token)", isDirectory: false)
        try? Data("ready\n".utf8).write(to: marker, options: .atomic)
    }

    static func value(after flag: String, in arguments: [String]) -> String? {
        guard let index = arguments.firstIndex(of: flag), arguments.indices.contains(index + 1) else { return nil }
        return arguments[index + 1]
    }

    static func snapshot(for scenario: String) throws -> RestorationSnapshot {
        var state = GamePresentationState()
        switch scenario {
        case "settings": state.screen = .settings
        case "how-to": state.screen = .howTo
        case "ai-lab": state.screen = .aiLab
        case "human-placement", "human-turn-start", "human-reveal", "local-handoff", "local-reveal":
            let handoff = scenario == "local-handoff"
            let undrawn = handoff || scenario == "human-turn-start"
            let game = try PicPacState(board: .empty, activePlayer: .one, remainingX: undrawn ? 5 : 4, remainingO: 5,
                                      phase: undrawn ? .awaitingDraw : .awaitingPlacement(held: .x, token: TurnToken(17)), starter: .one, revision: 1)
            state = GamePresentationState(screen: .game, mode: scenario.hasPrefix("human-") ? .picPacAI : .picPacLocal, picPac: game,
                                          stage: handoff ? .handoff : scenario == "human-turn-start" ? .turnStart : scenario.hasSuffix("reveal") ? .revealing : .playing, presentationID: 1)
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
        case "result", "terminal-settling-win":
            let board = try Board(symbols: [.x, .x, .x, .o, nil, nil, nil, nil, nil])
            let outcome = GameOutcome.win(.init(player: .two, symbol: .x, lines: board.winningLines(for: .x)))
            let game = try PicPacState(board: board, activePlayer: .two, remainingX: 2, remainingO: 4, phase: .terminal(outcome: outcome), starter: .one, revision: 1)
            state = GamePresentationState(screen: .game, mode: .picPacAI, picPac: game, stage: scenario == "result" ? .terminal : .aiSettling, presentationID: 1, aiTargetCell: 2, aiMoveSymbol: .x)
        case "draw-result", "terminal-settling-draw":
            let board = try Board(symbols: [.x, .o, .x, .x, .o, .o, .o, .x, .x])
            let game = try PicPacState(board: board, activePlayer: .two, remainingX: 0, remainingO: 1,
                                      phase: .terminal(outcome: .draw), starter: .two, revision: 1)
            state = GamePresentationState(screen: .game, mode: .picPacAI, picPac: game,
                                          stage: scenario == "draw-result" ? .terminal : .aiSettling, presentationID: 1, aiTargetCell: 8, aiMoveSymbol: .x)
        case "classic":
            state = GamePresentationState(screen: .game, mode: .classicLocal, classic: ClassicRules.newGame(revision: 1), stage: .playing, presentationID: 1)
        case "home": break
        default: throw CocoaError(.coderInvalidValue)
        }
        return RestorationSnapshot(state: state, revision: state.screen == .game ? 1 : 0, presentationCounter: 1, titleEntranceConsumed: true)
    }
}

private struct LiveUITestClock: PresentationClock {
    let multiplier: Int
    func sleep(milliseconds: Int) async throws {
        try await Task.sleep(for: .milliseconds(milliseconds * multiplier))
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
