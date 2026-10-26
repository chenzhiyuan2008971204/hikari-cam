package com.hikari.cam.data

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Orange film-camera date imprint, like '26 10 27 in the bottom-right corner. */
object DateStamp {
    fun draw(bitmap: Bitmap, date: Date = Date()): Bitmap {
        val out = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val text = SimpleDateFormat("''yy M d", Locale.US).format(date)
        val size = minOf(out.width, out.height) * 0.045f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = size
            letterSpacing = 0.08f
        }
        val w = paint.measureText(text)
        val x = out.width - w - out.width * 0.06f
        val y = out.height - out.height * 0.05f
        // Glow, then core, for the LED-burned look.
        paint.color = Color.argb(150, 255, 110, 30)
        paint.maskFilter = BlurMaskFilter(size * 0.25f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(text, x, y, paint)
        paint.maskFilter = null
        paint.color = Color.argb(230, 255, 150, 60)
        canvas.drawText(text, x, y, paint)
        return out
    }
}
