package org.fossify.shiguang.stories

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StoryDatesTest {
    @Test fun zeroContainerDateFallsBackToLibraryInsteadOf1904() {
        assertEquals("2026-09-19" to "library", StoryDates.choose(StoryDates.parse("19040101T000000.000Z"), "2026-09-19"))
        assertEquals("" to "unknown", StoryDates.choose("1904-01-01", "1970-01-01"))
    }
    @Test fun dateParsingHandlesExifIsoCompactAndRejectsMalformedValues() {
        listOf("20260919T100000.000Z", "2026-09-19T10:00:00Z", "2026:09:19 10:00:00").forEach { assertEquals("2026-09-19", StoryDates.parse(it)) }
        assertNull(StoryDates.parse("20260230")); assertNull(StoryDates.parse("none")); assertNull(StoryDates.parse(null))
        assertFalse(StoryDates.credible("2099-01-01", LocalDate.of(2026,9,19)))
        assertTrue(StoryDates.credible("1950-05-01", LocalDate.of(2026,9,19)))
    }
    @Test fun manualHistoricalDateSurvivesButLegacySentinelDoesNotAffectTitleOrSort() {
        val bad = StoryMoment(uri="bad", date="1904-01-01", dateVerified=true)
        val good = StoryMoment(uri="good",date="2020-01-01",dateVerified=true)
        val story = Story(moments= mutableListOf(bad,good))
        assertEquals("2020年1月1日的回忆",story.suggestedTitle())
        story.sortChronologically(); assertEquals(listOf("good","bad"), story.moments.map { it.uri })
        bad.dateSource="manual"; assertTrue(StoryDates.usable(bad))
    }
    @Test fun descendingSortKeepsUnknownDatesLastAndSameDayStable() {
        val s = Story(moments= mutableListOf(StoryMoment(uri="a",date="2020-01-01",dateVerified=true),StoryMoment(uri="unknown"),StoryMoment(uri="b",date="2023-01-01",dateVerified=true),StoryMoment(uri="c",date="2023-01-01",dateVerified=true)))
        s.sortChronologically(true); assertEquals(listOf("b","c","a","unknown"),s.moments.map { it.uri })
        s.restoreOriginalOrder(); assertEquals(listOf("a","unknown","b","c"),s.moments.map { it.uri })
    }
    @Test fun hundredMomentsKeepSelectionOrderAfterBulkMoveAndNewImport() {
        val s = Story(moments=(1..100).map { StoryMoment(id="$it",uri="$it") }.toMutableList())
        s.moveSelected(setOf("99","100"),true)
        assertEquals(listOf("99","100","1"),s.moments.take(3).map { it.uri })
        s.addMoments(listOf(StoryMoment(uri="101")))
        s.restoreOriginalOrder(); assertEquals((1..101).map { "$it" },s.moments.map { it.uri })
    }
    @Test fun oldDataGetsSafeDefaultsWithoutOverwritingItsDates() {
        val s = Gson().fromJson("""{"id":"old","title":"旧故事","date":"1904-01-01","dateConfirmed":true,"moments":[{"uri":"old","date":"1904-01-01","dateVerified":true}]}""",Story::class.java)
        assertFalse(s.favorite); assertFalse(s.pinned); assertFalse(s.dateManual)
        assertEquals("未注明",s.year()); assertEquals("1904-01-01",s.moments[0].date)
        assertFalse(StoryDates.usable(s.moments[0]))
    }
}
