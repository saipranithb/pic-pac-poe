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
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.system.measureNanoTime

class StochasticMctsAgent(
    private val defaultSimulations: Int = 1_000,
    private val exploration: Double = sqrt(2.0),
    private val random: Random = Random.Default,
) : AiAgent {
    init { require(defaultSimulations > 0) }

    override suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision {
        val rootState = PublicPicPacSearchModel.from(observation)
        val root = DecisionNode(rootState, rootSign = 1)
        val simulations = limits.nodeBudget ?: defaultSimulations
        var completed = 0
        var createdNodes = 1L
        var maxDepth = 0
        val elapsed = measureNanoTime {
            repeat(simulations) {
                currentCoroutineContext().ensureActive()
                if (limits.deadlineNanos?.let { deadline -> System.nanoTime() >= deadline } == true) return@measureNanoTime
                val result = simulate(root)
                createdNodes += result.created
                if (result.depth > maxDepth) maxDepth = result.depth
                completed++
            }
        }
        val best = root.edges.values.sortedWith(
            compareByDescending<ActionEdge> { it.visits }
                .thenByDescending { it.mean }
                .thenBy { moveRank(it.cell) },
        ).firstOrNull() ?: return AiDecision(rootState.legalCells.first())
        return AiDecision(
            best.cell,
            AiDiagnostics.Search(
                nodes = createdNodes,
                cacheHits = 0,
                maxDepth = maxDepth,
                elapsedNanos = elapsed,
                value = best.mean,
                simulations = completed,
            ),
        )
    }

    private fun simulate(root: DecisionNode): SimulationResult {
        val path = arrayListOf<Stats>(root)
        var node = root
        var depth = 0
        var created = 0L
        while (true) {
            depth++
            val edge = if (node.untried.isNotEmpty()) {
                val cell = node.untried.removeAt(random.nextInt(node.untried.size))
                ActionEdge(cell).also {
                    node.edges[cell] = it
                    created++
                }
            } else {
                select(node)
            }
            path += edge

            when (val transition = PublicPicPacSearchModel.place(node.state, edge.cell)) {
                SearchTransition.Win -> return finish(path, node.rootSign.toDouble(), depth, created)
                SearchTransition.Draw -> return finish(path, 0.0, depth, created)
                is SearchTransition.Chance -> {
                    val symbol = sample(transition.state)
                    val child = edge.children[symbol]
                    if (child == null) {
                        val next = DecisionNode(
                            PublicPicPacSearchModel.draw(transition.state, symbol),
                            -node.rootSign,
                        )
                        edge.children[symbol] = next
                        path += next
                        created++
                        val rollout = rollout(next.state, next.rootSign, depth + 1)
                        return finish(path, rollout.reward, rollout.depth, created)
                    }
                    node = child
                    path += node
                }
            }
        }
    }

    private fun select(node: DecisionNode): ActionEdge {
        val logParent = ln(node.visits.coerceAtLeast(1).toDouble())
        return node.edges.values.maxBy { edge ->
            if (edge.visits == 0) Double.POSITIVE_INFINITY
            else node.rootSign * edge.mean + exploration * sqrt(logParent / edge.visits)
        }
    }

    private fun rollout(initial: PicPacDecisionState, initialSign: Int, startingDepth: Int): Rollout {
        var state = initial
        var sign = initialSign
        var depth = startingDepth
        while (true) {
            val winning = state.legalCells.filter {
                PublicPicPacSearchModel.place(state, it) is SearchTransition.Win
            }
            val cell = if (winning.isNotEmpty()) winning[random.nextInt(winning.size)]
            else state.legalCells[random.nextInt(state.legalCells.size)]
            when (val transition = PublicPicPacSearchModel.place(state, cell)) {
                SearchTransition.Win -> return Rollout(sign.toDouble(), depth)
                SearchTransition.Draw -> return Rollout(0.0, depth)
                is SearchTransition.Chance -> {
                    state = PublicPicPacSearchModel.draw(transition.state, sample(transition.state))
                    sign = -sign
                    depth++
                }
            }
        }
    }

    private fun sample(chance: PicPacChanceState): Symbol =
        if (random.nextInt(chance.total) < chance.remainingX) Symbol.X else Symbol.O

    private fun finish(path: List<Stats>, reward: Double, depth: Int, created: Long): SimulationResult {
        path.forEach {
            it.visits++
            it.valueSum += reward
        }
        return SimulationResult(depth, created)
    }

    private fun moveRank(cell: Cell): Int = when (cell.index) {
        4 -> 0
        0 -> 1
        2 -> 2
        6 -> 3
        8 -> 4
        else -> 5 + cell.index
    }

    private interface Stats {
        var visits: Int
        var valueSum: Double
    }

    private class DecisionNode(val state: PicPacDecisionState, val rootSign: Int) : Stats {
        override var visits = 0
        override var valueSum = 0.0
        val untried = state.legalCells.toMutableList()
        val edges = linkedMapOf<Cell, ActionEdge>()
    }

    private class ActionEdge(val cell: Cell) : Stats {
        override var visits = 0
        override var valueSum = 0.0
        val children = hashMapOf<Symbol, DecisionNode>()
        val mean: Double get() = if (visits == 0) 0.0 else valueSum / visits
    }

    private data class Rollout(val reward: Double, val depth: Int)
    private data class SimulationResult(val depth: Int, val created: Long)
}
