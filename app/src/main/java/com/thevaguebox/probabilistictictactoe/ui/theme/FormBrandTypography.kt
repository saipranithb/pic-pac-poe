package com.thevaguebox.probabilistictictactoe.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.thevaguebox.probabilistictictactoe.R

/** An accent face for the identity and emotional moments, never the functional type system. */
object FormBrandTypography {
    // Unmodified static fonts work on every supported Android version, including API 24/25.
    // Redistribution notice travels in assets/licenses/fredoka-OFL.txt.
    val fontFamily = FontFamily(
        Font(R.font.fredoka_medium, FontWeight.Medium),
        Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    )

    val wordmark = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        // Fredoka has taller native metrics than the previous 42sp system face. 40sp keeps
        // its physical line box within that existing Home footprint without clipping glyphs.
        fontSize = 40.sp,
        lineHeight = 46.sp,
        letterSpacing = (-.35).sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    val emotionalHeadline = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

    val resultHeadline = emotionalHeadline.copy(fontWeight = FontWeight.SemiBold)
}
