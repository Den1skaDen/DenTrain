package com.dentrain.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

val LocalGradient = staticCompositionLocalOf { AccentGradient(Color(0xFF2196F3), Color(0xFF0D47A1)) }
val LocalGlassTheme = staticCompositionLocalOf { true }
val LocalSplashActive = staticCompositionLocalOf { false }
val LocalHeaderLogoVisible = staticCompositionLocalOf { false }



@Composable
fun DenTrainTheme(
    accent: String = AccentThemes.BLUE,
    dark: Boolean = true,
    glass: Boolean = true,
    content: @Composable () -> Unit
) {
    val red = accent == AccentThemes.RED
    val gradient = if (red) AccentGradient(Color(0xFFEF5350), Color(0xFFB71C1C))
    else AccentGradient(Color(0xFF2196F3), Color(0xFF0D47A1))

    val primary = when {
        red && dark -> Color(0xFFFF6B66)
        red -> Color(0xFFD32F2F)
        dark -> Color(0xFF4DA3F5)
        else -> Color(0xFF1565C0)
    }

    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.30f), onPrimaryContainer = Color.White,
        background = Color(0xFF0A0D14), onBackground = Color(0xFFF0F4F9),
        surface = Color(0xFF161B26), onSurface = Color(0xFFF0F4F9),
        surfaceVariant = Color(0xFF222938), onSurfaceVariant = Color(0xFFC0C9D6),
        outline = Color(0x40FFFFFF), outlineVariant = Color(0x25FFFFFF),
        surfaceContainer = Color(0xFF161B26), surfaceContainerHigh = Color(0xFF1E2433),
        surfaceContainerHighest = Color(0xFF252D3E), surfaceContainerLow = Color(0xFF121620),
        error = Color(0xFFFF6B66)
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.16f), onPrimaryContainer = Color(0xFF10151C),
        background = Color(0xFFF0F4FA), onBackground = Color(0xFF10151C),
        surface = Color.White, onSurface = Color(0xFF10151C),
        surfaceVariant = Color(0xFFE4EAF4), onSurfaceVariant = Color(0xFF4A5568),
        outline = Color(0xFFBCC6D4), outlineVariant = Color(0xFFD6DFEB),
        surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color(0xFFE4EAF4), surfaceContainerLow = Color(0xFFF4F7FB),
        error = Color(0xFFC62828)
    )

    CompositionLocalProvider(
        LocalGradient provides gradient,
        LocalGlassTheme provides glass
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
