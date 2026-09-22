package org.fossify.shiguang.stories

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/** The same crop calculation is used in the editor and the story list. */
class StoryCoverView(context: Context) : AppCompatImageView(context) {
    var fit = false
    var focusX = .5f
    var focusY = .5f
    var zoom = 1f
    fun position(story: Story) {
        fit = story.coverFit
        focusX = story.coverX
        focusY = story.coverY
        zoom = story.coverZoom.coerceIn(1f, 4f)
        updateCrop()
    }

    fun editCrop(changed: (Float, Float, Float) -> Unit) {
        var lastX = 0f
        var lastY = 0f
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (fit) return false
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
                updateCrop()
                changed(focusX, focusY, zoom)
                return true
            }
        })
        setOnTouchListener { view, event ->
            scaleDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x; lastY = event.y
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (!fit && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val d = drawable
                    if (d != null && width > 0 && height > 0) {
                        val base = maxOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
                        val overflowX = (d.intrinsicWidth * base * zoom - width).coerceAtLeast(0f)
                        val overflowY = (d.intrinsicHeight * base * zoom - height).coerceAtLeast(0f)
                        if (overflowX > 0f) focusX = (focusX - (event.x - lastX) / overflowX).coerceIn(0f, 1f)
                        if (overflowY > 0f) focusY = (focusY - (event.y - lastY) / overflowY).coerceIn(0f, 1f)
                        lastX = event.x; lastY = event.y
                        updateCrop()
                        changed(focusX, focusY, zoom)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }
    override fun setImageDrawable(drawable: Drawable?) { super.setImageDrawable(drawable); updateCrop() }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); updateCrop() }
    private fun updateCrop() {
        val d = drawable ?: return
        if (width <= 0 || height <= 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        scaleType = ScaleType.MATRIX
        val sx = width.toFloat() / d.intrinsicWidth; val sy = height.toFloat() / d.intrinsicHeight
        val scale = if (fit) minOf(sx, sy) else maxOf(sx, sy) * zoom.coerceIn(1f, 4f)
        val x = if (fit) .5f else focusX.coerceIn(0f, 1f)
        val y = if (fit) .5f else focusY.coerceIn(0f, 1f)
        imageMatrix = Matrix().apply { setScale(scale, scale); postTranslate((width - d.intrinsicWidth * scale) * x, (height - d.intrinsicHeight * scale) * y) }
    }
}
