package com.envelope.budget.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class EnvelopeColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val transfer: Color,
)

val LocalEnvelopeColors = staticCompositionLocalOf {
    EnvelopeColors(
        positive = MoneyPositive,
        negative = MoneyNegative,
        warning = MoneyWarning,
        transfer = MoneyTransfer,
    )
}

private val LightScheme = lightColorScheme(
    primary = GreenDeep,
    onPrimary = Color.White,
    primaryContainer = GreenPale,
    onPrimaryContainer = GreenInk,
    secondary = GreenMid,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EFE3),
    onSecondaryContainer = GreenInk,
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE08C),
    onTertiaryContainer = Color(0xFF261A00),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Color(0xFF47504B),
    surfaceContainer = Color(0xFFF1F4F0),
    surfaceContainerHigh = Color(0xFFE9EDE8),
    outline = Color(0xFF7A847E),
    outlineVariant = Color(0xFFCBD4CE),
    error = MoneyNegative,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
)

private val DarkScheme = darkColorScheme(
    primary = DarkGreen,
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF14503C),
    onPrimaryContainer = Color(0xFFA8E6D0),
    secondary = Color(0xFF9CD5C1),
    onSecondary = Color(0xFF06382B),
    secondaryContainer = Color(0xFF24513F),
    onSecondaryContainer = Color(0xFFD3EFE3),
    tertiary = Color(0xFFF0C060),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4400),
    onTertiaryContainer = Color(0xFFFFE08C),
    background = DarkSurface,
    onBackground = Color(0xFFE2E8E3),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E8E3),
    surfaceVariant = DarkSurfaceDim,
    onSurfaceVariant = Color(0xFFB6C0BA),
    surfaceContainer = Color(0xFF171D1A),
    surfaceContainerHigh = Color(0xFF212824),
    outline = Color(0xFF8C968F),
    outlineVariant = Color(0xFF3F4843),
    error = MoneyNegativeDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD5),
)

@Composable
fun EnvelopeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val money = if (darkTheme) {
        EnvelopeColors(MoneyPositiveDark, MoneyNegativeDark, MoneyWarningDark, MoneyTransferDark)
    } else {
        EnvelopeColors(MoneyPositive, MoneyNegative, MoneyWarning, MoneyTransfer)
    }
    CompositionLocalProvider(LocalEnvelopeColors provides money) {
        MaterialTheme(
            colorScheme = scheme,
            typography = EnvelopeTypography,
            content = content,
        )
    }
}
