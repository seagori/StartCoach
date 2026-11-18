package com.seagori.hgr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF24372D)
val Muted = Color(0xFF7B897D)
val Accent = Color(0xFF719B79)
val AccentDeep = Color(0xFF467958)
val AccentLight = Color(0xFFE7F2E7)
val Line = Color(0xFFE1E8DF)
val Soft = Color(0xFFF0F5EF)
val Bg = Color(0xFFF6F8F4)
val Card = Color(0xFFFFFFFF)
val Insight = Color(0xFFECF6E8)

private val Error = Color(0xFFB3261E)
private val OnErrorContainer = Color(0xFF410E0B)
private val ErrorContainer = Color(0xFFF9DEDC)

private val scheme = lightColorScheme(
    primary = AccentDeep,
    onPrimary = Card,
    primaryContainer = AccentLight,
    onPrimaryContainer = AccentDeep,
    inversePrimary = Accent,
    secondary = Accent,
    onSecondary = Card,
    secondaryContainer = AccentLight,
    onSecondaryContainer = AccentDeep,
    tertiary = AccentDeep,
    onTertiary = Card,
    tertiaryContainer = Insight,
    onTertiaryContainer = Ink,
    background = Bg,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Soft,
    onSurfaceVariant = Muted,
    surfaceTint = AccentDeep,
    inverseSurface = Ink,
    inverseOnSurface = Card,
    error = Error,
    onError = Card,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Line,
    outlineVariant = Line,
    scrim = Color.Black,
    surfaceBright = Card,
    surfaceDim = Soft,
    surfaceContainerLowest = Card,
    surfaceContainerLow = Bg,
    surfaceContainer = Soft,
    surfaceContainerHigh = Card,
    surfaceContainerHighest = Soft
)

@Composable
fun StartCoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
