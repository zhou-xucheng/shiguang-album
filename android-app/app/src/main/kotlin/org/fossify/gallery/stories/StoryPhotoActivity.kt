package org.fossify.shiguang.stories

import android.os.Bundle
import android.net.Uri
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.alexvasilkov.gestures.GestureImageView
import com.bumptech.glide.Glide

class StoryPhotoActivity : StoryActivity() {
    private lateinit var story: Story
    private var photos = listOf<StoryMoment>()
    private var index = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely {
            story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在")
            photos = story.moments.filterNot { it.video }
            require(photos.isNotEmpty())
            val selected = story.moments.getOrNull(intent.getIntExtra("start_index", 0))?.id
            index = (savedInstanceState?.getInt("photo_index") ?: photos.indexOfFirst { it.id == selected }).coerceIn(photos.indices)
            render()
        }) finish()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("photo_index", index); super.onSaveInstanceState(outState) }
    private fun render() {
        val moment = photos[index]
        val root = column().apply { setBackgroundColor(paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets -> val b = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(b.left, b.top, b.right, b.bottom); insets }
        val head = row().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        head.addView(iconButton(org.fossify.shiguang.R.drawable.ic_memory_back, "返回") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        head.addView(label("照片 ${index + 1} / ${photos.size}", 18f, bold = true)); root.addFull(head)
        val image = GestureImageView(this).apply {
            contentDescription = moment.caption.ifBlank { "故事照片" }
            controller.settings.maxZoom = 6f; controller.settings.doubleTapZoom = 2f
        }
        Glide.with(this).load(Uri.parse(moment.uri)).error(android.R.drawable.ic_menu_report_image).into(image)
        root.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        val info = column().apply { setPadding(dp(24), dp(10), dp(24), dp(8)) }
        info.addFull(label(StoryMedia.dateLabel(moment), 14f, muted))
        if (moment.caption.isNotBlank()) {
            info.space(6)
            info.addFull(label(moment.caption, 16f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            info.addFull(button("查看完整说明") {
                val scroll = ScrollView(this).apply { addView(label(moment.caption, 16f).apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }) }
                MemoryDialogBuilder(this).setTitle("照片说明").setView(scroll).setPositiveButton("关闭", null).show()
            })
        }
        val navigation = row()
        navigation.addView(button("前一张") { if (index > 0) { index--; render() } }.apply { isEnabled = index > 0; alpha = if (isEnabled) 1f else .4f }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(button("后一张") { if (index < photos.lastIndex) { index++; render() } }.apply { isEnabled = index < photos.lastIndex; alpha = if (isEnabled) 1f else .4f }, LinearLayout.LayoutParams(0, -2, 1f))
        info.addFull(navigation)
        root.addFull(info); setContentView(root)
        root.post { ViewCompat.requestApplyInsets(root) }
    }
}
