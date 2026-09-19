package org.fossify.shiguang.stories

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.util.UUID

object StoryMedia {
    /** Called once before activities start, so unfinished files cannot belong to a running import. */
    fun cleanupInterrupted(context: Context) {
        val directory = File(context.filesDir, "story-media")
        val children = directory.listFiles().orEmpty()
        if (children.isNotEmpty()) {
            val stories = StoryStore(context).all()
            val references = stories.flatMap { s -> s.moments.map { it.uri } + s.musicUri }.toSet()
            children.forEach { file ->
                if (file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.[a-zA-Z0-9]+\\.part"))) file.delete()
                if (file.isFile && file.name.matches(Regex("[a-f0-9-]{36}\\.[a-zA-Z0-9]+")) && Uri.fromFile(file).toString() !in references) file.delete()
                if (file.isDirectory && file.name.matches(Regex("restored-[a-f0-9-]{36}")) && references.none { it.startsWith(Uri.fromFile(file).toString() + "/") }) file.deleteRecursively()
            }
        }
        context.cacheDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("story-backup-") && it.extension == "zip" }.forEach { it.delete() }
    }
    fun open(context: Context, uri: String): InputStream {
        if (uri.startsWith("asset://")) return context.assets.open("music/" + uri.substringAfter("asset://"))
        return context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("文件无法读取")
    }
    fun playbackUri(uri: String): String = if (uri.startsWith("asset://")) "asset:///music/" + uri.substringAfter("asset://") else uri
    fun name(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    fun type(context: Context, uri: Uri): String = context.contentResolver.getType(uri)
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(uri.lastPathSegment.orEmpty().substringAfterLast('.').lowercase()).orEmpty()

    /** Copy first, publish only when complete. Original media are never changed. */
    fun copy(context: Context, uri: Uri): String {
        val directory = File(context.filesDir, "story-media").apply { mkdirs() }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(type(context, uri)) ?: "bin"
        val target = File(directory, "${UUID.randomUUID()}.$ext")
        val partial = File(directory, target.name + ".part")
        try {
            open(context, uri.toString()).use { input -> partial.outputStream().use { output -> copyStream(input, output) { directory.usableSpace } } }
            require(partial.length() > 0) { "文件为空" }
            check(partial.renameTo(target))
            return Uri.fromFile(target).toString()
        } catch (e: Exception) { partial.delete(); throw e }
    }
    internal fun copyStream(input: InputStream, output: java.io.OutputStream, freeBytes: () -> Long): Long {
        val buffer = ByteArray(65536)
        var total = 0L
        var nextCheck = 0L
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("保存已中断")
            val count = input.read(buffer)
            if (count < 0) return total
            if (total >= nextCheck) {
                require(freeBytes() > count + 4L * 1024 * 1024) { "手机可用空间不足" }
                nextCheck = total + 1024 * 1024
            }
            output.write(buffer, 0, count); total += count
        }
    }
    fun importMoment(context: Context, uri: Uri): StoryMoment {
        val type = type(context, uri)
        require(type.startsWith("image/") || type.startsWith("video/")) { "不是照片或视频" }
        val video = type.startsWith("video/")
        val (date, source) = captureDateInfo(context, uri, video)
        return StoryMoment(uri = copy(context, uri), video = video, date = date, sourceUri = uri.toString(), dateVerified = date.isNotBlank(), dateSource = source)
    }
    fun captureDate(context: Context, uri: Uri, video: Boolean): String = captureDateInfo(context, uri, video).first
    fun captureDateInfo(context: Context, uri: Uri, video: Boolean): Pair<String, String> {
        val embedded = runCatching {
            if (video) {
                val reader = MediaMetadataRetriever()
                try { reader.setDataSource(context, uri); StoryDates.parse(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)) }
                finally { reader.release() }
            } else open(context, uri.toString()).use { StoryDates.parse(ExifInterface(it).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) }
        }.getOrNull()
        val library = runCatching {
            context.contentResolver.query(uri, arrayOf("datetaken"), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0) && it.getLong(0) > 0) java.time.Instant.ofEpochMilli(it.getLong(0)).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() else ""
            }.orEmpty()
        }.getOrDefault("")
        return StoryDates.choose(embedded, library)
    }
    fun dateLabel(moment: StoryMoment) = when {
        moment.date.isBlank() -> "日期未知"
        moment.dateSource != "manual" && !StoryDates.credible(moment.date) -> "日期待核对"
        !moment.dateVerified -> moment.date.replace('-', '.') + " · 待核对"
        moment.dateSource == "manual" -> moment.date.replace('-', '.') + " · 已确认"
        else -> moment.date.replace('-', '.')
    }
}
