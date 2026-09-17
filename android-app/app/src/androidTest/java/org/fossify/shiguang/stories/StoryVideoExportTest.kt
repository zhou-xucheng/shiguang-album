package org.fossify.shiguang.stories

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.test.InstrumentationTestCase
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise Android decoders, image composition, video framing, audio and the real MP4 muxer. */
@Suppress("DEPRECATION")
class StoryVideoExportTest : InstrumentationTestCase() {
    fun testPhotoAlbumAndMixedVideoAlbumExport() {
        val context = instrumentation.targetContext
        val fixtures = File(context.cacheDir, "v070-fixtures").apply { mkdirs() }
        val photo = File(fixtures, "photo.jpg")
        val bitmap = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(189, 218, 213))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(57, 100, 84) }
        canvas.drawCircle(450f, 550f, 240f, paint)
        paint.color = Color.WHITE; paint.textSize = 64f
        canvas.drawText("测试照片 · 美好的一天", 85f, 1000f, paint)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }; bitmap.recycle()
        val video = File(fixtures, "video.mp4")
        instrumentation.context.assets.open("share-test-video.mp4").use { input -> video.outputStream().use { input.copyTo(it) } }
        val story = Story(title = "值得珍藏的日子", date = "2026-09-17", dateConfirmed = true,
            description = "把照片和那一天的声音，送给牵挂的人。", intervalSeconds = 2,
            moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString(), date = "2026-09-17", dateVerified = true, caption = "一起走过的路，都是珍贵的回忆。")))
        try {
            export(story, "v070-photos.mp4", 5000L, expectAudio = false)
            story.moments.add(StoryMoment(uri = Uri.fromFile(video).toString(), video = true, caption = "听听那一天，看看那时的笑容。"))
            export(story, "v070-mixed.mp4", 9000L, expectAudio = true)
            export(story, "v080-cinema.mp4", 9000L, expectAudio = true, style = AlbumStyle.CINEMA)
            export(story, "v080-journal.mp4", 9000L, expectAudio = true, style = AlbumStyle.JOURNAL)
            story.originalSound = false
            export(story, "v070-muted.mp4", 9000L, expectAudio = false)
        } finally { fixtures.deleteRecursively() }
    }

    private fun export(story: Story, name: String, expectedDurationMs: Long, expectAudio: Boolean, style: AlbumStyle = AlbumStyle.PAPER) {
        val context = instrumentation.targetContext
        val ready = StoryVideoExport.prepare(context, story, style) {}
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        var transformer: Transformer? = null
        try {
            instrumentation.runOnMainSync {
                try {
                    transformer = Transformer.Builder(context).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) { latch.countDown() }
                            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) { failure = exception; latch.countDown() }
                        }).build()
                    transformer!!.start(ready.composition, ready.output.absolutePath)
                } catch (e: Throwable) { failure = e; latch.countDown() }
            }
            assertTrue("Export timed out", latch.await(150, TimeUnit.SECONDS))
            failure?.let { throw AssertionError("$name export failed", it) }
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(ready.output.absolutePath)
                val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                val format = formats.single { it.getString(MediaFormat.KEY_MIME) == "video/avc" }
                val width = format.getInteger(MediaFormat.KEY_WIDTH); val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                // Media3 may encode landscape pixels with a 90-degree MP4 display transform.
                assertEquals(setOf(720, 1280), setOf(width, height))
                assertTrue(kotlin.math.abs(format.getLong(MediaFormat.KEY_DURATION) / 1000 - expectedDurationMs) < 200)
                assertEquals(expectAudio, formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true })
            } finally { extractor.release() }
            val reader = MediaMetadataRetriever()
            try {
                reader.setDataSource(ready.output.absolutePath)
                val sampleTimes = if (story.moments.any { it.video }) listOf(0L, 1_000_000L, 3_800_000L, 6_500_000L) else listOf(0L, 1_000_000L, 3_800_000L)
                for (timeUs in sampleTimes) {
                    val frame = reader.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 72, 128)!!
                    var colored = 0
                    for (y in 0 until frame.height) for (x in 0 until frame.width) {
                        val pixel = frame.getPixel(x, y)
                        val bg = style.background
                        if (kotlin.math.abs(Color.red(pixel) - Color.red(bg)) + kotlin.math.abs(Color.green(pixel) - Color.green(bg)) + kotlin.math.abs(Color.blue(pixel) - Color.blue(bg)) > 75) colored++
                    }
                    assertTrue("Blank frame at $timeUs in $name", colored > frame.width * frame.height / 10)
                    frame.recycle()
                }
            } finally { reader.release() }
            ready.output.copyTo(File(context.cacheDir, name), overwrite = true)
        } finally {
            instrumentation.runOnMainSync { transformer?.cancel() }
            ready.directory.deleteRecursively()
        }
    }
}
