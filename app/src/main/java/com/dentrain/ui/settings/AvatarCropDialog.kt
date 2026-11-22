package com.dentrain.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas as BitmapCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.dentrain.util.UserFacingException
import com.dentrain.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Composable
internal fun AvatarCropDialog(uri: Uri, onDismiss: () -> Unit, onSave: suspend (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var zoom by rememberSaveable(uri) { mutableFloatStateOf(1f) }
    var centerX by rememberSaveable(uri) { mutableFloatStateOf(0.5f) }
    var centerY by rememberSaveable(uri) { mutableFloatStateOf(0.5f) }

    LaunchedEffect(uri) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(2304)
                .scale(Scale.FIT)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            bitmap = (result as? SuccessResult)?.drawable.let { (it as? BitmapDrawable)?.bitmap }
            if (bitmap == null) error = "Не удалось открыть фотографию. Выберите другое изображение."
        } catch (failure: Exception) {
            error = failure.userMessage("Не удалось открыть фотографию.")
        }
    }

    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Обрезка фото", style = MaterialTheme.typography.titleLarge)
                val photo = bitmap
                if (photo == null && error == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (photo != null) {
                    Text("Переместите фото или измените масштаб. Круг показывает, как будет выглядеть аватар.")
                    val image = remember(photo) { photo.asImageBitmap() }
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .background(Color.Black)
                            .semantics { contentDescription = "Предпросмотр аватара" }
                            .pointerInput(photo, saving) {
                                if (!saving) detectTransformGestures { centroid, pan, scale, _ ->
                                    val previous = photo.cropBounds(zoom, centerX, centerY)
                                    zoom = (zoom * scale).coerceIn(1f, 4f)
                                    val edge = photo.cropEdge(zoom)
                                    val width = size.width.toFloat().coerceAtLeast(1f)
                                    val focus = (centroid / width) - Offset(0.5f, 0.5f)
                                    val center = Offset(previous.exactCenterX(), previous.exactCenterY()) +
                                        focus * (previous.width() - edge).toFloat() - pan * (edge / width)
                                    val bounded = photo.cropBounds(zoom, center.x / photo.width, center.y / photo.height)
                                    centerX = bounded.exactCenterX() / photo.width
                                    centerY = bounded.exactCenterY() / photo.height
                                }
                            }
                    ) {
                        val bounds = photo.cropBounds(zoom, centerX, centerY)
                        drawImage(
                            image,
                            srcOffset = IntOffset(bounds.left, bounds.top),
                            srcSize = IntSize(bounds.width(), bounds.height()),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.High
                        )
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = Stroke(2.dp.toPx()))
                    }
                    Text("Масштаб")
                    Slider(value = zoom, onValueChange = {
                        zoom = it
                        val bounds = photo.cropBounds(zoom, centerX, centerY)
                        centerX = bounds.exactCenterX() / photo.width
                        centerY = bounds.exactCenterY() / photo.height
                    }, valueRange = 1f..4f, enabled = !saving,
                        modifier = Modifier.semantics { contentDescription = "Масштаб фото" })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Отмена") }
                    Button(enabled = photo != null && !saving, onClick = {
                        if (photo != null && !saving) {
                            saving = true
                            error = null
                            val bounds = photo.cropBounds(zoom, centerX, centerY)
                            scope.launch {
                                try {
                                    val avatar = withContext(Dispatchers.Default) { photo.encodeAvatar(bounds) }
                                    onSave(avatar)
                                    onDismiss()
                                } catch (failure: Exception) {
                                    error = failure.userMessage("Не удалось сохранить фото.")
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    }) { Text(if (saving) "Сохранение…" else "Сохранить") }
                }
            }
        }
    }
}

private fun Bitmap.cropEdge(zoom: Float): Int = (minOf(width, height) / zoom).roundToInt().coerceAtLeast(1)

internal fun Bitmap.cropBounds(zoom: Float, centerX: Float, centerY: Float): Rect {
    val edge = cropEdge(zoom)
    val left = (width * centerX - edge / 2f).roundToInt().coerceIn(0, width - edge)
    val top = (height * centerY - edge / 2f).roundToInt().coerceIn(0, height - edge)
    return Rect(left, top, left + edge, top + edge)
}

internal fun Bitmap.encodeAvatar(bounds: Rect): String {
    for (maxSide in listOf(768, 640, 512)) {
        val side = minOf(maxSide, bounds.width())
        val square = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        try {
            val outputBounds = Rect(0, 0, side, side)
            BitmapCanvas(square).apply {
                drawColor(android.graphics.Color.BLACK)
                drawBitmap(this@encodeAvatar, bounds, outputBounds, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val buffer = ByteArrayOutputStream()
            for (quality in listOf(94, 88, 80)) {
                buffer.reset()
                if (square.compress(Bitmap.CompressFormat.JPEG, quality, buffer) && buffer.size() <= 300_000) {
                    return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
                }
            }
        } finally {
            square.recycle()
        }
    }
    throw UserFacingException("Фото слишком большое. Выберите другое изображение.")
}
