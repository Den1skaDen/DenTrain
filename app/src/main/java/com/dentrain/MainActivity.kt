package com.dentrain

import com.dentrain.ui.components.ConfirmDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentrain.data.SettingsEntity
import com.dentrain.ui.navigation.AppNav
import com.dentrain.ui.theme.LocalHeaderLogoVisible
import com.dentrain.ui.theme.LocalSplashActive
import com.dentrain.ui.theme.DenTrainTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startup = ViewModelProvider(this)[StartupViewModel::class.java]
        val playSplash = !startup.splashStarted
        startup.splashStarted = true
        val signedOutAtLaunch = FirebaseAuth.getInstance().currentUser == null

        setContent {
            val app = application as DenTrainApp
            val account = app.container
            val settings = if (account.uid == null) {
                SettingsEntity()
            } else {
                account.screenCache.settings.collectAsStateWithLifecycle().value
            }
            var showSplash by remember { mutableStateOf(playSplash) }
            var loginLogoBounds by remember { mutableStateOf<Rect?>(null) }
            val moveToLoginLogo = showSplash && signedOutAtLaunch
            val loginLogoModifier = if (moveToLoginLogo) {
                Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    if (bounds.width > 0f && bounds.height > 0f) loginLogoBounds = bounds
                }.alpha(0f)
            } else {
                Modifier
            }
            val uid = account.uid
            LaunchedEffect(uid) {
                if (startup.startAtHome == null) {
                    if (playSplash) withFrameNanos { }
                    startup.startAtHome = hasCompletedProfile(uid)
                }
            }
            val initialHome = startup.startAtHome

            val accent = settings.accent
            val dark = settings.darkTheme
            val glass = settings.glassTheme

            DenTrainTheme(accent = accent, dark = dark, glass = glass) {
                CompositionLocalProvider(
                    LocalSplashActive provides showSplash,
                    LocalHeaderLogoVisible provides true
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
                        if (initialHome != null) {
                            if (app.needsDiaryChoice && !showSplash) {
                                ConfirmDialog(
                                    title = "Дневник с этого телефона",
                                    text = "У старого дневника не указан владелец. Добавить его тренировки в аккаунт " +
                                        (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "") + "?",
                                    confirmText = "Добавить",
                                    destructive = false,
                                    onConfirm = { app.chooseLegacyDiary(true) },
                                    dismissText = "Начать отдельно",
                                    onSecondary = { app.chooseLegacyDiary(false) },
                                    onDismiss = {},
                                    properties = androidx.compose.ui.window.DialogProperties(
                                        dismissOnBackPress = false,
                                        dismissOnClickOutside = false
                                    )
                                )
                            } else if (!app.needsDiaryChoice) {
                                AppNav(startAtHome = initialHome, loginLogoModifier = loginLogoModifier)
                            }
                        }

                        if (showSplash) {
                            SplashScreenOverlay(
                                contentReady = initialHome != null,
                                moveToLoginLogo = moveToLoginLogo,
                                loginLogoBounds = loginLogoBounds,
                                onFinished = { showSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal class StartupViewModel : ViewModel() {
    var splashStarted = false
    var startAtHome by mutableStateOf<Boolean?>(null)
}

private suspend fun hasCompletedProfile(uid: String?): Boolean {
    val user = FirebaseAuth.getInstance().currentUser ?: return false
    if (user.uid != uid || !user.isEmailVerified) return false

    val profile = FirebaseFirestore.getInstance().collection("profiles").document(user.uid)
    return withTimeoutOrNull(400) {
        try {
            profile.get(Source.CACHE).await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    } ?: false
}

@Composable
private fun SplashScreenOverlay(
    contentReady: Boolean,
    moveToLoginLogo: Boolean,
    loginLogoBounds: Rect?,
    onFinished: () -> Unit
) {
    val ready by rememberUpdatedState(contentReady)
    val logoBounds by rememberUpdatedState(loginLogoBounds)
    var destination by remember { mutableStateOf<Rect?>(null) }
    val entrance = remember { Animatable(1f) }
    val dissolve = remember { Animatable(0f) }
    val entranceEasing = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }
    val riseEasing = remember { CubicBezierEasing(0.45f, 0f, 0.4f, 1f) }
    val letterD = painterResource(R.drawable.logo_letter_d)

    LaunchedEffect(Unit) {
        withFrameNanos { }
        entrance.animateTo(0f, tween(380, easing = entranceEasing))
        snapshotFlow { ready }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        if (moveToLoginLogo) {
            destination = withTimeoutOrNull(160) { snapshotFlow { logoBounds }.first { it != null } }
        }
        val exitAnimation = if (destination != null) tween<Float>(280, easing = riseEasing) else tween(180)
        dissolve.animateTo(1f, exitAnimation)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val centerX = with(density) { maxWidth.toPx() / 2 }
        val centerY = with(density) { maxHeight.toPx() / 2 }
        val travel = centerY + with(density) { 84.dp.toPx() }

        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = 1f - dissolve.value) }
        )

        Box(
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    val progress = dissolve.value
                    val target = destination
                    val scale = if (target != null) {
                        val source = letterD.intrinsicSize
                        val targetSize = Size(target.width, target.height)
                        val targetScale = ContentScale.Fit.computeScaleFactor(source, targetSize).scaleX
                        val fullScale = ContentScale.Fit.computeScaleFactor(source, size).scaleX
                        translationX = (target.center.x - centerX) * progress
                        translationY = (target.center.y - centerY) * progress
                        1f + (targetScale / fullScale - 1f) * progress
                    } else {
                        alpha = (1f - progress) * (1f - progress)
                        1f + 0.08f * progress
                    }
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = letterD,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = travel * entrance.value
                },
                contentScale = ContentScale.Fit
            )
            Image(
                painter = painterResource(R.drawable.logo_letter_t),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = -travel * entrance.value
                },
                contentScale = ContentScale.Fit
            )
        }
    }
}
