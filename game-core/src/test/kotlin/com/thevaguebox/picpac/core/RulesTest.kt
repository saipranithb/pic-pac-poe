package com.thevaguebox.picpac.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test fun `all eight winning lines are recognized`() {
        Board.WIN_LINES.forEach { line ->
            val board = line.cells.fold(Board.EMPTY) { current, cell -> current.place(cell, Symbol.X) }
            assertEquals(listOf(line), board.winningLines(Symbol.X))
        }
    }

    @Test fun `player one can win with O`() {
        var state = PicPacRules.newGame()
        state = state.draw(Symbol.O).place(0)
        state = state.draw(Symbol.X).place(3)
        state = state.draw(Symbol.O).place(1)
        state = state.draw(Symbol.X).place(4)
        state = state.draw(Symbol.O).place(2)

        val outcome = (state.phase as PicPacPhase.Terminal).outcome as GameOutcome.Win
        assertEquals(Player.ONE, outcome.player)
        assertEquals(Symbol.O, outcome.symbol)
    }

    @Test fun `player two can win with X`() {
        var state = PicPacRules.newGame()
        state = state.draw(Symbol.O).place(0)
        state = state.draw(Symbol.X).place(3)
        state = state.draw(Symbol.O).place(8)
        state = state.draw(Symbol.X).place(4)
        state = state.draw(Symbol.O).place(1)
        state = state.draw(Symbol.X).place(5)

        val outcome = (state.phase as PicPacPhase.Terminal).outcome as GameOutcome.Win
        assertEquals(Player.TWO, outcome.player)
        assertEquals(Symbol.X, outcome.symbol)
    }

    @Test fun `draw removes held piece before placement and conserves bag`() {
        val initial = PicPacRules.newGame()
        val drawn = initial.draw(Symbol.X)
        assertEquals(4, drawn.remainingX)
        assertEquals(5, drawn.remainingO)
        assertEquals(9, drawn.hiddenTotal)
        assertEquals(Symbol.X, (drawn.phase as PicPacPhase.AwaitingPlacement).held)
    }

    @Test fun `exhausted symbol cannot be drawn`() {
        val board = Board.fromSymbols(
            listOf(Symbol.X, Symbol.O, Symbol.X, Symbol.X, null, null, null, Symbol.X, Symbol.X),
        )
        val state = PicPacState(
            board = board,
            activePlayer = Player.ONE,
            remainingX = 0,
            remainingO = 4,
            phase = PicPacPhase.AwaitingDraw,
            starter = Player.ONE,
            revision = 0,
        )
        val result = PicPacRules.draw(state, Symbol.X)
        assertEquals(TransitionResult.Rejected(RejectionReason.EXHAUSTED_SYMBOL), result)
    }

    @Test fun `stale occupied and terminal moves are rejected`() {
        val revealed = PicPacRules.newGame().draw(Symbol.X)
        val token = (revealed.phase as PicPacPhase.AwaitingPlacement).token
        assertEquals(
            TransitionResult.Rejected(RejectionReason.STALE_TURN),
            PicPacRules.place(revealed, Cell.of(0), TurnToken(token.value + 1)),
        )
        val placed = revealed.place(0)
        val revealedAgain = placed.draw(Symbol.O)
        val nextToken = (revealedAgain.phase as PicPacPhase.AwaitingPlacement).token
        assertEquals(
            TransitionResult.Rejected(RejectionReason.OCCUPIED),
            PicPacRules.place(revealedAgain, Cell.of(0), nextToken),
        )

        var terminal = PicPacRules.newGame()
        terminal = terminal.draw(Symbol.X).place(0)
        terminal = terminal.draw(Symbol.O).place(3)
        terminal = terminal.draw(Symbol.X).place(1)
        terminal = terminal.draw(Symbol.O).place(4)
        terminal = terminal.draw(Symbol.X).place(2)
        assertEquals(
            TransitionResult.Rejected(RejectionReason.TERMINAL),
            PicPacRules.place(terminal, Cell.of(8), TurnToken(1)),
        )
    }

    @Test fun `classic fixes symbols to players`() {
        var state = ClassicRules.newGame()
        state = state.place(0)
        assertEquals(Symbol.X, state.board[Cell.of(0)])
        state = state.place(4)
        assertEquals(Symbol.O, state.board[Cell.of(4)])
    }

    @Test fun `scripted environment samples both sides of initial five-five split`() {
        val xSession = PicPacGameSession.forTesting(PicPacRules.newGame(), listOf(0).iterator())
        val oSession = PicPacGameSession.forTesting(PicPacRules.newGame(), listOf(5).iterator())
        assertEquals(Symbol.X, ((xSession.reveal() as TransitionResult.Accepted).state.phase as PicPacPhase.AwaitingPlacement).held)
        assertEquals(Symbol.O, ((oSession.reveal() as TransitionResult.Accepted).state.phase as PicPacPhase.AwaitingPlacement).held)
    }

    private fun PicPacState.draw(symbol: Symbol): PicPacState =
        (PicPacRules.draw(this, symbol) as TransitionResult.Accepted).state

    private fun PicPacState.place(index: Int): PicPacState {
        val token = (phase as PicPacPhase.AwaitingPlacement).token
        return (PicPacRules.place(this, Cell.of(index), token) as TransitionResult.Accepted).state
    }

    private fun ClassicState.place(index: Int): ClassicState =
        (ClassicRules.place(this, Cell.of(index), turnToken) as TransitionResult.Accepted).state
}
