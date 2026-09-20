package org.fossify.shiguang.stories

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import org.fossify.shiguang.R
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class StoriesActivity : StoryActivity() {
    private var query = ""
    private var section = 0
    private var year = "全部年份"
    private var compact = true
    private var order = 0
    private var list: RecyclerView? = null
    private var scrollState: Parcelable? = null
    private lateinit var countLabel: TextView
    private var all = listOf<Story>()
    private var shown = listOf<Story>()
    private val prefs by lazy { getSharedPreferences("story-shelf", MODE_PRIVATE) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); query = state?.getString("query").orEmpty(); section = state?.getInt("section") ?: 0
        scrollState = state?.getParcelable("shelf_scroll")
        if (!prefs.getBoolean("compact_v2", false)) prefs.edit().putBoolean("compact", true).putBoolean("compact_v2", true).apply()
        year = state?.getString("year") ?: "全部年份"; compact = prefs.getBoolean("compact", true); order = prefs.getInt("order", 0)
    }
    override fun onSaveInstanceState(out: Bundle) { out.putParcelable("shelf_scroll", list?.layoutManager?.onSaveInstanceState() ?: scrollState); out.putString("query", query); out.putInt("section", section); out.putString("year", year); super.onSaveInstanceState(out) }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val scroll = list?.layoutManager?.onSaveInstanceState() ?: scrollState
        if (!safely { all = store.all().filter { it.deletedAt == 0L } }) return
        val root = column().apply { background = MemoryPaper.background(this@StoriesActivity, paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets -> val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()); v.setPadding(b.left,b.top,b.right,b.bottom); insets }
        val header = row().apply { setPadding(dp(20), dp(6), dp(16), dp(4)) }
        header.addView(label("拾光", 26f, bold = true), LinearLayout.LayoutParams(0,-2,1f))
        header.addView(quietButton("＋ 新故事") { startActivity(Intent(this, StoryEditorActivity::class.java)) }); root.addFull(header)
        val tools = column().apply { setPadding(dp(20), dp(4), dp(20), 0) }
        tools.addFull(EditText(this).apply {
            hint = "搜索故事、文字或年份"; contentDescription = "搜索故事"; setText(query); textSize = 16f; isSingleLine = true
            setTextColor(ink); setHintTextColor(muted); background = shape(cardColor, 10); setPadding(dp(16),dp(10),dp(16),dp(10)); minimumHeight = dp(48)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); filter(); list?.scrollToPosition(0) }
                override fun afterTextChanged(s: Editable?) {}
            })
        })
        val sections = row()
        listOf("全部", "收藏", "草稿").forEachIndexed { index, title -> sections.addView(quietButton(title, section == index) { section = index; list = null; render() }.apply { textSize = 14f; strokeWidth = 0 }, LinearLayout.LayoutParams(0,-2,1f)) }
        sections.addView(iconButton(R.drawable.ic_memory_more, "故事排列与显示") { displayOptions() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        tools.addFull(sections)
        val filters = row()
        countLabel = label("", 13f, muted)
        filters.addView(countLabel, LinearLayout.LayoutParams(0, -2, 1f))
        filters.addView(quietButton("$year⌄") {
            val years = listOf("全部年份") + all.map { it.year() }.distinct().sortedDescending()
            MemoryDialogBuilder(this).setTitle("按年份找故事").setItems(years.toTypedArray()) { _, i -> year = years[i]; list = null; scrollState = null; render() }.show()
        }.apply { contentDescription = "按年份找故事" })
        tools.addFull(filters); root.addFull(tools)
        val columns = if (compact) StoryLayout.albumColumns(this) else 1
        val manager = GridLayoutManager(this, columns).apply { spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int) = if (shown.isEmpty()) columns else 1 } }
        list = RecyclerView(this).apply {
            layoutManager = manager; setPadding(dp(14), 0, dp(14), dp(12)); clipToPadding = false; isVerticalScrollBarEnabled = true
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = shown.size.coerceAtLeast(1)
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(column().apply { layoutParams = RecyclerView.LayoutParams(-1,-2); setPadding(dp(6),dp(6),dp(6),dp(12)) })
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    holder.box.removeAllViews()
                    if (shown.isEmpty()) { holder.box.addFull(editorial(if (all.isEmpty()) "从喜欢的照片开始" else "这里还没有故事", 24f)); holder.box.space(12); holder.box.addFull(label(if (all.isEmpty()) "点右上角新故事，把照片整理成一本相册。" else "试试其他年份、分类或搜索词。", 15f, muted)) }
                    else holder.box.addFull(card(shown[position], compact))
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1,0,1f)); root.addFull(MemoryChrome.navigation(this,0)); setContentView(root)
        filter(); manager.onRestoreInstanceState(scroll); root.post { ViewCompat.requestApplyInsets(root) }
    }
    private fun displayOptions() {
        MemoryChrome.sheet(this, "故事排列与显示", actions = listOf(
            MemoryChrome.Action("排列顺序", listOf("最近修改", "故事日期", "名称顺序")[order]) {
                MemoryDialogBuilder(this).setTitle("故事排列").setSingleChoiceItems(arrayOf("最近修改", "故事日期", "名称顺序"), order) { dialog, which ->
                    order = which; prefs.edit().putInt("order", order).apply(); dialog.dismiss(); filter(); list?.scrollToPosition(0)
                }.setNegativeButton("取消", null).show()
            },
            MemoryChrome.Action(if (compact) "切换大封面" else "切换小封面") {
                compact = !compact; prefs.edit().putBoolean("compact", compact).apply(); list = null; scrollState = null; render()
            }
        ))
    }
    private class Holder(val box: LinearLayout) : RecyclerView.ViewHolder(box)
    private fun filter() {
        val sorter = compareByDescending<Story> { it.pinned }.thenComparator { a,b -> when(order) {
            1 -> compareValues(if (b.year() == "未注明") "" else b.date, if (a.year() == "未注明") "" else a.date)
            2 -> a.title.compareTo(b.title)
            else -> b.updatedAt.compareTo(a.updatedAt)
        } }
        shown = all.filter { s -> (if (section == 2) s.draft else !s.draft && (section != 1 || s.favorite)) &&
            (year == "全部年份" || s.year() == year) && (query.isBlank() || (s.title + s.description + s.date + s.moments.joinToString { it.caption + it.date }).contains(query,true)) }.sortedWith(sorter)
        if (::countLabel.isInitialized) countLabel.text = "${shown.size} 本故事"
        list?.adapter?.notifyDataSetChanged()
    }
    private fun card(story: Story, small: Boolean) = column().apply {
        val open = { startActivity(Intent(this@StoriesActivity, if (story.draft) StoryEditorActivity::class.java else StoryDetailActivity::class.java).putExtra("story_id", story.id)) }
        val frame = if (small) StorySquareFrame(this@StoriesActivity) else FrameLayout(this@StoriesActivity)
        frame.background = shape(cardColor, 9); frame.clipToOutline = true
        val cover = StoryCoverView(this@StoriesActivity).apply {
            position(story); contentDescription = "打开故事：${story.title}"; setOnClickListener { open() }
            setOnLongClickListener { actions(story); true }
        }
        frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        story.cover()?.let { Glide.with(cover).load(Uri.parse(it.uri)).override(if (small) 320 else 900).dontTransform().error(android.R.drawable.ic_menu_report_image).into(cover) }
        val more = iconButton(R.drawable.ic_memory_more, "管理故事：${story.title}") { actions(story) }.apply {
            setPadding(dp(13), dp(13), dp(13), dp(13))
            background = android.graphics.drawable.InsetDrawable(shape(surfaceColor, 7), dp(10)); alpha = .94f
        }
        frame.addView(more, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END))
        addFull(frame, if (small) -2 else 220); space(6)
        addFull(label(story.title, if (small) 14f else 19f, bold = true).apply {
            maxLines = 2; if (small) minLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { open() }; setOnLongClickListener { actions(story); true }
        })
        addFull(label((if (story.pinned) "置顶 · " else if (story.favorite) "收藏 · " else "") + "${story.moments.size} 个片段", 12f, muted).apply { maxLines = 2 })
        if (!small) { space(4); addFull(label(story.dateLabel(), 13f, muted)) }
    }
    private fun actions(story: Story) {
        MemoryChrome.sheet(this,story.title,story.dateLabel(),actions=listOf(
            MemoryChrome.Action(if (story.draft) "继续制作" else "打开故事") { startActivity(Intent(this, if(story.draft) StoryEditorActivity::class.java else StoryDetailActivity::class.java).putExtra("story_id",story.id)) },
            MemoryChrome.Action(if(story.favorite) "取消收藏" else "收藏故事") { story.favorite = !story.favorite; if(safely { store.save(story) }) render() },
            MemoryChrome.Action(if(story.pinned) "取消置顶" else "置顶故事") { story.pinned = !story.pinned; if(safely { store.save(story) }) render() },
            MemoryChrome.Action("分享方式") { startActivity(Intent(this,StorySharingActivity::class.java).putExtra("story_id",story.id)) }
        ))
    }
}
