package org.fossify.shiguang.stories

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import org.fossify.shiguang.activities.MediaActivity
import org.fossify.shiguang.helpers.DIRECTORY
import org.fossify.shiguang.helpers.RECYCLE_BIN

class MemoryTrashActivity : StoryActivity() {
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val content = page("回收站", "删除的内容，在这里找回")
        content.addFull(panel().apply { addFull(settingRow("照片与视频", "查看手机相册中删除的文件") { requestMediaPermissions(true) { startActivity(Intent(this@MemoryTrashActivity, MediaActivity::class.java).putExtra(DIRECTORY, RECYCLE_BIN)) } }) })
        content.space(24); content.addFull(eyebrow("已删除的故事")); content.space(12)
        safely {
            val deleted = store.all().filter { it.deletedAt != 0L }
            if (deleted.isEmpty()) content.addFull(label("这里还没有故事。", 16f, muted))
            deleted.forEach { story ->
                content.addFull(panel().apply { addFull(settingRow(story.title, "${story.moments.size} 个片段 · 可恢复文字与编排") {
                    MemoryChrome.sheet(this@MemoryTrashActivity, story.title, actions = listOf(
                        MemoryChrome.Action("恢复故事") { safely { story.deletedAt = 0; store.save(story); render() } },
                        MemoryChrome.Action("彻底删除", "不会删除手机中的原始照片") {
                            MemoryDialogBuilder(this@MemoryTrashActivity).setTitle("彻底删除这个故事？").setMessage("文字与编排无法找回。未备份的故事请先恢复并备份。")
                                .setNegativeButton("取消", null).setPositiveButton("彻底删除") { _, _ -> safely { store.permanentlyDelete(story.id); render() } }.show()
                        }
                    ))
                }) }); content.space(12)
            }
        }
    }
}
