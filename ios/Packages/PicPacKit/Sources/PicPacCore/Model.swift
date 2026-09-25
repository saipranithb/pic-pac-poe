import Foundation

public enum Symbol: String, CaseIterable, Codable, Hashable, Sendable {
    case x = "X"
    case o = "O"

    public var other: Symbol { self == .x ? .o : .x }
}

public enum Player: String, CaseIterable, Codable, Hashable, Sendable {
    case one = "ONE"
    case two = "TWO"

    public var other: Player { self == .one ? .two : .one }
    public var label: String { self == .one ? "Player 1" : "Player 2" }
}

public enum DomainValidationError: Error, Equatable, Sendable, CustomStringConvertible {
    case invalidCellIndex(Int)
    case invalidBoardCode(Int)
    case invalidBoardSize(Int)
    case occupiedCell(Int)
    case invalidRemainingCounts(x: Int, o: Int)
    case negativeRevision(Int64)
    case revisionTooLarge(Int64)
    case conservation(symbol: Symbol, actual: Int)
    case nonterminalWinningBoard
    case nonterminalFullBoard
    case emptyBagAwaitingDraw
    case invalidTerminalOutcome
    case invalidClassicOutcome
    case invalidActivePlayer(expected: Player, actual: Player)
    case invalidClassicSymbolCounts(x: Int, o: Int)
    case invalidTurnToken(expected: Int64, actual: Int64)
    case invalidObservation

    public var description: String {
        switch self {
        case let .invalidCellIndex(index):
            "Cell index must be in 0...8; got \(index)"
        case let .invalidBoardCode(code):
            "Board code must be in 0..<19683; got \(code)"
        case let .invalidBoardSize(size):
            "Board must contain exactly 9 cells; got \(size)"
        case let .occupiedCell(index):
            "Cell \(index) is occupied"
        case let .invalidRemainingCounts(x, o):
            "Remaining counts must each be in 0...5; got X=\(x), O=\(o)"
        case let .negativeRevision(revision):
            "Revision must be nonnegative; got \(revision)"
        case let .revisionTooLarge(revision):
            "Revision exceeds the turn-token range; got \(revision)"
        case let .conservation(symbol, actual):
            "\(symbol.rawValue) conservation failed; total is \(actual), expected 5"
        case .nonterminalWinningBoard:
            "A nonterminal state cannot contain a winning line"
        case .nonterminalFullBoard:
            "A full board must be terminal"
        case .emptyBagAwaitingDraw:
            "An awaiting-draw state requires at least one hidden piece"
        case .invalidTerminalOutcome:
            "Terminal outcome does not match the board"
        case .invalidClassicOutcome:
            "Classic outcome does not match the board"
        case let .invalidActivePlayer(expected, actual):
            "Active player must be \(expected.rawValue); got \(actual.rawValue)"
        case let .invalidClassicSymbolCounts(x, o):
            "Classic symbol counts are unreachable; got X=\(x), O=\(o)"
        case let .invalidTurnToken(expected, actual):
            "Turn token must be \(expected); got \(actual)"
        case .invalidObservation:
            "Public AI observation is not a legal post-draw decision state"
        }
    }
}

public struct Cell: Hashable, Comparable, Codable, Sendable {
    public let index: Int

    public init(index: Int) {
        precondition((0...8).contains(index), "Cell index must be in 0...8")
        self.index = index
    }

    public init(_ index: Int) {
        self.init(index: index)
    }

    public static let all: [Cell] = (0...8).map { Cell(index: $0) }

    public var rowOneBased: Int { index / 3 + 1 }
    public var columnOneBased: Int { index % 3 + 1 }

    public static func < (lhs: Cell, rhs: Cell) -> Bool {
        lhs.index < rhs.index
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let index = try container.decode(Int.self)
        guard (0...8).contains(index) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: DomainValidationError.invalidCellIndex(index).description
            )
        }
        self.index = index
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(index)
    }
}

public struct WinningLine: Equatable, Hashable, Codable, Sendable {
    public let first: Cell
    public let second: Cell
    public let third: Cell

    public init(_ first: Cell, _ second: Cell, _ third: Cell) {
        self.first = first
        self.second = second
        self.third = third
    }

    public var cells: [Cell] { [first, second, third] }
}

public struct Board: Equatable, Hashable, Codable, Sendable {
    public static let stateCount = 19_683
    public static let empty = Board(uncheckedCode: 0)

    public static let winningLines: [WinningLine] = [
        WinningLine(Cell(0), Cell(1), Cell(2)),
        WinningLine(Cell(3), Cell(4), Cell(5)),
        WinningLine(Cell(6), Cell(7), Cell(8)),
        WinningLine(Cell(0), Cell(3), Cell(6)),
        WinningLine(Cell(1), Cell(4), Cell(7)),
        WinningLine(Cell(2), Cell(5), Cell(8)),
        WinningLine(Cell(0), Cell(4), Cell(8)),
        WinningLine(Cell(2), Cell(4), Cell(6)),
    ]

    private static let powers = [1, 3, 9, 27, 81, 243, 729, 2_187, 6_561]

    public let code: Int

    public init(code: Int) throws {
        guard (0..<Self.stateCount).contains(code) else {
            throw DomainValidationError.invalidBoardCode(code)
        }
        self.code = code
    }

    public init(symbols: [Symbol?]) throws {
        guard symbols.count == 9 else {
            throw DomainValidationError.invalidBoardSize(symbols.count)
        }
        var code = 0
        for (index, symbol) in symbols.enumerated() {
            let digit: Int
            switch symbol {
            case .x: digit = 1
            case .o: digit = 2
            case nil: digit = 0
            }
            code += digit * Self.powers[index]
        }
        self.code = code
    }

    private init(uncheckedCode: Int) {
        code = uncheckedCode
    }

    public subscript(cell: Cell) -> Symbol? {
        switch (code / Self.powers[cell.index]) % 3 {
        case 1: .x
        case 2: .o
        default: nil
        }
    }

    public func placing(_ symbol: Symbol, at cell: Cell) throws -> Board {
        guard self[cell] == nil else {
            throw DomainValidationError.occupiedCell(cell.index)
        }
        let digit = symbol == .x ? 1 : 2
        return Board(uncheckedCode: code + digit * Self.powers[cell.index])
    }

    public var occupiedCount: Int {
        var value = code
        var count = 0
        for _ in 0..<9 {
            if value % 3 != 0 { count += 1 }
            value /= 3
        }
        return count
    }

    public var isFull: Bool { occupiedCount == 9 }

    public func count(of symbol: Symbol) -> Int {
        let wanted = symbol == .x ? 1 : 2
        var value = code
        var count = 0
        for _ in 0..<9 {
            if value % 3 == wanted { count += 1 }
            value /= 3
        }
        return count
    }

    public var legalCells: [Cell] {
        Cell.all.filter { self[$0] == nil }
    }

    public func winningLines(for symbol: Symbol) -> [WinningLine] {
        Self.winningLines.filter { line in
            line.cells.allSatisfy { self[$0] == symbol }
        }
    }

    public var hasWinner: Bool {
        !winningLines(for: .x).isEmpty || !winningLines(for: .o).isEmpty
    }

    public var symbols: [Symbol?] {
        Cell.all.map { self[$0] }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let code = try container.decode(Int.self)
        guard (0..<Self.stateCount).contains(code) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: DomainValidationError.invalidBoardCode(code).description
            )
        }
        self.code = code
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(code)
    }
}

public struct TurnToken: Equatable, Hashable, Codable, Sendable {
    /// Leaves room for every turn ordinal in a ten-piece game without Int64 overflow.
    public static let maximumRevision = (Int64.max - 10) / 16

    public let value: Int64

    public init(_ value: Int64) {
        self.value = value
    }

    public static func derived(revision: Int64, ordinal: Int) -> TurnToken? {
        guard (0...maximumRevision).contains(revision), (1...10).contains(ordinal) else {
            return nil
        }
        let (base, multiplyOverflow) = revision.multipliedReportingOverflow(by: 16)
        let (value, addOverflow) = base.addingReportingOverflow(Int64(ordinal))
        guard !multiplyOverflow, !addOverflow else { return nil }
        return TurnToken(value)
    }
}
