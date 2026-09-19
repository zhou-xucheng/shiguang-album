package org.fossify.shiguang.stories

import android.content.Intent
import android.test.InstrumentationTestCase
import androidx.lifecycle.ViewModelProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class StoryWorkTest : InstrumentationTestCase() {
    fun testImportCompletionSurvivesEditorRecreationWithoutStaleSave() {
        val store = StoryStore(instrumentation.targetContext)
        val story = Story(title = "自动测试：页面重建")
        store.save(story)
        var activity: StoryEditorActivity? = null
        val release = CountDownLatch(1)
        try {
            activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, StoryEditorActivity::class.java)
                .putExtra("story_id", story.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StoryEditorActivity
            val old = activity
            lateinit var work: StoryWork
            instrumentation.runOnMainSync {
                work = ViewModelProvider(old)[StoryWork::class.java]
                work.start("添加照片与视频") { update ->
                    update("等待页面重建")
                    check(release.await(20, TimeUnit.SECONDS))
                    val fresh = store.get(story.id)!!
                    fresh.addMoments(listOf(StoryMoment(uri = "content://qa/recreated-photo")))
                    store.save(fresh)
                    StoryImportResult(1, 0, emptyList())
                }
            }
            val monitor = instrumentation.addMonitor(StoryEditorActivity::class.java.name, null, false)
            instrumentation.runOnMainSync { old.recreate() }
            activity = instrumentation.waitForMonitorWithTimeout(monitor, 15000) as? StoryEditorActivity
            instrumentation.removeMonitor(monitor)
            assertNotNull("Editor must recreate", activity)
            val freshActivity = activity!!
            instrumentation.runOnMainSync {
                assertSame(work, ViewModelProvider(freshActivity)[StoryWork::class.java])
                assertNotNull(work.state.value)
            }
            release.countDown()
            var completed = false
            repeat(150) {
                instrumentation.runOnMainSync { completed = work.state.value == null }
                if (!completed) Thread.sleep(50)
            }
            assertTrue("Recreated editor must consume completion", completed)
            instrumentation.runOnMainSync { freshActivity.finish() }
            instrumentation.waitForIdleSync()
            assertEquals(1, store.get(story.id)!!.moments.size)
        } finally {
            release.countDown()
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            instrumentation.waitForIdleSync()
            store.permanentlyDelete(story.id)
        }
    }
}
