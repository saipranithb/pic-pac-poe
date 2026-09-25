package com.thevaguebox.picpac.tools

import com.thevaguebox.picpac.ai.ExpectiminimaxAgent
import com.thevaguebox.picpac.ai.HeuristicAgent
import com.thevaguebox.picpac.ai.RandomAgent
import com.thevaguebox.picpac.ai.RlPolicyAgent
import com.thevaguebox.picpac.ai.StochasticMctsAgent
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

fun main(args: Array<String>) = runBlocking {
    when (args.firstOrNull() ?: "help") {
        "enumerate" -> {
            val (report) = enumerateReachableStates()
            println("chance=${report.chance},decision=${report.decision},terminal=${report.terminal},total=${report.total}")
        }
        "tournament" -> runTournament(args.drop(1))
        "train-rl" -> trainRl(args.drop(1))
        else -> printHelp()
    }
}

private suspend fun runTournament(args: List<String>) {
    val pairs = args.firstOrNull()?.toIntOrNull() ?: 20
    val seed = args.getOrNull(1)?.toLongOrNull() ?: 2_026_08_26L
    val runner = TournamentRunner()
    val matchups = listOf(
        Triple("Random", AgentFactory { RandomAgent(Random(it)) }, "Heuristic" to AgentFactory { HeuristicAgent(Random(it)) }),
        Triple("Heuristic", AgentFactory { HeuristicAgent(Random(it)) }, "Exact" to AgentFactory { ExpectiminimaxAgent() }),
        Triple("MCTS-1k", AgentFactory { StochasticMctsAgent(1_000, random = Random(it)) }, "Exact" to AgentFactory { ExpectiminimaxAgent() }),
    )
    println("agent_a,agent_b,games,wins_a,wins_b,draws,a_starter_wins,b_starter_wins,invalids,avg_plies,latency_us_a,latency_us_b,nodes_a,nodes_b")
    matchups.forEachIndexed { index, (nameA, factoryA, opponent) ->
        println(runner.paired(nameA, factoryA, opponent.first, opponent.second, pairs, seed + index).csv())
    }
}

private suspend fun trainRl(args: List<String>) {
    val episodes = args.firstOrNull()?.toIntOrNull() ?: 250_000
    val output = Path.of(args.getOrNull(1) ?: "game-tools/build/policies/picpac_rl_policy_v1.bin")
    val seed = args.getOrNull(2)?.toLongOrNull() ?: 7_331L
    val learner = NegamaxQLearner(seed = seed)
    val report = learner.train(episodes)
    Files.createDirectories(output.parent)
    Files.newOutputStream(output).use(learner.policy::write)
    val (_, states) = enumerateReachableStates()
    val sample = states.shuffled(Random(seed)).take(1_000)
    val quality = evaluatePolicy(learner.policy, sample)
    println("training=$report")
    println("quality=$quality")
    println("artifact=${output.toAbsolutePath()} bytes=${Files.size(output)}")
    println("inference=${RlPolicyAgent::class.simpleName}")
}

private fun printHelp() {
    println(
        """
        Pic-Pac-Poe offline tools
          enumerate
          tournament [paired-runs] [seed]
          train-rl [episodes] [artifact-path] [seed]
        """.trimIndent(),
    )
}
