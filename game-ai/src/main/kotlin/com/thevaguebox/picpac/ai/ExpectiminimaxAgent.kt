package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import com.thevaguebox.picpac.core.ai.AiDiagnostics
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.min
import kotlin.system.measureNanoTime

data class SearchAnalysis(
    val bestCell: Cell,
    val value: Double,
    val actionValues: Map<Cell, Double>,
    val nodes: Long,
    val cacheHits: Long,
    val reachedDepth: Int,
)

class ExpectiminimaxSolver {
    private val cache = HashMap<Long, Double>(40_000)
    private var nodes = 0L
    private var cacheHits = 0L
    private var deepest = 0
    private var deadlineNanos: Long? = null
    private var nodeBudget: Int? = null

    suspend fun analyze(
        state: PicPacDecisionState,
        maxDepth: Int = Int.MAX_VALUE,
        limits: SearchLimits = SearchLimits(),
    ): SearchAnalysis {
        nodes = 0
        cacheHits = 0
        deepest = 0
        deadlineNanos = limits.deadlineNanos
        nodeBudget = limits.nodeBudget
        val depth = min(maxDepth, state.legalCells.size)
        val values = linkedMapOf<Cell, Double>()
        for (cell in PositionEvaluator.ordered(state.legalCells)) {
            values[cell] = actionValue(state, cell, depth, 0)
        }
        val bestValue = values.maxOf { it.value }
        val bestCell = values.entries.first { nearlyEqual(it.value, bestValue) }.key
        return SearchAnalysis(bestCell, bestValue, values, nodes, cacheHits, deepest)
    }

    fun clearCache() = cache.clear()
    val cachedStateCount: Int get() = cache.size

    private suspend fun value(state: PicPacDecisionState, depth: Int, ply: Int): Double {
        checkpoint(ply)
        if (depth == 0) return PositionEvaluator.boundedValue(state)
        val key = key(state, depth)
        cache[key]?.let {
            cacheHits++
            return it
        }
        var best = Double.NEGATIVE_INFINITY
        for (cell in PositionEvaluator.ordered(state.legalCells)) {
            val candidate = actionValue(state, cell, depth, ply)
            if (candidate > best) best = candidate
        }
        cache[key] = best
        return best
    }

    private suspend fun actionValue(
        state: PicPacDecisionState,
        cell: Cell,
        depth: Int,
        ply: Int,
    ): Double = when (val transition = PublicPicPacSearchModel.place(state, cell)) {
        SearchTransition.Win -> 1.0
        SearchTransition.Draw -> 0.0
        is SearchTransition.Chance -> -expectedOpponentValue(transition.state, depth - 1, ply + 1)
    }

    private suspend fun expectedOpponentValue(
        chance: PicPacChanceState,
        depth: Int,
        ply: Int,
    ): Double {
        val total = chance.total.toDouble()
        var expected = 0.0
        if (chance.remainingX > 0) {
            expected += chance.remainingX / total * value(
                PublicPicPacSearchModel.draw(chance, Symbol.X), depth, ply,
            )
        }
        if (chance.remainingO > 0) {
            expected += chance.remainingO / total * value(
                PublicPicPacSearchModel.draw(chance, Symbol.O), depth, ply,
            )
        }
        return expected
    }

    private suspend fun checkpoint(ply: Int) {
        nodes++
        if (ply > deepest) deepest = ply
        if ((nodes and 255L) == 0L) currentCoroutineContext().ensureActive()
        if (nodeBudget?.let { nodes > it } == true) throw SearchLimitReached()
        if (deadlineNanos?.let { System.nanoTime() >= it } == true) throw SearchLimitReached()
    }

    private fun key(state: PicPacDecisionState, depth: Int): Long {
        val held = if (state.heldSymbol == Symbol.X) 0L else 1L
        return state.board.code.toLong() or
            (held shl 15) or
            (state.remainingX.toLong() shl 16) or
            (state.remainingO.toLong() shl 19) or
            (depth.toLong() shl 22)
    }

    private fun nearlyEqual(a: Double, b: Double) = kotlin.math.abs(a - b) < 1e-12
}

class ExpectiminimaxAgent(
    private val configuredDepth: Int = Int.MAX_VALUE,
    private val solver: ExpectiminimaxSolver = ExpectiminimaxSolver(),
    private val fallback: AiAgent = HeuristicAgent(),
) : AiAgent {
    override suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision {
        val state = PublicPicPacSearchModel.from(observation)
        var analysis: SearchAnalysis? = null
        val elapsed = measureNanoTime {
            analysis = try {
                solver.analyze(state, limits.maxDepth ?: configuredDepth, limits)
            } catch (_: SearchLimitReached) {
                null
            }
        }
        val result = analysis ?: return fallback.chooseMove(observation, limits.copy(nodeBudget = null, deadlineNanos = null))
        return AiDecision(
            cell = result.bestCell,
            diagnostics = AiDiagnostics.Search(
                nodes = result.nodes,
                cacheHits = result.cacheHits,
                maxDepth = result.reachedDepth,
                elapsedNanos = elapsed,
                value = result.value,
            ),
        )
    }
}

private class SearchLimitReached : RuntimeException(null, null, false, false)
