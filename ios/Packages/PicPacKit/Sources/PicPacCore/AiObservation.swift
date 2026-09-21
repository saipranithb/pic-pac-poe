import Foundation

public struct AiObservation: Equatable, Hashable, Codable, Sendable {
    public let board: Board
    public let activePlayer: Player
    public let agentPlayer: Player
    public let heldSymbol: Symbol
    public let remainingX: Int
    public let remainingO: Int

    public init(
        board: Board,
        activePlayer: Player,
        agentPlayer: Player,
        heldSymbol: Symbol,
        remainingX: Int,
        remainingO: Int
    ) throws {
        guard (0...5).contains(remainingX), (0...5).contains(remainingO) else {
            throw DomainValidationError.invalidRemainingCounts(x: remainingX, o: remainingO)
        }
        let heldX = heldSymbol == .x ? 1 : 0
        let heldO = heldSymbol == .o ? 1 : 0
        guard board.count(of: .x) + remainingX + heldX == 5,
              board.count(of: .o) + remainingO + heldO == 5,
              !board.hasWinner,
              !board.isFull else {
            throw DomainValidationError.invalidObservation
        }

        self.board = board
        self.activePlayer = activePlayer
        self.agentPlayer = agentPlayer
        self.heldSymbol = heldSymbol
        self.remainingX = remainingX
        self.remainingO = remainingO
    }

    public var legalCells: [Cell] { board.legalCells }

    public static func from(
        state: PicPacState,
        agentPlayer: Player
    ) throws -> AiObservation {
        guard case let .awaitingPlacement(held, _) = state.phase else {
            throw DomainValidationError.invalidObservation
        }
        return try AiObservation(
            board: state.board,
            activePlayer: state.activePlayer,
            agentPlayer: agentPlayer,
            heldSymbol: held,
            remainingX: state.remainingX,
            remainingO: state.remainingO
        )
    }

    private enum CodingKeys: String, CodingKey {
        case board, activePlayer, agentPlayer, heldSymbol, remainingX, remainingO
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        do {
            try self.init(
                board: container.decode(Board.self, forKey: .board),
                activePlayer: container.decode(Player.self, forKey: .activePlayer),
                agentPlayer: container.decode(Player.self, forKey: .agentPlayer),
                heldSymbol: container.decode(Symbol.self, forKey: .heldSymbol),
                remainingX: container.decode(Int.self, forKey: .remainingX),
                remainingO: container.decode(Int.self, forKey: .remainingO)
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
        try container.encode(agentPlayer, forKey: .agentPlayer)
        try container.encode(heldSymbol, forKey: .heldSymbol)
        try container.encode(remainingX, forKey: .remainingX)
        try container.encode(remainingO, forKey: .remainingO)
    }
}
