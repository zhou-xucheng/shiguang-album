package org.fossify.shiguang.stories

import androidx.annotation.Keep
import java.time.LocalDate
import java.util.UUID

@Keep
data class StoryMoment(
    val id: String = UUID.randomUUID().toString(),
    var uri: String,
    val video: Boolean = false,
    var date: String = "",
    var caption: String = "",
    var sourceUri: String? = "",
    var dateVerified: Boolean = false,
    var dateSource: String = "",
    var addedOrder: Long = 0
) {
    // Let Gson initialize Kotlin defaults when older metadata omits optional fields.
    private constructor() : this(uri = "")
}

@Keep
data class Story(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "新的故事",
    var date: String = "",
    var description: String = "",
    val moments: MutableList<StoryMoment> = mutableListOf(),
    // Legacy fields retained for non-destructive upgrades and old archive compatibility.
    var musicUri: String = "",
    var musicName: String = "",
    var intervalSeconds: Int = 5,
    var loop: Boolean = false,
    var originalSound: Boolean = true,
    var transition: Boolean = true,
    var updatedAt: Long = System.currentTimeMillis(),
    var deletedAt: Long = 0,
    var draft: Boolean = false,
    var coverId: String = "",
    var coverFit: Boolean = false,
    var musicVolume: Int = 65,
    var coverX: Float = .5f,
    var coverY: Float = .5f,
    var coverZoom: Float = 1f,
    var dateConfirmed: Boolean = false,
    var dateManual: Boolean = false,
    var favorite: Boolean = false,
    var pinned: Boolean = false
) {
    fun moveMoment(from: Int, to: Int) {
        if (from !in moments.indices || to !in moments.indices || from == to) return
        moments.add(to, moments.removeAt(from))
    }

    fun addMoments(items: List<StoryMoment>) {
        ensureOriginalOrder()
        val known = moments.mapTo(mutableSetOf()) { it.sourceUri.orEmpty().ifBlank { it.uri } }
        var order = moments.maxOfOrNull { it.addedOrder } ?: 0L
        moments.addAll(items.filter { known.add(it.sourceUri.orEmpty().ifBlank { it.uri }) }.onEach { it.addedOrder = ++order })
    }

    fun ensureOriginalOrder() { if (moments.any { it.addedOrder == 0L }) moments.forEachIndexed { i, m -> m.addedOrder = i + 1L } }
    fun sortChronologically(descending: Boolean = false) {
        ensureOriginalOrder()
        moments.sortWith(compareBy<StoryMoment> { !StoryDates.usable(it) }.thenComparator { a, b ->
            if (!StoryDates.usable(a) || !StoryDates.usable(b)) 0 else if (descending) b.date.compareTo(a.date) else a.date.compareTo(b.date)
        })
    }
    fun restoreOriginalOrder() { ensureOriginalOrder(); moments.sortBy { it.addedOrder } }
    fun moveSelected(ids: Set<String>, toStart: Boolean) {
        ensureOriginalOrder()
        val chosen = moments.filter { it.id in ids }; moments.removeAll { it.id in ids }
        moments.addAll(if (toStart) 0 else moments.size, chosen)
    }
    fun year(): String = if (date.isNotBlank() && (dateManual || StoryDates.credible(date))) date.take(4) else "未注明"

    fun dateLabel() = when {
        date.isBlank() -> "日期待补充"
        !dateManual && !StoryDates.credible(date) -> "日期待核对"
        !dateConfirmed -> date.replace('-', '.') + " · 待核对"
        else -> date.replace('-', '.')
    }

    fun isEmptyPlaceholder() = draft && deletedAt == 0L && moments.isEmpty() && description.isBlank() &&
        title in listOf("新的故事", "我的故事") && musicUri.isBlank() && date.isBlank()

    fun videoSoundEnabled(single: Boolean) = single || originalSound

    fun cover() = moments.find { it.id == coverId } ?: moments.firstOrNull()

    fun dateGroups() = moments.groupBy { it.date.takeIf { _ -> StoryDates.usable(it) } ?: "" }.toSortedMap()

    /** Suggest only from verified dates; never infer places or occasions from filenames. */
    fun suggestedTitle(): String? {
        val dates = moments.filter { StoryDates.usable(it) }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.sorted()
        if (dates.isEmpty()) return null
        val first = dates.first(); val last = dates.last()
        return when {
            first == last -> "${first.year}年${first.monthValue}月${first.dayOfMonth}日的回忆"
            first.year == last.year && first.month == last.month -> "${first.year}年${first.monthValue}月的回忆"
            first.year == last.year -> "${first.year}年的回忆"
            else -> "${first.year}—${last.year}的回忆"
        }
    }
}
