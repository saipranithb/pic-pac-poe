package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.theme.ElectricX
import com.thevaguebox.probabilistictictactoe.ui.theme.PlayerOne
import com.thevaguebox.probabilistictictactoe.ui.theme.PlayerTwo
import com.thevaguebox.probabilistictictactoe.ui.theme.SolarO

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    InfoPage(title = "How to play", onBack = onBack) {
        Text("Pic-Pac", style = MaterialTheme.typography.headlineMedium)
        Text(
            "There are five Xs and five Os in the bag.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        listOf(
            "Draw one.",
            "See what you got.",
            "Put it in any empty square.",
            "Make three Xs or three Os in a row.",
        ).forEachIndexed { index, text -> RuleStep(index + 1, text) }

        Spacer(Modifier.height(24.dp))
        Text("You're not X. You're not O.", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Either player can place either symbol. You win when the piece you place finishes the line.",
            modifier = Modifier.padding(top = 7.dp),
            style = MaterialTheme.typography.bodyLarge,
        )

        ProbabilityExample()
        Text(
            "The odds change as pieces leave the bag. Tapping faster doesn't change the draw.",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
        Spacer(Modifier.height(22.dp))
        Text("Classic", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Regular tic-tac-toe. Player 1 is X; Player 2 is O.",
            modifier = Modifier.padding(top = 5.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RuleStep(number: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$number.",
            modifier = Modifier.width(34.dp),
            style = MaterialTheme.typography.titleLarge,
            color = if (number % 2 == 0) SolarO else ElectricX,
            fontWeight = FontWeight.Black,
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProbabilityExample() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Text("After drawing X", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ExampleOdds("X ×4", "44%", ElectricX, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(28.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = .25f)))
            ExampleOdds("O ×5", "56%", SolarO, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ExampleOdds(label: String, odds: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
        Spacer(Modifier.weight(1f))
        Text(odds, style = MaterialTheme.typography.labelLarge, color = color)
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
        SettingToggle("Sound", "Small tones for draws, moves, and results.", settings.sound, onSound)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        SettingToggle("Haptics", "Vibrate on draws, moves, and results.", settings.haptics, onHaptics)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        SettingToggle("Reduced motion", "Skip most transitions.", settings.reducedMotion, onReducedMotion)

        Spacer(Modifier.height(24.dp))
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemePreference.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onTheme(theme) },
                    label = { Text(theme.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }

        Text(
            "Everything stays on this device. No account, ads, analytics, or Internet permission.",
            modifier = Modifier.padding(top = 30.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChecked(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
fun AiLabScreen(onBack: () -> Unit, onMcts: () -> Unit, onRl: () -> Unit) {
    InfoPage(title = "AI Lab", subtitle = "The other opponents live here.", onBack = onBack) {
        Text(
            "Each opponent sees the board, the piece in hand, and the bag counts. None can peek at the next draw.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        AlgorithmRow("Random", "Chooses any legal square.", MaterialTheme.colorScheme.onSurfaceVariant)
        AlgorithmRow("Heuristic", "Checks wins, threats, and the changing bag.", ElectricX)
        AlgorithmRow("Expectiminimax", "Medium searches four plies. Hard solves the full game.", SolarO)
        AlgorithmRow("MCTS", "Samples decisions and draws on a fixed budget.", PlayerTwo)
        AlgorithmRow("Q-learning", "A small table learned from seeded self-play.", PlayerOne)

        Spacer(Modifier.height(22.dp))
        Button(onClick = onMcts, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("Play MCTS")
        }
        Text(
            "2,000 simulations per move.",
            modifier = Modifier.padding(top = 5.dp, start = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onRl, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text("Play Q-learning")
        }
        Text(
            "92.3% agreement with exact play on the fixed 1,000-state sample.",
            modifier = Modifier.padding(top = 5.dp, start = 2.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "Hard uses exact search because this game is small enough to solve. MCTS and Q-learning are here to compare notes.",
            modifier = Modifier.padding(top = 24.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AlgorithmRow(name: String, description: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(42.dp).background(color, RoundedCornerShape(2.dp)))
        Column(Modifier.padding(start = 13.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = color)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoPage(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 660.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(9.dp)).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            content()
            Spacer(Modifier.height(30.dp))
        }
    }
}
