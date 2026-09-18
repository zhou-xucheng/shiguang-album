package org.fossify.shiguang.stories

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.fossify.commons.extensions.*
import org.fossify.shiguang.R
import org.fossify.shiguang.activities.MainActivity
import org.fossify.shiguang.activities.MediaActivity
import org.fossify.shiguang.extensions.config
import org.fossify.shiguang.helpers.DIRECTORY

/** Shared navigation and surfaces for the gallery and story screens. */
object MemoryChrome {
    data class Action(val title: String, val detail: String = "", val run: () -> Unit)
    fun dp(a: Activity, n: Int) = (n * a.resources.displayMetrics.density).toInt()
    fun rounded(a: Activity, color: Int, radius: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(a, radius).toFloat()
    }
    fun text(a: Activity, value: String, size: Float = 16f) = TextView(a).apply {
        text = value; textSize = size; setTextColor(a.getProperTextColor())
    }
    fun navigate(a: Activity, target: Class<*>) {
        if (a.javaClass == target) return
        // Enter the current gallery view directly instead of flashing the folder activity first.
        val actualTarget = if (target == MainActivity::class.java && a.config.showAll) MediaActivity::class.java else target
        if (a.javaClass == actualTarget && a.intent.getStringExtra(DIRECTORY).isNullOrEmpty()) return
        val intent = Intent(a, actualTarget).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (actualTarget == MediaActivity::class.java) intent.putExtra(DIRECTORY, "")
        a.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(a, 0, 0).toBundle())
        if (a !is StoriesActivity) a.finish()
        @Suppress("DEPRECATION")
        a.overridePendingTransition(0, 0)
    }
    fun navigation(a: Activity, selected: Int): LinearLayout {
        val paper = a.getProperBackgroundColor()
        val accent = a.getProperPrimaryColor()
        val ink = a.getProperTextColor()
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "memory-navigation"
            setBackgroundColor(paper)
            setPadding(dp(a, 16), dp(a, 6), dp(a, 16), dp(a, 6))
            val entries = listOf(Triple("故事", R.drawable.ic_memory_stories, StoriesActivity::class.java), Triple("相册", R.drawable.ic_memory_library, MainActivity::class.java), Triple("设置", R.drawable.ic_memory_settings, MemorySettingsActivity::class.java))
            entries.forEachIndexed { index, entry ->
                val active = index == selected
                val tint = if (active && ColorUtils.calculateContrast(accent, paper) >= 4.5) accent else ink
                val tab = LinearLayout(a).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; minimumHeight = dp(a, 62)
                    contentDescription = entry.first; isSelected = active; isFocusable = true
                    setOnClickListener { navigate(a, entry.third) }
                    addView(ImageView(a).apply {
                        setImageResource(entry.second); imageTintList = ColorStateList.valueOf(if (active) tint else ColorUtils.blendARGB(ink, paper, .22f))
                        setPadding(dp(a, 17), dp(a, 4), dp(a, 17), dp(a, 4))
                        if (active) background = rounded(a, ColorUtils.blendARGB(paper, accent, .11f), 14)
                    }, LinearLayout.LayoutParams(dp(a, 58), dp(a, 30)))
                    addView(text(a, entry.first, 12f).apply { gravity = Gravity.CENTER; setTextColor(tint); setPadding(0, dp(a, 4), 0, 0); if (active) typeface = Typeface.DEFAULT_BOLD })
                }
                addView(tab, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(a, 3), 0, dp(a, 3), 0) })
            }
        }
    }
    fun wrapGallery(a: Activity, content: View): View {
        val root = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(a.getProperBackgroundColor()) }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = navigation(a, 1)
        root.addView(nav)
        ViewCompat.setOnApplyWindowInsetsListener(nav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(a, 16) + bars.left, dp(a, 6), dp(a, 16) + bars.right, dp(a, 6) + bars.bottom); insets
        }
        return root
    }
    fun sheet(a: Activity, title: String, subtitle: String = "", actions: List<Action>) {
        val dialog = BottomSheetDialog(a)
        val paper = a.getProperBackgroundColor()
        val content = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(a, paper, 24)
            setPadding(dp(a, 24), dp(a, 20), dp(a, 24), dp(a, 24))
        }
        content.addView(View(a).apply { background = rounded(a, ColorUtils.blendARGB(paper, a.getProperTextColor(), .20f), 2) }, LinearLayout.LayoutParams(dp(a, 32), dp(a, 4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(a, 20) })
        content.addView(text(a, title, 22f).apply { typeface = Typeface.DEFAULT_BOLD })
        if (subtitle.isNotBlank()) content.addView(text(a, subtitle, 14f).apply { setPadding(0, dp(a, 8), 0, dp(a, 12)) })
        actions.forEach { action ->
            content.addView(LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL; minimumHeight = dp(a, 64); gravity = Gravity.CENTER_VERTICAL
                background = rounded(a, ColorUtils.blendARGB(paper, a.getProperTextColor(), .035f), 14)
                setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14)); isFocusable = true
                addView(text(a, action.title, 16f).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) })
                if (action.detail.isNotBlank()) addView(text(a, action.detail, 13f).apply { setPadding(0, dp(a, 4), 0, 0); setTextColor(ColorUtils.blendARGB(a.getProperTextColor(), paper, .25f)) })
                setOnClickListener { dialog.dismiss(); action.run() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(a, 8) })
        }
        val scroll = ScrollView(a).apply { addView(content) }
        dialog.setContentView(scroll)
        dialog.setOnShowListener {
            (scroll.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.behavior.peekHeight = a.resources.displayMetrics.heightPixels
        }
        dialog.show()
    }
    private val galleryIds = setOf(R.id.open_stories, R.id.open_camera, R.id.show_all, R.id.sort, R.id.filter, R.id.change_view_type, R.id.temporarily_show_hidden, R.id.stop_showing_hidden, R.id.temporarily_show_excluded, R.id.stop_showing_excluded, R.id.set_as_default_folder, R.id.unset_as_default_folder, R.id.create_new_folder, R.id.open_recycle_bin, R.id.column_count, R.id.settings, R.id.about, R.id.more_apps_from_us, R.id.toggle_filename, R.id.folder_view, R.id.empty_recycle_bin, R.id.empty_disable_recycle_bin, R.id.restore_all_files, R.id.group, R.id.slideshow)
    fun simplifyMenu(a: Activity, toolbar: Toolbar) {
        toolbar.popupTheme = if (ColorUtils.calculateLuminance(a.getProperBackgroundColor()) > .5) R.style.MemoryLightPopup else R.style.MemoryDarkPopup
        galleryIds.forEach { toolbar.menu.findItem(it)?.isVisible = false }
    }
    fun gallerySheet(a: Activity, toolbar: Toolbar, appearance: Boolean, folder: Boolean, recycle: Boolean = false, hidden: Boolean = false, excluded: Boolean = false) {
        fun item(id: Int, title: String, detail: String = "") = Action(title, detail) { toolbar.menu.performIdentifierAction(id, 0) }
        val actions = if (appearance) buildList {
            add(item(R.id.sort, "排列顺序", "按时间、名称或大小排列"))
            add(item(R.id.filter, "显示内容", "选择照片、视频等类型"))
            add(item(R.id.change_view_type, "浏览布局", "网格或列表"))
            add(item(R.id.column_count, "缩略图大小", "调整每行显示的数量"))
            if (!folder) { add(item(R.id.group, "日期分组")); add(item(R.id.toggle_filename, "显示 / 隐藏文件名")) }
        } else buildList {
            if (!recycle) {
                add(item(if (folder) R.id.show_all else R.id.folder_view, if (folder) "查看全部照片" else "查看相册文件夹"))
                if (folder) add(item(R.id.create_new_folder, "新建相册文件夹"))
                add(Action("回收站", "查看已删除的故事、照片与视频") { a.startActivity(Intent(a, MemoryTrashActivity::class.java)) })
            } else { add(item(R.id.restore_all_files, "恢复全部照片与视频")); add(item(R.id.empty_recycle_bin, "清空照片回收站")) }
            add(Action("高级选项", "隐藏内容、默认文件夹等") {
                sheet(a, "高级选项", "这些设置只影响当前相册的浏览方式", buildList {
                    add(item(if (hidden) R.id.stop_showing_hidden else R.id.temporarily_show_hidden, if (hidden) "停止显示隐藏内容" else "临时显示隐藏内容"))
                    if (folder) add(item(if (excluded) R.id.stop_showing_excluded else R.id.temporarily_show_excluded, if (excluded) "停止显示排除文件夹" else "临时显示排除文件夹"))
                    add(item(R.id.set_as_default_folder, if (folder) "清除默认文件夹" else "将当前文件夹设为默认"))
                    if (!folder) add(item(R.id.unset_as_default_folder, "取消默认文件夹"))
                    add(item(R.id.open_camera, "打开相机"))
                })
            })
        }
        sheet(a, if (appearance) "显示方式" else "相册管理", if (appearance) "按你习惯的方式浏览" else "整理内容与恢复删除的项目", actions)
    }
}
