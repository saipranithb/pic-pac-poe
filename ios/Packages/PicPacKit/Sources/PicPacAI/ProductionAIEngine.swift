import PicPacCore

public enum AIOpponent: String, CaseIterable, Sendable {
    case easy
    case medium
    case hard
    case mcts
    case qLearning
}

public enum ProductionAIConfiguration {
    public static let mediumDepth = 4
    public static let mctsSimulations = 2_000
}

/// Owns independent algorithm RNGs and isolated long-lived exact-search caches.
/// It accepts only the public observation, never a game session or draw source.
public actor ProductionAIEngine {
    private let easy: HeuristicAgent
    private let medium: ExpectiminimaxAgent
    private let hard: ExpectiminimaxAgent
    private let mcts: StochasticMCTSAgent
    private let qLearning: RlPolicyAgent

    public init(policy: TabularPolicy) {
        easy = HeuristicAgent()
        medium = ExpectiminimaxAgent(configuredDepth: ProductionAIConfiguration.mediumDepth)
        hard = ExpectiminimaxAgent()
        mcts = StochasticMCTSAgent(
            defaultSimulations: ProductionAIConfiguration.mctsSimulations
        )
        qLearning = RlPolicyAgent(policy: policy)
    }

    public init<EasyRandom: BoundedRandomSource, MCTSRandom: BoundedRandomSource>(
        policy: TabularPolicy,
        easyRandom: EasyRandom,
        mctsRandom: MCTSRandom
    ) {
        easy = HeuristicAgent(random: easyRandom)
        medium = ExpectiminimaxAgent(configuredDepth: ProductionAIConfiguration.mediumDepth)
        hard = ExpectiminimaxAgent()
        mcts = StochasticMCTSAgent(
            defaultSimulations: ProductionAIConfiguration.mctsSimulations,
            random: mctsRandom
        )
        qLearning = RlPolicyAgent(policy: policy)
    }

    public static func loadingBundledPolicy() async throws -> ProductionAIEngine {
        ProductionAIEngine(policy: try await TabularPolicy.loadBundled())
    }

    public func chooseMove(
        for observation: AiObservation,
        opponent: AIOpponent,
        limits: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        switch opponent {
        case .easy:
            try await easy.chooseMove(for: observation, limits: limits)
        case .medium:
            try await medium.chooseMove(for: observation, limits: limits)
        case .hard:
            try await hard.chooseMove(for: observation, limits: limits)
        case .mcts:
            try await mcts.chooseMove(for: observation, limits: limits)
        case .qLearning:
            try await qLearning.chooseMove(for: observation, limits: limits)
        }
    }
}
