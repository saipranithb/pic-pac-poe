package com.thevaguebox.picpac.tools

import com.thevaguebox.picpac.ai.ExpectiminimaxSolver
import com.thevaguebox.picpac.ai.TabularPolicy
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

data class TrainingReport(
    val episodes: Int,
    val states: Int,
    val wins: Int,
    val draws: Int,
    val meanAbsoluteTdError: Double,
)

class NegamaxQLearner(
    val policy: TabularPolicy = TabularPolicy.empty(),
    seed: Long,
) {
    private val environmentRandom = Random(seed)
    private val explorationRandom = Random(seed xor 0x51F15EED)
    private val visits = hashMapOf<Long, IntArray>()

    fun train(episodes: Int): TrainingReport {
        require(episodes > 0)
        var wins = 0
        var draws = 0
        var totalError = 0.0
        var updates = 0L
        repeat(episodes) { episode ->
            var state = initialDecision()
            val epsilon = max(0.02, 1.0 - episode.toDouble() / (episodes * 0.85))
            while (true) {
                val cell = choose(state, epsilon)
                val old = policy.value(state, cell).toDouble()
                val target: Double
                var next: PicPacDecisionState? = null
                when (val transition = PublicPicPacSearchModel.place(state, cell)) {
                    SearchTransition.Win -> {
                        target = 1.0
                        wins++
                    }
                    SearchTransition.Draw -> {
                        target = 0.0
                        draws++
                    }
                    is SearchTransition.Chance -> {
                        next = PublicPicPacSearchModel.draw(transition.state, sample(transition.state))
                        target = -policy.maxValue(next)
                    }
                }
                val key = TabularPolicy.key(state)
                val counts = visits.getOrPut(key) { IntArray(9) }
                counts[cell.index]++
                val alpha = 1.0 / sqrt(counts[cell.index].toDouble())
                policy.update(state, cell, alpha, target)
                totalError += kotlin.math.abs(target - old)
                updates++
                if (next == null) break
                state = next
            }
        }
        return TrainingReport(episodes, policy.stateCount, wins, draws, totalError / updates)
    }

    private fun choose(state: PicPacDecisionState, epsilon: Double): Cell =
        if (explorationRandom.nextDouble() < epsilon) state.legalCells[explorationRandom.nextInt(state.legalCells.size)]
        else policy.bestCell(state)

    private fun initialDecision(): PicPacDecisionState {
        val chance = PicPacChanceState(Board.EMPTY, 5, 5)
        return PublicPicPacSearchModel.draw(chance, sample(chance))
    }

    private fun sample(chance: PicPacChanceState): Symbol =
        if (environmentRandom.nextInt(chance.total) < chance.remainingX) Symbol.X else Symbol.O
}

data class PolicyQuality(
    val evaluatedStates: Int,
    val optimalActionAgreement: Double,
    val meanRegret: Double,
)

suspend fun evaluatePolicy(policy: TabularPolicy, states: List<PicPacDecisionState>): PolicyQuality {
    val solver = ExpectiminimaxSolver()
    var optimal = 0
    var regret = 0.0
    states.forEach { state ->
        val exact = solver.analyze(state)
        val chosen = policy.bestCell(state)
        val chosenValue = exact.actionValues.getValue(chosen)
        if (kotlin.math.abs(chosenValue - exact.value) < 1e-9) optimal++
        regret += exact.value - chosenValue
    }
    return PolicyQuality(states.size, optimal.toDouble() / states.size, regret / states.size)
}
