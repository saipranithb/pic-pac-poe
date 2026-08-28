package com.thevaguebox.probabilistictictactoe.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.PicPacState
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `local turn hands off then reveals before placement`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startPicPacLocal()
        assertEquals(TurnStage.HANDOFF, viewModel.uiState.value.stage)

        viewModel.readyForReveal()
        val revealed = viewModel.uiState.value
        assertEquals(TurnStage.REVEALING, revealed.stage)
        assertNotNull(revealed.heldSymbol)
        assertEquals(9, revealed.picPac!!.hiddenTotal)

        viewModel.presentationStepFinished(revealed.presentationId)
        assertEquals(TurnStage.PLAYING, viewModel.uiState.value.stage)
        viewModel.place(4)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)
        assertEquals(TurnStage.HANDOFF, viewModel.uiState.value.stage)
    }

    @Test fun `rapid duplicate placement is accepted once`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startPicPacLocal()
        viewModel.readyForReveal()
        viewModel.presentationStepFinished(viewModel.uiState.value.presentationId)
        viewModel.place(0)
        viewModel.place(1)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)
    }

    @Test fun `rematch alternates starting player`() {
        val viewModel = GameViewModel(application, SavedStateHandle())
        viewModel.startClassic()
        assertEquals(Player.ONE, viewModel.uiState.value.activePlayer)
        viewModel.rematch()
        assertEquals(Player.TWO, viewModel.uiState.value.activePlayer)
        viewModel.rematch()
        assertEquals(Player.ONE, viewModel.uiState.value.activePlayer)
    }

    @Test fun `saved held piece survives recreation`() {
        val handle = SavedStateHandle()
        val original = GameViewModel(application, handle)
        original.startPicPacLocal()
        original.readyForReveal()
        val before = original.uiState.value.picPac!!
        val restoredViewModel = GameViewModel(application, handle)
        val restored = restoredViewModel.uiState.value.picPac!!
        assertEquals(before, restored)
        assertEquals(TurnStage.REVEALING, restoredViewModel.uiState.value.stage)
        assertTrue(restored.phase is PicPacPhase.AwaitingPlacement)
    }

    @Test fun `computer turn is serialized between human reveals and gates input`() {
        val viewModel = testAiViewModel()
        viewModel.startPicPacAi(Difficulty.EASY)

        assertEquals(TurnStage.TURN_START, viewModel.uiState.value.stage)
        assertEquals("Your turn", viewModel.uiState.value.turnLabel())
        finishPresentation(viewModel)

        val firstHumanReveal = viewModel.uiState.value
        assertEquals(TurnStage.REVEALING, firstHumanReveal.stage)
        assertEquals("You drew ${firstHumanReveal.heldSymbol!!.name}", firstHumanReveal.drawLabel())
        finishPresentation(viewModel)
        assertEquals(TurnStage.PLAYING, viewModel.uiState.value.stage)

        viewModel.place(0)
        val computerTurn = viewModel.uiState.value
        assertEquals(TurnStage.TURN_START, computerTurn.stage)
        assertEquals("Computer's turn", computerTurn.turnLabel())
        assertEquals(1, computerTurn.board.occupiedCount)
        viewModel.place(1)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)

        finishPresentation(viewModel)
        dispatcher.scheduler.advanceUntilIdle()
        val computerReveal = viewModel.uiState.value
        assertEquals(TurnStage.REVEALING, computerReveal.stage)
        assertEquals("Computer drew ${computerReveal.heldSymbol!!.name}", computerReveal.drawLabel())
        viewModel.place(1)
        assertEquals(1, viewModel.uiState.value.board.occupiedCount)

        finishPresentation(viewModel)
        val targeting = viewModel.uiState.value
        assertEquals(TurnStage.AI_TARGETING, targeting.stage)
        assertNotNull(targeting.aiTargetCell)
        assertEquals(1, targeting.board.occupiedCount)

        finishPresentation(viewModel)
        val placing = viewModel.uiState.value
        assertEquals(TurnStage.AI_PLACING, placing.stage)
        assertEquals(2, placing.board.occupiedCount)

        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_SETTLING, viewModel.uiState.value.stage)
        finishPresentation(viewModel)
        assertEquals(TurnStage.TURN_START, viewModel.uiState.value.stage)
        assertEquals("Your turn", viewModel.uiState.value.turnLabel())

        finishPresentation(viewModel)
        val secondHumanReveal = viewModel.uiState.value
        assertEquals(TurnStage.REVEALING, secondHumanReveal.stage)
        assertEquals("You drew ${secondHumanReveal.heldSymbol!!.name}", secondHumanReveal.drawLabel())
        finishPresentation(viewModel)
        assertEquals(TurnStage.PLAYING, viewModel.uiState.value.stage)
    }

    @Test fun `restart during computer targeting rejects stale presentation callback`() {
        val viewModel = testAiViewModel()
        advanceToComputerTargeting(viewModel)
        val staleId = viewModel.uiState.value.presentationId

        viewModel.rematch()
        val restarted = viewModel.uiState.value
        assertEquals(0, restarted.board.occupiedCount)
        assertEquals(TurnStage.TURN_START, restarted.stage)

        viewModel.presentationStepFinished(staleId)
        assertEquals(restarted, viewModel.uiState.value)
    }

    @Test fun `computer targeting survives recreation without replaying the draw`() {
        val handle = SavedStateHandle()
        val original = testAiViewModel(handle)
        advanceToComputerTargeting(original)
        val before = original.uiState.value

        val restored = testAiViewModel(handle)
        assertEquals(TurnStage.AI_TARGETING, restored.uiState.value.stage)
        assertEquals(before.presentationId, restored.uiState.value.presentationId)
        assertEquals(before.aiTargetCell, restored.uiState.value.aiTargetCell)
        assertEquals(1, restored.uiState.value.board.occupiedCount)

        finishPresentation(restored)
        assertEquals(TurnStage.AI_PLACING, restored.uiState.value.stage)
        assertEquals(2, restored.uiState.value.board.occupiedCount)
    }

    @Test fun `vs computer actor labels never expose domain player names`() {
        val state = GameUiState(mode = GameMode.PIC_PAC_AI)
        assertEquals("You", state.actorLabel(Player.ONE))
        assertEquals("Computer", state.actorLabel(Player.TWO))
        assertEquals("Your turn", state.turnLabel(Player.ONE))
        assertEquals("Computer's turn", state.turnLabel(Player.TWO))
    }

    @Test fun `reduced motion keeps every presentation beat readable`() {
        val sequencedStages = listOf(
            TurnStage.TURN_START,
            TurnStage.REVEALING,
            TurnStage.AI_TARGETING,
            TurnStage.AI_PLACING,
            TurnStage.AI_SETTLING,
        )
        assertTrue(sequencedStages.all { presentationDelayMillis(it, reducedMotion = true)!! > 0L })
        assertTrue(presentationDelayMillis(TurnStage.REVEALING, reducedMotion = true)!! >= 500L)
    }

    @Test fun `computer cell announcements name the action symbol row and column`() {
        assertEquals(
            "Computer selected row 2, column 3",
            computerMoveDescription(5, Symbol.O, TurnStage.AI_TARGETING),
        )
        assertEquals(
            "Computer placed O in row 2, column 3",
            computerMoveDescription(5, Symbol.O, TurnStage.AI_PLACING),
        )
        assertEquals(null, computerMoveDescription(5, Symbol.O, TurnStage.PLAYING))
    }

    @Test fun `computer winning move settles before result and never starts human draw`() {
        val board = Board.EMPTY.place(Cell.of(0), Symbol.X).place(Cell.of(1), Symbol.X)
        val viewModel = testAiViewModel(
            restoredComputerPlacement(board, Symbol.X, remainingX = 2, remainingO = 5),
        )
        dispatcher.scheduler.advanceUntilIdle()

        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_TARGETING, viewModel.uiState.value.stage)
        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_PLACING, viewModel.uiState.value.stage)
        assertEquals("Computer wins", viewModel.uiState.value.resultLabel())
        assertEquals(3, viewModel.uiState.value.board.occupiedCount)

        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_SETTLING, viewModel.uiState.value.stage)
        finishPresentation(viewModel)
        assertEquals(TurnStage.TERMINAL, viewModel.uiState.value.stage)
        assertEquals("Computer wins", viewModel.uiState.value.resultLabel())

        viewModel.rematch()
        assertEquals(GameMode.PIC_PAC_AI, viewModel.uiState.value.mode)
        assertEquals(TurnStage.TURN_START, viewModel.uiState.value.stage)
        assertEquals(0, viewModel.uiState.value.board.occupiedCount)
    }

    @Test fun `computer draw move settles before draw result`() {
        val board = Board.fromSymbols(
            listOf(Symbol.X, Symbol.O, Symbol.X, Symbol.X, Symbol.O, Symbol.O, Symbol.O, Symbol.X, null),
        )
        val viewModel = testAiViewModel(
            restoredComputerPlacement(board, Symbol.X, remainingX = 0, remainingO = 1),
        )
        dispatcher.scheduler.advanceUntilIdle()

        finishPresentation(viewModel)
        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_PLACING, viewModel.uiState.value.stage)
        assertEquals(GameOutcome.Draw, viewModel.uiState.value.outcome)
        assertEquals(9, viewModel.uiState.value.board.occupiedCount)

        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_SETTLING, viewModel.uiState.value.stage)
        finishPresentation(viewModel)
        assertEquals(TurnStage.TERMINAL, viewModel.uiState.value.stage)
        assertEquals("Draw", viewModel.uiState.value.resultLabel())
    }

    @Test fun `human win is addressed directly in vs computer mode`() {
        val board = Board.EMPTY
            .place(Cell.of(0), Symbol.X)
            .place(Cell.of(1), Symbol.X)
            .place(Cell.of(2), Symbol.X)
        val outcome = GameOutcome.Win(Player.ONE, Symbol.X, board.winningLines(Symbol.X))
        val terminal = PicPacState(
            board = board,
            activePlayer = Player.ONE,
            remainingX = 2,
            remainingO = 5,
            phase = PicPacPhase.Terminal(outcome),
            starter = Player.ONE,
            revision = 1,
        )
        val viewModel = testAiViewModel(restoredTerminalGame(terminal, outcome))
        assertEquals("You win", viewModel.uiState.value.resultLabel())

        viewModel.rematch()
        assertEquals(GameMode.PIC_PAC_AI, viewModel.uiState.value.mode)
        assertEquals(TurnStage.TURN_START, viewModel.uiState.value.stage)
        assertEquals(0, viewModel.uiState.value.board.occupiedCount)
    }

    private fun testAiViewModel(handle: SavedStateHandle = SavedStateHandle()): GameViewModel {
        val firstLegalAgent = AiAgent { observation, _ -> AiDecision(observation.legalCells.first()) }
        return GameViewModel(application, handle, firstLegalAgent, dispatcher)
    }

    private fun finishPresentation(viewModel: GameViewModel) {
        val id = viewModel.uiState.value.presentationId
        viewModel.presentationStepFinished(id)
        dispatcher.scheduler.runCurrent()
    }

    private fun advanceToComputerTargeting(viewModel: GameViewModel) {
        viewModel.startPicPacAi(Difficulty.EASY)
        finishPresentation(viewModel)
        finishPresentation(viewModel)
        viewModel.place(0)
        finishPresentation(viewModel)
        dispatcher.scheduler.advanceUntilIdle()
        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_TARGETING, viewModel.uiState.value.stage)
    }

    private fun restoredComputerPlacement(
        board: Board,
        held: Symbol,
        remainingX: Int,
        remainingO: Int,
    ): SavedStateHandle = SavedStateHandle(
        mapOf(
            "screen" to AppScreen.GAME.name,
            "mode" to GameMode.PIC_PAC_AI.name,
            "difficulty" to Difficulty.EASY.name,
            "stage" to TurnStage.REVEALING.name,
            "presentation_id" to 10L,
            "revision" to 7L,
            "next_starter" to Player.ONE.name,
            "kind" to "picpac",
            "board" to board.code,
            "active" to Player.TWO.name,
            "starter" to Player.ONE.name,
            "remaining_x" to remainingX,
            "remaining_o" to remainingO,
            "phase" to "place",
            "held" to held.name,
            "token" to 99L,
        ),
    )

    private fun restoredTerminalGame(state: PicPacState, outcome: GameOutcome.Win): SavedStateHandle = SavedStateHandle(
        mapOf(
            "screen" to AppScreen.GAME.name,
            "mode" to GameMode.PIC_PAC_AI.name,
            "difficulty" to Difficulty.EASY.name,
            "stage" to TurnStage.TERMINAL.name,
            "presentation_id" to 10L,
            "revision" to state.revision,
            "next_starter" to Player.ONE.name,
            "kind" to "picpac",
            "board" to state.board.code,
            "active" to state.activePlayer.name,
            "starter" to state.starter.name,
            "remaining_x" to state.remainingX,
            "remaining_o" to state.remainingO,
            "phase" to "terminal",
            "outcome" to "win",
            "winner" to outcome.player.name,
            "win_symbol" to outcome.symbol.name,
        ),
    )
}
