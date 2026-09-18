package org.fossify.shiguang.stories

import android.Manifest
import android.content.ClipData
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/** Local photo library; selecting media never opens the system file browser. */
class StoryMediaPickerActivity : StoryActivity() {
    private data class Item(val uri: Uri, val album: String, val name: String, val video: Boolean, val duration: Long, val date: Long)
    private var items = emptyList<Item>()
    private var visible = emptyList<Item>()
    private val selected = linkedSetOf<String>()
    private var kind = 0
    private var album: String? = null
    private var loading = false
    private var generation = 0
    private var permissionAsked = false
    private lateinit var grid: RecyclerView
    private lateinit var confirm: com.google.android.material.button.MaterialButton
    private lateinit var empty: TextView
    private val maxSelection = 120

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(savedInstanceState?.getStringArrayList("selected").orEmpty())
        kind = savedInstanceState?.getInt("kind") ?: 0
        album = savedInstanceState?.getString("album")
        permissionAsked = savedInstanceState?.getBoolean("permissionAsked") ?: false
        render()
        if (!hasMediaAccess() && !permissionAsked) askPermission()
    }

    override fun onResume() { super.onResume(); if (::grid.isInitialized) loadMedia() }
    override fun onDestroy() { generation++; super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putStringArrayList("selected", ArrayList(selected)); out.putInt("kind", kind)
        out.putString("album", album); out.putBoolean("permissionAsked", permissionAsked)
        super.onSaveInstanceState(out)
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun hasMediaAccess() = if (Build.VERSION.SDK_INT >= 33) {
        granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) ||
            (Build.VERSION.SDK_INT >= 34 && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    } else granted(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun askPermission() {
        permissionAsked = true
        val permissions = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(permissions, 901)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 901) { render(); loadMedia() }
    }

    private fun render() {
        confirm = button("添加到故事", true) { completeSelection() }
        val content = page("选择照片与视频", "轻点选中，可跨相册选择；数字表示添加顺序。", footer = confirm)
        val filters = row()
        listOf("全部", "照片", "视频").forEachIndexed { index, title ->
            filters.addView(button(title, kind == index) { kind = index; render() }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addFull(filters)
        content.addFull(button((album ?: "全部相册") + " ▾") {
            val albums = items.map { it.album }.distinct().sorted()
            MemoryDialogBuilder(this).setTitle("选择相册").setItems((listOf("全部相册") + albums).toTypedArray()) { _, index ->
                album = if (index == 0) null else albums[index - 1]; render()
            }.show()
        })
        if (!hasMediaAccess()) {
            content.addFull(label("允许访问照片后，就能在这里选择手机相册里的照片与视频。", 15f, muted))
            content.addFull(button("允许访问照片") { askPermission() })
            content.addFull(button("打开权限设置") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            })
        } else if (Build.VERSION.SDK_INT >= 34 && !(granted(Manifest.permission.READ_MEDIA_IMAGES) && granted(Manifest.permission.READ_MEDIA_VIDEO))) {
            content.addFull(button("选择更多可访问的照片") { askPermission() })
        }
        empty = label("正在读取相册…", 15f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(24)) }
        content.addFull(empty)
        // A RecyclerView owns the scrolling area so a large library does not allocate every thumbnail.
        val scroll = pageScroll!!
        val root = scroll.parent as LinearLayout
        scroll.removeView(content); root.removeView(scroll)
        content.setPadding(dp(20), 0, dp(20), dp(8))
        root.addView(content, 1, LinearLayout.LayoutParams(-1, -2))
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@StoryMediaPickerActivity, if (resources.configuration.fontScale > 1.3f) 2 else 3)
            setPadding(dp(16), 0, dp(16), dp(8)); clipToPadding = false
            adapter = object : RecyclerView.Adapter<Holder>() {
                override fun getItemCount() = visible.size
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                    val frame = FrameLayout(this@StoryMediaPickerActivity).apply {
                        layoutParams = RecyclerView.LayoutParams(-1, dp(128)).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
                        background = shape(cardColor, 12); clipToOutline = true; isFocusable = true
                    }
                    val image = ImageView(this@StoryMediaPickerActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                    frame.addView(image, FrameLayout.LayoutParams(-1, -1))
                    val number = label("", 14f, android.graphics.Color.WHITE, true).apply { gravity = Gravity.CENTER }
                    frame.addView(number, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
                    val video = label("", 12f, android.graphics.Color.WHITE).apply { setPadding(dp(6), dp(3), dp(6), dp(3)); background = shape(0xB3000000.toInt(), 6) }
                    frame.addView(video, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
                    return Holder(frame, image, number, video)
                }
                override fun onBindViewHolder(holder: Holder, position: Int) {
                    val item = visible[position]
                    val order = selected.indexOf(item.uri.toString())
                    holder.frame.isSelected = order >= 0
                    holder.frame.contentDescription = "${if (item.video) "视频" else "照片"}：${item.name}，${if (order >= 0) "已选 ${order + 1}" else "未选中"}"
                    Glide.with(this@StoryMediaPickerActivity).load(item.uri).centerCrop().into(holder.image)
                    holder.number.text = if (order >= 0) "${order + 1}" else "○"
                    holder.number.background = shape(if (order >= 0) accent else 0x99000000.toInt(), 16)
                    holder.number.setTextColor(if (order >= 0) contrast(accent) else android.graphics.Color.WHITE)
                    holder.video.visibility = if (item.video) View.VISIBLE else View.GONE
                    holder.video.text = "▷ ${item.duration / 60000}:${"%02d".format(item.duration / 1000 % 60)}"
                    holder.frame.setOnClickListener {
                        val uri = item.uri.toString()
                        if (!selected.remove(uri)) {
                            if (selected.size >= maxSelection) { message("一次最多选择 $maxSelection 个片段"); return@setOnClickListener }
                            selected.add(uri)
                        }
                        updateSelection(); notifyDataSetChanged()
                    }
                }
            }
        }
        root.addView(grid, 2, LinearLayout.LayoutParams(-1, 0, 1f))
        filter(); updateSelection()
    }

    private class Holder(val frame: FrameLayout, val image: ImageView, val number: TextView, val video: TextView) : RecyclerView.ViewHolder(frame)
    private fun filter() {
        visible = items.filter { (album == null || it.album == album) && (kind == 0 || (kind == 2) == it.video) }
        grid.adapter?.notifyDataSetChanged()
        empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        empty.text = when { loading -> "正在读取相册…"; !hasMediaAccess() -> "尚未获得照片访问权限"; else -> "这里还没有可选择的照片或视频" }
    }
    private fun updateSelection() {
        confirm.text = if (selected.isEmpty()) "先选择照片与视频" else "添加 ${selected.size} 个片段到故事"
        confirm.isEnabled = selected.isNotEmpty(); confirm.alpha = if (selected.isEmpty()) .55f else 1f
    }
    private fun loadMedia() {
        if (!hasMediaAccess()) { items = emptyList(); selected.clear(); filter(); updateSelection(); return }
        val token = ++generation
        loading = true; filter()
        Thread {
            val result = runCatching {
                val found = mutableListOf<Item>()
                for ((base, video) in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to false, MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true)) {
                    val columns = mutableListOf("_id", "bucket_display_name", "_display_name", "date_added")
                    if (video) columns.add("duration")
                    // Query each collection separately so partial image/video access remains usable.
                    try {
                        contentResolver.query(base, columns.toTypedArray(), if (Build.VERSION.SDK_INT >= 29) "is_pending = 0" else null, null, "date_added DESC, _id DESC")?.use { cursor ->
                            while (cursor.moveToNext()) {
                                found.add(Item(ContentUris.withAppendedId(base, cursor.getLong(0)), cursor.getString(1) ?: "其他相册", cursor.getString(2) ?: "", video, if (video) cursor.getLong(4) else 0, cursor.getLong(3)))
                            }
                        }
                    } catch (_: SecurityException) { /* The other collection may still be authorized. */ }
                }
                found.sortedByDescending { it.date }
            }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                loading = false
                result.onSuccess { media ->
                    items = media; selected.retainAll(media.map { it.uri.toString() }.toSet())
                    if (album != null && media.none { it.album == album }) album = null
                    render()
                }.onFailure { filter(); empty.text = "相册读取未完成，请返回后重试。"; empty.visibility = View.VISIBLE }
            }
        }.start()
    }
    private fun completeSelection() {
        if (selected.isEmpty()) return
        val uris = selected.map(Uri::parse)
        val clip = ClipData("故事素材", arrayOf("image/*", "video/*"), ClipData.Item(uris.first()))
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        setResult(RESULT_OK, Intent().apply { clipData = clip; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        finish()
    }
}
