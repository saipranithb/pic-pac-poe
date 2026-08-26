package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AgentTest {
    @Test fun `exact opening values match independent oracle`() = runBlocking {
        for (held in Symbol.entries) {
            val state = PicPacDecisionState(
                board = Board.EMPTY,
                heldSymbol = held,
                remainingX = if (held == Symbol.X) 4 else 5,
                remainingO = if (held == Symbol.O) 4 else 5,
            )
            val result = ExpectiminimaxSolver().analyze(state)
            assertEquals(Cell.of(4), result.bestCell)
            assertEquals(5.0 / 21.0, result.value, 1e-12)
            result.actionValues.forEach { (cell, value) ->
                val expected = if (cell.index == 4) 5.0 / 21.0 else 11.0 / 126.0
                assertEquals(expected, value, 1e-12)
            }
        }
    }

    @Test fun `heuristic always takes an immediate win with either symbol`() = runBlocking {
        Symbol.entries.forEach { held ->
            val other = held.other()
            val board = Board.fromSymbols(listOf(held, held, null, other, null, null, null, null, null))
            val observation = observation(board, held)
            val decision = HeuristicAgent(Random(4)).chooseMove(observation, SearchLimits())
            assertEquals(Cell.of(2), decision.cell)
        }
    }

    @Test fun `random and heuristic are legal across every reachable decision state`() = runBlocking {
        val states = reachableDecisionStates()
        assertEquals(21_314, states.size)
        val random = RandomAgent(Random(10))
        val heuristic = HeuristicAgent(Random(11))
        for (state in states) {
            val observation = observation(state.board, state.heldSymbol)
            assertTrue(random.chooseMove(observation, SearchLimits()).cell in state.legalCells)
            assertTrue(heuristic.chooseMove(observation, SearchLimits()).cell in state.legalCells)
        }
    }

    @Test fun `agent constructors cannot receive production session capabilities`() {
        listOf(RandomAgent::class.java, HeuristicAgent::class.java, ExpectiminimaxAgent::class.java)
            .flatMap { it.declaredConstructors.toList() }
            .flatMap { it.parameterTypes.toList() }
            .forEach { type ->
                assertFalse(type.name.contains("GameSession"))
                assertFalse(type.name.contains("EnvironmentRandomSource"))
            }
    }

    private fun observation(board: Board, held: Symbol): AiObservation = AiObservation(
        board = board,
        activePlayer = Player.ONE,
        agentPlayer = Player.ONE,
        heldSymbol = held,
        remainingX = 5 - board.count(Symbol.X) - if (held == Symbol.X) 1 else 0,
        remainingO = 5 - board.count(Symbol.O) - if (held == Symbol.O) 1 else 0,
    )

    private fun reachableDecisionStates(): Set<PicPacDecisionState> {
        val chanceSeen = hashSetOf<PicPacChanceState>()
        val decisions = hashSetOf<PicPacDecisionState>()
        lateinit var visitChance: (PicPacChanceState) -> Unit
        lateinit var visitDecision: (PicPacDecisionState) -> Unit
        visitChance = { chance ->
            if (chanceSeen.add(chance)) {
                if (chance.remainingX > 0) visitDecision(PublicPicPacSearchModel.draw(chance, Symbol.X))
                if (chance.remainingO > 0) visitDecision(PublicPicPacSearchModel.draw(chance, Symbol.O))
            }
        }
        visitDecision = { state ->
            if (decisions.add(state)) {
                state.legalCells.forEach { cell ->
                    val transition = PublicPicPacSearchModel.place(state, cell)
                    if (transition is SearchTransition.Chance) visitChance(transition.state)
                }
            }
        }
        visitChance(PicPacChanceState(Board.EMPTY, 5, 5))
        return decisions
    }
}
