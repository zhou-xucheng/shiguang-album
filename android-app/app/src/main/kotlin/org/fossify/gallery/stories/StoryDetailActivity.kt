package org.fossify.shiguang.stories

import android.content.Intent
import android.os.Bundle

/** Compatibility route for older navigation stacks. Stories now open in the swipeable viewer. */
class StoryDetailActivity : StoryActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val id = intent.getStringExtra("story_id") ?: run { finish(); return }
        lateinit var story: Story
        if (!safely { story = store.get(id) ?: error("故事不存在") }) { finish(); return }
        val target = if (story.moments.isEmpty() || story.draft) StoryEditorActivity::class.java else StoryPhotoActivity::class.java
        startActivity(Intent(this, target).putExtra("story_id", id))
        finish()
    }
}
