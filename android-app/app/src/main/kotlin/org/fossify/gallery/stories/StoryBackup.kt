package org.fossify.shiguang.stories

import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import com.google.gson.Gson
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Keep data class StoryArchive(val version: Int = 2, val stories: List<Story> = emptyList())

/** A portable, self-contained archive. Restore adds copies and never overwrites existing stories. */
object StoryBackup {
    private val gson = Gson()
    fun export(context: Context, destination: Uri, progress: (String) -> Unit): Int = exportWithSize(context, destination, progress).first

    fun exportWithSize(context: Context, destination: Uri, progress: (String) -> Unit): Pair<Int, Long> = exportWithLimits(context, destination, ArchiveLimits(), progress)

    internal fun exportWithLimits(context: Context, destination: Uri, limits: ArchiveLimits, progress: (String) -> Unit): Pair<Int, Long> {
        val source = StoryStore(context).all()
        require(source.isNotEmpty()) { "还没有可备份的故事" }
        val copy = gson.fromJson(gson.toJson(StoryArchive(stories = source)), StoryArchive::class.java)
        // Built-in soundtracks are retired. Keep original records and imported audio intact;
        // omit only the retired asset references from the portable copy.
        copy.stories.filter { it.musicUri.startsWith("asset://") }.forEach {
            it.musicUri = ""; it.musicName = ""
        }
        val uris = copy.stories.flatMap { it.moments.map { m -> m.uri } + it.musicUri }.filter { it.isNotBlank() }.distinct()
        val paths = uris.mapIndexed { index, uri -> uri to "media/$index" }.toMap()
        copy.stories.forEach { story ->
            story.moments.forEach { it.uri = paths.getValue(it.uri); it.sourceUri = "" }
            if (story.musicUri.isNotBlank()) story.musicUri = paths.getValue(story.musicUri)
        }
        ArchivePolicy.validate(copy, limits)
        val metadata = gson.toJson(copy).toByteArray(Charsets.UTF_8)
        require(metadata.size <= limits.metadataBytes && paths.size + 1 <= limits.entries) { "备份信息或文件数量超过可恢复限制" }
        val budget = ArchiveBudget(limits)
        val temp = File.createTempFile("story-backup-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(temp.outputStream().buffered()).use { zip ->
                uris.forEachIndexed { index, uri ->
                    progress("正在备份文件 ${index + 1} / ${uris.size}")
                    budget.begin(paths.getValue(uri)); zip.putNextEntry(ZipEntry(paths.getValue(uri)))
                    val owner = source.first { s -> s.musicUri == uri || s.moments.any { it.uri == uri } }
                    val moment = owner.moments.indexOfFirst { it.uri == uri }
                    val label = "《${owner.title.take(40)}》" + if (moment >= 0) "第 ${moment + 1} 个片段" else "配乐"
                    try { StoryMedia.open(context, uri).use { input ->
                        val output = object : java.io.OutputStream() {
                            override fun write(b: Int) { budget.add(1); zip.write(b) }
                            override fun write(b: ByteArray, offset: Int, length: Int) { budget.add(length); zip.write(b, offset, length) }
                        }
                        StoryMedia.copyStream(input, output) { temp.parentFile!!.usableSpace }
                        budget.end()
                    } }
                    catch (e: Exception) { error("$label 备份未完成：${e.message ?: "文件无法读取"}。请检查文件和可用空间。") }
                    zip.closeEntry()
                }
                budget.begin("stories.json"); budget.add(metadata.size); budget.end()
                zip.putNextEntry(ZipEntry("stories.json")); zip.write(metadata); zip.closeEntry()
            }
            progress("正在保存备份…")
            context.contentResolver.openOutputStream(destination, "wt")!!.use { output -> temp.inputStream().use { it.copyTo(output) } }
            return source.size to temp.length()
        } finally { temp.delete() }
    }
    fun restore(context: Context, source: Uri, progress: (String) -> Unit): Int = restoreWithLimits(context, source, ArchiveLimits(), progress)

    internal fun restoreWithLimits(context: Context, source: Uri, limits: ArchiveLimits, progress: (String) -> Unit): Int {
        val folder = File(context.filesDir, "story-media/restored-${UUID.randomUUID()}").apply { mkdirs() }
        var committed = false
        try {
            var manifest: String? = null
            val budget = ArchiveBudget(limits)
            val files = mutableMapOf<String, File>()
            ZipInputStream(context.contentResolver.openInputStream(source)!!.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "备份格式不支持" }
                    val key = entry.name
                    budget.begin(key)
                    val outFile = File(folder, key.replace('/', '_'))
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(65536)
                        while (true) {
                            val count = zip.read(buffer); if (count < 0) break
                            budget.add(count)
                            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("恢复已中断")
                            require(folder.usableSpace > count + 4L * 1024 * 1024) { "手机空间不足，未恢复任何故事" }
                            output.write(buffer, 0, count)
                        }
                    }
                    budget.end()
                    files[key] = outFile
                    if (key == "stories.json") manifest = outFile.readText()
                    progress("已读取 ${files.size} 个备份项目")
                }
            }
            val archive = gson.fromJson(manifest ?: error("找不到故事信息"), StoryArchive::class.java)
            ArchivePolicy.validate(archive, limits)
            val restored = archive.stories.map { s ->
                s.moments.forEach { m ->
                    m.uri = Uri.fromFile(files[m.uri] ?: error("备份缺少照片或视频，未恢复任何故事")).toString()
                    m.sourceUri = ""
                }
                if (s.musicUri.isNotBlank()) {
                    s.musicUri = Uri.fromFile(files[s.musicUri] ?: error("备份缺少配乐，未恢复任何故事")).toString()
                }
                s.copy(id = UUID.randomUUID().toString(), updatedAt = System.currentTimeMillis())
            }
            StoryStore(context).merge(restored)
            committed = true
            return restored.size
        } finally { if (!committed) folder.deleteRecursively() }
    }
}
