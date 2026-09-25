package com.thevaguebox.picpac.core

import com.thevaguebox.picpac.core.ai.PicPacChanceState
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchTransition
import com.thevaguebox.picpac.testing.GoldenFixtureDocument
import com.thevaguebox.picpac.testing.requireInt
import org.junit.Assert.assertEquals
import org.junit.Test

class ReachabilityTest {
    @Test fun `canonical graph matches independently audited counts`() {
        val chance = hashSetOf<ChanceKey>()
        val decision = hashSetOf<DecisionKey>()
        val terminal = hashSetOf<TerminalKey>()

        fun visitChance(state: PicPacChanceState) {
            if (!chance.add(ChanceKey(state.board.code, state.remainingX, state.remainingO))) return
            if (state.remainingX > 0) visitDecision(PublicPicPacSearchModel.draw(state, Symbol.X))
            if (state.remainingO > 0) visitDecision(PublicPicPacSearchModel.draw(state, Symbol.O))
        }

        fun visitDecisionImpl(state: PicPacDecisionState) {
            if (!decision.add(DecisionKey(state.board.code, state.heldSymbol, state.remainingX, state.remainingO))) return
            state.legalCells.forEach { cell ->
                when (val result = PublicPicPacSearchModel.place(state, cell)) {
                    SearchTransition.Win -> terminal += TerminalKey(
                        state.board.place(cell, state.heldSymbol).code,
                        state.remainingX,
                        state.remainingO,
                        true,
                    )
                    SearchTransition.Draw -> terminal += TerminalKey(
                        state.board.place(cell, state.heldSymbol).code,
                        state.remainingX,
                        state.remainingO,
                        false,
                    )
                    is SearchTransition.Chance -> visitChance(result.state)
                }
            }
        }

        decisionVisitor = ::visitDecisionImpl
        visitChance(PicPacChanceState(Board.EMPTY, 5, 5))

        val oracle = GoldenFixtureDocument.load().objectValue("graphOracle")
        assertEquals(oracle.requireInt("chanceStates"), chance.size)
        assertEquals(oracle.requireInt("decisionStates"), decision.size)
        assertEquals(oracle.requireInt("terminalStates"), terminal.size)
        assertEquals(oracle.requireInt("totalStates"), chance.size + decision.size + terminal.size)
    }

    private data class ChanceKey(val board: Int, val x: Int, val o: Int)
    private data class DecisionKey(val board: Int, val held: Symbol, val x: Int, val o: Int)
    private data class TerminalKey(val board: Int, val x: Int, val o: Int, val win: Boolean)

    companion object {
        private lateinit var decisionVisitor: (PicPacDecisionState) -> Unit
        private fun visitDecision(state: PicPacDecisionState) = decisionVisitor(state)
    }
}
