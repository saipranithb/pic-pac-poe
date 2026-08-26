package com.thevaguebox.picpac.tools

import com.thevaguebox.picpac.ai.RandomAgent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ToolsTest {
    @Test fun `enumerator reproduces canonical state graph`() {
        val (report, states) = enumerateReachableStates()
        assertEquals(11_065, report.chance)
        assertEquals(21_314, report.decision)
        assertEquals(6_648, report.terminal)
        assertEquals(39_027, report.total)
        assertEquals(report.decision, states.size)
    }

    @Test fun `paired tournament is reproducible and swaps starters`() = runBlocking {
        val factory = AgentFactory { RandomAgent(Random(it)) }
        val first = TournamentRunner().paired("A", factory, "B", factory, pairs = 5, seed = 99)
        val second = TournamentRunner().paired("A", factory, "B", factory, pairs = 5, seed = 99)
        assertEquals(first.copy(averageLatencyMicrosA = 0.0, averageLatencyMicrosB = 0.0), second.copy(averageLatencyMicrosA = 0.0, averageLatencyMicrosB = 0.0))
        assertEquals(10, first.games)
        assertEquals(10, first.winsA + first.winsB + first.draws)
        assertEquals(0, first.invalids)
    }

    @Test fun `negamax learner builds a local policy`() {
        val learner = NegamaxQLearner(seed = 123)
        val report = learner.train(2_000)
        assertEquals(2_000, report.episodes)
        assertTrue(report.states > 100)
        assertTrue(report.meanAbsoluteTdError >= 0.0)
    }
}
