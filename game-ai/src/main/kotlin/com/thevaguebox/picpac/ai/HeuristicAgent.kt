package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlin.random.Random

class HeuristicAgent(private val random: Random = Random.Default) : AiAgent {
    override suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision {
        val state = PublicPicPacSearchModel.from(observation)
        val ordered = PositionEvaluator.ordered(state.legalCells)
        val immediate = ordered.filter { PublicPicPacSearchModel.place(state, it) is SearchTransition.Win }
        if (immediate.isNotEmpty()) return AiDecision(immediate[random.nextInt(immediate.size)])

        val scored = ordered.map { it to PositionEvaluator.actionScore(state, it) }
        val best = scored.maxOf { it.second }
        val topBand = scored.filter { (_, score) -> score >= best - 8.0 }
        return AiDecision(topBand[random.nextInt(topBand.size)].first)
    }
}
