package com.thevaguebox.picpac.core

object PicPacRules {
    fun newGame(starter: Player = Player.ONE, revision: Long = 0): PicPacState = PicPacState(
        board = Board.EMPTY,
        activePlayer = starter,
        remainingX = 5,
        remainingO = 5,
        phase = PicPacPhase.AwaitingDraw,
        starter = starter,
        revision = revision,
    )

    fun draw(state: PicPacState, symbol: Symbol): TransitionResult<PicPacState> {
        if (state.phase is PicPacPhase.Terminal) return TransitionResult.Rejected(RejectionReason.TERMINAL)
        if (state.phase !is PicPacPhase.AwaitingDraw) return TransitionResult.Rejected(RejectionReason.WRONG_PHASE)
        if ((symbol == Symbol.X && state.remainingX == 0) ||
            (symbol == Symbol.O && state.remainingO == 0)
        ) return TransitionResult.Rejected(RejectionReason.EXHAUSTED_SYMBOL)

        val token = TurnToken(state.revision * 16L + state.board.occupiedCount + 1L)
        val next = state.copy(
            remainingX = state.remainingX - if (symbol == Symbol.X) 1 else 0,
            remainingO = state.remainingO - if (symbol == Symbol.O) 1 else 0,
            phase = PicPacPhase.AwaitingPlacement(symbol, token),
        )
        return TransitionResult.Accepted(next, GameEvent.PieceRevealed(state.activePlayer, symbol))
    }

    fun place(state: PicPacState, cell: Cell, token: TurnToken): TransitionResult<PicPacState> {
        val phase = state.phase
        if (phase is PicPacPhase.Terminal) return TransitionResult.Rejected(RejectionReason.TERMINAL)
        if (phase !is PicPacPhase.AwaitingPlacement) return TransitionResult.Rejected(RejectionReason.WRONG_PHASE)
        if (phase.token != token) return TransitionResult.Rejected(RejectionReason.STALE_TURN)
        if (state.board[cell] != null) return TransitionResult.Rejected(RejectionReason.OCCUPIED)

        val board = state.board.place(cell, phase.held)
        val lines = board.winningLines(phase.held)
        if (lines.isNotEmpty()) {
            val outcome = GameOutcome.Win(state.activePlayer, phase.held, lines)
            return TransitionResult.Accepted(
                state.copy(board = board, phase = PicPacPhase.Terminal(outcome)),
                GameEvent.GameWon(outcome),
            )
        }
        if (board.isFull) {
            return TransitionResult.Accepted(
                state.copy(board = board, phase = PicPacPhase.Terminal(GameOutcome.Draw)),
                GameEvent.GameDrawn,
            )
        }
        return TransitionResult.Accepted(
            state.copy(
                board = board,
                activePlayer = state.activePlayer.other(),
                phase = PicPacPhase.AwaitingDraw,
            ),
            GameEvent.PiecePlaced(state.activePlayer, phase.held, cell),
        )
    }
}

object ClassicRules {
    fun newGame(starter: Player = Player.ONE, revision: Long = 0): ClassicState = ClassicState(
        board = Board.EMPTY,
        activePlayer = starter,
        starter = starter,
        turnToken = token(revision, 0),
        revision = revision,
    )

    fun place(state: ClassicState, cell: Cell, turnToken: TurnToken): TransitionResult<ClassicState> {
        if (state.isTerminal) return TransitionResult.Rejected(RejectionReason.TERMINAL)
        if (state.turnToken != turnToken) return TransitionResult.Rejected(RejectionReason.STALE_TURN)
        if (state.board[cell] != null) return TransitionResult.Rejected(RejectionReason.OCCUPIED)

        val symbol = state.symbolFor(state.activePlayer)
        val board = state.board.place(cell, symbol)
        val lines = board.winningLines(symbol)
        if (lines.isNotEmpty()) {
            val outcome = GameOutcome.Win(state.activePlayer, symbol, lines)
            return TransitionResult.Accepted(
                state.copy(board = board, outcome = outcome),
                GameEvent.GameWon(outcome),
            )
        }
        if (board.isFull) {
            return TransitionResult.Accepted(
                state.copy(board = board, outcome = GameOutcome.Draw),
                GameEvent.GameDrawn,
            )
        }
        val next = state.copy(
            board = board,
            activePlayer = state.activePlayer.other(),
            turnToken = token(state.revision, board.occupiedCount),
        )
        return TransitionResult.Accepted(next, GameEvent.PiecePlaced(state.activePlayer, symbol, cell))
    }

    private fun token(revision: Long, occupied: Int) = TurnToken(revision * 16L + occupied + 1L)
}
