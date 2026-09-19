package org.fossify.shiguang.stories

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.alexvasilkov.gestures.GestureImageView
import com.alexvasilkov.gestures.GestureController
import com.alexvasilkov.gestures.State
import com.bumptech.glide.Glide

/** One index for photos and videos, independent of automatic slideshow playback. */
class StoryPhotoActivity : StoryActivity() {
    private lateinit var story: Story
    private lateinit var pager: ViewPager
    private lateinit var counter: Button
    private lateinit var caption: TextView
    private lateinit var strip: RecyclerView
    private val pages = mutableMapOf<Int, FrameLayout>()
    private val videos = mutableMapOf<Int, VideoView>()
    private var index = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!safely { story = store.get(intent.getStringExtra("story_id") ?: "") ?: error("故事不存在"); require(story.moments.isNotEmpty()) }) { finish(); return }
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("start_index", 0)).coerceIn(story.moments.indices)
        render()
    }
    private fun render() {
        val controls = row()
        controls.addView(button("上一张") { jump(index - 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("全部照片") { overview(story, index) { jump(it) } }, LinearLayout.LayoutParams(0, -2, 1.2f))
        controls.addView(button("下一张") { jump(index + 1) }, LinearLayout.LayoutParams(0, -2, 1f))
        val body = fixedPage(story.title, controls)
        counter = button("") { jumpDialog() }.apply { strokeWidth = 0; contentDescription = "跳转到指定照片" }
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
                    frame.addView(button("▷ 播放视频", true) { playVideo(position) }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
                    frame.contentDescription = "第 ${position + 1} 段视频"
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
                    image.setOnTouchListener { _, e ->
                        image.parent.requestDisallowInterceptTouchEvent(e.pointerCount > 1 || image.controller.state.zoom > baseZoom + .01f)
                        false
                    }
                    Glide.with(this@StoryPhotoActivity).load(Uri.parse(moment.uri)).error(android.R.drawable.ic_menu_report_image).into(image)
                    frame.addView(image, FrameLayout.LayoutParams(-1, -1))
                }
                container.addView(frame); pages[position] = frame; return frame
            }
            override fun destroyItem(container: ViewGroup, position: Int, item: Any) { stopVideo(position); pages.remove(position); container.removeView(item as View) }
        }
        if (!MemoryPaper.reducedMotion(this)) pager.setPageTransformer(false) { view, position -> view.alpha = 1f - kotlin.math.abs(position).coerceAtMost(1f) * .2f }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                videos.keys.toList().forEach { stopVideo(it) }
                pages.values.forEach { (it.getChildAt(0) as? GestureImageView)?.controller?.resetState() }
                index = position; updateInfo()
            }
            override fun onPageScrollStateChanged(state: Int) { if (state == ViewPager.SCROLL_STATE_DRAGGING) videos.values.forEach { it.pause() } }
        })
        body.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        caption = label("", 14f, muted).apply {
            setPadding(dp(24), dp(8), dp(24), dp(8)); maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END; minimumHeight = dp(48)
            setOnClickListener { MemoryDialogBuilder(this@StoryPhotoActivity).setTitle("这一刻").setMessage(StoryMedia.dateLabel(story.moments[index]) + "\n\n" + story.moments[index].caption).setPositiveButton("关闭", null).show() }
        }
        body.addFull(caption)
        strip = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@StoryPhotoActivity, RecyclerView.HORIZONTAL, false)
            setPadding(dp(20), dp(4), dp(20), dp(8)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Thumb>() {
                override fun getItemCount() = story.moments.size
                override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Thumb(ImageView(this@StoryPhotoActivity).apply {
                    layoutParams = RecyclerView.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(6) }; scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true
                })
                override fun onBindViewHolder(holder: Thumb, position: Int) {
                    val m = story.moments[position]
                    holder.image.background = shape(if (position == index) accent else cardColor, 10)
                    holder.image.setPadding(dp(3), dp(3), dp(3), dp(3)); holder.image.alpha = if (position == index) 1f else .65f
                    holder.image.contentDescription = "跳到第 ${position + 1} ${if (m.video) "段视频" else "张照片"}"
                    Glide.with(holder.image).load(Uri.parse(m.uri)).override(180, 180).centerCrop().into(holder.image)
                    holder.image.setOnClickListener { jump(holder.bindingAdapterPosition) }
                }
            }
        }
        body.addFull(strip, 80); pager.setCurrentItem(index, false); updateInfo()
    }
    private class Thumb(val image: ImageView) : RecyclerView.ViewHolder(image)
    private fun jump(value: Int) { if (value in story.moments.indices) pager.setCurrentItem(value, !MemoryPaper.reducedMotion(this) && kotlin.math.abs(value - index) == 1) }
    private fun updateInfo() {
        counter.text = "${index + 1} / ${story.moments.size}  ·  点击跳转"
        val m = story.moments[index]
        caption.text = (if (m.video) "视频 · " else "") + StoryMedia.dateLabel(m) + if (m.caption.isBlank()) "" else "\n${m.caption}"
        if (::strip.isInitialized) { strip.adapter?.notifyDataSetChanged(); strip.scrollToPosition(index) }
    }
    private fun jumpDialog() {
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "输入 1—${story.moments.size}" }
        val dialog = MemoryDialogBuilder(this).setTitle("跳到哪一张？").setView(input).setNegativeButton("取消", null).setPositiveButton("前往", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            val value = input.text.toString().toIntOrNull()
            if (value == null || value !in 1..story.moments.size) input.error = "请输入 1—${story.moments.size}"
            else { dialog.dismiss(); jump(value - 1) }
        } }; dialog.show()
    }
    private fun playVideo(position: Int) {
        val frame = pages[position] ?: return
        if (position != index || videos.containsKey(position)) return
        val video = VideoView(this); frame.addView(video, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); videos[position] = video
        val control = MediaController(this); control.setAnchorView(video); video.setMediaController(control)
        video.setVideoURI(Uri.parse(story.moments[position].uri))
        video.setOnPreparedListener { if (position == index && hasWindowFocus()) video.start() }
        video.setOnCompletionListener { stopVideo(position) }
        video.setOnErrorListener { _, _, _ -> stopVideo(position); message("视频无法播放，请检查文件是否完整"); true }
    }
    private fun stopVideo(position: Int) { videos.remove(position)?.let { it.stopPlayback(); (it.parent as? ViewGroup)?.removeView(it) } }
    override fun onPause() { videos.keys.toList().forEach { stopVideo(it) }; super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putInt("index", index); super.onSaveInstanceState(outState) }
}
