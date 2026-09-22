package org.fossify.shiguang.stories

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.alexvasilkov.gestures.GestureController
import com.alexvasilkov.gestures.GestureImageView
import com.alexvasilkov.gestures.State
import com.bumptech.glide.Glide
import org.fossify.shiguang.R

/** The story's primary viewer: swipe, thumbnails and autoplay all share one position. */
class StoryPhotoActivity : StoryActivity() {
    private lateinit var story: Story
    private lateinit var pager: ViewPager
    private lateinit var counter: TextView
    private lateinit var caption: TextView
    private lateinit var strip: RecyclerView
    private lateinit var playButton: com.google.android.material.button.MaterialButton
    private val pages = mutableMapOf<Int, FrameLayout>()
    private val videos = mutableMapOf<Int, VideoView>()
    private val timer = Handler(Looper.getMainLooper())
    private var index = 0
    private var autoplay = false
    private val advance = Runnable { advanceFromCurrent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely {
                story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在")
                require(story.moments.isNotEmpty()) { "故事里还没有照片或视频" }
            }) { finish(); return }
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("start_index", 0)).coerceIn(story.moments.indices)
        autoplay = savedInstanceState?.getBoolean("autoplay") ?: false
        render()
    }

    private fun render() {
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val controls = row()
        controls.addView(button("缩略图") { pauseAutoplay(); overview(story, index) { jump(it) } }, LinearLayout.LayoutParams(0, -2, 1f))
        playButton = button("", true) { if (autoplay) pauseAutoplay() else startAutoplay() }
        controls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1.35f))
        controls.addView(button("编辑") { pauseAutoplay(); startActivity(Intent(this, StoryEditorActivity::class.java).putExtra("story_id", story.id)) }, LinearLayout.LayoutParams(0, -2, 1f))
        val more = iconButton(R.drawable.ic_memory_more, "故事操作") { pauseAutoplay(); storyActions() }
        val body = fixedPage(story.title, controls, more)

        counter = label("", 13f, muted, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), if (landscape) 0 else dp(6), dp(16), if (landscape) 0 else dp(8))
        }
        body.addFull(counter)
        pager = ViewPager(this).apply { id = View.generateViewId(); offscreenPageLimit = 1 }
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = story.moments.size
            override fun isViewFromObject(view: View, item: Any) = view === item
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val moment = story.moments[position]
                val frame = FrameLayout(this@StoryPhotoActivity).apply { setBackgroundColor(paper) }
                if (moment.video) {
                    val image = ImageView(this@StoryPhotoActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                    Glide.with(this@StoryPhotoActivity).load(Uri.parse(moment.uri)).into(image)
                    frame.addView(image, FrameLayout.LayoutParams(-1, -1))
                    frame.addView(button("▷ 播放视频", true) { pauseAutoplay(); playVideo(position, false) }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                    frame.contentDescription = "第 ${position + 1} 段视频，可左右滑动"
                } else {
                    val image = GestureImageView(this@StoryPhotoActivity).apply {
                        contentDescription = "第 ${position + 1} 张照片，可左右滑动，双指缩放"
                        controller.settings.maxZoom = 6f; controller.settings.doubleTapZoom = 2f
                    }
                    var baseZoom = 0f
                    image.controller.addOnStateChangeListener(object : GestureController.OnStateChangeListener {
                        override fun onStateChanged(state: State) {
                            if (baseZoom == 0f && image.controller.settings.hasImageSize() && image.controller.settings.hasViewportSize()) baseZoom = state.zoom
                        }
                    })
                    image.setOnTouchListener { _, event ->
                        image.parent.requestDisallowInterceptTouchEvent(event.pointerCount > 1 || image.controller.state.zoom > baseZoom + .01f)
                        false
                    }
                    Glide.with(this@StoryPhotoActivity).load(Uri.parse(moment.uri)).error(android.R.drawable.ic_menu_report_image).into(image)
                    frame.addView(image, FrameLayout.LayoutParams(-1, -1))
                }
                container.addView(frame); pages[position] = frame; return frame
            }
            override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
                stopVideo(position); pages.remove(position); container.removeView(item as View)
            }
        }
        if (!MemoryPaper.reducedMotion(this)) pager.setPageTransformer(false) { view, position -> view.alpha = 1f - kotlin.math.abs(position).coerceAtMost(1f) * .18f }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                timer.removeCallbacks(advance)
                videos.keys.toList().forEach { stopVideo(it) }
                pages.values.forEach { (it.getChildAt(0) as? GestureImageView)?.controller?.resetState() }
                index = position; updateInfo()
                if (autoplay) scheduleCurrent()
            }
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) pauseAutoplay()
            }
        })
        body.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))

        caption = label("", 14f, muted).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8)); maxLines = 3
            if (landscape) maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; minimumHeight = if (landscape) dp(40) else dp(48)
            setOnClickListener { showMomentInfo() }
        }
        body.addFull(caption)
        strip = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StoryPhotoActivity, RecyclerView.HORIZONTAL, false)
            setPadding(dp(20), dp(4), dp(20), dp(8)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Thumb>() {
                override fun getItemCount() = story.moments.size
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Thumb(ImageView(this@StoryPhotoActivity).apply {
                    layoutParams = RecyclerView.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(6) }
                    scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                })
                override fun onBindViewHolder(holder: Thumb, position: Int) {
                    val moment = story.moments[position]
                    holder.image.background = shape(if (position == index) accent else cardColor, 9)
                    holder.image.setPadding(dp(3), dp(3), dp(3), dp(3)); holder.image.alpha = if (position == index) 1f else .58f
                    holder.image.contentDescription = "第 ${position + 1} ${if (moment.video) "段视频" else "张照片"}"
                    Glide.with(holder.image).load(Uri.parse(moment.uri)).override(160, 160).centerCrop().into(holder.image)
                    holder.image.setOnClickListener { jump(holder.bindingAdapterPosition) }
                }
            }
        }
        if (!landscape) body.addFull(strip, 74)
        pager.setCurrentItem(index, false); updateInfo(); updatePlayButton()
        if (autoplay) scheduleCurrent()
    }

    private class Thumb(val image: ImageView) : RecyclerView.ViewHolder(image)

    private fun jump(value: Int) {
        if (value !in story.moments.indices) return
        pauseAutoplay()
        pager.setCurrentItem(value, !MemoryPaper.reducedMotion(this) && kotlin.math.abs(value - index) == 1)
    }

    private fun updateInfo() {
        counter.text = "${index + 1} / ${story.moments.size}"
        val moment = story.moments[index]
        caption.text = (if (moment.video) "视频 · " else "") + StoryMedia.dateLabel(moment) + if (moment.caption.isBlank()) "" else "\n${moment.caption}"
        if (::strip.isInitialized) { strip.adapter?.notifyDataSetChanged(); strip.scrollToPosition(index) }
    }

    private fun startAutoplay() { autoplay = true; updatePlayButton(); scheduleCurrent() }

    private fun pauseAutoplay() {
        timer.removeCallbacks(advance)
        autoplay = false
        videos.keys.toList().forEach { stopVideo(it) }
        updatePlayButton()
    }

    private fun updatePlayButton() {
        if (::playButton.isInitialized) {
            playButton.text = if (autoplay) "暂停播放" else "自动播放"
            playButton.contentDescription = if (autoplay) "暂停自动播放" else "开始自动播放"
        }
    }

    private fun scheduleCurrent() {
        timer.removeCallbacks(advance)
        if (!autoplay) return
        if (story.moments[index].video) playVideo(index, true)
        else timer.postDelayed(advance, story.intervalSeconds.coerceIn(1, 60) * 1000L)
    }

    private fun advanceFromCurrent() {
        if (!autoplay) return
        when {
            index < story.moments.lastIndex -> pager.setCurrentItem(index + 1, !MemoryPaper.reducedMotion(this))
            story.loop -> pager.setCurrentItem(0, !MemoryPaper.reducedMotion(this))
            else -> pauseAutoplay()
        }
    }

    private fun playVideo(position: Int, automatic: Boolean) {
        val frame = pages[position] ?: return
        if (position != index || videos.containsKey(position)) return
        val video = VideoView(this)
        frame.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); videos[position] = video
        if (!automatic) {
            val control = MediaController(this); control.setAnchorView(video); video.setMediaController(control)
        }
        video.setVideoURI(Uri.parse(story.moments[position].uri))
        video.setOnPreparedListener { player ->
            if (!story.originalSound) player.setVolume(0f, 0f)
            if (position == index && hasWindowFocus()) video.start()
        }
        video.setOnCompletionListener { stopVideo(position); if (automatic) advanceFromCurrent() }
        video.setOnErrorListener { _, _, _ -> stopVideo(position); pauseAutoplay(); message("视频无法播放，请检查文件是否完整"); true }
    }

    private fun stopVideo(position: Int) {
        videos.remove(position)?.let { video -> video.stopPlayback(); (video.parent as? ViewGroup)?.removeView(video) }
    }

    private fun showMomentInfo() {
        val moment = story.moments[index]
        MemoryDialogBuilder(this).setTitle("这一刻").setMessage(StoryMedia.dateLabel(moment) + if (moment.caption.isBlank()) "" else "\n\n${moment.caption}")
            .setPositiveButton("关闭", null).show()
    }

    private fun storyActions() {
        MemoryChrome.sheet(this, story.title, story.dateLabel(), listOf(
            MemoryChrome.Action("故事介绍", story.description.ifBlank { "还没有填写故事文字" }) {
                MemoryDialogBuilder(this).setTitle(story.title).setMessage(story.dateLabel() + "\n\n" + story.description.ifBlank { "还没有填写故事文字，可在编辑中补充。" }).setPositiveButton("关闭", null).show()
            },
            MemoryChrome.Action(if (story.favorite) "取消收藏" else "收藏故事") { story.favorite = !story.favorite; safely { store.save(story) } },
            MemoryChrome.Action(if (story.pinned) "取消置顶" else "置顶故事") { story.pinned = !story.pinned; safely { store.save(story) } }
        ))
    }

    override fun onPause() { pauseAutoplay(); super.onPause() }

    override fun onDestroy() {
        timer.removeCallbacksAndMessages(null)
        videos.keys.toList().forEach { stopVideo(it) }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("index", index); outState.putBoolean("autoplay", autoplay)
        super.onSaveInstanceState(outState)
    }
}
