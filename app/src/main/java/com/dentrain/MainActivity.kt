package com.dentrain

import com.dentrain.ui.components.ConfirmDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dentrain.data.SettingsEntity
import com.dentrain.timer.WorkoutReminderScheduler
import com.dentrain.ui.auth.PasswordRecoveryScreen
import com.dentrain.ui.auth.PasswordRecoveryViewModel
import com.dentrain.ui.auth.needsGooglePassword
import com.dentrain.ui.navigation.AppNav
import com.dentrain.ui.theme.LocalHeaderLogoVisible
import com.dentrain.ui.theme.LocalSplashActive
import com.dentrain.ui.theme.DenTrainTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private val passwordRecovery by lazy { ViewModelProvider(this)[PasswordRecoveryViewModel::class.java] }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openPasswordRecoveryLink(intent)
    }

    private fun openPasswordRecoveryLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let(passwordRecovery::openLink)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { WorkoutReminderScheduler.sync(this@MainActivity) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        val startup = ViewModelProvider(this)[StartupViewModel::class.java]
        if (!startup.splashStarted) openPasswordRecoveryLink(intent)
        val playSplash = !startup.splashStarted && !passwordRecovery.active
        startup.splashStarted = true
        val signedOutAtLaunch = FirebaseAuth.getInstance().currentUser == null

        setContent {
            val app = application as DenTrainApp
            val account = app.container
            var splashJoined by remember { mutableStateOf(!playSplash) }
            val settings = if (account.uid == null || !splashJoined) {
                SettingsEntity()
            } else {
                account.screenCache.settings.collectAsStateWithLifecycle().value
            }
            var showSplash by remember { mutableStateOf(playSplash) }
            var loginLogoBounds by remember { mutableStateOf<Rect?>(null) }
            var returnToLogin by remember { mutableStateOf(signedOutAtLaunch) }
            var authTransition by remember { mutableStateOf<AuthLogoTransition?>(null) }
            var recoveryReturnSource by remember { mutableStateOf<Rect?>(null) }
            val navState = rememberSaveableStateHolder()
            val moveToLoginLogo = showSplash && returnToLogin
            val uid = account.uid
            LaunchedEffect(uid, splashJoined) {
                if (startup.startAtHome == null && splashJoined) {
                    startup.startAtHome = hasCompletedProfile(uid)
                }
            }
            val initialHome = startup.startAtHome

            val accent = settings.accent
            val dark = settings.darkTheme
            val glass = settings.glassTheme

            DenTrainTheme(accent = accent, dark = dark, glass = glass) {
                CompositionLocalProvider(
                    LocalSplashActive provides (showSplash || authTransition != null || passwordRecovery.active),
                    LocalHeaderLogoVisible provides true
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        if (initialHome != null && splashJoined && (!passwordRecovery.active || recoveryReturnSource != null)) {
                            navState.SaveableStateProvider(startup.navigationGeneration) {
                                AppNav(
                                    startAtHome = initialHome && !returnToLogin,
                                    showLoginLogo = !moveToLoginLogo && recoveryReturnSource == null,
                                    onLoginLogoPositioned = { loginLogoBounds = it },
                                    onAuthTransition = { bounds, action ->
                                        if (authTransition == null) authTransition = AuthLogoTransition(bounds, action)
                                    },
                                    onSignedOut = {
                                        loginLogoBounds = null
                                        returnToLogin = true
                                        splashJoined = false
                                        showSplash = true
                                    },
                                    blockMain = app.needsDiaryChoice
                                )
                            }
                            if (app.needsDiaryChoice && !showSplash && authTransition == null) {
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
                            }
                        }

                        if (showSplash && !passwordRecovery.active) {
                            SplashScreenOverlay(
                                contentReady = initialHome != null,
                                moveToLoginLogo = moveToLoginLogo,
                                loginLogoBounds = loginLogoBounds,
                                onJoined = { splashJoined = true },
                                onFinished = { showSplash = false }
                            )
                        }
                        if (passwordRecovery.active && recoveryReturnSource == null) key(passwordRecovery.attempt) {
                            var recoveryLogoBounds by remember { mutableStateOf<Rect?>(null) }
                            var showRecoverySplash by rememberSaveable { mutableStateOf(true) }
                            LaunchedEffect(Unit) {
                                authTransition = null
                                showSplash = false
                                splashJoined = true
                            }
                            PasswordRecoveryScreen(
                                vm = passwordRecovery,
                                logoModifier = Modifier.onGloballyPositioned {
                                    recoveryLogoBounds = it.boundsInRoot()
                                }.alpha(if (showRecoverySplash || authTransition != null) 0f else 1f),
                                onClose = {
                                    if (authTransition == null && !showRecoverySplash) {
                                        returnToLogin = true
                                        loginLogoBounds = null
                                        if (recoveryLogoBounds != null) {
                                            recoveryReturnSource = recoveryLogoBounds
                                        } else {
                                            passwordRecovery.close()
                                        }
                                    }
                                }
                            )
                            if (showRecoverySplash) {
                                SplashScreenOverlay(
                                    contentReady = true,
                                    moveToLoginLogo = true,
                                    loginLogoBounds = recoveryLogoBounds,
                                    onJoined = {},
                                    onFinished = { showRecoverySplash = false },
                                    descendToLogo = true
                                )
                            }
                            LaunchedEffect(passwordRecovery.signedIn, showRecoverySplash) {
                                if (passwordRecovery.signedIn && !showRecoverySplash) {
                                    delay(800)
                                    authTransition = AuthLogoTransition(recoveryLogoBounds) {
                                        navState.removeState(startup.navigationGeneration)
                                        startup.navigationGeneration++
                                        startup.startAtHome = false
                                        returnToLogin = false
                                        passwordRecovery.close()
                                    }
                                }
                            }
                        }
                        recoveryReturnSource?.let { source ->
                            RecoveryReturnOverlay(source, loginLogoBounds) {
                                passwordRecovery.close()
                                recoveryReturnSource = null
                            }
                        }
                        authTransition?.let { AuthLogoOverlay(it) { authTransition = null } }
                    }
                }
            }
        }
    }
}

internal class StartupViewModel : ViewModel() {
    var splashStarted = false
    var startAtHome by mutableStateOf<Boolean?>(null)
    var navigationGeneration by mutableStateOf(0)
}

private suspend fun hasCompletedProfile(uid: String?): Boolean {
    val user = FirebaseAuth.getInstance().currentUser ?: return false
    if (user.uid != uid || !user.isEmailVerified || user.needsGooglePassword()) return false

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
    onJoined: () -> Unit,
    onFinished: () -> Unit,
    descendToLogo: Boolean = false
) {
    val ready by rememberUpdatedState(contentReady)
    val logoBounds by rememberUpdatedState(loginLogoBounds)
    var destination by remember { mutableStateOf<Rect?>(null) }
    var entrance by remember { mutableStateOf(1f) }
    BackHandler {}
    val dissolve = remember { Animatable(0f) }
    val riseEasing = remember { CubicBezierEasing(0.45f, 0f, 0.4f, 1f) }
    val letterD = painterResource(R.drawable.logo_letter_d)

    LaunchedEffect(Unit) {
        var previousFrame = withFrameNanos { it }
        var elapsed = 0L
        val duration = (620_000_000L * (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f)).toLong()
        while (elapsed < duration) {
            val frame = withFrameNanos { it }
            elapsed += (frame - previousFrame).coerceIn(0L, 32_000_000L)
            previousFrame = frame
            entrance = 1f - FastOutSlowInEasing.transform((elapsed.toFloat() / duration).coerceAtMost(1f))
        }
        entrance = 0f
        onJoined()
        snapshotFlow { ready }.first { it }
        withFrameNanos { }
        withFrameNanos { }
        if (moveToLoginLogo) {
            destination = withTimeoutOrNull(160) { snapshotFlow { logoBounds }.first { it != null } }
        }
        val exitAnimation = if (destination != null) tween<Float>(480, easing = riseEasing) else tween(340, easing = FastOutSlowInEasing)
        dissolve.animateTo(1f, exitAnimation)
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().blockTouches(),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val centerX = with(density) { maxWidth.toPx() / 2 }
        val centerY = with(density) { maxHeight.toPx() / 2 }
        val travel = centerY + with(density) { 84.dp.toPx() }
        val startOffsetY = if (descendToLogo) -centerY * 0.7f else 0f

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
                    translationY = startOffsetY
                    val scale = if (target != null) {
                        val source = letterD.intrinsicSize
                        val targetSize = Size(target.width, target.height)
                        val targetScale = ContentScale.Fit.computeScaleFactor(source, targetSize).scaleX
                        val fullScale = ContentScale.Fit.computeScaleFactor(source, size).scaleX
                        translationX = (target.center.x - centerX) * progress
                        translationY += (target.center.y - centerY - startOffsetY) * progress
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
                    translationY = travel * entrance
                },
                contentScale = ContentScale.Fit
            )
            Image(
                painter = painterResource(R.drawable.logo_letter_t),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationY = -travel * entrance
                },
                contentScale = ContentScale.Fit
            )
        }
    }
}

private class AuthLogoTransition(val source: Rect?, val action: suspend () -> Unit)

@Composable
private fun RecoveryReturnOverlay(source: Rect, destination: Rect?, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val target by rememberUpdatedState(destination)
    val logo = painterResource(R.drawable.logo_dt)
    var endBounds by remember { mutableStateOf<Rect?>(null) }
    BackHandler {}
    LaunchedEffect(Unit) {
        endBounds = withTimeoutOrNull(1000) { snapshotFlow { target }.first { it != null } }
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        onFinished()
    }
    BoxWithConstraints(Modifier.fillMaxSize().blockTouches(), contentAlignment = Alignment.Center) {
        val centerX = with(LocalDensity.current) { maxWidth.toPx() / 2f }
        val centerY = with(LocalDensity.current) { maxHeight.toPx() / 2f }
        Image(
            painter = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(168.dp).graphicsLayer {
                val end = endBounds ?: source
                val startScale = ContentScale.Fit.computeScaleFactor(logo.intrinsicSize, Size(source.width, source.height)).scaleX
                val endScale = ContentScale.Fit.computeScaleFactor(logo.intrinsicSize, Size(end.width, end.height)).scaleX
                val baseScale = ContentScale.Fit.computeScaleFactor(logo.intrinsicSize, size).scaleX
                val scale = (startScale + (endScale - startScale) * progress.value) / baseScale
                scaleX = scale
                scaleY = scale
                translationX = source.center.x + (end.center.x - source.center.x) * progress.value - centerX
                translationY = source.center.y + (end.center.y - source.center.y) * progress.value - centerY
            }
        )
    }
}

@Composable
private fun AuthLogoOverlay(transition: AuthLogoTransition, onFinished: () -> Unit) {
    val movement = remember { Animatable(0f) }
    val curtain = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    val logo = painterResource(R.drawable.logo_dt)
    BackHandler {}
    LaunchedEffect(transition) {
        curtain.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
        movement.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
        transition.action()
        repeat(2) { withFrameNanos { } }
        fade.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        onFinished()
    }

    BoxWithConstraints(Modifier.fillMaxSize().blockTouches(), contentAlignment = Alignment.Center) {
        val centerX = with(LocalDensity.current) { maxWidth.toPx() / 2f }
        val centerY = with(LocalDensity.current) { maxHeight.toPx() / 2f }
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Color.Black, alpha = curtain.value * (1f - fade.value))
        })
        Image(
            painter = logo,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(168.dp).graphicsLayer {
                val remaining = 1f - movement.value
                val source = transition.source
                if (source != null) {
                    val startSize = ContentScale.Fit.computeScaleFactor(
                        logo.intrinsicSize, Size(source.width, source.height)
                    ).scaleX
                    val endSize = ContentScale.Fit.computeScaleFactor(logo.intrinsicSize, size).scaleX
                    val scale = 1f + (startSize / endSize - 1f) * remaining
                    scaleX = scale
                    scaleY = scale
                    translationX = (source.center.x - centerX) * remaining
                    translationY = (source.center.y - centerY) * remaining
                }
                alpha = 1f - fade.value
            }
        )
    }
}

private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
