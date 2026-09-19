package org.fossify.shiguang.stories

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
    private var all = listOf<Story>()
    private var shown = listOf<Story>()
    private val prefs by lazy { getSharedPreferences("story-shelf", MODE_PRIVATE) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); query = state?.getString("query").orEmpty(); section = state?.getInt("section") ?: 0
        year = state?.getString("year") ?: "全部年份"; compact = prefs.getBoolean("compact", true); order = prefs.getInt("order", 0)
    }
    override fun onSaveInstanceState(out: Bundle) { out.putString("query", query); out.putInt("section", section); out.putString("year", year); super.onSaveInstanceState(out) }
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val scroll = list?.layoutManager?.onSaveInstanceState()
        if (!safely { all = store.all().filter { it.deletedAt == 0L } }) return
        val root = column().apply { background = MemoryPaper.background(this@StoriesActivity, paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets -> val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()); v.setPadding(b.left,b.top,b.right,b.bottom); insets }
        val header = row().apply { setPadding(dp(24), dp(12), dp(24), dp(6)) }
        header.addView(column().apply { addFull(editorial("拾光", 29f)); addFull(label("把日子，收进相册", 12f, muted)) }, LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("＋ 新故事", true) { startActivity(Intent(this, StoryEditorActivity::class.java)) }); root.addFull(header)
        val tools = column().apply { setPadding(dp(24), dp(8), dp(24), dp(6)) }
        tools.addFull(EditText(this).apply {
            hint = "搜索名称、文字或年份"; contentDescription = "搜索故事"; setText(query); textSize = 15f; isSingleLine = true
            setTextColor(ink); setHintTextColor(muted); background = shape(surfaceColor, 14).apply { setStroke(dp(1), lineColor) }; setPadding(dp(16),dp(10),dp(16),dp(10)); minimumHeight = dp(48)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { query = s.toString(); filter(); list?.scrollToPosition(0) }
                override fun afterTextChanged(s: Editable?) {}
            })
        })
        val sections = row()
        listOf("全部", "收藏", "草稿").forEachIndexed { index, title -> sections.addView(button(title, section == index) { section = index; list = null; render() }.apply { textSize = 14f; strokeWidth = 0 }, LinearLayout.LayoutParams(0,-2,1f)) }
        tools.addFull(sections)
        val filters = row()
        filters.addView(button(year) {
            val years = listOf("全部年份") + all.map { it.year() }.distinct().sortedDescending()
            MemoryDialogBuilder(this).setTitle("按年份找故事").setItems(years.toTypedArray()) { _, i -> year = years[i]; list = null; render() }.show()
        }.apply { textSize = 13f }, LinearLayout.LayoutParams(0,-2,1.15f))
        filters.addView(button(listOf("最近修改", "故事日期", "名称顺序")[order]) {
            MemoryDialogBuilder(this).setTitle("故事排列").setItems(arrayOf("最近修改", "故事日期", "名称顺序")) { _, i -> order = i; prefs.edit().putInt("order", order).apply(); filter() }.show()
        }.apply { textSize = 13f }, LinearLayout.LayoutParams(0,-2,1.15f))
        filters.addView(button(if (compact) "大封面" else "网格") { compact = !compact; prefs.edit().putBoolean("compact", compact).apply(); list = null; render() }.apply { textSize = 13f }, LinearLayout.LayoutParams(0,-2,.9f))
        tools.addFull(filters); root.addFull(tools)
        val columns = if (compact && resources.configuration.fontScale <= 1.2f) 2 else 1
        val manager = GridLayoutManager(this, columns).apply { spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() { override fun getSpanSize(position: Int) = if (shown.isEmpty()) columns else 1 } }
        list = RecyclerView(this).apply {
            layoutManager = manager; setPadding(dp(18), dp(4), dp(18), dp(12)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = shown.size.coerceAtLeast(1)
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(column().apply { layoutParams = RecyclerView.LayoutParams(-1,-2); setPadding(dp(6),dp(6),dp(6),dp(12)) })
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    holder.box.removeAllViews()
                    if (shown.isEmpty()) { holder.box.addFull(editorial(if (all.isEmpty()) "从喜欢的照片开始" else "这里还没有故事", 24f)); holder.box.space(12); holder.box.addFull(label(if (all.isEmpty()) "点右上角新故事，把照片整理成一本相册。" else "试试其他年份、分类或搜索词。", 15f, muted)) }
                    else holder.box.addFull(card(shown[position], columns == 2))
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1,0,1f)); root.addFull(MemoryChrome.navigation(this,0)); setContentView(root)
        filter(); manager.onRestoreInstanceState(scroll); root.post { ViewCompat.requestApplyInsets(root) }
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
        list?.adapter?.notifyDataSetChanged()
    }
    private fun card(story: Story, small: Boolean) = column().apply {
        background = shape(surfaceColor,20).apply { setStroke(dp(1),lineColor) }; clipToOutline = true
        val open = { startActivity(Intent(this@StoriesActivity, if (story.draft) StoryEditorActivity::class.java else StoryDetailActivity::class.java).putExtra("story_id",story.id)) }
        val image = StoryCoverView(this@StoriesActivity).apply { position(story); contentDescription = "打开故事：${story.title}"; setOnClickListener { open() } }
        story.cover()?.let { Glide.with(image).load(Uri.parse(it.uri)).override(if (small) 600 else 1100).dontTransform().into(image) }
        addFull(image, if (small) 148 else 230)
        val copy = column().apply { setPadding(dp(14),dp(12),dp(14),dp(10)) }
        copy.addFull(label((if (story.pinned) "置顶 · " else "") + (if (story.favorite) "收藏 · " else "") + if (story.date.isBlank()) "" else story.dateLabel(), 12f,accentText))
        copy.space(5); copy.addFull(label(story.title, if (small) 18f else 23f, bold = true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setOnClickListener { open() } })
        copy.space(7); copy.addFull(label("${story.moments.count { !it.video }} 张照片 · ${story.moments.count { it.video }} 段视频",12f,muted))
        val actions = row()
        actions.addView(button(if (story.draft) "继续制作" else "翻开相册") { open() }.apply { textSize = 13f; strokeWidth = 0; setPadding(dp(2),0,dp(2),0) }, LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("•••") { actions(story) }.apply { contentDescription = "管理故事：${story.title}"; strokeWidth = 0; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0) }, LinearLayout.LayoutParams(dp(48),dp(48)))
        copy.addFull(actions); addFull(copy)
    }
    private fun actions(story: Story) {
        MemoryChrome.sheet(this,story.title,actions=listOf(
            MemoryChrome.Action(if(story.favorite) "取消收藏" else "收藏故事") { story.favorite = !story.favorite; if(safely { store.save(story) }) render() },
            MemoryChrome.Action(if(story.pinned) "取消置顶" else "置顶故事") { story.pinned = !story.pinned; if(safely { store.save(story) }) render() },
            MemoryChrome.Action("分享方式") { startActivity(Intent(this,StorySharingActivity::class.java).putExtra("story_id",story.id)) }
        ))
    }
}
