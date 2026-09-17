package org.fossify.shiguang.stories

import java.time.LocalDate

internal data class ArchiveLimits(
    val bytes: Long = 20L * 1024 * 1024 * 1024,
    val metadataBytes: Long = 16L * 1024 * 1024,
    val entries: Int = 50000,
    val stories: Int = 10000
)

/** Used by both directions so every exported archive fits the restore contract. */
internal class ArchiveBudget(private val limits: ArchiveLimits) {
    private var total = 0L
    private var entryBytes = 0L
    private var current = ""
    private val names = mutableSetOf<String>()
    fun begin(name: String) {
        require(name == "stories.json" || name.matches(Regex("media/[0-9]+"))) { "备份包含不支持的路径" }
        require(names.size < limits.entries && names.add(name)) { "备份文件数量超限或有重复项目" }
        current = name; entryBytes = 0
    }
    fun add(count: Int) {
        require(count >= 0)
        total += count; entryBytes += count
        require(total <= limits.bytes && (current != "stories.json" || entryBytes <= limits.metadataBytes)) {
            "备份超过当前可恢复的容量限制，未完成备份。请保留应用内的故事"
        }
    }
    fun end() { require(entryBytes > 0) { "备份文件为空，未完成操作" } }
}

internal object ArchivePolicy {
    fun validate(archive: StoryArchive, limits: ArchiveLimits) {
        require(archive.version in 1..2 && archive.stories.isNotEmpty() && archive.stories.size <= limits.stories) { "备份版本不支持、故事数量超限或内容为空" }
        archive.stories.forEach { s ->
            require(s.title.length <= 10000 && s.description.length <= 100000 && s.moments.size <= 50000) { "故事文字或片段数量超过备份限制" }
            if (s.date.isNotBlank()) LocalDate.parse(s.date)
            require(s.intervalSeconds in 1..60 && s.musicVolume in 0..100)
            require(s.coverX.isFinite() && s.coverY.isFinite() && s.coverX in 0f..1f && s.coverY in 0f..1f)
            require(s.moments.map { it.id }.distinct().size == s.moments.size)
            s.moments.forEach { m ->
                require(m.id.length in 1..100 && m.caption.length <= 100000 && m.uri.matches(Regex("media/[0-9]+"))) { "片段信息不符合备份要求" }
                if (m.date.isNotBlank()) LocalDate.parse(m.date)
            }
            require(s.musicName.length <= 10000)
            if (s.musicUri.isNotBlank()) require(s.musicUri.matches(Regex("media/[0-9]+")))
        }
    }
}
