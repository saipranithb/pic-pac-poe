package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme

/** A noninteractive explanation: a shared bag, one drawn piece, then a square. */
@Composable
fun FormHomeScene(modifier: Modifier = Modifier) {
    val colors = FormTheme.colors
    Box(modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
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
            FormPiece(Symbol.X, Modifier.size(44.dp))
        }
    }
}
