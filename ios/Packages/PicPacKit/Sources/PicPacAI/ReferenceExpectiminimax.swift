import PicPacCore

public struct ReferenceSearchAnalysis: Equatable, Sendable {
    public let bestCell: Cell
    public let value: Double
    public let actionValues: [Cell: Double]
    public let nodes: Int
    public let cacheHits: Int

    public init(
        bestCell: Cell,
        value: Double,
        actionValues: [Cell: Double],
        nodes: Int,
        cacheHits: Int
    ) {
        self.bestCell = bestCell
        self.value = value
        self.actionValues = actionValues
        self.nodes = nodes
        self.cacheHits = cacheHits
    }
}

/// A small, deterministic full-tree oracle used to prove the portable game model.
/// Production difficulty agents, limits, cancellation and mutable shared caches are
/// intentionally outside this reference solver's scope.
public struct ReferenceExpectiminimaxSolver: Sendable {
    public init() {}

    public func analyze(_ state: PicPacDecisionState) throws -> ReferenceSearchAnalysis {
        let context = Context()
        var actionValues: [Cell: Double] = [:]
        for cell in Self.ordered(state.legalCells) {
            actionValues[cell] = try context.actionValue(state, cell: cell)
        }
        guard let bestValue = actionValues.values.max(),
              let bestCell = Self.ordered(state.legalCells).first(where: {
                  guard let value = actionValues[$0] else { return false }
                  return abs(value - bestValue) < 1e-12
              }) else {
            throw SearchModelError.invalidState
        }
        return ReferenceSearchAnalysis(
            bestCell: bestCell,
            value: bestValue,
            actionValues: actionValues,
            nodes: context.nodes,
            cacheHits: context.cacheHits
        )
    }

    public static func ordered(_ cells: [Cell]) -> [Cell] {
        let rank = [1, 5, 2, 6, 0, 7, 3, 8, 4]
        return cells.sorted { rank[$0.index] < rank[$1.index] }
    }

    private final class Context {
        var cache: [PicPacDecisionState: Double] = [:]
        var nodes = 0
        var cacheHits = 0

        func value(_ state: PicPacDecisionState) throws -> Double {
            nodes += 1
            if let value = cache[state] {
                cacheHits += 1
                return value
            }
            var best = -Double.infinity
            for cell in ReferenceExpectiminimaxSolver.ordered(state.legalCells) {
                best = max(best, try actionValue(state, cell: cell))
            }
            cache[state] = best
            return best
        }

        func actionValue(_ state: PicPacDecisionState, cell: Cell) throws -> Double {
            switch try PublicPicPacSearchModel.place(state, at: cell) {
            case .win:
                return 1
            case .draw:
                return 0
            case let .chance(chance):
                return try -expectedOpponentValue(chance)
            }
        }

        func expectedOpponentValue(_ chance: PicPacChanceState) throws -> Double {
            let total = Double(chance.total)
            var expected = 0.0
            if chance.remainingX > 0 {
                let state = try PublicPicPacSearchModel.draw(chance, symbol: .x)
                expected += Double(chance.remainingX) / total * (try value(state))
            }
            if chance.remainingO > 0 {
                let state = try PublicPicPacSearchModel.draw(chance, symbol: .o)
                expected += Double(chance.remainingO) / total * (try value(state))
            }
            return expected
        }
    }
}
