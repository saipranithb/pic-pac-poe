package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thevaguebox.probabilistictictactoe.ui.theme.ElectricX
import com.thevaguebox.probabilistictictactoe.ui.theme.SolarO

@Composable
fun HomeScreen(
    onClassic: () -> Unit,
    onPicPacLocal: () -> Unit,
    onPicPacAi: (Difficulty) -> Unit,
    onHowTo: () -> Unit,
    onSettings: () -> Unit,
    onAiLab: () -> Unit,
) {
    var difficulty by remember { mutableStateOf(Difficulty.MEDIUM) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HomeBoardMotif()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().widthIn(max = 620.dp)) {
                Spacer(Modifier.height(18.dp))
                Text("Pic-Pac-Poe", style = MaterialTheme.typography.displayLarge)
                Text(
                    "Tic-tac-toe, except you don't choose your symbol.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(34.dp))
                Text("Pick a game", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(7.dp))

                ModeRow(
                    mark = "3×3",
                    title = "Classic",
                    subtitle = "Regular tic-tac-toe.",
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onClassic,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))
                ModeRow(
                    mark = "X/O",
                    title = "Pic-Pac Local",
                    subtitle = "Two players. One phone.",
                    accent = ElectricX,
                    onClick = onPicPacLocal,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .22f))

                Column(Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("O?", style = MaterialTheme.typography.titleLarge, color = SolarO)
                        Column(Modifier.padding(start = 16.dp)) {
                            Text("Vs Computer", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Play Pic-Pac against the computer.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Difficulty.entries.filter(Difficulty::production).forEach { option ->
                            DifficultyChoice(
                                option = option,
                                selected = difficulty == option,
                                onClick = { difficulty = option },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Button(
                        onClick = { onPicPacAi(difficulty) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SolarO,
                            contentColor = Color(0xFF241600),
                        ),
                    ) { Text("Play") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    QuietAction("How to play", onHowTo)
                    QuietAction("AI Lab", onAiLab)
                    QuietAction("Settings", onSettings)
                }
            }
        }
    }
}

@Composable
private fun ModeRow(
    mark: String,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 17.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 52.dp, height = 44.dp), contentAlignment = Alignment.CenterStart) {
            Text(mark, color = accent, fontWeight = FontWeight.Black)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("→", style = MaterialTheme.typography.titleLarge, color = accent)
    }
}

@Composable
private fun DifficultyChoice(
    option: Difficulty,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val foreground = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(option.title, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HomeBoardMotif() {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = .035f)
    Canvas(Modifier.fillMaxSize()) {
        val side = size.minDimension * .82f
        val left = size.width - side * .72f
        val top = -side * .28f
        for (index in 1..2) {
            val offset = side * index / 3f
            drawLine(color, Offset(left + offset, top), Offset(left + offset, top + side), 3f)
            drawLine(color, Offset(left, top + offset), Offset(left + side, top + offset), 3f)
        }
    }
}
