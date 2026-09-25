package com.thevaguebox.picpac.core

import java.security.SecureRandom

private fun interface EnvironmentRandomSource {
    fun nextInt(bound: Int): Int
}

private class DefaultEnvironmentRandomSource : EnvironmentRandomSource {
    private val random = SecureRandom()
    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}

class PicPacGameSession private constructor(
    initialState: PicPacState,
    private val random: EnvironmentRandomSource,
) {
    var state: PicPacState = initialState
        private set

    fun reveal(): TransitionResult<PicPacState> {
        if (state.phase is PicPacPhase.Terminal) return TransitionResult.Rejected(RejectionReason.TERMINAL)
        if (state.phase !is PicPacPhase.AwaitingDraw) return TransitionResult.Rejected(RejectionReason.WRONG_PHASE)
        val total = state.hiddenTotal
        val symbol = if (random.nextInt(total) < state.remainingX) Symbol.X else Symbol.O
        return PicPacRules.draw(state, symbol).also { result ->
            if (result is TransitionResult.Accepted) state = result.state
        }
    }

    fun place(cell: Cell, token: TurnToken): TransitionResult<PicPacState> =
        PicPacRules.place(state, cell, token).also { result ->
            if (result is TransitionResult.Accepted) state = result.state
        }

    companion object {
        fun create(starter: Player = Player.ONE, revision: Long = 0): PicPacGameSession =
            PicPacGameSession(PicPacRules.newGame(starter, revision), DefaultEnvironmentRandomSource())

        fun restore(state: PicPacState): PicPacGameSession =
            PicPacGameSession(state, DefaultEnvironmentRandomSource())

        internal fun forTesting(state: PicPacState, draws: Iterator<Int>): PicPacGameSession =
            PicPacGameSession(state, EnvironmentRandomSource { bound -> draws.next().mod(bound) })
    }
}

class ClassicGameSession private constructor(initialState: ClassicState) {
    var state: ClassicState = initialState
        private set

    fun place(cell: Cell, token: TurnToken): TransitionResult<ClassicState> =
        ClassicRules.place(state, cell, token).also { result ->
            if (result is TransitionResult.Accepted) state = result.state
        }

    companion object {
        fun create(starter: Player = Player.ONE, revision: Long = 0): ClassicGameSession =
            ClassicGameSession(ClassicRules.newGame(starter, revision))

        fun restore(state: ClassicState): ClassicGameSession = ClassicGameSession(state)
    }
}
