package org.fossify.shiguang.stories

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.shiguang.R

class StoryDetailActivity : StoryActivity() {
    private var gridMode = true
    private var timelineState: Parcelable? = null
    private var currentTimeline: RecyclerView? = null
    private class Holder(val box: LinearLayout) : RecyclerView.ViewHolder(box)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); gridMode = state?.getBoolean("grid", true) ?: true; timelineState = state?.getParcelable("scroll")
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("grid", gridMode); out.putParcelable("scroll", currentTimeline?.layoutManager?.onSaveInstanceState()); super.onSaveInstanceState(out)
    }
    override fun onPause() { timelineState = currentTimeline?.layoutManager?.onSaveInstanceState(); super.onPause() }
    override fun onResume() { super.onResume(); render() }
    private fun render(reset: Boolean = false) {
        if (reset) timelineState = null
        else currentTimeline?.layoutManager?.onSaveInstanceState()?.let { timelineState = it }
        safely {
            val story = store.get(intent.getStringExtra("story_id") ?: "") ?: run { finish(); return@safely }
            if (story.deletedAt != 0L) { finish(); return@safely }
            val actions = row()
            actions.addView(button("编辑") { startActivity(Intent(this, StoryEditorActivity::class.java).putExtra("story_id", story.id)) }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("浏览", true) {
                if (story.moments.isEmpty()) message("先给故事添加片段") else openMoment(this, story, firstVisible())
            }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("分享") {
                if (story.moments.isEmpty()) message("先给故事添加片段") else startActivity(Intent(this, StorySharingActivity::class.java).putExtra("story_id", story.id))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val body = fixedPage(story.title, actions)
            val summary = row().apply { setPadding(dp(20), 0, dp(16), 0) }
            summary.addView(label("${story.moments.count { !it.video }} 张照片 · ${story.moments.count { it.video }} 段视频", 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
            summary.addView(quietButton("故事介绍") { introduction(story) })
            body.addFull(summary)
            val modes = row().apply { setPadding(dp(16), 0, dp(16), 0) }
            modes.addView(quietButton("照片总览", gridMode) { gridMode = true; render(true) }, LinearLayout.LayoutParams(0, -2, 1f))
            modes.addView(quietButton("故事时间线", !gridMode) { gridMode = false; render(true) }, LinearLayout.LayoutParams(0, -2, 1f))
            modes.addView(iconButton(R.drawable.ic_memory_more, "故事操作") { storyActions(story) }, LinearLayout.LayoutParams(dp(48), dp(48)))
            body.addFull(modes)
            if (story.moments.isEmpty()) {
                currentTimeline = null
                body.addFull(label("还没有照片或视频，点下方「编辑」添加。", 16f, muted).apply { setPadding(dp(20), dp(28), dp(20), dp(28)) })
                return@safely
            }
            body.addFull(positionControl(story.moments.size, { firstVisible() }) { index ->
                val grid = currentTimeline as? StoryTiles
                if (grid != null) grid.jumpTo(index) else (currentTimeline?.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
            })
            val timeline = if (gridMode) StoryTiles(this, story.moments) { index -> openMoment(this, story, index) } else timeline(story)
            currentTimeline = timeline
            body.addView(timeline, LinearLayout.LayoutParams(-1, 0, 1f))
            timeline.layoutManager?.onRestoreInstanceState(timelineState)
        }
    }
    private fun firstVisible() = (currentTimeline?.layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
    private fun introduction(story: Story) {
        MemoryDialogBuilder(this).setTitle(story.title).setMessage(story.dateLabel() + "\n\n" + story.description.ifBlank { "还没有填写故事文字，可在编辑中补充。" })
            .setPositiveButton("关闭", null).show()
    }
    private fun storyActions(story: Story) {
        MemoryChrome.sheet(this, story.title, actions = listOf(
            MemoryChrome.Action(if (story.favorite) "取消收藏" else "收藏") { story.favorite = !story.favorite; if (safely { store.save(story) }) render() },
            MemoryChrome.Action(if (story.pinned) "取消置顶" else "置顶") { story.pinned = !story.pinned; if (safely { store.save(story) }) render() },
            MemoryChrome.Action("自动播放") { if (story.moments.isNotEmpty()) startActivity(Intent(this, StoryPlayerActivity::class.java).putExtra("story_id", story.id)) else message("先给故事添加片段") }
        ))
    }
    private fun timeline(story: Story) = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@StoryDetailActivity); isVerticalScrollBarEnabled = true
        adapter = object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount() = story.moments.size
            override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(column().apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2); setPadding(dp(20), dp(6), dp(20), dp(20))
            })
            override fun onBindViewHolder(holder: Holder, index: Int) {
                holder.box.removeAllViews()
                val moment = story.moments[index]
                val heading = row()
                heading.addView(label("${index + 1} / ${story.moments.size}", 12f, accentText), LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(label(StoryMedia.dateLabel(moment), 12f, muted))
                holder.box.addFull(heading); holder.box.space(8)
                val photo = ImageView(this@StoryDetailActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER; adjustViewBounds = true; maxHeight = dp(460); minimumHeight = dp(120)
                    background = shape(cardColor, 10); clipToOutline = true
                    contentDescription = if (moment.video) "播放视频片段" else "查看完整照片"
                    setOnClickListener { openMoment(this@StoryDetailActivity, story, index) }
                }
                Glide.with(photo).load(Uri.parse(moment.uri)).override(1000).fitCenter().error(android.R.drawable.ic_menu_report_image).into(photo)
                holder.box.addFull(photo)
                if (moment.caption.isNotBlank() || moment.video) holder.box.addFull(label((if (moment.video) "▷ 视频  " else "") + moment.caption, 15f).apply { setPadding(0, dp(8), 0, 0) })
            }
        }
    }
}
