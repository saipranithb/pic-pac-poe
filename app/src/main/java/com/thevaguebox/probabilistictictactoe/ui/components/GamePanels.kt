package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.GameMode
import com.thevaguebox.probabilistictictactoe.ui.GameUiState
import com.thevaguebox.probabilistictictactoe.ui.TurnStage
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme
import kotlin.math.roundToInt

@Composable
fun PlayerTurnHeader(state: GameUiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Actor names must remain whole at large font sizes, not split across narrow columns.
        if (LocalDensity.current.fontScale > 1.5f || (maxWidth < 320.dp && LocalDensity.current.fontScale > 1.2f)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Player.entries.forEach { PlayerIndicator(state, it, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Player.entries.forEach { PlayerIndicator(state, it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PlayerIndicator(state: GameUiState, player: Player, modifier: Modifier) {
    val colors = FormTheme.colors
    val active = state.displayedPlayer == player
    val actor = if (player == Player.ONE) colors.playerOne else colors.playerTwo
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(actor, CircleShape))
            Text(state.actorLabel(player), Modifier.padding(start = 8.dp), color = if (active) colors.text else colors.textSecondary, style = MaterialTheme.typography.titleMedium)
        }
        Text(if (active) state.turnLabel(player) else "Waiting", Modifier.padding(top = 3.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = if (active) actor else colors.textSecondary)
        Box(Modifier.fillMaxWidth().height(if (active) 3.dp else 1.dp).background(if (active) actor else colors.borderSubtle))
    }
}

@Composable
fun TurnInstructions(state: GameUiState, modifier: Modifier = Modifier) {
    val colors = FormTheme.colors
    val symbol = state.heldSymbol ?: when {
        state.stage == TurnStage.AI_PLACING || state.stage == TurnStage.AI_SETTLING -> state.aiMoveSymbol
        state.mode == GameMode.CLASSIC_LOCAL && state.stage == TurnStage.PLAYING -> if (state.activePlayer == Player.ONE) Symbol.X else Symbol.O
        else -> null
    }
    val target = state.aiTargetCell
    val title = when (state.stage) {
        TurnStage.PLAYING -> symbol?.let { "Place ${it.name}" } ?: state.turnLabel()
        TurnStage.AI_THINKING -> "Computer is thinking"
        TurnStage.AI_TARGETING -> "Square selected"
        TurnStage.AI_PLACING -> "Placing ${state.aiMoveSymbol?.name.orEmpty()}"
        TurnStage.AI_SETTLING -> "Move placed"
        TurnStage.TERMINAL -> "Game over"
        TurnStage.REVEALING -> state.drawLabel() ?: "Drawing a piece"
        TurnStage.HANDOFF -> "Pass to ${state.activePlayer.label}"
        TurnStage.TURN_START -> state.turnLabel()
    }
    val detail = when (state.stage) {
        TurnStage.PLAYING -> "Choose any empty square."
        TurnStage.TURN_START -> "A piece comes from the shared bag."
        TurnStage.REVEALING -> "Draw first. Then choose a square."
        TurnStage.AI_THINKING -> "Choosing where to place ${symbol?.name.orEmpty()}."
        TurnStage.AI_TARGETING -> target?.let { "Computer chose row ${it / 3 + 1}, column ${it % 3 + 1}." } ?: "Computer has chosen a square."
        TurnStage.AI_PLACING -> "Computer is placing its piece."
        TurnStage.AI_SETTLING -> "Computer's move is on the board."
        TurnStage.TERMINAL -> "The final position."
        TurnStage.HANDOFF -> "Pass the phone before revealing."
    }
    Row(modifier.fillMaxWidth().testTag("turn-status"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.text)
            Text(detail, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
        if (symbol != null && state.stage != TurnStage.TERMINAL) {
            Spacer(Modifier.width(12.dp))
            FormSurface(Modifier.size(62.dp).semantics {
                contentDescription = if (state.stage == TurnStage.AI_PLACING || state.stage == TurnStage.AI_SETTLING) "Placed ${symbol.name}" else "Piece in hand: ${symbol.name}"
            }, recessed = true, elevated = false) { FormPiece(symbol, Modifier.fillMaxSize().padding(4.dp)) }
        }
    }
}

@Composable
fun ProbabilityTray(state: GameUiState, modifier: Modifier = Modifier) {
    val pic = state.picPac ?: return
    val colors = FormTheme.colors
    FormSurface(modifier = modifier.fillMaxWidth(), elevated = false) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bag", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("Next draw", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp), color = colors.borderSubtle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProbabilityItem(Symbol.X, pic.remainingX, pic.nextXProbability, Modifier.weight(1f))
                ProbabilityItem(Symbol.O, pic.remainingO, pic.nextOProbability, Modifier.weight(1f))
            }
            Text(if (state.heldSymbol != null) "${pic.hiddenTotal} pieces remain · held piece excluded" else "${pic.hiddenTotal} pieces remain in the shared bag", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
private fun ProbabilityItem(symbol: Symbol, count: Int, probability: Double, modifier: Modifier) {
    val colors = FormTheme.colors
    val percent = (probability * 100).roundToInt()
    Column(modifier.clearAndSetSemantics { contentDescription = "${symbol.name}, $count remaining, $percent percent next draw" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormPiece(symbol, Modifier.size(28.dp))
            Text("${symbol.name} ×$count", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.titleMedium)
        }
        Text("$percent%", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = if (symbol == Symbol.X) colors.x else colors.o)
    }
}
