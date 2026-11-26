package com.dentrain.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext

private val avatarCache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = key.length * 2 + value.allocationByteCount
}

@OptIn(ExperimentalCoroutinesApi::class)
private val avatarDecoder = Dispatchers.Default.limitedParallelism(2)

@Composable
internal fun rememberAvatarBitmap(source: String?): Bitmap? {
    if (source.isNullOrBlank() || source.startsWith("http")) return null

    return key(source) {
        val cached = remember { avatarCache.get(source) }
        produceState(cached) {
            if (cached == null) {
                value = withContext(avatarDecoder) {
                    avatarCache.get(source) ?: decodeAvatar(source)?.also { avatarCache.put(source, it) }
                }
            }
        }.value
    }
}

internal fun decodeAvatar(source: String): Bitmap? {
    val bytes = try {
        Base64.decode(source, Base64.DEFAULT)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    val longestSide = maxOf(options.outWidth, options.outHeight)
    if (longestSide <= 0) return null

    options.inSampleSize = 1
    while (longestSide / options.inSampleSize > 1024) options.inSampleSize *= 2
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}
