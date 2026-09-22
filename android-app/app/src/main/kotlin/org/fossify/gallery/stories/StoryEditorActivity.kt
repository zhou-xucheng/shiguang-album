package org.fossify.shiguang.stories

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import java.time.LocalDate

class StoryEditorActivity : StoryActivity() {
    private lateinit var story: Story
    private lateinit var titleInput: EditText
    private lateinit var descriptionInput: EditText
    private var step = 0
    private var textFieldsActive = false
    private var closing = false
    private var mediaGrid: StoryTiles? = null
    private var mediaScrollState: android.os.Parcelable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        step = savedInstanceState?.getInt("step") ?: 0
        mediaScrollState = savedInstanceState?.getParcelable("media_scroll")
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) { override fun handleOnBackPressed() { leavePage() } })
        if (!safely {
            val id = savedInstanceState?.getString("story_id") ?: intent.getStringExtra("story_id")
            story = store.get(id ?: "") ?: if (id != null) Story(id = id, draft = true) else Story(draft = true)
            render()
            if (savedInstanceState == null && intent.getStringExtra("story_id") == null) {
                val paths = intent.getStringArrayListExtra("media_paths")
                if (paths.isNullOrEmpty()) pickMedia() else importMedia(paths.map { Uri.fromFile(java.io.File(it)) })
            }
        }) finish()
    }
    override fun onResume() {
        super.onResume()
        if (::story.isInitialized && !hasBackgroundWork) { store.get(story.id)?.let { story = it }; render() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::story.isInitialized) outState.putString("story_id", story.id)
        outState.putInt("step", step)
        outState.putParcelable("media_scroll", mediaGrid?.layoutManager?.onSaveInstanceState() ?: mediaScrollState)
        super.onSaveInstanceState(outState)
    }
    private fun field(hintText: String, value: String, multiline: Boolean = false) = EditText(this).apply {
        hint = hintText; setText(value); setTextColor(ink); setHintTextColor(muted); textSize = 17f
        setPadding(0, dp(10), 0, dp(10))
        inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multiline) minLines = 2 else setSingleLine(true)
        background = shape(cardColor, 12)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }
    private fun captureText() {
        if (textFieldsActive && ::titleInput.isInitialized) { story.title = titleInput.text.toString().trim().ifBlank { "我的故事" }; story.description = descriptionInput.text.toString().trim() }
    }
    private fun save(): Boolean {
        captureText()
        return safely { store.save(story) }
    }
    override fun onPause() {
        mediaScrollState = mediaGrid?.layoutManager?.onSaveInstanceState() ?: mediaScrollState
        if (::story.isInitialized && !closing && !hasBackgroundWork) save()
        super.onPause()
    }
    override fun leavePage() { if (!hasBackgroundWork && save()) { if (step == 1) { step = 0; render() } else { closing = true; finish() } } }
    private fun render() {
        mediaScrollState = mediaGrid?.layoutManager?.onSaveInstanceState() ?: mediaScrollState
        mediaGrid = null
        textFieldsActive = false
        if (step == 0) { renderMedia(); return }
        val dock = row()
        dock.addView(button("上一步") { if (save()) { step = 0; render() } }, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(button("预览") { if (save()) startActivity(Intent(this, StoryPhotoActivity::class.java).putExtra("story_id", story.id)) }, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(button("完成", true) {
            if (story.moments.isEmpty()) { message("请先添加照片或视频"); return@button }
            val draft = story.draft; story.draft = false
            if (save()) {
                closing = true
                startActivity(Intent(this, StoryPhotoActivity::class.java).putExtra("story_id", story.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish()
            } else story.draft = draft
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val content = page("封面与文字", "", footer = dock)
        content.addFull(eyebrow("第 2 步 / 共 2 步 · 封面与文字")); content.space(12)
        val cover = StoryCoverView(this).apply { position(story); background = shape(cardColor, 20); clipToOutline = true; contentDescription = "更换故事封面"
            setOnClickListener { if (save()) startActivity(Intent(this@StoryEditorActivity, StoryArrangeActivity::class.java).putExtra("story_id", story.id).putExtra("choose_cover", true)) }
        }
        story.cover()?.let { Glide.with(this).load(Uri.parse(it.uri)).into(cover) }
        content.addFull(cover, 132)
        content.addFull(button("更换封面与调整位置") { cover.performClick() }); content.space(18)
        content.addFull(label("相册名称", 15f, bold = true)); content.space(8)
        titleInput = field("例如：周末去公园", story.title.takeUnless { it in listOf("新的故事", "我的故事") } ?: "").apply { contentDescription = "相册名称" }
        content.addFull(titleInput); content.space(16)
        content.addFull(label("写几句话 · 可不填", 15f, bold = true)); content.space(8)
        descriptionInput = field("那天和谁在一起，有什么想记住的？", story.description, true).apply { contentDescription = "故事文字" }
        content.addFull(descriptionInput); textFieldsActive = true; content.space(12)
        story.suggestedTitle()?.let { suggestion -> content.addFull(button("用日期命名") {
            if (titleInput.text.isNotBlank()) MemoryDialogBuilder(this).setTitle("替换当前名称？").setMessage(suggestion).setNegativeButton("保留", null).setPositiveButton("替换") { _, _ -> titleInput.setText(suggestion); save() }.show()
            else { titleInput.setText(suggestion); save() }
        }.apply { strokeWidth = 0; textSize = 13f }) }
        content.addFull(settingRow("故事日期 · 可不填", story.dateLabel()) { captureText(); chooseDate(story.date) { story.date = it; story.dateConfirmed = true; story.dateManual = true; if (save()) render() } })
        if (story.date.isNotBlank()) content.addFull(button("不显示故事日期") { story.date = ""; story.dateConfirmed = false; story.dateManual = true; if (save()) render() }.apply { strokeWidth = 0 })
        content.space(12)
        content.addFull(settingRow("自动播放选项", "每张 ${story.intervalSeconds} 秒 · ${if (story.originalSound) "保留视频原声" else "关闭视频原声"}") { captureText(); playbackOptions() })
        content.space(14)
        content.addFull(label("照片已保存为独立副本，原图不变。卸载应用会清除本地故事。", 13f, muted))
        content.addFull(button(if (story.draft) "删除草稿" else "删除故事") {
            MemoryDialogBuilder(this).setTitle("移入回收站？").setMessage("不删除手机原图，可在设置的回收站恢复。")
                .setNegativeButton("取消", null).setPositiveButton("移入回收站") { _, _ -> story.deletedAt = System.currentTimeMillis(); if (save()) { closing = true; finish() } }.show()
        }.apply { strokeWidth = 0; setTextColor(muted) })
    }
    private fun renderMedia() {
        story.ensureOriginalOrder()
        val dock = row()
        dock.addView(button("＋ 添加") { if (save()) pickMedia() }, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(button("下一步", true) { if (story.moments.isEmpty()) message("先添加照片或视频") else if (save()) { step = 1; render() } }, LinearLayout.LayoutParams(0, -2, 1.4f))
        val body = fixedPage(if (story.draft) "制作故事" else "编辑故事", dock)
        val heading = column().apply { setPadding(dp(20), dp(4), dp(20), dp(4)) }
        heading.addFull(eyebrow("第 1 步 / 共 2 步 · ${story.moments.size} 个片段")); heading.space(8)
        heading.addFull(label("轻点查看 · 长按拖动 · 随时继续添加", 13f, muted))
        val tools = row()
        tools.addView(button("排序") { confirmDateSort(story) { if (save()) render() } }, LinearLayout.LayoutParams(0, -2, .8f))
        tools.addView(button("整理照片") { if (save()) startActivity(Intent(this, StoryArrangeActivity::class.java).putExtra("story_id", story.id)) }, LinearLayout.LayoutParams(0, -2, 1.2f))
        heading.addFull(tools); body.addFull(heading)
        if (story.moments.isEmpty()) { body.addFull(label("还没有照片，点下方「添加」开始。", 16f, muted).apply { setPadding(dp(24), dp(32), dp(24), 0) }); return }
        val grid = StoryTiles(this, story.moments, details = false) { index -> if (save()) openMoment(this, story, index) }
        val helper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(15, 0) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(rv: androidx.recyclerview.widget.RecyclerView, source: androidx.recyclerview.widget.RecyclerView.ViewHolder, target: androidx.recyclerview.widget.RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition; val to = target.bindingAdapterPosition
                if (from < 0 || to < 0) return false
                story.moveMoment(from, to); rv.adapter?.notifyItemMoved(from, to); return true
            }
            override fun onSwiped(h: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
            override fun clearView(rv: androidx.recyclerview.widget.RecyclerView, h: androidx.recyclerview.widget.RecyclerView.ViewHolder) { super.clearView(rv, h); save(); rv.adapter?.notifyDataSetChanged() }
        })
        helper.attachToRecyclerView(grid); grid.longPress = { helper.startDrag(it) }
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        mediaGrid = grid
        grid.layoutManager?.onRestoreInstanceState(mediaScrollState)
    }
    private fun preview() {
        if (story.moments.isEmpty()) message("先添加照片或视频") else if (save()) startActivity(Intent(this, StoryPhotoActivity::class.java).putExtra("story_id", story.id))
    }
    private fun suggestOrganization() {
        captureText()
        confirmDateSort(story) { if (save()) render() }
    }
    private fun chooseDate(value: String, result: (String) -> Unit) {
        val date = runCatching { LocalDate.parse(value) }.getOrElse { LocalDate.now() }
        DatePickerDialog(this, { _, y, m, d -> result(LocalDate.of(y, m + 1, d).toString()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    private fun playbackOptions() {
        val box = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        val durationValue = label("${story.intervalSeconds} 秒", 14f, muted)
        val duration = row().apply {
            minimumHeight = dp(64); setPadding(0, dp(8), 0, dp(8)); isFocusable = true
            addView(label("每张照片停留", 16f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(durationValue); addView(label("  ›", 24f, muted))
            setOnClickListener { chooseInterval { durationValue.text = "${story.intervalSeconds} 秒" } }
        }
        box.addFull(duration)
        fun toggle(title: String, value: Boolean, change: (Boolean) -> Unit) {
            box.addFull(androidx.appcompat.widget.SwitchCompat(this).apply {
                text = title; textSize = 16f; setTextColor(ink); minimumHeight = dp(56); isChecked = value
                thumbTintList = android.content.res.ColorStateList.valueOf(accent)
                setOnCheckedChangeListener { _, on -> change(on); save() }
            })
        }
        toggle("保留视频原声", story.originalSound) { story.originalSound = it }
        toggle("柔和转场", story.transition) { story.transition = it }
        toggle("循环播放", story.loop) { story.loop = it }
        MemoryDialogBuilder(this).setTitle("播放选项").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("完成") { _, _ -> render() }.show()
    }
    private fun chooseInterval(changed: () -> Unit) {
        val presets = listOf(3, 5, 8, 12)
        val actions = presets.map { seconds -> MemoryChrome.Action("$seconds 秒", if (story.intervalSeconds == seconds) "当前选择" else "") {
            story.intervalSeconds = seconds; save(); changed()
        } }.toMutableList()
        actions += MemoryChrome.Action("自定义时间", "可设置 1—60 秒") {
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "输入 1—60"
                setText(story.intervalSeconds.toString())
                selectAll()
            }
            val dialog = MemoryDialogBuilder(this).setTitle("自定义停留时间").setView(input)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
            dialog.setOnShowListener {
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val seconds = input.text.toString().toIntOrNull()
                    if (seconds == null || seconds !in 1..60) input.error = "请输入 1—60 秒"
                    else { story.intervalSeconds = seconds; save(); changed(); dialog.dismiss() }
                }
            }
            dialog.show()
        }
        MemoryChrome.sheet(this, "照片停留时间", "自动播放时，每张照片停留多久。", actions)
    }
    private fun pickMedia() {
        captureText()
        startActivityForResult(Intent(this, StoryMediaPickerActivity::class.java), 401)
    }
    private fun importMedia(uris: List<Uri>) {
        captureText()
        backgroundWork("添加照片与视频", { update ->
            val failures = mutableListOf<String>()
            var added = 0
            var duplicates = 0
                uris.forEachIndexed { index, uri ->
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("添加已中断，已保存的片段会保留")
                    if (story.moments.any { it.sourceUri == uri.toString() || it.uri == uri.toString() }) { duplicates++; return@forEachIndexed }
                    update("正在保存独立副本 ${index + 1} / ${uris.size}")
                    runCatching {
                        val moment = StoryMedia.importMoment(applicationContext, uri)
                        val oldDate = story.date
                        val oldConfirmed = story.dateConfirmed
                        if (story.date.isBlank() && story.moments.isEmpty() && moment.dateVerified) { story.date = moment.date; story.dateConfirmed = true }
                        story.addMoments(listOf(moment))
                        try { store.save(story) } catch (e: Exception) {
                            story.moments.remove(moment); story.date = oldDate; story.dateConfirmed = oldConfirmed
                            java.io.File(Uri.parse(moment.uri).path!!).delete(); throw e
                        }
                        added++
                    }.onFailure { failures.add("${StoryMedia.name(applicationContext, uri)}：${it.message ?: "文件无法读取"}") }
                }
                StoryImportResult(added, duplicates, failures)
        }) { result ->
            onBackgroundWorkRestored("添加照片与视频", result)
        }
    }
    override fun onBackgroundWorkRestored(title: String, result: Any?) {
        if (!::story.isInitialized) return
        if (!safely { store.get(story.id)?.let { story = it }; render() }) return
        if (result is StoryImportResult) {
            val summary = "已保存 ${result.added} 个片段" + if (result.duplicates > 0) "，跳过 ${result.duplicates} 个重复文件" else ""
            if (result.failures.isEmpty()) message(summary)
            else MemoryDialogBuilder(this).setTitle("添加结果").setMessage("$summary\n${result.failures.size} 个文件未保存：\n" + result.failures.take(8).joinToString("\n") + if (result.failures.size > 8) "\n其余失败文件请重新选择。" else "").setPositiveButton("知道了", null).show()
        }
    }
    @Deprecated("Activity document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 401) return
        if (resultCode != RESULT_OK || data == null) { if (story.moments.isEmpty() && store.get(story.id) == null) finish(); return }
        val uris = data.clipData?.let { clip -> (0 until clip.itemCount).map { clip.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        importMedia(uris)
    }
}

fun openMoment(activity: android.app.Activity, story: Story, index: Int) {
    activity.startActivity(Intent(activity, StoryPhotoActivity::class.java)
        .putExtra("story_id", story.id).putExtra("start_index", index).putExtra("single", true))
}
