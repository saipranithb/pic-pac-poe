import XCTest
@testable import PicPacAI
@testable import PicPacCore

final class SearchAndGraphTests: XCTestCase {
    func testCanonicalPhaseAwareGraphCounts() throws {
        var chanceStates = Set<ChanceKey>()
        var decisionStates = Set<DecisionKey>()
        var terminalStates = Set<TerminalKey>()

        var visitChance: ((PicPacChanceState) throws -> Void)!
        var visitDecision: ((PicPacDecisionState) throws -> Void)!

        visitChance = { state in
            let key = ChanceKey(board: state.board.code, x: state.remainingX, o: state.remainingO)
            guard chanceStates.insert(key).inserted else { return }
            if state.remainingX > 0 {
                try visitDecision(PublicPicPacSearchModel.draw(state, symbol: .x))
            }
            if state.remainingO > 0 {
                try visitDecision(PublicPicPacSearchModel.draw(state, symbol: .o))
            }
        }

        visitDecision = { state in
            let key = DecisionKey(
                board: state.board.code,
                held: state.heldSymbol,
                x: state.remainingX,
                o: state.remainingO
            )
            guard decisionStates.insert(key).inserted else { return }
            for cell in state.legalCells {
                let placed = try state.board.placing(state.heldSymbol, at: cell)
                switch try PublicPicPacSearchModel.place(state, at: cell) {
                case .win:
                    terminalStates.insert(
                        TerminalKey(
                            board: placed.code,
                            x: state.remainingX,
                            o: state.remainingO,
                            win: true
                        )
                    )
                case .draw:
                    terminalStates.insert(
                        TerminalKey(
                            board: placed.code,
                            x: state.remainingX,
                            o: state.remainingO,
                            win: false
                        )
                    )
                case let .chance(chance):
                    try visitChance(chance)
                }
            }
        }

        try visitChance(PicPacChanceState(board: .empty, remainingX: 5, remainingO: 5))

        XCTAssertEqual(chanceStates.count, 11_065)
        XCTAssertEqual(decisionStates.count, 21_314)
        XCTAssertEqual(terminalStates.count, 6_648)
        XCTAssertEqual(chanceStates.count + decisionStates.count + terminalStates.count, 39_027)
    }

    func testOpeningOracleForBothHeldSymbols() throws {
        for held in Symbol.allCases {
            let state = try PicPacDecisionState(
                board: .empty,
                heldSymbol: held,
                remainingX: held == .x ? 4 : 5,
                remainingO: held == .o ? 4 : 5
            )
            let result = try ReferenceExpectiminimaxSolver().analyze(state)
            XCTAssertEqual(result.bestCell, Cell(4), "held \(held.rawValue)")
            XCTAssertEqual(result.value, 5.0 / 21.0, accuracy: 1e-12)
            for cell in Cell.all {
                let expected = cell.index == 4 ? 5.0 / 21.0 : 11.0 / 126.0
                XCTAssertEqual(try XCTUnwrap(result.actionValues[cell]), expected, accuracy: 1e-12)
            }
        }
    }

    func testScriptedBoundedRandomnessChecksBoundsAndCallOrder() throws {
        var random = ScriptedBoundedRandomSource(
            steps: [
                BoundedRandomStep(upperBound: 10, result: 4),
                BoundedRandomStep(upperBound: 9, result: 8),
            ]
        )
        var state = try accepted(try PicPacRules.draw(PicPacRules.newGame(), using: &random))
        XCTAssertEqual(state.phase.heldSymbol, .x)
        state = try accepted(PicPacRules.place(state, at: Cell(4), token: try XCTUnwrap(state.phase.turnToken)))
        state = try accepted(try PicPacRules.draw(state, using: &random))
        XCTAssertEqual(state.phase.heldSymbol, .o)
        XCTAssertEqual(random.consumedCount, 2)

        var wrongBound = ScriptedBoundedRandomSource(
            steps: [BoundedRandomStep(upperBound: 9, result: 0)]
        )
        XCTAssertThrowsError(try PicPacRules.draw(PicPacRules.newGame(), using: &wrongBound)) {
            XCTAssertEqual(
                $0 as? BoundedRandomError,
                .unexpectedBound(expected: 9, actual: 10)
            )
        }
    }

    private func accepted<State>(_ result: TransitionResult<State>) throws -> State {
        switch result {
        case let .accepted(state, _): return state
        case let .rejected(reason): throw TestError.rejected(reason)
        }
    }

    private enum TestError: Error { case rejected(RejectionReason) }
}

private struct ChanceKey: Hashable {
    let board: Int
    let x: Int
    let o: Int
}

private struct DecisionKey: Hashable {
    let board: Int
    let held: Symbol
    let x: Int
    let o: Int
}

private struct TerminalKey: Hashable {
    let board: Int
    let x: Int
    let o: Int
    let win: Bool
}
