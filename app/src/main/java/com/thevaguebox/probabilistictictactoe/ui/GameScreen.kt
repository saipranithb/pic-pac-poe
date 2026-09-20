package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.thevaguebox.picpac.core.GameOutcome
import com.thevaguebox.picpac.core.Player
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.ui.components.FormBackButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormPiece
import com.thevaguebox.probabilistictictactoe.ui.components.FormPrimaryButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormSecondaryButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormSurface
import com.thevaguebox.probabilistictictactoe.ui.components.PhysicalBoard
import com.thevaguebox.probabilistictictactoe.ui.components.PlayerTurnHeader
import com.thevaguebox.probabilistictictactoe.ui.components.ProbabilityTray
import com.thevaguebox.probabilistictictactoe.ui.components.TurnInstructions
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme
import kotlinx.coroutines.delay

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
    val colors = FormTheme.colors
    Box(Modifier.fillMaxSize().background(colors.canvas).testTag("game-root").statusBarsPadding().navigationBarsPadding()) {
        PresentationClock(state, reducedMotion, onPresentationFinished)
        if (state.stage == TurnStage.HANDOFF) {
            // Private contents are absent, not merely dimmed or click-blocked.
            HandoffPresentation(state.activePlayer, onReady, onHome)
        } else {
            val modal = state.stage == TurnStage.REVEALING || state.stage == TurnStage.TERMINAL
            BoxWithConstraints(Modifier.fillMaxSize().then(if (modal) Modifier.clearAndSetSemantics {} else Modifier)) {
                val wide = maxWidth >= 760.dp && LocalDensity.current.fontScale < 1.6f
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = if (wide) 1000.dp else 500.dp).fillMaxWidth()) {
                        GameHeader(state, onHome)
                        Spacer(Modifier.height(16.dp))
                        PlayerTurnHeader(state)
                        Spacer(Modifier.height(20.dp))
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                                BoardArea(state, reducedMotion, onCell, Modifier.weight(1f))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    TurnInstructions(state)
                                    if (state.isPicPac) ProbabilityTray(state)
                                }
                            }
                        } else {
                            TurnInstructions(state)
                            Spacer(Modifier.height(20.dp))
                            BoardArea(state, reducedMotion, onCell, Modifier.fillMaxWidth())
                            if (state.isPicPac) {
                                Spacer(Modifier.height(24.dp))
                                ProbabilityTray(state)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
            when (state.stage) {
                TurnStage.REVEALING -> RevealPresentation(state, onHome)
                TurnStage.TERMINAL -> ResultPresentation(state, onRematch, onHome)
                else -> Unit
            }
        }
    }
}

@Composable
private fun GameHeader(state: GameUiState, onHome: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FormBackButton(onClick = onHome)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(when (state.mode) {
                GameMode.CLASSIC_LOCAL -> "Classic"
                GameMode.PIC_PAC_LOCAL -> "Pic-Pac Local"
                GameMode.PIC_PAC_AI -> "Vs Computer"
                null -> "Pic-Pac-Poe"
            }, style = MaterialTheme.typography.titleLarge)
            if (state.mode == GameMode.PIC_PAC_AI) Text(state.difficulty.title, style = MaterialTheme.typography.labelMedium, color = FormTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun BoardArea(state: GameUiState, reducedMotion: Boolean, onCell: (Int) -> Unit, modifier: Modifier) {
    PhysicalBoard(state.board,
        state.stage == TurnStage.PLAYING && (state.mode != GameMode.PIC_PAC_AI || state.activePlayer == Player.ONE),
        state.winningLine, reducedMotion, state.aiTargetCell, state.aiMoveSymbol, state.stage, onCell, modifier)
}

@Composable
private fun HandoffPresentation(player: Player, onReady: () -> Unit, onHome: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Pic-Pac Local", style = MaterialTheme.typography.labelLarge, color = FormTheme.colors.textSecondary)
            Box(Modifier.size(56.dp).background(if (player == Player.ONE) FormTheme.colors.playerOne else FormTheme.colors.playerTwo, FormTheme.shapes.control), contentAlignment = Alignment.Center) {
                Text(if (player == Player.ONE) "1" else "2", color = FormTheme.colors.canvas, style = MaterialTheme.typography.headlineMedium)
            }
            Text("${player.label}, you're up.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
            Text("Pass the phone, then tap when they're ready.", style = MaterialTheme.typography.bodyLarge, color = FormTheme.colors.textSecondary, textAlign = TextAlign.Center)
            FormPrimaryButton("Ready", onReady, Modifier.fillMaxWidth())
            FormSecondaryButton("Home", onHome, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun RevealPresentation(state: GameUiState, onHome: () -> Unit) {
    val held = state.heldSymbol ?: return
    val announcement = state.drawLabel() ?: return
    PresentationDialog(onHome) {
        Text(state.actorLabel(state.activePlayer), style = MaterialTheme.typography.labelLarge, color = if (state.activePlayer == Player.ONE) FormTheme.colors.playerOne else FormTheme.colors.playerTwo)
        Text(announcement, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        FormSurface(Modifier.size(132.dp), recessed = true, elevated = false) { FormPiece(held, Modifier.fillMaxSize()) }
        Text(if (state.mode == GameMode.PIC_PAC_AI && state.activePlayer == Player.TWO) "Computer will choose a square." else "One piece. Your choice of square.", style = MaterialTheme.typography.bodyMedium, color = FormTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ResultPresentation(state: GameUiState, onRematch: () -> Unit, onHome: () -> Unit) {
    val outcome = state.outcome ?: return
    PresentationDialog(onHome) {
        Text(state.resultLabel() ?: "Draw", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
        Text(if (outcome is GameOutcome.Win) "Three ${outcome.symbol.name}s. One completed line." else "No line this time.", style = MaterialTheme.typography.bodyLarge, color = FormTheme.colors.textSecondary, textAlign = TextAlign.Center)
        // Static evidence of the final position, not another set of controls.
        Box(Modifier.size(188.dp).clearAndSetSemantics {
            contentDescription = "Final board. " + (0..8).joinToString(". ") { index -> "Row ${index / 3 + 1}, column ${index % 3 + 1}, ${state.board[com.thevaguebox.picpac.core.Cell.of(index)]?.name ?: "empty"}" }
        }) {
            PhysicalBoard(state.board, false, state.winningLine, true, null, null, TurnStage.TERMINAL, {}, Modifier.fillMaxSize())
        }
        FormPrimaryButton("Rematch", onRematch, Modifier.fillMaxWidth())
        FormSecondaryButton("Home", onHome, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PresentationDialog(onHome: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onHome, properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        // The presentation stage owns visibility; platform window animations must not add
        // another delay or keep fading after the reduced-motion reveal has completed.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setWindowAnimations(0) }
        FormSurface(modifier = Modifier.padding(24.dp).widthIn(max = 400.dp).fillMaxWidth()) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun PresentationClock(state: GameUiState, reducedMotion: Boolean, onFinished: (Long) -> Unit) {
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
    return if (stage == TurnStage.AI_TARGETING) "Computer selected row $row, column $column"
    else symbol?.let { "Computer placed ${it.name} in row $row, column $column" }
}
