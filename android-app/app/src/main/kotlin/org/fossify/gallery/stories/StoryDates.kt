package org.fossify.shiguang.stories

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** A syntactically valid container timestamp is not necessarily a capture date. */
object StoryDates {
    fun parse(raw: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
        val value = raw?.trim().orEmpty()
        val normalized = when {
            Regex("^\\d{8}.*").matches(value) -> "${value.take(4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
            value.length >= 10 -> value.take(10).replace(':', '-')
            else -> return null
        }
        val date = runCatching { LocalDate.parse(normalized).toString() }.getOrNull() ?: return null
        // Container epoch sentinels must stay recognizable even when the local day differs.
        if (date in setOf("1904-01-01", "1970-01-01", "0001-01-01")) return date
        if (!value.contains('T')) return date // EXIF has a local wall-clock date without an offset.
        val iso = if (value.length > 15 && value[8] == 'T') {
            "$date" + "T${value.substring(9, 11)}:${value.substring(11, 13)}:${value.substring(13)}"
        } else value
        val withOffset = iso.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        return runCatching { OffsetDateTime.parse(withOffset).atZoneSameInstant(zone).toLocalDate().toString() }.getOrNull()
    }

    fun credible(date: String?, today: LocalDate = LocalDate.now()): Boolean {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return parsed.year >= 1826 && !parsed.isAfter(today.plusDays(1)) &&
            date !in setOf("1904-01-01", "1970-01-01", "0001-01-01")
    }

    fun usable(moment: StoryMoment): Boolean = moment.date.isNotBlank() &&
        (moment.dateSource == "manual" || (moment.dateVerified && credible(moment.date)))

    fun choose(embedded: String?, library: String?): Pair<String, String> = when {
        credible(embedded) -> embedded!! to "embedded"
        credible(library) -> library!! to "library"
        else -> "" to "unknown"
    }
}
