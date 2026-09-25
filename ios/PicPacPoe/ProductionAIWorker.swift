import PicPacAI
import PicPacCore
import PicPacPresentation

/// Bridges the presentation boundary to the native production opponents. The
/// policy artifact loads off the main actor while non-policy opponents remain
/// immediately available through an engine with an inert table.
actor ProductionAIWorker: AIWorker {
    private let standardEngine = ProductionAIEngine(policy: .empty)
    private let policyEngine: Task<ProductionAIEngine, any Error>

    init() {
        policyEngine = Task {
            do {
                return try await ProductionAIEngine.loadingBundledPolicy()
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                // Match Android's governed failure path: an unreadable whole
                // policy becomes an empty table, whose agent uses Heuristic.
                return ProductionAIEngine(policy: .empty)
            }
        }
    }

    func chooseMove(
        for observation: AiObservation,
        difficulty: Difficulty
    ) async throws -> Int {
        let engine: ProductionAIEngine
        if difficulty == .qLearning {
            engine = try await policyEngine.value
        } else {
            engine = standardEngine
        }
        let decision = try await engine.chooseMove(
            for: observation,
            opponent: difficulty.opponent
        )
        return decision.cell.index
    }
}

private extension Difficulty {
    var opponent: AIOpponent {
        switch self {
        case .easy: .easy
        case .medium: .medium
        case .hard: .hard
        case .mcts: .mcts
        case .qLearning: .qLearning
        }
    }
}
