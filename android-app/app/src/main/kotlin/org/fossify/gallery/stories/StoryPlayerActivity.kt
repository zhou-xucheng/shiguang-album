package org.fossify.shiguang.stories

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import android.graphics.drawable.Drawable

/** Foreground-only story player: audio focus is needed only for audible video. */
class StoryPlayerActivity : StoryActivity() {
    private lateinit var story: Story
    private lateinit var image: ImageView
    private lateinit var surface: SurfaceView
    private lateinit var caption: TextView
    private lateinit var counter: TextView
    private lateinit var playButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var video: ExoPlayer
    private lateinit var audio: AudioManager
    private lateinit var focus: AudioFocusRequest
    private val handler = Handler(Looper.getMainLooper())
    private var index = 0
    private var playing = false
    private var ended = false
    private var ready = false
    private var photoRemaining = 0L
    private var photoStarted = 0L
    private var renderGeneration = 0
    private var receiverRegistered = false
    private var resumeAfterFocus = false
    private var disposed = false
    private var single = false
    private lateinit var seek: SeekBar
    private lateinit var segmentButton: Button
    private var singleSound = true
    private var soundButton: Button? = null
    private lateinit var timing: TextView
    private var seeking = false
    private val tick = object : Runnable {
        override fun run() {
            if (disposed) return
            if (ended) timing.text = "已播放完成 · 可以重播"
            else if (::video.isInitialized && story.moments[index].video) {
                val duration = video.duration.coerceAtLeast(0)
                if (!seeking) seek.progress = if (duration > 0) (video.currentPosition * 1000 / duration).toInt() else 0
                timing.text = "${time(video.currentPosition)} / ${time(duration)}"
            } else {
                val remain = (photoRemaining - if (playing && photoStarted > 0) SystemClock.elapsedRealtime() - photoStarted else 0).coerceAtLeast(0)
                timing.text = "照片停留 ${story.intervalSeconds} 秒 · 剩余 ${(remain + 999) / 1000} 秒"
            }
            handler.postDelayed(this, 100)
        }
    }
    private fun time(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
    private val nextPhoto = Runnable { advance() }
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { resumeAfterFocus = false; pause() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("Missing story") }) { finish(); return }
        if (story.moments.isEmpty()) { message("这个故事还没有片段"); finish(); return }
        single = intent.getBooleanExtra("single", false)
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("start_index", 0)).coerceIn(story.moments.indices)
        audio = getSystemService(AudioManager::class.java)
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterFocus = false; pause() }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        resumeAfterFocus = playing; pause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) { resumeAfterFocus = false; resume() }
                }
            }.build()
        video = ExoPlayer.Builder(this).build()
        buildPlayer()
        video.setVideoSurfaceView(surface)
        video.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (disposed) return
                if (state == Player.STATE_ENDED && playing) advance()
                if (state == Player.STATE_READY && story.moments[index].video) { ready = true; progress.visibility = View.GONE }
            }
            override fun onPlayerError(error: PlaybackException) { mediaError() }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.width <= 0 || size.height <= 0) return
                surface.post {
                    val parent = surface.parent as? android.view.ViewGroup ?: return@post
                    val ratio = size.width * size.pixelWidthHeightRatio / size.height
                    val width = minOf(parent.width, (parent.height * ratio).toInt())
                    surface.layoutParams = FrameLayout.LayoutParams(width, (width / ratio).toInt(), Gravity.CENTER)
                }
            }
        })
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        showMoment()
        resume()
        handler.post(tick)
    }

    private fun buildPlayer() {
        val root = column().apply { setBackgroundColor(Color.rgb(25, 23, 21)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val top = row().apply { setPadding(dp(18), dp(8), dp(18), dp(8)) }
        top.addView(iconButton(org.fossify.shiguang.R.drawable.ic_memory_back, "返回") { finish() }.apply {
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        counter = label("", 14f, Color.LTGRAY).apply { gravity = Gravity.END }
        top.addView(counter, LinearLayout.LayoutParams(0, -2, 1f))
        if (!single) top.addView(iconButton(org.fossify.shiguang.R.drawable.ic_memory_settings, "播放设置") { showPlaybackSettings() }.apply {
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        if (single) {
            soundButton = button("原声开") { singleSound = !singleSound; video.volume = if (singleSound) 1f else 0f; soundButton?.text = if (singleSound) "原声开" else "原声关"; if (playing && !syncAudioFocus()) pause() }.apply { setTextColor(Color.WHITE) }
            top.addView(soundButton)
        }
        root.addFull(top)
        val stage = FrameLayout(this)
        image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = "故事照片" }
        surface = SurfaceView(this)
        stage.addView(image, FrameLayout.LayoutParams(-1, -1))
        stage.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        progress = ProgressBar(this)
        stage.addView(progress, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottom = column().apply { setPadding(dp(24), dp(18), dp(24), dp(18)) }
        bottom.addFull(editorial(story.title, 25f, Color.WHITE).apply { gravity = Gravity.CENTER; maxLines = 2 })
        caption = label("", 14f, Color.LTGRAY).apply { maxLines = 4; gravity = Gravity.CENTER }
        bottom.space(6); bottom.addFull(caption)
        bottom.space(8)
        timing = label("", 13f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        bottom.addFull(timing)
        seek = SeekBar(this).apply {
            max = 1000; contentDescription = "当前视频进度"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(s: SeekBar?) { seeking = true }
                override fun onStopTrackingTouch(s: SeekBar?) { if (video.duration > 0) video.seekTo(video.duration * progress / 1000); seeking = false }
                override fun onProgressChanged(s: SeekBar?, value: Int, user: Boolean) {}
            })
        }
        bottom.addFull(seek)
        segmentButton = button("选择故事片段") { chooseSegment() }.apply { setTextColor(Color.LTGRAY); strokeWidth = 0; visibility = if (single || story.moments.size == 1) View.GONE else View.VISIBLE }
        bottom.addFull(segmentButton)
        val controls = row()
        controls.addView(button("上一段") { index = (index - 1).coerceAtLeast(0); ended = false; showMoment() }.apply { if (single) visibility = View.GONE; setTextColor(Color.WHITE); strokeColor = android.content.res.ColorStateList.valueOf(0xFF68615B.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        playButton = button("暂停", true) {
            resumeAfterFocus = false
            if (playing) pause() else {
                if (ended) { if (!single) index = 0; ended = false; showMoment() }
                resume()
            }
        }
        controls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("下一段") { advance() }.apply { if (single) visibility = View.GONE; setTextColor(Color.WHITE); strokeColor = android.content.res.ColorStateList.valueOf(0xFF68615B.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addFull(controls)
        root.addFull(bottom)
        setContentView(root)
    }

    private fun showMoment() {
        if (disposed) return
        handler.removeCallbacks(nextPhoto)
        photoStarted = 0
        photoRemaining = story.intervalSeconds.coerceIn(1, 60) * 1000L
        ready = false
        renderGeneration++
        val generation = renderGeneration
        val moment = story.moments[index]
        counter.text = if (single) "视频" else "${index + 1} / ${story.moments.size}"
        seek.visibility = if (moment.video) View.VISIBLE else View.GONE
        segmentButton.text = "故事片段 ${index + 1} / ${story.moments.size}  ·  选择片段"
        playButton.text = if (playing) "暂停" else "播放"
        caption.text = StoryMedia.dateLabel(moment) + if (moment.caption.isBlank()) "" else "\n${moment.caption}"
        video.stop()
        video.clearMediaItems()
        image.animate().cancel()
        Glide.with(this).clear(image)
        image.setImageDrawable(null)
        image.alpha = 1f
        image.visibility = if (moment.video) View.GONE else View.VISIBLE
        surface.visibility = if (moment.video) View.VISIBLE else View.GONE
        progress.visibility = View.VISIBLE
        if (playing && !syncAudioFocus()) { pause(); message("其他应用正在使用声音，请稍后点击播放") }
        if (moment.video) {
            video.volume = if (story.videoSoundEnabled(single) && (!single || singleSound)) 1f else 0f
            video.setMediaItem(MediaItem.fromUri(moment.uri))
            video.prepare()
            video.playWhenReady = playing
        } else {
            Glide.with(this).load(Uri.parse(moment.uri)).listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    if (generation == renderGeneration) handler.post { if (generation == renderGeneration) mediaError() }
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    if (generation == renderGeneration) handler.post {
                        if (disposed || generation != renderGeneration) return@post
                        progress.visibility = View.GONE; ready = true
                        if (story.transition) { image.alpha = 0f; image.animate().alpha(1f).setDuration(650).start() }
                        if (playing) schedulePhoto()
                    }
                    return false
                }
            }).into(image)
        }
    }

    private fun mediaError() {
        if (disposed || !::caption.isInitialized) return
        pause()
        progress.visibility = View.GONE
        caption.text = "这个片段暂时无法播放。文件可能已移动、删除或格式不支持。\n可切到下一段，或返回重新添加。"
        ready = false
    }

    private fun schedulePhoto() {
        handler.removeCallbacks(nextPhoto)
        photoStarted = SystemClock.elapsedRealtime()
        handler.postDelayed(nextPhoto, photoRemaining.coerceAtLeast(100))
    }

    private fun resume() {
        if (disposed || !::video.isInitialized) return
        if (!syncAudioFocus()) {
            message("其他应用正在使用声音，请稍后点击播放")
            return
        }
        playing = true
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        playButton.text = "暂停"
        if (story.moments[index].video) video.play() else if (ready) schedulePhoto()
    }

    private fun pause() {
        if (!::video.isInitialized) return
        if (playing && photoStarted != 0L) photoRemaining = (photoRemaining - (SystemClock.elapsedRealtime() - photoStarted)).coerceAtLeast(100)
        photoStarted = 0L
        playing = false
        handler.removeCallbacks(nextPhoto)
        video.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::playButton.isInitialized) playButton.text = if (ended) "重播" else "播放"
    }

    private fun syncAudioFocus(): Boolean {
        val audible = story.moments[index].video && story.videoSoundEnabled(single) && (!single || singleSound)
        if (!audible) {
            audio.abandonAudioFocusRequest(focus)
            return true
        }
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun showPlaybackSettings() {
        val wasPlaying = playing
        pause()
        val box = column().apply { setPadding(dp(24), dp(8), dp(24), dp(16)) }
        box.addFull(label("每张照片停留", 16f)); box.space(8)
        val choices = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(3, 5, 8, 12).forEach { seconds -> choices.addView(RadioButton(this).apply {
            id = seconds; text = "${seconds}秒"; textSize = 14f; setTextColor(ink); minimumHeight = dp(48)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
        }, RadioGroup.LayoutParams(0, -2, 1f)) }
        choices.check(story.intervalSeconds)
        choices.setOnCheckedChangeListener { _, seconds -> story.intervalSeconds = seconds; photoRemaining = seconds * 1000L }
        box.addFull(choices)
        MemoryDialogBuilder(this).setTitle("播放设置").setView(box).setPositiveButton(if (wasPlaying) "继续观看" else "完成", null)
            .setOnDismissListener { safely { store.save(story) }; if (wasPlaying && !isFinishing && !disposed && androidx.lifecycle.Lifecycle.State.RESUMED.let { lifecycle.currentState.isAtLeast(it) }) resume() }.show()
    }
    private fun chooseSegment() {
        val wasPlaying = playing
        pause()
        val names = story.moments.mapIndexed { i, m -> "${i + 1} · ${if (m.video) "视频" else "照片"} · ${m.caption.ifBlank { StoryMedia.dateLabel(m) }.take(50)}" }.toTypedArray()
        MemoryDialogBuilder(this).setTitle("选择故事片段").setSingleChoiceItems(names, index) { dialog, selected ->
            index = selected; ended = false; showMoment(); dialog.dismiss()
        }.setNegativeButton("返回", null).setOnDismissListener {
            if (wasPlaying && !isFinishing && !disposed && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) resume()
        }.show()
    }
    private fun advance() {
        if (single) { ended = true; pause(); return }
        if (index < story.moments.lastIndex) { index++; showMoment() }
        else if (story.loop) { index = 0; showMoment() }
        else { ended = true; pause() }
    }

    override fun onResume() {
        super.onResume()
        // The player always has a dark background, independently of the gallery theme.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onPause() { resumeAfterFocus = false; pause(); if (::audio.isInitialized) audio.abandonAudioFocusRequest(focus); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("index", index); super.onSaveInstanceState(outState) }
    override fun onDestroy() {
        disposed = true
        handler.removeCallbacksAndMessages(null)
        if (receiverRegistered) unregisterReceiver(noisy)
        if (::video.isInitialized) { video.release(); audio.abandonAudioFocusRequest(focus) }
        super.onDestroy()
    }
}
