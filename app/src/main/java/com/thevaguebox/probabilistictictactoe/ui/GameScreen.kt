package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
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
    onRevealFinished: () -> Unit,
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
                PlayerStrip(state.activePlayer)
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
            TurnStage.REVEALING -> RevealOverlay(state, reducedMotion, onRevealFinished)
            TurnStage.TERMINAL -> ResultOverlay(state, onRematch, onHome)
            else -> Unit
        }
    }
}

@Composable
private fun GameHeader(state: GameUiState, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onHome),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
        ) { Text("←", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.titleLarge) }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
            Text(
                when (state.mode) {
                    GameMode.CLASSIC_LOCAL -> "CLASSIC"
                    GameMode.PIC_PAC_LOCAL -> "PIC-PAC LOCAL"
                    GameMode.PIC_PAC_AI -> "PIC-PAC • ${state.difficulty.title.uppercase()}"
                    null -> "PIC-PAC-POE"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Match ${state.classic?.revision ?: state.picPac?.revision ?: 1}", style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), shape = CircleShape) {
            Text("OFFLINE", Modifier.padding(horizontal = 11.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlayerStrip(active: Player) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        PlayerPill(Player.ONE, active == Player.ONE, PlayerOne, Modifier.weight(1f))
        PlayerPill(Player.TWO, active == Player.TWO, PlayerTwo, Modifier.weight(1f))
    }
}

@Composable
private fun PlayerPill(player: Player, active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(if (active) 1.5.dp else 1.dp, color.copy(alpha = if (active) .8f else .18f), RoundedCornerShape(17.dp)),
        color = color.copy(alpha = if (active) .15f else .05f),
        shape = RoundedCornerShape(17.dp),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (active) color else color.copy(alpha = .25f), CircleShape))
            Text(player.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
            if (active) Text("  TURN", style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun BagHud(state: GameUiState) {
    val pic = state.picPac ?: return
    val held = state.heldSymbol
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("HIDDEN BAG", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (held == null) "Next draw" else "Held ${held.name} • next draw odds",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (held != null) MiniMark(held)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangemen
                .spacedBy(10.dp)) {
                BagItem(Symbol.X, pic.remainingX, pic.nextXProbability, Modifier.weight(1f))
                BagItem(Symbol.O, pic.remainingO, pic.nextOProbability, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BagItem(symbol: Symbol, count: Int, probability: Double, modifier: Modifier) {
    val color = if (symbol == Symbol.X) ElectricX else SolarO
    Row(
        modifier.background(color.copy(alpha = .1f), RoundedCornerShape(14.dp)).padding(11.dp),
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
    val color = if (state.activePlayer == Player.ONE) PlayerOne else PlayerTwo
    val text = when (state.stage) {
        TurnStage.HANDOFF -> "Pass to ${state.activePlayer.label}"
        TurnStage.REVEALING -> "Drawing from the hidden bag…"
        TurnStage.AI_THINKING -> "Opponent is weighing the odds…"
        TurnStage.TERMINAL -> "Match complete"
        TurnStage.PLAYING -> state.heldSymbol?.let { "Place your ${it.name}" }
            ?: "${state.activePlayer.label}: choose a cell"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, color.copy(alpha = .35f), RoundedCornerShape(18.dp)),
        color = color.copy(alpha = .09f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
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
    onCell: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val winProgress by animateFloatAsState(
        targetValue = if (winningLine == null) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(650),
        label = "winning line",
    )
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f))
            .padding(8.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    repeat(3) { column ->
                        val index = row * 3 + column
                        val symbol = board[Cell.of(index)]
                        val winning = winningLine?.cells?.any { it.index == index } == true
                        BoardCell(
                            symbol = symbol,
                            enabled = enabled && symbol == null,
                            winning = winning,
                            reducedMotion = reducedMotion,
                            onClick = { onCell(index) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            index = index,
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
) {
    val progress by animateFloatAsState(
        targetValue = if (symbol == null) 0f else 1f,
        animationSpec = if (reducedMotion) tween(0) else tween(280),
        label = "piece",
    )
    val semantics = Modifier.semantics {
        role = Role.Button
        contentDescription = "Cell ${index + 1}, ${symbol?.name ?: "empty"}"
        if (!enabled) disabled()
    }
    Surface(
        modifier = modifier.then(semantics).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        color = if (winning) MaterialTheme.colorScheme.primary.copy(alpha = .16f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (symbol == null) 1.dp else 4.dp,
    ) {
        Box(Modifier.fillMaxSize().padding(17.dp), contentAlignment = Alignment.Center) {
            if (symbol != null) Mark(symbol, progress, Modifier.fillMaxSize())
        }
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
    Box(
        Modifier.size(42.dp).background((if (symbol == Symbol.X) ElectricX else SolarO).copy(alpha = .12f), CircleShape).padding(9.dp),
    ) { Mark(symbol, 1f, Modifier.fillMaxSize()) }
}

@Composable
private fun HandoffOverlay(player: Player, onReady: () -> Unit) {
    OverlayScrim {
        Text("PASS THE DEVICE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
        Text("${player.label}'s turn", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text(
            "Only reveal when the next player is ready. The draw is sampled before the animation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onReady, modifier = Modifier.fillMaxWidth()) { Text("Ready • reveal my piece") }
    }
}

@Composable
private fun RevealOverlay(state: GameUiState, reducedMotion: Boolean, onFinished: () -> Unit) {
    val held = state.heldSymbol ?: return
    LaunchedEffect(state.picPac?.revision, state.picPac?.board?.occupiedCount, held) {
        delay(if (reducedMotion) 80 else 520)
        onFinished()
    }
    OverlayScrim {
        Text("YOU DREW", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.size(112.dp).padding(10.dp)) { Mark(held, 1f, Modifier.fillMaxSize()) }
        Text("Plan the placement. The hidden counts already changed.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResultOverlay(state: GameUiState, onRematch: () -> Unit, onHome: () -> Unit) {
    val outcome = state.outcome ?: return
    OverlayScrim {
        when (outcome) {
            GameOutcome.Draw -> {
                Text("BALANCED TO THE END", style = MaterialTheme.typography.labelMedium, color = SolarO)
                Text("Draw game", style = MaterialTheme.typography.headlineLarge)
                Text("Nine placements. One hidden piece left.", style = MaterialTheme.typography.bodyLarge)
            }
            is GameOutcome.Win -> {
                Text("LINE COMPLETE", style = MaterialTheme.typography.labelMedium, color = if (outcome.symbol == Symbol.X) ElectricX else SolarO)
                Text("${outcome.player.label} wins", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("with ", style = MaterialTheme.typography.bodyLarge)
                    MiniMark(outcome.symbol)
                }
            }
        }
        Button(onClick = onRematch, modifier = Modifier.fillMaxWidth()) { Text("Rematch • starter alternates") }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Home") }
    }
}

@Composable
private fun OverlayScrim(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .64f)).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = RoundedCornerShape(30.dp), tonalElevation = 12.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}
