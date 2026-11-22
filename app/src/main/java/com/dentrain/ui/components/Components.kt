package com.dentrain.ui.components

import com.dentrain.ui.theme.DenTrainPalette
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dentrain.DenTrainApp
import com.dentrain.data.SettingsEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.dentrain.data.AdminUserModel
import com.dentrain.data.AssignedExerciseModel
import com.dentrain.data.AssignedWorkoutModel
import com.dentrain.data.ClubModel
import java.time.temporal.ChronoUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.R
import com.dentrain.data.WorkoutWithExercises
import com.dentrain.ui.theme.LocalGlassTheme
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.LocalHeaderLogoVisible
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.dentrain.util.isWorkingPlanComplete
import com.dentrain.util.totalWorkingSets
import com.dentrain.util.completedWorkingSets
import com.dentrain.util.Fmt
import com.dentrain.util.Haptic
import java.time.LocalDate
import kotlin.math.round

/** Фоновые анимированные сфера-светящиеся ореолы для стеклянного UI (Apple Glassmorphism) с кэшированием на GPU. */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isGlass = LocalGlassTheme.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f

    Box(
        modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0C0F17) else Color(0xFFF2F5FB))
            .drawWithCache {
                val w = size.width
                val h = size.height
                val brush1 = Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = if (isDark) 0.35f else 0.18f), Color.Transparent),
                    center = Offset(w * 0.15f, h * 0.12f),
                    radius = w * 0.85f
                )
                val brush2 = Brush.radialGradient(
                    colors = listOf(Color(0xFFAB47BC).copy(alpha = if (isDark) 0.28f else 0.12f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.65f),
                    radius = w * 0.90f
                )
                onDrawBehind {
                    if (isGlass) {
                        drawCircle(brush = brush1, center = Offset(w * 0.15f, h * 0.12f), radius = w * 0.85f)
                        drawCircle(brush = brush2, center = Offset(w * 0.85f, h * 0.65f), radius = w * 0.90f)
                    }
                }
            }
    ) {
        content()
    }
}

/** Главная объёмная 3D кнопка с градиентом и верхним бликом. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val buttonGradient = Brush.verticalGradient(
        listOf(
            LocalGradient.current.start.copy(alpha = 0.95f),
            LocalGradient.current.end
        )
    )

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(elevation = 8.dp, shape = shape, spotColor = LocalGradient.current.start.copy(alpha = 0.5f))
            .clip(shape)
            .background(buttonGradient)
            .border(BorderStroke(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent))), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = Color.White)
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Высококонтрастная матовая стеклянная карточка в стиле Apple Glassmorphism с кэшированием текстур на GPU. */
@Composable
fun ZCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    gradient: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    val isGlass = LocalGlassTheme.current

    val borderStroke = when {
        isGlass && isDark -> BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.40f), Color.White.copy(alpha = 0.08f))))
        isGlass && !isDark -> BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White, Color.White.copy(alpha = 0.5f))))
        isDark -> BorderStroke(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.06f))))
        else -> BorderStroke(1.5.dp, Brush.verticalGradient(listOf(Color.White, Color(0xFFC5D1E0))))
    }

    val cardFill = when {
        gradient -> Modifier.background(
            Brush.verticalGradient(
                listOf(LocalGradient.current.start, LocalGradient.current.end)
            )
        )
        isGlass && isDark -> Modifier.background(Color(0x28FFFFFF)).border(borderStroke, shape)
        isGlass && !isDark -> Modifier.background(Color(0xE6FFFFFF)).border(borderStroke, shape)
        isDark -> Modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF222B3D), Color(0xFF131722))
            )
        ).border(borderStroke, shape)
        else -> Modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFFFFFFFF), Color(0xFFEBF1F9))
            )
        ).border(borderStroke, shape)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (gradient) 12.dp else if (!isGlass) 8.dp else 4.dp,
                shape = shape,
                spotColor = if (isDark) Color.Black else Color(0x33000000)
            )
            .clip(shape)
            .then(cardFill)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp)
    ) {
        Column(content = content)
    }
}

@Composable
internal fun WorkoutListCount(count: Int, label: String, labelColor: Color) {
    val text = remember(count, label, labelColor) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)) {
                append(count.toString())
            }
            append(' ')
            append(label)
        }
    }
    Text(text, color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
    )
}

@Composable
fun BorderedBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(DenTrainPalette.RaisedPanel)
                .border(BorderStroke(1.dp, Color.White), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Компактная шапка с крупным прозрачным логотипом DT строго по центру экрана. */
@Composable
fun ScreenHeader(
    title: String = "",
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isHeaderLogoVisible = LocalHeaderLogoVisible.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (onBack != null) {
            Box(Modifier.align(Alignment.CenterStart)) {
                BorderedBackButton(onClick = onBack)
            }
        }

        Image(
            painter = painterResource(id = R.drawable.logo_dt),
            contentDescription = "Логотип DenTrain",
            modifier = Modifier
                .size(64.dp)
                .alpha(if (isHeaderLogoVisible) 1f else 0f),
            contentScale = ContentScale.Fit
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(title, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (hint != null) Text(
            hint, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall
        )
    }
}


@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) Text(
        message, modifier = modifier, color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium
    )
}

/** Кнопка выбора даты. Дата хранится как epochDay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    epochDay: Long,
    onChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Дата",
    maxEpochDay: Long? = null
) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true },
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DenTrainPalette.RaisedPanel,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, DenTrainPalette.Border)
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = DenTrainPalette.Lime)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${Fmt.shortDate(epochDay)}", color = Color.White, fontWeight = FontWeight.Medium)
    }
    if (show) {
        val selectableDates = remember(maxEpochDay) {
            object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    maxEpochDay == null || Math.floorDiv(utcTimeMillis, 86_400_000L) <= maxEpochDay

                override fun isSelectableYear(year: Int): Boolean =
                    maxEpochDay == null || year <= LocalDate.ofEpochDay(maxEpochDay).year
            }
        }
        val selectedDay = maxEpochDay?.let { epochDay.coerceAtMost(it) } ?: epochDay
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDay * 86_400_000L,
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L)) }
                    show = false
                }) { Text("OK", color = DenTrainPalette.Lime) }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Отмена", color = DenTrainPalette.MutedText) } }
        ) { DatePicker(state = state) }
    }
}

/** Легковесный каркас вложенных экранов без SubcomposeLayout задержек. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                BorderedBackButton(onClick = onBack)
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        GlassBackground(modifier = Modifier.weight(1f)) {
            content(PaddingValues(0.dp))
        }
    }
}

private val workoutProgressCache = mutableMapOf<Long, Float>()

/** Круговой индикатор прогресса выполненных подходов. */
@Composable
fun CircularWorkoutProgress(
    completedSets: Int,
    totalPlannedSets: Int,
    modifier: Modifier = Modifier,
    workoutId: Long = 0L,
    size: Dp = 72.dp,
    strokeWidth: Dp = 6.dp,
    accentColor: Color = DenTrainPalette.Lime
) {
    val targetProgress = remember(completedSets, totalPlannedSets) {
        if (totalPlannedSets > 0) {
            (completedSets.toFloat() / totalPlannedSets.toFloat()).coerceIn(0f, 1f)
        } else if (completedSets > 0) {
            1f
        } else {
            0f
        }
    }

    val targetPercentage = remember(targetProgress) {
        round(targetProgress * 100f).toInt()
    }

    val initialProgress = remember(workoutId) {
        workoutProgressCache[workoutId] ?: targetProgress
    }

    val animatableProgress = remember(workoutId) { Animatable(initialProgress) }

    LaunchedEffect(targetProgress, workoutId) {
        if (animatableProgress.value != targetProgress) {
            animatableProgress.animateTo(
                targetValue = targetProgress,
                animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
            )
        }
        workoutProgressCache[workoutId] = targetProgress
    }

    val animatedProgress = animatableProgress.value
    val percentage = if (animatableProgress.isRunning) {
        if (targetProgress >= initialProgress) {
            round(animatedProgress * 100f).toInt().coerceAtMost(targetPercentage)
        } else {
            round(animatedProgress * 100f).toInt().coerceAtLeast(targetPercentage)
        }
    } else {
        targetPercentage
    }

    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val trackColor = Color(0xFF2C2C34)

    val percentageStyle = TextStyle(
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp,
        color = Color.White
    )
    val percentageSize = rememberTextMeasurer().measure("100%", percentageStyle, maxLines = 1).size
    val ringSize = with(LocalDensity.current) {
        maxOf(size, percentageSize.width.toDp() + (strokeWidth + 4.dp) * 2)
    }
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val arcSize = (this.size.minDimension - strokePx).coerceAtLeast(0f)
            val topLeft = Offset((this.size.width - arcSize) / 2f, (this.size.height - arcSize) / 2f)

            // Track arc
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(arcSize, arcSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        // Percentage text centered rigidly with tabular numbers to prevent position jittering
        Text(
            text = "$percentage%",
            style = percentageStyle,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Карточка тренировки в процессе с круговым индикатором подходов справа (по 2-му фото). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveWorkoutCard(
    workout: WorkoutWithExercises,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accentColor = DenTrainPalette.Lime // Яркий неон/лайм акцент

    val completedSets = remember(workout) { workout.exercises.sumOf { it.completedWorkingSets } }
    val totalSets = remember(workout) { workout.exercises.sumOf { it.totalWorkingSets } }

    val completedExercisesCount = remember(workout) {
        workout.exercises.count { ex ->
            ex.isWorkingPlanComplete
        }
    }
    val totalExercisesCount = workout.exercises.size

    val subtitleText = remember(completedExercisesCount, totalExercisesCount, completedSets, totalSets) {
        if (totalExercisesCount > 0) {
            "$completedExercisesCount из $totalExercisesCount упражнений"
        } else {
            "$completedSets из $totalSets подходов"
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(
                BorderStroke(1.dp, DenTrainPalette.SubtleBorder),
                RoundedCornerShape(16.dp)
            )
            .clickable {
                Haptic.click(context)
                onClick()
            }
    ) {
        val stackProgress = maxWidth < 380.dp && LocalDensity.current.fontScale > 1.3f
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (stackProgress) 16.dp else 32.dp, top = 16.dp, end = 16.dp, bottom = 16.dp)
                .heightIn(min = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = if (stackProgress) 1 else 2
        ) {
            // Основная информация
            Column(
                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Бэдж: В процессе
                Row(
                    modifier = Modifier
                        .offset(x = if (stackProgress) (-8).dp else (-24).dp, y = (-8).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DenTrainPalette.RaisedPanel)
                        .border(BorderStroke(1.dp, DenTrainPalette.Border), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = "В процессе",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Название тренировки
                Text(
                    text = workout.workout.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = if (stackProgress) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Подзаголовок: например "3 из 6 упражнений"
                Text(
                    text = subtitleText,
                    color = DenTrainPalette.MutedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(4.dp))

                // Кнопка Продолжить
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Продолжить",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Круг выполненных подходов справа
            CircularWorkoutProgress(
                completedSets = completedSets,
                totalPlannedSets = totalSets,
                workoutId = workout.workout.id,
                modifier = Modifier.align(Alignment.CenterVertically),
                size = 72.dp,
                strokeWidth = 6.dp,
                accentColor = accentColor
            )
        }
    }
}

fun Modifier.dashedBorder(
    color: Color,
    shape: Shape,
    strokeWidth: Dp = 1.5.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    val strokeWidthPx = strokeWidth.toPx()
    val dashLengthPx = dashLength.toPx()
    val gapLengthPx = gapLength.toPx()
    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLengthPx, gapLengthPx), 0f)
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        color = color,
        style = Stroke(width = strokeWidthPx, pathEffect = pathEffect)
    )
}

/** Строка 7 дней недели в одну линию (Пн..Вс) по фото. */
@Composable
fun WeekCalendarRow(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    completedDates: Set<LocalDate> = emptySet(),
    plannedDates: Set<LocalDate> = emptySet(),
    workoutDates: Set<LocalDate> = emptySet(),
    showSelection: Boolean = true,
    selectionColor: Color = Color(0xFF00B0FF)
) {
    val context = LocalContext.current
    val today = remember { LocalDate.now() }
    val startOfWeek = remember(selectedDate) {
        val anchor = if (selectedDate.month == today.month && selectedDate.year == today.year) today else selectedDate
        anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
    }
    val daysOfWeek = remember(startOfWeek) {
        (0..6).map { startOfWeek.plusDays(it.toLong()) }
    }

    val accentLime = DenTrainPalette.Lime

    val effectiveCompleted = remember(completedDates, workoutDates) {
        if (completedDates.isNotEmpty()) completedDates else workoutDates
    }
    val effectivePlanned = remember(plannedDates) {
        plannedDates
    }

    val baseStyle = LocalTextStyle.current
    val weekdayStyle = baseStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val numberStyle = baseStyle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    val todayStyle = baseStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val weekdaySizes = Fmt.dayShort.map { measurer.measure(it, weekdayStyle, maxLines = 1).size }
    val numberSizes = daysOfWeek.map { measurer.measure(it.dayOfMonth.toString(), numberStyle, maxLines = 1).size }
    val minDayWidth = with(density) {
        maxOf(weekdaySizes.maxOf { it.width }, numberSizes.maxOf { it.width }).toDp() + 2.dp
    }
    val todayIndex = daysOfWeek.indexOf(today)
    val todayWidth = with(density) {
        measurer.measure("сегодня", todayStyle, maxLines = 1).size.width.toDp() + 2.dp
    }
    val cardHeight = with(density) {
        maxOf(76.dp, (weekdaySizes.maxOf { it.height } + numberSizes.maxOf { it.height }).toDp() + 34.dp)
    }
    val spacing = 4.dp
    val totalSpacing = spacing * (daysOfWeek.size - 1)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val minWeekWidth = minDayWidth * daysOfWeek.size + totalSpacing
        val weekWidth = maxOf(maxWidth, minWeekWidth)
        val dayWidth = (weekWidth - totalSpacing) / daysOfWeek.size
        val dayStride = dayWidth + spacing
        val scroll = if (weekWidth > maxWidth) {
            val selectedIndex = daysOfWeek.indexOf(selectedDate).coerceAtLeast(0)
            val selectedEnd = dayStride * selectedIndex + dayWidth
            Modifier.horizontalScroll(rememberScrollState(with(density) {
                (selectedEnd - maxWidth).coerceAtLeast(0.dp).roundToPx()
            }))
        } else {
            Modifier
        }
        Column(scroll.width(weekWidth)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                daysOfWeek.forEach { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val isCompleted = date in effectiveCompleted
                    val isPlanned = date in effectivePlanned && !isCompleted
                    val dayName = Fmt.dayShort[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
                    val dayNum = date.dayOfMonth.toString()

                    val cardShape = RoundedCornerShape(16.dp)

                    var cardModifier = Modifier
                        .fillMaxWidth()
                        .height(cardHeight)
                        .clip(cardShape)
                        .background(DenTrainPalette.Panel)

                    when {
                        isSelected && showSelection -> {
                            // Сине-голубая обводка при выборе (как на фото)
                            cardModifier = cardModifier.border(BorderStroke(2.dp, selectionColor), cardShape)
                        }
                        isToday -> {
                            cardModifier = cardModifier.border(BorderStroke(2.dp, Color.White), cardShape)
                        }
                        isCompleted -> {
                            cardModifier = cardModifier.border(BorderStroke(1.5.dp, accentLime), cardShape)
                        }
                        isPlanned -> {
                            cardModifier = cardModifier.dashedBorder(
                                color = Color(0xFF686F82),
                                shape = cardShape,
                                strokeWidth = 1.5.dp,
                                dashLength = 6.dp,
                                gapLength = 4.dp
                            )
                        }
                        else -> {
                            cardModifier = cardModifier.border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), cardShape)
                        }
                    }

                    Box(
                        modifier = Modifier.weight(1f).then(cardModifier)
                            .clickable {
                                Haptic.click(context)
                                onDateSelected(date)
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            // День недели (белым цветом для сегодняшнего дня)
                            Text(
                                text = dayName,
                                style = weekdayStyle,
                                color = if (isToday) Color.White else Color(0xFFA0A0A8)
                            )

                            // Число
                            Text(
                                text = dayNum,
                                style = numberStyle,
                                color = Color.White
                            )

                            // Иконка
                            when {
                                isCompleted -> {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Выполнено",
                                        tint = accentLime,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                isPlanned -> {
                                    Icon(
                                        imageVector = Icons.Filled.FitnessCenter,
                                        contentDescription = "Запланировано",
                                        tint = DenTrainPalette.MutedText,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                else -> {
                                    Spacer(Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (todayIndex >= 0) {
                val labelStart = dayStride * todayIndex + (dayWidth - todayWidth) / 2
                Text(
                    text = "сегодня",
                    style = todayStyle,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset(x = labelStart.coerceIn(0.dp, (weekWidth - todayWidth).coerceAtLeast(0.dp)))
                        .width(todayWidth)
                        .padding(top = 1.dp)
                )
            }
        }
    }
}

/** Полноэкранный аккаунт пользователя в стиле социальных сетей */
@Composable
fun UserProfilePanel(
    user: AdminUserModel,
    onBack: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onAssignWorkout: (() -> Unit)? = null,
    onSelectTrainer: ((AdminUserModel) -> Unit)? = null,
    onWorkoutClick: ((AssignedWorkoutModel) -> Unit)? = null,
    currentUserId: String? = null,
    viewerRole: String = "athlete",
    athleteTrainerUid: String? = null,
    athleteHasTrainer: Boolean = false,
    userWorkouts: List<AssignedWorkoutModel> = emptyList(),
    isLoadingWorkouts: Boolean = false,
    onDetachStudent: (() -> Unit)? = null
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDetachConfirm by remember(user.uid) { mutableStateOf(false) }
    if (showDetachConfirm && onDetachStudent != null) {
        ConfirmDialog(
            title = "Открепить ученика?",
            text = "После открепления вы потеряете доступ к тренировкам ${user.nickname}.",
            confirmText = "Открепить",
            onConfirm = {
                showDetachConfirm = false
                onDetachStudent()
            },
            onDismiss = { showDetachConfirm = false }
        )
    }
    val decodedBitmap = rememberAvatarBitmap(user.avatarBase64)

    val ageText = remember(user.dobEpochDay) {
        if (user.dobEpochDay != null) {
            val age = ChronoUnit.YEARS.between(
                LocalDate.ofEpochDay(user.dobEpochDay),
                LocalDate.now()
            ).toInt()
            ", $age лет"
        } else ""
    }

    val avatarBorderColor = when (user.role) {
        "admin" -> Color(0xFFFFC107)
        "trainer" -> Color(0xFF00BFFF)
        else -> Color.White
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = if (user.username.isNotBlank()) "@${user.username}" else user.nickname,
            onBack = onBack,
            actions = {
                if (onDetachStudent != null) {
                    TextButton(onClick = { showDetachConfirm = true }) {
                        Text("Открепить", color = DenTrainPalette.Error)
                    }
                }
                if (onEdit != null) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, "Редактировать", tint = Color.White)
                    }
                }
                if (onDelete != null && user.uid != currentUserId) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Filled.Delete, "Удалить аккаунт", tint = DenTrainPalette.Error)
                    }
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Главная карточка профиля (Instagram-like profile header)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1C1E26))
                    .border(1.dp, avatarBorderColor, RoundedCornerShape(20.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val avatarModifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(DenTrainPalette.RaisedPanel)
                        .border(2.dp, avatarBorderColor, CircleShape)

                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap.asImageBitmap(),
                            contentDescription = "Аватар",
                            modifier = avatarModifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = user.nickname.ifBlank { "Без имени" } + ageText,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 20.sp
                    )

                    if (user.username.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "@${user.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DenTrainPalette.MutedText
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (user.role == "admin" || user.role == "trainer") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(avatarBorderColor.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(if (user.role == "admin") "Администратор · Тренер" else "Тренер", color = avatarBorderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!user.clubName.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(user.clubName, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Доп информация (Дата рождения / Email)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1C1E26))
                    .padding(16.dp)
            ) {
                Column {
                    val dobText = remember(user.dobEpochDay) {
                        if (user.dobEpochDay != null) {
                            val date = LocalDate.ofEpochDay(user.dobEpochDay)
                            val d = if (date.dayOfMonth < 10) "0${date.dayOfMonth}" else "${date.dayOfMonth}"
                            val m = if (date.monthValue < 10) "0${date.monthValue}" else "${date.monthValue}"
                            "$d.$m.${date.year}"
                        } else "Не указана"
                    }
                    Text("Дата рождения:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(2.dp))
                    Text(dobText, style = MaterialTheme.typography.bodyMedium, color = Color.White)

                    if (user.email.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Email:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(2.dp))
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    }
                }
            }

            // Записаться к тренеру / Статус тренера
            if (viewerRole == "athlete" && (user.role == "trainer" || user.role == "admin") && user.uid != currentUserId) {
                Spacer(Modifier.height(16.dp))
                when {
                    user.uid == athleteTrainerUid -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF00BFFF).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF00BFFF), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ваш тренер", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold)
                        }
                    }
                    athleteHasTrainer -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(1.dp, Color.Gray, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("У вас уже есть тренер", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                    onSelectTrainer != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF1C1E26))
                                .border(1.dp, Color(0xFF00BFFF), RoundedCornerShape(16.dp))
                                .clickable { onSelectTrainer(user) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Записаться к тренеру", color = Color(0xFF00BFFF), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Назначить тренировку (Admin or Trainer)
            if (onAssignWorkout != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1E26))
                        .border(1.dp, Color.White, RoundedCornerShape(16.dp))
                        .clickable { onAssignWorkout() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Назначить тренировку", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            val workouts = userWorkouts.filter { it.athleteUid == user.uid }
            val assignedList = workouts.filter { !it.isCompleted }.sortedBy { it.dateEpochDay }
            val historyList = workouts.filter { it.isCompleted }.sortedByDescending { it.dateEpochDay }

            val canViewWorkouts = viewerRole == "admin" ||
                (viewerRole == "trainer" && currentUserId != null && user.trainerUid == currentUserId)

            // Назначенные тренировки
            if (canViewWorkouts) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Назначенные тренировки (${assignedList.size})")
                if (viewerRole == "admin" && !isLoadingWorkouts) {
                    Text("Всего назначено: ${workouts.size}", color = DenTrainPalette.MutedText)
                }
                Spacer(Modifier.height(8.dp))

                if (isLoadingWorkouts) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (assignedList.isEmpty()) {
                    Text("У пользователя пока нет назначенных тренировок", color = Color.Gray)
                } else {
                    assignedList.forEach { workout ->
                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            AssignedWorkoutCard(
                                workout = workout,
                                onClick = { if (onWorkoutClick != null) onWorkoutClick(workout) }
                            )
                        }
                    }
                }
            }

            // История назначений
            if (canViewWorkouts) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("История назначенных тренировок (${historyList.size})")
                Spacer(Modifier.height(8.dp))

                if (isLoadingWorkouts) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else if (historyList.isEmpty()) {
                    Text("В истории пока нет тренировок", color = Color.Gray)
                } else {
                    historyList.forEach { workout ->
                        Box(modifier = Modifier.padding(vertical = 4.dp)) {
                            HistoryAssignedWorkoutCard(
                                workout = workout,
                                onClick = { if (onWorkoutClick != null) onWorkoutClick(workout) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteConfirmDialog) {
        val target = if (user.username.isNotBlank()) "@${user.username}" else user.nickname
        DeleteProfileDialog(
            target = target,
            onDelete = {
                showDeleteConfirmDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

class AssignedExerciseForm(
    val uid: Long,
    name: String = "",
    sets: String = "3",
    reps: String = "10",
    weight: String = ""
) {
    var name by mutableStateOf(name)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)

    var nameError by mutableStateOf<String?>(null)
    var setsError by mutableStateOf<String?>(null)

    fun onNameChange(value: String) {
        name = value.take(60)
        if (name.isNotBlank()) {
            nameError = null
        }
    }

    fun onSetsChange(value: String) {
        sets = value.filter(Char::isDigit).take(2)
        if (sets.isNotBlank() && (sets.toIntOrNull() ?: 0) in 1..20) {
            setsError = null
        }
    }

    fun onRepsChange(value: String) {
        val digits = value.filter(Char::isDigit).take(3)
        val num = digits.toIntOrNull()
        reps = if (num != null && num > 999) "999" else digits
    }
}

@Composable
private fun AssignEditorCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutDateSelector(dayName: String, dateText: String, epochDay: Long, onDateChange: (Long) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
    ) {
        Text(
            text = dayName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DenTrainPalette.Lime
        )
        Text(
            text = ",",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(Modifier.width(6.dp))

        var showDatePicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(DenTrainPalette.RaisedPanel)
                .border(BorderStroke(1.dp, DenTrainPalette.Border), CircleShape)
                .clickable { showDatePicker = true }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = "Выбрать дату",
                    tint = DenTrainPalette.Lime,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = dateText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { onDateChange(Math.floorDiv(it, 86_400_000L)) }
                        showDatePicker = false
                    }) { Text("OK", color = DenTrainPalette.Lime) }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Отмена", color = DenTrainPalette.MutedText)
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
internal fun workoutFieldColors(isFilled: Boolean, hasError: Boolean = false) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = DenTrainPalette.Panel,
    unfocusedContainerColor = DenTrainPalette.Panel,
    focusedBorderColor = if (hasError) DenTrainPalette.Error else DenTrainPalette.Lime,
    unfocusedBorderColor = if (hasError) DenTrainPalette.Error else if (isFilled) Color.White else DenTrainPalette.Border,
    focusedLabelColor = if (hasError) DenTrainPalette.Error else DenTrainPalette.Lime,
    unfocusedLabelColor = if (hasError) DenTrainPalette.Error else if (isFilled) Color(0xFFE0E0E6) else DenTrainPalette.MutedText,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = if (hasError) DenTrainPalette.Error else DenTrainPalette.Lime
)

@Composable
internal fun EditorTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    hasError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = keyboardOptions,
        colors = workoutFieldColors(value.isNotBlank(), hasError),
        shape = RoundedCornerShape(12.dp)
    )
}

/** Полноэкранная панель назначения тренировки ученику (дизайн как у WorkoutEditorScreen) */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssignWorkoutPanel(
    athleteName: String,
    onBack: () -> Unit,
    onAssign: (title: String, dateEpochDay: Long, description: String, exercises: List<AssignedExerciseModel>) -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialExercises: List<AssignedExerciseModel> = emptyList(),
    initialDateEpochDay: Long = LocalDate.now().toEpochDay()
) {
    val context = LocalContext.current
    val app = context.applicationContext as? DenTrainApp
    val repo = app?.container?.repository
    val recentExerciseNames by repo?.recentExerciseNames?.collectAsStateWithLifecycle(initialValue = emptyList())
        ?: remember { mutableStateOf(emptyList()) }
    val settings by repo?.settings?.collectAsStateWithLifecycle(initialValue = SettingsEntity())
        ?: remember { mutableStateOf(SettingsEntity()) }

    var workoutTitle by remember { mutableStateOf(initialTitle) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var workoutDescription by remember { mutableStateOf(initialDescription) }
    var epochDay by remember { mutableLongStateOf(initialDateEpochDay) }
    var dayOfWeek by remember { mutableIntStateOf(LocalDate.ofEpochDay(initialDateEpochDay).dayOfWeek.value) }
    var error by remember { mutableStateOf<String?>(null) }

    var nextUid by remember { mutableLongStateOf(1L) }
    val exercises = remember {
        mutableStateListOf<AssignedExerciseForm>().apply {
            if (initialExercises.isNotEmpty()) {
                initialExercises.forEach { e ->
                    add(
                        AssignedExerciseForm(
                            uid = nextUid++,
                            name = e.name,
                            sets = e.plannedSets.toString(),
                            reps = e.plannedReps,
                            weight = Fmt.weight(e.plannedWeight) ?: ""
                        )
                    )
                }
            } else {
                add(
                    AssignedExerciseForm(
                        uid = nextUid++,
                        name = "Жим лежа",
                        sets = "4",
                        reps = "10",
                        weight = "60.0"
                    )
                )
            }
        }
    }

    fun exerciseValues() = exercises.map { listOf(it.name, it.sets, it.reps, it.weight) }
    val originalExercises = remember { exerciseValues() }
    var showDiscard by remember { mutableStateOf(false) }
    var pendingAssignment by remember { mutableStateOf<List<AssignedExerciseModel>?>(null) }
    val handleBack = {
        val changed = workoutTitle != initialTitle || workoutDescription != initialDescription ||
            epochDay != initialDateEpochDay || exerciseValues() != originalExercises
        if (changed) showDiscard = true else onBack()
    }
    BackHandler(onBack = handleBack)
    if (showDiscard) {
        DiscardChangesDialog(
            onDiscard = { showDiscard = false; onBack() },
            onDismiss = { showDiscard = false }
        )
    }

    pendingAssignment?.let { plan ->
        ConfirmDialog(
            title = "Назначить тренировку?",
            text = "Тренировка «${workoutTitle.trim()}» появится в плане спортсмена «$athleteName».",
            confirmText = "Назначить",
            destructive = false,
            onConfirm = {
                pendingAssignment = null
                onAssign(workoutTitle.trim(), epochDay, workoutDescription.trim(), plan)
            },
            onDismiss = { pendingAssignment = null }
        )
    }

    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible

    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            delay(350)
            focusManager.clearFocus()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val titleFocusRequester = remember { FocusRequester() }
    val nameFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    val setsFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }

    fun changeDate(day: Long) {
        epochDay = day
        dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek.value
    }

    fun changeDayOfWeek(targetDayOfWeek: Int) {
        dayOfWeek = targetDayOfWeek
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek.value
        var daysToAdd = targetDayOfWeek - currentDayOfWeek
        if (daysToAdd < 0) {
            daysToAdd += 7
        }
        epochDay = today.plusDays(daysToAdd.toLong()).toEpochDay()
    }

    fun addExercise() {
        exercises.add(AssignedExerciseForm(nextUid++))
    }

    fun removeExercise(index: Int) {
        if (index in exercises.indices) {
            exercises.removeAt(index)
        }
    }

    fun move(index: Int, delta: Int) {
        val j = index + delta
        if (index in exercises.indices && j in exercises.indices) {
            val t = exercises[index]
            exercises[index] = exercises[j]
            exercises[j] = t
        }
    }

    fun validateAndAssign() {
        if (workoutTitle.isBlank()) {
            titleError = "Введите название тренировки"
            error = "Введите название тренировки"
            coroutineScope.launch {
                listState.animateScrollToItem(1)
                titleFocusRequester.requestFocus()
            }
            return
        }

        if (exercises.isEmpty()) {
            error = "Добавьте хотя бы одно упражнение"
            return
        }

        for ((i, e) in exercises.withIndex()) {
            val n = i + 1
            if (e.name.isBlank()) {
                e.nameError = "Введите название упражнения"
                error = "Упражнение $n: введите название"
                coroutineScope.launch {
                    listState.animateScrollToItem(i + 3)
                    nameFocusRequesters[e.uid]?.requestFocus()
                }
                return
            }
            val setsNum = e.sets.trim().toIntOrNull()
            if (setsNum == null || setsNum < 1 || setsNum > 20) {
                e.setsError = "Подходов должно быть от 1 до 20"
                error = "Упражнение $n: подходов должно быть от 1 до 20"
                coroutineScope.launch {
                    listState.animateScrollToItem(i + 3)
                    setsFocusRequesters[e.uid]?.requestFocus()
                }
                return
            }
            val weight = Fmt.parseWeight(e.weight)
            if (e.weight.isNotBlank() && (weight == null || !weight.isFinite() || weight < 0)) {
                error = "Упражнение $n: укажите вес числом от 0 или оставьте поле пустым."
                return
            }
        }

        error = null
        val assignedList = exercises.map { e ->
            val weight = if (e.weight.isBlank()) null else Fmt.parseWeight(e.weight)
            AssignedExerciseModel(
                name = e.name.trim(),
                plannedSets = e.sets.trim().toIntOrNull() ?: 1,
                plannedReps = e.reps.trim().ifBlank { "10" },
                plannedWeight = weight
            )
        }
        pendingAssignment = assignedList
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = "Назначение тренировки",
            onBack = handleBack
        )

        val tfShape = RoundedCornerShape(12.dp)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Баннер: Тренировка для ...
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Color(0xFFFFC107), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Назначение тренировки", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.height(2.dp))
                            Text("Тренировка для $athleteName", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Основные настройки
            item {
                AssignEditorCard {
                    Text(
                        "Основные настройки",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    val dateText = remember(epochDay) {
                        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
                        LocalDate.ofEpochDay(epochDay).format(formatter)
                    }
                    val dayName = remember(dayOfWeek) { Fmt.dayName(dayOfWeek) }

                    WorkoutDateSelector(dayName, dateText, epochDay, ::changeDate)

                    if (titleError != null) {
                        Text(
                            text = titleError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    OutlinedTextField(
                        value = workoutTitle,
                        onValueChange = {
                            workoutTitle = it.take(72)
                            if (it.isNotBlank()) titleError = null
                        },
                        label = { Text("Название (напр., Грудь и трицепс)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester),
                        colors = workoutFieldColors(isFilled = workoutTitle.isNotBlank(), hasError = titleError != null),
                        shape = tfShape
                    )

                    Spacer(Modifier.height(12.dp))

                    Text("День недели", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = DenTrainPalette.MutedText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Fmt.dayShort.forEachIndexed { i, name ->
                            val selected = dayOfWeek == i + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(DenTrainPalette.RaisedPanel)
                                    .border(
                                        BorderStroke(
                                            if (selected) 1.5.dp else 1.dp,
                                            if (selected) DenTrainPalette.Lime else DenTrainPalette.Border
                                        ),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { changeDayOfWeek(i + 1) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) Color.White else DenTrainPalette.MutedText
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = workoutDescription,
                        onValueChange = { workoutDescription = it.take(300) },
                        label = { Text("Описание (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = workoutFieldColors(isFilled = workoutDescription.isNotBlank()),
                        shape = tfShape
                    )
                }
            }

            // План тренировки:
            item {
                Text(
                    "План тренировки:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
            }

            // Карточки упражнений
            itemsIndexed(exercises, key = { _, e -> e.uid }) { index, e ->
                val nameFocus = nameFocusRequesters.getOrPut(e.uid) { FocusRequester() }
                val setsFocus = setsFocusRequesters.getOrPut(e.uid) { FocusRequester() }

                AssignEditorCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Упражнение ${index + 1}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { move(index, -1) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowUpward,
                                    "Выше",
                                    tint = if (index > 0) Color.White else Color(0xFF444858),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { move(index, 1) },
                                enabled = index < exercises.lastIndex,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.ArrowDownward,
                                    "Ниже",
                                    tint = if (index < exercises.lastIndex) Color.White else Color(0xFF444858),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { removeExercise(index) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    "Удалить",
                                    tint = DenTrainPalette.Error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (e.nameError != null) {
                        Text(
                            text = e.nameError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    var isNameFocused by remember { mutableStateOf(false) }

                    OutlinedTextField(
                        value = e.name,
                        onValueChange = e::onNameChange,
                        label = { Text("Название упражнения") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocus)
                            .onFocusChanged { isNameFocused = it.isFocused },
                        colors = workoutFieldColors(isFilled = e.name.isNotBlank(), hasError = e.nameError != null),
                        shape = tfShape
                    )

                    val filteredSuggestions = remember(e.name, recentExerciseNames) {
                        if (e.name.isBlank()) {
                            recentExerciseNames.take(8)
                        } else {
                            recentExerciseNames.filter {
                                it.contains(e.name, ignoreCase = true)
                            }.take(8)
                        }
                    }

                    val showSuggestions = isNameFocused && filteredSuggestions.isNotEmpty()

                    AnimatedVisibility(
                        visible = showSuggestions,
                        enter = fadeIn(tween(350)) + expandVertically(tween(350)),
                        exit = fadeOut(tween(500)) + shrinkVertically(tween(500))
                    ) {
                        LazyRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredSuggestions, key = { it }) { item ->
                                val isSel = e.name.equals(item, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) Color(0xFF2A2415) else DenTrainPalette.RaisedPanel)
                                        .border(
                                            BorderStroke(
                                                if (isSel) 1.5.dp else 1.dp,
                                                if (isSel) Color(0xFFFFB300) else DenTrainPalette.Border
                                            ),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable { e.onNameChange(item) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = item,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color(0xFFFFD54F) else DenTrainPalette.MutedText
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (e.setsError != null) {
                        Text(
                            text = e.setsError!!,
                            color = DenTrainPalette.Error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = e.sets,
                            onValueChange = e::onSetsChange,
                            label = { Text("Подходы", fontSize = 11.sp, maxLines = 1) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(setsFocus),
                            colors = workoutFieldColors(isFilled = e.sets.isNotBlank(), hasError = e.setsError != null),
                            shape = tfShape
                        )

                        if (settings.swapWeightReps) {
                            OutlinedTextField(
                                value = e.weight,
                                onValueChange = { e.weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                label = { Text("Вес, кг", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.weight.isNotBlank()),
                                shape = tfShape
                            )
                            OutlinedTextField(
                                value = e.reps,
                                onValueChange = e::onRepsChange,
                                label = { Text("Повторы", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.reps.isNotBlank()),
                                shape = tfShape
                            )
                        } else {
                            OutlinedTextField(
                                value = e.reps,
                                onValueChange = e::onRepsChange,
                                label = { Text("Повторы", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.reps.isNotBlank()),
                                shape = tfShape
                            )
                            OutlinedTextField(
                                value = e.weight,
                                onValueChange = { e.weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(7) },
                                label = { Text("Вес, кг", fontSize = 11.sp, maxLines = 1) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                                modifier = Modifier.weight(1f),
                                colors = workoutFieldColors(isFilled = e.weight.isNotBlank()),
                                shape = tfShape
                            )
                        }
                    }
                }
            }

            // Добавить упражнение
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                        .clickable { addExercise() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = DenTrainPalette.Lime)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Добавить упражнение",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DenTrainPalette.Lime
                        )
                    }
                }
            }

            // Кнопка: Назначить тренировку
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.Lime), RoundedCornerShape(16.dp))
                        .clickable { validateAndAssign() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Отправить тренировку",
                        color = DenTrainPalette.Lime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

/** Полноэкранный просмотр назначенной/выполненной тренировки пользователя (стиль WorkoutDetailScreen) */
@Composable
fun AssignedWorkoutDetailPanel(
    workout: AssignedWorkoutModel,
    user: AdminUserModel,
    onBack: () -> Unit,
    onReassign: (() -> Unit)? = null
) {
    val dateText = remember(workout.dateEpochDay) {
        val date = LocalDate.ofEpochDay(workout.dateEpochDay)
        "${date.dayOfMonth} ${Fmt.monthName(date.monthValue)} ${date.year}"
    }
    val dayName = remember(workout.dateEpochDay) {
        val date = LocalDate.ofEpochDay(workout.dateEpochDay)
        Fmt.dayName(date.dayOfWeek.value)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = if (workout.isCompleted) "Завершённая тренировка" else "Просмотр тренировки",
            onBack = onBack
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Плашка пометки режима просмотра
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DenTrainPalette.RaisedPanel)
                        .border(BorderStroke(1.dp, DenTrainPalette.Lime), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = workout.deliveryState?.label ?: "Режим просмотра",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Карточка с главной информацией о тренировке (DetailCard style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = workout.title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DenTrainPalette.Lime
                        )
                        Text(
                            text = ", $dateText",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = DenTrainPalette.MutedText
                        )
                    }

                    if (workout.trainerName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Назначил: ${workout.trainerName}", fontSize = 14.sp, color = Color(0xFF00BFFF), fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.height(10.dp))

                    // Статистика
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val exercisesCount = workout.exercises.size
                        val totalSetsCount = workout.exercises.sumOf { it.plannedSets }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$exercisesCount ${Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$totalSetsCount ${Fmt.plural(totalSetsCount, "подход", "подхода", "подходов")}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    // Заметки
                    if (workout.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DenTrainPalette.RaisedPanel)
                                .border(BorderStroke(1.5.dp, Color(0xFFFFB300)), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Заметки",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFB300)
                                )
                                Text(
                                    text = workout.description,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            // Карточки упражнений (как в WorkoutDetailScreen)
            workout.exercises.forEach { ex ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DenTrainPalette.Panel)
                        .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = ex.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(DenTrainPalette.RaisedPanel)
                                    .border(BorderStroke(1.dp, DenTrainPalette.Border), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "В планах ${ex.plannedSets} подх.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DenTrainPalette.MutedText
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val setsCount = ex.plannedSets.coerceAtLeast(1)
                            for (setNum in 1..setsCount) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DenTrainPalette.RaisedPanel)
                                        .border(BorderStroke(1.dp, DenTrainPalette.Border), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "№ $setNum",
                                        color = DenTrainPalette.MutedText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    if (ex.plannedWeight != null && ex.plannedWeight > 0) {
                                        Text(
                                            text = "${Fmt.weight(ex.plannedWeight)} кг",
                                            color = DenTrainPalette.Lime,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = " | ",
                                            color = DenTrainPalette.Border,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${ex.plannedReps} повт.",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Кнопка действий: Назначить снова (без кнопок Закрепить и Изменить)
            if (onReassign != null) Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, DenTrainPalette.Lime), RoundedCornerShape(16.dp))
                    .clickable { onReassign() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        tint = DenTrainPalette.Lime,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Назначить снова",
                        color = DenTrainPalette.Lime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Полноэкранный профиль редактирования пользователя для Администратора (стиль WorkoutEditorScreen) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminEditUserPanel(
    user: AdminUserModel,
    availableClubs: List<ClubModel>,
    availableTrainers: List<AdminUserModel>,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (
        nickname: String,
        username: String,
        role: String,
        club: ClubModel?,
        trainer: AdminUserModel?
    ) -> Unit
) {
    var nickname by remember { mutableStateOf(user.nickname) }
    var username by remember { mutableStateOf(user.username) }
    var role by remember { mutableStateOf(user.role) }
    var selectedClub by remember { mutableStateOf(availableClubs.find { it.id == user.clubId }) }
    var selectedTrainer by remember { mutableStateOf(availableTrainers.find { it.uid == user.trainerUid }) }

    var showClubDialog by remember { mutableStateOf(false) }
    var showTrainerDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    fun buildDiffAnnotatedList(): List<AnnotatedString> {
        val diffs = mutableListOf<AnnotatedString>()
        val initialClubName = availableClubs.find { it.id == user.clubId }?.name ?: "Без клуба"
        val initialTrainerName = availableTrainers.find { it.uid == user.trainerUid }?.nickname ?: "Без тренера"
        val initialRoleName = when (user.role) { "admin" -> "Администратор"; "trainer" -> "Тренер"; else -> "Спортсмен" }

        val currentRoleName = when (role) { "admin" -> "Администратор"; "trainer" -> "Тренер"; else -> "Спортсмен" }
        val currentClubName = selectedClub?.name ?: "Без клуба"
        val currentTrainerName = selectedTrainer?.nickname ?: "Без тренера"

        fun formatDiff(field: String, oldVal: String, newVal: String): AnnotatedString {
            return buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White)) { append("$field: ") }
                append("с ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = DenTrainPalette.MutedText)) { append("\"$oldVal\"") }
                append(" на ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = DenTrainPalette.Lime)) { append("\"$newVal\"") }
            }
        }

        if (nickname.trim() != user.nickname.trim()) {
            diffs.add(formatDiff("Имя", user.nickname.ifBlank { "Без имени" }, nickname.trim()))
        }
        if (username.trim() != user.username.trim()) {
            diffs.add(formatDiff("Логин", "@${user.username}", "@${username.trim()}"))
        }
        if (role != user.role) {
            diffs.add(formatDiff("Роль", initialRoleName, currentRoleName))
        }
        if (selectedClub?.id != user.clubId) {
            diffs.add(formatDiff("Клуб", initialClubName, currentClubName))
        }
        if (selectedTrainer?.uid != user.trainerUid) {
            diffs.add(formatDiff("Тренер", initialTrainerName, currentTrainerName))
        }
        return diffs
    }

    val handleBack = {
        val diffs = buildDiffAnnotatedList()
        if (diffs.isNotEmpty()) {
            showConfirmDialog = true
        } else {
            onBack()
        }
    }

    BackHandler {
        handleBack()
    }

    val avatarBorderColor = when (role) {
        "admin" -> Color(0xFFFFC107)
        "trainer" -> Color(0xFF00BFFF)
        else -> Color.White
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        ScreenHeader(
            title = "Редактирование профиля",
            onBack = { handleBack() },
            actions = {
                if (onDelete != null) {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Filled.Delete, "Удалить аккаунт", tint = DenTrainPalette.Error)
                    }
                }
            }
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Герой-карточка предварительного просмотра профиля
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, avatarBorderColor), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(DenTrainPalette.RaisedPanel)
                            .border(BorderStroke(2.dp, avatarBorderColor), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(nickname.ifBlank { "Без имени" }, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                    if (username.isNotBlank()) Text("@$username", style = MaterialTheme.typography.bodyMedium, color = DenTrainPalette.MutedText)
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(avatarBorderColor.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(role.uppercase(), color = avatarBorderColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Карточка редактирования основных данных
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                    .animateContentSize()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Личные данные", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)

                    EditorTextField(nickname, { nickname = it }, "Имя / Никнейм")
                    EditorTextField(username, { username = it.replace("@", "").trim() }, "Логин (@username)")

                    // Выбор роли
                    Text("Роль аккаунта:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("athlete" to "Спортсмен", "trainer" to "Тренер", "admin" to "Админ").forEach { (rKey, rLabel) ->
                            val isSel = role == rKey
                            val color = when(rKey) { "admin" -> Color(0xFFFFC107); "trainer" -> Color(0xFF00BFFF); else -> Color.White }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) color.copy(alpha = 0.2f) else DenTrainPalette.RaisedPanel)
                                    .border(1.dp, if (isSel) color else DenTrainPalette.Border, RoundedCornerShape(12.dp))
                                    .clickable { role = rKey }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(rLabel, fontSize = 13.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) color else Color.Gray)
                            }
                        }
                    }
                }
            }

            // Карточка выбора Клуба и Тренера (Высокий контраст)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
                    .animateContentSize()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Клуб и Тренер", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)

                    // Выбор Клуба
                    Text("Фитнес-клуб:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DenTrainPalette.RaisedPanel)
                            .border(1.dp, DenTrainPalette.Lime, RoundedCornerShape(12.dp))
                            .clickable { showClubDialog = true }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Business, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(selectedClub?.name ?: "Без клуба", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }
                            Text("Изменить >", color = DenTrainPalette.Lime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Выбор Тренера
                    Text("Личный тренер:", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DenTrainPalette.RaisedPanel)
                            .border(1.dp, DenTrainPalette.Lime, RoundedCornerShape(12.dp))
                            .clickable { showTrainerDialog = true }
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, tint = Color(0xFFFFC107), modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(selectedTrainer?.nickname ?: "Без тренера", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }
                            Text("Изменить >", color = DenTrainPalette.Lime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Кнопка: Сохранить изменения
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DenTrainPalette.Panel)
                    .border(BorderStroke(1.dp, DenTrainPalette.Lime), RoundedCornerShape(16.dp))
                    .clickable {
                        val diffs = buildDiffAnnotatedList()
                        if (diffs.isNotEmpty()) {
                            showConfirmDialog = true
                        } else {
                            onSave(nickname, username, role, selectedClub, selectedTrainer)
                        }
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Сохранить изменения", color = DenTrainPalette.Lime, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Модальное окно выбора Клуба
    if (showClubDialog) {
        AlertDialog(
            onDismissRequest = { showClubDialog = false },
            title = { Text("Выберите Фитнес-Клуб", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedClub == null) Color(0xFF2A2E1A) else Color(0xFF1C1E26))
                            .border(1.dp, if (selectedClub == null) DenTrainPalette.Lime else DenTrainPalette.Border, RoundedCornerShape(12.dp))
                            .clickable { selectedClub = null; showClubDialog = false }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Business, null, tint = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text("Без клуба", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    availableClubs.forEach { club ->
                        val isSel = selectedClub?.id == club.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF2A2E1A) else Color(0xFF1C1E26))
                                .border(1.dp, if (isSel) DenTrainPalette.Lime else DenTrainPalette.Border, RoundedCornerShape(12.dp))
                                .clickable { selectedClub = club; showClubDialog = false }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Business, null, tint = Color(0xFF00BFFF))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(club.name, fontWeight = FontWeight.Bold, color = Color.White)
                                if (club.address.isNotBlank()) Text(club.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClubDialog = false }) { Text("Закрыть", color = DenTrainPalette.Lime) }
            }
        )
    }

    // Модальное окно выбора Тренера
    if (showTrainerDialog) {
        AlertDialog(
            onDismissRequest = { showTrainerDialog = false },
            title = { Text("Выберите Тренера", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedTrainer == null) Color(0xFF2A2E1A) else Color(0xFF1C1E26))
                            .border(1.dp, if (selectedTrainer == null) DenTrainPalette.Lime else DenTrainPalette.Border, RoundedCornerShape(12.dp))
                            .clickable { selectedTrainer = null; showTrainerDialog = false }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Color.Gray)
                        Spacer(Modifier.width(12.dp))
                        Text("Без тренера", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(8.dp))
                    availableTrainers.filter { it.uid != user.uid }.forEach { tr ->
                        val isSel = selectedTrainer?.uid == tr.uid
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) Color(0xFF2A2E1A) else Color(0xFF1C1E26))
                                .border(1.dp, if (isSel) DenTrainPalette.Lime else DenTrainPalette.Border, RoundedCornerShape(12.dp))
                                .clickable { selectedTrainer = tr; showTrainerDialog = false }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Person, null, tint = Color(0xFF00BFFF))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tr.nickname, fontWeight = FontWeight.Bold, color = Color.White)
                                if (tr.username.isNotBlank()) Text("@${tr.username}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTrainerDialog = false }) { Text("Закрыть", color = DenTrainPalette.Lime) }
            }
        )
    }

    if (showConfirmDialog) {
        val diffs = buildDiffAnnotatedList()
        ConfirmDialog(
            title = "Сохранить изменения профиля?",
            confirmText = "Сохранить",
            destructive = false,
            onConfirm = {
                showConfirmDialog = false
                onSave(nickname, username, role, selectedClub, selectedTrainer)
            },
            dismissText = "Выйти без сохранения",
            secondaryDestructive = true,
            onSecondary = {
                showConfirmDialog = false
                onBack()
            },
            onDismiss = { showConfirmDialog = false }
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                diffs.forEach { diff -> Text(diff) }
            }
        }
    }

    // Диалог подтверждения удаления пользователя
    if (showDeleteConfirmDialog) {
        val target = if (user.username.isNotBlank()) "@${user.username}" else user.nickname
        DeleteProfileDialog(
            target = target,
            onDelete = {
                showDeleteConfirmDialog = false
                onDelete?.invoke()
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }
}

/** Карточка Назначенной тренировки (дизайн как у PlannedWorkoutCard на главном экране) */
@Composable
fun AssignedWorkoutCard(
    workout: AssignedWorkoutModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val neoGreen = DenTrainPalette.NeonGreen

    val date = remember(workout.dateEpochDay) {
        LocalDate.ofEpochDay(workout.dateEpochDay)
    }

    val dayOfWeekName = remember(date) {
        Fmt.dayShort[(date.dayOfWeek.value - 1).coerceIn(0, 6)]
    }

    val monthNamesGenitive = remember {
        listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    }
    val monthName = remember(date) {
        monthNamesGenitive[(date.monthValue - 1).coerceIn(0, 11)]
    }
    val dayOfMonth = date.dayOfMonth.toString()

    val exercisesCount = workout.exercises.size
    val totalSetsCount = remember(workout) {
        workout.exercises.sumOf { it.plannedSets.coerceAtLeast(1) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DenTrainPalette.Panel)
            .border(BorderStroke(1.dp, DenTrainPalette.SubtleBorder), RoundedCornerShape(16.dp))
            .clickable {
                Haptic.click(context)
                onClick()
            }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.width(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dayOfWeekName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = neoGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = dayOfMonth,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = monthName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DenTrainPalette.MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(DenTrainPalette.SubtleBorder)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = workout.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "$exercisesCount ${Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений")} · $totalSetsCount ${Fmt.plural(totalSetsCount, "подход", "подхода", "подходов")}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DenTrainPalette.MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = {
                    Haptic.click(context)
                    onClick()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Просмотр",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Карточка Выполненной/Прошлой тренировки в истории назначенных тренировок (дизайн как у HistoryWorkoutCard) */
@Composable
fun HistoryAssignedWorkoutCard(
    workout: AssignedWorkoutModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cardBg = DenTrainPalette.Panel
    val borderColor = DenTrainPalette.SubtleBorder
    val labelColor = DenTrainPalette.MutedText
    val accentColor = DenTrainPalette.Lime // салатовый акцент

    val date = remember(workout.dateEpochDay) { LocalDate.ofEpochDay(workout.dateEpochDay) }
    val dayName = remember(date) { Fmt.dayName(date.dayOfWeek.value) }
    val dateText = remember(date) {
        val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
        date.format(formatter)
    }

    val exercisesCount = workout.exercises.size
    val setsCount = remember(workout) {
        workout.exercises.sumOf { it.plannedSets.coerceAtLeast(1) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(16.dp))
            .clickable {
                Haptic.click(context)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = workout.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Text(
                    text = ", $dateText",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = labelColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = exercisesCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Fmt.plural(exercisesCount, "упражнение", "упражнения", "упражнений"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .height(16.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = setsCount.toString(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = Fmt.plural(setsCount, "подход", "подхода", "подходов"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
