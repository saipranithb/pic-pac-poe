import CryptoKit
import Foundation
import XCTest
@testable import PicPacAI
@testable import PicPacCore

final class ProductionAITests: XCTestCase {
    func testEasyTakesEveryImmediateWinAndUsesStableRandomTieBand() async throws {
        for held in Symbol.allCases {
            let board = try Board(symbols: [
                held, held, nil,
                held, held.other, nil,
                nil, held.other, nil,
            ])
            let state = try decisionState(board: board, held: held)
            let agent = HeuristicAgent(
                random: ScriptedBoundedRandomSource(
                    steps: [BoundedRandomStep(upperBound: 2, result: 1)]
                )
            )
            let decision = try await agent.chooseMove(for: observation(state))
            XCTAssertEqual(decision.cell, Cell(6), "held \(held.rawValue)")
        }
    }

    func testRandomAndEasyAreLegalAcrossEveryReachableDecisionState() async throws {
        let states = try reachableDecisionStates()
        XCTAssertEqual(states.count, 21_314)
        let random = RandomAgent(random: ZeroRandomSource())
        let easy = HeuristicAgent(random: ZeroRandomSource())

        for state in states {
            let publicState = try observation(state)
            let randomMove = try await random.chooseMove(for: publicState)
            let easyMove = try await easy.chooseMove(for: publicState)
            XCTAssertTrue(state.legalCells.contains(randomMove.cell))
            XCTAssertTrue(state.legalCells.contains(easyMove.cell))
        }
    }

    func testMediumUsesDepthFourAndStableOpeningChoice() async throws {
        let state = try openingState(held: .x)
        let agent = ExpectiminimaxAgent(configuredDepth: ProductionAIConfiguration.mediumDepth)
        let decision = try await agent.chooseMove(for: observation(state))
        XCTAssertEqual(decision.cell, Cell(4))
        guard case let .search(diagnostics) = decision.diagnostics else {
            return XCTFail("Medium must report search diagnostics")
        }
        XCTAssertEqual(diagnostics.maxDepth, 4)
        XCTAssertEqual(diagnostics.simulations, 0)
    }

    func testHardProductionSolverMatchesExactOpeningOracleAndRetainsCache() async throws {
        let state = try openingState(held: .x)
        let solver = ExpectiminimaxSolver()
        let first = try await solver.analyze(state)
        XCTAssertEqual(first.bestCell, Cell(4))
        XCTAssertEqual(first.value, 5.0 / 21.0, accuracy: 1e-12)
        for cell in Cell.all {
            let expected = cell.index == 4 ? 5.0 / 21.0 : 11.0 / 126.0
            XCTAssertEqual(try XCTUnwrap(first.actionValues[cell]), expected, accuracy: 1e-12)
        }

        let second = try await solver.analyze(state)
        XCTAssertEqual(second.bestCell, first.bestCell)
        XCTAssertEqual(second.actionValues, first.actionValues)
        XCTAssertGreaterThan(second.cacheHits, 0)
        let cachedStateCount = await solver.cachedStateCount
        XCTAssertGreaterThan(cachedStateCount, 0)
    }

    func testExactLimitFallsBackToUnboundedHeuristic() async throws {
        let board = try Board(symbols: [
            .x, .x, nil,
            .o, nil, nil,
            nil, nil, nil,
        ])
        let state = try decisionState(board: board, held: .x)
        let fallback = HeuristicAgent(
            random: ScriptedBoundedRandomSource(
                steps: [BoundedRandomStep(upperBound: 1, result: 0)]
            )
        )
        let agent = ExpectiminimaxAgent(fallback: fallback)
        let decision = try await agent.chooseMove(
            for: observation(state),
            limits: SearchLimits(nodeBudget: 1)
        )
        XCTAssertEqual(decision, AiDecision(cell: Cell(2)))
    }

    func testHardSearchObservesCancellation() async throws {
        let state = try openingState(held: .o)
        let publicState = try observation(state)
        let agent = ExpectiminimaxAgent()
        let search = Task {
            try await agent.chooseMove(for: publicState)
        }
        search.cancel()
        do {
            _ = try await search.value
            XCTFail("Cancelled search returned a move")
        } catch is CancellationError {
            // Expected cooperative cancellation.
        }
    }

    func testMCTSIsRepeatableBoundedAndProductionBudgetIsTwoThousand() async throws {
        let observation = try observation(openingState(held: .x))
        let first = StochasticMCTSAgent(
            defaultSimulations: 400,
            random: SplitMix64RandomSource(seed: 77)
        )
        let second = StochasticMCTSAgent(
            defaultSimulations: 400,
            random: SplitMix64RandomSource(seed: 77)
        )
        let firstDecision = try await first.chooseMove(for: observation)
        let secondDecision = try await second.chooseMove(for: observation)
        XCTAssertEqual(firstDecision.cell, secondDecision.cell)
        XCTAssertTrue(observation.legalCells.contains(firstDecision.cell))
        guard case let .search(firstDiagnostics) = firstDecision.diagnostics,
              case let .search(secondDiagnostics) = secondDecision.diagnostics else {
            return XCTFail("MCTS must report search diagnostics")
        }
        XCTAssertEqual(firstDiagnostics.simulations, 400)
        XCTAssertEqual(secondDiagnostics.simulations, 400)
        XCTAssertGreaterThan(firstDiagnostics.nodes, 1)

        let production = StochasticMCTSAgent(
            defaultSimulations: ProductionAIConfiguration.mctsSimulations,
            random: SplitMix64RandomSource(seed: 7331)
        )
        let productionDecision = try await production.chooseMove(for: observation)
        guard case let .search(productionDiagnostics) = productionDecision.diagnostics else {
            return XCTFail("MCTS must report production diagnostics")
        }
        XCTAssertEqual(productionDiagnostics.simulations, 2_000)
    }

    func testMCTSScriptedCallsProveExpansionChanceAndRolloutBounds() async throws {
        let board = try Board(symbols: [
            .x, .o, .x,
            .x, .o, .o,
            .o, nil, nil,
        ])
        let state = try decisionState(board: board, held: .x)
        let agent = StochasticMCTSAgent(
            defaultSimulations: 1,
            random: ScriptedBoundedRandomSource(
                steps: [
                    BoundedRandomStep(upperBound: 2, result: 0),
                    BoundedRandomStep(upperBound: 2, result: 0),
                    BoundedRandomStep(upperBound: 1, result: 0),
                ]
            )
        )
        let decision = try await agent.chooseMove(for: observation(state))
        XCTAssertEqual(decision.cell, Cell(7))
        guard case let .search(diagnostics) = decision.diagnostics else {
            return XCTFail("MCTS must report diagnostics")
        }
        XCTAssertEqual(diagnostics.simulations, 1)
        XCTAssertEqual(diagnostics.nodes, 3)
    }

    func testMCTSExpiredDeadlineUsesFirstRowMajorLegalFallback() async throws {
        let state = try openingState(held: .x)
        let agent = StochasticMCTSAgent(
            defaultSimulations: 2_000,
            random: SplitMix64RandomSource(seed: 1)
        )
        let decision = try await agent.chooseMove(
            for: observation(state),
            limits: SearchLimits(deadline: ContinuousClock().now)
        )
        XCTAssertEqual(decision, AiDecision(cell: Cell(0)))
    }

    func testBundledPolicyMatchesCanonicalArtifactAndKnownOpeningRow() async throws {
        let bundled = try TabularPolicy.bundledArtifactData()
        let canonical = try Data(contentsOf: XCTUnwrap(canonicalPolicyURL()))
        XCTAssertEqual(bundled, canonical)
        XCTAssertEqual(bundled.count, TabularPolicy.artifactByteCount)
        let digest = SHA256.hash(data: bundled).map { String(format: "%02x", $0) }.joined()
        XCTAssertEqual(digest, TabularPolicy.artifactSHA256)

        let policy = try await TabularPolicy.loadBundled()
        XCTAssertEqual(policy.stateCount, 20_266)
        let opening = try openingState(held: .x)
        XCTAssertEqual(policy.value(opening, at: Cell(4)), 0.23589475, accuracy: 1e-7)
        XCTAssertEqual(policy.bestCell(for: opening), Cell(4))
    }

    func testPolicyFormatRoundTripsAndRejectsIncompatibleArtifacts() throws {
        let state = try openingState(held: .x)
        var row = [Float](repeating: 0, count: 9)
        row[4] = 0.75
        let policy = TabularPolicy(rows: [TabularPolicy.key(for: state): row])
        let encoded = policy.encoded()
        let restored = try TabularPolicy(data: encoded)
        XCTAssertEqual(restored.stateCount, 1)
        XCTAssertEqual(restored.value(state, at: Cell(4)), 0.75, accuracy: 1e-6)
        XCTAssertEqual(restored.bestCell(for: state), Cell(4))

        var badMagic = encoded
        badMagic[0] = 0
        XCTAssertThrowsError(try TabularPolicy(data: badMagic)) {
            guard case .invalidMagic = $0 as? TabularPolicyError else {
                return XCTFail("Expected invalid magic, got \($0)")
            }
        }
        var badVersion = encoded
        badVersion[7] = 2
        XCTAssertThrowsError(try TabularPolicy(data: badVersion)) {
            XCTAssertEqual($0 as? TabularPolicyError, .unsupportedVersion(2))
        }
        XCTAssertThrowsError(try TabularPolicy(data: encoded.dropLast())) {
            XCTAssertEqual($0 as? TabularPolicyError, .truncated)
        }
    }

    func testPolicyAlwaysMasksIllegalCellsAcrossReachableGraph() async throws {
        let policy = try await TabularPolicy.loadBundled()
        let states = try reachableDecisionStates()
        for state in states {
            XCTAssertTrue(state.legalCells.contains(policy.bestCell(for: state)))
        }
    }

    func testPolicyFallbackOnlyAppliesWhenWholeTableIsEmpty() async throws {
        let winningBoard = try Board(symbols: [
            .x, .x, nil,
            .o, nil, nil,
            nil, nil, nil,
        ])
        let winningState = try decisionState(board: winningBoard, held: .x)
        let emptyAgent = RlPolicyAgent(
            policy: .empty,
            fallback: HeuristicAgent(
                random: ScriptedBoundedRandomSource(
                    steps: [BoundedRandomStep(upperBound: 1, result: 0)]
                )
            )
        )
        let emptyDecision = try await emptyAgent.chooseMove(for: observation(winningState))
        XCTAssertEqual(emptyDecision.cell, Cell(2))

        let opening = try openingState(held: .x)
        var row = [Float](repeating: 0, count: 9)
        row[4] = 1
        let nonempty = TabularPolicy(rows: [TabularPolicy.key(for: opening): row])
        let missingBoard = try Board(symbols: [
            .x, nil, nil,
            nil, .o, nil,
            nil, nil, nil,
        ])
        let missingState = try decisionState(board: missingBoard, held: .x)
        let missingAgent = RlPolicyAgent(policy: nonempty)
        let missingDecision = try await missingAgent.chooseMove(for: observation(missingState))
        XCTAssertEqual(missingDecision.cell, PositionEvaluator.ordered(missingState.legalCells)[0])
        XCTAssertEqual(missingDecision.cell, Cell(2))
    }

    func testProductionEngineMapsEveryOpponentToItsGovernedConfiguration() async throws {
        let policy = try await TabularPolicy.loadBundled()
        let engine = ProductionAIEngine(
            policy: policy,
            easyRandom: ScriptedBoundedRandomSource(
                steps: [BoundedRandomStep(upperBound: 1, result: 0)]
            ),
            mctsRandom: SplitMix64RandomSource(seed: 42)
        )
        let opening = try observation(openingState(held: .x))
        let medium = try await engine.chooseMove(for: opening, opponent: .medium)
        XCTAssertEqual(medium.cell, Cell(4))
        let qLearning = try await engine.chooseMove(for: opening, opponent: .qLearning)
        XCTAssertEqual(qLearning.cell, Cell(4))
        let mcts = try await engine.chooseMove(for: opening, opponent: .mcts)
        guard case let .search(diagnostics) = mcts.diagnostics else {
            return XCTFail("MCTS must report diagnostics")
        }
        XCTAssertEqual(diagnostics.simulations, 2_000)
        XCTAssertEqual(ProductionAIConfiguration.mediumDepth, 4)
        XCTAssertEqual(ProductionAIConfiguration.mctsSimulations, 2_000)
    }

    private func openingState(held: Symbol) throws -> PicPacDecisionState {
        try PicPacDecisionState(
            board: .empty,
            heldSymbol: held,
            remainingX: held == .x ? 4 : 5,
            remainingO: held == .o ? 4 : 5
        )
    }

    private func decisionState(board: Board, held: Symbol) throws -> PicPacDecisionState {
        try PicPacDecisionState(
            board: board,
            heldSymbol: held,
            remainingX: 5 - board.count(of: .x) - (held == .x ? 1 : 0),
            remainingO: 5 - board.count(of: .o) - (held == .o ? 1 : 0)
        )
    }

    private func observation(_ state: PicPacDecisionState) throws -> AiObservation {
        try AiObservation(
            board: state.board,
            activePlayer: .one,
            agentPlayer: .one,
            heldSymbol: state.heldSymbol,
            remainingX: state.remainingX,
            remainingO: state.remainingO
        )
    }

    private func reachableDecisionStates() throws -> Set<PicPacDecisionState> {
        var chanceSeen = Set<PicPacChanceState>()
        var decisions = Set<PicPacDecisionState>()
        var visitChance: ((PicPacChanceState) throws -> Void)!
        var visitDecision: ((PicPacDecisionState) throws -> Void)!

        visitChance = { chance in
            guard chanceSeen.insert(chance).inserted else { return }
            if chance.remainingX > 0 {
                try visitDecision(PublicPicPacSearchModel.draw(chance, symbol: .x))
            }
            if chance.remainingO > 0 {
                try visitDecision(PublicPicPacSearchModel.draw(chance, symbol: .o))
            }
        }
        visitDecision = { state in
            guard decisions.insert(state).inserted else { return }
            for cell in state.legalCells {
                if case let .chance(chance) = try PublicPicPacSearchModel.place(state, at: cell) {
                    try visitChance(chance)
                }
            }
        }
        try visitChance(PicPacChanceState(board: .empty, remainingX: 5, remainingO: 5))
        return decisions
    }

    private func canonicalPolicyURL() -> URL? {
        var directory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
        while directory.path != "/" {
            let candidate = directory.appendingPathComponent(
                "app/src/main/res/raw/picpac_rl_policy_v1.bin"
            )
            if FileManager.default.fileExists(atPath: candidate.path) { return candidate }
            directory.deleteLastPathComponent()
        }
        return nil
    }
}

private struct ZeroRandomSource: BoundedRandomSource {
    mutating func nextInt(upperBound: Int) throws -> Int {
        guard upperBound > 0 else {
            throw BoundedRandomError.invalidUpperBound(upperBound)
        }
        return 0
    }
}
