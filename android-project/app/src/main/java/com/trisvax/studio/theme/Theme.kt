package com.trisvax.studio.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object TrisvaxColors {
    val MainBackground = Color(0xFF0B0807)
    val PrimaryCard = Color(0xFF18110D)
    val SecondaryCard = Color(0xFF221711)

    val PrimaryOrange = Color(0xFFFF5500)
    val SecondaryOrange = Color(0xFFFF8232)

    val PrimaryText = Color(0xFFFFFFFF)
    val SecondaryText = Color(0xFF9AA69B)
    val MutedText = Color(0xFF667568)

    val Border = Color(0xFF2B352D)
    val BlueHighlight = Color(0xFF82B8FF)
    val ErrorDanger = Color(0xFFFF5A5A)
}

private val TrisvaxDarkColorScheme = darkColorScheme(
    primary = TrisvaxColors.PrimaryOrange,
    onPrimary = TrisvaxColors.PrimaryText,
    primaryContainer = TrisvaxColors.SecondaryCard,
    onPrimaryContainer = TrisvaxColors.SecondaryOrange,
    secondary = TrisvaxColors.SecondaryOrange,
    onSecondary = TrisvaxColors.PrimaryText,
    secondaryContainer = TrisvaxColors.SecondaryCard,
    onSecondaryContainer = TrisvaxColors.PrimaryText,
    tertiary = TrisvaxColors.BlueHighlight,
    background = TrisvaxColors.MainBackground,
    onBackground = TrisvaxColors.PrimaryText,
    surface = TrisvaxColors.PrimaryCard,
    onSurface = TrisvaxColors.PrimaryText,
    surfaceVariant = TrisvaxColors.SecondaryCard,
    onSurfaceVariant = TrisvaxColors.SecondaryText,
    outline = TrisvaxColors.Border,
    outlineVariant = TrisvaxColors.Border,
    error = TrisvaxColors.ErrorDanger,
    onError = TrisvaxColors.PrimaryText
)

private val TrisvaxTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = TrisvaxColors.PrimaryText
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = TrisvaxColors.PrimaryText
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TrisvaxColors.PrimaryText
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = TrisvaxColors.PrimaryText
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TrisvaxColors.PrimaryText
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TrisvaxColors.SecondaryText
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TrisvaxColors.PrimaryText
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = TrisvaxColors.SecondaryText
    )
)

@Composable
fun TrisvaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TrisvaxDarkColorScheme,
        typography = TrisvaxTypography,
        content = content
    )
}
