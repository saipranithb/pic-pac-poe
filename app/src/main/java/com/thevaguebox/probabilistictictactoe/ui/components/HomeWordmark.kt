package com.thevaguebox.probabilistictictactoe.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thevaguebox.probabilistictictactoe.ui.theme.FormBrandTypography
import com.thevaguebox.probabilistictictactoe.ui.theme.FormTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Internal test-observation metadata; not a spoken accessibility state or an animation driver.
internal val WordmarkProgressKey = SemanticsPropertyKey<List<Float>>("WordmarkProgress")

private val WordmarkParts = listOf("Pic", "-", "Pac", "-", "Poe")
private val PartGroups = listOf(0, 1, 1, 2, 2)
private val GroupRotations = listOf(-.8f, .6f, -.6f)

/** One accessible identity, three softly placed groups, no animation-owned layout or game state. */
@Composable
fun HomeWordmark(
    modifier: Modifier = Modifier,
    animateEntrance: Boolean = false,
    motionReady: Boolean = true,
    onEntranceStarted: () -> Unit = {},
    style: TextStyle = FormBrandTypography.wordmark,
) {
    val colors = FormTheme.colors
    val reducedMotion = FormTheme.reducedMotion
    // The app shell consumes the entrance as soon as it starts. Latch the entry decision so
    // that parent recomposition does not cancel this visit's one animation.
    val requestedOnEntry = remember { animateEntrance }
    val progress = remember {
        List(3) { Animatable(if (requestedOnEntry && !reducedMotion) 0f else 1f) }
    }
    var started by remember { mutableStateOf(false) }
    val consumeEntrance by rememberUpdatedState(onEntranceStarted)
    LaunchedEffect(motionReady, reducedMotion) {
        if (!motionReady) return@LaunchedEffect
        if (started || !requestedOnEntry || reducedMotion) {
            progress.forEach { it.snapTo(1f) }
            if (!started && requestedOnEntry) consumeEntrance()
            started = true
            return@LaunchedEffect
        }
        started = true
        consumeEntrance()
        coroutineScope {
            progress.forEachIndexed { index, value ->
                launch {
                    delay(index * 70L)
                    // A bounded, spring-like settle: only .12dp positional overshoot. Unlike
                    // an unbounded spring this has an exact idle endpoint, 620ms for all groups.
                    value.animateTo(1f, keyframes {
                        durationMillis = 480
                        0f at 0 using FastOutSlowInEasing
                        1.015f at 360 using LinearOutSlowInEasing
                        1f at 480
                    })
                }
            }
        }
    }

    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 24)
    BoxWithConstraints(
        modifier.fillMaxWidth().testTag("home-wordmark").clearAndSetSemantics {
            text = AnnotatedString("Pic-Pac-Poe")
            heading()
            this[WordmarkProgressKey] = progress.map { it.value }
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        val available = (constraints.maxWidth - 2 * with(density) { 1.dp.roundToPx() }).coerceAtLeast(0)
        val fittedStyle = remember(available, density, style, measurer) {
            fun atSize(size: Float) = style.copy(
                fontSize = size.sp,
                lineHeight = (size * style.lineHeight.value / style.fontSize.value).sp,
            )
            fun width(candidate: TextStyle) = WordmarkParts.sumOf {
                measurer.measure(it, candidate, softWrap = false, maxLines = 1, layoutDirection = LayoutDirection.Ltr).size.width
            }
            // Only the identity lockup is width-fitted. Functional text continues to respect
            // the full user font scale. Binary search also handles Android nonlinear scaling.
            if (width(style) <= available) style else {
                var low = 1f
                var high = style.fontSize.value
                repeat(12) {
                    val middle = (low + high) / 2
                    if (width(atSize(middle)) <= available) low = middle else high = middle
                }
                atSize(low)
            }
        }
        // This English product identity keeps its spelling/order even in an RTL app locale.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.padding(horizontal = 1.dp)) {
                WordmarkParts.forEachIndexed { index, part ->
                    val group = PartGroups[index]
                    Text(
                        part,
                        Modifier.testTag("wordmark-part-$index").graphicsLayer {
                            val value = if (reducedMotion) 1f else progress[group].value
                            alpha = value.coerceIn(0f, 1f)
                            translationY = 8.dp.toPx() * (1f - value)
                            scaleX = .95f + .05f * value
                            scaleY = scaleX
                            rotationZ = if (part == "-") 0f else GroupRotations[group] * (1f - value)
                        },
                        style = fittedStyle,
                        color = when (index) {
                            0 -> colors.x
                            2 -> colors.text
                            4 -> colors.o
                            else -> colors.textSecondary
                        },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
