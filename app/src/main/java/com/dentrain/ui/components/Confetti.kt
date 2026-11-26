package com.dentrain.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dentrain.R
import com.dentrain.data.RecordEntity
import com.dentrain.ui.theme.DenTrainPalette
import com.dentrain.util.Fmt
import java.math.BigDecimal
import kotlin.random.Random

private data class Particle(
    val xRatio: Float,
    val initialYRatio: Float,
    val speedY: Float,
    val speedX: Float,
    val sizePx: Float,
    val color: Color
)

/**
 * Конфетти при выполнении плана тренировки.
 */
@Composable
fun ConfettiOverlay(
    visible: Boolean,
    onFinished: () -> Unit = {}
) {
    if (!visible) return

    val progress = remember { Animatable(0f) }
    val particles = remember {
        val colors = listOf(
            Color(0xFFFFD700), Color(0xFFFF4081), Color(0xFF00E676),
            Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFFFF7043)
        )
        List(60) {
            Particle(
                xRatio = Random.nextFloat(),
                initialYRatio = -0.1f - Random.nextFloat() * 0.3f,
                speedY = 0.8f + Random.nextFloat() * 0.7f,
                speedX = (Random.nextFloat() - 0.5f) * 0.3f,
                sizePx = 14f + Random.nextFloat() * 20f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(2500, easing = LinearEasing))
        onFinished()
    }

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress.value

        particles.forEach { p ->
            val x = (p.xRatio + p.speedX * t) * w
            val y = (p.initialYRatio + p.speedY * t) * h
            val alpha = (1f - (t - 0.75f) / 0.25f).coerceIn(0f, 1f)

            if (y in 0f..h) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(x, y),
                    size = Size(p.sizePx, p.sizePx)
                )
            }
        }
    }
}

private val RecordGold = Color(0xFFEBC675)

private data class RecordParticle(
    val fromLeft: Boolean,
    val speedX: Float,
    val speedY: Float,
    val spin: Float,
    val size: Float,
    val color: Color
)

@Composable
private fun RecordConfetti(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val random = Random(31)
        val colors = listOf(RecordGold, Color(0xFFFFE2A6), Color(0xFFAB8044), DenTrainPalette.Lime)
        List(76) { index ->
            RecordParticle(
                fromLeft = index % 2 == 0,
                speedX = random.nextFloat() * 0.42f + 0.1f,
                speedY = random.nextFloat() * 0.64f + 0.25f,
                spin = random.nextFloat() * 560f - 280f,
                size = random.nextFloat() * 5f + 3f,
                color = colors[random.nextInt(colors.size)]
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2500, easing = LinearEasing))
    }
    Canvas(modifier) {
        val time = progress.value
        if (time <= 0f || time >= 1f) return@Canvas
        val opacity = minOf(time / 0.04f, 1f) * 0.87f
        particles.forEach { particle ->
            val spread = size.width * (0.015f + particle.speedX * time)
            val center = Offset(
                x = if (particle.fromLeft) spread else size.width - spread,
                y = size.height * (0.18f - particle.speedY * time + (0.9f + particle.speedY) * time * time)
            )
            val width = particle.size.dp.toPx()
            rotate(particle.spin * time, pivot = center) {
                drawRect(
                    color = particle.color.copy(alpha = opacity),
                    topLeft = center - Offset(width / 2, width),
                    size = Size(width, width * 2)
                )
            }
        }
    }
}

@Composable
fun RecordCelebrationDialog(
    exerciseName: String,
    weightText: String,
    repsText: String,
    previous: RecordEntity? = null,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            RecordCelebrationContent(exerciseName, weightText, repsText, previous, onDismiss)
            RecordConfetti(Modifier.matchParentSize())
        }
    }
}

@Composable
internal fun RecordCelebrationContent(
    exerciseName: String,
    weightText: String,
    repsText: String,
    previous: RecordEntity?,
    onDismiss: () -> Unit
) {
    val improvement = previous?.let { recordImprovement(weightText, repsText, it) }
    Column(
        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = DenTrainPalette.Panel,
            contentColor = Color.White,
            border = BorderStroke(1.dp, RecordGold)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(RecordGold.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_lucide_trophy),
                            contentDescription = null,
                            tint = RecordGold,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    Text(
                        "Личный рекорд",
                        modifier = Modifier.weight(1f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier.size(42.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.logo_dt),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        exerciseName,
                        color = DenTrainPalette.MutedText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${weightText} кг × $repsText",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                        lineHeight = 39.sp
                    )
                }

                if (previous != null && improvement != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val tile = Modifier.weight(1f)
                            .background(DenTrainPalette.RaisedPanel, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                        Column(tile, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Прошлый рекорд", color = DenTrainPalette.MutedText, fontSize = 12.sp)
                            Text(
                                "${Fmt.weight(previous.weight)} кг × ${previous.reps}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(tile, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Прогресс", color = DenTrainPalette.MutedText, fontSize = 12.sp)
                            improvement.forEach { delta ->
                                Text(delta, color = DenTrainPalette.Lime, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DenTrainPalette.Lime,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Продолжить", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun recordImprovement(weightText: String, repsText: String, previous: RecordEntity): List<String> {
    val weight = weightText.replace(',', '.').toBigDecimal()
    val weightGain = weight - BigDecimal.valueOf(previous.weight)
    val repGain = repsText.toInt() - previous.reps
    return buildList {
        if (weightGain.signum() > 0) {
            add("+${weightGain.stripTrailingZeros().toPlainString().replace('.', ',')} кг")
        }
        if (repGain > 0) {
            add("+$repGain ${Fmt.plural(repGain, "повторение", "повторения", "повторений")}")
        }
    }
}
