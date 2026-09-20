package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.thevaguebox.picpac.core.Symbol
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.components.FormBackButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormChoice
import com.thevaguebox.probabilistictictactoe.ui.components.FormPiece
import com.thevaguebox.probabilistictictactoe.ui.components.FormPrimaryButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormSecondaryButton
import com.thevaguebox.probabilistictictactoe.ui.components.FormSectionHeading
import com.thevaguebox.probabilistictictactoe.ui.components.FormSurface
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    InfoPage(title = "How to play", onBack = onBack) {
        InfoHeading("Pic-Pac")
        Text(
            "There are five Xs and five Os in the bag.",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = FormTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        listOf(
            "Draw one.",
            "See what you got.",
            "Put it in any empty square.",
            "Make three Xs or three Os in a row.",
        ).forEachIndexed { index, text -> RuleStep(index + 1, text) }
        Spacer(Modifier.height(24.dp))
        InfoHeading("You're not X. You're not O.")
        Text(
            "Either player can place either symbol. You win when the piece you place finishes the line.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        ProbabilityExample()
        Text(
            "The odds change as pieces leave the bag. Tapping faster doesn't change the draw.",
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FormTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = FormTheme.colors.borderSubtle)
        Spacer(Modifier.height(24.dp))
        InfoHeading("Classic")
        Text(
            "Regular tic-tac-toe. Player 1 is X; Player 2 is O.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = FormTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun RuleStep(number: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        Text("$number.", Modifier.width(34.dp), style = MaterialTheme.typography.titleLarge, color = FormTheme.colors.textSecondary)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProbabilityExample() {
    val largeText = LocalDensity.current.fontScale > 1.35f
    FormSurface(Modifier.fillMaxWidth().padding(top = 22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("After drawing X", style = MaterialTheme.typography.titleMedium)
            Text("Next draw · 9 pieces in the bag", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = FormTheme.colors.textSecondary)
            if (largeText) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExampleOdds(Symbol.X, 4, "44%", Modifier.fillMaxWidth())
                    ExampleOdds(Symbol.O, 5, "56%", Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ExampleOdds(Symbol.X, 4, "44%", Modifier.weight(1f))
                    ExampleOdds(Symbol.O, 5, "56%", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ExampleOdds(symbol: Symbol, count: Int, odds: String, modifier: Modifier = Modifier) {
    val color = if (symbol == Symbol.X) FormTheme.colors.x else FormTheme.colors.o
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormPiece(symbol, Modifier.size(28.dp).clearAndSetSemantics {})
            Text("${symbol.name} ×$count", style = MaterialTheme.typography.titleMedium)
        }
        Text(odds, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.headlineLarge, color = color)
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSound: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onTheme: (ThemePreference) -> Unit,
) {
    InfoPage(title = "Settings", onBack = onBack) {
        FormSectionHeading("Feedback")
        SettingToggle("Sound", "Small tones for draws, moves, and results.", settings.sound, onSound)
        HorizontalDivider(color = FormTheme.colors.borderSubtle)
        SettingToggle("Haptics", "Vibrate on draws, moves, and results.", settings.haptics, onHaptics)
        HorizontalDivider(color = FormTheme.colors.borderSubtle)
        SettingToggle("Reduced motion", "Keep every turn readable without extra movement.", settings.reducedMotion, onReducedMotion)
        Spacer(Modifier.height(28.dp))
        FormSectionHeading("Theme")
        ThemeSelector(settings.theme, onTheme)
        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = FormTheme.colors.borderSubtle)
        Text(
            "Everything stays on this device. No account, ads, analytics, or Internet permission.",
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FormTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val colors = FormTheme.colors
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .clip(FormTheme.shapes.control)
        .background(if (pressed) colors.recess else Color.Transparent)
        .then(if (focused) Modifier.border(2.dp, colors.focus, FormTheme.shapes.control) else Modifier)
        .toggleable(value = checked, interactionSource = interactions, indication = null, role = Role.Switch, onValueChange = onChecked)
        .padding(vertical = 16.dp)
    if (LocalDensity.current.fontScale > 1.35f) {
        Column(rowModifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
                SettingSwitch(checked)
            }
            Text(description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
            SettingSwitch(checked)
        }
    }
}

@Composable
private fun SettingSwitch(checked: Boolean) {
    val colors = FormTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = null,
        modifier = Modifier.clearAndSetSemantics {},
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAction,
            checkedTrackColor = colors.action,
            checkedBorderColor = colors.action,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.recess,
            uncheckedBorderColor = colors.border,
        ),
    )
}

@Composable
private fun ThemeSelector(selected: ThemePreference, onSelected: (ThemePreference) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.35f
    if (largeText) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { theme ->
                FormChoice(theme.title(), selected == theme, { onSelected(theme) }, Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { theme ->
                FormChoice(theme.title(), selected == theme, { onSelected(theme) }, Modifier.weight(1f))
            }
        }
    }
}

private fun ThemePreference.title(): String = name.lowercase().replaceFirstChar(Char::uppercase)

@Composable
fun AiLabScreen(onBack: () -> Unit, onMcts: () -> Unit, onRl: () -> Unit) {
    InfoPage(title = "AI Lab", subtitle = "The other opponents live here.", onBack = onBack) {
        Text(
            "Each opponent sees the board, the piece in hand, and the bag counts. None can peek at the next draw.",
            style = MaterialTheme.typography.bodyLarge,
            color = FormTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        FormSectionHeading("How they play")
        AlgorithmRow("Random", "Chooses any legal square.")
        AlgorithmRow("Heuristic", "Checks wins, threats, and the changing bag.")
        AlgorithmRow("Expectiminimax", "Medium searches four plies. Hard solves the full game.")
        Spacer(Modifier.height(20.dp))
        FormSectionHeading("Try an experiment")
        AlgorithmRow("MCTS", "Samples decisions and draws on a fixed budget.")
        FormPrimaryButton("Play MCTS", onMcts, Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(
            "2,000 simulations per move.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FormTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        AlgorithmRow("Q-learning", "A small table learned from seeded self-play.")
        FormSecondaryButton("Play Q-learning", onRl, Modifier.fillMaxWidth().padding(top = 8.dp))
        Text(
            "92.3% agreement with exact play on the fixed 1,000-state sample.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = FormTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = FormTheme.colors.borderSubtle)
        Text(
            "Hard uses exact search because this game is small enough to solve. MCTS and Q-learning are here to compare notes.",
            Modifier.padding(top = 20.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AlgorithmRow(name: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Text(name, style = MaterialTheme.typography.titleLarge)
        Text(description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = FormTheme.colors.textSecondary)
    }
}

@Composable
private fun InfoHeading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
}

@Composable
private fun InfoPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(FormTheme.colors.canvas).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = FormTheme.spacing.screen, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 660.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                FormBackButton(onBack)
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 4.dp)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
                    if (subtitle != null) {
                        Text(subtitle, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = FormTheme.colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}
