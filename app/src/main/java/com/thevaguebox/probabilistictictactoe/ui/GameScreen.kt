package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thevaguebox.picpac.core.Board
import com.thevaguebox.picpac.core.Cell
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.picpac.core.WinningLine
import com.thevaguebox.probabilistictictactoe.ui.theme.ElectricX
import com.thevaguebox.probabilistictictactoe.ui.theme.PlayerOne
import com.thevaguebox.probabilistictictactoe.ui.theme.PlayerTwo
import com.thevaguebox.probabilistictictactoe.ui.theme.SolarO
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun GameScreen(
    state: GameUiState,
    reducedMotion: Boolean,
    onHome: () -> Unit,
    onReady: () -> Unit,
    onPresentationFinished: (Long) -> Unit,
    onCell: (Int) -> Unit,
    onRematch: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        PresentationClock(state, reducedMotion, onPresentationFinished)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = if (wide) 32.dp else 18.dp, vertical = 12.dp),
            ) {
                GameHeader(state, onHome)
                Spacer(Modifier.height(12.dp))
                PlayerStrip(state)
                Spacer(Modifier.height(12.dp))
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BoardArea(state, reducedMotion, onCell, Modifier.weight(1.1f))
                        Column(Modifier.weight(.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (state.isPicPac) BagHud(state)
                            TurnBanner(state)
                        }
                    }
                } else {
                    if (state.isPicPac) {
                        BagHud(state)
                        Spacer(Modifier.height(12.dp))
                    }
                    TurnBanner(state)
                    Spacer(Modifier.height(14.dp))
                    BoardArea(state, reducedMotion, onCell, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        when (state.stage) {
            TurnStage.HANDOFF -> HandoffOverlay(state.activePlayer, onReady)
            TurnStage.REVEALING -> RevealOverlay(state)
            TurnStage.TERMINAL -> ResultOverlay(state, onRematch, onHome)
            else -> Unit
        }
    }
}

@Composable
private fun GameHeader(state: GameUiState, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(role = Role.Button, onClick = onHome),
            contentAlignment = Alignment.Center,
        ) {
            Text("←", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            when (state.mode) {
                GameMode.CLASSIC_LOCAL -> "Classic"
                GameMode.PIC_PAC_LOCAL -> "Pic-Pac Local"
                GameMode.PIC_PAC_AI -> "Vs Computer · ${state.difficulty.title}"
                null -> "Pic-Pac-Poe"
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun PlayerStrip(state: GameUiState) {
    val active = state.displayedPlayer
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        PlayerStatus(state, Player.ONE, active == Player.ONE, PlayerOne, Modifier.weight(1f))
        PlayerStatus(state, Player.TWO, active == Player.TWO, PlayerTwo, Modifier.weight(1f))
    }
}

@Composable
private fun PlayerStatus(state: GameUiState, player: Player, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.alpha(if (active) 1f else .46f).padding(horizontal = 4.dp, vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(state.actorLabel(player), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        Text(if (active) state.turnLabel(player) else " ", style = MaterialTheme.typography.labelMedium, color = color)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(if (active) color else Color.Transparent))
    }
}

@Composable
private fun BagHud(state: GameUiState) {
    val pic = state.picPac ?: return
    val held = state.heldSymbol
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bag", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (held == null) {
                            "Next draw"
                        } else if (state.mode == GameMode.PIC_PAC_AI) {
                            "${state.actorLabel(state.activePlayer)} drew ${held.name} · next draw"
                        } else {
                            "Drew ${held.name} · next draw"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (held != null) MiniMark(held)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 9.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = .22f),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BagItem(Symbol.X, pic.remainingX, pic.nextXProbability, Modifier.weight(1f))
                Box(Modifier.width(1.dp).height(30.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .25f)))
                BagItem(Symbol.O, pic.remainingO, pic.nextOProbability, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BagItem(symbol: Symbol, count: Int, probability: Double, modifier: Modifier) {
    val color = if (symbol == Symbol.X) ElectricX else SolarO
    Row(
        modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(symbol.name, style = MaterialTheme.typography.titleLarge, color = color)
        Text(" ×$count", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text("${(probability * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun TurnBanner(state: GameUiState) {
    val color = if (state.displayedPlayer == Player.ONE) PlayerOne else PlayerTwo
    val text = if (state.mode == GameMode.PIC_PAC_AI) {
        when (state.stage) {
            TurnStage.TERMINAL -> "Game over"
            TurnStage.PLAYING -> state.heldSymbol?.let { "Place ${it.name}" } ?: "Your turn"
            else -> state.turnLabel()
        }
    } else {
        when (state.stage) {
            TurnStage.HANDOFF -> "Pass to ${state.activePlayer.label}"
            TurnStage.REVEALING -> "Drawing…"
            TurnStage.TERMINAL -> "Game over"
            TurnStage.PLAYING -> state.heldSymbol?.let { "Place ${it.name}" }
                ?: "${state.activePlayer.label}, pick a square"
            else -> state.turnLabel()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(25.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text, Modifier.padding(start = 11.dp), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun BoardArea(state: GameUiState, reducedMotion: Boolean, onCell: (Int) -> Unit, modifier: Modifier) {
    val humanEnabled = state.stage == TurnStage.PLAYING &&
        (state.mode != GameMode.PIC_PAC_AI || state.activePlayer == Player.ONE)
    BoardGrid(
        board = state.board,
        enabled = humanEnabled,
        winningLine = state.winningLine,
        reducedMotion = reducedMotion,
        aiTargetCell = state.aiTargetCell,
        aiMoveSymbol = state.aiMoveSymbol,
        stage = state.stage,
        onCell = onCell,
        modifier = modifier,
    )
}

@Composable
private fun BoardGrid(
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
    val winProgress by animateFloatAsState(
        targetValue = if (winningLine == null) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(650),
        label = "winning line",
    )
    Box(
        modifier = modifier.aspectRatio(1f).padding(4.dp),
    ) {
        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = .48f)
        Canvas(Modifier.fillMaxSize()) {
            for (index in 1..2) {
                val x = size.width * index / 3f
                val y = size.height * index / 3f
                drawLine(gridColor, Offset(x, 8.dp.toPx()), Offset(x, size.height - 8.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
                drawLine(gridColor, Offset(8.dp.toPx(), y), Offset(size.width - 8.dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        Column(Modifier.fillMaxSize()) {
            repeat(3) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val symbol = board[Cell.of(index)]
                        val winning = winningLine?.cells?.any { it.index == index } == true
                        val computerFocus = aiTargetCell == index && stage in setOf(
                            TurnStage.AI_TARGETING,
                            TurnStage.AI_PLACING,
                            TurnStage.AI_SETTLING,
                        )
                        val computerMoveDescription = if (computerFocus) {
                            computerMoveDescription(index, aiMoveSymbol ?: symbol, stage)
                        } else null
                        BoardCell(
                            symbol = symbol,
                            enabled = enabled && symbol == null,
                            winning = winning,
                            reducedMotion = reducedMotion,
                            onClick = { onCell(index) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            index = index,
                            computerFocus = computerFocus,
                            computerMoveDescription = computerMoveDescription,
                        )
                    }
                }
            }
        }
        if (winningLine != null) {
            val accent = if ((board[winningLine.first]) == Symbol.X) ElectricX else SolarO
            Canvas(Modifier.fillMaxSize()) {
                fun center(cell: Cell): Offset {
                    val col = cell.index % 3
                    val row = cell.index / 3
                    return Offset((col + .5f) * size.width / 3f, (row + .5f) * size.height / 3f)
                }
                val start = center(winningLine.first)
                val end = center(winningLine.third)
                drawLine(accent, start, start + (end - start) * winProgress, 9.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun BoardCell(
    symbol: Symbol?,
    enabled: Boolean,
    winning: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    index: Int,
    computerFocus: Boolean,
    computerMoveDescription: String?,
) {
    val progress by animateFloatAsState(
        targetValue = if (symbol == null) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(280),
        label = "piece",
    )
    val semantics = Modifier.semantics {
        role = Role.Button
        contentDescription = computerMoveDescription ?: run {
            val row = index / 3 + 1
            val column = index % 3 + 1
            "Row $row, column $column, ${symbol?.name ?: "empty"}"
        }
        if (computerMoveDescription != null) liveRegion = LiveRegionMode.Polite
        if (!enabled) disabled()
    }
    val focusScale by animateFloatAsState(
        targetValue = if (computerFocus && symbol == null) .92f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(220),
        label = "computer cell focus",
    )
    Box(
        modifier = modifier
            .then(semantics)
            .padding(7.dp)
            .graphicsLayer { scaleX = focusScale; scaleY = focusScale }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    winning -> MaterialTheme.colorScheme.primary.copy(alpha = .13f)
                    computerFocus -> PlayerTwo.copy(alpha = if (symbol == null) .16f else .09f)
                    else -> Color.Transparent
                },
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol != null) Mark(symbol, progress, Modifier.fillMaxSize().padding(10.dp))
    }
}

@Composable
private fun Mark(symbol: Symbol, progress: Float, modifier: Modifier = Modifier) {
    val color = if (symbol == Symbol.X) ElectricX else SolarO
    Canvas(modifier.alpha(.35f + .65f * progress)) {
        val inset = size.minDimension * .16f
        val stroke = size.minDimension * .1f
        if (symbol == Symbol.X) {
            val firstEnd = Offset(inset + (size.width - 2 * inset) * progress, inset + (size.height - 2 * inset) * progress)
            val secondEnd = Offset(size.width - inset - (size.width - 2 * inset) * progress, inset + (size.height - 2 * inset) * progress)
            drawLine(color, Offset(inset, inset), firstEnd, stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width - inset, inset), secondEnd, stroke, StrokeCap.Round)
        } else {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun MiniMark(symbol: Symbol) {
    Box(Modifier.size(42.dp).padding(7.dp)) { Mark(symbol, 1f, Modifier.fillMaxSize()) }
}

@Composable
private fun HandoffOverlay(player: Player, onReady: () -> Unit) {
    OverlayScrim {
        Text("${player.label}, you're up.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(
            "Pass the phone, then tap when they're ready.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onReady, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Ready") }
    }
}

@Composable
private fun RevealOverlay(state: GameUiState) {
    val held = state.heldSymbol ?: return
    val announcement = state.drawLabel() ?: return
    OverlayScrim {
        Text(
            announcement,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            style = MaterialTheme.typography.titleLarge,
        )
        Box(Modifier.size(112.dp).padding(10.dp)) { Mark(held, 1f, Modifier.fillMaxSize()) }
    }
}

@Composable
private fun ResultOverlay(state: GameUiState, onRematch: () -> Unit, onHome: () -> Unit) {
    val outcome = state.outcome ?: return
    OverlayScrim {
        when (outcome) {
            GameOutcome.Draw -> {
                Text("Draw", style = MaterialTheme.typography.headlineLarge)
                Text("No line this time.", style = MaterialTheme.typography.bodyLarge)
            }
            is GameOutcome.Win -> {
                Text(
                    state.resultLabel() ?: return@OverlayScrim,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("with ", style = MaterialTheme.typography.bodyLarge)
                    MiniMark(outcome.symbol)
                }
            }
        }
        Button(onClick = onRematch, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Rematch") }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) { Text("Home") }
    }
}

@Composable
private fun PresentationClock(
    state: GameUiState,
    reducedMotion: Boolean,
    onFinished: (Long) -> Unit,
) {
    val duration = presentationDelayMillis(state.stage, reducedMotion) ?: return
    LaunchedEffect(state.presentationId, state.stage, reducedMotion) {
        delay(duration)
        onFinished(state.presentationId)
    }
}

internal fun presentationDelayMillis(stage: TurnStage, reducedMotion: Boolean): Long? = when (stage) {
    TurnStage.TURN_START -> if (reducedMotion) 160L else 300L
    TurnStage.REVEALING -> if (reducedMotion) 500L else 650L
    TurnStage.AI_TARGETING -> if (reducedMotion) 160L else 280L
    TurnStage.AI_PLACING -> if (reducedMotion) 180L else 340L
    TurnStage.AI_SETTLING -> if (reducedMotion) 320L else 480L
    else -> null
}

internal fun computerMoveDescription(index: Int, symbol: Symbol?, stage: TurnStage): String? {
    if (stage !in setOf(TurnStage.AI_TARGETING, TurnStage.AI_PLACING, TurnStage.AI_SETTLING)) return null
    val row = index / 3 + 1
    val column = index % 3 + 1
    return if (stage == TurnStage.AI_TARGETING) {
        "Computer selected row $row, column $column"
    } else {
        symbol?.let { "Computer placed ${it.name} in row $row, column $column" }
    }
}

@Composable
private fun OverlayScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .64f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth().widthIn(max = 440.dp)) {
            Column(
                Modifier.padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
