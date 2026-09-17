package org.fossify.shiguang.stories

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.test.InstrumentationTestCase
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Suppress("DEPRECATION")
class StoryStorageTest : InstrumentationTestCase() {
    fun testExistingEmptyDraftCanBeTrashed() {
        val store = StoryStore(context)
        val draft = Story(draft = true, moments = mutableListOf(StoryMoment(uri = "content://test/photo")))
        store.save(draft); draft.moments.clear(); store.save(draft)
        draft.deletedAt = 1234L; store.save(draft)
        assertEquals(1234L, store.get(draft.id)!!.deletedAt)
        val blank = Story(draft = true); store.save(blank)
        assertNull(store.get(blank.id))
    }
    fun testUnchangedSavePreservesTimestampAndIndexBytes() {
        val store = StoryStore(context); val story = Story(title = "不改动这个故事")
        store.save(story)
        val index = File(context.filesDir, "stories-v1.json")
        val original = index.readBytes(); val oldTime = story.updatedAt
        story.updatedAt = 1; store.save(story)
        assertEquals(oldTime, story.updatedAt); assertTrue(original.contentEquals(index.readBytes()))
        story.description = "增加一句话"; store.save(story)
        assertTrue(story.updatedAt > oldTime); assertFalse(original.contentEquals(index.readBytes()))
    }
    fun testExportLimitFailureLeavesDestinationUntouchedAndMatchesRestore() {
        val photo = File(directory, "large.jpg").apply { writeBytes(ByteArray(2048) { 1 }) }
        val store = StoryStore(context); store.save(Story(moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString()))))
        val destination = File(directory, "existing.zip").apply { writeText("keep-existing-backup") }
        val limits = ArchiveLimits(bytes = 1024)
        assertTrue(runCatching { StoryBackup.exportWithLimits(context, Uri.fromFile(destination), limits) {} }.isFailure)
        assertEquals("keep-existing-backup", destination.readText())
        val valid = File(directory, "valid.zip"); StoryBackup.export(context, Uri.fromFile(valid)) {}
        val before = store.all()
        assertTrue(runCatching { StoryBackup.restoreWithLimits(context, Uri.fromFile(valid), limits) {} }.isFailure)
        assertEquals(before, store.all())
    }
    fun testMetadataAndEntryLimitsApplyBeforeDestinationWrite() {
        val photo = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(1)) }
        StoryStore(context).save(Story(title = "备份检查", moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString()))))
        val target = File(directory, "backup.zip").apply { writeText("existing") }
        listOf(ArchiveLimits(metadataBytes = 1), ArchiveLimits(entries = 1), ArchiveLimits(stories = 0)).forEach { limit ->
            assertTrue(runCatching { StoryBackup.exportWithLimits(context, Uri.fromFile(target), limit) {} }.isFailure)
            assertEquals("existing", target.readText())
        }
    }
    fun testLegacyArchiveDatesAndNewUnknownDatesBothRestore() {
        val old = File(directory, "legacy.zip")
        ZipOutputStream(old.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("stories.json"))
            z.write("""{"version":1,"stories":[{"title":"旧故事","date":"2020-01-02","moments":[]}]}""".toByteArray()); z.closeEntry()
        }
        StoryBackup.restore(context, Uri.fromFile(old)) {}
        val store = StoryStore(context)
        assertEquals("2020.01.02 · 待核对", store.all().first().dateLabel())
        store.save(Story(title = "日期未知"))
        val fresh = File(directory, "new.zip"); StoryBackup.export(context, Uri.fromFile(fresh)) {}
        StoryBackup.restore(context, Uri.fromFile(fresh)) {}
        assertEquals("日期待补充", store.all().last().dateLabel())
    }
    fun testRestartCleanupPreservesCommittedMedia() {
        val root = File(context.filesDir, "story-media").apply { mkdirs() }
        val retained = File(root, "restored-${UUID.randomUUID()}").apply { mkdirs() }
        val photo = File(retained, "media_0").apply { writeBytes(byteArrayOf(1)) }
        val abandoned = File(root, "restored-${UUID.randomUUID()}").apply { mkdirs() }
        File(abandoned, "media_0").writeBytes(byteArrayOf(2))
        val partial = File(root, "${UUID.randomUUID()}.jpg.part").apply { writeBytes(byteArrayOf(3)) }
        val unpublished = File(root, "${UUID.randomUUID()}.jpg").apply { writeBytes(byteArrayOf(4)) }
        StoryStore(context).save(Story(moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString()))))
        StoryMedia.cleanupInterrupted(context)
        assertTrue(photo.exists()); assertFalse(abandoned.exists()); assertFalse(partial.exists())
        assertFalse(unpublished.exists())
    }
    fun testLowSpaceStopsCopyBeforeWriting() {
        val output = java.io.ByteArrayOutputStream()
        assertTrue(runCatching { StoryMedia.copyStream(byteArrayOf(1, 2, 3).inputStream(), output) { 1024L } }.isFailure)
        assertEquals(0, output.size())
    }
    fun testEmptySourceLeavesNoPartialFile() {
        val source = File(directory, "empty.jpg").apply { writeBytes(byteArrayOf()) }
        assertTrue(runCatching { StoryMedia.copy(context, Uri.fromFile(source)) }.isFailure)
        assertEquals(0, File(context.filesDir, "story-media").listFiles()!!.size)
    }
    fun testInterruptedRestoreLeavesExistingStoryAndCleansStaging() {
        val store = StoryStore(context); store.save(Story(title = "原有故事", musicUri = "asset://afternoon.wav"))
        val zip = File(directory, "interrupt.zip"); StoryBackup.export(context, Uri.fromFile(zip)) {}
        val before = store.all()
        assertTrue(runCatching { StoryBackup.restore(context, Uri.fromFile(zip)) { throw java.io.InterruptedIOException("模拟中途中断") } }.isFailure)
        assertEquals(before, store.all())
        assertTrue(File(context.filesDir, "story-media").listFiles().orEmpty().isEmpty())
    }
    fun testBulkStoryAndLargeMediaRoundTrip() {
        val original = File(directory, "large.mp4")
        val random = java.util.Random(19)
        original.outputStream().use { out -> repeat(256) { val bytes = ByteArray(65536); random.nextBytes(bytes); out.write(bytes) } }
        val uri = Uri.fromFile(original).toString()
        val story = Story(title = "大量片段", moments = (0 until 2000).map { StoryMoment(uri = uri, video = true, caption = "片段 $it") }.toMutableList(), coverX = .2f, coverY = .8f)
        val store = StoryStore(context); store.save(story)
        val hash = original.inputStream().use { input -> val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }; digest.digest() }
        val zip = File(directory, "bulk.zip"); StoryBackup.export(context, Uri.fromFile(zip)) {}; original.delete()
        StoryBackup.restore(context, Uri.fromFile(zip)) {}
        val restored = store.all().last()
        assertEquals(2000, restored.moments.size); assertEquals("片段 1999", restored.moments.last().caption)
        assertEquals(.2f, restored.coverX); assertEquals(.8f, restored.coverY)
        val restoredHash = StoryMedia.open(context, restored.moments.last().uri).use { input -> val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }; digest.digest() }
        assertTrue(hash.contentEquals(restoredHash))
    }
    private lateinit var directory: File
    private lateinit var context: ContextWrapper
    override fun setUp() {
        directory = File(instrumentation.targetContext.cacheDir, "storage-tests-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getApplicationContext() = this
        }
    }
    override fun tearDown() { directory.deleteRecursively() }
    fun testIndependentCopyAndCaptureDate() {
        val original = File(directory, "photo.jpg")
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        ExifInterface(original).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2018:04:05 14:15:00"); saveAttributes() }
        val imported = StoryMedia.importMoment(context, Uri.fromFile(original))
        assertEquals("2018-04-05", imported.date); assertTrue(imported.dateVerified)
        val bytes = original.readBytes(); original.delete()
        assertTrue(StoryMedia.open(context, imported.uri).use { it.readBytes() }.contentEquals(bytes))
    }
    fun testUnknownDateStaysUnknown() {
        val original = File(directory, "undated.jpg")
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        assertEquals("", StoryMedia.captureDate(context, Uri.fromFile(original), false))
    }
    fun testCompleteArchiveSurvivesMissingOriginalsAndPreservesExistingStories() {
        val photo = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val video = File(directory, "video.mp4").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val music = File(directory, "music.wav").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        val story = Story(title = "海边", description = "第一天", musicUri = Uri.fromFile(music).toString(), musicName = "音乐", draft = true,
            moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString(), caption = "海风", date = "2018-04-05", dateVerified = true), StoryMoment(uri = Uri.fromFile(video).toString(), video = true)))
        story.coverId = story.moments.last().id; story.musicVolume = 32
        val store = StoryStore(context); store.save(story)
        val zip = File(directory, "backup.zip")
        assertEquals(1, StoryBackup.export(context, Uri.fromFile(zip)) {})
        photo.delete(); video.delete(); music.delete()
        assertEquals(1, StoryBackup.restore(context, Uri.fromFile(zip)) {})
        val stories = store.all(); assertEquals(2, stories.size)
        val restored = stories.single { it.id != story.id }
        assertEquals("海边", restored.title); assertEquals("海风", restored.moments.first().caption)
        assertEquals(32, restored.musicVolume); assertEquals(story.coverId, restored.coverId); assertTrue(restored.draft)
        assertTrue(StoryMedia.open(context, restored.moments.first().uri).use { it.readBytes() }.contentEquals(byteArrayOf(1, 2, 3)))
        assertTrue(StoryMedia.open(context, restored.moments.last().uri).use { it.readBytes() }.contentEquals(byteArrayOf(4, 5, 6)))
        assertTrue(StoryMedia.open(context, restored.musicUri).use { it.readBytes() }.contentEquals(byteArrayOf(7, 8, 9)))
    }
    fun testIncompleteArchiveDoesNotChangeLibrary() {
        val store = StoryStore(context); store.save(Story(title = "保留这个故事"))
        val before = store.all()
        val zip = File(directory, "incomplete.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("stories.json"))
            z.write(com.google.gson.Gson().toJson(StoryArchive(stories = listOf(Story(moments = mutableListOf(StoryMoment(uri = "media/0")))))).toByteArray()); z.closeEntry()
        }
        assertTrue(runCatching { StoryBackup.restore(context, Uri.fromFile(zip)) {} }.isFailure)
        assertEquals(before, store.all())
    }
    fun testArchiveRejectsPathTraversal() {
        val zip = File(directory, "unsafe.zip")
        ZipOutputStream(zip.outputStream()).use { z -> z.putNextEntry(ZipEntry("../escape")); z.write(byteArrayOf(1)); z.closeEntry() }
        assertTrue(runCatching { StoryBackup.restore(context, Uri.fromFile(zip)) {} }.isFailure)
        assertFalse(File(context.filesDir, "escape").exists()); assertTrue(StoryStore(context).all().isEmpty())
    }
    fun testMalformedMetadataDoesNotCommit() {
        val zip = File(directory, "malformed.zip")
        val value = com.google.gson.Gson().toJson(StoryArchive(stories = listOf(Story(moments = mutableListOf(StoryMoment(uri = "media/0", caption = "broken")))))).replace("\"caption\":\"broken\"", "\"caption\":null")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("media/0")); z.write(byteArrayOf(1)); z.closeEntry()
            z.putNextEntry(ZipEntry("stories.json")); z.write(value.toByteArray()); z.closeEntry()
        }
        assertTrue(runCatching { StoryBackup.restore(context, Uri.fromFile(zip)) {} }.isFailure)
        assertTrue(StoryStore(context).all().isEmpty())
    }
    fun testRetiredBuiltinMusicDoesNotBlockBackupOrChangeOriginalStory() {
        val store = StoryStore(context)
        val photo = File(directory, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val original = Story(title = "旧版故事", musicUri = "asset://afternoon.wav", musicName = "午后微光",
            moments = mutableListOf(StoryMoment(uri = Uri.fromFile(photo).toString())))
        store.save(original)
        val before = File(context.filesDir, "stories-v1.json").readBytes()
        val zip = File(directory, "retired-music.zip")
        StoryBackup.export(context, Uri.fromFile(zip)) {}
        assertTrue(before.contentEquals(File(context.filesDir, "stories-v1.json").readBytes()))
        StoryBackup.restore(context, Uri.fromFile(zip)) {}
        val restored = store.all().single { it.id != original.id }
        assertEquals("", restored.musicUri)
        assertEquals("", restored.musicName)
        assertTrue(byteArrayOf(1, 2, 3).contentEquals(StoryMedia.open(context, restored.moments.single().uri).use { it.readBytes() }))
        assertEquals("asset://afternoon.wav", store.get(original.id)!!.musicUri)
    }
}
