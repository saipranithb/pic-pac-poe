import Foundation
import PicPacCore

public enum AppScreen: String, Codable, CaseIterable, Sendable {
    case home
    case game
    case howTo
    case settings
    case aiLab
}

public enum GameMode: String, Codable, CaseIterable, Sendable {
    case classicLocal
    case picPacLocal
    case picPacAI
}

public enum Difficulty: String, Codable, CaseIterable, Sendable {
    case easy
    case medium
    case hard
    case mcts
    case qLearning

    public var isProduction: Bool {
        switch self {
        case .easy, .medium, .hard: true
        case .mcts, .qLearning: false
        }
    }
}

public enum TurnStage: String, Codable, CaseIterable, Sendable {
    case handoff
    case turnStart
    case revealing
    case playing
    case aiThinking
    case aiTargeting
    case aiPlacing
    case aiSettling
    case terminal

    public func delayMilliseconds(reducedMotion: Bool) -> Int? {
        switch (self, reducedMotion) {
        case (.turnStart, false): 300
        case (.turnStart, true): 160
        case (.revealing, false): 650
        case (.revealing, true): 500
        case (.aiTargeting, false): 280
        case (.aiTargeting, true): 160
        case (.aiPlacing, false): 340
        case (.aiPlacing, true): 180
        case (.aiSettling, false): 480
        case (.aiSettling, true): 320
        default: nil
        }
    }
}

public enum FeedbackKind: String, Codable, CaseIterable, Sendable {
    case reveal
    case place
    case win
    case draw
}

public struct FeedbackEvent: Equatable, Sendable {
    public let id: UInt64
    public let kind: FeedbackKind

    public init(id: UInt64, kind: FeedbackKind) {
        self.id = id
        self.kind = kind
    }
}

public enum ThemePreference: String, Codable, CaseIterable, Sendable {
    case system
    case light
    case dark
}

public struct AppSettings: Codable, Equatable, Sendable {
    public var soundEnabled: Bool
    public var hapticsEnabled: Bool
    public var reducedMotion: Bool
    public var theme: ThemePreference

    public init(
        soundEnabled: Bool = true,
        hapticsEnabled: Bool = true,
        reducedMotion: Bool = false,
        theme: ThemePreference = .system
    ) {
        self.soundEnabled = soundEnabled
        self.hapticsEnabled = hapticsEnabled
        self.reducedMotion = reducedMotion
        self.theme = theme
    }
}

public struct GamePresentationState: Codable, Equatable, Sendable {
    public var screen: AppScreen
    public var mode: GameMode?
    public var difficulty: Difficulty
    public var classic: ClassicState?
    public var picPac: PicPacState?
    public var stage: TurnStage
    public var presentationID: UInt64
    public var aiTargetCell: Int?
    public var aiMoveSymbol: Symbol?

    public init(
        screen: AppScreen = .home,
        mode: GameMode? = nil,
        difficulty: Difficulty = .medium,
        classic: ClassicState? = nil,
        picPac: PicPacState? = nil,
        stage: TurnStage = .playing,
        presentationID: UInt64 = 0,
        aiTargetCell: Int? = nil,
        aiMoveSymbol: Symbol? = nil
    ) {
        self.screen = screen
        self.mode = mode
        self.difficulty = difficulty
        self.classic = classic
        self.picPac = picPac
        self.stage = stage
        self.presentationID = presentationID
        self.aiTargetCell = aiTargetCell
        self.aiMoveSymbol = aiMoveSymbol
    }

    public var activePlayer: Player {
        classic?.activePlayer ?? picPac?.activePlayer ?? .one
    }

    public var displayedPlayer: Player {
        if mode == .picPacAI,
           [.aiThinking, .aiTargeting, .aiPlacing, .aiSettling].contains(stage) {
            return .two
        }
        return activePlayer
    }

    public var heldSymbol: Symbol? {
        guard case let .awaitingPlacement(held: symbol, token: _) = picPac?.phase else { return nil }
        return symbol
    }

    public var outcome: GameOutcome? {
        if let classic { return classic.outcome }
        guard case let .terminal(outcome) = picPac?.phase else { return nil }
        return outcome
    }

    public var board: Board {
        classic?.board ?? picPac?.board ?? .empty
    }

    public func actorLabel(for player: Player) -> String {
        guard mode == .picPacAI else { return player.label }
        return player == .one ? "You" : "Computer"
    }

    public func turnLabel(for player: Player? = nil) -> String {
        let player = player ?? displayedPlayer
        guard mode == .picPacAI else { return "\(player.label)'s turn" }
        return player == .one ? "Your turn" : "Computer's turn"
    }

    public var drawLabel: String? {
        guard let heldSymbol else { return nil }
        let actor = mode == .picPacAI ? actorLabel(for: activePlayer) : "You"
        return "\(actor) drew \(heldSymbol.rawValue)"
    }

    public var resultLabel: String? {
        switch outcome {
        case nil:
            nil
        case .draw:
            "Draw"
        case let .win(win):
            if mode == .picPacAI, win.player == .one {
                "You win"
            } else {
                "\(actorLabel(for: win.player)) wins"
            }
        }
    }
}

public struct RestorationSnapshot: Codable, Equatable, Sendable {
    public static let currentSchemaVersion = 1

    public var schemaVersion: Int
    public var state: GamePresentationState
    public var revision: Int64
    public var nextStarter: Player
    public var presentationCounter: UInt64
    public var titleEntranceConsumed: Bool

    public init(
        schemaVersion: Int = currentSchemaVersion,
        state: GamePresentationState = GamePresentationState(),
        revision: Int64 = 0,
        nextStarter: Player = .one,
        presentationCounter: UInt64 = 0,
        titleEntranceConsumed: Bool = false
    ) {
        self.schemaVersion = schemaVersion
        self.state = state
        self.revision = revision
        self.nextStarter = nextStarter
        self.presentationCounter = presentationCounter
        self.titleEntranceConsumed = titleEntranceConsumed
    }
}
