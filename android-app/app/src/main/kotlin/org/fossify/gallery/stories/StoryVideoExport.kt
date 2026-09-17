package org.fossify.shiguang.stories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.*
import com.bumptech.glide.Glide
import java.io.File
import java.util.UUID

/** Local MP4 export. Preparation runs on a worker; Transformer is driven on the main thread. */
object StoryVideoExport {
    data class Prepared(val composition: Composition, val directory: File, val durationMs: Long) {
        val output get() = File(directory, "相册视频.mp4")
    }

    private fun checkWork(context: Context) {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        require(context.cacheDir.usableSpace > 96L * 1024 * 1024) { "手机剩余空间不足，请清理后重试。" }
    }

    fun prepare(context: Context, story: Story, style: AlbumStyle = AlbumStyle.PAPER, progress: (String) -> Unit): Prepared {
        require(story.moments.isNotEmpty()) { "先给故事添加照片或视频。" }
        require(story.moments.size <= 120) { "一个视频相册最多包含 120 个片段，请拆成几个故事分享。" }
        val parent = File(context.cacheDir, "shared-stories").apply { mkdirs() }
        // Retain recent outgoing files so another app can finish reading a shared URI.
        parent.listFiles().orEmpty().filter {
            it.isDirectory && it.name.matches(Regex("[a-f0-9-]{36}")) &&
                it.canonicalPath.startsWith(parent.canonicalPath + File.separator) &&
                System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000
        }.forEach { it.deleteRecursively() }
        val dir = File(parent, UUID.randomUUID().toString()).apply { mkdirs() }
        try {
            val durations = story.moments.map { moment ->
                checkWork(context)
                if (!moment.video) story.intervalSeconds.coerceIn(1, 60) * 1000L else {
                    val reader = MediaMetadataRetriever()
                    try {
                        reader.setDataSource(context, Uri.parse(moment.uri))
                        reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                            ?.takeIf { it > 0 } ?: error("有视频无法读取时长，请检查原文件。")
                    } finally { reader.release() }
                }
            }
            val duration = 3000L + durations.sum()
            require(duration <= 20 * 60 * 1000L) { "这个故事超过 20 分钟，请拆成几个视频相册。" }
            require(context.cacheDir.usableSpace > duration * 700L + 128L * 1024 * 1024) { "生成视频需要更多临时空间，请清理后重试。" }
            val items = mutableListOf<EditedMediaItem>()
            val cover = story.cover() ?: error("故事没有封面")
            progress("正在制作封面…")
            writeImage(context, cover.uri, File(dir, "cover.jpg")) { StoryVideoArtwork.cover(story, it, style) }
            items.add(imageItem(File(dir, "cover.jpg"), 3000, story.transition, background = style.background))
            var startMs = 3000L
            story.moments.forEachIndexed { index, moment ->
                checkWork(context); progress("正在排版 ${index + 1} / ${story.moments.size}")
                if (!moment.video) {
                    val file = File(dir, "page-$index.jpg")
                    writeImage(context, moment.uri, file) { StoryVideoArtwork.page(story, moment, index, photo = it, style = style) }
                    items.add(imageItem(file, durations[index], story.transition, startMs, style.background))
                } else {
                    val layout = VideoLayout(style)
                    val overlay = object : BitmapOverlay() {
                        private var bitmap: Bitmap? = null
                        override fun getBitmap(timeUs: Long): Bitmap = bitmap ?: StoryVideoArtwork.page(story, moment, index, videoHole = layout.rect, style = style).also { bitmap = it }
                        override fun release() { super.release(); bitmap?.recycle(); bitmap = null }
                    }
                    val overlays = mutableListOf<androidx.media3.effect.TextureOverlay>(overlay)
                    if (story.transition) overlays.add(FadeOverlay(durations[index], startMs, style.background))
                    items.add(EditedMediaItem.Builder(MediaItem.fromUri(moment.uri))
                        .setRemoveAudio(!story.originalSound)
                        .setEffects(Effects(emptyList(), listOf(layout, OverlayEffect(overlays)))).build())
                }
                startMs += durations[index]
            }
            checkWork(context)
            val tracks = if (story.originalSound && story.moments.any { it.video }) setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO) else setOf(C.TRACK_TYPE_VIDEO)
            val sequence = EditedMediaItemSequence.Builder(tracks).addItems(items).build()
            val composition = Composition.Builder(sequence)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL).build()
            return Prepared(composition, dir, duration)
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }

    private fun writeImage(context: Context, uri: String, file: File, render: (Bitmap) -> Bitmap) {
        val request = Glide.with(context).asBitmap().disallowHardwareConfig().load(Uri.parse(uri)).submit(1280, 1280)
        try {
            val image = request.get(); checkWork(context)
            val page = render(image)
            try { file.outputStream().use { require(page.compress(Bitmap.CompressFormat.JPEG, 94, it)) { "相册画面保存失败。" } } }
            finally { page.recycle() }
        } finally { Glide.with(context).clear(request) }
    }

    private fun imageItem(file: File, durationMs: Long, fade: Boolean, startMs: Long = 0, background: Int = StoryVideoArtwork.paper): EditedMediaItem {
        val item = MediaItem.Builder().setUri(Uri.fromFile(file)).setMimeType(MimeTypes.IMAGE_JPEG).setImageDurationMs(durationMs).build()
        return EditedMediaItem.Builder(item).setFrameRate(30)
            .setEffects(Effects(emptyList(), if (fade) listOf(OverlayEffect(listOf(FadeOverlay(durationMs, startMs, background)))) else emptyList())).build()
    }

    private class VideoLayout(private val style: AlbumStyle) : MatrixTransformation {
        var rect = StoryVideoArtwork.window(style)
        private val matrix = Matrix()
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            rect = StoryVideoArtwork.fit(inputWidth, inputHeight, StoryVideoArtwork.window(style))
            matrix.setScale(rect.width() / 720f, rect.height() / 1280f)
            matrix.postTranslate((rect.centerX() - 360f) / 360f, (640f - rect.centerY()) / 640f)
            return Size(720, 1280)
        }
        override fun getMatrix(presentationTimeUs: Long): Matrix = matrix
    }

    private class FadeOverlay(private val durationMs: Long, private val startMs: Long = 0, private val background: Int = StoryVideoArtwork.paper) : BitmapOverlay() {
        private var bitmap: Bitmap? = null
        override fun getBitmap(timeUs: Long): Bitmap = bitmap ?: Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888).apply { eraseColor(background) }.also { bitmap = it }
        override fun getOverlaySettings(timeUs: Long): androidx.media3.common.OverlaySettings {
            // Multi-item effects receive composition timestamps, not time since this item began.
            val timeMs = timeUs / 1000f - startMs
            // Keep the very first frame visible: messaging apps often use it as the thumbnail.
            val edgeMs = if (startMs == 0L) durationMs - timeMs else minOf(timeMs, durationMs - timeMs)
            val alpha = (1f - edgeMs.coerceAtLeast(0f) / 250f).coerceIn(0f, 1f)
            return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
        }
        override fun release() { super.release(); bitmap?.recycle(); bitmap = null }
    }
}
