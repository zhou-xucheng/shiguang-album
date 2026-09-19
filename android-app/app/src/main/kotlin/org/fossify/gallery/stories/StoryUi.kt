package org.fossify.shiguang.stories

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.widget.*
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.shiguang.activities.SimpleActivity
import org.fossify.shiguang.R

abstract class StoryActivity : SimpleActivity() {
    protected val store by lazy { StoryStore(this) }
    protected val ink get() = getProperTextColor()
    protected val paper get() = getProperBackgroundColor()
    protected val accent get() = getProperPrimaryColor()
    protected val muted get() = ColorUtils.blendARGB(ink, paper, .30f)
    protected val cardColor get() = ColorUtils.blendARGB(paper, ink, .045f)
    protected val surfaceColor get() = if (ColorUtils.calculateLuminance(paper) > .5) Color.WHITE else ColorUtils.blendARGB(paper, Color.WHITE, .055f)
    protected val lineColor get() = ColorUtils.blendARGB(paper, ink, .09f)
    protected val accentText get() = if (ColorUtils.calculateContrast(accent, paper) >= 4.5) accent else ink
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    protected fun shape(color: Int, radius: Int = 24) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    protected fun label(value: String, size: Float = 16f, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    protected fun editorial(value: String, size: Float = 30f, color: Int = ink) = label(value, size, color).apply {
        typeface = Typeface.create("serif", Typeface.NORMAL)
        setLineSpacing(dp(5).toFloat(), 1.06f)
    }

    protected fun eyebrow(value: String) = label(value, 13f, accentText, true).apply { letterSpacing = .04f }

    protected fun badge(value: String) = label(value, 12f, accentText, true).apply {
        background = shape(ColorUtils.blendARGB(paper, accent, .08f), 8)
        setPadding(dp(10), dp(5), dp(10), dp(5))
    }

    protected fun sectionHeading(title: String, subtitle: String = ""): LinearLayout = column().apply {
        addFull(editorial(title, 29f))
        if (subtitle.isNotBlank()) { space(8); addFull(label(subtitle, 14f, muted)) }
        space(24)
    }

    protected fun panel() = column().apply {
        background = shape(surfaceColor, 20).apply { setStroke(dp(1), lineColor) }
        setPadding(dp(18), dp(12), dp(18), dp(12))
    }

    protected fun LinearLayout.rule() {
        addView(View(this@StoryActivity).apply { setBackgroundColor(lineColor) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    protected fun settingRow(title: String, value: String = "", action: () -> Unit): LinearLayout = row().apply {
        minimumHeight = dp(66)
        setPadding(0, dp(10), 0, dp(10))
        val copy = column()
        copy.addFull(label(title, 16f, bold = true))
        if (value.isNotBlank()) { copy.space(4); copy.addFull(label(value, 14f, muted).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }) }
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label("›", 24f, muted))
        isFocusable = true
        setOnClickListener { action() }
    }

    protected fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    protected fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        dividerDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT); setSize(dp(8), 1) }
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }
    protected fun LinearLayout.space(height: Int = 16) = addView(View(this@StoryActivity), LinearLayout.LayoutParams(1, dp(height)))
    protected fun LinearLayout.addFull(view: View, height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (height < 0) height else dp(height)))

    protected fun button(title: String, primary: Boolean = false, action: () -> Unit) = com.google.android.material.button.MaterialButton(this).apply {
        text = title
        textSize = 15f
        isAllCaps = false
        cornerRadius = dp(16)
        elevation = 0f
        insetTop = dp(4)
        insetBottom = dp(4)
        minHeight = dp(48)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        backgroundTintList = ColorStateList.valueOf(if (primary) accent else Color.TRANSPARENT)
        if (!primary) { strokeWidth = dp(1); strokeColor = ColorStateList.valueOf(lineColor) }
        setTextColor(if (primary) contrast(accent) else ink)
        setOnClickListener { action() }
    }

    protected fun iconButton(icon: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        imageTintList = ColorStateList.valueOf(ink)
        background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(ColorUtils.blendARGB(paper, accent, .16f)), shape(Color.TRANSPARENT, 24), shape(Color.WHITE, 24))
        setPadding(dp(12), dp(12), dp(12), dp(12))
        contentDescription = description
        tooltipText = description
        setOnClickListener { action() }
    }

    protected fun confirmDateSort(story: Story, done: () -> Unit) {
        story.ensureOriginalOrder()
        MemoryDialogBuilder(this).setTitle("照片顺序").setItems(arrayOf("选择时的顺序", "拍摄时间 · 从早到晚", "拍摄时间 · 从晚到早")) { _, which ->
            val before = story.moments.map { it.id }
            if (which == 0) story.restoreOriginalOrder() else story.sortChronologically(which == 2)
            val after = story.moments.map { it.id }; done()
            com.google.android.material.snackbar.Snackbar.make(window.decorView, if (which == 0) "已恢复选择顺序" else "已排序，未知日期放在最后", com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE)
                .setAnchorView(pageFooter)
                .setAction("撤销") {
                    if (story.moments.map { it.id } == after) { story.moments.sortBy { before.indexOf(it.id) }; done() }
                    else message("顺序已再次调整，保留当前结果")
                }.show()
        }.setNegativeButton("取消", null).show()
    }

    protected fun fixedPage(title: String, footer: View? = null): LinearLayout {
        val unused = page(title, "", footer = footer)
        val scroll = pageScroll!!; val root = scroll.parent as LinearLayout
        root.removeView(scroll); pageScroll = null
        val body = column()
        root.addView(body, 1, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }

    protected fun overview(story: Story, selected: Int = 0, pick: (Int) -> Unit) {
        val dialog = MemoryDialogBuilder(this).setTitle("全部照片 · ${story.moments.size}").setNegativeButton("关闭", null).create()
        val grid = StoryTiles(this, story.moments, { it.id == story.moments.getOrNull(selected)?.id }) { index -> dialog.dismiss(); pick(index) }
        dialog.setView(grid); dialog.show()
        grid.layoutParams = grid.layoutParams.apply { height = (resources.displayMetrics.heightPixels * .62f).toInt() }
        grid.scrollToPosition(selected)
    }

    protected var pageScroll: ScrollView? = null
    private var pageFooter: View? = null
    protected open fun leavePage() { finish() }
    protected fun <T> backgroundWork(title: String, work: ((String) -> Unit) -> T, done: (T) -> Unit) {
        val progress = MemoryDialogBuilder(this).setTitle(title).setMessage("准备中…").setCancelable(false).create()
        progress.show()
        Thread {
            val result = runCatching { work { status -> runOnUiThread { if (!isDestroyed) progress.setMessage(status) } } }
            runOnUiThread {
                if (!isDestroyed) {
                    progress.dismiss()
                    result.onSuccess(done).onFailure {
                        MemoryDialogBuilder(this).setTitle("$title 未完成")
                            .setMessage(it.message ?: "请检查文件和可用空间后重试。")
                            .setPositiveButton("知道了", null).show()
                    }
                }
            }
        }.start()
    }

    protected fun page(title: String, subtitle: String, back: Boolean = true, footer: View? = null, headerAction: View? = null): LinearLayout {
        val previousScroll = pageScroll?.scrollY ?: 0
        val root = column().apply { background = MemoryPaper.background(this@StoryActivity, paper) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val header = row().apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }
        if (back) header.addView(iconButton(R.drawable.ic_memory_back, "返回") { leavePage() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        header.addView(label(title, if (back) 18f else 22f, bold = true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        headerAction?.let { header.addView(it, LinearLayout.LayoutParams(dp(44), dp(44))) }
        root.addFull(header)
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; isVerticalScrollBarEnabled = false }
        val content = column().apply { setPadding(dp(24), dp(12), dp(24), dp(28)) }
        if (subtitle.isNotBlank()) { content.addFull(label(subtitle, 14f, muted)); content.space(20) }
        scroll.addView(content)
        pageScroll = scroll
        scroll.post { scroll.scrollTo(0, previousScroll) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        pageFooter = null
        if (footer?.tag == "memory-navigation") { root.addFull(footer); pageFooter = footer }
        else if (footer != null) {
            val dock = column().apply { setBackgroundColor(surfaceColor); setPadding(dp(20), dp(8), dp(20), dp(10)) }
            dock.addFull(footer)
            root.addFull(dock)
            pageFooter = dock
        }
        setContentView(root)
        root.post { ViewCompat.requestApplyInsets(root) }
        return content
    }

    protected fun message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    protected fun safely(action: () -> Unit): Boolean = try { action(); true } catch (e: Exception) {
        android.util.Log.e("Stories", "Story operation failed", e)
        message("操作未完成，请重试。原有内容已保留。")
        false
    }

    protected fun contrast(color: Int) = if (ColorUtils.calculateLuminance(color) > .45) Color.BLACK else Color.WHITE
}
