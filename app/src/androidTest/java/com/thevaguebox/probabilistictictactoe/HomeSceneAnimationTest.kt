package com.thevaguebox.probabilistictictactoe

import android.graphics.Bitmap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.HomeScreen
import com.thevaguebox.probabilistictictactoe.ui.components.FormHomeScene
import com.thevaguebox.probabilistictictactoe.ui.theme.PicPacTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Clock-controlled captures of production composables, not substitute marks/mockups. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class HomeSceneAnimationTest {
    private val systemScale = object : MotionDurationScale {
        var value = mutableFloatStateOf(1f)
        override val scaleFactor get() = value.floatValue
    }
    @get:Rule val compose = createComposeRule(effectContext = systemScale)

    @Test fun bothSymbolsKeepGeometryAndStationarySceneAcrossThemesMotionAndNarrowWidth() {
        val request = mutableStateOf(Request())
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val current = request.value
            BoxWithConstraints(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalDensity provides Density(constraints.maxWidth / current.width, 1f)) {
                    key(current) {
                        PicPacTheme(current.theme, reducedMotion = current.reduced) {
                            Surface(Modifier.fillMaxSize()) { HomeScreen({}, {}, {}, {}, {}, {}) }
                        }
                    }
                }
            }
        }
        for (theme in listOf(ThemePreference.DARK, ThemePreference.LIGHT)) {
            for (reduced in listOf(false, true)) {
                for (width in listOf(412f, 320f)) {
                    compose.runOnIdle { request.value = Request(theme, reduced, width) }
                    advance(800)
                    val bounds = scene().fetchSemanticsNode().boundsInRoot
                    val controls = compose.onNodeWithText("Classic").fetchSemanticsNode().boundsInRoot
                    val x = bitmap()
                    assertSymbol(x, isX = true)
                    val label = "home-scene-${theme.name.lowercase()}-${if (reduced) "reduced" else "normal"}-${width.toInt()}dp"
                    captureSettledDevice("$label-x")
                    assertStableSemantics()
                    advance(1_400)
                    val o = bitmap()
                    assertSymbol(o, isX = false)
                    assertEquals(bounds, scene().fetchSemanticsNode().boundsInRoot)
                    assertEquals(controls, compose.onNodeWithText("Classic").fetchSemanticsNode().boundsInRoot)
                    assertStationaryOutsidePiece(x, o)
                    assertStableSemantics()
                    captureSettledDevice("$label-o")
                    advance(1_400)
                    assertSymbol(bitmap(), isX = true)
                }
            }
        }
    }

    @Test fun settingsReadinessLifecycleAndRemovalCancelAndRestartTheIllustration() {
        val ready = mutableStateOf(false)
        val show = mutableStateOf(true)
        val owner = TestOwner()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PicPacTheme(ThemePreference.DARK) {
                    Surface(Modifier.fillMaxSize()) {
                        Column {
                            if (show.value) FormHomeScene(Modifier.fillMaxWidth().height(118.dp), motionReady = ready.value)
                        }
                    }
                }
            }
        }
        advance(2_100)
        assertSymbol(bitmap(), true)
        compose.runOnIdle { ready.value = true }
        advance(2_100)
        assertSymbol(bitmap(), false)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        advance(2_100)
        assertSymbol(bitmap(), true)
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        advance(800)
        assertSymbol(bitmap(), true)
        advance(1_400)
        assertSymbol(bitmap(), false)
        compose.runOnIdle { show.value = false }
        advance(3_000)
        scene().assertDoesNotExist()
        compose.runOnIdle { show.value = true }
        advance(800)
        assertSymbol(bitmap(), true)
    }

    @Test(timeout = 60_000) fun scrollingFullyOffscreenDisposesTheTimeline() {
        lateinit var scroll: ScrollState
        lateinit var scope: CoroutineScope
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scrollState = rememberScrollState()
            val scrollScope = rememberCoroutineScope()
            SideEffect { scroll = scrollState; scope = scrollScope }
            PicPacTheme(ThemePreference.DARK) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(scrollState)) {
                        FormHomeScene(Modifier.fillMaxWidth().height(118.dp))
                        Spacer(Modifier.height(2_000.dp))
                        Text("Bottom of fixture")
                    }
                }
            }
        }
        advance(2_100)
        assertSymbol(bitmap(), false)
        // Launch asynchronously so scrolling's frame await cannot deadlock a paused test clock.
        compose.runOnIdle { scope.launch { scroll.scrollTo(scroll.maxValue) } }
        advance(2_800)
        compose.runOnIdle { assertTrue("Fixture must really scroll offscreen", scroll.value > 1_000) }
        compose.runOnIdle { scope.launch { scroll.scrollTo(0) } }
        advance(100)
        // A retained timeline would still be O here; visibility restarts at X.
        assertSymbol(bitmap(), true)
        advance(2_000)
        assertSymbol(bitmap(), false)
    }

    @Test fun systemAnimationsOffStillAlternatesWithoutPulseOrFade() {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { systemScale.value.floatValue = 0f }
        compose.setContent {
            PicPacTheme(ThemePreference.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    Column { FormHomeScene(Modifier.fillMaxWidth().height(118.dp)) }
                }
            }
        }
        advance(800)
        val x = bitmap()
        assertSymbol(x, true)
        advance(1_400)
        val o = bitmap()
        assertSymbol(o, false)
        assertStationaryOutsidePiece(x, o)
        assertStableSemantics()
        captureSettledDevice("home-scene-system-motion-off-o")
        advance(1_400)
        assertSymbol(bitmap(), true)
        compose.runOnIdle { systemScale.value.floatValue = 1f }
        advance(2_100)
        assertSymbol(bitmap(), false)
        compose.runOnIdle { systemScale.value.floatValue = 0f }
        advance(100)
        assertSymbol(bitmap(), true)
        advance(1_500)
        assertSymbol(bitmap(), false)
    }

    private fun advance(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun scene() = compose.onNodeWithTag("home-draw-illustration")
    private fun bitmap() = scene().captureToImage().asAndroidBitmap()

    private fun assertStableSemantics() {
        compose.onAllNodesWithContentDescription(DESCRIPTION, useUnmergedTree = true).assertCountEquals(1)
        val config = scene().fetchSemanticsNode().config
        assertEquals(listOf(DESCRIPTION), config[SemanticsProperties.ContentDescription])
        assertFalse(config.contains(SemanticsProperties.LiveRegion))
        assertFalse(config.contains(SemanticsProperties.StateDescription))
    }

    private fun assertSymbol(bitmap: Bitmap, isX: Boolean) {
        var coral = 0
        var lime = 0
        // Production piece faces/highlights use different red/green dominance in both themes.
        for (x in bitmap.width * 2 / 5 until bitmap.width * 3 / 5) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                if (r > g + 25 && r > b + 25) coral++
                if (g > r + 8 && g > b + 25) lime++
            }
        }
        assertTrue("Expected ${if (isX) "coral X" else "lime O"}, coral=$coral lime=$lime", if (isX) coral > 50 && lime == 0 else lime > 50 && coral == 0)
    }

    private fun assertStationaryOutsidePiece(first: Bitmap, second: Bitmap) {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        // Center exclusion is 20% of the scene, enclosing the fixed44dp piece and its shadow.
        for (x in 0 until first.width) {
            if (x in first.width * 2 / 5..first.width * 3 / 5) continue
            for (y in 0 until first.height) {
                assertEquals("Bag/arrows/board moved at $x,$y", first.getPixel(x, y), second.getPixel(x, y))
            }
        }
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    private data class Request(val theme: ThemePreference = ThemePreference.DARK, val reduced: Boolean = false, val width: Float = 412f)
    private companion object {
        const val DESCRIPTION = "A random X or O is drawn from the bag, then placed on the board."
    }
}
