package com.dentrain

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.dentrain.ui.settings.ProfileSettingsViewModel
import com.dentrain.data.SettingsEntity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentrain.ui.components.appViewModel
import com.dentrain.ui.navigation.AppNav
// Removed SettingsViewModel import
import com.dentrain.ui.theme.AccentThemes
import com.dentrain.ui.theme.LocalHeaderLogoVisible
import com.dentrain.ui.theme.LocalSplashActive
import com.dentrain.ui.theme.DenTrainTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm = appViewModel { ProfileSettingsViewModel(it.repository) }
            val settingsState = vm.settings?.collectAsStateWithLifecycle()
            val settings = settingsState?.value ?: SettingsEntity()

            var showSplash by remember { mutableStateOf(true) }
            var isHeaderLogoVisible by remember { mutableStateOf(false) }
            val mainAppAlpha = remember { Animatable(0f) }

            val accent = settings.accent
            val dark = settings.darkTheme
            val glass = settings.glassTheme

            DenTrainTheme(accent = accent, dark = dark, glass = glass) {
                CompositionLocalProvider(
                    LocalSplashActive provides showSplash,
                    LocalHeaderLogoVisible provides isHeaderLogoVisible
                ) {
                    val view = LocalView.current
                    SideEffect {
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !dark
                        controller.isAppearanceLightNavigationBars = !dark
                        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(WindowInsetsCompat.Type.navigationBars())
                    }

                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.graphicsLayer { alpha = mainAppAlpha.value }) {
                            AppNav()
                        }

                        if (showSplash) {
                            SplashScreenOverlay(
                                mainAppAlpha = mainAppAlpha,
                                onHeaderLogoArrived = { isHeaderLogoVisible = true },
                                onFinished = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Кинематографичная анимированная заставка старта приложения с вылетом отдельных букв D.png и T.png.
 * Вся анимация сглажена шёлковой кривой CubicBezierEasing (как в iOS/macOS).
 */
@Composable
private fun SplashScreenOverlay(
    mainAppAlpha: Animatable<Float, *>,
    onHeaderLogoArrived: () -> Unit,
    onFinished: () -> Unit
) {
    val density = LocalDensity.current

    // ================= НАСТРОЙКИ ДЛЯ САМОСТОЯТЕЛЬНОГО ИЗМЕНЕНИЯ =================
    val initialLogoSize = 120.dp               // Исходный размер букв D и T в центре
    val letterOverlapDp = (-120).dp            // Сближение букв D и T
    val targetHeaderYOffsetDp = -375.dp        // Смещение по высоте вверх к шапке
    val targetLogoScale = 0.53f                // Итоговый размер логотипа при прилёте в шапку

    val slideDurationMs = 1500                 // Скорость съезда букв D и T (миллисекунды)
    val riseDurationMs = 1000                  // Скорость подъёма логотипа в шапку (миллисекунды)

    // 🔥 НАСТРОЙКИ ДЛЯ УПРАВЛЕНИЯ ПРОЯВЛЕНИЕМ ГЛАВНОГО ЭКРАНА (МЕНЮ) С 0-й МИЛЛИСЕКУНДЫ:
    val mainAppFadeStartDelayMs = 1500L        // 👈 Задержка старта проявления Главного экрана от МОМЕНТА ОТКРЫТИЯ
    val mainAppFadeDurationMs = 800            // 👈 Скорость/длительность проявления главного экрана (миллисекунды)
    // =============================================================================

    val smoothEasing = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.0f)

    val leftY = remember { Animatable(1000f) }
    val rightY = remember { Animatable(-1000f) }

    val logoScale = remember { Animatable(1.4f) }
    val logoY = remember { Animatable(0f) }
    val splashLogoAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // 🔥 Буфер 32 мс для завершения первого пасса верстки Compose перед началом движения букв (убирает начальный фриз!)
        withFrameNanos { }
        delay(32)

        // НАСТРАИВАЕМЫЙ СТАРТ ПРОЯВЛЕНИЯ ГЛАВНОГО ЭКРАНА:
        launch {
            if (mainAppFadeStartDelayMs > 0) {
                delay(mainAppFadeStartDelayMs)
            }
            mainAppAlpha.animateTo(1f, tween(mainAppFadeDurationMs, easing = smoothEasing))
        }

        // Шаг 1: Буквы D и T плавно съезжаются к центру на черном фоне
        launch { leftY.animateTo(0f, tween(slideDurationMs, easing = smoothEasing)) }
        rightY.animateTo(0f, tween(slideDurationMs, easing = smoothEasing))

        delay(180) // Мягкая пауза при стыковке

        // Шаг 2: Подъём логотипа к шапке
        val targetYPx = with(density) { targetHeaderYOffsetDp.toPx() }
        launch { logoScale.animateTo(targetLogoScale, tween(riseDurationMs, easing = smoothEasing)) }
        logoY.animateTo(targetYPx, tween(riseDurationMs, easing = smoothEasing))

        // ТОЧНЫЙ ТРИГГЕР ПОЯВЛЕНИЯ ЛОГОТИПА В ШАПКЕ:
        onHeaderLogoArrived()
        splashLogoAlpha.animateTo(0f, tween(200))

        // Шаг 3: Финиш заставки
        onFinished()
    }

    val currentBgAlpha = (1f - mainAppAlpha.value).coerceIn(0f, 1f)

    if (currentBgAlpha > 0f || splashLogoAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = currentBgAlpha)),
            contentAlignment = Alignment.Center
        ) {
            if (splashLogoAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .alpha(splashLogoAlpha.value)
                        .graphicsLayer {
                            translationY = logoY.value
                            scaleX = logoScale.value
                            scaleY = logoScale.value
                        }
                        .size(initialLogoSize),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(letterOverlapDp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_letter_d),
                            contentDescription = "Буква D",
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    translationY = leftY.value
                                    compositingStrategy = CompositingStrategy.Offscreen
                                },
                            contentScale = ContentScale.Fit
                        )

                        Image(
                            painter = painterResource(id = R.drawable.logo_letter_t),
                            contentDescription = "Буква T",
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    translationY = rightY.value
                                    compositingStrategy = CompositingStrategy.Offscreen
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}
