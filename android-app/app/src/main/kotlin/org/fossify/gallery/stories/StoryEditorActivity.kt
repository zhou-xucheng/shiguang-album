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
    private var closing = false
    private var importing = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely {
            story = store.get(savedInstanceState?.getString("story_id") ?: intent.getStringExtra("story_id") ?: "") ?: Story(draft = true)
            render()
            if (savedInstanceState == null && intent.getStringExtra("story_id") == null) {
                val paths = intent.getStringArrayListExtra("media_paths")
                if (paths.isNullOrEmpty()) pickMedia() else importMedia(paths.map { Uri.fromFile(java.io.File(it)) })
            }
        }) finish()
    }
    override fun onResume() {
        super.onResume()
        if (::story.isInitialized && !importing) { store.get(story.id)?.let { story = it }; render() }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("story_id", story.id); super.onSaveInstanceState(outState) }
    private fun field(hintText: String, value: String, multiline: Boolean = false) = EditText(this).apply {
        hint = hintText; setText(value); setTextColor(ink); setHintTextColor(muted); textSize = 17f
        setPadding(0, dp(10), 0, dp(10))
        inputType = InputType.TYPE_CLASS_TEXT or if (multiline) InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multiline) minLines = 2 else setSingleLine(true)
        background = shape(android.graphics.Color.TRANSPARENT, 0)
    }
    private fun captureText() {
        if (::titleInput.isInitialized) { story.title = titleInput.text.toString().trim().ifBlank { "我的故事" }; story.description = descriptionInput.text.toString().trim() }
    }
    private fun save(): Boolean {
        captureText()
        return safely { store.save(story) }
    }
    override fun onPause() { if (::story.isInitialized && !closing && !importing) save(); super.onPause() }
    override fun leavePage() { if (save()) { closing = true; finish() } }
    private fun render() {
        val dock = row()
        dock.addView(button("＋ 添加") { if (save()) pickMedia() }, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(button("预览") { preview() }, LinearLayout.LayoutParams(0, -2, 1f))
        dock.addView(button("完成", true) {
            if (story.moments.isEmpty()) message("请先添加照片或视频") else {
                val wasDraft = story.draft; story.draft = false
                if (save()) {
                    closing = true
                    startActivity(Intent(this, StoryDetailActivity::class.java).putExtra("story_id", story.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                } else story.draft = wasDraft
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val content = page(if (story.draft) "制作故事" else "编辑故事", "", footer = dock)
        content.addFull(row().apply { addView(badge(if (story.draft) "草稿 · 自动保存" else "修改自动保存")) }); content.space(12)
        content.addFull(eyebrow("01  照片与视频 · ${story.moments.size} 个片段")); content.space(12)
        val media = panel()
        if (story.moments.isEmpty()) { media.addFull(label("先选几张喜欢的照片", 20f, bold = true)); media.space(8); media.addFull(label("日期会自动读取；没有拍摄时间的文件可以稍后补充。", 15f, muted)); media.space(16); media.addFull(button("选择照片与视频", true) { pickMedia() }) }
        else {
            val strip = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val thumbs = row()
            story.moments.take(8).forEachIndexed { index, moment ->
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(cardColor, 10); clipToOutline = true
                    contentDescription = "查看第 ${index + 1} 个片段"; setOnClickListener { captureText(); if (save()) openMoment(this@StoryEditorActivity, story, index) }
                }
                Glide.with(this).load(Uri.parse(moment.uri)).into(image); thumbs.addView(image, LinearLayout.LayoutParams(dp(100), dp(120)))
            }
            strip.addView(thumbs); media.addFull(strip); media.space(10)
            media.addFull(settingRow("整理片段", "拖动排序、批量移除、修改日期与文字") { if (save()) startActivity(Intent(this, StoryArrangeActivity::class.java).putExtra("story_id", story.id)) })
            media.rule(); media.addFull(settingRow("按拍摄日期整理", "先预览日期分组，再决定是否排序") { suggestOrganization() })
            media.rule(); media.addFull(settingRow("故事封面", "当前使用第 ${story.moments.indexOf(story.cover()) + 1} 张 · ${if (story.coverFit) "完整显示" else "铺满封面"}") {
                if (save()) startActivity(Intent(this, StoryArrangeActivity::class.java).putExtra("story_id", story.id).putExtra("choose_cover", true))
            })
        }
        content.addFull(media); content.space(22)
        content.addFull(eyebrow("02  标题与文字 · 可以稍后填写")); content.space(12)
        val info = panel()
        titleInput = field("给故事起个名字", story.title).apply { textSize = 21f }
        descriptionInput = field("写几句话，记住那一天…", story.description, true)
        info.addFull(titleInput); info.rule(); info.addFull(descriptionInput); info.rule()
        story.suggestedTitle()?.takeIf { it != story.title }?.let { suggestion ->
            info.addFull(settingRow("试试这个标题", suggestion) { titleInput.setText(suggestion); save() })
            info.rule()
        }
        info.addFull(settingRow("故事发生日期", story.dateLabel()) { captureText(); chooseDate(story.date) { story.date = it; story.dateConfirmed = true; if (save()) render() } })
        if (story.date.isNotBlank()) info.addFull(button("暂不确定日期") { story.date = ""; story.dateConfirmed = false; if (save()) render() })
        content.addFull(info); content.space(22)
        content.addFull(eyebrow("03  播放方式")); content.space(12)
        val playback = panel()
        playback.addFull(settingRow("播放选项", "每张 ${story.intervalSeconds} 秒 · ${if (story.originalSound) "保留视频原声" else "关闭视频原声"}") { playbackOptions() })
        content.addFull(playback); content.space(18)
        val external = story.moments.count { Uri.parse(it.uri).scheme == "content" }
        content.addFull(label(if (story.moments.isEmpty()) "添加成功后，这里会显示保存状态。" else if (external > 0) "有 $external 个旧版片段仍引用手机原文件，可保存独立副本。" else "${story.moments.size} 个片段已保存到应用，手机原图不受影响。卸载应用会清除故事及其副本。", 14f, muted))
        if (external > 0) content.addFull(button("保存旧片段的独立副本") {
            captureText(); importing = true
            backgroundWork("保存副本", { update ->
                try { story.moments.forEachIndexed { index, m -> if (Uri.parse(m.uri).scheme == "content") { update("正在保存 ${index + 1} / ${story.moments.size}"); m.sourceUri = m.uri; m.uri = StoryMedia.copy(applicationContext, Uri.parse(m.uri)); store.save(story) } } } finally { importing = false }
            }) { importing = false; render() }
        })
        content.space(18); content.addFull(settingRow(if (story.draft) "删除草稿" else "删除这个故事", "移入回收站，不删除手机原图") {
            MemoryDialogBuilder(this).setTitle("将${if (story.draft) "草稿" else "故事"}移入回收站？").setMessage("以后可在设置的回收站恢复。")
                .setNegativeButton("取消", null).setPositiveButton("移入回收站") { _, _ -> story.deletedAt = System.currentTimeMillis(); if (save()) { closing = true; finish() } }.show()
        })
    }
    private fun preview() {
        if (story.moments.isEmpty()) message("先添加照片或视频") else if (save()) startActivity(Intent(this, StoryPlayerActivity::class.java).putExtra("story_id", story.id))
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
        val duration = button("照片停留 ${story.intervalSeconds} 秒") {}
        duration.setOnClickListener {
            val choices = intArrayOf(3, 5, 8, 12)
            MemoryDialogBuilder(this).setTitle("每张照片停留多久").setSingleChoiceItems(choices.map { "$it 秒" }.toTypedArray(), choices.indexOf(story.intervalSeconds)) { dialog, index -> story.intervalSeconds = choices[index]; save(); duration.text = "照片停留 ${story.intervalSeconds} 秒"; dialog.dismiss() }.show()
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
    private fun pickMedia() {
        captureText()
        startActivityForResult(Intent(this, StoryMediaPickerActivity::class.java), 401)
    }
    private fun importMedia(uris: List<Uri>) {
        captureText(); importing = true
        backgroundWork("添加照片与视频", { update ->
            val failures = mutableListOf<String>()
            var added = 0
            var duplicates = 0
            try {
                uris.forEachIndexed { index, uri ->
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
                Triple(added, duplicates, failures)
            } finally { importing = false }
        }) { result ->
            render()
            val summary = "已保存 ${result.first} 个片段" + if (result.second > 0) "，跳过 ${result.second} 个重复文件" else ""
            if (result.third.isEmpty()) message(summary)
            else MemoryDialogBuilder(this).setTitle("添加结果").setMessage("$summary\n${result.third.size} 个文件未保存：\n" + result.third.take(8).joinToString("\n") + if (result.third.size > 8) "\n其余失败文件请重新选择。" else "").setPositiveButton("知道了", null).show()
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
    val moment = story.moments[index]
    activity.startActivity(Intent(activity, if (moment.video) StoryPlayerActivity::class.java else StoryPhotoActivity::class.java)
        .putExtra("story_id", story.id).putExtra("start_index", index).putExtra("single", true))
}
