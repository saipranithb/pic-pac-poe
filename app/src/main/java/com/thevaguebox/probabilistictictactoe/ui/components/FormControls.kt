package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.thevaguebox.probabilistictictactoe.ui.theme.FormDepth
import com.thevaguebox.probabilistictictactoe.ui.theme.FormMotion
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme

/** A bounded material plane, not a semantic card. Callers retain their own reading and focus order. */
@Composable
fun FormSurface(
    modifier: Modifier = Modifier,
    shape: Shape = FormTheme.shapes.surface,
    recessed: Boolean = false,
    elevated: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = FormTheme.colors
    val face = if (recessed) colors.recess else colors.surface
    val top = if (recessed) lerp(face, colors.shadow, .12f) else colors.surfaceRaised
    Box(
        modifier
            .shadow(if (elevated && !recessed) FormDepth.surface else 0.dp, shape, clip = false)
            .background(Brush.verticalGradient(listOf(top, face)), shape)
            .border(.75.dp, if (recessed) colors.border else colors.borderSubtle, shape)
            .clip(shape),
        content = content,
    )
}

@Composable
fun FormPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = FormButton(label, onClick, modifier, enabled, primary = true)

@Composable
fun FormSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = FormButton(label, onClick, modifier, enabled, primary = false)

@Composable
private fun FormButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    primary: Boolean,
) {
    val colors = FormTheme.colors
    val shape = FormTheme.shapes.control
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val reducedMotion = FormTheme.reducedMotion
    val press by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else FormMotion.pressMillis, easing = FormMotion.easing),
        label = "control-contact",
    )
    val face = when {
        !enabled -> colors.recess
        primary -> colors.action
        else -> colors.surface
    }
    val content = when {
        !enabled -> colors.textSecondary
        primary -> colors.onAction
        else -> colors.text
    }
    val base = if (primary && enabled) colors.xEdge else colors.shadow
    val rim = when {
        focused -> if (primary) colors.onAction else colors.focus
        !enabled -> colors.borderSubtle
        primary -> lerp(face, colors.highlight, .14f)
        else -> colors.border
    }
    Box(
        modifier
            .heightIn(min = 52.dp)
            .shadow(if (enabled) FormDepth.surface else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (enabled) base else face)
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .padding(bottom = if (enabled) FormDepth.controlBase else 0.dp)
                .graphicsLayer {
                    translationY = if (reducedMotion) 0f else FormDepth.pressTravel.toPx() * press
                }
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            lerp(face, colors.highlight, if (enabled) .035f else 0f),
                            lerp(face, colors.shadow, press * .08f),
                        ),
                    ),
                    shape,
                )
                .border(if (focused) 2.dp else .75.dp, rim, shape)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = content, textAlign = TextAlign.Center)
        }
    }
}

/** Use inside a selectableGroup; both the check and fill reinforce the native selected semantics. */
@Composable
fun FormChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FormTheme.colors
    val shape = FormTheme.shapes.choice
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    val face = when {
        selected -> colors.text
        pressed && enabled -> colors.surfaceRaised
        else -> colors.recess
    }
    val content = if (selected) colors.canvas else if (enabled) colors.text else colors.textSecondary
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(face)
            .border(if (focused) 2.dp else 1.dp, if (focused) content else colors.border, shape)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Canvas(Modifier.padding(end = 5.dp).size(13.dp)) {
                drawLine(
                    content,
                    Offset(size.width * .13f, size.height * .53f),
                    Offset(size.width * .40f, size.height * .78f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    content,
                    Offset(size.width * .40f, size.height * .78f),
                    Offset(size.width * .90f, size.height * .20f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
    }
}

@Composable
fun FormBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FormTheme.colors
    val shape = FormTheme.shapes.control
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box(
        modifier
            .size(48.dp)
            .clip(shape)
            .background(if (pressed) colors.recess else colors.surface)
            .border(if (focused) 2.dp else .75.dp, if (focused) colors.focus else colors.borderSubtle, shape)
            .clickable(interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val start = if (rtl) .35f else .65f
            val end = if (rtl) .68f else .32f
            drawLine(
                colors.text,
                Offset(size.width * start, size.height * .18f),
                Offset(size.width * end, size.height * .5f),
                2.dp.toPx(),
                StrokeCap.Round,
            )
            drawLine(
                colors.text,
                Offset(size.width * end, size.height * .5f),
                Offset(size.width * start, size.height * .82f),
                2.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun FormTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FormTheme.colors
    val shape = FormTheme.shapes.choice
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val focused by interactions.collectIsFocusedAsState()
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (pressed && enabled) colors.surface else Color.Transparent)
            .then(if (focused) Modifier.border(2.dp, colors.focus, shape) else Modifier)
            .clickable(interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun FormSectionHeading(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = FormTheme.colors.textSecondary,
    )
}
