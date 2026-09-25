import PicPacCore

public enum SearchModelError: Error, Equatable, Sendable {
    case invalidState
    case occupiedCell(Int)
    case emptyBag
    case exhaustedSymbol(Symbol)
}

public struct PicPacDecisionState: Equatable, Hashable, Sendable {
    public let board: Board
    public let heldSymbol: Symbol
    public let remainingX: Int
    public let remainingO: Int

    public init(
        board: Board,
        heldSymbol: Symbol,
        remainingX: Int,
        remainingO: Int
    ) throws {
        guard (0...5).contains(remainingX), (0...5).contains(remainingO),
              board.count(of: .x) + remainingX + (heldSymbol == .x ? 1 : 0) == 5,
              board.count(of: .o) + remainingO + (heldSymbol == .o ? 1 : 0) == 5,
              !board.hasWinner,
              !board.isFull else {
            throw SearchModelError.invalidState
        }
        self.board = board
        self.heldSymbol = heldSymbol
        self.remainingX = remainingX
        self.remainingO = remainingO
    }

    public var legalCells: [Cell] { board.legalCells }
    public var hiddenTotal: Int { remainingX + remainingO }
}

public struct PicPacChanceState: Equatable, Hashable, Sendable {
    public let board: Board
    public let remainingX: Int
    public let remainingO: Int

    public init(board: Board, remainingX: Int, remainingO: Int) throws {
        guard (0...5).contains(remainingX), (0...5).contains(remainingO),
              board.count(of: .x) + remainingX == 5,
              board.count(of: .o) + remainingO == 5,
              remainingX + remainingO > 0,
              !board.hasWinner,
              !board.isFull else {
            throw SearchModelError.invalidState
        }
        self.board = board
        self.remainingX = remainingX
        self.remainingO = remainingO
    }

    public var total: Int { remainingX + remainingO }
}

public enum SearchTransition: Equatable, Hashable, Sendable {
    case win
    case draw
    case chance(PicPacChanceState)
}

public enum PublicPicPacSearchModel {
    public static func from(_ observation: AiObservation) -> PicPacDecisionState {
        try! PicPacDecisionState(
            board: observation.board,
            heldSymbol: observation.heldSymbol,
            remainingX: observation.remainingX,
            remainingO: observation.remainingO
        )
    }

    public static func place(
        _ state: PicPacDecisionState,
        at cell: Cell
    ) throws -> SearchTransition {
        guard state.board[cell] == nil else {
            throw SearchModelError.occupiedCell(cell.index)
        }
        let board = try state.board.placing(state.heldSymbol, at: cell)
        if !board.winningLines(for: state.heldSymbol).isEmpty { return .win }
        if board.isFull { return .draw }
        return .chance(
            try PicPacChanceState(
                board: board,
                remainingX: state.remainingX,
                remainingO: state.remainingO
            )
        )
    }

    public static func draw(
        _ state: PicPacChanceState,
        symbol: Symbol
    ) throws -> PicPacDecisionState {
        guard state.total > 0 else { throw SearchModelError.emptyBag }
        let available = symbol == .x ? state.remainingX : state.remainingO
        guard available > 0 else { throw SearchModelError.exhaustedSymbol(symbol) }
        return try PicPacDecisionState(
            board: state.board,
            heldSymbol: symbol,
            remainingX: state.remainingX - (symbol == .x ? 1 : 0),
            remainingO: state.remainingO - (symbol == .o ? 1 : 0)
        )
    }
}
