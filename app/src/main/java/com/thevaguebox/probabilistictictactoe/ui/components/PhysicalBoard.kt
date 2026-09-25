package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.WinningLine
import com.thevaguebox.probabilistictictactoe.ui.TurnStage
import com.thevaguebox.probabilistictictactoe.ui.computerMoveDescription
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme
import com.thevaguebox.probabilistictictactoe.ui.theme.FormMotion

/** Decorative, front-facing resin piece. The owning cell/label supplies its semantics. */
@Composable
fun FormPiece(symbol: Symbol, modifier: Modifier = Modifier, progress: Float = 1f) {
    val colors = FormTheme.colors
    val face = if (symbol == Symbol.X) colors.x else colors.o
    val highlight = if (symbol == Symbol.X) colors.xHighlight else colors.oHighlight
    val edge = if (symbol == Symbol.X) colors.xEdge else colors.oEdge
    Box(
        modifier.graphicsLayer {
            alpha = progress
            scaleX = .96f + .04f * progress
            scaleY = scaleX
            translationY = -(1f - progress) * 3.dp.toPx()
        }.drawWithCache {
            val side = size.minDimension
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = side * .25f
            val stroke = side * .19f
            val path = Path().apply {
                if (symbol == Symbol.X) {
                    moveTo(cx - radius, cy - radius)
                    lineTo(cx + radius, cy + radius)
                    moveTo(cx + radius, cy - radius)
                    lineTo(cx - radius, cy + radius)
                } else {
                    addOval(androidx.compose.ui.geometry.Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                }
            }
            val faceBrush = Brush.linearGradient(listOf(highlight, face), Offset(cx, cy - radius), Offset(cx, cy + radius))
            val thickness = minOf(2.dp.toPx(), side * .035f)
            val style = Stroke(stroke, cap = StrokeCap.Round)
            onDrawBehind {
                translate(top = thickness + 2.dp.toPx()) {
                    drawPath(path, colors.shadow.copy(alpha = .12f), style = Stroke(stroke + 3.dp.toPx(), cap = StrokeCap.Round))
                }
                translate(top = thickness) { drawPath(path, edge, style = style) }
                drawPath(path, faceBrush, style = style)
            }
        },
    )
}

/** One board body, nine fixed coordinate wells. No cell is spatially animated. */
@Composable
fun PhysicalBoard(
    board: Board,
    enabled: Boolean,
    winningLine: WinningLine?,
    reducedMotion: Boolean,
    aiTargetCell: Int?,
    aiMoveSymbol: Symbol?,
    stage: TurnStage,
    onCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FormTheme.colors
    Box(
        modifier.aspectRatio(1f).testTag("game-board").drawWithCache {
            val corner = CornerRadius(24.dp.toPx())
            val body = Brush.verticalGradient(listOf(colors.surfaceRaised, colors.surface))
            onDrawBehind {
                drawRoundRect(colors.shadow.copy(alpha = .24f), Offset(0f, 3.dp.toPx()), size, corner)
                drawRoundRect(body, cornerRadius = corner)
                drawRoundRect(colors.border, Offset(.5.dp.toPx(), .5.dp.toPx()), Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), corner, style = Stroke(1.dp.toPx()))
                drawLine(colors.highlight.copy(alpha = .35f), Offset(24.dp.toPx(), 1.dp.toPx()), Offset(size.width - 24.dp.toPx(), 1.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
            }
        },
    ) {
        // This padding defines both visual and semantic cell geometry, including the win line.
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { row ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(3) { col ->
                            val index = row * 3 + col
                            val symbol = board[Cell.of(index)]
                            val aiFocus = aiTargetCell == index && stage in setOf(TurnStage.AI_TARGETING, TurnStage.AI_PLACING, TurnStage.AI_SETTLING)
                            RecessedWell(
                                symbol, enabled && symbol == null,
                                winningLine?.cells?.any { it.index == index } == true,
                                aiFocus, reducedMotion,
                                if (aiFocus) computerMoveDescription(index, aiMoveSymbol ?: symbol, stage) else null,
                                index, { onCell(index) }, Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
            // The line is static: a terminal AI board is already settled before the result opens.
            if (winningLine != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val gap = 6.dp.toPx()
                    val cell = (size.width - 2 * gap) / 3f
                    fun center(cellIndex: Int) = Offset(
                        (cellIndex % 3) * (cell + gap) + cell / 2,
                        (cellIndex / 3) * (cell + gap) + cell / 2,
                    )
                    val from = center(winningLine.first.index)
                    val to = center(winningLine.third.index)
                    drawLine(colors.recess, from, to, 5.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.text, from, to, 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun RecessedWell(
    symbol: Symbol?, enabled: Boolean, winning: Boolean, aiFocus: Boolean,
    reducedMotion: Boolean, moveDescription: String?, index: Int, onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = FormTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val progress by animateFloatAsState(
        if (symbol == null) 0f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else FormMotion.placementMillis, easing = FormMotion.easing), label = "piece contact",
    )
    Box(
        modifier.testTag("board-cell-$index")
            .semantics {
                role = Role.Button
                contentDescription = moveDescription ?: "Row ${index / 3 + 1}, column ${index % 3 + 1}, ${symbol?.name ?: "empty"}"
                if (moveDescription != null) liveRegion = LiveRegionMode.Polite
                if (!enabled) disabled()
            }
            .drawWithCache {
                val radius = CornerRadius(minOf(18.dp.toPx(), size.minDimension * .19f))
                val face = Brush.verticalGradient(listOf(colors.recess, colors.surface))
                val inset = 3.dp.toPx()
                onDrawBehind {
                    drawRoundRect(face, cornerRadius = radius)
                    drawRoundRect(colors.border, Offset(.5.dp.toPx(), .5.dp.toPx()), Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), radius, style = Stroke(1.dp.toPx()))
                    // A fixed inset edge supplies the well's depth. It never moves the hit area.
                    drawLine(colors.shadow.copy(alpha = if (pressed) .65f else .32f), Offset(radius.x, 2.dp.toPx()), Offset(size.width - radius.x, 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    if (pressed && enabled) drawRoundRect(colors.border.copy(alpha = .16f), cornerRadius = radius)
                    if (aiFocus || winning) {
                        drawRoundRect(colors.focus, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), radius, style = Stroke(2.dp.toPx()))
                    }
                    if (aiFocus) {
                        val inner = 7.dp.toPx()
                        drawRoundRect(colors.focus, Offset(inner, inner), Size(size.width - 2 * inner, size.height - 2 * inner), CornerRadius(maxOf(1f, radius.x - inner)), style = Stroke(1.dp.toPx()))
                    }
                    if (focused) {
                        drawRoundRect(colors.focus, cornerRadius = radius, style = Stroke(3.dp.toPx()))
                    }
                }
            }
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol != null) FormPiece(symbol, Modifier.fillMaxSize().padding(4.dp), progress)
    }
}
