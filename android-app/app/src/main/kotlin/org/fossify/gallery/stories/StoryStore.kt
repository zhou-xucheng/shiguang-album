package org.fossify.shiguang.stories

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** Small local story index. Media remain in their original location; no originals are deleted here. */
class StoryStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val mediaDirectory = File(context.filesDir, "story-media")
    private val file = AtomicFile(File(context.filesDir, "stories-v1.json"))
    private val gson = Gson()

    fun all(): MutableList<Story> = synchronized(lock) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@synchronized mutableListOf()
        file.openRead().bufferedReader().use {
            gson.fromJson<MutableList<Story>>(it, object : TypeToken<MutableList<Story>>() {}.type)
                ?: error("故事索引无法读取")
        }
    }

    fun get(id: String): Story? = all().find { it.id == id }

    fun merge(stories: List<Story>) = synchronized(lock) {
        val current = all()
        require(stories.map { it.id }.distinct().size == stories.size)
        require(stories.none { item -> current.any { it.id == item.id } })
        write(current + stories)
    }

    fun save(story: Story) = synchronized(lock) {
        val items = all()
        val previousUris = referencedUris(items)
        val index = items.indexOfFirst { it.id == story.id }
        if (index < 0 && story.isEmptyPlaceholder()) return@synchronized
        if (index >= 0 && items[index].copy(updatedAt = 0) == story.copy(updatedAt = 0)) {
            story.updatedAt = items[index].updatedAt
            return@synchronized
        }
        require(index < 0 || story.updatedAt == items[index].updatedAt) { "故事已在其他页面更新，请返回后重试。" }
        val oldRevision = story.updatedAt
        story.updatedAt = maxOf(System.currentTimeMillis(), (items.getOrNull(index)?.updatedAt ?: 0) + 1)
        if (index < 0) items.add(story) else items[index] = story
        try { write(items) } catch (error: Exception) { story.updatedAt = oldRevision; throw error }
        releaseUnused(previousUris - referencedUris(items))
    }

    fun permanentlyDelete(id: String) = synchronized(lock) {
        val previous = all()
        val remaining = previous.filterNot { it.id == id }
        write(remaining)
        releaseUnused(referencedUris(previous) - referencedUris(remaining))
    }

    private fun referencedUris(items: List<Story>) = items.flatMap { it.moments.map { moment -> moment.uri } + it.musicUri }.filter { it.isNotBlank() }.toSet()

    private fun releaseUnused(uris: Set<String>) {
        uris.forEach { uri ->
            runCatching {
                val parsed = android.net.Uri.parse(uri)
                if (parsed.scheme == "file") {
                    val candidate = File(parsed.path ?: return@runCatching).canonicalFile
                    if (candidate.path.startsWith(mediaDirectory.canonicalPath + File.separator) && candidate.isFile) candidate.delete()
                } else if (parsed.scheme == "content") resolver.releasePersistableUriPermission(parsed, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun write(items: List<Story>) {
        val output = file.startWrite()
        try {
            output.write(gson.toJson(items).toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    companion object { private val lock = Any() }
}
