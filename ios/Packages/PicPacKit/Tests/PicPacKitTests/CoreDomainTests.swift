import XCTest
@testable import PicPacCore

final class CoreDomainTests: XCTestCase {
    func testAllBase3BoardCodesRoundTrip() throws {
        for code in 0..<Board.stateCount {
            let board = try Board(code: code)
            XCTAssertEqual(try Board(symbols: board.symbols), board, "code \(code)")
        }
    }

    func testBoardEncodingUsesRowMajorLeastSignificantCellOrder() throws {
        let board = try Board(symbols: [.x, .o, nil, nil, nil, nil, nil, nil, .x])
        XCTAssertEqual(board.code, 1 + 2 * 3 + 6_561)
        XCTAssertEqual(try Board(code: 1)[Cell(0)], .x)
        XCTAssertEqual(try Board(code: 6_561)[Cell(8)], .x)
    }

    func testAllEightWinningLinesRemainInStableOrder() throws {
        for line in Board.winningLines {
            var board = Board.empty
            for cell in line.cells {
                board = try board.placing(.x, at: cell)
            }
            XCTAssertEqual(board.winningLines(for: .x), [line])
        }
    }

    func testActorOwnsWinIndependentlyOfSymbol() throws {
        var state = PicPacRules.newGame()
        state = try accepted(PicPacRules.draw(state, symbol: .o))
        state = try accepted(PicPacRules.place(state, at: Cell(0), token: token(in: state)))
        state = try accepted(PicPacRules.draw(state, symbol: .x))
        state = try accepted(PicPacRules.place(state, at: Cell(3), token: token(in: state)))
        state = try accepted(PicPacRules.draw(state, symbol: .o))
        state = try accepted(PicPacRules.place(state, at: Cell(1), token: token(in: state)))
        state = try accepted(PicPacRules.draw(state, symbol: .x))
        state = try accepted(PicPacRules.place(state, at: Cell(4), token: token(in: state)))
        state = try accepted(PicPacRules.draw(state, symbol: .o))
        state = try accepted(PicPacRules.place(state, at: Cell(2), token: token(in: state)))

        guard case let .terminal(.win(win)) = state.phase else {
            return XCTFail("Expected a terminal win")
        }
        XCTAssertEqual(win.player, .one)
        XCTAssertEqual(win.symbol, .o)
    }

    func testNinthMoveWinTakesPrecedenceOverDraw() throws {
        let board = try Board(symbols: [.x, .o, .o, .o, .x, .x, .x, .o, nil])
        let state = try PicPacState(
            board: board,
            activePlayer: .two,
            remainingX: 0,
            remainingO: 1,
            phase: .awaitingPlacement(held: .x, token: TurnToken(9)),
            starter: .two,
            revision: 0
        )
        let result = PicPacRules.place(state, at: Cell(8), token: TurnToken(9))
        let terminal = try accepted(result)
        XCTAssertTrue(terminal.board.isFull)
        guard case let .terminal(.win(win)) = terminal.phase else {
            return XCTFail("A full-board winning placement must be a win")
        }
        XCTAssertEqual(win.player, .two)
        XCTAssertEqual(win.lines, [WinningLine(Cell(0), Cell(4), Cell(8))])
    }

    func testOnePlacementCanRetainMultipleWinningLines() throws {
        let board = try Board(symbols: [.x, nil, .x, nil, nil, nil, .x, nil, .x])
        let state = try PicPacState(
            board: board,
            activePlayer: .one,
            remainingX: 0,
            remainingO: 5,
            phase: .awaitingPlacement(held: .x, token: TurnToken(5)),
            starter: .one,
            revision: 0
        )
        let terminal = try accepted(
            PicPacRules.place(state, at: Cell(4), token: TurnToken(5))
        )
        guard case let .terminal(.win(win)) = terminal.phase else {
            return XCTFail("Expected a terminal win")
        }
        XCTAssertEqual(
            win.lines,
            [
                WinningLine(Cell(0), Cell(4), Cell(8)),
                WinningLine(Cell(2), Cell(4), Cell(6)),
            ]
        )
    }

    func testRejectedTransitionsLeaveValueStateUnchanged() throws {
        let drawn = try accepted(PicPacRules.draw(PicPacRules.newGame(), symbol: .x))
        let original = drawn

        XCTAssertEqual(
            PicPacRules.place(drawn, at: Cell(4), token: TurnToken(999)),
            .rejected(.staleTurn)
        )
        XCTAssertEqual(drawn, original)

        let placed = try accepted(
            PicPacRules.place(drawn, at: Cell(4), token: token(in: drawn))
        )
        let nextDraw = try accepted(PicPacRules.draw(placed, symbol: .o))
        XCTAssertEqual(
            PicPacRules.place(nextDraw, at: Cell(4), token: token(in: nextDraw)),
            .rejected(.occupied)
        )
        XCTAssertEqual(
            PicPacRules.place(placed, at: Cell(0), token: TurnToken(2)),
            .rejected(.wrongPhase)
        )
    }

    func testStateValidationEnforcesBagConservation() throws {
        XCTAssertThrowsError(
            try PicPacState(
                board: .empty,
                activePlayer: .one,
                remainingX: 4,
                remainingO: 5,
                phase: .awaitingDraw,
                starter: .one,
                revision: 0
            )
        ) { error in
            XCTAssertEqual(
                error as? DomainValidationError,
                .conservation(symbol: .x, actual: 4)
            )
        }
    }

    func testCodableRoundTripRevalidatesState() throws {
        let state = try accepted(PicPacRules.draw(PicPacRules.newGame(), symbol: .x))
        let data = try JSONEncoder().encode(state)
        XCTAssertEqual(try JSONDecoder().decode(PicPacState.self, from: data), state)

        XCTAssertThrowsError(try JSONDecoder().decode(Board.self, from: Data("19683".utf8)))
    }

    func testDecodedTerminalStatesRejectSimultaneousWinners() throws {
        let board = try Board(symbols: [
            .x, .x, .x,
            .o, .o, .o,
            nil, nil, nil,
        ])
        let win = GameOutcome.Win(
            player: .two,
            symbol: .o,
            lines: board.winningLines(for: .o)
        )

        let picPacData = try JSONEncoder().encode(
            UncheckedPicPacState(
                board: board,
                activePlayer: .two,
                remainingX: 2,
                remainingO: 2,
                phase: .terminal(outcome: .win(win)),
                starter: .one,
                revision: 0
            )
        )
        XCTAssertThrowsError(try JSONDecoder().decode(PicPacState.self, from: picPacData))

        let classicData = try JSONEncoder().encode(
            UncheckedClassicState(
                board: board,
                activePlayer: .two,
                starter: .one,
                turnToken: TurnToken(6),
                revision: 0,
                outcome: .win(win)
            )
        )
        XCTAssertThrowsError(try JSONDecoder().decode(ClassicState.self, from: classicData))
    }

    func testStateValidationRejectsUnreachableTurnsAndFullNonterminalBoards() throws {
        XCTAssertThrowsError(
            try PicPacState(
                board: .empty,
                activePlayer: .two,
                remainingX: 5,
                remainingO: 5,
                phase: .awaitingDraw,
                starter: .one,
                revision: 0
            )
        ) { error in
            XCTAssertEqual(
                error as? DomainValidationError,
                .invalidActivePlayer(expected: .one, actual: .two)
            )
        }

        let fullDrawBoard = try Board(symbols: [
            .x, .o, .x,
            .x, .o, .o,
            .o, .x, .x,
        ])
        XCTAssertThrowsError(
            try PicPacState(
                board: fullDrawBoard,
                activePlayer: .two,
                remainingX: 0,
                remainingO: 0,
                phase: .awaitingPlacement(held: .o, token: TurnToken(10)),
                starter: .one,
                revision: 0
            )
        ) { error in
            XCTAssertEqual(error as? DomainValidationError, .nonterminalFullBoard)
        }

        XCTAssertThrowsError(
            try PicPacState(
                board: .empty,
                activePlayer: .one,
                remainingX: 4,
                remainingO: 5,
                phase: .awaitingPlacement(held: .x, token: TurnToken(999)),
                starter: .one,
                revision: 0
            )
        ) { error in
            XCTAssertEqual(
                error as? DomainValidationError,
                .invalidTurnToken(expected: 1, actual: 999)
            )
        }
    }

    func testClassicSymbolsRemainBoundToPlayersWhenPlayerTwoStarts() throws {
        var state = ClassicRules.newGame(starter: .two, revision: 3)
        state = try accepted(
            ClassicRules.place(state, at: Cell(4), token: state.turnToken)
        )
        XCTAssertEqual(state.board[Cell(4)], .o)
        XCTAssertEqual(state.activePlayer, .one)
        XCTAssertEqual(state.turnToken, TurnToken(50))
    }

    func testPublicObservationSeparatesActiveAndAgentActors() throws {
        let state = try accepted(PicPacRules.draw(PicPacRules.newGame(), symbol: .o))
        let observation = try AiObservation.from(state: state, agentPlayer: .two)
        XCTAssertEqual(observation.activePlayer, .one)
        XCTAssertEqual(observation.agentPlayer, .two)
        XCTAssertEqual(observation.heldSymbol, .o)
        XCTAssertEqual(observation.remainingX, 5)
        XCTAssertEqual(observation.remainingO, 4)
        XCTAssertEqual(observation.legalCells, Cell.all)
        XCTAssertThrowsError(
            try AiObservation.from(state: PicPacRules.newGame(), agentPlayer: .two)
        )
    }

    private func token(in state: PicPacState) -> TurnToken {
        guard case let .awaitingPlacement(_, token) = state.phase else {
            preconditionFailure("Expected awaiting-placement state")
        }
        return token
    }

    private func accepted<State>(_ result: TransitionResult<State>) throws -> State {
        switch result {
        case let .accepted(state, _): return state
        case let .rejected(reason):
            throw TestError.rejected(reason)
        }
    }

    private enum TestError: Error {
        case rejected(RejectionReason)
    }
}

private struct UncheckedPicPacState: Encodable {
    let board: Board
    let activePlayer: Player
    let remainingX: Int
    let remainingO: Int
    let phase: PicPacPhase
    let starter: Player
    let revision: Int64
}

private struct UncheckedClassicState: Encodable {
    let board: Board
    let activePlayer: Player
    let starter: Player
    let turnToken: TurnToken
    let revision: Int64
    let outcome: GameOutcome?
}
