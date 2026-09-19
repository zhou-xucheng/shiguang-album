package org.fossify.shiguang.stories

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import kotlin.random.Random

object MemoryPaper {
    fun background(context: Context, color: Int): Drawable {
        if (!context.getSharedPreferences("memory-design", Context.MODE_PRIVATE).getBoolean("paper", true)) return ColorDrawable(color)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val random = Random(19)
        for (y in 0..63) for (x in 0..63) {
            val delta = random.nextInt(-2, 3)
            bitmap.setPixel(x, y, Color.rgb((Color.red(color) + delta).coerceIn(0,255), (Color.green(color) + delta).coerceIn(0,255), (Color.blue(color) + delta).coerceIn(0,255)))
        }
        return BitmapDrawable(context.resources, bitmap).apply { tileModeX = Shader.TileMode.REPEAT; tileModeY = Shader.TileMode.REPEAT }
    }
    fun reducedMotion(context: Context) = context.getSharedPreferences("memory-design", Context.MODE_PRIVATE).getBoolean("reduce_motion", false)
}
