package org.fossify.shiguang.stories

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StoryBackupStatus {
    private fun prefs(context: Context) = context.getSharedPreferences("story-backup-status", Context.MODE_PRIVATE)
    fun record(context: Context, bytes: Long, name: String, startedAt: Long) {
        prefs(context).edit().putLong("time", System.currentTimeMillis()).putLong("snapshot", startedAt).putLong("bytes", bytes).putString("name", name).apply()
    }
    fun summary(context: Context): String {
        val time = prefs(context).getLong("time", 0)
        if (time == 0L) return "尚未在此设备完成备份"
        return "上次成功：" + SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA).format(Date(time))
    }
    fun details(context: Context): String {
        val p = prefs(context)
        if (p.getLong("time", 0) == 0L) return "把故事存成一个文件，换手机时也能带走。"
        val changed = StoryStore(context).all().any { it.updatedAt > p.getLong("snapshot", 0) }
        return "${p.getString("name", "备份文件")} · ${Formatter.formatFileSize(context, p.getLong("bytes", 0))}\n" +
            (if (changed) "上次备份后有故事更新，建议再次备份。" else "这里记录上次成功导出；请保管好外部备份文件。")
    }
}

class MemoryBackupActivity : StoryActivity() {
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val content = page("备份与恢复", "为珍贵的回忆，多留一份")
        val status = panel()
        status.addFull(label(StoryBackupStatus.summary(this), 18f, bold = true)); status.space(8)
        status.addFull(label(StoryBackupStatus.details(this), 14f, muted)); status.space(18)
        status.addFull(button("完整备份故事", true) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, "拾光故事-${java.time.LocalDate.now()}.zip"), 701)
        }); content.addFull(status); content.space(20)
        val restore = panel()
        restore.addFull(settingRow("从备份文件恢复", "作为新故事加入，保留现有内容") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 702)
        }); content.addFull(restore); content.space(24)
        content.addFull(eyebrow("哪些内容会保存")); content.space(10)
        content.addFull(label("包括故事、草稿、故事回收站，以及它们使用的照片、视频、文字和封面。", 14f, muted)); content.space(12)
        content.addFull(label("未加入故事的手机照片、相册回收站和外观设置不在此备份中。", 14f, muted)); content.space(20)
        content.addFull(label("单份备份内容最多 20 GiB，超限会中止导出。新备份请使用 0.5.0 或更新版本恢复；旧版备份仍可读取。", 14f, muted)); content.space(20)
        content.addFull(eyebrow("换手机或卸载前")); content.space(10)
        content.addFull(label("把备份保存到下载文件夹，并复制到电脑或其他设备。卸载会清除应用内的故事和独立副本，重新安装后需要选择备份文件恢复。", 14f, muted))
    }
    @Deprecated("Activity document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == 701) backgroundWork("完整备份", { update ->
            val started = System.currentTimeMillis()
            val result = StoryBackup.exportWithSize(applicationContext, uri, update)
            StoryBackupStatus.record(applicationContext, result.second, StoryMedia.name(applicationContext, uri), started)
            result.first
        }) { count -> render(); message("已完整备份 $count 个故事。请保管好备份文件。") }
        if (requestCode == 702) MemoryDialogBuilder(this).setTitle("恢复这份备份？").setMessage("照片和视频将复制到应用中。现有故事不会被覆盖，重复恢复会生成新的副本。")
            .setNegativeButton("取消", null).setPositiveButton("恢复") { _, _ -> backgroundWork("恢复故事", { update -> StoryBackup.restore(applicationContext, uri, update) }) { count -> render(); message("已恢复 $count 个故事，可在故事和草稿中查看。") } }.show()
    }
}
