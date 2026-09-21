package com.thevaguebox.probabilistictictactoe.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.PicPacRules
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.TransitionResult
import com.thevaguebox.picpac.core.ai.AiAgent
import com.thevaguebox.picpac.core.ai.AiDecision
import com.thevaguebox.picpac.testing.GoldenFixtureDocument
import com.thevaguebox.picpac.testing.intValues
import com.thevaguebox.picpac.testing.optionalBoolean
import com.thevaguebox.picpac.testing.optionalObject
import com.thevaguebox.picpac.testing.optionalString
import com.thevaguebox.picpac.testing.requireArray
import com.thevaguebox.picpac.testing.requireBoolean
import com.thevaguebox.picpac.testing.requireInt
import com.thevaguebox.picpac.testing.requireLong
import com.thevaguebox.picpac.testing.requireObject
import com.thevaguebox.picpac.testing.requireString
import kotlinx.coroutines.CompletableDeferred
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
class GoldenFixtureCoordinatorTest {
    private val fixtures = GoldenFixtureDocument.load()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `presentation fixtures execute through domain and coordinator APIs`() {
        fixtures.cases("presentationScenarios").forEach { fixture -> fixture.verify {
            when (fixture.id) {
                "local-handoff-ready-reveal-place" -> runLocalHandoff(fixture.value)
                "computer-winning-move-settles-before-result",
                "computer-draw-move-settles-before-result" -> runComputerSettlement(fixture.value)
                else -> error("unhandled presentation fixture ${fixture.id}")
            }
        } }
    }

    @Test fun `restoration fixtures execute through coordinator restoration guards`() {
        fixtures.cases("restorationScenarios").forEach { fixture -> fixture.verify {
            when (fixture.id) {
                "held-piece-restores-without-redraw" -> runHeldPieceRestore(fixture.value)
                "computer-target-restores-and-commits-once" -> runTargetRestore(fixture.value)
                "stale-presentation-callback-after-rematch-is-ignored" -> runStalePresentation(fixture.value)
                "cancelled-ai-result-after-mode-replacement-is-ignored" -> runCancelledAi(fixture.value)
                else -> error("unhandled restoration fixture ${fixture.id}")
            }
        } }
    }

    private fun runLocalHandoff(value: JsonObject) {
        val initial = value.requireObject("initial")
        val steps = value.requireArray("steps").map { it.requireObject("step") }
        val fresh = GameViewModel(application, SavedStateHandle())
        fresh.startPicPacLocal()
        assertEquals(TurnStage.valueOf(initial.requireString("stage")), fresh.uiState.value.stage)
        assertEquals(Player.valueOf(initial.requireString("activePlayer")), fresh.uiState.value.activePlayer)
        assertEquals(initial.requireBoolean("boardVisible"), fresh.uiState.value.stage != TurnStage.HANDOFF)
        assertEquals(initial.requireBoolean("bagVisible"), fresh.uiState.value.stage != TurnStage.HANDOFF)

        val ready = steps[0]
        assertEquals("READY", ready.requireString("event"))
        val scriptedSymbol = if (ready.requireInt("scriptedDrawResult") < 5) Symbol.X else Symbol.O
        val domainReveal = PicPacRules.draw(PicPacRules.newGame(), scriptedSymbol) as TransitionResult.Accepted
        val revealed = domainReveal.state
        val handle = picPacHandle(
            screen = AppScreen.GAME,
            mode = GameMode.PIC_PAC_LOCAL,
            difficulty = Difficulty.MEDIUM,
            stage = TurnStage.valueOf(ready.requireString("expectedStage")),
            presentationId = 1,
            board = revealed.board,
            activePlayer = revealed.activePlayer,
            starter = revealed.starter,
            revision = revealed.revision,
            remainingX = revealed.remainingX,
            remainingO = revealed.remainingO,
            held = (revealed.phase as PicPacPhase.AwaitingPlacement).held,
            token = (revealed.phase as PicPacPhase.AwaitingPlacement).token.value,
        )
        val viewModel = GameViewModel(application, handle)
        assertEquals(Symbol.valueOf(ready.requireString("expectedHeldSymbol")), viewModel.uiState.value.heldSymbol)
        assertEquals(ready.requireInt("expectedRemainingX"), viewModel.uiState.value.picPac!!.remainingX)
        assertEquals(ready.requireInt("expectedRemainingO"), viewModel.uiState.value.picPac!!.remainingO)
        assertEquals(10 - ready.requireInt("expectedRandomCalls"), viewModel.uiState.value.picPac!!.hiddenTotal)

        val revealFinished = steps[1]
        finishPresentation(viewModel)
        assertEquals(TurnStage.valueOf(revealFinished.requireString("expectedStage")), viewModel.uiState.value.stage)
        assertEquals(
            revealFinished.requireArray("expectedInputEnabledCells").intValues("expectedInputEnabledCells"),
            viewModel.uiState.value.board.legalCells().map(Cell::index),
        )

        val placement = steps[2]
        viewModel.place(placement.requireInt("cell"))
        val state = viewModel.uiState.value
        assertEquals(TurnStage.valueOf(placement.requireString("expectedStage")), state.stage)
        assertEquals(Player.valueOf(placement.requireString("expectedActivePlayer")), state.activePlayer)
        assertEquals(placement.requireArray("expectedBoard").symbols(), state.board.symbols())
        assertEquals(10 - placement.requireInt("expectedRandomCalls"), state.picPac!!.hiddenTotal)
    }

    private fun runComputerSettlement(value: JsonObject) {
        val initial = value.requireObject("initial")
        val mode = GameMode.valueOf(value.requireString("mode"))
        val board = Board.fromSymbols(initial.requireArray("board").symbols())
        val token = initial.requireLong("turnToken")
        val revision = (token - board.occupiedCount - 1) / 16
        val handle = picPacHandle(
            screen = AppScreen.GAME,
            mode = mode,
            difficulty = Difficulty.MEDIUM,
            stage = TurnStage.valueOf(initial.requireString("stage")),
            presentationId = initial.requireLong("presentationId"),
            board = board,
            activePlayer = Player.valueOf(initial.requireString("activePlayer")),
            starter = Player.ONE,
            revision = revision,
            remainingX = initial.requireInt("remainingX"),
            remainingO = initial.requireInt("remainingO"),
            held = Symbol.valueOf(initial.requireString("heldSymbol")),
            token = token,
            aiTarget = initial.requireInt("aiTargetCell"),
            aiSymbol = Symbol.valueOf(initial.requireString("aiMoveSymbol")),
        )
        val viewModel = GameViewModel(application, handle, firstLegalAgent(), dispatcher)
        assertEquals(Player.valueOf(initial.requireString("displayedPlayer")), viewModel.uiState.value.displayedPlayer)

        value.requireArray("steps").forEach { element ->
            val step = element.requireObject("step")
            val callbackId = if (step.requireString("event") == "PRESENTATION_FINISHED") {
                step.requireLong("presentationId")
            } else {
                viewModel.uiState.value.presentationId
            }
            viewModel.presentationStepFinished(callbackId)
            dispatcher.scheduler.runCurrent()
            val state = viewModel.uiState.value
            assertEquals(TurnStage.valueOf(step.requireString("expectedStage")), state.stage)
            if (step.has("expectedBoard")) assertEquals(step.requireArray("expectedBoard").symbols(), state.board.symbols())
            step.optionalString("expectedDisplayedPlayer")?.let { assertEquals(Player.valueOf(it), state.displayedPlayer) }
            step.optionalObject("expectedOutcome")?.let { assertOutcome(it, state.outcome) }
            step.optionalBoolean("resultVisible")?.let { assertEquals(it, state.stage == TurnStage.TERMINAL) }
            if (step.optionalBoolean("mustNotStartAnotherDraw") == true) {
                val terminal = state
                finishPresentation(viewModel)
                assertEquals(terminal, viewModel.uiState.value)
            }
        }
    }

    private fun runHeldPieceRestore(value: JsonObject) {
        val snapshot = value.requireObject("snapshot")
        val expected = value.requireObject("expectedAfterRestore")
        val viewModel = GameViewModel(application, handleFromSnapshot(snapshot))
        val state = viewModel.uiState.value
        val picPac = requireNotNull(state.picPac)
        assertEquals(TurnStage.valueOf(expected.requireString("stage")), state.stage)
        assertEquals(expected.requireLong("presentationId"), state.presentationId)
        assertEquals(Symbol.valueOf(expected.requireString("heldSymbol")), state.heldSymbol)
        assertEquals(expected.requireInt("remainingX"), picPac.remainingX)
        assertEquals(expected.requireInt("remainingO"), picPac.remainingO)
        assertEquals(0, expected.requireInt("randomCalls"))
        assertEquals(snapshot.requireInt("remainingX") + snapshot.requireInt("remainingO"), picPac.hiddenTotal)
    }

    private fun runTargetRestore(value: JsonObject) {
        val snapshot = value.requireObject("snapshot")
        val expected = value.requireObject("expectedAfterRestore")
        val countingAgent = CountingAgent()
        val viewModel = GameViewModel(application, handleFromSnapshot(snapshot), countingAgent, dispatcher)
        val state = viewModel.uiState.value
        assertEquals(TurnStage.valueOf(expected.requireString("stage")), state.stage)
        assertEquals(expected.requireLong("presentationId"), state.presentationId)
        assertEquals(expected.requireInt("aiTargetCell"), state.aiTargetCell)
        assertEquals(Symbol.valueOf(expected.requireString("aiMoveSymbol")), state.aiMoveSymbol)
        assertEquals(expected.requireInt("boardOccupiedCount"), state.board.occupiedCount)
        assertEquals(expected.requireInt("newSearches"), countingAgent.calls)
        assertEquals(0, expected.requireInt("randomCalls"))
        assertEquals(snapshot.requireInt("remainingX") + snapshot.requireInt("remainingO"), state.picPac!!.hiddenTotal)

        val afterCallback = value.requireObject("expectedAfterCurrentCallback")
        finishPresentation(viewModel)
        val committed = viewModel.uiState.value
        assertEquals(TurnStage.valueOf(afterCallback.requireString("stage")), committed.stage)
        assertEquals(afterCallback.requireArray("board").symbols(), committed.board.symbols())
        assertEquals(afterCallback.requireInt("boardOccupiedCount"), committed.board.occupiedCount)
        val afterOneCommit = committed
        viewModel.presentationStepFinished(snapshot.requireLong("presentationId"))
        assertEquals(afterOneCommit, viewModel.uiState.value)
    }

    private fun runStalePresentation(value: JsonObject) {
        val before = value.requireObject("beforeReplacement")
        val board = Board.fromSymbols(listOf(Symbol.O, null, null, null, null, null, null, null, null))
        val handle = picPacHandle(
            screen = AppScreen.GAME,
            mode = GameMode.PIC_PAC_AI,
            difficulty = Difficulty.EASY,
            stage = TurnStage.valueOf(before.requireString("stage")),
            presentationId = before.requireLong("presentationId"),
            board = board,
            activePlayer = Player.TWO,
            starter = Player.ONE,
            revision = 1,
            remainingX = 4,
            remainingO = 4,
            held = Symbol.X,
            token = 18,
            aiTarget = 4,
            aiSymbol = Symbol.X,
        )
        val viewModel = GameViewModel(application, handle, firstLegalAgent(), dispatcher)
        val replacement = value.requireObject("replacement")
        assertEquals("REMATCH", replacement.requireString("event"))
        viewModel.rematch()
        val replaced = viewModel.uiState.value
        assertEquals(TurnStage.valueOf(replacement.requireString("expectedStage")), replaced.stage)
        assertEquals(replacement.requireInt("expectedBoardOccupiedCount"), replaced.board.occupiedCount)
        assertTrue(replaced.presentationId > replacement.requireLong("expectedPresentationIdGreaterThan"))
        val late = value.requireObject("lateEvent")
        viewModel.presentationStepFinished(late.requireLong("presentationId"))
        if (value.requireObject("expected").requireBoolean("replacementStateUnchanged")) {
            assertEquals(replaced, viewModel.uiState.value)
        }
    }

    private fun runCancelledAi(value: JsonObject) {
        val before = value.requireObject("beforeReplacement")
        val board = Board.fromSymbols(listOf(Symbol.O, null, null, null, null, null, null, null, null))
        val gate = CompletableDeferred<Unit>()
        val delayedAgent = AiAgent { observation, _ ->
            gate.await()
            AiDecision(observation.legalCells.first())
        }
        val handle = picPacHandle(
            screen = AppScreen.GAME,
            mode = GameMode.valueOf(before.requireString("mode")),
            difficulty = Difficulty.EASY,
            stage = TurnStage.valueOf(before.requireString("stage")),
            presentationId = 30,
            board = board,
            activePlayer = Player.TWO,
            starter = Player.ONE,
            revision = before.requireLong("capturedRevision"),
            remainingX = 4,
            remainingO = 4,
            held = Symbol.X,
            token = before.requireLong("capturedTurnToken"),
        )
        val viewModel = GameViewModel(application, handle, delayedAgent, dispatcher)
        dispatcher.scheduler.runCurrent()
        val replacement = value.requireObject("replacement")
        assertEquals("START_CLASSIC", replacement.requireString("event"))
        viewModel.startClassic()
        val replaced = viewModel.uiState.value
        assertEquals(GameMode.valueOf(replacement.requireString("mode")), replaced.mode)
        assertEquals(TurnStage.valueOf(replacement.requireString("stage")), replaced.stage)
        assertEquals(replacement.requireInt("boardOccupiedCount"), replaced.board.occupiedCount)
        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        if (value.requireObject("expected").requireBoolean("replacementStateUnchanged")) {
            assertEquals(replaced, viewModel.uiState.value)
        }
    }

    private fun handleFromSnapshot(snapshot: JsonObject): SavedStateHandle {
        val board = Board.fromSymbols(snapshot.requireArray("board").symbols())
        return picPacHandle(
            screen = AppScreen.valueOf(snapshot.requireString("screen")),
            mode = GameMode.valueOf(snapshot.requireString("mode")),
            difficulty = Difficulty.valueOf(snapshot.optionalString("difficulty") ?: Difficulty.MEDIUM.name),
            stage = TurnStage.valueOf(snapshot.requireString("stage")),
            presentationId = snapshot.requireLong("presentationId"),
            board = board,
            activePlayer = Player.valueOf(snapshot.requireString("activePlayer")),
            starter = Player.valueOf(snapshot.requireString("starter")),
            revision = snapshot.requireLong("revision"),
            remainingX = snapshot.requireInt("remainingX"),
            remainingO = snapshot.requireInt("remainingO"),
            held = Symbol.valueOf(snapshot.requireString("heldSymbol")),
            token = snapshot.requireLong("turnToken"),
            aiTarget = if (snapshot.has("aiTargetCell")) snapshot.requireInt("aiTargetCell") else null,
            aiSymbol = snapshot.optionalString("aiMoveSymbol")?.let(Symbol::valueOf),
        )
    }

    private fun picPacHandle(
        screen: AppScreen,
        mode: GameMode,
        difficulty: Difficulty,
        stage: TurnStage,
        presentationId: Long,
        board: Board,
        activePlayer: Player,
        starter: Player,
        revision: Long,
        remainingX: Int,
        remainingO: Int,
        held: Symbol,
        token: Long,
        aiTarget: Int? = null,
        aiSymbol: Symbol? = null,
    ): SavedStateHandle = SavedStateHandle(
        mapOf(
            "screen" to screen.name,
            "mode" to mode.name,
            "difficulty" to difficulty.name,
            "stage" to stage.name,
            "presentation_id" to presentationId,
            "ai_target" to aiTarget,
            "ai_symbol" to aiSymbol?.name,
            "revision" to revision,
            "next_starter" to starter.name,
            "kind" to "picpac",
            "board" to board.code,
            "active" to activePlayer.name,
            "starter" to starter.name,
            "remaining_x" to remainingX,
            "remaining_o" to remainingO,
            "phase" to "place",
            "held" to held.name,
            "token" to token,
        ),
    )

    private fun assertOutcome(expected: JsonObject, actual: GameOutcome?) {
        when (expected.requireString("kind")) {
            "DRAW" -> assertEquals(GameOutcome.Draw, actual)
            "WIN" -> {
                assertNotNull(actual)
                actual as GameOutcome.Win
                assertEquals(Player.valueOf(expected.requireString("actor")), actual.player)
                assertEquals(Symbol.valueOf(expected.requireString("symbol")), actual.symbol)
            }
            else -> error("unsupported outcome")
        }
    }

    private fun finishPresentation(viewModel: GameViewModel) {
        viewModel.presentationStepFinished(viewModel.uiState.value.presentationId)
        dispatcher.scheduler.runCurrent()
    }

    private fun firstLegalAgent(): AiAgent = AiAgent { observation, _ -> AiDecision(observation.legalCells.first()) }

    private fun JsonArray.symbols(): List<Symbol?> = map { value ->
        if (value.isJsonNull) null else Symbol.valueOf(value.asString)
    }

    private class CountingAgent : AiAgent {
        var calls: Int = 0
            private set

        override suspend fun chooseMove(
            observation: com.thevaguebox.picpac.core.ai.AiObservation,
            limits: com.thevaguebox.picpac.core.ai.SearchLimits,
        ): AiDecision {
            calls++
            return AiDecision(observation.legalCells.first())
        }
    }
}
