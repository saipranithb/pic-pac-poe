package com.thevaguebox.probabilistictictactoe.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thevaguebox.picpac.ai.ExpectiminimaxAgent
import com.thevaguebox.picpac.ai.HeuristicAgent
import com.thevaguebox.picpac.ai.RlPolicyAgent
import com.thevaguebox.picpac.ai.StochasticMctsAgent
import com.thevaguebox.picpac.ai.TabularPolicy
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.ClassicGameSession
import com.thevaguebox.picpac.core.ClassicState
import com.thevaguebox.picpac.core.GameEvent
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.PicPacGameSession
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.PicPacState
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.TransitionResult
import com.thevaguebox.picpac.core.TurnToken
import com.thevaguebox.picpac.core.WinningLine
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiObservation
import com.thevaguebox.picpac.core.ai.SearchLimits
import com.thevaguebox.probabilistictictactoe.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class AppScreen { HOME, GAME, HOW_TO, SETTINGS, AI_LAB }
enum class GameMode { CLASSIC_LOCAL, PIC_PAC_LOCAL, PIC_PAC_AI }
enum class Difficulty(val title: String, val description: String, val production: Boolean = true) {
    EASY("Easy", "Quick thinker. Makes human mistakes."),
    MEDIUM("Medium", "Looks ahead and weighs the odds."),
    HARD("Hard", "Solves the probabilities. Good luck."),
    MCTS("MCTS Lab", "Learns by sampling thousands of possible futures.", false),
    RL("RL Lab", "A compact policy trained through offline self-play.", false),
}
enum class TurnStage { HANDOFF, REVEALING, PLAYING, AI_THINKING, TERMINAL }
enum class UiEffect { REVEAL, PLACE, WIN, DRAW }

data class GameUiState(
    val screen: AppScreen = AppScreen.HOME,
    val mode: GameMode? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val classic: ClassicState? = null,
    val picPac: PicPacState? = null,
    val stage: TurnStage = TurnStage.PLAYING,
    val effectId: Long = 0,
    val effect: UiEffect? = null,
) {
    val board: Board get() = classic?.board ?: picPac?.board ?: Board.EMPTY
    val activePlayer: Player get() = classic?.activePlayer ?: picPac?.activePlayer ?: Player.ONE
    val outcome: GameOutcome? get() = classic?.outcome ?: (picPac?.phase as? PicPacPhase.Terminal)?.outcome
    val heldSymbol: Symbol? get() = (picPac?.phase as? PicPacPhase.AwaitingPlacement)?.held
    val winningLine: WinningLine? get() = (outcome as? GameOutcome.Win)?.lines?.firstOrNull()
    val isPicPac: Boolean get() = mode != GameMode.CLASSIC_LOCAL
}

class GameViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private var revision = savedStateHandle[KEY_REVISION] ?: 0L
    private var nextStarter = savedStateHandle.get<String>(KEY_NEXT_STARTER)?.let(Player::valueOf) ?: Player.ONE
    private var classicSession: ClassicGameSession? = null
    private var picPacSession: PicPacGameSession? = null
    private var aiJob: Job? = null
    private var pendingAi: PendingAi? = null
    private var revealAcknowledged = false

    private val easyAgent: AiAgent = HeuristicAgent(Random(System.nanoTime()))
    private val mediumAgent: AiAgent = ExpectiminimaxAgent(configuredDepth = 4)
    private val hardAgent: AiAgent = ExpectiminimaxAgent()
    private val mctsAgent: AiAgent = StochasticMctsAgent(defaultSimulations = 2_000, random = Random(System.nanoTime() xor 0x4D435453L))
    private val rlAgent: AiAgent by lazy {
        val policy = runCatching {
            getApplication<Application>().resources.openRawResource(R.raw.picpac_rl_policy_v1).use(TabularPolicy::read)
        }.getOrElse { TabularPolicy.empty() }
        RlPolicyAgent(policy)
    }

    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        restoreSessions()
        val state = _uiState.value
        if (state.screen == AppScreen.GAME && state.mode == GameMode.PIC_PAC_AI && state.stage != TurnStage.TERMINAL) {
            when (state.picPac?.phase) {
                PicPacPhase.AwaitingDraw -> revealCurrentPiece()
                is PicPacPhase.AwaitingPlacement -> if (state.activePlayer == Player.TWO) {
                    revealAcknowledged = state.stage == TurnStage.AI_THINKING
                    resumeAiTurnIfNeeded()
                } else if (state.stage == TurnStage.AI_THINKING) {
                    update(state.copy(stage = TurnStage.REVEALING))
                }
                else -> Unit
            }
        }
    }

    fun show(screen: AppScreen) {
        if (screen != AppScreen.GAME) cancelAi()
        update(_uiState.value.copy(screen = screen, effect = null))
    }

    fun goHome() {
        cancelAi()
        classicSession = null
        picPacSession = null
        clearSavedGame()
        update(GameUiState(screen = AppScreen.HOME))
    }

    fun startClassic() {
        beginNewGame(GameMode.CLASSIC_LOCAL, Difficulty.MEDIUM)
    }

    fun startPicPacLocal() {
        beginNewGame(GameMode.PIC_PAC_LOCAL, Difficulty.MEDIUM)
    }

    fun startPicPacAi(difficulty: Difficulty) {
        beginNewGame(GameMode.PIC_PAC_AI, difficulty)
    }

    fun readyForReveal() {
        val current = _uiState.value
        if (current.stage != TurnStage.HANDOFF) return
        revealCurrentPiece()
    }

    fun revealAnimationFinished() {
        val current = _uiState.value
        if (current.stage != TurnStage.REVEALING) return
        revealAcknowledged = true
        if (current.mode == GameMode.PIC_PAC_AI && current.activePlayer == Player.TWO) {
            val pending = pendingAi
            if (pending != null) applyAiDecision(pending) else update(current.copy(stage = TurnStage.AI_THINKING))
        } else {
            update(current.copy(stage = TurnStage.PLAYING))
        }
    }

    fun place(cellIndex: Int) {
        val current = _uiState.value
        if (current.screen != AppScreen.GAME || current.stage != TurnStage.PLAYING) return
        val cell = Cell.of(cellIndex)
        when (current.mode) {
            GameMode.CLASSIC_LOCAL -> placeClassic(cell)
            GameMode.PIC_PAC_LOCAL -> placePicPac(cell)
            GameMode.PIC_PAC_AI -> if (current.activePlayer == Player.ONE) placePicPac(cell)
            null -> Unit
        }
    }

    fun rematch() {
        val current = _uiState.value
        val mode = current.mode ?: return
        nextStarter = when (mode) {
            GameMode.CLASSIC_LOCAL -> current.classic?.starter?.other() ?: nextStarter.other()
            else -> current.picPac?.starter?.other() ?: nextStarter.other()
        }
        savedStateHandle[KEY_NEXT_STARTER] = nextStarter.name
        beginNewGame(mode, current.difficulty)
    }

    private fun beginNewGame(mode: GameMode, difficulty: Difficulty) {
        cancelAi()
        revision++
        savedStateHandle[KEY_REVISION] = revision
        val starter = nextStarter
        if (mode == GameMode.CLASSIC_LOCAL) {
            val session = ClassicGameSession.create(starter, revision)
            classicSession = session
            picPacSession = null
            update(
                GameUiState(
                    screen = AppScreen.GAME,
                    mode = mode,
                    difficulty = difficulty,
                    classic = session.state,
                    stage = TurnStage.PLAYING,
                ),
            )
        } else {
            val session = PicPacGameSession.create(starter, revision)
            picPacSession = session
            classicSession = null
            val stage = if (mode == GameMode.PIC_PAC_LOCAL) TurnStage.HANDOFF else TurnStage.REVEALING
            update(
                GameUiState(
                    screen = AppScreen.GAME,
                    mode = mode,
                    difficulty = difficulty,
                    picPac = session.state,
                    stage = stage,
                ),
            )
            if (mode == GameMode.PIC_PAC_AI) revealCurrentPiece()
        }
    }

    private fun revealCurrentPiece() {
        val session = picPacSession ?: return
        val result = session.reveal()
        if (result !is TransitionResult.Accepted) return
        revealAcknowledged = false
        pendingAi = null
        val next = _uiState.value.copy(
            picPac = result.state,
            stage = TurnStage.REVEALING,
        ).withEffect(UiEffect.REVEAL)
        update(next)
        if (next.mode == GameMode.PIC_PAC_AI && result.state.activePlayer == Player.TWO) launchAi(result.state)
    }

    private fun placeClassic(cell: Cell) {
        val session = classicSession ?: return
        val result = session.place(cell, session.state.turnToken)
        if (result !is TransitionResult.Accepted) return
        val terminal = result.state.isTerminal
        update(
            _uiState.value.copy(
                classic = result.state,
                stage = if (terminal) TurnStage.TERMINAL else TurnStage.PLAYING,
            ).withEffect(effectFor(result.event)),
        )
    }

    private fun placePicPac(cell: Cell) {
        val session = picPacSession ?: return
        val phase = session.state.phase as? PicPacPhase.AwaitingPlacement ?: return
        val result = session.place(cell, phase.token)
        if (result !is TransitionResult.Accepted) return
        if (result.state.isTerminal) {
            update(
                _uiState.value.copy(picPac = result.state, stage = TurnStage.TERMINAL)
                    .withEffect(effectFor(result.event)),
            )
            return
        }
        val base = _uiState.value.copy(picPac = result.state).withEffect(UiEffect.PLACE)
        update(base)
        when (base.mode) {
            GameMode.PIC_PAC_LOCAL -> update(base.copy(stage = TurnStage.HANDOFF))
            GameMode.PIC_PAC_AI -> revealCurrentPiece()
            else -> Unit
        }
    }

    private fun launchAi(state: PicPacState) {
        val phase = state.phase as? PicPacPhase.AwaitingPlacement ?: return
        val request = AiRequest(state.revision, phase.token)
        val observation = AiObservation.from(state, Player.TWO)
        val agent = when (_uiState.value.difficulty) {
            Difficulty.EASY -> easyAgent
            Difficulty.MEDIUM -> mediumAgent
            Difficulty.HARD -> hardAgent
            Difficulty.MCTS -> mctsAgent
            Difficulty.RL -> rlAgent
        }
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            try {
                val decision = withContext(Dispatchers.Default) {
                    agent.chooseMove(observation, SearchLimits())
                }
                val pending = PendingAi(request, decision.cell)
                pendingAi = pending
                if (revealAcknowledged) applyAiDecision(pending)
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Throwable) {
                val fallback = observation.legalCells.first()
                val pending = PendingAi(request, fallback)
                pendingAi = pending
                if (revealAcknowledged) applyAiDecision(pending)
            }
        }
    }

    private fun applyAiDecision(pending: PendingAi) {
        val session = picPacSession ?: return
        val state = session.state
        val phase = state.phase as? PicPacPhase.AwaitingPlacement ?: return
        if (state.revision != pending.request.revision || phase.token != pending.request.token) return
        if (state.activePlayer != Player.TWO || state.board[pending.cell] != null) return
        pendingAi = null
        val result = session.place(pending.cell, pending.request.token)
        if (result !is TransitionResult.Accepted) return
        if (result.state.isTerminal) {
            update(
                _uiState.value.copy(picPac = result.state, stage = TurnStage.TERMINAL)
                    .withEffect(effectFor(result.event)),
            )
        } else {
            update(_uiState.value.copy(picPac = result.state).withEffect(UiEffect.PLACE))
            revealCurrentPiece()
        }
    }

    private fun resumeAiTurnIfNeeded() {
        val state = picPacSession?.state ?: return
        when (state.phase) {
            PicPacPhase.AwaitingDraw -> revealCurrentPiece()
            is PicPacPhase.AwaitingPlacement -> launchAi(state)
            is PicPacPhase.Terminal -> Unit
        }
    }

    private fun cancelAi() {
        aiJob?.cancel()
        aiJob = null
        pendingAi = null
        revealAcknowledged = false
    }

    private fun update(state: GameUiState) {
        _uiState.value = state
        persist(state)
    }

    private fun GameUiState.withEffect(effect: UiEffect): GameUiState =
        copy(effectId = effectId + 1, effect = effect)

    private fun effectFor(event: GameEvent): UiEffect = when (event) {
        is GameEvent.GameWon -> UiEffect.WIN
        GameEvent.GameDrawn -> UiEffect.DRAW
        else -> UiEffect.PLACE
    }

    private fun persist(state: GameUiState) {
        savedStateHandle[KEY_SCREEN] = state.screen.name
        savedStateHandle[KEY_MODE] = state.mode?.name
        savedStateHandle[KEY_DIFFICULTY] = state.difficulty.name
        savedStateHandle[KEY_STAGE] = state.stage.name
        savedStateHandle[KEY_NEXT_STARTER] = nextStarter.name
        state.classic?.let(::saveClassic)
        state.picPac?.let(::savePicPac)
    }

    private fun saveClassic(state: ClassicState) {
        savedStateHandle[KEY_KIND] = "classic"
        savedStateHandle[KEY_BOARD] = state.board.code
        savedStateHandle[KEY_ACTIVE] = state.activePlayer.name
        savedStateHandle[KEY_STARTER] = state.starter.name
        savedStateHandle[KEY_TOKEN] = state.turnToken.value
        saveOutcome(state.outcome)
    }

    private fun savePicPac(state: PicPacState) {
        savedStateHandle[KEY_KIND] = "picpac"
        savedStateHandle[KEY_BOARD] = state.board.code
        savedStateHandle[KEY_ACTIVE] = state.activePlayer.name
        savedStateHandle[KEY_STARTER] = state.starter.name
        savedStateHandle[KEY_REMAINING_X] = state.remainingX
        savedStateHandle[KEY_REMAINING_O] = state.remainingO
        when (val phase = state.phase) {
            PicPacPhase.AwaitingDraw -> savedStateHandle[KEY_PHASE] = "draw"
            is PicPacPhase.AwaitingPlacement -> {
                savedStateHandle[KEY_PHASE] = "place"
                savedStateHandle[KEY_HELD] = phase.held.name
                savedStateHandle[KEY_TOKEN] = phase.token.value
            }
            is PicPacPhase.Terminal -> {
                savedStateHandle[KEY_PHASE] = "terminal"
                saveOutcome(phase.outcome)
            }
        }
    }

    private fun saveOutcome(outcome: GameOutcome?) {
        when (outcome) {
            null -> savedStateHandle[KEY_OUTCOME] = null
            GameOutcome.Draw -> savedStateHandle[KEY_OUTCOME] = "draw"
            is GameOutcome.Win -> {
                savedStateHandle[KEY_OUTCOME] = "win"
                savedStateHandle[KEY_WINNER] = outcome.player.name
                savedStateHandle[KEY_WIN_SYMBOL] = outcome.symbol.name
            }
        }
    }

    private fun restoreState(): GameUiState {
        val screen = savedStateHandle.get<String>(KEY_SCREEN)?.let(AppScreen::valueOf) ?: AppScreen.HOME
        val mode = savedStateHandle.get<String>(KEY_MODE)?.let(GameMode::valueOf)
        val difficulty = savedStateHandle.get<String>(KEY_DIFFICULTY)?.let(Difficulty::valueOf) ?: Difficulty.MEDIUM
        val stage = savedStateHandle.get<String>(KEY_STAGE)?.let(TurnStage::valueOf) ?: TurnStage.PLAYING
        if (screen != AppScreen.GAME || mode == null) return GameUiState(screen = screen, difficulty = difficulty)
        return try {
            if (savedStateHandle.get<String>(KEY_KIND) == "classic") {
                GameUiState(screen, mode, difficulty, classic = restoreClassic(), stage = stage)
            } else {
                GameUiState(screen, mode, difficulty, picPac = restorePicPac(), stage = stage)
            }
        } catch (_: Throwable) {
            clearSavedGame()
            GameUiState()
        }
    }

    private fun restoreSessions() {
        _uiState.value.classic?.let { classicSession = ClassicGameSession.restore(it) }
        _uiState.value.picPac?.let { picPacSession = PicPacGameSession.restore(it) }
    }

    private fun restoreClassic(): ClassicState {
        val board = Board.fromCode(requireNotNull(savedStateHandle[KEY_BOARD]))
        return ClassicState(
            board = board,
            activePlayer = Player.valueOf(requireNotNull(savedStateHandle[KEY_ACTIVE])),
            starter = Player.valueOf(requireNotNull(savedStateHandle[KEY_STARTER])),
            turnToken = TurnToken(requireNotNull(savedStateHandle[KEY_TOKEN])),
            revision = revision,
            outcome = restoreOutcome(board),
        )
    }

    private fun restorePicPac(): PicPacState {
        val board = Board.fromCode(requireNotNull(savedStateHandle[KEY_BOARD]))
        val phase = when (savedStateHandle.get<String>(KEY_PHASE)) {
            "draw" -> PicPacPhase.AwaitingDraw
            "place" -> PicPacPhase.AwaitingPlacement(
                Symbol.valueOf(requireNotNull(savedStateHandle[KEY_HELD])),
                TurnToken(requireNotNull(savedStateHandle[KEY_TOKEN])),
            )
            "terminal" -> PicPacPhase.Terminal(requireNotNull(restoreOutcome(board)))
            else -> error("Missing phase")
        }
        return PicPacState(
            board = board,
            activePlayer = Player.valueOf(requireNotNull(savedStateHandle[KEY_ACTIVE])),
            remainingX = requireNotNull(savedStateHandle[KEY_REMAINING_X]),
            remainingO = requireNotNull(savedStateHandle[KEY_REMAINING_O]),
            phase = phase,
            starter = Player.valueOf(requireNotNull(savedStateHandle[KEY_STARTER])),
            revision = revision,
        )
    }

    private fun restoreOutcome(board: Board): GameOutcome? = when (savedStateHandle.get<String>(KEY_OUTCOME)) {
        "draw" -> GameOutcome.Draw
        "win" -> {
            val symbol = Symbol.valueOf(requireNotNull(savedStateHandle[KEY_WIN_SYMBOL]))
            GameOutcome.Win(
                Player.valueOf(requireNotNull(savedStateHandle[KEY_WINNER])),
                symbol,
                board.winningLines(symbol),
            )
        }
        else -> null
    }

    private fun clearSavedGame() {
        listOf(
            KEY_KIND, KEY_BOARD, KEY_ACTIVE, KEY_STARTER, KEY_TOKEN, KEY_PHASE, KEY_HELD,
            KEY_REMAINING_X, KEY_REMAINING_O, KEY_OUTCOME, KEY_WINNER, KEY_WIN_SYMBOL, KEY_MODE,
        ).forEach { savedStateHandle.remove<Any?>(it) }
    }

    private data class AiRequest(val revision: Long, val token: TurnToken)
    private data class PendingAi(val request: AiRequest, val cell: Cell)

    private companion object {
        const val KEY_SCREEN = "screen"
        const val KEY_MODE = "mode"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_STAGE = "stage"
        const val KEY_REVISION = "revision"
        const val KEY_NEXT_STARTER = "next_starter"
        const val KEY_KIND = "kind"
        const val KEY_BOARD = "board"
        const val KEY_ACTIVE = "active"
        const val KEY_STARTER = "starter"
        const val KEY_TOKEN = "token"
        const val KEY_PHASE = "phase"
        const val KEY_HELD = "held"
        const val KEY_REMAINING_X = "remaining_x"
        const val KEY_REMAINING_O = "remaining_o"
        const val KEY_OUTCOME = "outcome"
        const val KEY_WINNER = "winner"
        const val KEY_WIN_SYMBOL = "win_symbol"
    }
}
