package com.thevaguebox.picpac.ai

import com.google.gson.JsonArray
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.picpac.testing.GoldenFixtureDocument
import com.thevaguebox.picpac.testing.intValues
import com.thevaguebox.picpac.testing.optionalArray
import com.thevaguebox.picpac.testing.optionalObject
import com.thevaguebox.picpac.testing.requireArray
import com.thevaguebox.picpac.testing.requireDouble
import com.thevaguebox.picpac.testing.requireInt
import com.thevaguebox.picpac.testing.requireObject
import com.thevaguebox.picpac.testing.requireString
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GoldenFixtureAiTest {
    private val fixtures = GoldenFixtureDocument.load()

    @Test fun `AI choice fixtures execute through canonical agents`() {
        fixtures.cases("aiChoices").forEach { fixture -> fixture.verify {
            val value = fixture.value
            val state = value.decisionState()
            val observation = state.observation()
            when (value.requireString("agent")) {
                "HEURISTIC" -> {
                    val random = ScriptedRandom(listOf(RandomCall(1, 0)))
                    val decision = runBlocking { HeuristicAgent(random).chooseMove(observation, SearchLimits()) }
                    assertEquals(Cell.of(value.requireInt("expectedCell")), decision.cell)
                    random.assertExhausted()
                }
                "EXPECTIMINIMAX_DEPTH_4" -> {
                    value.optionalArray("tieOrder")?.let { order ->
                        assertEquals(order.intValues("tieOrder"), PositionEvaluator.ordered(state.legalCells).map(Cell::index))
                    }
                    val decision = runBlocking {
                        ExpectiminimaxAgent(configuredDepth = 4).chooseMove(observation, SearchLimits())
                    }
                    assertEquals(Cell.of(value.requireInt("expectedCell")), decision.cell)
                }
                "EXPECTIMINIMAX_FULL" -> {
                    val analysis = runBlocking { ExpectiminimaxSolver().analyze(state) }
                    assertEquals(Cell.of(value.requireInt("expectedCell")), analysis.bestCell)
                    value.optionalObject("expectedRootValue")?.let { fraction ->
                        assertEquals(fraction.fraction(), analysis.value, fraction.requireDouble("tolerance"))
                    }
                    value.optionalObject("expectedActionValues")?.entrySet()?.forEach { (cell, fractionElement) ->
                        val expected = fractionElement.requireObject("expectedActionValues.$cell").fraction()
                        assertEquals(expected, analysis.actionValues.getValue(Cell.of(cell.toInt())), 1e-12)
                    }
                }
                "RANDOM_BASELINE" -> {
                    val legal = value.requireArray("legalCells").intValues("legalCells")
                    assertEquals(legal, state.legalCells.map(Cell::index))
                    val random = ScriptedRandom(
                        listOf(RandomCall(legal.size, value.requireInt("scriptedNextIntResult"))),
                    )
                    val decision = runBlocking { RandomAgent(random).chooseMove(observation, SearchLimits()) }
                    assertEquals(Cell.of(value.requireInt("expectedCell")), decision.cell)
                    random.assertExhausted()
                }
                else -> error("unsupported AI fixture agent")
            }
        } }
    }

    @Test fun `scripted agent random trace enforces every bound and call in order`() {
        fixtures.cases("scriptedRandomTraces")
            .filter { it.value.requireString("consumer") == "RANDOM_AGENT" }
            .forEach { fixture -> fixture.verify {
                val steps = fixture.value.requireArray("steps").map { it.requireObject("step") }
                val random = ScriptedRandom(steps.map { RandomCall(it.requireInt("nextIntBound"), it.requireInt("scriptedResult")) })
                steps.forEachIndexed { index, step ->
                    assertEquals(index + 1, step.requireInt("call"))
                    val state = step.decisionState()
                    assertEquals(step.requireArray("legalCells").intValues("legalCells"), state.legalCells.map(Cell::index))
                    val decision = runBlocking { RandomAgent(random).chooseMove(state.observation(), SearchLimits()) }
                    assertEquals(Cell.of(step.requireInt("expectedCell")), decision.cell)
                    assertEquals(index + 1, random.callCount)
                }
                random.assertExhausted()
            } }
    }

    private fun com.google.gson.JsonObject.decisionState(): PicPacDecisionState = PicPacDecisionState(
        board = Board.fromSymbols(requireArray("board").symbols()),
        heldSymbol = Symbol.valueOf(requireString("heldSymbol")),
        remainingX = requireInt("remainingX"),
        remainingO = requireInt("remainingO"),
    )

    private fun PicPacDecisionState.observation(): AiObservation = AiObservation(
        board = board,
        activePlayer = Player.ONE,
        agentPlayer = Player.ONE,
        heldSymbol = heldSymbol,
        remainingX = remainingX,
        remainingO = remainingO,
    )

    private fun com.google.gson.JsonObject.fraction(): Double =
        requireInt("numerator").toDouble() / requireInt("denominator")

    private fun JsonArray.symbols(): List<Symbol?> = map { value ->
        if (value.isJsonNull) null else Symbol.valueOf(value.asString)
    }

    private data class RandomCall(val bound: Int, val result: Int)

    private class ScriptedRandom(private val calls: List<RandomCall>) : Random() {
        private var index = 0
        val callCount: Int get() = index

        override fun nextInt(until: Int): Int {
            val call = calls.getOrElse(index) { throw AssertionError("unexpected random call ${index + 1} with bound $until") }
            assertEquals("random bound for call ${index + 1}", call.bound, until)
            assertTrue("scripted result must be within bound", call.result in 0 until until)
            index++
            return call.result
        }

        override fun nextBits(bitCount: Int): Int =
            throw AssertionError("unexpected nextBits($bitCount); bounded nextInt was required")

        fun assertExhausted() = assertEquals("missing scripted random calls", calls.size, index)
    }
}
