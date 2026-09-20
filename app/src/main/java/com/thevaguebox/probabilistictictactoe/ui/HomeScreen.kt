package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thevaguebox.probabilistictictactoe.ui.components.FormChoice
import com.thevaguebox.probabilistictictactoe.ui.components.FormHomeScene
import com.thevaguebox.probabilistictictactoe.ui.components.FormPrimaryButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormSectionHeading
import com.thevaguebox.probabilistictictactoe.ui.components.FormTextAction
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme

@Composable
fun HomeScreen(
    onClassic: () -> Unit,
    onPicPacLocal: () -> Unit,
    onPicPacAi: (Difficulty) -> Unit,
    onHowTo: () -> Unit,
    onSettings: () -> Unit,
    onAiLab: () -> Unit,
) {
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.MEDIUM) }
    val colors = FormTheme.colors
    val largeText = LocalDensity.current.fontScale > 1.35f
    BoxWithConstraints(
        Modifier.fillMaxSize().background(colors.canvas).statusBarsPadding().navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val compact = maxHeight < 680.dp || largeText
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = FormTheme.spacing.screen, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                Text("Pic-Pac-Poe", Modifier.semantics { heading() }, style = MaterialTheme.typography.displayLarge)
                Text(
                    "Tic-tac-toe, except you don't choose your symbol.",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary,
                )
                FormHomeScene(Modifier.fillMaxWidth().padding(top = 12.dp).height(if (compact) 90.dp else 118.dp))
                Text(
                    "Draw a piece. Choose a square.",
                    Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 20.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                FormSectionHeading("Pick a game")
                ModeRow("3×3", "Classic", "Regular tic-tac-toe.", onClassic)
                HorizontalDivider(color = colors.borderSubtle)
                ModeRow("2P", "Pic-Pac Local", "Two players. One phone.", onPicPacLocal)
                HorizontalDivider(color = colors.borderSubtle)
                Column(Modifier.padding(top = 18.dp)) {
                    Text("Vs Computer", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Play Pic-Pac against the computer.",
                        Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    DifficultySelector(difficulty, { difficulty = it }, largeText)
                    Text(
                        difficulty.description,
                        Modifier.padding(top = 8.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    FormPrimaryButton("Play", { onPicPacAi(difficulty) }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(16.dp))
                if (largeText) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                        FormTextAction("How to play", onHowTo)
                        FormTextAction("AI Lab", onAiLab)
                        FormTextAction("Settings", onSettings)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        FormTextAction("How to play", onHowTo)
                        FormTextAction("AI Lab", onAiLab)
                        FormTextAction("Settings", onSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeRow(mark: String, title: String, subtitle: String, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val colors = FormTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(FormTheme.shapes.control)
            .background(if (pressed) colors.recess else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, colors.focus, FormTheme.shapes.control) else Modifier)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(mark, Modifier.width(46.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.labelLarge, color = FormTheme.colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = FormTheme.colors.textSecondary)
        }
        Text("›", Modifier.padding(start = 12.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium, color = FormTheme.colors.textSecondary)
    }
}

@Composable
private fun DifficultySelector(selected: Difficulty, onSelected: (Difficulty) -> Unit, largeText: Boolean) {
    val options = Difficulty.entries.filter(Difficulty::production)
    if (largeText) {
        Column(Modifier.fillMaxWidth().padding(top = 14.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FormChoice(option.title, selected == option, { onSelected(option) }, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FormChoice(option.title, selected == option, { onSelected(option) }, Modifier.weight(1f))
            }
        }
    }
}
