package org.fossify.shiguang.stories

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.AppCompatImageView

/** The same crop calculation is used in the editor and the story list. */
class StoryCoverView(context: Context) : AppCompatImageView(context) {
    var fit = false
    var focusX = .5f
    var focusY = .5f
    fun position(story: Story) { fit = story.coverFit; focusX = story.coverX; focusY = story.coverY; updateCrop() }
    override fun setImageDrawable(drawable: Drawable?) { super.setImageDrawable(drawable); updateCrop() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); updateCrop() }
    private fun updateCrop() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        scaleType = ScaleType.MATRIX
        val sx = width.toFloat() / d.intrinsicWidth; val sy = height.toFloat() / d.intrinsicHeight
        val scale = if (fit) minOf(sx, sy) else maxOf(sx, sy)
        val x = if (fit) .5f else focusX.coerceIn(0f, 1f)
        val y = if (fit) .5f else focusY.coerceIn(0f, 1f)
        imageMatrix = Matrix().apply { setScale(scale, scale); postTranslate((width - d.intrinsicWidth * scale) * x, (height - d.intrinsicHeight * scale) * y) }
    }
}
