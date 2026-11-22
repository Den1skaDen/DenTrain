package com.dentrain.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dentrain.ui.theme.DenTrainPalette
import com.dentrain.util.Fmt
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun ProgressChart(series: ProgressSeries) {
    val points = series.points
    var selectedDay by rememberSaveable(series.filter) { mutableStateOf<Long?>(null) }
    val selectedIndex = selectedDay?.let { day -> points.binarySearchBy(day) { it.dateEpochDay } }
        ?.takeIf { it >= 0 } ?: points.lastIndex
    val selected = points[selectedIndex]
    val accent = DenTrainPalette.Lime
    val muted = DenTrainPalette.MutedText
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier.fillMaxWidth()
            .background(DenTrainPalette.Panel, shape)
            .border(1.dp, DenTrainPalette.SubtleBorder, shape)
            .semantics(mergeDescendants = true) {
                customActions = buildList {
                    if (selectedIndex > 0) {
                        add(CustomAccessibilityAction("Предыдущая дата") {
                            selectedDay = points[selectedIndex - 1].dateEpochDay
                            true
                        })
                    }
                    if (selectedIndex < points.lastIndex) {
                        add(CustomAccessibilityAction("Следующая дата") {
                            selectedDay = points[selectedIndex + 1].dateEpochDay
                            true
                        })
                    }
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Динамика веса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (series.filter.favoritesOnly) "Максимальный вес за день · избранное" else "Максимальный вес за день",
                style = MaterialTheme.typography.bodySmall,
                color = muted
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .background(DenTrainPalette.RaisedPanel, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${chartWeight(selected.weight)} кг × ${selected.reps}",
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(Fmt.shortDate(selected.dateEpochDay), color = muted, style = MaterialTheme.typography.labelMedium)
        }
        WeightPlot(points, selectedIndex) { selectedDay = it }
    }
}

@Composable
private fun WeightPlot(points: List<ProgressPoint>, selectedIndex: Int, onSelect: (Long) -> Unit) {
    val ticks = remember(points) { progressWeightTicks(points) }
    val fractions = remember(points) {
        val first = points.first().dateEpochDay
        val last = points.last().dateEpochDay
        points.map { progressDateFraction(it.dateEpochDay, first, last) }
    }
    val accent = DenTrainPalette.Lime
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = DenTrainPalette.MutedText)
    val labels = remember(ticks, axisStyle, textMeasurer) {
        ticks.map { textMeasurer.measure("${chartWeight(it)} кг", axisStyle) }
    }
    val density = LocalDensity.current
    val left = with(density) { labels.maxOf { it.size.width } + 10.dp.toPx() }
    val onSelectState by rememberUpdatedState(onSelect)
    val currentIndex = rememberUpdatedState(selectedIndex)

    Box(
        Modifier.fillMaxWidth().height(148.dp)
            .pointerInput(points, fractions, left) {
                detectTapGestures { tap ->
                    val right = size.width - 8.dp.toPx()
                    val plotLeft = left.coerceAtMost(right - 1f)
                    val fraction = (tap.x - plotLeft) / (right - plotLeft)
                    val match = fractions.binarySearch(fraction)
                    val index = if (match >= 0) match else {
                        val after = (-match - 1).coerceAtMost(fractions.lastIndex)
                        val before = (after - 1).coerceAtLeast(0)
                        if (abs(fractions[before] - fraction) <= abs(fractions[after] - fraction)) before else after
                    }
                    onSelectState(points[index].dateEpochDay)
                }
            }
            .drawWithCache {
                val right = size.width - 8.dp.toPx()
                val plotLeft = left.coerceAtMost(right - 1f)
                val top = labels.maxOf { it.size.height } / 2f + 2.dp.toPx()
                val bottom = size.height - top
                val minWeight = ticks.first()
                val weightRange = ticks.last() - minWeight
                fun y(weight: Double) = bottom - ((weight - minWeight) / weightRange).toFloat() * (bottom - top)

                val gridY = ticks.map(::y)
                val positions = points.mapIndexed { index, point ->
                    Offset(plotLeft + (right - plotLeft) * fractions[index], y(point.weight))
                }
                val line = Path().apply {
                    moveTo(positions.first().x, positions.first().y)
                    for (index in 1..positions.lastIndex) {
                        lineTo(positions[index].x, positions[index].y)
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(positions.last().x, bottom)
                    lineTo(positions.first().x, bottom)
                    close()
                }
                val fillBrush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.02f)), top, bottom)
                val dashed = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                val lineStroke = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                onDrawBehind {
                    gridY.forEachIndexed { index, y ->
                        val label = labels[index]
                        drawLine(DenTrainPalette.Border, Offset(plotLeft, y), Offset(right, y), strokeWidth = 1.dp.toPx())
                        drawText(label, topLeft = Offset(plotLeft - 10.dp.toPx() - label.size.width, y - label.size.height / 2f))
                    }
                    val selected = positions[currentIndex.value.coerceIn(positions.indices)]
                    if (positions.size > 1) {
                        drawPath(fill, fillBrush)
                        drawLine(accent.copy(alpha = 0.5f), Offset(selected.x, top), Offset(selected.x, bottom),
                            strokeWidth = 1.dp.toPx(), pathEffect = dashed)
                        drawPath(line, accent, style = lineStroke)
                        for (point in positions) drawCircle(accent, radius = 2.5.dp.toPx(), center = point)
                    }
                    drawCircle(accent.copy(alpha = 0.22f), radius = 8.dp.toPx(), center = selected)
                    drawCircle(DenTrainPalette.Panel, radius = 5.dp.toPx(), center = selected)
                    drawCircle(accent, radius = 3.5.dp.toPx(), center = selected)
                }
            }
    )
    if (points.size > 1) {
        Row(
            Modifier.fillMaxWidth().padding(start = with(density) { left.toDp() }, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(Fmt.shortDate(points.first().dateEpochDay), style = axisStyle)
            Text(Fmt.shortDate(points.last().dateEpochDay), style = axisStyle)
        }
    }
}

private fun chartWeight(weight: Double): String =
    if (weight >= 100_000) String.format(Locale.ROOT, "%.1e", weight)
    else Fmt.weight(weight).orEmpty()
