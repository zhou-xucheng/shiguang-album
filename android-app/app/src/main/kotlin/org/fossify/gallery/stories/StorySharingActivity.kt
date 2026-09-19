package org.fossify.shiguang.stories

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import org.fossify.shiguang.BuildConfig
import java.io.File

/** A video export is not an interactive shared album. */
class StorySharingActivity : StoryActivity() {
    private lateinit var story: Story
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if(!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在") }) { finish(); return }
        val content = page("分享方式", "")
        content.addFull(editorial(story.title,28f)); content.space(12)
        content.addFull(label("先选择亲友收到的内容",15f,muted)); content.space(24)
        content.addFull(panel().apply {
            addFull(label("照片与视频",20f,bold=true)); space(8)
            addFull(label("发送选中的原始媒体，亲友可以逐张查看。不会包含相册封面排版和故事文字。",15f,muted)); space(12)
            addFull(button("选择要发送的照片",true) { selectMedia() })
        }); content.space(16)
        content.addFull(panel().apply {
            addFull(label("视频文件",20f,bold=true)); space(8)
            addFull(label("按顺序合成一段 MP4，适合连续播放。接收后无法像相册一样自由选照片。",15f,muted)); space(12)
            addFull(button("导出视频") { startActivity(Intent(this@StorySharingActivity,StoryShareActivity::class.java).putExtra("story_id",story.id)) })
        }); content.space(24)
        content.addFull(label("在线相册 · 尚未接入",17f,bold=true)); content.space(8)
        content.addFull(label("打开链接就能看封面、照片总览并自由翻页。需要在线存储；当前版本尚不能生成这种链接，照片仍只在手机上。",15f,muted))
    }
    private fun selectMedia() {
        if(story.moments.isEmpty()) { message("故事还没有照片"); return }
        val selected = linkedSetOf<String>()
        val dialog = MemoryDialogBuilder(this).setTitle("选择要发送的照片与视频").setNegativeButton("取消",null).setPositiveButton("发送",null).create()
        val box = column()
        val info = label("已选 0 个 · 接收应用可能限制一次发送数量",13f,muted).apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }; box.addFull(info)
        lateinit var grid: StoryTiles
        grid = StoryTiles(this,story.moments,{it.id in selected}) { i ->
            if(!selected.add(story.moments[i].id)) selected.remove(story.moments[i].id)
            info.text = "已选 ${selected.size} 个"; grid.adapter?.notifyItemChanged(i)
        }
        box.addView(grid,LinearLayout.LayoutParams(-1,(resources.displayMetrics.heightPixels*.55).toInt()))
        dialog.setView(box); dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            if(selected.isEmpty()) message("先选择要发送的照片") else { dialog.dismiss(); sendMedia(story.moments.filter { it.id in selected }) }
        } }; dialog.show()
    }
    private fun sendMedia(moments:List<StoryMoment>) {
        safely {
            val uris = ArrayList(moments.map { m ->
                val uri = Uri.parse(m.uri)
                if(uri.scheme == "file") { val file = File(uri.path ?: error("文件无法读取")); require(file.isFile); FileProvider.getUriForFile(this,BuildConfig.APPLICATION_ID+".provider",file) } else uri
            })
            val fallbackType = if(moments.all { !it.video }) "image/*" else if(moments.all { it.video }) "video/*" else "*/*"
            val type = if (uris.size == 1) contentResolver.getType(uris.first()) ?: fallbackType else fallbackType
            val clip = ClipData.newUri(contentResolver,"照片与视频",uris.first()); uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            val send = StoryShareIntents.media(uris, type, clip)
            startActivity(Intent.createChooser(send,"发送照片与视频"))
        }
    }
}
