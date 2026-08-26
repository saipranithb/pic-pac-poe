package com.thevaguebox.picpac.core

sealed interface GameOutcome {
    data class Win(
        val player: Player,
        val symbol: Symbol,
        val lines: List<WinningLine>,
    ) : GameOutcome

    data object Draw : GameOutcome
}

sealed interface PicPacPhase {
    data object AwaitingDraw : PicPacPhase
    data class AwaitingPlacement(val held: Symbol, val token: TurnToken) : PicPacPhase
    data class Terminal(val outcome: GameOutcome) : PicPacPhase
}

data class PicPacState(
    val board: Board,
    val activePlayer: Player,
    val remainingX: Int,
    val remainingO: Int,
    val phase: PicPacPhase,
    val starter: Player,
    val revision: Long,
) {
    init {
        require(remainingX in 0..5 && remainingO in 0..5)
        require(revision >= 0)
        val heldX = if ((phase as? PicPacPhase.AwaitingPlacement)?.held == Symbol.X) 1 else 0
        val heldO = if ((phase as? PicPacPhase.AwaitingPlacement)?.held == Symbol.O) 1 else 0
        require(board.count(Symbol.X) + remainingX + heldX == 5) { "X conservation failed" }
        require(board.count(Symbol.O) + remainingO + heldO == 5) { "O conservation failed" }
        if (phase !is PicPacPhase.Terminal) require(!board.hasWinner())
        if (phase is PicPacPhase.AwaitingDraw) require(remainingX + remainingO > 0)
        if (phase is PicPacPhase.Terminal && phase.outcome is GameOutcome.Draw) {
            require(board.isFull && !board.hasWinner())
        }
    }

    val isTerminal: Boolean get() = phase is PicPacPhase.Terminal
    val hiddenTotal: Int get() = remainingX + remainingO
    val nextXProbability: Double get() = if (hiddenTotal == 0) 0.0 else remainingX.toDouble() / hiddenTotal
    val nextOProbability: Double get() = if (hiddenTotal == 0) 0.0 else remainingO.toDouble() / hiddenTotal
}

data class ClassicState(
    val board: Board,
    val activePlayer: Player,
    val starter: Player,
    val turnToken: TurnToken,
    val revision: Long,
    val outcome: GameOutcome? = null,
) {
    init {
        require(revision >= 0)
        if (outcome == null) require(!board.hasWinner())
    }

    val isTerminal: Boolean get() = outcome != null
    fun symbolFor(player: Player): Symbol = if (player == Player.ONE) Symbol.X else Symbol.O
}

enum class RejectionReason {
    WRONG_PHASE,
    STALE_TURN,
    OCCUPIED,
    TERMINAL,
    EXHAUSTED_SYMBOL,
}

sealed interface TransitionResult<out T> {
    data class Accepted<T>(val state: T, val event: GameEvent) : TransitionResult<T>
    data class Rejected(val reason: RejectionReason) : TransitionResult<Nothing>
}

sealed interface GameEvent {
    data class PieceRevealed(val player: Player, val symbol: Symbol) : GameEvent
    data class PiecePlaced(val player: Player, val symbol: Symbol, val cell: Cell) : GameEvent
    data class GameWon(val outcome: GameOutcome.Win) : GameEvent
    data object GameDrawn : GameEvent
}
