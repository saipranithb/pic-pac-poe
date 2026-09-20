package com.thevaguebox.probabilistictictactoe

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.probabilistictictactoe.ui.AppScreen
import com.thevaguebox.probabilistictictactoe.ui.GameUiState
import com.thevaguebox.probabilistictictactoe.ui.GameViewModel
import com.thevaguebox.probabilistictictactoe.ui.TurnStage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val game by lazy { ViewModelProvider(compose.activity)[GameViewModel::class.java] }

    @Test fun localModeRequiresReadyBeforeReveal() {
        compose.onNodeWithText("Pic-Pac Local").performScrollTo().performClick()
        compose.onNodeWithText("Player 1, you're up.").assertIsDisplayed()
        capture("actual-flow-local-handoff")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Ready").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("You drew", substring = true).assertIsDisplayed()
        capture("actual-flow-local-reveal")
        compose.mainClock.advanceTimeBy(700)
        capture("actual-flow-local-human-placement")
        compose.onNodeWithTag("board-cell-0").performScrollTo().assertIsEnabled().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Player 2, you're up.").assertIsDisplayed()
    }

    @Test fun tutorialExplainsPlayerSymbolSeparation() {
        compose.onNodeWithText("How to play").performScrollTo().performClick()
        compose.onNodeWithText("You're not X. You're not O.").performScrollTo().assertIsDisplayed()
    }

    @Test fun classicWinRematchAndRecreationPreserveTheProductFlow() {
        compose.onNodeWithText("Classic").performScrollTo().performClick()
        capture("actual-flow-classic-start")
        listOf(0, 3, 1, 4, 2).forEach { cell ->
            compose.onNodeWithTag("board-cell-$cell").performScrollTo().performClick()
        }
        compose.onNodeWithText("Player 1 wins").assertIsDisplayed()
        capture("actual-flow-classic-result")
        compose.onNodeWithText("Rematch").performScrollTo().performClick()
        compose.onNodeWithTag("board-cell-4").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Row 2, column 2, O").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Row 2, column 2, O").performScrollTo().assertIsDisplayed()
        capture("actual-flow-classic-rematch-recreated")
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.onNodeWithText("Pic-Pac-Poe").assertIsDisplayed()
    }

    @Test fun computerCompletesOneTurnAndReturnsControlOnlyAfterSettlement() {
        compose.onNodeWithText("Easy").performScrollTo().performClick()
        compose.onNodeWithText("Easy").assertIsSelected()
        // Presentation delays use Compose's clock. Polling real time alone cannot advance them.
        // The activity's existing ViewModel is observed only; every game command remains a UI tap.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Play").performScrollTo().performClick()
        awaitStage(TurnStage.TURN_START)
        awaitStage(TurnStage.REVEALING)
        capture("actual-flow-first-human-reveal")
        awaitStage(TurnStage.PLAYING)
        compose.onNodeWithTag("board-cell-0").assertIsEnabled()
        capture("actual-flow-first-human-placement")
        compose.onNodeWithTag("board-cell-0").performScrollTo().performClick()
        assertEquals(Player.TWO, awaitStage(TurnStage.TURN_START).activePlayer)
        capture("actual-flow-computer-turn-start", settleMillis = 100)
        awaitStage(TurnStage.REVEALING)
        capture("actual-flow-computer-reveal")
        val afterReveal = awaitStage(TurnStage.AI_THINKING, TurnStage.AI_TARGETING)
        if (afterReveal.stage == TurnStage.AI_THINKING) capture("actual-flow-computer-thinking")
        val target = awaitStage(TurnStage.AI_TARGETING)
        val targetCell = requireNotNull(target.aiTargetCell)
        compose.onNodeWithContentDescription("Computer selected row ${targetCell / 3 + 1}, column ${targetCell % 3 + 1}")
            .performScrollTo().assertIsDisplayed()
        assertEquals(1, target.board.occupiedCount)
        capture("actual-flow-computer-target")
        assertEquals(2, awaitStage(TurnStage.AI_PLACING).board.occupiedCount)
        assertBoardLocked()
        capture("actual-flow-computer-placement", settleMillis = 100)
        assertEquals(2, awaitStage(TurnStage.AI_SETTLING).board.occupiedCount)
        assertBoardLocked()
        capture("actual-flow-computer-settled")
        assertEquals(Player.ONE, awaitStage(TurnStage.TURN_START).activePlayer)
        awaitStage(TurnStage.REVEALING)
        val nextHuman = awaitStage(TurnStage.PLAYING)
        assertEquals(2, nextHuman.board.occupiedCount)
        assertEquals(null, nextHuman.aiTargetCell)
        compose.onNodeWithTag("board-cell-${nextHuman.board.legalCells().first().index}").assertIsEnabled()
        capture("actual-flow-next-human-placement")
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Back").performScrollTo().performClick()
        compose.onNodeWithText("Pic-Pac-Poe").assertIsDisplayed()
    }

    private fun awaitStage(vararg stages: TurnStage): GameUiState {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            val state = game.uiState.value
            if (state.screen == AppScreen.GAME && state.stage in stages) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                return state
            }
            compose.mainClock.advanceTimeByFrame()
            // Yield actual time to the worker dispatcher; AI is not run by the Compose clock.
            SystemClock.sleep(8)
        }
        error("Timed out waiting for ${stages.toList()}; actual ${game.uiState.value.stage}")
    }

    private fun assertBoardLocked() {
        repeat(9) { cell ->
            assertEquals(0, compose.onAllNodes(hasTestTag("board-cell-$cell") and isEnabled()).fetchSemanticsNodes().size)
        }
    }

    private fun capture(name: String, settleMillis: Long = 0) {
        if (!visualCaptureRequested()) return
        if (settleMillis > 0) {
            val stage = game.uiState.value.stage
            // Sample inside the beat, not the zero-alpha first frame. 100ms stays below
            // both the normal and reduced TURN_START / AI_PLACING presentation delays.
            compose.mainClock.advanceTimeBy(settleMillis)
            compose.waitForIdle()
            assertEquals("Capture crossed a presentation boundary: $name", stage, game.uiState.value.stage)
        }
        compose.waitForIdle()
        captureSettledDevice(name)
    }
}
