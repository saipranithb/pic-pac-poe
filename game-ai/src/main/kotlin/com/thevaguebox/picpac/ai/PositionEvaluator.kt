package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlin.math.tanh

internal object PositionEvaluator {
    fun boundedValue(state: PicPacDecisionState): Double {
        var best = Double.NEGATIVE_INFINITY
        for (cell in ordered(state.legalCells)) {
            val score = actionScore(state, cell)
            if (score > best) best = score
        }
        return tanh(best / 260.0).coerceIn(-0.95, 0.95)
    }

    fun actionScore(state: PicPacDecisionState, cell: Cell): Double {
        val transition = PublicPicPacSearchModel.place(state, cell)
        if (transition is SearchTransition.Win) return 100_000.0
        if (transition is SearchTransition.Draw) return 0.0
        val chance = (transition as SearchTransition.Chance).state
        val board = chance.board

        var score = geometry(board, state.heldSymbol) * 22.0
        score += if (cell.index == 4) 22.0 else if (cell.index % 2 == 0) 10.0 else 4.0

        val total = chance.total.toDouble()
        if (chance.remainingX > 0) {
            val next = PublicPicPacSearchModel.draw(chance, Symbol.X)
            score -= chance.remainingX / total * immediateThreatCost(next)
        }
        if (chance.remainingO > 0) {
            val next = PublicPicPacSearchModel.draw(chance, Symbol.O)
            score -= chance.remainingO / total * immediateThreatCost(next)
        }
        return score
    }

    private fun immediateThreatCost(state: PicPacDecisionState): Double {
        val winningMoves = state.legalCells.count {
            PublicPicPacSearchModel.place(state, it) is SearchTransition.Win
        }
        return when (winningMoves) {
            0 -> 0.0
            1 -> 520.0
            else -> 760.0 + (winningMoves - 2) * 80.0
        }
    }

    private fun geometry(board: Board, focus: Symbol): Double {
        var score = 0.0
        for (line in Board.WIN_LINES) {
            val values = line.cells.map(board::get)
            val focusCount = values.count { it == focus }
            val otherCount = values.count { it == focus.other() }
            val empty = 3 - focusCount - otherCount
            score += when {
                focusCount == 2 && empty == 1 -> 12.0
                focusCount == 1 && empty == 2 -> 3.0
                otherCount == 2 && empty == 1 -> 4.0
                otherCount == 1 && empty == 2 -> 1.0
                else -> 0.0
            }
        }
        return score
    }

    internal fun ordered(cells: List<Cell>): List<Cell> {
        val rank = intArrayOf(1, 5, 2, 6, 0, 7, 3, 8, 4)
        return cells.sortedBy { rank[it.index] }
    }
}
