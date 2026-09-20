package com.thevaguebox.probabilistictictactoe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.AppScreen
import com.thevaguebox.probabilistictictactoe.ui.GameMode
import com.thevaguebox.probabilistictictactoe.ui.GameScreen
import com.thevaguebox.probabilistictictactoe.ui.GameUiState
import com.thevaguebox.probabilistictictactoe.ui.HomeScreen
import com.thevaguebox.probabilistictictactoe.ui.TurnStage
import com.thevaguebox.probabilistictictactoe.ui.components.HomeWordmark
import com.thevaguebox.probabilistictictactoe.ui.components.WordmarkProgressKey
import com.thevaguebox.probabilistictictactoe.ui.theme.FormBrandTypography
import com.thevaguebox.probabilistictictactoe.ui.theme.PicPacTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real font/composable rendering. These labelled fixtures are not live game-flow captures. */
@RunWith(AndroidJUnit4::class)
class BrandTypographyTest {
    @get:Rule val compose = createComposeRule()

    @Test fun candidateWeightsAreRenderedTogetherInBothThemes() {
        val theme = mutableStateOf(ThemePreference.DARK)
        compose.setContent {
            PicPacTheme(theme.value) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Text("Wordmark comparison", style = MaterialTheme.typography.titleLarge)
                        Text("Before · system sans, ExtraBold · 42sp", style = MaterialTheme.typography.bodyMedium)
                        HomeWordmark(style = TextStyle(
                            fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold,
                            fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-.8).sp,
                        ))
                        Text("Fredoka Medium · 500 · 40sp", style = MaterialTheme.typography.bodyMedium)
                        HomeWordmark(style = FormBrandTypography.wordmark.copy(fontWeight = FontWeight.Medium))
                        Text("Fredoka SemiBold · 600 · 40sp", style = MaterialTheme.typography.bodyMedium)
                        HomeWordmark(style = FormBrandTypography.wordmark)
                        Text("The same available width, without effects or colour accents.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { value ->
            compose.runOnIdle { theme.value = value }
            compose.waitForIdle()
            compose.onAllNodes(hasTestTag("home-wordmark")).assertCountEquals(3)
            captureSettledDevice("brand-candidates-${value.name.lowercase()}")
        }
    }

    @Test fun homeWordmarkKeepsOneAccessibleHeadingAt320dpAndAllRequiredFontScales() {
        val request = mutableStateOf(LayoutRequest())
        val previousWordmarkHeightPx = mutableStateOf(0)
        compose.setContent {
            val current = request.value
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = constraints.maxWidth.toFloat() / 320f
                CompositionLocalProvider(LocalDensity provides Density(density, current.fontScale)) {
                    key(current.fontScale) {
                        PicPacTheme(current.theme) {
                            val measurer = rememberTextMeasurer()
                            val previousStyle = MaterialTheme.typography.displayLarge
                            val availableWidth = with(LocalDensity.current) { 280.dp.roundToPx() }
                            val measuredPreviousHeight = measurer.measure(
                                "Pic-Pac-Poe",
                                style = previousStyle,
                                maxLines = 1,
                                softWrap = false,
                                constraints = Constraints(maxWidth = availableWidth),
                            ).size.height
                            SideEffect { previousWordmarkHeightPx.value = measuredPreviousHeight }
                            Surface(Modifier.fillMaxSize().testTag("brand-viewport")) {
                                HomeScreen({}, {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
            listOf(1f, 1.3f, 2f).forEach { scale ->
                compose.runOnIdle { request.value = LayoutRequest(theme, scale) }
                compose.waitForIdle()
                compose.onNodeWithText("Pic-Pac-Poe").performScrollTo().assertIsDisplayed()
                    .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                compose.onAllNodes(hasTestTag("home-wordmark")).assertCountEquals(1)
                val title = compose.onNodeWithTag("home-wordmark").fetchSemanticsNode().boundsInRoot
                val viewport = compose.onNodeWithTag("brand-viewport").fetchSemanticsNode().boundsInRoot
                val pxPerDp = viewport.width / 320f
                assertTrue("Wordmark crossed the left gutter: $title", title.left >= viewport.left + 19f * pxPerDp)
                assertTrue("Wordmark crossed the right gutter: $title", title.right <= viewport.right - 19f * pxPerDp)
                val previousHeight = previousWordmarkHeightPx.value
                assertTrue("Previous system wordmark was not measured", previousHeight > 0)
                assertTrue(
                    "Wordmark grew above the actual prior single-line title: previous=${previousHeight}px new=${title.height}px fontScale=$scale density=$pxPerDp bounds=$title",
                    title.height <= previousHeight + 1f,
                )
                assertPartsFitOnOneLine()
                captureSettledDevice("brand-home-320dp-${(scale * 100).toInt()}pct-${theme.name.lowercase()}")
                compose.onNodeWithText("Play").performScrollTo().assertIsDisplayed().assertIsEnabled()
                compose.onNodeWithText("Settings").performScrollTo().assertIsDisplayed()
            }
        }
    }

    @Test fun englishIdentityPreservesItsOrderAndUnclippedTextInsideAnRtlApp() {
        val scale = mutableStateOf(1f)
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = constraints.maxWidth.toFloat() / 320f
                CompositionLocalProvider(
                    LocalDensity provides Density(density, scale.value),
                    LocalLayoutDirection provides LayoutDirection.Rtl,
                ) {
                    key(scale.value) {
                        PicPacTheme(ThemePreference.LIGHT, reducedMotion = true) {
                            HomeWordmark(Modifier.padding(horizontal = 20.dp, vertical = 40.dp))
                        }
                    }
                }
            }
        }
        listOf(1f, 1.3f, 2f).forEach { value ->
            compose.runOnIdle { scale.value = value }
            compose.waitForIdle()
            compose.onNodeWithText("Pic-Pac-Poe").assertIsDisplayed()
            assertPartsFitOnOneLine()
            captureSettledDevice("brand-wordmark-rtl-320dp-${(value * 100).toInt()}pct")
        }
    }

    @Test fun entranceWaitsForSettingsStaggersFinishesAndDoesNotRestartOnRecomposition() {
        val ready = mutableStateOf(false)
        val theme = mutableStateOf(ThemePreference.DARK)
        var starts = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicPacTheme(theme.value) {
                Surface(Modifier.fillMaxWidth().padding(20.dp)) {
                    HomeWordmark(animateEntrance = true, motionReady = ready.value, onEntranceStarted = { starts++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(200)
        compose.waitForIdle()
        assertEquals("Entrance must wait for persisted motion preference", 0, starts)
        compose.runOnIdle { ready.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        captureSettledDevice("brand-motion-start")
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        assertEquals(1, starts)
        val stagger = progress()
        assertEquals(3, stagger.size)
        assertTrue("First group must lead second: $stagger", stagger[0] > stagger[1])
        assertTrue("Second group must lead third: $stagger", stagger[1] > stagger[2])
        captureSettledDevice("brand-motion-stagger")
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        captureSettledDevice("brand-motion-settling")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertSettled()
        captureSettledDevice("brand-motion-final")
        compose.runOnIdle { theme.value = ThemePreference.LIGHT }
        compose.mainClock.advanceTimeBy(1_800)
        compose.waitForIdle()
        assertSettled()
        assertEquals("Recomposition must not replay the entrance", 1, starts)
    }

    @Test fun reducedMotionIsImmediatelyStillAndCanStopAnInFlightEntrance() {
        val reduced = mutableStateOf(true)
        val instance = mutableStateOf(0)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PicPacTheme(ThemePreference.DARK, reducedMotion = reduced.value) {
                key(instance.value) { HomeWordmark(animateEntrance = true) }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertSettled()
        captureSettledDevice("brand-motion-reduced-immediate")
        compose.runOnIdle { reduced.value = false; instance.value++ }
        compose.mainClock.advanceTimeBy(180)
        compose.waitForIdle()
        assertTrue("Normal fresh entrance should be in progress", progress().any { it < .99f })
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertSettled()
        compose.mainClock.advanceTimeBy(1_800)
        compose.waitForIdle()
        assertSettled()
    }

    @Test fun emotionalHeadlinesRemainReadableAndActionsReachableAcrossThemesAndLargeText() {
        val request = mutableStateOf(EmotionRequest())
        compose.setContent {
            val current = request.value
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = constraints.maxWidth.toFloat() / 320f
                CompositionLocalProvider(LocalDensity provides Density(density, current.fontScale)) {
                    key(current.fontScale) {
                        PicPacTheme(current.theme, reducedMotion = true) {
                            // Match PicPacApp's parent Surface so inherited headline/content
                            // colours are real theme colours, not the test host's default black.
                            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                                GameScreen(current.fixture.state, true, {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
            }
        }
        listOf(ThemePreference.DARK, ThemePreference.LIGHT).forEach { theme ->
            listOf(1f, 2f).forEach { scale ->
                emotionalFixtures().forEach { fixture ->
                    compose.runOnIdle { request.value = EmotionRequest(theme, scale, fixture) }
                    compose.waitForIdle()
                    compose.onNodeWithText(fixture.headline).performScrollTo().assertIsDisplayed()
                    captureSettledDevice("brand-emotion-${fixture.name}-320dp-${(scale * 100).toInt()}pct-${theme.name.lowercase()}")
                    fixture.action?.let { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed().assertIsEnabled() }
                    if (fixture.state.stage == TurnStage.HANDOFF) {
                        compose.onAllNodes(hasTestTag("game-board")).assertCountEquals(0)
                    }
                }
            }
        }
    }

    private fun progress(): List<Float> = compose.onNodeWithTag("home-wordmark").fetchSemanticsNode().config[WordmarkProgressKey]

    private fun assertSettled() = assertTrue("Title must be fully settled: ${progress()}", progress().all { abs(it - 1f) < .001f })

    private fun assertPartsFitOnOneLine() {
        val parent = compose.onNodeWithTag("home-wordmark").fetchSemanticsNode().boundsInRoot
        val parts = listOf("Pic", "-", "Pac", "-", "Poe").mapIndexed { index, expected ->
            val node = compose.onNodeWithTag("wordmark-part-$index", useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            assertEquals("Expected a real Text layout for part $index", 1, layouts.size)
            val layout = layouts.single()
            val fontPx = with(layout.layoutInput.density) { layout.layoutInput.style.fontSize.toPx() }
            val lineHeightPx = with(layout.layoutInput.density) { layout.layoutInput.style.lineHeight.toPx() }
            val lineLeft = layout.getLineLeft(0)
            val lineRight = layout.getLineRight(0)
            val lineTop = layout.getLineTop(0)
            val lineBottom = layout.getLineBottom(0)
            val baseline = layout.getLineBaseline(0)
            val diagnostic = "part=$index expected=$expected actual=${layout.layoutInput.text.text} " +
                "size=${layout.size} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} " +
                "overflowWidth=${layout.didOverflowWidth} overflowHeight=${layout.didOverflowHeight} " +
                "line=[$lineLeft,$lineTop,$lineRight,$lineBottom] baseline=$baseline " +
                "fontPx=$fontPx lineHeightPx=$lineHeightPx fontScale=${layout.layoutInput.density.fontScale} " +
                "lines=${layout.lineCount} style=${layout.layoutInput.style} density=${layout.layoutInput.density}"
            assertEquals(diagnostic, expected, layout.layoutInput.text.text)
            assertEquals("Wordmark part wrapped: $diagnostic", 1, layout.lineCount)
            // Compose 1.8 MultiParagraph.width retains the incoming maxWidth, while a
            // non-wrapping Text node can measure to its much smaller intrinsic width.
            // didOverflowWidth compares those containers, not the actual line extents.
            // Check every visible character and its rendered line instead of that proxy.
            assertEquals("Wordmark part dropped text: $diagnostic", expected.length, layout.getLineEnd(0, visibleEnd = true))
            assertTrue("Wordmark part ellipsized or exceeded its line limit: $diagnostic", !layout.isLineEllipsized(0) && !layout.multiParagraph.didExceedMaxLines)
            assertTrue("Wordmark line crossed its measured width: $diagnostic", lineLeft >= -1f && lineRight <= layout.size.width + 1f)
            assertTrue("Wordmark line crossed its measured height: $diagnostic", lineTop >= -1f && lineBottom <= layout.size.height + 1f)
            assertTrue("Wordmark baseline escaped its line: $diagnostic", baseline in lineTop..lineBottom)
            expected.indices.forEach { offset ->
                val glyph = layout.getBoundingBox(offset)
                assertTrue("Character $offset crossed measured width: $glyph; $diagnostic", glyph.left >= -1f && glyph.right <= layout.size.width + 1f)
                assertTrue("Character $offset crossed measured height: $glyph; $diagnostic", glyph.top >= -1f && glyph.bottom <= layout.size.height + 1f)
            }
            node.fetchSemanticsNode().boundsInRoot.also { bounds ->
                assertTrue("Part $index crossed title bounds: $bounds / $parent", bounds.left >= parent.left - 1f && bounds.right <= parent.right + 1f)
                assertTrue("Part $index crossed title height: $bounds / $parent", bounds.top >= parent.top - 1f && bounds.bottom <= parent.bottom + 1f)
            }
        }
        parts.zipWithNext().forEachIndexed { index, (left, right) ->
            assertTrue("English word groups reordered or overlap at $index: $parts", left.right <= right.left + 1f)
            assertTrue("Wordmark parts do not share one line: $parts", abs(left.top - right.top) < 1f)
        }
    }

    private data class LayoutRequest(val theme: ThemePreference = ThemePreference.DARK, val fontScale: Float = 1f)
    private data class EmotionRequest(
        val theme: ThemePreference = ThemePreference.DARK,
        val fontScale: Float = 1f,
        val fixture: EmotionalFixture = emotionalFixtures().first(),
    )
    private data class EmotionalFixture(val name: String, val headline: String, val state: GameUiState, val action: String? = null)

    private companion object {
        fun emotionalFixtures(): List<EmotionalFixture> {
            val winBoard = Board.EMPTY.place(Cell.of(0), Symbol.X).place(Cell.of(1), Symbol.X)
                .place(Cell.of(2), Symbol.X).place(Cell.of(3), Symbol.O)
            val outcome = GameOutcome.Win(Player.TWO, Symbol.X, winBoard.winningLines(Symbol.X))
            val computerWin = GameUiState(
                screen = AppScreen.GAME, mode = GameMode.PIC_PAC_AI, stage = TurnStage.TERMINAL,
                picPac = PicPacState(winBoard, Player.TWO, 2, 4, PicPacPhase.Terminal(outcome), Player.ONE, 1),
            )
            val playerWin = computerWin.copy(
                mode = GameMode.PIC_PAC_LOCAL,
                picPac = computerWin.picPac!!.copy(phase = PicPacPhase.Terminal(outcome.copy(player = Player.ONE))),
            )
            val drawBoard = listOf(Symbol.X, Symbol.O, Symbol.X, Symbol.X, Symbol.O, Symbol.O, Symbol.O, Symbol.X, Symbol.X)
                .foldIndexed(Board.EMPTY) { index, board, symbol -> board.place(Cell.of(index), symbol) }
            val draw = GameUiState(
                screen = AppScreen.GAME, mode = GameMode.CLASSIC_LOCAL, stage = TurnStage.TERMINAL,
                classic = ClassicState(drawBoard, Player.ONE, Player.ONE, TurnToken(9), 1, GameOutcome.Draw),
            )
            val reveal = GameUiState(
                screen = AppScreen.GAME, mode = GameMode.PIC_PAC_AI, stage = TurnStage.REVEALING,
                picPac = PicPacState(Board.EMPTY, Player.TWO, 5, 4, PicPacPhase.AwaitingPlacement(Symbol.O, TurnToken(1)), Player.TWO, 1),
            )
            val handoff = GameUiState(
                screen = AppScreen.GAME, mode = GameMode.PIC_PAC_LOCAL, stage = TurnStage.HANDOFF,
                picPac = PicPacState(Board.EMPTY, Player.ONE, 5, 5, PicPacPhase.AwaitingDraw, Player.ONE, 1),
            )
            return listOf(
                EmotionalFixture("computer-win", "Computer wins", computerWin, "Rematch"),
                EmotionalFixture("player-win", "Player 1 wins", playerWin, "Rematch"),
                EmotionalFixture("draw", "Draw", draw, "Rematch"),
                EmotionalFixture("computer-reveal", "Computer drew O", reveal),
                EmotionalFixture("handoff", "Player 1, you're up.", handoff, "Ready"),
            )
        }
    }
}
