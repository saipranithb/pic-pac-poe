import Darwin
import PicPacCore

public actor StochasticMCTSAgent: AiAgent {
    public static let defaultSimulations = 1_000
    public static let productionSimulations = 2_000

    private let simulations: Int
    private let exploration: Double
    private var random: any BoundedRandomSource

    public init(
        defaultSimulations: Int = StochasticMCTSAgent.defaultSimulations,
        exploration: Double = sqrt(2)
    ) {
        precondition(defaultSimulations > 0)
        precondition(exploration >= 0 && exploration.isFinite)
        simulations = defaultSimulations
        self.exploration = exploration
        random = SystemAIRandomSource()
    }

    public init<Random: BoundedRandomSource>(
        defaultSimulations: Int = StochasticMCTSAgent.defaultSimulations,
        exploration: Double = sqrt(2),
        random: Random
    ) {
        precondition(defaultSimulations > 0)
        precondition(exploration >= 0 && exploration.isFinite)
        simulations = defaultSimulations
        self.exploration = exploration
        self.random = random
    }

    public func chooseMove(
        for observation: AiObservation,
        limits: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        let rootState = PublicPicPacSearchModel.from(observation)
        let root = DecisionNode(state: rootState, rootSign: 1)
        let simulationBudget = limits.nodeBudget ?? simulations
        var completed = 0
        var createdNodes = 1
        var maxDepth = 0
        let clock = ContinuousClock()
        let start = clock.now

        for _ in 0..<simulationBudget {
            try Task.checkCancellation()
            if limits.isPastDeadline { break }
            let result = try simulate(root)
            createdNodes += result.created
            maxDepth = max(maxDepth, result.depth)
            completed += 1
        }

        guard let best = bestFinalEdge(root) else {
            return AiDecision(cell: rootState.legalCells[0])
        }
        return AiDecision(
            cell: best.cell,
            diagnostics: .search(
                SearchDiagnostics(
                    nodes: createdNodes,
                    cacheHits: 0,
                    maxDepth: maxDepth,
                    elapsedNanoseconds: elapsedNanoseconds(start.duration(to: clock.now)),
                    value: best.mean,
                    simulations: completed
                )
            )
        )
    }

    private func simulate(_ root: DecisionNode) throws -> SimulationResult {
        var path: [Stats] = [root]
        var node = root
        var depth = 0
        var created = 0

        while true {
            depth += 1
            let edge: ActionEdge
            if !node.untried.isEmpty {
                let index = try random.nextInt(upperBound: node.untried.count)
                let cell = node.untried.remove(at: index)
                edge = ActionEdge(cell: cell)
                node.edges[cell] = edge
                node.edgeOrder.append(cell)
                created += 1
            } else {
                edge = select(node)
            }
            path.append(edge)

            switch try PublicPicPacSearchModel.place(node.state, at: edge.cell) {
            case .win:
                return finish(
                    path: path,
                    reward: Double(node.rootSign),
                    depth: depth,
                    created: created
                )
            case .draw:
                return finish(path: path, reward: 0, depth: depth, created: created)
            case let .chance(chance):
                let symbol = try sample(chance)
                if let child = edge.children[symbol] {
                    node = child
                    path.append(child)
                } else {
                    let next = DecisionNode(
                        state: try PublicPicPacSearchModel.draw(chance, symbol: symbol),
                        rootSign: -node.rootSign
                    )
                    edge.children[symbol] = next
                    path.append(next)
                    created += 1
                    let rollout = try rollout(
                        initial: next.state,
                        initialSign: next.rootSign,
                        startingDepth: depth + 1
                    )
                    return finish(
                        path: path,
                        reward: rollout.reward,
                        depth: rollout.depth,
                        created: created
                    )
                }
            }
        }
    }

    private func select(_ node: DecisionNode) -> ActionEdge {
        let logParent = log(Double(max(node.visits, 1)))
        var selected: ActionEdge?
        var selectedValue = -Double.infinity
        for cell in node.edgeOrder {
            guard let edge = node.edges[cell] else { continue }
            let value = edge.visits == 0
                ? Double.infinity
                : Double(node.rootSign) * edge.mean
                    + exploration * sqrt(logParent / Double(edge.visits))
            if value > selectedValue {
                selected = edge
                selectedValue = value
            }
        }
        return selected!
    }

    private func rollout(
        initial: PicPacDecisionState,
        initialSign: Int,
        startingDepth: Int
    ) throws -> Rollout {
        var state = initial
        var sign = initialSign
        var depth = startingDepth

        while true {
            let winning = try state.legalCells.filter {
                try PublicPicPacSearchModel.place(state, at: $0) == .win
            }
            let candidates = winning.isEmpty ? state.legalCells : winning
            let cell = candidates[try random.nextInt(upperBound: candidates.count)]
            switch try PublicPicPacSearchModel.place(state, at: cell) {
            case .win:
                return Rollout(reward: Double(sign), depth: depth)
            case .draw:
                return Rollout(reward: 0, depth: depth)
            case let .chance(chance):
                state = try PublicPicPacSearchModel.draw(chance, symbol: sample(chance))
                sign = -sign
                depth += 1
            }
        }
    }

    private func sample(_ chance: PicPacChanceState) throws -> Symbol {
        try random.nextInt(upperBound: chance.total) < chance.remainingX ? .x : .o
    }

    private func finish(
        path: [Stats],
        reward: Double,
        depth: Int,
        created: Int
    ) -> SimulationResult {
        for stats in path {
            stats.visits += 1
            stats.valueSum += reward
        }
        return SimulationResult(depth: depth, created: created)
    }

    private func bestFinalEdge(_ root: DecisionNode) -> ActionEdge? {
        var best: ActionEdge?
        for cell in root.edgeOrder {
            guard let edge = root.edges[cell] else { continue }
            guard let current = best else {
                best = edge
                continue
            }
            if edge.visits > current.visits
                || (edge.visits == current.visits && edge.mean > current.mean)
                || (edge.visits == current.visits
                    && edge.mean == current.mean
                    && moveRank(edge.cell) < moveRank(current.cell)) {
                best = edge
            }
        }
        return best
    }

    private func moveRank(_ cell: Cell) -> Int {
        switch cell.index {
        case 4: 0
        case 0: 1
        case 2: 2
        case 6: 3
        case 8: 4
        default: 5 + cell.index
        }
    }
}

private class Stats {
    var visits = 0
    var valueSum = 0.0
}

private final class DecisionNode: Stats {
    let state: PicPacDecisionState
    let rootSign: Int
    var untried: [Cell]
    var edges: [Cell: ActionEdge] = [:]
    var edgeOrder: [Cell] = []

    init(state: PicPacDecisionState, rootSign: Int) {
        self.state = state
        self.rootSign = rootSign
        untried = state.legalCells
    }
}

private final class ActionEdge: Stats {
    let cell: Cell
    var children: [Symbol: DecisionNode] = [:]
    var mean: Double { visits == 0 ? 0 : valueSum / Double(visits) }

    init(cell: Cell) {
        self.cell = cell
    }
}

private struct Rollout {
    let reward: Double
    let depth: Int
}

private struct SimulationResult {
    let depth: Int
    let created: Int
}
