package com.dentrain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Две акцентные темы: голубо-синяя и красная. Фон (светлый/тёмный) выбирается независимо. */
object AccentThemes {
    const val BLUE = "BLUE"
    const val RED = "RED"
}

/** Градиент акцента — используется в кнопках, активных вкладках и выделенных карточках. */
@Immutable
class AccentGradient(val start: Color, val end: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(start, end))
}

private val primary = DenTrainPalette.NeonGreen
private val defaultGradient = AccentGradient(primary, primary)

val LocalGradient = staticCompositionLocalOf { defaultGradient }
val LocalGlassTheme = staticCompositionLocalOf { true }
val LocalSplashActive = staticCompositionLocalOf { false }
val LocalHeaderLogoVisible = staticCompositionLocalOf { false }

private val colorScheme = darkColorScheme(
    primary = primary, onPrimary = Color.Black,
    primaryContainer = primary.copy(alpha = 0.20f), onPrimaryContainer = primary,
    background = Color(0xFF0D0F13), onBackground = Color(0xFFF0F4F9),
    surface = Color(0xFF1A1D24), onSurface = Color(0xFFF0F4F9),
    surfaceVariant = Color(0xFF222938), onSurfaceVariant = Color(0xFFC0C9D6),
    outline = Color(0x40FFFFFF), outlineVariant = Color(0x25FFFFFF),
    surfaceContainer = Color(0xFF1A1D24), surfaceContainerHigh = Color(0xFF252D3E),
    surfaceContainerHighest = Color(0xFF2C3549), surfaceContainerLow = Color(0xFF12151A),
    error = Color(0xFFFF6B66)
)

@Composable
fun DenTrainTheme(
    accent: String = AccentThemes.BLUE, // Оставляем для совместимости
    dark: Boolean = true, // Оставляем для совместимости, но всегда используем темную
    glass: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalGradient provides defaultGradient,
        LocalGlassTheme provides glass
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
