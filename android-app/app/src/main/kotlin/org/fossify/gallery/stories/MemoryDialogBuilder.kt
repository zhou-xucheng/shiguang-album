package org.fossify.shiguang.stories

import android.app.Activity
import android.graphics.Typeface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

/** Explicit title styling avoids the upstream gallery's fixed light title color. */
class MemoryDialogBuilder(private val activity: Activity) : MaterialAlertDialogBuilder(activity) {
    override fun setTitle(title: CharSequence?): MemoryDialogBuilder {
        setCustomTitle(TextView(context).apply {
            text = title; textSize = 20f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(activity.getProperTextColor())
            setPadding(MemoryChrome.dp(activity, 24), MemoryChrome.dp(activity, 22), MemoryChrome.dp(activity, 24), MemoryChrome.dp(activity, 12))
        })
        return this
    }
    override fun setTitle(titleId: Int): MemoryDialogBuilder = setTitle(context.getText(titleId))
    override fun create(): AlertDialog = super.create().apply {
        setOnShowListener {
            window?.setBackgroundDrawable(MemoryChrome.rounded(activity, activity.getProperBackgroundColor(), 20))
            findViewById<TextView>(android.R.id.message)?.setTextColor(activity.getProperTextColor())
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL).forEach { getButton(it)?.setTextColor(activity.getProperPrimaryColor()) }
        }
    }
}
