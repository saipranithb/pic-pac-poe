package com.thevaguebox.probabilistictictactoe

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thevaguebox.probabilistictictactoe.settings.SettingsStore
import com.thevaguebox.probabilistictictactoe.ui.components.WordmarkProgressKey
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandLifecycleTest {
    // Launch explicitly so an interrupted-entrance test can freeze the clock before composition.
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun returningHomeAndRecreatingTheActivityNeverReplayAConsumedEntrance() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Pic-Pac-Poe").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
            assertSettled()
            compose.onNodeWithText("Settings").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Back").performScrollTo().assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Back").performClick()
            repeat(12) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertSettled()
            }
            captureSettledDevice("brand-actual-home-return-settled")
            scenario.recreate()
            repeat(12) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                assertSettled()
            }
            compose.onNodeWithText("Pic-Pac-Poe").assertIsDisplayed()
            captureSettledDevice("brand-actual-home-recreated-settled")
        }
    }

    @Test fun recreationDuringTheFirstEntranceReturnsDirectlyToItsFinalState() {
        val settings = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val previousReducedMotion = runBlocking { settings.settings.first().reducedMotion }
        runBlocking { settings.setReducedMotion(false) }
        compose.mainClock.autoAdvance = false
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val deadline = SystemClock.uptimeMillis() + 10_000
                var inFlight = false
                while (SystemClock.uptimeMillis() < deadline && !inFlight) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    val values = progress()
                    inFlight = values.any { it > .02f && it < .95f }
                    if (!inFlight) SystemClock.sleep(8)
                }
                assertTrue("Did not observe the fresh entrance before its 620ms endpoint", inFlight)
                captureSettledDevice("brand-actual-home-interrupted-before-recreation")
                scenario.recreate()
                repeat(12) {
                    compose.mainClock.advanceTimeByFrame()
                    compose.waitForIdle()
                    assertSettled()
                }
                captureSettledDevice("brand-actual-home-interrupted-recreation-settled")
            }
        } finally {
            runBlocking { settings.setReducedMotion(previousReducedMotion) }
        }
    }

    private fun progress(): List<Float> = compose.onNodeWithTag("home-wordmark").fetchSemanticsNode().config[WordmarkProgressKey]

    private fun assertSettled() {
        val values = progress()
        assertTrue("A consumed title entrance replayed: $values", values.all { abs(it - 1f) < .001f })
    }
}
