package com.thevaguebox.picpac.ai

import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.PicPacDecisionState
import com.thevaguebox.picpac.core.ai.PublicPicPacSearchModel
import com.thevaguebox.picpac.core.ai.SearchLimits
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

class TabularPolicy private constructor(
    private val rows: MutableMap<Long, FloatArray>,
) {
    val stateCount: Int get() = rows.size

    fun value(state: PicPacDecisionState, cell: Cell): Float = rows[key(state)]?.get(cell.index) ?: 0f

    fun update(state: PicPacDecisionState, cell: Cell, alpha: Double, target: Double) {
        val row = rows.getOrPut(key(state)) { FloatArray(9) }
        val old = row[cell.index]
        row[cell.index] = (old + alpha * (target - old)).toFloat()
    }

    fun bestCell(state: PicPacDecisionState): Cell = PositionEvaluator.ordered(state.legalCells)
        .maxBy { value(state, it) }

    fun maxValue(state: PicPacDecisionState): Double = state.legalCells.maxOf { value(state, it).toDouble() }

    fun write(output: OutputStream) {
        DataOutputStream(output.buffered()).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(VERSION)
            data.writeUTF(RULES_ID)
            val nonZero = rows.filterValues { row -> row.any { it != 0f } }
            data.writeInt(nonZero.size)
            nonZero.toSortedMap().forEach { (key, row) ->
                data.writeLong(key)
                row.forEach(data::writeFloat)
            }
        }
    }

    companion object {
        const val RULES_ID = "picpac-5x5-draw-before-place-v1"
        private const val MAGIC = 0x50505051
        private const val VERSION = 1

        fun empty(): TabularPolicy = TabularPolicy(hashMapOf())

        fun read(input: InputStream): TabularPolicy {
            DataInputStream(input.buffered()).use { data ->
                require(data.readInt() == MAGIC) { "Not a Pic-Pac policy" }
                require(data.readInt() == VERSION) { "Unsupported policy version" }
                require(data.readUTF() == RULES_ID) { "Policy rules mismatch" }
                val count = data.readInt()
                require(count in 0..100_000)
                val rows = HashMap<Long, FloatArray>(count)
                repeat(count) {
                    rows[data.readLong()] = FloatArray(9) { data.readFloat() }
                }
                return TabularPolicy(rows)
            }
        }

        fun key(state: PicPacDecisionState): Long {
            val held = if (state.heldSymbol == Symbol.X) 0L else 1L
            return state.board.code.toLong() or
                (held shl 15) or
                (state.remainingX.toLong() shl 16) or
                (state.remainingO.toLong() shl 19)
        }
    }
}

class RlPolicyAgent(
    private val policy: TabularPolicy,
    private val fallback: AiAgent = HeuristicAgent(),
) : AiAgent {
    override suspend fun chooseMove(observation: AiObservation, limits: SearchLimits): AiDecision {
        val state = PublicPicPacSearchModel.from(observation)
        return if (policy.stateCount == 0) fallback.chooseMove(observation, limits)
        else AiDecision(policy.bestCell(state))
    }
}
