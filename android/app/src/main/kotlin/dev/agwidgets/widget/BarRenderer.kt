package dev.agwidgets.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.roundToInt

object BarRenderer {
    fun renderBar(fraction: Float, fillColor: Int, trackColor: Int): Bitmap {
        val width = 240
        val height = 8
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = trackColor
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), height / 2f, height / 2f, paint)
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped > 0.002f) {
            paint.color = fillColor
            val fillWidth = (width * clamped).roundToInt().coerceAtLeast(height)
            canvas.drawRoundRect(RectF(0f, 0f, fillWidth.toFloat(), height.toFloat()), height / 2f, height / 2f, paint)
        }
        return bmp
    }
}
