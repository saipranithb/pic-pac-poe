package com.thevaguebox.picpac.core.ai

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol

data class PicPacDecisionState(
    val board: Board,
    val heldSymbol: Symbol,
    val remainingX: Int,
    val remainingO: Int,
) {
    init {
        require(remainingX in 0..5 && remainingO in 0..5)
        require(board.count(Symbol.X) + remainingX + (if (heldSymbol == Symbol.X) 1 else 0) == 5)
        require(board.count(Symbol.O) + remainingO + (if (heldSymbol == Symbol.O) 1 else 0) == 5)
        require(!board.hasWinner() && !board.isFull)
    }

    val legalCells: List<Cell> get() = board.legalCells()
    val hiddenTotal: Int get() = remainingX + remainingO
}

data class PicPacChanceState(
    val board: Board,
    val remainingX: Int,
    val remainingO: Int,
) {
    val total: Int get() = remainingX + remainingO
}

sealed interface SearchTransition {
    data object Win : SearchTransition
    data object Draw : SearchTransition
    data class Chance(val state: PicPacChanceState) : SearchTransition
}

object PublicPicPacSearchModel {
    fun from(observation: AiObservation): PicPacDecisionState = PicPacDecisionState(
        board = observation.board,
        heldSymbol = observation.heldSymbol,
        remainingX = observation.remainingX,
        remainingO = observation.remainingO,
    )

    fun place(state: PicPacDecisionState, cell: Cell): SearchTransition {
        require(state.board[cell] == null)
        val board = state.board.place(cell, state.heldSymbol)
        if (board.winningLines(state.heldSymbol).isNotEmpty()) return SearchTransition.Win
        if (board.isFull) return SearchTransition.Draw
        return SearchTransition.Chance(
            PicPacChanceState(board, state.remainingX, state.remainingO),
        )
    }

    fun draw(state: PicPacChanceState, symbol: Symbol): PicPacDecisionState {
        require(state.total > 0)
        require(if (symbol == Symbol.X) state.remainingX > 0 else state.remainingO > 0)
        return PicPacDecisionState(
            board = state.board,
            heldSymbol = symbol,
            remainingX = state.remainingX - if (symbol == Symbol.X) 1 else 0,
            remainingO = state.remainingO - if (symbol == Symbol.O) 1 else 0,
        )
    }
}
