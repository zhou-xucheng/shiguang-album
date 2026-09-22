package org.fossify.shiguang.stories

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class StoryTest {
    @Test fun missingLegacyMomentMetadataUsesDefaultsDuringEditing() {
        val story = Gson().fromJson("""{"moments":[{"id":"photo","uri":"content://photos/1"}]}""", Story::class.java)
        val moment = story.moments.single()
        assertEquals("", moment.date)
        assertEquals("", moment.caption)
        assertEquals("", moment.dateSource)
        assertNull(story.suggestedTitle())
        story.sortChronologically()
        assertEquals(listOf(moment), story.dateGroups()[""])
        assertEquals(story, Gson().fromJson(Gson().toJson(story), Story::class.java))
    }
    @Test fun unknownStoryDatesRemainUnknownAndLegacyDatesNeedConfirmation() {
        assertEquals("", Story().date)
        assertEquals("日期待补充", Story().dateLabel())
        val old = Gson().fromJson("""{"date":"2020-01-02"}""", Story::class.java)
        assertEquals("2020.01.02 · 待核对", old.dateLabel())
        old.dateConfirmed = true
        assertEquals("2020.01.02", old.dateLabel())
    }
    @Test fun unverifiedDatesSortLastAndRemainStable() {
        val story = Story(moments = mutableListOf(StoryMoment(uri = "old", date = "2000-01-01"), StoryMoment(uri = "unknown"), StoryMoment(uri = "verified", date = "2020-01-01", dateVerified = true)))
        story.sortChronologically()
        assertEquals(listOf("verified", "old", "unknown"), story.moments.map { it.uri })
    }
    @Test fun singleVideoRetainsOriginalSoundWhenStoryIsMuted() {
        val story = Story(originalSound = false)
        assertTrue(story.videoSoundEnabled(true)); assertFalse(story.videoSoundEnabled(false))
    }
    @Test fun archiveBudgetCountsMediaAndMetadataTogetherAtExactBoundary() {
        val budget = ArchiveBudget(ArchiveLimits(bytes = 10, metadataBytes = 4, entries = 2))
        budget.begin("media/0"); budget.add(6); budget.end()
        budget.begin("stories.json"); budget.add(4); budget.end()
        assertTrue(runCatching { budget.add(1) }.isFailure)
    }
    @Test fun archivePolicyRejectsOversizedTextBeforeExportAndRestore() {
        val archive = StoryArchive(stories = listOf(Story(description = "x".repeat(100001))))
        assertTrue(runCatching { ArchivePolicy.validate(archive, ArchiveLimits()) }.isFailure)
    }
    @Test fun titleSuggestionsUseOnlyVerifiedDates() {
        val story = Story(moments = mutableListOf(StoryMoment(uri = "a", date = "2020-05-03")))
        assertNull(story.suggestedTitle())
        story.moments.first().dateVerified = true
        assertEquals("2020年5月3日的回忆", story.suggestedTitle())
        story.moments.add(StoryMoment(uri = "b", date = "2020-05-20", dateVerified = true))
        assertEquals("2020年5月的回忆", story.suggestedTitle())
        story.moments.add(StoryMoment(uri = "c"))
        assertEquals(1, story.dateGroups()[""]?.size)
    }
    @Test fun legacyCoversStayCentered() {
        val story = Gson().fromJson("""{"id":"old","title":"old"}""", Story::class.java)
        assertEquals(.5f, story.coverX); assertEquals(.5f, story.coverY); assertEquals(1f, story.coverZoom)
    }
    @Test fun legacyMomentsCanBeReadAndDeduplicated() {
        val legacy = Gson().fromJson("""{"id":"old","title":"旧版故事","date":"2020-01-01","moments":[{"id":"m","uri":"content://old/1","date":"2020-01-01","caption":"往事"}]}""", Story::class.java)
        legacy.addMoments(listOf(StoryMoment(uri = "content://old/1")))
        assertEquals(1, legacy.moments.size)
        assertFalse(legacy.draft)
        assertEquals(65, legacy.musicVolume)
        assertFalse(legacy.moments.first().dateVerified)
    }

    @Test fun unknownDatesStayAtEndAndSourceCopiesDeduplicate() {
        val story = Story(moments = mutableListOf(StoryMoment(uri = "unknown"), StoryMoment(uri = "copy", sourceUri = "original", date = "2001-01-01", dateVerified = true)))
        story.addMoments(listOf(StoryMoment(uri = "another-copy", sourceUri = "original")))
        story.sortChronologically()
        assertEquals(listOf("copy", "unknown"), story.moments.map { it.uri })
    }
    @Test fun repeatedSelectionDoesNotDuplicateMoments() {
        val story = Story()
        story.addMoments(listOf(StoryMoment(uri = "content://photos/1"), StoryMoment(uri = "content://photos/1")))
        story.addMoments(listOf(StoryMoment(uri = "content://photos/1"), StoryMoment(uri = "content://photos/2", video = true)))
        assertEquals(listOf("content://photos/1", "content://photos/2"), story.moments.map { it.uri })
    }

    @Test fun movingFirstToLastKeepsAllOriginalItemsAndCaptions() {
        val story = Story(moments = mutableListOf(StoryMoment(uri = "a", caption = "团聚"), StoryMoment(uri = "b"), StoryMoment(uri = "c")))
        story.moveMoment(0, 2)
        assertEquals(listOf("b", "c", "a"), story.moments.map { it.uri })
        assertEquals("团聚", story.moments.last().caption)
        story.moveMoment(-1, 0)
        story.moveMoment(0, 9)
        assertEquals(listOf("b", "c", "a"), story.moments.map { it.uri })
    }

    @Test fun chronologicalSortPreservesOrderWithinADate() {
        val story = Story(moments = mutableListOf(StoryMoment(uri = "a", date = "2026-09-15", dateVerified = true), StoryMoment(uri = "b", date = "1999-01-01", dateVerified = true), StoryMoment(uri = "c", date = "2026-09-15", dateVerified = true)))
        story.sortChronologically()
        assertEquals(listOf("b", "a", "c"), story.moments.map { it.uri })
    }

    @Test fun savedStoryRoundTripKeepsMusicPlaybackOptionsAndDeletionState() {
        val story = Story(title = "旅行与日常", musicUri = "content://music/1", musicName = "喜欢的歌", intervalSeconds = 8,
            loop = true, originalSound = false, transition = false, deletedAt = 12345,
            moments = mutableListOf(StoryMoment(uri = "content://photos/1", caption = "第一天\n海边", date = "2020-05-01")))
        val gson = Gson()
        assertEquals(story, gson.fromJson(gson.toJson(story), Story::class.java))
    }
}
