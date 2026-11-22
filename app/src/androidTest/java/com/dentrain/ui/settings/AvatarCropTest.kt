package com.dentrain.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import kotlin.random.Random

internal object AvatarCropTest {
    fun run() {
        pannedCropSavesOnlyTheSelectedArea()
        detailedPhotoFitsTheFirestoreLimit()
    }

    private fun pannedCropSavesOnlyTheSelectedArea() {
        val photo = Bitmap.createBitmap(1800, 1000, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.RED)
            drawRect(1000f, 0f, 1800f, 1000f, Paint().apply { color = Color.BLUE })
        }

        val bounds = photo.cropBounds(2f, 1f, 0f)
        check(bounds == Rect(1300, 0, 1800, 500))
        val saved = decode(photo.encodeAvatar(bounds))
        check(saved.width == 500 && saved.height == 500)
        val center = saved.getPixel(250, 250)
        check(Color.blue(center) > 240 && Color.red(center) < 15)
    }

    private fun detailedPhotoFitsTheFirestoreLimit() {
        val side = 900
        val random = Random(7)
        val pixels = IntArray(side * side) { random.nextInt() or Color.BLACK }
        val photo = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        val encoded = photo.encodeAvatar(photo.cropBounds(1f, 0.5f, 0.5f))

        check(encoded.length <= 400_000)
        val saved = decode(encoded)
        check(saved.width == saved.height && saved.width in 512..768)
    }

    private fun decode(avatar: String): Bitmap {
        val bytes = Base64.decode(avatar, Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
