package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.SearchLimits
import kotlin.random.Random

class RandomAgent(private val random: Random = Random.Default) : AiAgent {
    override suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision =
        AiDecision(observation.legalCells[random.nextInt(observation.legalCells.size)])
}
