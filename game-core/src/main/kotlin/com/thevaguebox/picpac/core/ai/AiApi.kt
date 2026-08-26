package com.thevaguebox.picpac.core.ai

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.PicPacState
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol

data class AiObservation(
    val board: Board,
    val activePlayer: Player,
    val agentPlayer: Player,
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

    companion object {
        fun from(state: PicPacState, agentPlayer: Player): AiObservation {
            val phase = state.phase as? PicPacPhase.AwaitingPlacement
                ?: error("AI observation requires an awaiting-placement state")
            return AiObservation(
                board = state.board,
                activePlayer = state.activePlayer,
                agentPlayer = agentPlayer,
                heldSymbol = phase.held,
                remainingX = state.remainingX,
                remainingO = state.remainingO,
            )
        }
    }
}

data class SearchLimits(
    val maxDepth: Int? = null,
    val nodeBudget: Int? = null,
    val deadlineNanos: Long? = null,
) {
    init {
        require(maxDepth == null || maxDepth >= 0)
        require(nodeBudget == null || nodeBudget > 0)
    }
}

sealed interface AiDiagnostics {
    data object None : AiDiagnostics
    data class Search(
        val nodes: Long,
        val cacheHits: Long,
        val maxDepth: Int,
        val elapsedNanos: Long,
        val value: Double,
        val simulations: Int = 0,
    ) : AiDiagnostics
}

data class AiDecision(
    val cell: Cell,
    val diagnostics: AiDiagnostics = AiDiagnostics.None,
)

fun interface AiAgent {
    suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision
}
