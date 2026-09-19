package org.fossify.shiguang.stories

import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide

class StoryDetailActivity : StoryActivity() {
    override fun onCreate(state: android.os.Bundle?) { super.onCreate(state); gridMode = state?.getBoolean("grid",true) ?: true; timelineState = state?.getParcelable("scroll") }
    override fun onSaveInstanceState(out: android.os.Bundle) { out.putBoolean("grid",gridMode); out.putParcelable("scroll", currentTimeline?.layoutManager?.onSaveInstanceState()); super.onSaveInstanceState(out) }
    private var gridMode = true
    private var timelineState: android.os.Parcelable? = null
    private var currentTimeline: androidx.recyclerview.widget.RecyclerView? = null
    private class TimelineHolder(val box: LinearLayout) : androidx.recyclerview.widget.RecyclerView.ViewHolder(box)
    override fun onPause() { timelineState = currentTimeline?.layoutManager?.onSaveInstanceState(); super.onPause() }
    override fun onResume() {
        super.onResume()
        safely {
            val story = store.get(intent.getStringExtra("story_id") ?: "") ?: run { finish(); return@safely }
            if (story.deletedAt != 0L) { finish(); return@safely }
            val actions = row()
            actions.addView(button("编辑") { startActivity(Intent(this, StoryEditorActivity::class.java).putExtra("story_id", story.id)) }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("浏览", true) {
                if (story.moments.isEmpty()) message("先给故事添加片段")
                else openMoment(this, story, 0)
            }, LinearLayout.LayoutParams(0, -2, .8f))
            actions.addView(button("分享") {
                if (story.moments.isEmpty()) message("先给故事添加片段")
                else startActivity(Intent(this, StorySharingActivity::class.java).putExtra("story_id", story.id))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            val content = page("故事", "", footer = actions)
            if (story.date.isNotBlank()) content.addFull(eyebrow(story.dateLabel()))
            content.space(12)
            content.addFull(editorial(story.title, 31f))
            if (story.description.isNotBlank()) { content.space(14); content.addFull(label(story.description, 16f, muted)) }
            content.space(18)
            val facts = row()
            facts.addView(badge("${story.moments.count { !it.video }} 张照片"))
            facts.addView(badge("${story.moments.count { it.video }} 段视频"))
            content.addFull(facts); content.space(18)
            val tools = row()
            tools.addView(button(if(story.favorite) "已收藏" else "收藏") { story.favorite = !story.favorite; if(safely { store.save(story) }) onResume() }.apply { strokeWidth = 0 }, LinearLayout.LayoutParams(0,-2,1f))
            tools.addView(button("自动播放") { if(story.moments.isNotEmpty()) startActivity(Intent(this,StoryPlayerActivity::class.java).putExtra("story_id",story.id)) }.apply { strokeWidth = 0 },LinearLayout.LayoutParams(0,-2,1.2f))
            tools.addView(button(if(story.pinned) "取消置顶" else "置顶") { story.pinned = !story.pinned; if(safely { store.save(story) }) onResume() }.apply { strokeWidth = 0 },LinearLayout.LayoutParams(0,-2,1f))
            content.addFull(tools); content.space(12)
            val modes = row()
            modes.addView(button("照片总览",gridMode) { gridMode = true; timelineState = null; onResume() },LinearLayout.LayoutParams(0,-2,1f))
            modes.addView(button("故事时间线",!gridMode) { gridMode = false; timelineState = null; onResume() },LinearLayout.LayoutParams(0,-2,1f))
            content.addFull(modes)
            val oldScroll = pageScroll!!
            val root = oldScroll.parent as LinearLayout
            oldScroll.removeView(content)
            root.removeView(oldScroll)
            val timeline = androidx.recyclerview.widget.RecyclerView(this@StoryDetailActivity).apply {
                val columns = if(!gridMode) 1 else if(resources.configuration.fontScale > 1.2f) 2 else 3
                layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@StoryDetailActivity, columns).apply {
                    spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int) = if(position == 0) columns else 1 }
                }
                adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<TimelineHolder>() {
                    override fun getItemCount() = story.moments.size + 1
                    override fun getItemViewType(position: Int) = if(position == 0) 0 else 1
                    override fun onCreateViewHolder(parent: android.view.ViewGroup, type: Int) = TimelineHolder(column().apply { layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, -2) })
                    override fun onBindViewHolder(holder: TimelineHolder, position: Int) {
                        holder.box.removeAllViews(); holder.box.setPadding(0, 0, 0, 0)
                        if (position == 0) { (content.parent as? android.view.ViewGroup)?.removeView(content); holder.box.addFull(content); return }
                        val index = position - 1
                        val moment = story.moments[index]
                        if(gridMode) {
                            holder.box.setPadding(dp(4),dp(4),dp(4),dp(8))
                            val tile = ImageView(this@StoryDetailActivity).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(cardColor,12); clipToOutline = true
                                contentDescription = "查看第 ${index + 1} 个片段"; setOnClickListener { openMoment(this@StoryDetailActivity,story,index) }
                            }
                            Glide.with(tile).load(Uri.parse(moment.uri)).override(360,360).centerCrop().into(tile)
                            holder.box.addFull(tile,116)
                            holder.box.addFull(label("${index + 1}" + if(moment.video) " · ▷ 视频" else "",12f,muted).apply { setPadding(dp(4),dp(4),0,0) })
                            return
                        }

                        val target = holder.box.apply { setPadding(dp(24), 0, dp(24), dp(20)) }

                val chapter = column()
                val heading = row()
                heading.addView(label("%02d".format(index + 1), 13f, accentText, true).apply {
                    gravity = Gravity.CENTER; background = shape(cardColor, 10)
                }, LinearLayout.LayoutParams(dp(34), dp(34)))
                val showDate = index == 0 || moment.date != story.moments[index - 1].date || moment.dateVerified != story.moments[index - 1].dateVerified
                heading.addView(label(if (showDate) StoryMedia.dateLabel(moment) else "", 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
                heading.addView(label(if (moment.video) "视频" else "照片", 12f, muted))
                chapter.addFull(heading); chapter.space(10)
                val card = column().apply { background = shape(cardColor, 16); clipToOutline = true }
                val photo = ImageView(this@StoryDetailActivity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true; maxHeight = dp(460); minimumHeight = dp(160)
                    contentDescription = if (moment.video) "播放视频片段" else "查看完整照片"
                    setOnClickListener { openMoment(this@StoryDetailActivity, story, index) }
                }
                Glide.with(this@StoryDetailActivity).load(Uri.parse(moment.uri)).fitCenter().error(android.R.drawable.ic_menu_report_image).into(photo)
                card.addFull(photo)
                chapter.addFull(card)
                if (moment.caption.isNotBlank() || moment.video) chapter.addFull(label((if (moment.video) "▷ 视频  " else "") + moment.caption, 15f).apply { setPadding(dp(3), dp(12), dp(3), dp(12)) })
                target.addFull(chapter)
            }
                }
            }
            root.addView(timeline, 1, LinearLayout.LayoutParams(-1, 0, 1f))
            timeline.layoutManager?.onRestoreInstanceState(timelineState)
            currentTimeline = timeline
        }
    }
}
