package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage

@Immutable
data class NoorGlassPalette(
    val isDark: Boolean,
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val glassSurface: Color,
    val glassElevated: Color,
    val glassBorder: Color,
    val accentGold: Color,
    val accentEmerald: Color,
    val mushafPaper: Color,
    val mushafInk: Color,
    val mushafBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val LocalNoorGlass = staticCompositionLocalOf {
    NoorGlassPalette(
        isDark = false,
        backgroundTop = AlabasterBg,
        backgroundBottom = Color(0xFFE7EFEA),
        glassSurface = Color(0xDDFCFCFC),
        glassElevated = Color(0xF2FFFFFF),
        glassBorder = Color(0x380B6E57),
        accentGold = GoldDeep,
        accentEmerald = EmeraldPrimaryLight,
        mushafPaper = ParchmentMushaf,
        mushafInk = Color(0xFF171C19),
        mushafBorder = ParchmentBorder,
        textPrimary = LightInkPrimary,
        textSecondary = LightInkSecondary
    )
}

private val NoorDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = Color(0xFF04211A),
    primaryContainer = Color(0xFF114B3E),
    onPrimaryContainer = Color(0xFFD6F7EA),
    secondary = GoldWarm,
    onSecondary = Color(0xFF281D00),
    secondaryContainer = Color(0xFF3E3008),
    onSecondaryContainer = GoldSoft,
    tertiary = Color(0xFF6DD5ED),
    onTertiary = Color(0xFF00242B),
    background = DarkBg,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = Color(0xFF13332C),
    onSurfaceVariant = DarkInkSecondary,
    outline = Color(0xFF3B665B),
    outlineVariant = Color(0xFF1D4239),
    error = ErrorRed,
    onError = Color(0xFF2B0000)
)

private val NoorLightColorScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F1E8),
    onPrimaryContainer = Color(0xFF04291F),
    secondary = GoldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDF0CA),
    onSecondaryContainer = Color(0xFF2B1F00),
    tertiary = Color(0xFF0D657A),
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = Color(0xFFE4ECE7),
    onSurfaceVariant = LightInkSecondary,
    outline = Color(0xFF6E8981),
    outlineVariant = Color(0xFFC5D5CE),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val NoorShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun NoorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    language: AppLanguage = AppLanguage.AR,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NoorDarkColorScheme else NoorLightColorScheme
    val glassPalette = if (darkTheme) {
        NoorGlassPalette(
            isDark = true,
            backgroundTop = Color(0xFF051512),
            backgroundBottom = Color(0xFF0A2922),
            glassSurface = Color(0xCC0D2923),
            glassElevated = Color(0xE813372F),
            glassBorder = Color(0x45E5C158),
            accentGold = GoldWarm,
            accentEmerald = EmeraldPrimaryDark,
            mushafPaper = DarkMushafBg,
            mushafInk = Color(0xFFF3F6F1),
            mushafBorder = Color(0xFF3A5C4E),
            textPrimary = DarkInkPrimary,
            textSecondary = DarkInkSecondary
        )
    } else {
        NoorGlassPalette(
            isDark = false,
            backgroundTop = Color(0xFFF3F7F4),
            backgroundBottom = Color(0xFFE5EFE9),
            glassSurface = Color(0xDBFFFFFF),
            glassElevated = Color(0xF5FFFFFF),
            glassBorder = Color(0x380B6E57),
            accentGold = GoldDeep,
            accentEmerald = EmeraldPrimaryLight,
            mushafPaper = ParchmentMushaf,
            mushafInk = Color(0xFF151C18),
            mushafBorder = ParchmentBorder,
            textPrimary = LightInkPrimary,
            textSecondary = LightInkSecondary
        )
    }

    val layoutDirection = if (language == AppLanguage.AR) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildNoorTypography(isArabic = language == AppLanguage.AR)

    CompositionLocalProvider(
        LocalNoorGlass provides glassPalette,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = NoorShapes,
            content = content
        )
    }
}
