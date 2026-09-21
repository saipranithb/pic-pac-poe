import Darwin
import PicPacCore

internal enum PositionEvaluator {
    private static let rank = [1, 5, 2, 6, 0, 7, 3, 8, 4]

    static func ordered(_ cells: [Cell]) -> [Cell] {
        cells.sorted { rank[$0.index] < rank[$1.index] }
    }

    static func boundedValue(_ state: PicPacDecisionState) throws -> Double {
        var best = -Double.infinity
        for cell in ordered(state.legalCells) {
            best = max(best, try actionScore(state, cell: cell))
        }
        return min(max(tanh(best / 260), -0.95), 0.95)
    }

    static func actionScore(_ state: PicPacDecisionState, cell: Cell) throws -> Double {
        let board = try state.board.placing(state.heldSymbol, at: cell)
        if !board.winningLines(for: state.heldSymbol).isEmpty { return 100_000 }
        if board.isFull { return 0 }

        var score = geometry(board, focus: state.heldSymbol) * 22
        score += cell.index == 4 ? 22 : (cell.index.isMultiple(of: 2) ? 10 : 4)

        let total = Double(state.remainingX + state.remainingO)
        if state.remainingX > 0 {
            score -= Double(state.remainingX) / total
                * (try immediateThreatCost(board: board, held: .x))
        }
        if state.remainingO > 0 {
            score -= Double(state.remainingO) / total
                * (try immediateThreatCost(board: board, held: .o))
        }
        return score
    }

    private static func immediateThreatCost(board: Board, held: Symbol) throws -> Double {
        var winningMoves = 0
        for cell in board.legalCells {
            let placed = try board.placing(held, at: cell)
            if !placed.winningLines(for: held).isEmpty {
                winningMoves += 1
            }
        }
        switch winningMoves {
        case 0: return 0
        case 1: return 520
        default: return 760 + Double(winningMoves - 2) * 80
        }
    }

    private static func geometry(_ board: Board, focus: Symbol) -> Double {
        var score = 0.0
        for line in Board.winningLines {
            var focusCount = 0
            var otherCount = 0
            for cell in line.cells {
                if board[cell] == focus {
                    focusCount += 1
                } else if board[cell] == focus.other {
                    otherCount += 1
                }
            }
            let empty = 3 - focusCount - otherCount
            switch (focusCount, otherCount, empty) {
            case (2, _, 1): score += 12
            case (1, _, 2): score += 3
            case (_, 2, 1): score += 4
            case (_, 1, 2): score += 1
            default: break
            }
        }
        return score
    }
}
