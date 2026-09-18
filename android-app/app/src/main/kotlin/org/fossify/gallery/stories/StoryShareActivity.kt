package org.fossify.shiguang.stories

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.bumptech.glide.Glide
import org.fossify.shiguang.BuildConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

class StoryShareActivity : StoryActivity() {
    private lateinit var story: Story
    private var style = AlbumStyle.PAPER
    private val exportKey get() = "${story.id}.${style.name}"
    private lateinit var status: TextView
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var job: Future<*>? = null
    private var transformer: Transformer? = null
    private var prepared: StoryVideoExport.Prepared? = null
    private var output: File? = null
    private var durationMs = 0L
    private var working = false
    private var generation = 0
    private var preview: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事已不存在。") }) { finish(); return }
        style = AlbumStyle.from(savedInstanceState?.getString("style") ?: getSharedPreferences("story-share-videos", MODE_PRIVATE).getString("${story.id}.style", null))
        savedInstanceState?.getString("output")?.let { path ->
            val file = File(path)
            if (file.canonicalPath.startsWith(File(cacheDir, "shared-stories").canonicalPath + File.separator) && file.isFile && file.length() > 0) output = file
        }
        durationMs = savedInstanceState?.getLong("duration") ?: 0
        if (output == null) {
            val history = getSharedPreferences("story-share-videos", MODE_PRIVATE)
            if (history.getLong("$exportKey.updated", -1) == story.updatedAt) {
                val file = File(history.getString("$exportKey.path", "") ?: "")
                if (file.canonicalPath.startsWith(File(cacheDir, "shared-stories").canonicalPath + File.separator) && file.isFile && file.length() > 0) {
                    output = file; durationMs = history.getLong("$exportKey.duration", 0)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { leavePage() } })
        render()
    }

    private fun render() {
        val action = if (working) null else button(if (output != null) "分享到微信" else "生成分享视频", true) {
            if (output != null) share(true) else startExport()
        }
        val content = page("分享故事", "", footer = action)
        content.addFull(label(if (output != null) "视频已做好，点下方「分享到微信」，再选择亲友发送。" else "选样式 → 生成视频 → 发给亲友", 15f, accentText, true))
        content.space(14)
        content.addFull(editorial(story.title, 25f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        content.space(7)
        content.addFull(label("${story.moments.count { !it.video }} 张照片 · ${story.moments.count { it.video }} 段视频", 13f, muted))
        content.space(18)
        val frame = FrameLayout(this).apply { background = shape(cardColor, 18); clipToOutline = true }
        val cover = ImageView(this).apply {
            scaleType = if (output != null) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            contentDescription = if (output != null) "预览视频" else "相册封面"
        }
        if (output != null) Glide.with(this).load(output).into(cover)
        else story.cover()?.let { Glide.with(this).load(Uri.parse(it.uri)).into(cover) }
        frame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        if (output != null) {
            frame.isFocusable = true; frame.contentDescription = "预览视频"; frame.setOnClickListener { showPreview() }
            frame.addView(iconButton(org.fossify.shiguang.R.drawable.ic_memory_play, "预览视频") { showPreview() }.apply {
                background = shape(accent, 32); imageTintList = android.content.res.ColorStateList.valueOf(contrast(accent))
            }, FrameLayout.LayoutParams(dp(60), dp(60), android.view.Gravity.CENTER))
        }
        content.addFull(frame, if (output != null) 290 else 164)
        content.space(16)
        status = label(if (output != null) "${style.title} · ${durationMs / 1000} 秒 · ${Formatter.formatFileSize(this, output!!.length())}" else if (working) "正在制作相册…" else "选一种喜欢的样式", 15f, if (output != null) muted else ink, output == null)
        content.addFull(status)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = if (working) View.VISIBLE else View.GONE }
        content.addFull(progressBar)
        if (working) {
            content.space(12); content.addFull(label("正在手机上生成，请留在这个页面。", 14f, muted))
            content.addFull(button("取消生成") { cancelExport(); render() })
        } else if (output != null) {
            content.space(14)
            val secondary = row()
            secondary.addView(button("保存到手机") { saveVideo() }, LinearLayout.LayoutParams(0, -2, 1f))
            secondary.addView(button("其他分享方式") { share(false) }, LinearLayout.LayoutParams(0, -2, 1f))
            content.addFull(secondary)
            content.addFull(button("更换相册样式") { output = null; render() }.apply { strokeWidth = 0; setTextColor(accentText) })
            content.space(10); content.addFull(label("点画面先看一遍。亲友收到视频后，可以直接播放。", 14f, muted))
        } else {
            content.space(10)
            AlbumStyle.entries.forEach { option ->
                val selected = style == option
                val item = row().apply {
                    setPadding(dp(14), dp(12), dp(14), dp(12)); minimumHeight = dp(66)
                    background = shape(if (selected) androidx.core.graphics.ColorUtils.blendARGB(paper, accent, .08f) else surfaceColor, 14).apply { setStroke(dp(1), if (selected) accent else lineColor) }
                    isFocusable = true; isSelected = selected; contentDescription = option.title + if (selected) "，已选择" else ""
                    setOnClickListener {
                        style = option
                        getSharedPreferences("story-share-videos", MODE_PRIVATE).edit().putString("${story.id}.style", style.name).apply()
                        render()
                    }
                }
                val swatch = TextView(this).apply {
                    text = "拾光"; gravity = android.view.Gravity.CENTER; textSize = 12f
                    setTextColor(option.foreground); background = shape(option.background, 6)
                }
                item.addView(swatch, LinearLayout.LayoutParams(dp(44), dp(48)))
                item.addView(column().apply { addFull(label(option.title, 15f, bold = true)); space(3); addFull(label(option.detail, 12f, muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
                item.addView(label(if (selected) "✓" else "○", 20f, accentText))
                content.addFull(item); content.space(8)
            }
            content.space(4)
            content.addFull(label("照片与视频完整显示，保留故事文字${if (story.originalSound) "与视频原声" else ""}。长文字会按画幅省略。", 13f, muted))
        }
    }

    private fun startExport() {
        if (working) return
        val token = ++generation
        working = true; render(); progressBar.isIndeterminate = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Snapshot the story; exporting never changes its order, title or playback preferences.
        val snapshot = com.google.gson.Gson().fromJson(com.google.gson.Gson().toJson(story), Story::class.java)
        job = worker.submit {
            val result = runCatching { StoryVideoExport.prepare(applicationContext, snapshot, style) { message ->
                runOnUiThread { if (!isDestroyed && token == generation) status.text = message }
            } }
            runOnUiThread {
                if (isDestroyed || token != generation) { result.getOrNull()?.directory?.deleteRecursively(); return@runOnUiThread }
                result.onSuccess { ready ->
                    prepared = ready; durationMs = ready.durationMs
                    runCatching { encode(ready, token) }.onFailure { failed(it) }
                }.onFailure { failed(it) }
            }
        }
    }

    private fun encode(ready: StoryVideoExport.Prepared, token: Int) {
        val encoder = DefaultEncoderFactory.Builder(this)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(2_500_000).build())
            .setEnableFallback(true).build()
        transformer = Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoder).addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    if (token != generation || isDestroyed) return
                    if (!ready.output.isFile || ready.output.length() == 0L) { failed(IllegalStateException("没有生成完整的视频，请重试。")); return }
                    handler.removeCallbacksAndMessages(null)
                    output = ready.output; working = false; transformer = null
                    getSharedPreferences("story-share-videos", MODE_PRIVATE).edit()
                        .putLong("$exportKey.updated", story.updatedAt).putString("$exportKey.path", ready.output.absolutePath)
                        .putLong("$exportKey.duration", durationMs).apply()
                    ready.directory.listFiles().orEmpty().filter { it.extension == "jpg" }.forEach { it.delete() }
                    prepared = null
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    render()
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    if (token == generation && !isDestroyed) failed(exception)
                }
            }).build()
        status.text = "正在生成视频…"
        transformer!!.start(ready.composition, ready.output.absolutePath)
        val holder = ProgressHolder()
        handler.post(object : Runnable {
            override fun run() {
                if (!working || token != generation) return
                if (cacheDir.usableSpace < 32L * 1024 * 1024) { failed(IllegalStateException("手机空间不足，请清理后重试。")); return }
                val progress = transformer?.getProgress(holder)
                progressBar.isIndeterminate = progress != Transformer.PROGRESS_STATE_AVAILABLE
                if (progress == Transformer.PROGRESS_STATE_AVAILABLE) { progressBar.progress = holder.progress; status.text = "正在生成视频 ${holder.progress}%" }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun failed(error: Throwable) {
        android.util.Log.e("StoryVideoExport", "Export failed", error)
        cancelExport(); render()
        MemoryDialogBuilder(this).setTitle("视频还没有生成")
            .setMessage(if (error is ExportException) "手机暂时无法处理其中的照片或视频。请检查原文件，或减少片段后重试。原故事没有改动。" else error.message ?: "请检查文件和手机空间后重试。")
            .setPositiveButton("知道了", null).show()
    }

    private fun cancelExport() {
        generation++; handler.removeCallbacksAndMessages(null); job?.cancel(true); job = null
        transformer?.cancel(); transformer = null
        prepared?.directory?.deleteRecursively(); prepared = null; working = false
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sharedUri(): Uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", output ?: error("请先生成视频。"))

    private fun share(wechat: Boolean) {
        if (output?.isFile != true) { output = null; render(); message("临时视频已清理，请重新生成。"); return }
        val uri = sharedUri()
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newUri(contentResolver, "相册视频", uri) }
        if (wechat) {
            send.setPackage("com.tencent.mm")
            try { startActivity(send) } catch (_: android.content.ActivityNotFoundException) {
                message("没有找到可接收视频的微信，请先保存到手机，或使用其他分享方式。")
            }
        } else startActivity(Intent.createChooser(send, "分享视频相册"))
    }

    private fun showPreview() {
        val file = output?.takeIf { it.isFile } ?: return
        val video = VideoView(this)
        val controller = MediaController(this)
        video.setMediaController(controller); controller.setAnchorView(video)
        video.setVideoPath(file.absolutePath)
        video.setOnPreparedListener { video.start() }
        video.setOnErrorListener { _, _, _ -> message("视频预览失败，可以保存到手机后播放。"); true }
        val box = FrameLayout(this).apply { addView(video, FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER)) }
        preview = MemoryDialogBuilder(this).setTitle("视频预览").setView(box).setPositiveButton("关闭", null)
            .setOnDismissListener { video.stopPlayback(); preview = null }.create()
        preview!!.show(); box.layoutParams = box.layoutParams.apply { height = dp(420) }
    }

    private fun saveVideo() {
        val name = story.title.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "-").take(40).ifBlank { "回忆相册" }
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType("video/mp4").putExtra(Intent.EXTRA_TITLE, "$name.mp4"), 801)
    }

    @Deprecated("Activity document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 801 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return; val file = output ?: return
        backgroundWork("保存视频", { _ ->
            contentResolver.openOutputStream(uri, "wt")!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        }) { message("视频已保存，可从微信选择发送。") }
    }

    override fun leavePage() {
        if (working) MemoryDialogBuilder(this).setTitle("停止生成视频？").setMessage("返回后需要重新生成，原故事不受影响。")
            .setNegativeButton("继续生成", null).setPositiveButton("停止并返回") { _, _ -> cancelExport(); finish() }.show()
        else finish()
    }
    override fun onPause() { preview?.dismiss(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("style", style.name); output?.let { outState.putString("output", it.absolutePath); outState.putLong("duration", durationMs) }; super.onSaveInstanceState(outState) }
    override fun onDestroy() { cancelExport(); worker.shutdownNow(); super.onDestroy() }
}
