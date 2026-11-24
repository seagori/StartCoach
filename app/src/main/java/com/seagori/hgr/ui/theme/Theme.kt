package com.seagori.hgr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppThemePreset {
    SAGE,
    BLUE,
    DARK;

    companion object {
        fun fromStored(raw: String?): AppThemePreset =
            entries.firstOrNull { it.name == raw } ?: SAGE
    }
}

var Ink by mutableStateOf(Color(0xFF382218))
    private set
var Muted by mutableStateOf(Color(0xFF756C63))
    private set
var Accent by mutableStateOf(Color(0xFF6A994E))
    private set
var AccentDeep by mutableStateOf(Color(0xFF4F7A38))
    private set
var AccentLight by mutableStateOf(Color(0xFFE4EEDF))
    private set
var Line by mutableStateOf(Color(0xFFDCD6CA))
    private set
var Soft by mutableStateOf(Color(0xFFEAE5DB))
    private set
var Bg by mutableStateOf(Color(0xFFF4F1EA))
    private set
var Card by mutableStateOf(Color(0xFFFFFFFF))
    private set
var Insight by mutableStateOf(Color(0xFFEDF3E8))
    private set

object AppTheme {
    private val _preset = MutableStateFlow(AppThemePreset.SAGE)
    val preset: StateFlow<AppThemePreset> = _preset

    fun init(stored: AppThemePreset) {
        set(stored)
    }

    fun set(next: AppThemePreset) {
        _preset.value = next
        when (next) {
            AppThemePreset.SAGE -> {
                Ink = Color(0xFF382218)
                Muted = Color(0xFF756C63)
                Accent = Color(0xFF6A994E)
                AccentDeep = Color(0xFF4F7A38)
                AccentLight = Color(0xFFE4EEDF)
                Line = Color(0xFFDCD6CA)
                Soft = Color(0xFFEAE5DB)
                Bg = Color(0xFFF4F1EA)
                Card = Color(0xFFFFFFFF)
                Insight = Color(0xFFEDF3E8)
            }
            AppThemePreset.BLUE -> {
                Ink = Color(0xFF1E2D3D)
                Muted = Color(0xFF6B7C8F)
                Accent = Color(0xFF4A80B4)
                AccentDeep = Color(0xFF2B5B8C)
                AccentLight = Color(0xFFE3EFFA)
                Line = Color(0xFFDCE5EE)
                Soft = Color(0xFFEEF4FA)
                Bg = Color(0xFFF5F8FC)
                Card = Color(0xFFFFFFFF)
                Insight = Color(0xFFE6F1FC)
            }
            AppThemePreset.DARK -> {
                Ink = Color(0xFFE7EDE8)
                Muted = Color(0xFF9AA89D)
                Accent = Color(0xFF7EB088)
                AccentDeep = Color(0xFF5D9369)
                AccentLight = Color(0xFF24382B)
                Line = Color(0xFF2C352F)
                Soft = Color(0xFF222925)
                Bg = Color(0xFF141816)
                Card = Color(0xFF1B211E)
                Insight = Color(0xFF1F2B23)
            }
        }
    }
}

private val Error = Color(0xFFB3261E)
private val OnErrorContainer = Color(0xFF410E0B)
private val ErrorContainer = Color(0xFFF9DEDC)

@Composable
fun StartCoachTheme(content: @Composable () -> Unit) {
    val isDark = AppTheme.preset.value == AppThemePreset.DARK
    val scheme = if (isDark) {
        darkColorScheme(
            primary = AccentDeep,
            onPrimary = Color.White,
            primaryContainer = AccentLight,
            onPrimaryContainer = Ink,
            secondary = Accent,
            onSecondary = Color.White,
            secondaryContainer = AccentLight,
            onSecondaryContainer = Ink,
            background = Bg,
            onBackground = Ink,
            surface = Card,
            onSurface = Ink,
            surfaceVariant = Soft,
            onSurfaceVariant = Muted,
            outline = Line,
            outlineVariant = Line,
            error = Error,
            onError = Color.White
        )
    } else {
        lightColorScheme(
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
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

data class WidgetPalette(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val accentDeep: Color,
    val accentLight: Color
)

fun widgetPalette(preset: AppThemePreset): WidgetPalette = when (preset) {
    AppThemePreset.SAGE -> WidgetPalette(
        bg = Color(0xFFF4F1EA),
        ink = Color(0xFF382218),
        muted = Color(0xFF756C63),
        accentDeep = Color(0xFF4F7A38),
        accentLight = Color(0xFFE4EEDF)
    )
    AppThemePreset.BLUE -> WidgetPalette(
        bg = Color(0xFFF5F8FC),
        ink = Color(0xFF1E2D3D),
        muted = Color(0xFF6B7C8F),
        accentDeep = Color(0xFF2B5B8C),
        accentLight = Color(0xFFE3EFFA)
    )
    AppThemePreset.DARK -> WidgetPalette(
        bg = Color(0xFF141816),
        ink = Color(0xFFE7EDE8),
        muted = Color(0xFF9AA89D),
        accentDeep = Color(0xFF5D9369),
        accentLight = Color(0xFF24382B)
    )
}
