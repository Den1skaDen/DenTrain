package com.dentrain.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dentrain.R
import com.dentrain.ui.theme.LocalGlassTheme
import com.dentrain.ui.theme.LocalGradient
import com.dentrain.ui.theme.LocalHeaderLogoVisible
import com.dentrain.util.Fmt

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
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
    )
}

/** Компактная шапка с крупным прозрачным логотипом DT строго по центру экрана. */
@Composable
fun ScreenHeader(title: String = "", subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val isHeaderLogoVisible = LocalHeaderLogoVisible.current

    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
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
fun ConfirmDialog(
    title: String, text: String, confirmText: String = "Удалить",
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
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
fun DateField(epochDay: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, label: String = "Дата") {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { show = true }, modifier = modifier, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Icon(Icons.Filled.CalendarMonth, null)
        Spacer(Modifier.width(8.dp))
        Text("$label: ${Fmt.shortDate(epochDay)}")
    }
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Math.floorDiv(it, 86_400_000L)) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Отмена") } }
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
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        GlassBackground(modifier = Modifier.weight(1f)) {
            content(PaddingValues(0.dp))
        }
    }
}
