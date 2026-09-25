package com.thevaguebox.picpac.tools

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDiagnostics
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.picpac.core.ai.SearchTransition
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.system.measureNanoTime

fun interface AgentFactory {
    fun create(seed: Long): AiAgent
}

data class MatchResult(
    val winner: Int?,
    val starter: Int,
    val plies: Int,
    val latencyNanos: LongArray,
    val nodes: LongArray,
    val invalidSide: Int? = null,
)

class MatchRunner(private val timeoutMillis: Long = 2_000) {
    suspend fun run(
        agentA: AiAgent,
        agentB: AiAgent,
        environmentSeed: Long,
        starter: Int,
    ): MatchResult {
        require(starter == 0 || starter == 1)
        val agents = listOf(agentA, agentB)
        val random = Random(environmentSeed)
        var side = starter
        var decision = PublicPicPacSearchModel.draw(
            PicPacChanceState(Board.EMPTY, 5, 5),
            sample(PicPacChanceState(Board.EMPTY, 5, 5), random),
        )
        val latency = LongArray(2)
        val nodes = LongArray(2)
        var plies = 0
        while (true) {
            val player = if (side == 0) Player.ONE else Player.TWO
            val observation = AiObservation(
                board = decision.board,
                activePlayer = player,
                agentPlayer = player,
                heldSymbol = decision.heldSymbol,
                remainingX = decision.remainingX,
                remainingO = decision.remainingO,
            )
            var chosen: com.thevaguebox.picpac.core.Cell? = null
            var diagnostics: AiDiagnostics = AiDiagnostics.None
            try {
                latency[side] += measureNanoTime {
                    val result = withTimeout(timeoutMillis) {
                        agents[side].chooseMove(
                            observation,
                            SearchLimits(deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000),
                        )
                    }
                    chosen = result.cell
                    diagnostics = result.diagnostics
                }
            } catch (_: Throwable) {
                return MatchResult(1 - side, starter, plies, latency, nodes, invalidSide = side)
            }
            val cell = chosen
            if (cell == null || cell !in decision.legalCells) {
                return MatchResult(1 - side, starter, plies, latency, nodes, invalidSide = side)
            }
            (diagnostics as? AiDiagnostics.Search)?.let { nodes[side] += it.nodes }
            plies++
            when (val transition = PublicPicPacSearchModel.place(decision, cell)) {
                SearchTransition.Win -> return MatchResult(side, starter, plies, latency, nodes)
                SearchTransition.Draw -> return MatchResult(null, starter, plies, latency, nodes)
                is SearchTransition.Chance -> {
                    side = 1 - side
                    decision = PublicPicPacSearchModel.draw(transition.state, sample(transition.state, random))
                }
            }
        }
    }

    private fun sample(chance: PicPacChanceState, random: Random): Symbol =
        if (random.nextInt(chance.total) < chance.remainingX) Symbol.X else Symbol.O
}

data class TournamentSummary(
    val nameA: String,
    val nameB: String,
    val games: Int,
    val winsA: Int,
    val winsB: Int,
    val draws: Int,
    val aAsStarterWins: Int,
    val bAsStarterWins: Int,
    val invalids: Int,
    val averagePlies: Double,
    val averageLatencyMicrosA: Double,
    val averageLatencyMicrosB: Double,
    val averageNodesA: Double,
    val averageNodesB: Double,
) {
    fun csv(): String = listOf(
        nameA, nameB, games, winsA, winsB, draws, aAsStarterWins, bAsStarterWins,
        invalids, averagePlies, averageLatencyMicrosA, averageLatencyMicrosB,
        averageNodesA, averageNodesB,
    ).joinToString(",")
}

class TournamentRunner(private val matchRunner: MatchRunner = MatchRunner()) {
    suspend fun paired(
        nameA: String,
        factoryA: AgentFactory,
        nameB: String,
        factoryB: AgentFactory,
        pairs: Int,
        seed: Long,
    ): TournamentSummary {
        require(pairs > 0)
        val results = ArrayList<MatchResult>(pairs * 2)
        repeat(pairs) { pair ->
            val envSeed = mix(seed, pair.toLong())
            val aSeed = mix(seed xor 0xA11CE, pair.toLong())
            val bSeed = mix(seed xor 0xB0B, pair.toLong())
            results += matchRunner.run(factoryA.create(aSeed), factoryB.create(bSeed), envSeed, starter = 0)
            results += matchRunner.run(factoryA.create(aSeed), factoryB.create(bSeed), envSeed, starter = 1)
        }
        val winsA = results.count { it.winner == 0 }
        val winsB = results.count { it.winner == 1 }
        return TournamentSummary(
            nameA = nameA,
            nameB = nameB,
            games = results.size,
            winsA = winsA,
            winsB = winsB,
            draws = results.count { it.winner == null },
            aAsStarterWins = results.count { it.starter == 0 && it.winner == 0 },
            bAsStarterWins = results.count { it.starter == 1 && it.winner == 1 },
            invalids = results.count { it.invalidSide != null },
            averagePlies = results.map { it.plies }.average(),
            averageLatencyMicrosA = results.map { it.latencyNanos[0] / 1_000.0 }.average(),
            averageLatencyMicrosB = results.map { it.latencyNanos[1] / 1_000.0 }.average(),
            averageNodesA = results.map { it.nodes[0].toDouble() }.average(),
            averageNodesB = results.map { it.nodes[1].toDouble() }.average(),
        )
    }

    private fun mix(seed: Long, index: Long): Long {
        var value = seed + index * -7046029254386353131L
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }
}
