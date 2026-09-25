import Foundation

public enum GameOutcome: Equatable, Hashable, Codable, Sendable {
    public struct Win: Equatable, Hashable, Codable, Sendable {
        public let player: Player
        public let symbol: Symbol
        public let lines: [WinningLine]

        public init(player: Player, symbol: Symbol, lines: [WinningLine]) {
            self.player = player
            self.symbol = symbol
            self.lines = lines
        }
    }

    case win(Win)
    case draw
}

public enum PicPacPhase: Equatable, Hashable, Codable, Sendable {
    case awaitingDraw
    case awaitingPlacement(held: Symbol, token: TurnToken)
    case terminal(outcome: GameOutcome)

    public var heldSymbol: Symbol? {
        guard case let .awaitingPlacement(held, _) = self else { return nil }
        return held
    }

    public var turnToken: TurnToken? {
        guard case let .awaitingPlacement(_, token) = self else { return nil }
        return token
    }

    public var outcome: GameOutcome? {
        guard case let .terminal(outcome) = self else { return nil }
        return outcome
    }
}

public struct PicPacState: Equatable, Hashable, Codable, Sendable {
    public let board: Board
    public let activePlayer: Player
    public let remainingX: Int
    public let remainingO: Int
    public let phase: PicPacPhase
    public let starter: Player
    public let revision: Int64

    public init(
        board: Board,
        activePlayer: Player,
        remainingX: Int,
        remainingO: Int,
        phase: PicPacPhase,
        starter: Player,
        revision: Int64
    ) throws {
        guard (0...5).contains(remainingX), (0...5).contains(remainingO) else {
            throw DomainValidationError.invalidRemainingCounts(x: remainingX, o: remainingO)
        }
        guard revision >= 0 else {
            throw DomainValidationError.negativeRevision(revision)
        }
        guard revision <= TurnToken.maximumRevision else {
            throw DomainValidationError.revisionTooLarge(revision)
        }

        let heldX = phase.heldSymbol == .x ? 1 : 0
        let heldO = phase.heldSymbol == .o ? 1 : 0
        let totalX = board.count(of: .x) + remainingX + heldX
        let totalO = board.count(of: .o) + remainingO + heldO
        guard totalX == 5 else {
            throw DomainValidationError.conservation(symbol: .x, actual: totalX)
        }
        guard totalO == 5 else {
            throw DomainValidationError.conservation(symbol: .o, actual: totalO)
        }

        let occupiedCount = board.occupiedCount
        switch phase {
        case .awaitingDraw:
            guard !board.hasWinner else {
                throw DomainValidationError.nonterminalWinningBoard
            }
            guard !board.isFull else {
                throw DomainValidationError.nonterminalFullBoard
            }
            guard remainingX + remainingO > 0 else {
                throw DomainValidationError.emptyBagAwaitingDraw
            }
            try Self.validateActivePlayer(
                activePlayer,
                starter: starter,
                occupiedCount: occupiedCount,
                terminal: false
            )
        case let .awaitingPlacement(held: _, token: token):
            guard !board.hasWinner else {
                throw DomainValidationError.nonterminalWinningBoard
            }
            guard !board.isFull else {
                throw DomainValidationError.nonterminalFullBoard
            }
            try Self.validateActivePlayer(
                activePlayer,
                starter: starter,
                occupiedCount: occupiedCount,
                terminal: false
            )
            guard let expected = TurnToken.derived(
                revision: revision,
                ordinal: occupiedCount + 1
            ) else {
                throw DomainValidationError.revisionTooLarge(revision)
            }
            guard token == expected else {
                throw DomainValidationError.invalidTurnToken(
                    expected: expected.value,
                    actual: token.value
                )
            }
        case let .terminal(outcome):
            guard Self.terminalOutcome(outcome, matches: board, activePlayer: activePlayer) else {
                throw DomainValidationError.invalidTerminalOutcome
            }
            try Self.validateActivePlayer(
                activePlayer,
                starter: starter,
                occupiedCount: occupiedCount,
                terminal: true
            )
        }

        self.board = board
        self.activePlayer = activePlayer
        self.remainingX = remainingX
        self.remainingO = remainingO
        self.phase = phase
        self.starter = starter
        self.revision = revision
    }

    public var isTerminal: Bool {
        if case .terminal = phase { return true }
        return false
    }

    public var hiddenTotal: Int { remainingX + remainingO }
    public var nextXProbability: Double {
        hiddenTotal == 0 ? 0 : Double(remainingX) / Double(hiddenTotal)
    }
    public var nextOProbability: Double {
        hiddenTotal == 0 ? 0 : Double(remainingO) / Double(hiddenTotal)
    }

    private static func terminalOutcome(
        _ outcome: GameOutcome,
        matches board: Board,
        activePlayer: Player
    ) -> Bool {
        switch outcome {
        case .draw:
            return board.isFull && !board.hasWinner
        case let .win(win):
            return win.player == activePlayer
                && !win.lines.isEmpty
                && board.winningLines(for: win.symbol) == win.lines
                && board.winningLines(for: win.symbol.other).isEmpty
        }
    }

    private static func validateActivePlayer(
        _ activePlayer: Player,
        starter: Player,
        occupiedCount: Int,
        terminal: Bool
    ) throws {
        let starterActs = terminal
            ? !occupiedCount.isMultiple(of: 2)
            : occupiedCount.isMultiple(of: 2)
        let expected = starterActs ? starter : starter.other
        guard activePlayer == expected else {
            throw DomainValidationError.invalidActivePlayer(
                expected: expected,
                actual: activePlayer
            )
        }
    }

    private enum CodingKeys: String, CodingKey {
        case board, activePlayer, remainingX, remainingO, phase, starter, revision
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        do {
            try self.init(
                board: container.decode(Board.self, forKey: .board),
                activePlayer: container.decode(Player.self, forKey: .activePlayer),
                remainingX: container.decode(Int.self, forKey: .remainingX),
                remainingO: container.decode(Int.self, forKey: .remainingO),
                phase: container.decode(PicPacPhase.self, forKey: .phase),
                starter: container.decode(Player.self, forKey: .starter),
                revision: container.decode(Int64.self, forKey: .revision)
            )
        } catch let error as DomainValidationError {
            throw DecodingError.dataCorrupted(
                .init(codingPath: decoder.codingPath, debugDescription: error.description)
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(board, forKey: .board)
        try container.encode(activePlayer, forKey: .activePlayer)
        try container.encode(remainingX, forKey: .remainingX)
        try container.encode(remainingO, forKey: .remainingO)
        try container.encode(phase, forKey: .phase)
        try container.encode(starter, forKey: .starter)
        try container.encode(revision, forKey: .revision)
    }
}

public struct ClassicState: Equatable, Hashable, Codable, Sendable {
    public let board: Board
    public let activePlayer: Player
    public let starter: Player
    public let turnToken: TurnToken
    public let revision: Int64
    public let outcome: GameOutcome?

    public init(
        board: Board,
        activePlayer: Player,
        starter: Player,
        turnToken: TurnToken,
        revision: Int64,
        outcome: GameOutcome? = nil
    ) throws {
        guard revision >= 0 else {
            throw DomainValidationError.negativeRevision(revision)
        }
        guard revision <= TurnToken.maximumRevision else {
            throw DomainValidationError.revisionTooLarge(revision)
        }
        let occupiedCount = board.occupiedCount
        if let outcome {
            guard Self.outcome(outcome, matches: board, activePlayer: activePlayer) else {
                throw DomainValidationError.invalidClassicOutcome
            }
        } else {
            guard !board.hasWinner else {
                throw DomainValidationError.nonterminalWinningBoard
            }
            guard !board.isFull else {
                throw DomainValidationError.nonterminalFullBoard
            }
        }

        let expectedX = starter == .one ? (occupiedCount + 1) / 2 : occupiedCount / 2
        let expectedO = starter == .two ? (occupiedCount + 1) / 2 : occupiedCount / 2
        guard board.count(of: .x) == expectedX, board.count(of: .o) == expectedO else {
            throw DomainValidationError.invalidClassicSymbolCounts(
                x: board.count(of: .x),
                o: board.count(of: .o)
            )
        }

        let starterActs = outcome == nil
            ? occupiedCount.isMultiple(of: 2)
            : !occupiedCount.isMultiple(of: 2)
        let expectedPlayer = starterActs ? starter : starter.other
        guard activePlayer == expectedPlayer else {
            throw DomainValidationError.invalidActivePlayer(
                expected: expectedPlayer,
                actual: activePlayer
            )
        }

        let tokenOrdinal = outcome == nil ? occupiedCount + 1 : occupiedCount
        guard let expectedToken = TurnToken.derived(
            revision: revision,
            ordinal: tokenOrdinal
        ) else {
            throw DomainValidationError.revisionTooLarge(revision)
        }
        guard turnToken == expectedToken else {
            throw DomainValidationError.invalidTurnToken(
                expected: expectedToken.value,
                actual: turnToken.value
            )
        }

        self.board = board
        self.activePlayer = activePlayer
        self.starter = starter
        self.turnToken = turnToken
        self.revision = revision
        self.outcome = outcome
    }

    public var isTerminal: Bool { outcome != nil }
    public func symbol(for player: Player) -> Symbol { player == .one ? .x : .o }

    private static func outcome(
        _ outcome: GameOutcome,
        matches board: Board,
        activePlayer: Player
    ) -> Bool {
        switch outcome {
        case .draw:
            return board.isFull && !board.hasWinner
        case let .win(win):
            let fixedSymbol: Symbol = win.player == .one ? .x : .o
            return win.player == activePlayer
                && win.symbol == fixedSymbol
                && !win.lines.isEmpty
                && board.winningLines(for: win.symbol) == win.lines
                && board.winningLines(for: win.symbol.other).isEmpty
        }
    }

    private enum CodingKeys: String, CodingKey {
        case board, activePlayer, starter, turnToken, revision, outcome
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        do {
            try self.init(
                board: container.decode(Board.self, forKey: .board),
                activePlayer: container.decode(Player.self, forKey: .activePlayer),
                starter: container.decode(Player.self, forKey: .starter),
                turnToken: container.decode(TurnToken.self, forKey: .turnToken),
                revision: container.decode(Int64.self, forKey: .revision),
                outcome: container.decodeIfPresent(GameOutcome.self, forKey: .outcome)
            )
        } catch let error as DomainValidationError {
            throw DecodingError.dataCorrupted(
                .init(codingPath: decoder.codingPath, debugDescription: error.description)
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(board, forKey: .board)
        try container.encode(activePlayer, forKey: .activePlayer)
        try container.encode(starter, forKey: .starter)
        try container.encode(turnToken, forKey: .turnToken)
        try container.encode(revision, forKey: .revision)
        try container.encodeIfPresent(outcome, forKey: .outcome)
    }
}

public enum RejectionReason: String, Equatable, Codable, Sendable {
    case wrongPhase = "WRONG_PHASE"
    case staleTurn = "STALE_TURN"
    case occupied = "OCCUPIED"
    case terminal = "TERMINAL"
    case exhaustedSymbol = "EXHAUSTED_SYMBOL"
}

public enum GameEvent: Equatable, Codable, Sendable {
    case pieceRevealed(player: Player, symbol: Symbol)
    case piecePlaced(player: Player, symbol: Symbol, cell: Cell)
    case gameWon(GameOutcome.Win)
    case gameDrawn
}

public enum TransitionResult<State: Equatable & Sendable>: Equatable, Sendable {
    case accepted(state: State, event: GameEvent)
    case rejected(RejectionReason)

    public var acceptedState: State? {
        guard case let .accepted(state, _) = self else { return nil }
        return state
    }
}

extension TransitionResult: Codable where State: Codable {}
