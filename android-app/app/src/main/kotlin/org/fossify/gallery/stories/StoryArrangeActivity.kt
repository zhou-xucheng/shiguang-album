package org.fossify.shiguang.stories

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.time.LocalDate

class StoryArrangeActivity : StoryActivity() {
    private lateinit var story: Story
    private lateinit var list: RecyclerView
    private val selected = mutableSetOf<String>()
    private var chooseCover = false
    private var dragHelper: ItemTouchHelper? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在") }) { finish(); return }
        chooseCover = intent.getBooleanExtra("choose_cover", false)
        render()
    }
    private fun render() {
        val root = column().apply { setBackgroundColor(paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets -> val b = insets.getInsets(WindowInsetsCompat.Type.systemBars()); v.setPadding(b.left, b.top, b.right, b.bottom); insets }
        val header = row().apply { setPadding(dp(16), dp(10), dp(16), dp(10)) }
        header.addView(iconButton(org.fossify.shiguang.R.drawable.ic_memory_back, "返回") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(label(if (chooseCover) "选择封面" else "整理片段", 22f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(button("完成") { finish() }); root.addFull(header)
        root.addFull(label(if (chooseCover) "点一张照片或视频，设为故事封面。" else "长按拖动排序 · 点文字编辑 · 勾选可批量移除", 14f, muted).apply { setPadding(dp(24), 0, dp(24), dp(12)) })
        list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StoryArrangeActivity); setPadding(dp(16), 0, dp(16), dp(12)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = story.moments.size
                override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder = Holder(row().apply { layoutParams = RecyclerView.LayoutParams(-1, -2); setPadding(dp(8), dp(10), dp(8), dp(10)) })
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    val moment = story.moments[position]; holder.box.removeAllViews()
                    val image = ImageView(this@StoryArrangeActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(cardColor, 10); clipToOutline = true; contentDescription = "查看片段 ${position + 1}"; setOnClickListener { if (chooseCover) selectCover(moment) else openMoment(this@StoryArrangeActivity, story, holder.bindingAdapterPosition) } }
                    Glide.with(this@StoryArrangeActivity).load(Uri.parse(moment.uri)).into(image); holder.box.addView(image, LinearLayout.LayoutParams(dp(68), dp(80)))
                    val copy = column()
                    copy.addFull(label("${position + 1} · ${if (moment.video) "视频" else "照片"}" + if (story.cover()?.id == moment.id) " · 封面" else "", 15f, bold = true))
                    copy.addFull(label(StoryMedia.dateLabel(moment), 13f, muted)); copy.addFull(label(moment.caption.ifBlank { "添加描述" }, 15f).apply { maxLines = 2 })
                    copy.setOnClickListener { if (chooseCover) selectCover(moment) else editMoment(moment) }
                    holder.box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
                    if (!chooseCover) holder.box.addView(label("≡", 25f, muted).apply {
                        gravity = Gravity.CENTER; contentDescription = "拖动片段 ${position + 1} 排序"
                        setOnTouchListener { _, event -> if (event.actionMasked == MotionEvent.ACTION_DOWN) dragHelper?.startDrag(holder); true }
                    }, LinearLayout.LayoutParams(dp(40), dp(48)))
                    if (!chooseCover) holder.box.addView(CheckBox(this@StoryArrangeActivity).apply {
                        contentDescription = "选择片段 ${position + 1}"; isChecked = selected.contains(moment.id); buttonTintList = android.content.res.ColorStateList.valueOf(accent)
                        setOnCheckedChangeListener { _, checked -> if (checked) selected.add(moment.id) else selected.remove(moment.id) }
                    })
                }
            }
        }
        if (!chooseCover) dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition; val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                story.moveMoment(from, to); rv.adapter?.notifyItemMoved(from, to); return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) { super.clearView(rv, holder); safely { store.save(story) }; rv.adapter?.notifyDataSetChanged() }
        }).also { it.attachToRecyclerView(list) }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = row().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        if (chooseCover) actions.addView(button("调整当前封面") { story.cover()?.let { selectCover(it) } }, LinearLayout.LayoutParams(-1, -2))
        else {
            actions.addView(button("按日期") { confirmDateSort(story) { if (safely { store.save(story) }) list.adapter?.notifyDataSetChanged() } }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("全选") { selected.addAll(story.moments.map { it.id }); list.adapter?.notifyDataSetChanged() }, LinearLayout.LayoutParams(0, -2, 1f))
            actions.addView(button("移除所选") {
                if (selected.isEmpty()) { message("先勾选要移除的片段"); return@button }
                MemoryDialogBuilder(this).setTitle("移除 ${selected.size} 个片段？").setMessage("只从这个故事移除，手机原图会保留。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                        story.moments.removeAll { it.id in selected }; selected.clear(); if (story.moments.isEmpty()) story.draft = true
                        safely { store.save(story) }; list.adapter?.notifyDataSetChanged()
                    }.show()
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addFull(actions); setContentView(root); root.post { ViewCompat.requestApplyInsets(root) }
    }
    private class Holder(val box: LinearLayout) : RecyclerView.ViewHolder(box)
    private fun selectCover(moment: StoryMoment) {
        val draft = story.copy(coverId = moment.id)
        if (story.cover()?.id != moment.id) { draft.coverX = .5f; draft.coverY = .5f }
        val box = column().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
        val preview = StoryCoverView(this).apply { setBackgroundColor(cardColor); position(draft); contentDescription = "封面位置预览" }
        preview.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val homeWidth = (resources.displayMetrics.widthPixels - dp(48)).coerceAtLeast(1)
            val height = (dp(200) * v.width.toFloat() / homeWidth).toInt().coerceAtLeast(1)
            if (v.layoutParams.height != height) v.layoutParams = v.layoutParams.apply { this.height = height }
        }
        Glide.with(this).load(Uri.parse(moment.uri)).dontTransform().into(preview)
        box.addFull(preview, 200); box.space(12)
        box.addFull(label("移动滑块，把人物留在画面中。原照片不会改变。", 14f, muted))
        val sliders = column()
        fun slider(title: String, value: Float, change: (Float) -> Unit) {
            sliders.addFull(label(title, 14f))
            sliders.addFull(SeekBar(this).apply {
                max = 100; progress = (value * 100).toInt(); contentDescription = title
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                    override fun onProgressChanged(s: SeekBar?, value: Int, user: Boolean) { if (user) { change(value / 100f); preview.position(draft) } }
                })
            })
        }
        val mode = button(if (draft.coverFit) "完整显示" else "铺满封面") {}
        mode.setOnClickListener { draft.coverFit = !draft.coverFit; mode.text = if (draft.coverFit) "完整显示" else "铺满封面"; sliders.visibility = if (draft.coverFit) View.GONE else View.VISIBLE; preview.position(draft) }
        box.addFull(mode)
        slider("左右位置", draft.coverX) { draft.coverX = it }; slider("上下位置", draft.coverY) { draft.coverY = it }
        sliders.visibility = if (draft.coverFit) View.GONE else View.VISIBLE; box.addFull(sliders)
        val scroll = ScrollView(this).apply { addView(box) }
        MemoryDialogBuilder(this).setTitle("调整故事封面").setView(scroll).setNegativeButton("取消", null)
            .setPositiveButton("保存封面") { _, _ -> story.coverId = draft.coverId; story.coverFit = draft.coverFit; story.coverX = draft.coverX; story.coverY = draft.coverY; if (safely { store.save(story) }) { message("封面已保存"); finish() } }.show()
    }
    private fun editMoment(moment: StoryMoment) {
        val editor = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val caption = EditText(this).apply { hint = "这一刻发生了什么？"; setText(moment.caption); setTextColor(ink); setHintTextColor(muted); minLines = 2 }
        editor.addFull(caption)
        var date = moment.date
        var verified = moment.dateVerified
        val dateButton = button(StoryMedia.dateLabel(moment)) {}
        dateButton.setOnClickListener {
            val d = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
            DatePickerDialog(this, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString(); verified = true; dateButton.text = date }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }
        editor.addFull(dateButton)
        MemoryDialogBuilder(this).setTitle("片段文字与日期").setView(editor).setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ -> moment.caption = caption.text.toString().trim(); moment.date = date; moment.dateVerified = verified; if (safely { store.save(story) }) list.adapter?.notifyDataSetChanged() }.show()
    }
}
