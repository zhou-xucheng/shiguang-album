package org.fossify.shiguang.stories

import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils

/** Fixed pixel canvas: exported typography must not depend on the phone's font scale. */
enum class AlbumStyle(val title: String, val detail: String, val background: Int, val foreground: Int, val secondary: Int, val highlight: Int) {
    PAPER("纸质相册", "暖纸留白 · 像翻开一本相簿", Color.rgb(248,245,239), Color.rgb(52,46,41), Color.rgb(120,110,99), Color.rgb(138,89,63)),
    CINEMA("光影故事", "深色画幅 · 让影像更突出", Color.rgb(24,30,32), Color.rgb(245,244,239), Color.rgb(185,197,194), Color.rgb(212,187,143)),
    JOURNAL("简洁纪实", "清爽排版 · 留下日期与文字", Color.rgb(242,246,245), Color.rgb(31,65,57), Color.rgb(88,116,106), Color.rgb(58,115,95));
    companion object { fun from(value: String?) = entries.firstOrNull { it.name == value } ?: PAPER }
}

object StoryVideoArtwork {
    const val WIDTH = 720
    const val HEIGHT = 1280
    val paper = Color.rgb(248, 245, 239)
    private val ink = Color.rgb(52, 46, 41)
    private val muted = Color.rgb(120, 110, 99)
    private val accent = Color.rgb(138, 89, 63)
    val window = RectF(40f, 210f, 680f, 1010f)

    fun fit(width: Int, height: Int, area: RectF = window): RectF {
        val scale = minOf(area.width() / width.coerceAtLeast(1), area.height() / height.coerceAtLeast(1))
        val w = width * scale; val h = height * scale
        return RectF(area.centerX() - w / 2, area.centerY() - h / 2, area.centerX() + w / 2, area.centerY() + h / 2)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, width: Int, size: Float,
                     color: Int = ink, lines: Int = 1, serif: Boolean = false) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(if (serif) "serif" else "sans-serif", Typeface.NORMAL)
        }
        val clean = value.take(10000)
        val layout = StaticLayout.Builder.obtain(clean, 0, clean.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setLineSpacing(6f, 1f).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        canvas.save(); canvas.translate(x, y); layout.draw(canvas); canvas.restore()
    }

    fun window(style: AlbumStyle) = when (style) {
        AlbumStyle.PAPER -> RectF(window)
        AlbumStyle.CINEMA -> RectF(0f, 150f, 720f, 1040f)
        AlbumStyle.JOURNAL -> RectF(40f, 140f, 680f, 1000f)
    }
    private fun base(style: AlbumStyle): Bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(style.background) }

    fun cover(story: Story, photo: Bitmap, style: AlbumStyle = AlbumStyle.PAPER): Bitmap {
        val ink = style.foreground; val muted = style.secondary; val accent = style.highlight
        val out = base(style); val canvas = Canvas(out); val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val frame = RectF(54f, 82f, 666f, 754f)
        paint.color = if (style == AlbumStyle.CINEMA) Color.rgb(38,46,48) else Color.WHITE; canvas.drawRoundRect(frame, if (style == AlbumStyle.JOURNAL) 0f else 22f, if (style == AlbumStyle.JOURNAL) 0f else 22f, paint)
        val area = RectF(74f, 102f, 646f, 734f)
        canvas.drawBitmap(photo, null, fit(photo.width, photo.height, area), paint)
        text(canvas, "回 忆 相 册", 58f, 795f, 604, 22f, accent)
        text(canvas, story.title, 54f, 842f, 612, 54f, ink, lines = 3, serif = style != AlbumStyle.JOURNAL)
        val date = if (story.dateConfirmed && story.date.isNotBlank()) story.date.replace('-', '.') else ""
        text(canvas, date, 58f, 1064f, 600, 24f, muted)
        text(canvas, story.description.ifBlank { "把日子，慢慢收藏。" }, 58f, 1120f, 604, 25f, muted, 3)
        return out
    }

    fun page(story: Story, moment: StoryMoment, index: Int, photo: Bitmap? = null, videoHole: RectF? = null, style: AlbumStyle = AlbumStyle.PAPER): Bitmap {
        val ink = style.foreground; val muted = style.secondary; val accent = style.highlight
        val out = base(style); val canvas = Canvas(out); val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val area = window(style)
        text(canvas, story.title, 42f, 48f, 636, 32f, ink, serif = style != AlbumStyle.JOURNAL)
        paint.color = style.highlight; paint.strokeWidth = 2f
        canvas.drawLine(42f, 106f, if (style == AlbumStyle.JOURNAL) 126f else 678f, 106f, paint)
        if (style == AlbumStyle.PAPER) {
            text(canvas, (index + 1).toString().padStart(2, '0'), 42f, 146f, 85, 26f, accent)
            if (moment.dateVerified && moment.date.isNotBlank()) text(canvas, moment.date.replace('-', '.'), 130f, 149f, 520, 23f, muted)
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(30f, 200f, 690f, 1020f), 18f, 18f, paint)
        }
        if (photo != null) canvas.drawBitmap(photo, null, fit(photo.width, photo.height, area), paint)
        if (videoHole != null) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRect(videoHole, paint); paint.xfermode = null
        }
        text(canvas, moment.caption, 44f, 1050f, 632, 29f, ink, lines = 4)
        val footer = if (style != AlbumStyle.PAPER && moment.dateVerified && moment.date.isNotBlank()) "${moment.date.replace('-', '.')}    ·    ${index + 1} / ${story.moments.size}" else "${index + 1} / ${story.moments.size}"
        text(canvas, footer, 44f, 1220f, 632, 21f, muted)
        return out
    }
}
