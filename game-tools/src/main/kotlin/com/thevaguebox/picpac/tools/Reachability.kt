package com.thevaguebox.picpac.tools

import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchTransition

data class ReachabilityReport(val chance: Int, val decision: Int, val terminal: Int) {
    val total: Int get() = chance + decision + terminal
}

fun enumerateReachableStates(): Pair<ReachabilityReport, List<PicPacDecisionState>> {
    val chanceSeen = hashSetOf<PicPacChanceState>()
    val decisions = linkedSetOf<PicPacDecisionState>()
    val terminals = hashSetOf<TerminalKey>()
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
                when (val transition = PublicPicPacSearchModel.place(state, cell)) {
                    SearchTransition.Win -> terminals += TerminalKey(
                        state.board.place(cell, state.heldSymbol).code,
                        state.remainingX,
                        state.remainingO,
                        true,
                    )
                    SearchTransition.Draw -> terminals += TerminalKey(
                        state.board.place(cell, state.heldSymbol).code,
                        state.remainingX,
                        state.remainingO,
                        false,
                    )
                    is SearchTransition.Chance -> visitChance(transition.state)
                }
            }
        }
    }
    visitChance(PicPacChanceState(Board.EMPTY, 5, 5))
    return ReachabilityReport(chanceSeen.size, decisions.size, terminals.size) to decisions.toList()
}

private data class TerminalKey(val board: Int, val x: Int, val o: Int, val win: Boolean)
