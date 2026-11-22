package com.dentrain.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

@Composable
internal fun MetricNumber(
    text: String,
    minDigits: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    lineHeight: TextUnit = TextUnit.Unspecified,
    minWidth: Dp = Dp.Unspecified
) {
    val style = LocalTextStyle.current.copy(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFeatureSettings = "tnum",
        lineHeight = lineHeight
    )
    val width = if (minWidth.isSpecified) minWidth
        else rememberMetricNumberWidth(minDigits, fontSize, fontWeight, lineHeight)
    Text(
        text = text,
        color = color,
        style = style,
        modifier = Modifier.widthIn(min = width),
        maxLines = 1,
        softWrap = false
    )
}

@Composable
internal fun rememberMetricNumberWidth(
    minDigits: Int,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit
): Dp {
    val style = LocalTextStyle.current.copy(
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFeatureSettings = "tnum",
        lineHeight = lineHeight
    )
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, style, minDigits) {
        with(density) { measurer.measure("0".repeat(minDigits), style, maxLines = 1).size.width.toDp() }
    }
}
