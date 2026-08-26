package com.thevaguebox.probabilistictictactoe.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.thevaguebox.probabilistictictactoe.settings.ThemePreference

val ElectricX = Color(0xFF73E7FF)
val SolarO = Color(0xFFFFC56E)
val PlayerOne = Color(0xFF64E6C3)
val PlayerTwo = Color(0xFFFF7E9D)
val DeepInk = Color(0xFF090D1B)
val NightPanel = Color(0xFF151B2F)
val Mist = Color(0xFFF3F5FF)
val Ink = Color(0xFF172039)
val Violet = Color(0xFF9A8BFF)

private val DarkScheme = darkColorScheme(
    primary = ElectricX,
    secondary = SolarO,
    tertiary = Violet,
    background = DeepInk,
    surface = NightPanel,
    surfaceVariant = Color(0xFF202844),
    onPrimary = DeepInk,
    onSecondary = DeepInk,
    onBackground = Mist,
    onSurface = Mist,
    onSurfaceVariant = Color(0xFFC5CAE1),
    outline = Color(0xFF56607E),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00687A),
    secondary = Color(0xFF8B5700),
    tertiary = Color(0xFF5D51B4),
    background = Color(0xFFF7F8FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECF7),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF4E5770),
    outline = Color(0xFF7A8298),
)

private val PicPacTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        lineHeight = 56.sp,
        letterSpacing = (-2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.7.sp),
)

@Composable
fun PicPacTheme(preference: ThemePreference, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = PicPacTypography,
        content = content,
    )
}
