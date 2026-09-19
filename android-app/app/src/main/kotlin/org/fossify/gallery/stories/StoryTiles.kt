package org.fossify.shiguang.stories

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.*

/** Recycled thumbnails shared by editing, overview and browsing. Never decodes full media into a grid. */
class StoryTiles(context: Context, private val items: List<StoryMoment>, private val selected: (StoryMoment) -> Boolean = { false },
    private val details: Boolean = false, private val click: (Int) -> Unit) : RecyclerView(context) {
    private val scale = resources.displayMetrics.density
    private fun dp(n: Int) = (n * scale).toInt()
    private val ink = context.getProperTextColor()
    private val paper = context.getProperBackgroundColor()
    private val accent = context.getProperPrimaryColor()
    var longPress: ((ViewHolder) -> Unit)? = null
    init {
        layoutManager = GridLayoutManager(context, if (resources.configuration.fontScale > 1.2f) 2 else 3)
        setPadding(dp(16), 0, dp(16), dp(12)); clipToPadding = false
        adapter = object : Adapter<Tile>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Tile {
                val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(4), dp(4), dp(4)); layoutParams = LayoutParams(-1, -2) }
                val frame = FrameLayout(context).apply {
                    background = MemoryChrome.rounded(context as android.app.Activity, paper, 12); clipToOutline = true
                }
                val photo = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                frame.addView(photo, FrameLayout.LayoutParams(-1, -1))
                val badge = TextView(context).apply {
                    textSize = 12f; setTextColor(Color.WHITE); setPadding(dp(7), dp(3), dp(7), dp(3))
                    background = MemoryChrome.rounded(context as android.app.Activity, 0xB3222222.toInt(), 6)
                }
                frame.addView(badge, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(6), dp(6), 0, 0) })
                val mark = TextView(context).apply { textSize = 12f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
                frame.addView(mark, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(6), dp(6)) })
                box.addView(frame, LinearLayout.LayoutParams(-1, dp(132)))
                val copy = TextView(context).apply { textSize = 12f; setTextColor(ink); maxLines = 2; minHeight = dp(38); setPadding(dp(2), dp(5), dp(2), 0) }
                if (details) box.addView(copy)
                frame.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    if (v.width > 0 && v.layoutParams.height != v.width) v.layoutParams = v.layoutParams.apply { height = v.width }
                }
                return Tile(box, photo, badge, mark, copy)
            }
            override fun onBindViewHolder(holder: Tile, position: Int) {
                val moment = items[position]; val active = selected(moment)
                Glide.with(holder.photo).load(Uri.parse(moment.uri)).override(360, 360).centerCrop().error(android.R.drawable.ic_menu_report_image).into(holder.photo)
                holder.badge.text = "${position + 1}" + if (moment.video) "  ▷" else ""
                holder.mark.text = if (active) "✓" else ""
                holder.mark.background = MemoryChrome.rounded(context as android.app.Activity, if (active) accent else Color.TRANSPARENT, 14)
                holder.box.background = MemoryChrome.rounded(context as android.app.Activity, if (active) ColorUtils.blendARGB(paper, accent, .18f) else Color.TRANSPARENT, 16)
                holder.copy.text = if (moment.caption.isNotBlank()) moment.caption else StoryMedia.dateLabel(moment)
                holder.box.isSelected = active; holder.box.isFocusable = true
                holder.box.contentDescription = "第 ${position + 1} ${if (moment.video) "段视频" else "张照片"}${if (active) "，已选择" else ""}"
                holder.box.setOnClickListener { holder.bindingAdapterPosition.takeIf { it != NO_POSITION }?.let(click) }
                holder.box.setOnLongClickListener { longPress?.invoke(holder); longPress != null }
            }
            override fun onViewRecycled(holder: Tile) { Glide.with(holder.photo).clear(holder.photo) }
        }
    }
    private class Tile(val box: LinearLayout, val photo: ImageView, val badge: TextView, val mark: TextView, val copy: TextView) : ViewHolder(box)
}
