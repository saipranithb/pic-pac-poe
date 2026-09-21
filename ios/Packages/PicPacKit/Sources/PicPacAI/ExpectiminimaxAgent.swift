import PicPacCore

public struct SearchAnalysis: Equatable, Sendable {
    public let bestCell: Cell
    public let value: Double
    public let actionValues: [Cell: Double]
    public let nodes: Int
    public let cacheHits: Int
    public let reachedDepth: Int

    public init(
        bestCell: Cell,
        value: Double,
        actionValues: [Cell: Double],
        nodes: Int,
        cacheHits: Int,
        reachedDepth: Int
    ) {
        self.bestCell = bestCell
        self.value = value
        self.actionValues = actionValues
        self.nodes = nodes
        self.cacheHits = cacheHits
        self.reachedDepth = reachedDepth
    }
}

public actor ExpectiminimaxSolver {
    private var cache: [UInt64: Double] = [:]
    private var nodes = 0
    private var cacheHits = 0
    private var deepest = 0
    private var limits = SearchLimits()

    public init() {
        cache.reserveCapacity(40_000)
    }

    public var cachedStateCount: Int { cache.count }

    public func clearCache() {
        cache.removeAll(keepingCapacity: true)
    }

    public func analyze(
        _ state: PicPacDecisionState,
        maxDepth: Int = .max,
        limits: SearchLimits = SearchLimits()
    ) throws -> SearchAnalysis {
        precondition(maxDepth > 0)
        try Task.checkCancellation()
        nodes = 0
        cacheHits = 0
        deepest = 0
        self.limits = limits

        let depth = min(maxDepth, state.legalCells.count)
        var values: [Cell: Double] = [:]
        values.reserveCapacity(state.legalCells.count)
        for cell in PositionEvaluator.ordered(state.legalCells) {
            try Task.checkCancellation()
            values[cell] = try actionValue(state, cell: cell, depth: depth, ply: 0)
        }
        guard let bestValue = values.values.max(),
              let bestCell = PositionEvaluator.ordered(state.legalCells).first(where: {
                  guard let value = values[$0] else { return false }
                  return abs(value - bestValue) < 1e-12
              }) else {
            throw SearchModelError.invalidState
        }
        return SearchAnalysis(
            bestCell: bestCell,
            value: bestValue,
            actionValues: values,
            nodes: nodes,
            cacheHits: cacheHits,
            reachedDepth: deepest
        )
    }

    private func value(_ state: PicPacDecisionState, depth: Int, ply: Int) throws -> Double {
        try checkpoint(ply: ply)
        if depth == 0 { return try PositionEvaluator.boundedValue(state) }

        let cacheKey = key(state, depth: depth)
        if let cached = cache[cacheKey] {
            cacheHits += 1
            return cached
        }

        var best = -Double.infinity
        for cell in PositionEvaluator.ordered(state.legalCells) {
            best = max(best, try actionValue(state, cell: cell, depth: depth, ply: ply))
        }
        cache[cacheKey] = best
        return best
    }

    private func actionValue(
        _ state: PicPacDecisionState,
        cell: Cell,
        depth: Int,
        ply: Int
    ) throws -> Double {
        switch try PublicPicPacSearchModel.place(state, at: cell) {
        case .win:
            return 1
        case .draw:
            return 0
        case let .chance(chance):
            return try -expectedOpponentValue(chance, depth: depth - 1, ply: ply + 1)
        }
    }

    private func expectedOpponentValue(
        _ chance: PicPacChanceState,
        depth: Int,
        ply: Int
    ) throws -> Double {
        let total = Double(chance.total)
        var expected = 0.0
        if chance.remainingX > 0 {
            let next = try PublicPicPacSearchModel.draw(chance, symbol: .x)
            expected += Double(chance.remainingX) / total * (try value(next, depth: depth, ply: ply))
        }
        if chance.remainingO > 0 {
            let next = try PublicPicPacSearchModel.draw(chance, symbol: .o)
            expected += Double(chance.remainingO) / total * (try value(next, depth: depth, ply: ply))
        }
        return expected
    }

    private func checkpoint(ply: Int) throws {
        nodes += 1
        deepest = max(deepest, ply)
        if nodes & 255 == 0 { try Task.checkCancellation() }
        if let nodeBudget = limits.nodeBudget, nodes > nodeBudget {
            throw SearchLimitReached()
        }
        if limits.isPastDeadline {
            throw SearchLimitReached()
        }
    }

    private func key(_ state: PicPacDecisionState, depth: Int) -> UInt64 {
        let held: UInt64 = state.heldSymbol == .x ? 0 : 1
        return UInt64(state.board.code)
            | (held << 15)
            | (UInt64(state.remainingX) << 16)
            | (UInt64(state.remainingO) << 19)
            | (UInt64(depth) << 22)
    }
}

public actor ExpectiminimaxAgent: AiAgent {
    private let configuredDepth: Int
    private let solver: ExpectiminimaxSolver
    private let fallback: any AiAgent

    public init(
        configuredDepth: Int = .max,
        solver: ExpectiminimaxSolver = ExpectiminimaxSolver(),
        fallback: any AiAgent = HeuristicAgent()
    ) {
        precondition(configuredDepth > 0)
        self.configuredDepth = configuredDepth
        self.solver = solver
        self.fallback = fallback
    }

    public func chooseMove(
        for observation: AiObservation,
        limits: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        let state = PublicPicPacSearchModel.from(observation)
        let clock = ContinuousClock()
        let start = clock.now
        let analysis: SearchAnalysis
        do {
            analysis = try await solver.analyze(
                state,
                maxDepth: limits.maxDepth ?? configuredDepth,
                limits: limits
            )
        } catch is SearchLimitReached {
            return try await fallback.chooseMove(
                for: observation,
                limits: limits.withoutResourceBounds
            )
        }
        return AiDecision(
            cell: analysis.bestCell,
            diagnostics: .search(
                SearchDiagnostics(
                    nodes: analysis.nodes,
                    cacheHits: analysis.cacheHits,
                    maxDepth: analysis.reachedDepth,
                    elapsedNanoseconds: elapsedNanoseconds(start.duration(to: clock.now)),
                    value: analysis.value
                )
            )
        )
    }
}

private struct SearchLimitReached: Error {}
