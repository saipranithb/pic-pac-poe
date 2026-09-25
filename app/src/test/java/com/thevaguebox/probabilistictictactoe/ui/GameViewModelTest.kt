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
import kotlinx.coroutines.CompletableDeferred
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

    @Test fun `presentation clocks retain the committed normal and reduced timing contracts`() {
        val timedStages = mapOf(
            TurnStage.TURN_START to (300L to 160L),
            TurnStage.REVEALING to (650L to 500L),
            TurnStage.AI_TARGETING to (280L to 160L),
            TurnStage.AI_PLACING to (340L to 180L),
            TurnStage.AI_SETTLING to (480L to 320L),
        )
        TurnStage.entries.forEach { stage ->
            assertEquals("normal $stage", timedStages[stage]?.first, presentationDelayMillis(stage, false))
            assertEquals("reduced $stage", timedStages[stage]?.second, presentationDelayMillis(stage, true))
        }
    }

    @Test fun `all locked presentation stages reject human placement without any state change`() {
        val local = GameViewModel(application, SavedStateHandle())
        local.startPicPacLocal()
        assertPlacementLocked(local, TurnStage.HANDOFF)
        local.readyForReveal()
        assertPlacementLocked(local, TurnStage.REVEALING)

        val decisionGate = CompletableDeferred<Unit>()
        val delayedAgent = AiAgent { observation, _ ->
            decisionGate.await()
            AiDecision(observation.legalCells.first())
        }
        val computer = GameViewModel(application, SavedStateHandle(), delayedAgent, dispatcher)
        computer.startPicPacAi(Difficulty.EASY)
        assertPlacementLocked(computer, TurnStage.TURN_START)
        finishPresentation(computer)
        assertPlacementLocked(computer, TurnStage.REVEALING)
        finishPresentation(computer)
        computer.place(0)
        assertPlacementLocked(computer, TurnStage.TURN_START)
        finishPresentation(computer)
        assertPlacementLocked(computer, TurnStage.REVEALING)
        finishPresentation(computer)
        assertPlacementLocked(computer, TurnStage.AI_THINKING)
        decisionGate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertPlacementLocked(computer, TurnStage.AI_TARGETING)
        finishPresentation(computer)
        assertPlacementLocked(computer, TurnStage.AI_PLACING)
        finishPresentation(computer)
        assertPlacementLocked(computer, TurnStage.AI_SETTLING)

        val classic = GameViewModel(application, SavedStateHandle())
        classic.startClassic()
        listOf(0, 3, 1, 4, 2).forEach(classic::place)
        assertPlacementLocked(classic, TurnStage.TERMINAL)
    }

    @Test fun `target and symbol survive every committed computer placement beat and recreation`() {
        listOf(TurnStage.AI_TARGETING, TurnStage.AI_PLACING, TurnStage.AI_SETTLING).forEach { stage ->
            val handle = SavedStateHandle()
            val original = testAiViewModel(handle)
            advanceToComputerTargeting(original)
            while (original.uiState.value.stage != stage) finishPresentation(original)
            val before = original.uiState.value
            val restored = testAiViewModel(handle)
            val after = restored.uiState.value
            assertEquals(stage, after.stage)
            assertEquals(before.presentationId, after.presentationId)
            assertEquals(before.picPac, after.picPac)
            assertEquals(before.aiTargetCell, after.aiTargetCell)
            assertEquals(before.aiMoveSymbol, after.aiMoveSymbol)
            assertNotNull(after.aiTargetCell)
            assertNotNull(after.aiMoveSymbol)
            assertEquals(Player.TWO, after.displayedPlayer)
            if (stage != TurnStage.AI_TARGETING) {
                assertEquals(after.aiMoveSymbol, after.board[Cell.of(after.aiTargetCell!!)])
            }
            assertPlacementLocked(restored, stage)
            while (restored.uiState.value.stage != TurnStage.TURN_START) finishPresentation(restored)
            assertEquals(null, restored.uiState.value.aiTargetCell)
            assertEquals(null, restored.uiState.value.aiMoveSymbol)
            assertEquals(2, restored.uiState.value.board.occupiedCount)
            assertEquals(Player.ONE, restored.uiState.value.displayedPlayer)
        }
    }

    @Test fun `completed presentation callbacks cannot be replayed in later stages or after going home`() {
        val viewModel = testAiViewModel()
        advanceToComputerTargeting(viewModel)
        val oldTargetId = viewModel.uiState.value.presentationId
        finishPresentation(viewModel)
        val placing = viewModel.uiState.value
        viewModel.presentationStepFinished(oldTargetId)
        assertEquals(placing, viewModel.uiState.value)
        finishPresentation(viewModel)
        val settling = viewModel.uiState.value
        viewModel.presentationStepFinished(placing.presentationId)
        assertEquals(settling, viewModel.uiState.value)
        viewModel.goHome()
        val home = viewModel.uiState.value
        viewModel.presentationStepFinished(settling.presentationId)
        assertEquals(home, viewModel.uiState.value)
        viewModel.startPicPacAi(Difficulty.EASY)
        val restarted = viewModel.uiState.value
        viewModel.presentationStepFinished(oldTargetId)
        assertEquals(restarted, viewModel.uiState.value)
        assertTrue(restarted.presentationId > settling.presentationId)
    }

    @Test fun `cancelled late AI decision cannot mutate a replacement game`() {
        val decisionGate = CompletableDeferred<Unit>()
        val delayedAgent = AiAgent { observation, _ ->
            decisionGate.await()
            AiDecision(observation.legalCells.first())
        }
        val viewModel = GameViewModel(application, SavedStateHandle(), delayedAgent, dispatcher)
        viewModel.startPicPacAi(Difficulty.EASY)
        finishPresentation(viewModel)
        finishPresentation(viewModel)
        viewModel.place(0)
        finishPresentation(viewModel)
        finishPresentation(viewModel)
        assertEquals(TurnStage.AI_THINKING, viewModel.uiState.value.stage)
        viewModel.startClassic()
        val replacement = viewModel.uiState.value
        decisionGate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(replacement, viewModel.uiState.value)
    }

    private fun assertPlacementLocked(viewModel: GameViewModel, stage: TurnStage) {
        val before = viewModel.uiState.value
        assertEquals(stage, before.stage)
        repeat(9) { viewModel.place(it) }
        assertEquals("placement mutated $stage", before, viewModel.uiState.value)
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
