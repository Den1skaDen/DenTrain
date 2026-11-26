package com.dentrain.ui.auth

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dentrain.R
import kotlinx.coroutines.delay

internal val AuthBackground = Color(0xFF07080A)
internal const val AuthLogoTransitionMillis = 780
internal const val AuthFormEnterMillis = 540
internal val AuthPanel = Color(0xFF15181C)
internal val AuthSecondaryText = Color(0xFFB6BBC4)
private val TrackBlue = Color(0xFF4388FF)
private val TrackCyan = Color(0xFF6BDFFF)
private val TrackGray = Color(0xFF777C84)
private val FeedbackModifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }

@Composable
internal fun EmailVerificationScreen(
    email: String,
    verified: Boolean,
    sendingEmail: Boolean,
    emailResent: Boolean,
    verificationError: String?,
    sendError: String?,
    resendDeadline: Long,
    complete: Boolean,
    returning: Boolean,
    onFinished: () -> Unit,
    logoModifier: Modifier = Modifier,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val emailProgress = remember { Animatable(0f) }
    val verificationProgress = remember { Animatable(0f) }
    val completionProgress = remember { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) { entered = true }
    LaunchedEffect(verified, complete) {
        if (emailProgress.value < 1f) emailProgress.animateTo(1f, tween(460))
        if (verified && verificationProgress.value < 1f) {
            verificationProgress.animateTo(1f, tween(460))
        }
        if (complete) {
            if (completionProgress.value < 1f) completionProgress.animateTo(1f, tween(460))
            if (!leaving) {
                delay(1_000)
                leaving = true
            }
            delay(420)
            finish()
        }
    }

    AuthFlowLayout(
        showContent = entered && !leaving && !returning,
        showWallpaper = !leaving,
        logoCentered = entered && !returning,
        animatedLogo = true,
        logoModifier = logoModifier,
        onBack = onBack,
        backEnabled = !sendingEmail && !verified
    ) {
        when {
            complete -> AuthFlowTitle("Регистрация ", "завершена")
            verified -> AuthFlowTitle("Почта ", "подтверждена")
            else -> AuthFlowTitle("Подтвердите ", "почту")
        }
        if (!verified) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Перейдите по ссылке в письме и вернитесь в DenTrain.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        RegistrationTrack(
            email = email,
            verified = verified,
            complete = complete,
            emailProgress = emailProgress,
            verificationProgress = verificationProgress,
            completionProgress = completionProgress
        )
        if (!verified) {
            ResendVerificationButton(sendingEmail, resendDeadline, onResend)
        }
        listOfNotNull(sendError.takeUnless { verified }, verificationError).forEach { message ->
            Text(
                message,
                FeedbackModifier.padding(top = 4.dp),
                color = Color(0xFFFFA6A2),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (!verified && emailResent && sendError == null) {
            Text(
                "Письмо отправлено ещё раз",
                FeedbackModifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun AuthWallpaper() {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = R.drawable.bg_athlete,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.4f),
                    0.4f to Color.Transparent,
                    0.7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.6f)
                )
            )
        )
    }
}

@Composable
internal fun AuthFlowLayout(
    showContent: Boolean,
    showWallpaper: Boolean = showContent,
    logoCentered: Boolean = true,
    animatedLogo: Boolean = false,
    logoModifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val logoProgress by animateFloatAsState(
        if (logoCentered) 1f else 0f,
        tween(AuthLogoTransitionMillis, easing = FastOutSlowInEasing),
        label = "Logo position"
    )

    Box(Modifier.fillMaxSize().background(AuthBackground)) {
        AnimatedVisibility(visible = showWallpaper, enter = fadeIn(tween(380)), exit = fadeOut(tween(300))) {
            AuthWallpaper()
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 24.dp)) {
                Image(
                    painter = painterResource(R.drawable.logo_dt),
                    contentDescription = "DenTrain",
                    modifier = Modifier
                        .align(BiasAlignment(if (animatedLogo) logoProgress - 1f else 0f, -1f))
                        .padding(top = if (animatedLogo) 16.dp + 22.dp * logoProgress else 38.dp)
                        .height(52.dp)
                        .padding(bottom = 4.dp)
                        .then(logoModifier),
                    contentScale = ContentScale.Fit
                )
                if (onBack != null) {
                    AuthFlowBackButton(
                        visible = showContent,
                        enabled = backEnabled,
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = showContent,
                modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 480.dp),
                enter = fadeIn(tween(AuthFormEnterMillis)) + slideInVertically(tween(AuthFormEnterMillis)) { it / 12 },
                exit = fadeOut(tween(420)) + slideOutVertically(tween(420)) { it / 12 }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun AuthFlowBackButton(visible: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(250)), exit = fadeOut(tween(280))) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.42f)
            )
        }
    }
}

@Composable
internal fun AuthFlowTitle(first: String, accent: String) {
    Text(
        text = buildAnnotatedString {
            append(first)
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(accent) }
        },
        modifier = Modifier.semantics { heading() },
        color = Color.White,
        style = MaterialTheme.typography.headlineLarge.copy(
            fontSize = 35.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.8).sp
        )
    )
}

@Composable
private fun ResendVerificationButton(
    sendingEmail: Boolean,
    resendDeadline: Long,
    onResend: () -> Unit
) {
    val secondsLeft by produceState(0L, resendDeadline) {
        do {
            val remaining = (resendDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            value = (remaining + 999L) / 1_000L
            if (remaining > 0L) delay((remaining - 1L) % 1_000L + 1L)
        } while (remaining > 0L)
    }
    TextButton(
        onClick = onResend,
        enabled = !sendingEmail && secondsLeft == 0L,
        modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = AuthSecondaryText
        )
    ) {
        Text(
            when {
                sendingEmail -> "Отправляем письмо…"
                secondsLeft > 0L -> "Повторить через ${secondsLeft} сек."
                else -> "Отправить письмо ещё раз"
            },
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RegistrationTrack(
    email: String,
    verified: Boolean,
    complete: Boolean,
    emailProgress: Animatable<Float, *>,
    verificationProgress: Animatable<Float, *>,
    completionProgress: Animatable<Float, *>
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        RegistrationStage(email, "Пройдено", { emailProgress.value }) {
            Icon(
                Icons.Filled.Email,
                contentDescription = null,
                tint = TrackCyan,
                modifier = Modifier.size(20.dp)
            )
        }
        RegistrationConnector { emailProgress.value }
        RegistrationStage(
            if (verified) "Почта подтверждена" else "Ожидаем подтверждения",
            if (verified) "Пройдено" else "Текущий этап",
            { verificationProgress.value }
        ) {
            if (!verified) {
                CircularProgressIndicator(Modifier.size(16.dp), color = TrackCyan, strokeWidth = 2.dp)
            }
        }
        RegistrationConnector { verificationProgress.value }
        RegistrationStage(
            if (complete) "Регистрация завершена" else "Завершение регистрации",
            when {
                complete -> "Пройдено"
                verified -> "Текущий этап"
                else -> "Ожидает"
            },
            { completionProgress.value }
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun RegistrationConnector(progress: () -> Float) {
    Spacer(
        Modifier.width(10.dp).height(12.dp).drawBehind {
            val x = center.x
            val endY = size.height
            drawLine(TrackGray, Offset(x, 0f), Offset(x, endY), 1.5.dp.toPx())
            val amount = progress()
            if (amount > 0f) {
                drawLine(TrackCyan, Offset(x, 0f), Offset(x, endY * amount), 1.5.dp.toPx())
            }
        }
    )
}

@Composable
private fun RegistrationStage(
    label: String,
    status: String,
    progress: () -> Float,
    leading: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(AuthBackground.copy(alpha = 0.88f), shape)
            .drawWithStageOutline(progress)
            .semantics(mergeDescendants = true) { stateDescription = status }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Text(
            label,
            color = if (status == "Ожидает") AuthSecondaryText else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun Modifier.drawWithStageOutline(progress: () -> Float): Modifier = drawWithCache {
    val stroke = 1.75.dp.toPx()
    val inset = stroke / 2f
    val right = size.width - inset
    val bottom = size.height - inset
    val middle = size.width / 2f
    val radius = (12.dp.toPx() - inset).coerceIn(0f, (middle - inset).coerceAtLeast(0f))
    val strokes = listOf(inset to 1f, right to -1f).map { (edge, direction) ->
        val outline = Path().apply {
            moveTo(middle, inset)
            lineTo(edge + direction * radius, inset)
            quadraticTo(edge, inset, edge, inset + radius)
            lineTo(edge, bottom - radius)
            quadraticTo(edge, bottom, edge + direction * radius, bottom)
            lineTo(middle, bottom)
        }
        PathMeasure().apply { setPath(outline, false) }
    }
    val brush = Brush.horizontalGradient(listOf(TrackBlue, TrackCyan))
    val segment = Path()
    onDrawBehind {
        val amount = progress()
        if (amount < 1f) {
            drawRoundRect(
                color = TrackGray.copy(alpha = 1f - amount),
                topLeft = Offset(inset, inset),
                size = Size(right - inset, bottom - inset),
                cornerRadius = CornerRadius(radius),
                style = Stroke(1.dp.toPx())
            )
        }
        if (amount > 0f) {
            for (path in strokes) {
                segment.reset()
                path.getSegment(0f, path.length * amount, segment)
                drawPath(segment, brush, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}
