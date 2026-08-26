package com.thevaguebox.probabilistictictactoe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thevaguebox.probabilistictictactoe.ui.theme.ElectricX
import com.thevaguebox.probabilistictictactoe.ui.theme.SolarO
import com.thevaguebox.probabilistictictactoe.ui.theme.Violet

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
                ),
            ),
    ) {
        LaboratoryBackdrop()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                color = Violet.copy(alpha = .15f),
                contentColor = MaterialTheme.colorScheme.tertiary,
                shape = CircleShape,
                modifier = Modifier.border(1.dp, Violet.copy(alpha = .4f), CircleShape),
            ) {
                Text(
                    "OFFLINE STRATEGY LAB",
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("PIC", style = MaterialTheme.typography.displayLarge, color = ElectricX)
                Text("·", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("PAC", style = MaterialTheme.typography.displayLarge, color = SolarO)
            }
            Text(
                "POE",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 10.sp,
            )
            Text(
                "Fate chooses the piece. You choose where it matters.",
                modifier = Modifier.padding(top = 10.dp, bottom = 26.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            ModeCard(
                eyebrow = "THE ORIGINAL",
                title = "Classic",
                subtitle = "No probability. No excuses.",
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onClassic,
            )
            Spacer(Modifier.height(12.dp))
            ModeCard(
                eyebrow = "SIGNATURE MODE  •  RECOMMENDED",
                title = "Pic-Pac Local",
                subtitle = "Draw a piece. Read the odds. Outsmart the player beside you.",
                accent = ElectricX,
                hero = true,
                onClick = onPicPacLocal,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = .92f),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("OFFLINE COMPUTER", style = MaterialTheme.typography.labelMedium, color = SolarO)
                    Text("Pic-Pac AI", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        difficulty.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Difficulty.entries.forEach { option ->
                            FilterChip(
                                selected = difficulty == option,
                                onClick = { difficulty = option },
                                label = { Text(option.title) },
                            )
                        }
                    }
                    Button(
                        onClick = { onPicPacAi(difficulty) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SolarO, contentColor = Color(0xFF241600)),
                    ) { Text("Challenge ${difficulty.title}") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuietAction("How to play", onHowTo)
                QuietAction("Settings", onSettings)
                QuietAction("AI Lab", onAiLab)
            }
        }
    }
}

@Composable
private fun ModeCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: Color,
    hero: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        color = if (hero) accent.copy(alpha = .11f) else MaterialTheme.colorScheme.surface.copy(alpha = .92f),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = if (hero) 6.dp else 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (hero) 1.5.dp else 1.dp, accent.copy(alpha = if (hero) .65f else .18f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(accent.copy(alpha = .18f)),
                contentAlignment = Alignment.Center,
            ) { Text(if (hero) "✦" else "3×3", color = accent, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(eyebrow, style = MaterialTheme.typography.labelMedium, color = accent)
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("→", style = MaterialTheme.typography.headlineMedium, color = accent)
        }
    }
}

@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LaboratoryBackdrop() {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = .055f)
    Canvas(Modifier.fillMaxSize()) {
        val step = size.minDimension / 7f
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(ElectricX.copy(alpha = .08f), size.minDimension * .38f, Offset(size.width * .05f, size.height * .22f))
        drawCircle(SolarO.copy(alpha = .07f), size.minDimension * .3f, Offset(size.width * .95f, size.height * .65f))
    }
}
