package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.infiniteRepeatable
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.components.HomeSceneMotion
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Samples the production Compose specs directly, without a second timing implementation. */
class HomeSceneMotionTest {
    @Test fun `each symbol receives a 1400 millisecond half of the complete cycle`() {
        assertEquals(1_400, HomeSceneMotion.symbolMillis)
        assertEquals(2_800, HomeSceneMotion.cycleMillis)
        listOf(false, true).forEach { reduced ->
            Symbol.entries.forEach { symbol ->
                val alpha = alphaAnimation(symbol, reduced)
                val scale = scaleAnimation(symbol, reduced)
                assertEquals(2_800_000_000L, alpha.durationNanos)
                assertEquals(2_800_000_000L, scale.durationNanos)
                val initial = if (symbol == Symbol.X) 1f else 0f
                assertEquals(initial, at(alpha, 0), 0f)
                assertEquals(1f - initial, at(alpha, 1_400), 0f)
                assertEquals(initial, at(alpha, 2_800), 0f)
            }
        }
    }

    @Test fun `normal and reduced alpha stay complementary and bounded throughout the cycle`() {
        listOf(false, true).forEach { reduced ->
            val x = alphaAnimation(Symbol.X, reduced)
            val o = alphaAnimation(Symbol.O, reduced)
            for (millis in 0..HomeSceneMotion.cycleMillis) {
                val xValue = at(x, millis)
                val oValue = at(o, millis)
                val context = "reduced=$reduced at ${millis}ms: X=$xValue O=$oValue"
                assertTrue("X alpha escaped 0..1: $context", xValue in 0f..1f)
                assertTrue("O alpha escaped 0..1: $context", oValue in 0f..1f)
                assertEquals("Fade must not expose a blank or over-opaque gap: $context", 1f, xValue + oValue, EPSILON)
            }
        }
    }

    @Test fun `normal fades last 280 milliseconds and reduced fades last 400 milliseconds`() {
        listOf(false to 280, true to 400).forEach { (reduced, fadeMillis) ->
            val x = alphaAnimation(Symbol.X, reduced)
            val o = alphaAnimation(Symbol.O, reduced)
            for (half in 0..1) {
                val start = half * HomeSceneMotion.symbolMillis
                val end = start + HomeSceneMotion.symbolMillis
                val fadeStart = end - fadeMillis
                val outgoing = if (half == 0) x else o
                val incoming = if (half == 0) o else x
                for (millis in start..fadeStart) {
                    assertEquals("Outgoing plateau changed early at ${millis}ms (reduced=$reduced)", 1f, at(outgoing, millis), 0f)
                    assertEquals("Incoming piece appeared early at ${millis}ms (reduced=$reduced)", 0f, at(incoming, millis), 0f)
                }
                var previous = 1f
                for (millis in fadeStart + 1 until end) {
                    val value = at(outgoing, millis)
                    assertTrue("Fade not in progress at ${millis}ms (reduced=$reduced): $value", value > 0f && value < 1f)
                    assertTrue("Fade reversed at ${millis}ms (reduced=$reduced)", value <= previous + EPSILON)
                    previous = value
                }
                assertEquals("Outgoing fade did not finish at ${end}ms", 0f, at(outgoing, end), 0f)
                assertEquals("Incoming fade did not finish at ${end}ms", 1f, at(incoming, end), 0f)
            }
        }
    }

    @Test fun `normal pressure has exactly one bounded pulse per symbol with no overshoot`() {
        Symbol.entries.forEach { symbol ->
            val scale = scaleAnimation(symbol, reduced = false)
            val offset = if (symbol == Symbol.X) 0 else HomeSceneMotion.symbolMillis
            assertEquals(1f, at(scale, offset + 200), 0f)
            assertEquals(1.035f, at(scale, offset + 340), 0f)
            assertEquals(1f, at(scale, offset + 520), 0f)

            var pulseCount = 0
            var wasAboveRest = false
            for (millis in 0..HomeSceneMotion.cycleMillis) {
                val value = at(scale, millis)
                assertTrue("$symbol pressure escaped .97..1.035 at ${millis}ms: $value", value >= .97f - EPSILON && value <= 1.035f + EPSILON)
                val aboveRest = value > 1f + EPSILON
                if (aboveRest && !wasAboveRest) pulseCount++
                wasAboveRest = aboveRest
            }
            assertEquals("$symbol must have one pulse, not a bounce or repeated pressure", 1, pulseCount)

            var previous = at(scale, offset + 200)
            for (millis in offset + 201..offset + 340) {
                val value = at(scale, millis)
                assertTrue("$symbol pressure reversed on ascent at ${millis}ms", value + EPSILON >= previous)
                previous = value
            }
            for (millis in offset + 341..offset + 520) {
                val value = at(scale, millis)
                assertTrue("$symbol pressure bounced while settling at ${millis}ms", value <= previous + EPSILON)
                previous = value
            }
        }
    }

    @Test fun `reduced motion scale remains exactly one during both plateaus and fades`() {
        Symbol.entries.forEach { symbol ->
            val scale = scaleAnimation(symbol, reduced = true)
            // Quarter-millisecond samples include sub-frame phases, not only keyframe times.
            for (quarterMillis in 0..HomeSceneMotion.cycleMillis * 4) {
                val value = scale.getValueFromNanos(quarterMillis * 250_000L)
                assertEquals("$symbol moved under reduced motion at quarter-ms $quarterMillis", 1f, value, 0f)
            }
        }
    }

    @Test fun `alpha and scale join continuously at every repeating cycle boundary`() {
        listOf(false, true).forEach { reduced ->
            Symbol.entries.forEach { symbol ->
                val initialAlpha = if (symbol == Symbol.X) 1f else 0f
                val initialScale = if (reduced || symbol == Symbol.X) 1f else .97f
                val specs = listOf(
                    HomeSceneMotion.alpha(symbol, reduced) to initialAlpha,
                    HomeSceneMotion.scale(symbol, reduced) to initialScale,
                )
                specs.forEach { (spec, initial) ->
                    val finite = animation(spec, initial)
                    val repeating = animation(infiniteRepeatable(spec), initial)
                    assertEquals("Finite endpoints differ for $symbol (reduced=$reduced)", at(finite, 0), at(finite, HomeSceneMotion.cycleMillis), 0f)
                    for (cycle in 1..3) {
                        val boundary = cycle * HomeSceneMotion.cycleMillis
                        val before = at(repeating, boundary - 1)
                        val joined = at(repeating, boundary)
                        val after = at(repeating, boundary + 1)
                        assertEquals("Repeat boundary reset incorrectly for $symbol (reduced=$reduced)", initial, joined, EPSILON)
                        assertTrue("Visible jump before repeat boundary: $before -> $joined", abs(before - joined) < .001f)
                        assertTrue("Visible jump after repeat boundary: $joined -> $after", abs(after - joined) < .001f)
                    }
                }
            }
        }
    }

    private fun alphaAnimation(symbol: Symbol, reduced: Boolean) =
        animation(HomeSceneMotion.alpha(symbol, reduced), if (symbol == Symbol.X) 1f else 0f)

    private fun scaleAnimation(symbol: Symbol, reduced: Boolean) =
        animation(HomeSceneMotion.scale(symbol, reduced), if (reduced || symbol == Symbol.X) 1f else .97f)

    private fun animation(spec: AnimationSpec<Float>, initial: Float) = TargetBasedAnimation(
        animationSpec = spec,
        typeConverter = Float.VectorConverter,
        initialValue = initial,
        targetValue = initial,
    )

    private fun at(animation: TargetBasedAnimation<Float, AnimationVector1D>, millis: Int) =
        animation.getValueFromNanos(millis * 1_000_000L)

    private companion object {
        const val EPSILON = .00001f
    }
}
