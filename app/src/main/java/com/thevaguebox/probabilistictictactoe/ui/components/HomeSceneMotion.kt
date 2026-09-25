package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.animation.core.keyframes
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.theme.FormMotion

/** Local illustrative timing only: never draws from a game bag or advances a game turn. */
internal object HomeSceneMotion {
    const val symbolMillis = 1_400
    const val cycleMillis = symbolMillis * 2

    fun alpha(symbol: Symbol, reducedMotion: Boolean) = keyframes {
        durationMillis = cycleMillis
        val fade = if (reducedMotion) 400 else 280
        val first = if (symbol == Symbol.X) 1f else 0f
        val second = 1f - first
        first at 0
        first at (symbolMillis - fade) using FormMotion.easing
        second at symbolMillis
        second at (cycleMillis - fade) using FormMotion.easing
        first at cycleMillis
    }

    fun scale(symbol: Symbol, reducedMotion: Boolean) = keyframes {
        durationMillis = cycleMillis
        if (reducedMotion) {
            1f at 0
            1f at cycleMillis
        } else if (symbol == Symbol.X) {
            1f at 0
            1f at 200 using FormMotion.easing
            1.035f at 340 using FormMotion.easing
            1f at 520
            1f at 1_120 using FormMotion.easing
            .97f at symbolMillis
            .97f at 2_520 using FormMotion.easing
            1f at cycleMillis
        } else {
            .97f at 0
            .97f at 1_120 using FormMotion.easing
            1f at symbolMillis
            1f at 1_600 using FormMotion.easing
            1.035f at 1_740 using FormMotion.easing
            1f at 1_920
            1f at 2_520 using FormMotion.easing
            .97f at cycleMillis
        }
    }
}
