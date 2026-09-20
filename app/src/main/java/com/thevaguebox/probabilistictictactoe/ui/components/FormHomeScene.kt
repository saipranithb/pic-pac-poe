package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** A noninteractive explanation: a shared bag, one drawn piece, then a square. */
@Composable
fun FormHomeScene(modifier: Modifier = Modifier, motionReady: Boolean = true) {
    val colors = FormTheme.colors
    val reducedMotion = FormTheme.reducedMotion
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    var visible by remember { mutableStateOf(false) }
    val active = motionReady && visible && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    Box(modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }
        .testTag("home-draw-illustration")
        .clearAndSetSemantics {
            contentDescription = "A random X or O is drawn from the bag, then placed on the board."
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 400.dp).fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val side = size.height * .82f
                val top = (size.height - side) / 2
                val bagLeft = (size.width * .2f - side / 2).coerceAtLeast(0f)
                val bag = Path().apply {
                    moveTo(bagLeft + side * .22f, top + side * .2f)
                    cubicTo(bagLeft + side * .14f, top + side * .39f, bagLeft, top + side * .63f, bagLeft + side * .1f, top + side * .84f)
                    cubicTo(bagLeft + side * .19f, top + side * 1.02f, bagLeft + side * .81f, top + side * 1.02f, bagLeft + side * .9f, top + side * .84f)
                    cubicTo(bagLeft + side, top + side * .63f, bagLeft + side * .86f, top + side * .39f, bagLeft + side * .78f, top + side * .2f)
                    close()
                }
                translate(top = 2.dp.toPx()) { drawPath(bag, colors.shadow) }
                drawPath(bag, Brush.verticalGradient(listOf(colors.surfaceRaised, colors.surface), startY = top, endY = top + side))
                drawPath(bag, colors.border, style = Stroke(1.dp.toPx()))
                drawOval(colors.recess, Offset(bagLeft + side * .19f, top + side * .08f), Size(side * .62f, side * .22f))
                drawOval(colors.border, Offset(bagLeft + side * .19f, top + side * .08f), Size(side * .62f, side * .22f), style = Stroke(1.dp.toPx()))
                // Seams identify the bag without implying that the player picks X or O.
                drawLine(colors.borderSubtle, Offset(bagLeft + side * .31f, top + side * .51f), Offset(bagLeft + side * .26f, top + side * .79f), 1.dp.toPx(), StrokeCap.Round)
                drawLine(colors.borderSubtle, Offset(bagLeft + side * .69f, top + side * .51f), Offset(bagLeft + side * .74f, top + side * .79f), 1.dp.toPx(), StrokeCap.Round)

                val boardLeft = size.width * .8f - side / 2
                val radius = CornerRadius(side * .12f)
                drawRoundRect(colors.shadow, Offset(boardLeft, top + 2.dp.toPx()), Size(side, side), radius)
                drawRoundRect(colors.surfaceRaised, Offset(boardLeft, top), Size(side, side), radius)
                drawRoundRect(colors.border, Offset(boardLeft, top), Size(side, side), radius, style = Stroke(1.dp.toPx()))
                val frame = side * .075f
                val gap = side * .035f
                val cell = (side - frame * 2 - gap * 2) / 3
                repeat(9) { index ->
                    val cellOrigin = Offset(boardLeft + frame + (index % 3) * (cell + gap), top + frame + (index / 3) * (cell + gap))
                    drawRoundRect(colors.recess, cellOrigin, Size(cell, cell), CornerRadius(cell * .19f))
                    drawRoundRect(if (index == 4) colors.text else colors.borderSubtle, cellOrigin, Size(cell, cell), CornerRadius(cell * .19f), style = Stroke(if (index == 4) 1.5.dp.toPx() else 1.dp.toPx()))
                }
                val arrowY = top + side * .52f
                fun arrow(start: Float, end: Float) {
                    if (end - start < 4.dp.toPx()) return
                    drawLine(colors.textSecondary, Offset(start, arrowY), Offset(end, arrowY), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.textSecondary, Offset(end - 4.dp.toPx(), arrowY - 4.dp.toPx()), Offset(end, arrowY), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.textSecondary, Offset(end - 4.dp.toPx(), arrowY + 4.dp.toPx()), Offset(end, arrowY), 1.5.dp.toPx(), StrokeCap.Round)
                }
                arrow(bagLeft + side + 5.dp.toPx(), size.width * .5f - 26.dp.toPx())
                arrow(size.width * .5f + 26.dp.toPx(), boardLeft - 6.dp.toPx())
            }
            // Only these equal-sized layers move. The scene's Canvas and layout never animate.
            if (active) {
                key(reducedMotion) { AlternatingHomePiece(reducedMotion) }
            } else {
                FormPiece(Symbol.X, Modifier.size(44.dp))
            }
        }
    }
}

@Composable
private fun AlternatingHomePiece(reducedMotion: Boolean) {
    val systemMotionScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    if (systemMotionScale == 0f) {
        // InfiniteTransition suspends at Android's animations-off setting. Retain the
        // explanation with infrequent, instantaneous swaps instead (no frame loop).
        var showO by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            while (isActive) {
                delay(HomeSceneMotion.symbolMillis.toLong())
                showO = !showO
            }
        }
        FormPiece(Symbol.X, Modifier.size(44.dp).graphicsLayer { alpha = if (showO) 0f else 1f })
        FormPiece(Symbol.O, Modifier.size(44.dp).graphicsLayer { alpha = if (showO) 1f else 0f })
    } else {
        val transition = rememberInfiniteTransition(label = "Home drawn piece")
        val xAlpha = transition.animateFloat(1f, 1f, infiniteRepeatable(HomeSceneMotion.alpha(Symbol.X, reducedMotion)), label = "X opacity")
        val oAlpha = transition.animateFloat(0f, 0f, infiniteRepeatable(HomeSceneMotion.alpha(Symbol.O, reducedMotion)), label = "O opacity")
        val xScale = transition.animateFloat(1f, 1f, infiniteRepeatable(HomeSceneMotion.scale(Symbol.X, reducedMotion)), label = "X pressure")
        val oScale = transition.animateFloat(if (reducedMotion) 1f else .97f, if (reducedMotion) 1f else .97f,
            infiniteRepeatable(HomeSceneMotion.scale(Symbol.O, reducedMotion)), label = "O pressure")
        // Read animation values in the layers, not composition or accessibility semantics.
        FormPiece(Symbol.X, Modifier.size(44.dp).graphicsLayer {
            alpha = xAlpha.value
            scaleX = xScale.value
            scaleY = scaleX
        })
        FormPiece(Symbol.O, Modifier.size(44.dp).graphicsLayer {
            alpha = oAlpha.value
            scaleX = oScale.value
            scaleY = scaleX
        })
    }
}
