package org.fossify.shiguang.stories

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import org.fossify.shiguang.activities.SettingsActivity
import org.fossify.shiguang.extensions.config
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import org.fossify.shiguang.R

class MemorySettingsActivity : StoryActivity() {
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val content = page("设置", "", back = false, footer = MemoryChrome.navigation(this, 2))
        content.addFull(sectionHeading("安心收藏，慢慢回看。", "熟悉的颜色，妥帖保存的回忆。"))
        content.addFull(eyebrow("浏览习惯")); content.space(10)
        content.addFull(panel().apply {
            addFull(feature(R.drawable.ic_memory_settings, "外观", "选择喜欢的配色与相册显示") { startActivity(Intent(this@MemorySettingsActivity, MemoryAppearanceActivity::class.java)) })
        }); content.space(24)
        content.addFull(eyebrow("回忆保管")); content.space(10)
        content.addFull(panel().apply {
            addFull(feature(R.drawable.ic_memory_trash, "回收站", "找回删除的故事、照片与视频") { startActivity(Intent(this@MemorySettingsActivity, MemoryTrashActivity::class.java)) })
        })
    }
    private fun feature(icon: Int, title: String, detail: String, action: () -> Unit) = row().apply {
        addView(ImageView(this@MemorySettingsActivity).apply {
            setImageResource(icon); imageTintList = ColorStateList.valueOf(accentText)
            background = shape(ColorUtils.blendARGB(paper, accent, .08f), 13)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(4) })
        addView(settingRow(title, detail, action), LinearLayout.LayoutParams(0, -2, 1f))
        setOnClickListener { action() }; isFocusable = true
    }
}

class MemoryAppearanceActivity : StoryActivity() {
    override fun onResume() { super.onResume(); render() }
    private fun render() {
        val content = page("外观", "")
        content.addFull(sectionHeading("选一种舒服的颜色", "页面、菜单和弹窗会一起换上新颜色。"))
        listOf(
            theme("清透", "浅灰白与静蓝", 0xFFF7F8FA.toInt(), 0xFF252D34.toInt(), 0xFF355B77.toInt()),
            theme("暖纸", "米白与暖棕", 0xFFF8F5EF.toInt(), 0xFF342E29.toInt(), 0xFF8A593F.toInt()),
            theme("青苔", "浅绿与森林绿", 0xFFF2F6F0.toInt(), 0xFF243B30.toInt(), 0xFF39654D.toInt()),
            theme("雾蓝", "浅灰蓝与海蓝", 0xFFF2F5F9.toInt(), 0xFF26374D.toInt(), 0xFF466687.toInt()),
            theme("暖夜", "柔和深棕，适合夜间", 0xFF25221F.toInt(), 0xFFF3ECE3.toInt(), 0xFFD9AD8C.toInt())
        ).forEach { content.addFull(it); content.space(12) }
        content.space(10)
        val design = getSharedPreferences("memory-design", MODE_PRIVATE)
        content.addFull(panel().apply {
            fun option(title: String, key: String, default: Boolean) {
                addFull(androidx.appcompat.widget.SwitchCompat(this@MemoryAppearanceActivity).apply {
                    text = title; textSize = 16f; setTextColor(ink); minimumHeight = dp(56)
                    isChecked = design.getBoolean(key, default)
                    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                    thumbTintList = ColorStateList(states, intArrayOf(accent, ColorUtils.blendARGB(paper, ink, .45f)))
                    trackTintList = ColorStateList(states, intArrayOf(ColorUtils.blendARGB(paper, accent, .35f), ColorUtils.blendARGB(paper, ink, .18f)))
                    setOnCheckedChangeListener { _, checked -> design.edit().putBoolean(key,checked).apply(); render() }
                })
            }
            option("轻微纸张纹理", "paper", false)
            option("减少浏览动效", "reduce_motion", false)
            addFull(label("纹理只用于页面背景，不改变照片。",13f,muted))
        }); content.space(16)
        content.addFull(panel().apply { addFull(settingRow("更多相册偏好", "字体、缩略图与图片编辑设置") { startActivity(Intent(this@MemoryAppearanceActivity, SettingsActivity::class.java)) }) })
    }
    private fun theme(name: String, detail: String, backgroundColor: Int, textColor: Int, primaryColor: Int) = row().apply {
        val active = paper == backgroundColor && accent == primaryColor
        background = shape(surfaceColor, 20).apply { setStroke(dp(1), if (active) accent else lineColor) }
        setPadding(dp(16), dp(17), dp(16), dp(17)); minimumHeight = dp(88)
        addView(label("拾", 23f, textColor).apply {
            typeface = Typeface.create("serif", Typeface.NORMAL); gravity = Gravity.CENTER
            background = shape(backgroundColor, 13).apply { setStroke(dp(2), primaryColor) }
        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(8) })
        val copy = column().apply { addFull(label(name, 17f, bold = true)); space(5); addFull(label(detail, 13f, muted)) }
        addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        addView(label(if (active) "✓" else "", 21f, accentText))
        contentDescription = "$name，$detail${if (active) "，已选中" else ""}"; isSelected = active; isFocusable = true
        setOnClickListener { config.isSystemThemeEnabled = false; config.backgroundColor = backgroundColor; config.textColor = textColor; config.primaryColor = primaryColor; recreate() }
    }
}
