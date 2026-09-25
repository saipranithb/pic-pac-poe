public enum PicPacRules {
    public static func newGame(
        starter: Player = .one,
        revision: Int64 = 0
    ) -> PicPacState {
        precondition((0...TurnToken.maximumRevision).contains(revision))
        // These constants satisfy every domain invariant by construction.
        return try! PicPacState(
            board: .empty,
            activePlayer: starter,
            remainingX: 5,
            remainingO: 5,
            phase: .awaitingDraw,
            starter: starter,
            revision: revision
        )
    }

    public static func draw(
        _ state: PicPacState,
        symbol: Symbol
    ) -> TransitionResult<PicPacState> {
        switch state.phase {
        case .terminal:
            return .rejected(.terminal)
        case .awaitingPlacement:
            return .rejected(.wrongPhase)
        case .awaitingDraw:
            break
        }

        if (symbol == .x && state.remainingX == 0)
            || (symbol == .o && state.remainingO == 0) {
            return .rejected(.exhaustedSymbol)
        }

        let token = token(revision: state.revision, occupiedCount: state.board.occupiedCount)
        let next = try! PicPacState(
            board: state.board,
            activePlayer: state.activePlayer,
            remainingX: state.remainingX - (symbol == .x ? 1 : 0),
            remainingO: state.remainingO - (symbol == .o ? 1 : 0),
            phase: .awaitingPlacement(held: symbol, token: token),
            starter: state.starter,
            revision: state.revision
        )
        return .accepted(
            state: next,
            event: .pieceRevealed(player: state.activePlayer, symbol: symbol)
        )
    }

    public static func place(
        _ state: PicPacState,
        at cell: Cell,
        token: TurnToken
    ) -> TransitionResult<PicPacState> {
        let held: Symbol
        let currentToken: TurnToken
        switch state.phase {
        case .terminal:
            return .rejected(.terminal)
        case .awaitingDraw:
            return .rejected(.wrongPhase)
        case let .awaitingPlacement(symbol, phaseToken):
            held = symbol
            currentToken = phaseToken
        }

        guard currentToken == token else { return .rejected(.staleTurn) }
        guard state.board[cell] == nil else { return .rejected(.occupied) }

        let board = try! state.board.placing(held, at: cell)
        let lines = board.winningLines(for: held)
        if !lines.isEmpty {
            let win = GameOutcome.Win(
                player: state.activePlayer,
                symbol: held,
                lines: lines
            )
            let next = try! PicPacState(
                board: board,
                activePlayer: state.activePlayer,
                remainingX: state.remainingX,
                remainingO: state.remainingO,
                phase: .terminal(outcome: .win(win)),
                starter: state.starter,
                revision: state.revision
            )
            return .accepted(state: next, event: .gameWon(win))
        }

        if board.isFull {
            let next = try! PicPacState(
                board: board,
                activePlayer: state.activePlayer,
                remainingX: state.remainingX,
                remainingO: state.remainingO,
                phase: .terminal(outcome: .draw),
                starter: state.starter,
                revision: state.revision
            )
            return .accepted(state: next, event: .gameDrawn)
        }

        let next = try! PicPacState(
            board: board,
            activePlayer: state.activePlayer.other,
            remainingX: state.remainingX,
            remainingO: state.remainingO,
            phase: .awaitingDraw,
            starter: state.starter,
            revision: state.revision
        )
        return .accepted(
            state: next,
            event: .piecePlaced(player: state.activePlayer, symbol: held, cell: cell)
        )
    }

    private static func token(revision: Int64, occupiedCount: Int) -> TurnToken {
        guard let token = TurnToken.derived(
            revision: revision,
            ordinal: occupiedCount + 1
        ) else {
            preconditionFailure("Revision and occupied count must form a valid turn token")
        }
        return token
    }
}

public enum ClassicRules {
    public static func newGame(
        starter: Player = .one,
        revision: Int64 = 0
    ) -> ClassicState {
        precondition((0...TurnToken.maximumRevision).contains(revision))
        return try! ClassicState(
            board: .empty,
            activePlayer: starter,
            starter: starter,
            turnToken: token(revision: revision, occupiedCount: 0),
            revision: revision
        )
    }

    public static func place(
        _ state: ClassicState,
        at cell: Cell,
        token suppliedToken: TurnToken
    ) -> TransitionResult<ClassicState> {
        guard !state.isTerminal else { return .rejected(.terminal) }
        guard state.turnToken == suppliedToken else { return .rejected(.staleTurn) }
        guard state.board[cell] == nil else { return .rejected(.occupied) }

        let symbol = state.symbol(for: state.activePlayer)
        let board = try! state.board.placing(symbol, at: cell)
        let lines = board.winningLines(for: symbol)
        if !lines.isEmpty {
            let win = GameOutcome.Win(
                player: state.activePlayer,
                symbol: symbol,
                lines: lines
            )
            let next = try! ClassicState(
                board: board,
                activePlayer: state.activePlayer,
                starter: state.starter,
                turnToken: state.turnToken,
                revision: state.revision,
                outcome: .win(win)
            )
            return .accepted(state: next, event: .gameWon(win))
        }

        if board.isFull {
            let next = try! ClassicState(
                board: board,
                activePlayer: state.activePlayer,
                starter: state.starter,
                turnToken: state.turnToken,
                revision: state.revision,
                outcome: .draw
            )
            return .accepted(state: next, event: .gameDrawn)
        }

        let next = try! ClassicState(
            board: board,
            activePlayer: state.activePlayer.other,
            starter: state.starter,
            turnToken: token(revision: state.revision, occupiedCount: board.occupiedCount),
            revision: state.revision
        )
        return .accepted(
            state: next,
            event: .piecePlaced(player: state.activePlayer, symbol: symbol, cell: cell)
        )
    }

    private static func token(revision: Int64, occupiedCount: Int) -> TurnToken {
        guard let token = TurnToken.derived(
            revision: revision,
            ordinal: occupiedCount + 1
        ) else {
            preconditionFailure("Revision and occupied count must form a valid turn token")
        }
        return token
    }
}
