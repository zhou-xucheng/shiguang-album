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
    private var multi = false
    private var chooseCover = false
    private var dragHelper: ItemTouchHelper? = null
    private var scrollState: android.os.Parcelable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在") }) { finish(); return }
        chooseCover = intent.getBooleanExtra("choose_cover", false)
        selected.addAll(savedInstanceState?.getStringArrayList("selected").orEmpty())
        multi = savedInstanceState?.getBoolean("multi") ?: false
        scrollState = savedInstanceState?.getParcelable("grid_scroll")
        story.ensureOriginalOrder()
        render()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("selected", ArrayList(selected)); outState.putBoolean("multi", multi); super.onSaveInstanceState(outState)
        outState.putParcelable("grid_scroll", list.layoutManager?.onSaveInstanceState())
    }
    private fun render() {
        if (::list.isInitialized) scrollState = list.layoutManager?.onSaveInstanceState()
        val footer = if (multi && !chooseCover) button("批量操作 · 已选 ${selected.size} 个", true) { batchActions() } else button("完成", true) { finish() }
        val body = fixedPage(if (chooseCover) "选择封面" else "整理照片与视频", footer)
        val head = column().apply { setPadding(dp(24), dp(4), dp(24), dp(12)) }
        head.addFull(label(if (chooseCover) "点一张喜欢的照片，调整封面位置。" else if (multi) "轻点勾选，可批量改日期、移动或移除。" else "轻点查看或编辑 · 长按拖动排序", 14f, muted))
        if (!chooseCover) {
            val tools = row()
            tools.addView(button("排序") { confirmDateSort(story) { if (safely { store.save(story) }) list.adapter?.notifyDataSetChanged() } }, LinearLayout.LayoutParams(0,-2,1f))
            tools.addView(button(if (multi) "取消多选" else "多选") { multi = !multi; selected.clear(); render() }, LinearLayout.LayoutParams(0,-2,1.2f))
            tools.addView(button("日期") { dateActions() }, LinearLayout.LayoutParams(0,-2,1f))
            head.addFull(tools)
            if (multi) head.addFull(button(if (selected.size == story.moments.size) "取消全选" else "全选") { if (selected.size == story.moments.size) selected.clear() else selected.addAll(story.moments.map { it.id }); render() }.apply { strokeWidth = 0 })
        }
        body.addFull(head)
        val grid = StoryTiles(this, story.moments, { it.id in selected }, details = true) { index ->
            val moment = story.moments[index]
            if (chooseCover) selectCover(moment)
            else if (multi) { if (!selected.add(moment.id)) selected.remove(moment.id); val state = list.layoutManager?.onSaveInstanceState(); render(); list.layoutManager?.onRestoreInstanceState(state) }
            else MemoryChrome.sheet(this, "第 ${index + 1} 个片段", actions = listOf(
                MemoryChrome.Action("查看大图") { openMoment(this, story, index) },
                MemoryChrome.Action("文字与日期") { editMoment(moment) },
                MemoryChrome.Action("设为封面") { selectCover(moment) }
            ))
        }
        list = grid
        if (!chooseCover && !multi) {
            dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(15, 0) {
                override fun isLongPressDragEnabled() = false
                override fun onMove(rv: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                    val from = source.bindingAdapterPosition; val to = target.bindingAdapterPosition
                    if (from < 0 || to < 0) return false
                    story.moveMoment(from, to); rv.adapter?.notifyItemMoved(from, to); return true
                }
                override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) {}
                override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) { super.clearView(rv, holder); safely { store.save(story) }; rv.adapter?.notifyDataSetChanged() }
            }).also { it.attachToRecyclerView(grid) }
            grid.longPress = { dragHelper?.startDrag(it) }
        }
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        grid.layoutManager?.onRestoreInstanceState(scrollState)
    }
    private fun batchActions() {
        if (selected.isEmpty()) { message("先选择照片或视频"); return }
        MemoryChrome.sheet(this, "已选择 ${selected.size} 个", actions = listOf(
            MemoryChrome.Action("修改所选日期") { batchDate() },
            MemoryChrome.Action("移到开头") { story.moveSelected(selected, true); safely { store.save(story) }; render() },
            MemoryChrome.Action("移到末尾") { story.moveSelected(selected, false); safely { store.save(story) }; render() },
            MemoryChrome.Action("从故事移除", "手机原图保留") {
                MemoryDialogBuilder(this).setTitle("移除 ${selected.size} 个片段？").setMessage("仅从这个故事移除，不删除手机原图。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                        story.moments.removeAll { it.id in selected }; selected.clear(); if (story.moments.isEmpty()) story.draft = true
                        if (safely { store.save(story) }) render()
                    }.show()
            }
        ))
    }
    private fun batchDate() {
        val today = LocalDate.now()
        DatePickerDialog(this, { _, y, m, d ->
            val date = LocalDate.of(y, m + 1, d).toString()
            story.moments.filter { it.id in selected }.forEach { it.date = date; it.dateSource = "manual"; it.dateVerified = true }
            if (safely { store.save(story) }) render()
        }, today.year, today.monthValue - 1, today.dayOfMonth).show()
    }
    private fun dateActions() {
        MemoryChrome.sheet(this, "照片日期", "自动日期来自文件或系统相册；不确定的日期不参与排序。", listOf(
            MemoryChrome.Action("重新识别自动日期", "保留已手动确认的日期") {
                MemoryDialogBuilder(this).setTitle("重新识别日期？").setMessage("优先处理未知或异常日期。旧版没有记录修改来源，正常旧日期会保留；旧版异常日期若曾手动填写，请先逐张确认。不会改动照片原文件。")
                    .setNegativeButton("取消", null).setPositiveButton("重新识别") { _, _ ->
                        backgroundWork("识别日期", { update ->
                            var changed = 0
                            story.moments.forEachIndexed { i, moment ->
                                if (moment.dateSource == "manual" || (moment.dateSource.isNullOrBlank() && StoryDates.credible(moment.date))) return@forEachIndexed
                                update("正在检查 ${i + 1} / ${story.moments.size}")
                                var info = StoryMedia.captureDateInfo(applicationContext, Uri.parse(moment.sourceUri?.takeIf { it.isNotBlank() } ?: moment.uri), moment.video)
                                if (info.first.isBlank()) info = StoryMedia.captureDateInfo(applicationContext, Uri.parse(moment.uri), moment.video)
                                if (info.first.isNotBlank() || !StoryDates.credible(moment.date)) {
                                    if (moment.date != info.first) changed++
                                    moment.date = info.first; moment.dateSource = info.second; moment.dateVerified = info.first.isNotBlank()
                                }
                            }
                            if (!story.dateManual && !StoryDates.credible(story.date)) { story.date = ""; story.dateConfirmed = false }
                            store.save(story); changed
                        }) { count -> render(); message("已更新 $count 个日期；无法确定的保持未知") }
                    }.show()
            },
            MemoryChrome.Action("批量修改日期", "先多选需要调整的照片") { multi = true; render() }
        ))
    }
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
        var manual = moment.dateSource == "manual"
        val dateButton = button(StoryMedia.dateLabel(moment)) {}
        dateButton.setOnClickListener {
            val d = runCatching { LocalDate.parse(date) }.getOrElse { LocalDate.now() }
            DatePickerDialog(this, { _, y, m, day -> date = LocalDate.of(y, m + 1, day).toString(); verified = true; manual = true; dateButton.text = date }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        }
        editor.addFull(dateButton)
        editor.addFull(button("日期未知") { date = ""; verified = false; manual = true; dateButton.text = "日期未知" })
        MemoryDialogBuilder(this).setTitle("片段文字与日期").setView(editor).setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ -> moment.caption = caption.text.toString().trim(); moment.date = date; moment.dateVerified = verified; if (manual) moment.dateSource = "manual"; if (safely { store.save(story) }) list.adapter?.notifyDataSetChanged() }.show()
    }
}
