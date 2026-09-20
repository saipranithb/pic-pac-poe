package com.thevaguebox.probabilistictictactoe.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference

/** Portable material roles: symbols identify pieces; actor colours never imply symbol ownership. */
@Immutable
data class FormColors(
    val isDark: Boolean,
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val recess: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val borderSubtle: Color,
    val highlight: Color,
    val shadow: Color,
    val x: Color,
    val xHighlight: Color,
    val xEdge: Color,
    val o: Color,
    val oHighlight: Color,
    val oEdge: Color,
    val playerOne: Color,
    val playerTwo: Color,
    val focus: Color,
    val action: Color,
    val onAction: Color,
)

internal val FormDarkColors = FormColors(
    isDark = true,
    canvas = Color(0xFF251F1C),
    surface = Color(0xFF47382F),
    surfaceRaised = Color(0xFF503E33),
    recess = Color(0xFF342923),
    text = Color(0xFFFFF2DB),
    textSecondary = Color(0xFFDDC5B5),
    border = Color(0xFFB59B88),
    borderSubtle = Color(0xFF796455),
    highlight = Color(0xFFFFE5C6),
    shadow = Color(0xFF17120F),
    x = Color(0xFFFFAC8F),
    xHighlight = Color(0xFFFFD5BC),
    xEdge = Color(0xFFBB775F),
    o = Color(0xFFBFD680),
    oHighlight = Color(0xFFDDEBB1),
    oEdge = Color(0xFF819748),
    playerOne = Color(0xFF7ADBD1),
    playerTwo = Color(0xFFBEB9EF),
    focus = Color(0xFFFFF2DB),
    action = Color(0xFFFFAC8F),
    onAction = Color(0xFF382218),
)

internal val FormLightColors = FormColors(
    isDark = false,
    canvas = Color(0xFFFBF1DE),
    surface = Color(0xFFFFF9EC),
    surfaceRaised = Color(0xFFFFFCF5),
    recess = Color(0xFFEADDC7),
    text = Color(0xFF34291F),
    textSecondary = Color(0xFF695543),
    border = Color(0xFF8D755F),
    borderSubtle = Color(0xFFC2AF95),
    highlight = Color(0xFFFFFDF7),
    shadow = Color(0xFF9B8872),
    x = Color(0xFF983A24),
    xHighlight = Color(0xFFB44C32),
    xEdge = Color(0xFF6F2B1C),
    o = Color(0xFF4C6819),
    oHighlight = Color(0xFF638033),
    oEdge = Color(0xFF344C0C),
    playerOne = Color(0xFF00645D),
    playerTwo = Color(0xFF5B4B97),
    focus = Color(0xFF34291F),
    action = Color(0xFF983A24),
    onAction = Color(0xFFFFF9EC),
)

object FormSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val screen = 20.dp
}

object FormShapes {
    val choice = RoundedCornerShape(10.dp)
    val control = RoundedCornerShape(14.dp)
    val surface = RoundedCornerShape(18.dp)
    val board = RoundedCornerShape(24.dp)
    val well = RoundedCornerShape(18.dp)
}

object FormDepth {
    val surface = 2.dp
    val controlBase = 3.dp
    val pressTravel = 2.dp
    val rim = 1.dp
}

/** Decorative motion only. The existing presentation clock remains the sole turn-stage authority. */
object FormMotion {
    const val pressMillis = 90
    const val stateMillis = 140
    const val revealMillis = 160
    const val placementMillis = 180
    val easing = CubicBezierEasing(.2f, .8f, .2f, 1f)
}

private val LocalFormColors = staticCompositionLocalOf { FormDarkColors }
private val LocalFormReducedMotion = staticCompositionLocalOf { false }

object FormTheme {
    val colors: FormColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFormColors.current

    val reducedMotion: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalFormReducedMotion.current

    val spacing = FormSpacing
    val shapes = FormShapes
}

private fun formText(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private val FormTypography = Typography(
    displayLarge = formText(42, 46, FontWeight.ExtraBold, -.8f),
    displayMedium = formText(36, 41, FontWeight.ExtraBold, -.6f),
    displaySmall = formText(32, 37, FontWeight.Bold, -.4f),
    headlineLarge = formText(30, 35, FontWeight.ExtraBold, -.4f),
    headlineMedium = formText(24, 29, FontWeight.Bold, -.2f),
    headlineSmall = formText(22, 27, FontWeight.Bold),
    titleLarge = formText(20, 25, FontWeight.Bold),
    titleMedium = formText(16, 22, FontWeight.SemiBold),
    titleSmall = formText(14, 20, FontWeight.SemiBold),
    bodyLarge = formText(16, 24),
    bodyMedium = formText(14, 21),
    bodySmall = formText(12, 18),
    labelLarge = formText(14, 19, FontWeight.Bold, .15f),
    labelMedium = formText(12, 17, FontWeight.Bold, .4f),
    labelSmall = formText(11, 16, FontWeight.SemiBold, .4f),
)

private fun FormColors.materialScheme() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = action,
    onPrimary = onAction,
    primaryContainer = surfaceRaised,
    onPrimaryContainer = text,
    secondary = o,
    onSecondary = canvas,
    secondaryContainer = recess,
    onSecondaryContainer = text,
    tertiary = playerOne,
    onTertiary = canvas,
    tertiaryContainer = recess,
    onTertiaryContainer = text,
    background = canvas,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = recess,
    onSurfaceVariant = textSecondary,
    surfaceTint = Color.Transparent,
    surfaceDim = if (isDark) canvas else recess,
    surfaceBright = surfaceRaised,
    surfaceContainerLowest = canvas,
    surfaceContainerLow = if (isDark) recess else surface,
    surfaceContainer = surface,
    surfaceContainerHigh = surfaceRaised,
    surfaceContainerHighest = surfaceRaised,
    outline = border,
    outlineVariant = borderSubtle,
    inverseSurface = text,
    inverseOnSurface = canvas,
    inversePrimary = if (isDark) FormLightColors.action else FormDarkColors.action,
)

private val FormDarkScheme = FormDarkColors.materialScheme()
private val FormLightScheme = FormLightColors.materialScheme()
private val MaterialFormShapes = Shapes(
    extraSmall = FormShapes.choice,
    small = FormShapes.control,
    medium = FormShapes.surface,
    large = FormShapes.board,
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PicPacTheme(
    preference: ThemePreference,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val colors = if (dark) FormDarkColors else FormLightColors
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(LocalFormColors provides colors, LocalFormReducedMotion provides reducedMotion) {
        MaterialTheme(
            colorScheme = if (dark) FormDarkScheme else FormLightScheme,
            typography = FormTypography,
            shapes = MaterialFormShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
