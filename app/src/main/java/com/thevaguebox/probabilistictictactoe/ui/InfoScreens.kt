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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thevaguebox.probabilistictictactoe.settings.AppSettings
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference
import com.thevaguebox.probabilistictictactoe.ui.theme.ElectricX
import com.thevaguebox.probabilistictictactoe.ui.theme.SolarO
import com.thevaguebox.probabilistictictactoe.ui.theme.Violet

@Composable
fun HowToPlayScreen(onBack: () -> Unit) {
    InfoPage("How to play", "A one-minute field guide", onBack) {
        Surface(
            color = Violet.copy(alpha = .13f),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("PLAYERS ARE NOT X AND O", style = MaterialTheme.typography.labelLarge, color = Violet)
                Text(
                    "Either player can draw either symbol. If you place the piece that completes X-X-X or O-O-O, you win.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        val steps = listOf(
            "10" to "The bag begins with five X pieces and five O pieces.",
            "↓" to "Draw one without replacement. The hidden count changes immediately.",
            "◉" to "See your piece before choosing a cell—this is the strategic heart of Pic-Pac.",
            "3×3" to "Place it in any empty cell. Tap timing never changes the draw.",
            "XXX" to "Complete either symbol's line on your turn to win.",
            "%" to "Watch NEXT DRAW odds. A scarce symbol changes every threat.",
        )
        steps.forEachIndexed { index, (mark, text) -> TutorialStep(index + 1, mark, text) }
        Spacer(Modifier.height(10.dp))
        Text("Classic mode", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Familiar pass-and-play: Player 1 is X, Player 2 is O, with no bag or probability.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TutorialStep(number: Int, mark: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).background(if (number % 2 == 0) SolarO.copy(alpha = .14f) else ElectricX.copy(alpha = .13f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(mark, fontWeight = FontWeight.Black, color = if (number % 2 == 0) SolarO else ElectricX) }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text("STEP $number", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
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
    InfoPage("Settings", "Just the useful things", onBack) {
        SettingToggle("Sound", "Lightweight local tones—no downloaded audio.", settings.sound, onSound)
        SettingToggle("Haptics", "Tactile placement, reveal, and result feedback.", settings.haptics, onHaptics)
        SettingToggle("Reduced motion", "Shortens or removes decorative transitions.", settings.reducedMotion, onReducedMotion)
        Spacer(Modifier.height(14.dp))
        Text("THEME", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { theme ->
                FilterChip(
                    selected = settings.theme == theme,
                    onClick = { onTheme(theme) },
                    label = { Text(theme.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .09f), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("PRIVATE BY DESIGN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Every match and every AI move runs on this device. Pic-Pac-Poe requests no Internet permission.",
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChecked(!checked) }.padding(vertical = 12.dp),
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
    InfoPage("AI Lab", "Algorithms, not magic", onBack) {
        Text(
            "Every agent sees the same public board, held piece, and bag counts. None can access the live bag or future draws.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        AlgorithmCard("RANDOM", "A reproducible legal baseline.", Color(0xFF94A0BD))
        AlgorithmCard("HEURISTIC", "Probability-aware tactics with controlled top-band variety.", ElectricX)
        AlgorithmCard("EXPECTIMINIMAX", "Depth-limited Medium and exact memoized Hard.", SolarO)
        AlgorithmCard("STOCHASTIC MCTS", "Budgeted decision/chance search for comparison.", Violet)
        AlgorithmCard("TABULAR Q-LEARNING", "Offline self-play policy measured against the exact oracle.", Color(0xFF64E6C3))
        Spacer(Modifier.height(14.dp))
        Button(onClick = onMcts, modifier = Modifier.fillMaxWidth()) {
            Text("Play against MCTS • 2,000 simulations")
        }
        Button(onClick = onRl, modifier = Modifier.fillMaxWidth()) {
            Text("Play against RL • 92.3% oracle agreement")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "The production opponent uses conventional exact search on Hard because this game is small enough to solve. The Lab keeps approximate methods honest.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AlgorithmCard(name: String, description: String, color: Color) {
    Surface(
        color = color.copy(alpha = .09f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Column(Modifier.padding(start = 12.dp)) {
                Text(name, style = MaterialTheme.typography.labelLarge, color = color)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InfoPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.clickable(role = Role.Button, onClick = onBack),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
            ) { Text("←", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(25.dp))
        content()
        Spacer(Modifier.height(28.dp))
    }
}
