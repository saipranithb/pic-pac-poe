import PicPacCore

public actor RandomAgent: AiAgent {
    private var random: any BoundedRandomSource

    public init() {
        random = SystemAIRandomSource()
    }

    public init<Random: BoundedRandomSource>(random: Random) {
        self.random = random
    }

    public func chooseMove(
        for observation: AiObservation,
        limits _: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        try Task.checkCancellation()
        let legal = observation.legalCells
        let index = try random.nextInt(upperBound: legal.count)
        return AiDecision(cell: legal[index])
    }
}

public actor HeuristicAgent: AiAgent {
    private var random: any BoundedRandomSource

    public init() {
        random = SystemAIRandomSource()
    }

    public init<Random: BoundedRandomSource>(random: Random) {
        self.random = random
    }

    public func chooseMove(
        for observation: AiObservation,
        limits _: SearchLimits = SearchLimits()
    ) async throws -> AiDecision {
        try Task.checkCancellation()
        let state = PublicPicPacSearchModel.from(observation)
        let ordered = PositionEvaluator.ordered(state.legalCells)
        let immediate = try ordered.filter {
            try PublicPicPacSearchModel.place(state, at: $0) == .win
        }
        if !immediate.isEmpty {
            let index = try random.nextInt(upperBound: immediate.count)
            return AiDecision(cell: immediate[index])
        }

        let scored = try ordered.map { ($0, try PositionEvaluator.actionScore(state, cell: $0)) }
        guard let best = scored.map(\.1).max() else {
            throw SearchModelError.invalidState
        }
        let topBand = scored.filter { $0.1 >= best - 8 }
        let index = try random.nextInt(upperBound: topBand.count)
        return AiDecision(cell: topBand[index].0)
    }
}
