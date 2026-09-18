package org.fossify.shiguang.stories

import android.content.Intent
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.shiguang.R

class StoriesActivity : StoryActivity() {
    private var query = ""
    private var drafts = false
    private var searching = false
    private var list: RecyclerView? = null
    private var allStories = listOf<Story>()
    private var displayed = listOf<Story>()
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val state = list?.layoutManager?.onSaveInstanceState()
        if (!safely { allStories = store.all().filter { it.deletedAt == 0L }.sortedByDescending { it.updatedAt } }) return
        val root = column().apply { setBackgroundColor(paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars()); view.setPadding(b.left, b.top, b.right, b.bottom); insets
        }
        val header = row().apply { setPadding(dp(24), dp(10), dp(24), dp(4)) }
        val identity = column().apply {
            addFull(editorial("拾光", 28f))

        }
        header.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(R.drawable.ic_memory_search, "搜索故事") { searching = !searching; if (!searching) query = ""; render() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(button("＋ 新故事", true) { startActivity(Intent(this, StoryEditorActivity::class.java)) }.apply { textSize = 14f })
        root.addFull(header)
        val search = EditText(this).apply {
            hint = "搜索故事、文字或年份"; setText(query); textSize = 15f; isSingleLine = true
            setTextColor(ink); setHintTextColor(muted); background = shape(surfaceColor, 16).apply { setStroke(dp(1), lineColor) }
            setPadding(dp(16), dp(10), dp(16), dp(10)); minimumHeight = dp(48)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); filter() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        val tools = column().apply { setPadding(dp(24), dp(4), dp(24), dp(4)) }
        if (searching) tools.addFull(search)
        val tabs = row()
        tabs.addView(button("故事 ${allStories.count { !it.draft }}") { drafts = false; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        tabs.addView(button("草稿 ${allStories.count { it.draft }}") { drafts = true; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        for (i in 0 until tabs.childCount) {
            val tab = tabs.getChildAt(i) as com.google.android.material.button.MaterialButton
            val active = if (drafts) i == 1 else i == 0
            tab.strokeWidth = 0
            tab.setTextColor(if (active) accentText else muted)
            tab.backgroundTintList = ColorStateList.valueOf(if (active) ColorUtils.blendARGB(paper, accent, .09f) else Color.TRANSPARENT)
            if (active) tab.typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        if (searching) tools.space(8); tools.addFull(tabs); root.addFull(tools)
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StoriesActivity); setPadding(dp(24), dp(6), dp(24), dp(20)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = displayed.size.coerceAtLeast(1)
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(column().apply { layoutParams = RecyclerView.LayoutParams(-1, -2) })
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    holder.box.removeAllViews(); holder.box.addFull(if (displayed.isEmpty()) empty() else storyCard(displayed[position])); holder.box.space(18)
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addFull(MemoryChrome.navigation(this, 0)); setContentView(root)
        filter(); list?.layoutManager?.onRestoreInstanceState(state)
        root.post { ViewCompat.requestApplyInsets(root) }
    }
    private class Holder(val box: LinearLayout) : RecyclerView.ViewHolder(box)
    private fun filter() {
        displayed = allStories.filter { it.draft == drafts && (query.isBlank() || (it.title + it.description + it.date + it.moments.joinToString { m -> m.caption + m.date }).contains(query, true)) }
        list?.adapter?.notifyDataSetChanged()
    }
    private fun empty(): View = panel().apply {
        if (query.isNotBlank()) { addFull(label("没有找到相关故事", 20f)); space(8); addFull(label("试试其他名字或年份。", 15f, muted)) }
        else if (drafts) { addFull(label("还没有草稿", 20f)); space(8); addFull(label("未完成的故事会保存在这里。", 15f, muted)) }
        else {
            addFull(ImageView(this@StoriesActivity).apply { setImageResource(R.drawable.memory_still_life); scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = "一本等待放入照片的相簿" }, 180)
            space(20); addFull(label("从几张喜欢的照片开始", 22f, bold = true)); space(8)
            addFull(label("选好照片和视频，就能播放。标题和文字可以慢慢补。", 15f, muted)); space(16)
            addFull(button("选择照片与视频", true) { startActivity(Intent(this@StoriesActivity, StoryEditorActivity::class.java)) })
        }
    }
    private fun storyCard(story: Story): View = column().apply {
        val open = { startActivity(Intent(this@StoriesActivity, if (story.draft) StoryEditorActivity::class.java else StoryDetailActivity::class.java).putExtra("story_id", story.id)) }
        val frame = FrameLayout(this@StoriesActivity).apply {
            background = shape(cardColor, 22); clipToOutline = true
            isFocusable = true; contentDescription = "打开故事：${story.title}"
            setOnClickListener { open() }
        }
        val picture = StoryCoverView(this@StoriesActivity).apply { position(story); contentDescription = null }
        story.cover()?.let { Glide.with(this@StoriesActivity).load(Uri.parse(it.uri)).dontTransform().error(android.R.drawable.ic_menu_report_image).into(picture) }
            ?: picture.setImageResource(R.drawable.memory_still_life)
        frame.addView(picture, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this@StoriesActivity).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.argb(30, 0, 0, 0), Color.argb(230, 0, 0, 0)))
        }, FrameLayout.LayoutParams(-1, -1))
        val copy = column().apply {
            setPadding(dp(22), dp(20), dp(22), dp(22))
            addFull(label(story.dateLabel(), 13f, Color.WHITE))
            space(7)
            addFull(editorial(story.title, 27f, Color.WHITE).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (story.description.isNotBlank()) { space(7); addFull(label(story.description, 14f, Color.WHITE).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }) }
        }
        frame.addView(copy, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        addFull(frame, if (resources.configuration.fontScale > 1.2f) 360 else 290)
        val meta = row().apply { setPadding(dp(3), dp(8), dp(3), 0) }
        meta.addView(label("${story.moments.count { !it.video }} 张照片 · ${story.moments.count { it.video }} 段视频", 13f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        meta.addView(button(if (story.draft) "继续制作" else "播放故事") {
            if (story.draft) open() else startActivity(Intent(this@StoriesActivity, StoryPlayerActivity::class.java).putExtra("story_id", story.id))
        }.apply { strokeWidth = 0; setTextColor(accentText) })
        addFull(meta)
        if (!story.draft) addFull(button("分享给亲友") {
            startActivity(Intent(this@StoriesActivity, StoryShareActivity::class.java).putExtra("story_id", story.id))
        }.apply { contentDescription = "分享故事：${story.title}"; setTextColor(accentText) })
    }
}
