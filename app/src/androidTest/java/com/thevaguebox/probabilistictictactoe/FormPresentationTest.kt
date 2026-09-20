package com.thevaguebox.probabilistictictactoe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.ClassicState
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.PicPacPhase
import com.thevaguebox.picpac.core.PicPacState
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.TurnToken
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.AiLabScreen
import com.thevaguebox.probabilistictictactoe.ui.AppScreen
import com.thevaguebox.probabilistictictactoe.ui.Difficulty
import com.thevaguebox.probabilistictictactoe.ui.GameMode
import com.thevaguebox.probabilistictictactoe.ui.GameScreen
import com.thevaguebox.probabilistictictactoe.ui.GameUiState
import com.thevaguebox.probabilistictictactoe.ui.HomeScreen
import com.thevaguebox.probabilistictictactoe.ui.HowToPlayScreen
import com.thevaguebox.probabilistictictactoe.ui.SettingsScreen
import com.thevaguebox.probabilistictictactoe.ui.TurnStage
import com.thevaguebox.probabilistictictactoe.ui.theme.PicPacTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * State fixtures exercise the real composables, not a second implementation of their visuals.
 * They intentionally do not drive the ViewModel. ProductFlowTest covers the integrated flows.
 * Optional PNGs are named stage-fixture and must not be reported as observed live AI turns.
 */
@RunWith(AndroidJUnit4::class)
class FormPresentationTest {
    @get:Rule val compose = createComposeRule()
    private val request = mutableStateOf(RenderRequest())
    private val clickedCells = mutableListOf<Int>()

    @Test fun everyComputerStageKeepsInputLockedAndActorIdentityExplicit() {
        render()
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
            listOf(false, true).forEach { reduced ->
                computerFixtures().forEach { (name, state) ->
                    show(RenderRequest(state = state, theme = theme, reducedMotion = reduced))
                    repeat(9) { index ->
                        compose.onAllNodes(hasTestTag("board-cell-$index") and isEnabled()).assertCountEquals(0)
                    }
                    if (state.stage == TurnStage.REVEALING) {
                        compose.onNodeWithText("Computer drew O").assertIsDisplayed()
                    } else if (state.stage != TurnStage.TERMINAL) {
                        compose.onNodeWithText("Computer").assertExists()
                    }
                    if (state.stage == TurnStage.AI_TARGETING) {
                        compose.onNodeWithContentDescription("Computer selected row 3, column 3")
                            .performScrollTo().assertIsNotEnabled()
                    }
                    if (state.stage == TurnStage.AI_PLACING || state.stage == TurnStage.AI_SETTLING) {
                        compose.onNodeWithContentDescription("Computer placed O in row 3, column 3")
                            .performScrollTo().assertIsNotEnabled()
                    }
                    capture("stage-fixture-$name-${theme.name.lowercase()}-${if (reduced) "reduced" else "normal"}")
                }
            }
        }
        assertEquals(emptyList<Int>(), clickedCells)
    }

    @Test fun humanPlacementHasNineEqualSquareTargetsAndOnlyEmptyCellsAcceptInput() {
        render()
        show(RenderRequest(state = humanPlacement()))
        compose.onNodeWithTag("game-board").performScrollTo().assertIsDisplayed()
        val cells = (0..8).map { index -> compose.onNodeWithTag("board-cell-$index").fetchSemanticsNode() }
        val first = cells.first().boundsInRoot
        cells.forEach { cell ->
            assertTrue("Well is not square", abs(cell.boundsInRoot.width - cell.boundsInRoot.height) < 2f)
            assertTrue("Well widths differ", abs(first.width - cell.boundsInRoot.width) < 2f)
        }
        listOf(0, 2, 3, 4).forEach { compose.onNodeWithTag("board-cell-$it").assertIsNotEnabled() }
        listOf(1, 5, 6, 7, 8).forEach { compose.onNodeWithTag("board-cell-$it").assertIsEnabled() }
        compose.onNodeWithTag("board-cell-1").performClick()
        assertEquals(listOf(1), clickedCells)
        capture("stage-fixture-human-placement-dark")
    }

    @Test fun localHandoffDoesNotExposeThePrivateGameBehindIt() {
        render()
        val handoff = humanPlacement().copy(
            mode = GameMode.PIC_PAC_LOCAL,
            stage = TurnStage.HANDOFF,
            picPac = humanPlacement().picPac!!.copy(
                phase = PicPacPhase.AwaitingDraw,
                remainingX = 3,
            ),
        )
        show(RenderRequest(state = handoff))
        compose.onNodeWithText("Player 1, you're up.").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsEnabled()
        compose.onAllNodes(hasTestTag("game-board")).assertCountEquals(0)
        repeat(9) { compose.onAllNodes(hasTestTag("board-cell-$it")).assertCountEquals(0) }
        capture("stage-fixture-local-handoff-dark")
    }

    @Test fun difficultyAndThemeExposeSelectionAndSettingsExposeSingleToggleTargets() {
        render()
        show(RenderRequest(screen = AppScreen.HOME))
        compose.onNodeWithText("Medium").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Easy").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Easy").assertIsSelected()
        compose.onNodeWithText("Medium").assertIsNotSelected()
        compose.onNodeWithText("Pic-Pac-Poe").performScrollTo().assertIsDisplayed()
        capture("screen-fixture-home-dark")
        show(RenderRequest(screen = AppScreen.HOME, theme = ThemePreference.LIGHT))
        compose.onNodeWithText("Pic-Pac-Poe").performScrollTo().assertIsDisplayed()
        capture("screen-fixture-home-light")

        show(RenderRequest(screen = AppScreen.SETTINGS))
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Dark").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Light").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Light").assertIsSelected()
        compose.onNodeWithText("Dark").assertIsNotSelected()
        val switches = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        compose.onAllNodes(switches).assertCountEquals(3)
        compose.onNodeWithText("Sound", substring = true).performScrollTo().assertIsOn().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Sound", substring = true).assertIsOff()
        compose.onNodeWithText("Haptics", substring = true).performScrollTo().assertIsOn().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Haptics", substring = true).assertIsOff()
        compose.onNodeWithText("Reduced motion", substring = true).performScrollTo().assertIsOff().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Reduced motion", substring = true).assertIsOn()
        compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
        capture("screen-fixture-settings-light")
        show(RenderRequest(screen = AppScreen.SETTINGS))
        compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
        capture("screen-fixture-settings-dark")
    }

    @Test fun largeFontsKeepNarrowAndWideGameplayAndNavigationReachable() {
        render()
        listOf(320f, 900f).forEach { widthDp ->
            listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
                show(RenderRequest(state = humanPlacement(), theme = theme, widthDp = widthDp, fontScale = 2f))
                compose.onNodeWithTag("board-cell-8").performScrollTo().assertIsDisplayed().assertIsEnabled()
                val pxPerDp = compose.onNodeWithTag("game-root").fetchSemanticsNode().boundsInRoot.width / widthDp
                val bounds = compose.onNodeWithTag("board-cell-8").fetchSemanticsNode().boundsInRoot
                assertTrue("Cell smaller than 48dp at $widthDp", bounds.width / pxPerDp >= 48f)
                assertTrue("Cell smaller than 48dp at $widthDp", bounds.height / pxPerDp >= 48f)
                compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
                capture("layout-fixture-game-${widthDp.toInt()}dp-200pct-${theme.name.lowercase()}")
                show(RenderRequest(screen = AppScreen.HOME, theme = theme, widthDp = widthDp, fontScale = 2f))
                compose.onNodeWithText("Play").performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
                show(RenderRequest(screen = AppScreen.SETTINGS, theme = theme, widthDp = widthDp, fontScale = 2f))
                compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
                capture("layout-fixture-settings-top-${widthDp.toInt()}dp-200pct-${theme.name.lowercase()}")
                compose.onNodeWithText("Reduced motion", substring = true).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText("Light").performScrollTo().assertIsDisplayed()
                capture("layout-fixture-settings-${widthDp.toInt()}dp-200pct-${theme.name.lowercase()}")
                show(RenderRequest(state = computerFixtures().last().second, theme = theme, widthDp = widthDp, fontScale = 2f))
                compose.onNodeWithText("Rematch").performScrollTo().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("Home").performScrollTo().assertIsDisplayed().assertIsEnabled()
                capture("layout-fixture-result-${widthDp.toInt()}dp-200pct-${theme.name.lowercase()}")
            }
        }
    }

    @Test fun ordinaryWideGamePlacesTheBoardBesideInstructionsAndProbabilities() {
        render()
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
            show(RenderRequest(state = humanPlacement(), theme = theme, widthDp = 900f, fontScale = 1f))
            capture("layout-fixture-game-900dp-100pct-${theme.name.lowercase()}")
            compose.onNodeWithTag("game-board").assertIsDisplayed()
            compose.onNodeWithTag("turn-status").assertIsDisplayed()
            compose.onNodeWithText("Bag").assertIsDisplayed()
            val board = compose.onNodeWithTag("game-board").fetchSemanticsNode().boundsInRoot
            val instruction = compose.onNodeWithTag("turn-status").fetchSemanticsNode().boundsInRoot
            val bag = compose.onNodeWithText("Bag").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("game-root").fetchSemanticsNode().boundsInRoot
            val geometry = "900dp/font1 viewport=$viewport board=$board instruction=$instruction bag=$bag"
            assertTrue("Wide instructions should sit to the right of the board: $geometry", instruction.left > board.right)
            assertTrue("Wide bag should sit to the right of the board: $geometry", bag.left > board.right)
            compose.onNodeWithContentDescription("X, 2 remaining, 40 percent next draw").assertIsDisplayed()
            compose.onNodeWithContentDescription("O, 3 remaining, 60 percent next draw").assertIsDisplayed()
            compose.onNodeWithTag("board-cell-8").assertIsEnabled()
        }
    }

    @Test fun supportingScreensAndClassicRemainAccessibleInBothThemes() {
        render()
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
            show(RenderRequest(screen = AppScreen.HOW_TO, theme = theme))
            compose.onNodeWithText("You're not X. You're not O.").performScrollTo().assertIsDisplayed()
            capture("screen-fixture-how-to-${theme.name.lowercase()}")
            show(RenderRequest(screen = AppScreen.AI_LAB, theme = theme))
            compose.onNodeWithText("Play MCTS").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Play Q-learning").performScrollTo().assertIsDisplayed()
            capture("screen-fixture-ai-lab-${theme.name.lowercase()}")
            val classic = GameUiState(
                screen = AppScreen.GAME,
                mode = GameMode.CLASSIC_LOCAL,
                classic = ClassicState(Board.EMPTY, Player.ONE, Player.ONE, TurnToken(1), 1),
            )
            show(RenderRequest(state = classic, theme = theme))
            compose.onNodeWithTag("board-cell-4").performScrollTo().assertIsEnabled()
            capture("screen-fixture-classic-${theme.name.lowercase()}")
        }
    }

    private fun render() {
        // Fixtures hold their stage through no-op completion callbacks. Let Compose advance
        // drawing/scrolling normally so semantics assertions do not depend on an unrendered frame.
        compose.mainClock.autoAdvance = true
        compose.setContent {
            val current = request.value
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Resource display metrics can describe a different window (or an earlier wm
                // override). Derive logical width from this actual test host's pixel constraints.
                val density = current.widthDp?.let { constraints.maxWidth.toFloat() / it } ?: LocalDensity.current.density
                CompositionLocalProvider(LocalDensity provides Density(density, current.fontScale)) {
                    // A synthetic density change leaves physical constraints unchanged. Recreate
                    // the viewport so nested subcomposition cannot reuse its earlier dp scope.
                    key(current.widthDp, current.fontScale) {
                        PicPacTheme(current.theme, reducedMotion = current.reducedMotion || current.settings.reducedMotion) {
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                Box(Modifier.fillMaxSize()) {
                                    when (current.screen) {
                                        AppScreen.GAME -> GameScreen(current.state, current.reducedMotion, {}, {}, {}, clickedCells::add, {})
                                        AppScreen.HOME -> HomeScreen({}, {}, {}, {}, {}, {})
                                        AppScreen.HOW_TO -> HowToPlayScreen({})
                                        AppScreen.AI_LAB -> AiLabScreen({}, {}, {})
                                        AppScreen.SETTINGS -> SettingsScreen(
                                            settings = current.settings,
                                            onBack = {},
                                            onSound = { request.value = current.copy(settings = current.settings.copy(sound = it)) },
                                            onHaptics = { request.value = current.copy(settings = current.settings.copy(haptics = it)) },
                                            onReducedMotion = { request.value = current.copy(settings = current.settings.copy(reducedMotion = it)) },
                                            onTheme = { request.value = current.copy(theme = it, settings = current.settings.copy(theme = it)) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun show(value: RenderRequest) {
        compose.runOnIdle { request.value = value }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        if (!visualCaptureRequested()) return
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        captureSettledDevice(name)
    }

    private data class RenderRequest(
        val screen: AppScreen = AppScreen.GAME,
        val state: GameUiState = humanPlacement(),
        val theme: ThemePreference = ThemePreference.DARK,
        val reducedMotion: Boolean = false,
        val widthDp: Float? = null,
        val fontScale: Float = 1f,
        val settings: AppSettings = AppSettings(theme = theme),
    )

    private companion object {
        fun sampleBoard(): Board = Board.EMPTY
            .place(Cell.of(0), Symbol.X).place(Cell.of(2), Symbol.O)
            .place(Cell.of(3), Symbol.O).place(Cell.of(4), Symbol.X)

        fun humanPlacement(): GameUiState = GameUiState(
            screen = AppScreen.GAME,
            mode = GameMode.PIC_PAC_AI,
            difficulty = Difficulty.MEDIUM,
            stage = TurnStage.PLAYING,
            picPac = PicPacState(
                sampleBoard(), Player.ONE, 2, 3,
                PicPacPhase.AwaitingPlacement(Symbol.X, TurnToken(5)), Player.ONE, 1,
            ),
        )

        fun computerFixtures(): List<Pair<String, GameUiState>> {
            val computerBoard = sampleBoard().place(Cell.of(6), Symbol.X)
            val draw = humanPlacement().copy(
                stage = TurnStage.TURN_START,
                picPac = PicPacState(computerBoard, Player.TWO, 2, 3, PicPacPhase.AwaitingDraw, Player.ONE, 1),
            )
            val reveal = draw.copy(
                stage = TurnStage.REVEALING,
                picPac = draw.picPac!!.copy(remainingO = 2, phase = PicPacPhase.AwaitingPlacement(Symbol.O, TurnToken(6))),
            )
            val targeting = reveal.copy(stage = TurnStage.AI_TARGETING, aiTargetCell = 8, aiMoveSymbol = Symbol.O)
            val placing = targeting.copy(
                stage = TurnStage.AI_PLACING,
                picPac = targeting.picPac!!.copy(
                    board = computerBoard.place(Cell.of(8), Symbol.O), activePlayer = Player.ONE,
                    phase = PicPacPhase.AwaitingDraw,
                ),
            )
            val terminalBoard = Board.EMPTY.place(Cell.of(0), Symbol.X).place(Cell.of(1), Symbol.X)
                .place(Cell.of(2), Symbol.X).place(Cell.of(3), Symbol.O)
            val outcome = GameOutcome.Win(Player.TWO, Symbol.X, terminalBoard.winningLines(Symbol.X))
            val terminal = draw.copy(
                stage = TurnStage.TERMINAL,
                picPac = PicPacState(terminalBoard, Player.TWO, 2, 4, PicPacPhase.Terminal(outcome), Player.ONE, 1),
            )
            return listOf(
                "computer-turn-start" to draw,
                "computer-reveal" to reveal,
                "computer-thinking" to reveal.copy(stage = TurnStage.AI_THINKING),
                "computer-target" to targeting,
                "computer-placement" to placing,
                "computer-settled" to placing.copy(stage = TurnStage.AI_SETTLING),
                "computer-result" to terminal,
            )
        }
    }
}
