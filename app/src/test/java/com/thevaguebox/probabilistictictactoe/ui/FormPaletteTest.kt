package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.thevaguebox.probabilistictictactoe.ui.theme.FormDarkColors
import com.thevaguebox.probabilistictictactoe.ui.theme.FormLightColors
import org.junit.Assert.assertTrue
import org.junit.Test

class FormPaletteTest {
    @Test fun `every wordmark group meets normal text contrast on the Home canvas`() {
        listOf(FormDarkColors, FormLightColors).forEach { colors ->
            listOf(colors.x, colors.textSecondary, colors.text, colors.textSecondary, colors.o)
                .forEachIndexed { index, color -> assertContrast(color, colors.canvas, 4.5, "wordmark part $index") }
        }
    }

    @Test fun `semantic text meets normal text contrast on every content surface`() {
        listOf(FormDarkColors, FormLightColors).forEach { colors ->
            val surfaces = listOf(colors.canvas, colors.surface, colors.surfaceRaised, colors.recess)
            surfaces.forEach { surface ->
                assertContrast(colors.text, surface, 4.5, "primary text")
                assertContrast(colors.textSecondary, surface, 4.5, "secondary text")
            }
            assertContrast(colors.onAction, colors.action, 4.5, "action label")
        }
    }

    @Test fun `meaningful symbol actor and focus colors stay distinguishable from their surfaces`() {
        listOf(FormDarkColors, FormLightColors).forEach { colors ->
            val foregrounds = mapOf(
                "X" to colors.x,
                "O" to colors.o,
                "Player one" to colors.playerOne,
                "Player two" to colors.playerTwo,
                "Focus" to colors.focus,
                "Boundary" to colors.border,
            )
            listOf(colors.canvas, colors.surface, colors.recess).forEach { surface ->
                foregrounds.forEach { (role, foreground) -> assertContrast(foreground, surface, 3.0, role) }
            }
            assertTrue("Actor colors must not imply symbol ownership", colors.playerOne != colors.x)
            assertTrue("Actor colors must not imply symbol ownership", colors.playerTwo != colors.o)
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double, role: String) {
        val first = foreground.luminance().toDouble()
        val second = background.luminance().toDouble()
        val ratio = (maxOf(first, second) + .05) / (minOf(first, second) + .05)
        assertTrue("$role contrast $ratio is below $minimum ($foreground on $background)", ratio >= minimum)
    }
}
