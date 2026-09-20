package org.fossify.shiguang.stories

import android.content.Context

/** Shared density rules: small previews without shrinking text or touch targets. */
object StoryLayout {
    fun albumColumns(context: Context): Int = if (context.resources.configuration.fontScale > 1.2f) 2 else 3
    fun mediaColumns(context: Context, details: Boolean = false): Int {
        val config = context.resources.configuration
        if (details) return if (config.fontScale > 1.2f) 2 else 3
        val minimum = if (config.fontScale > 1.4f) 108 else if (config.fontScale > 1.2f) 92 else 72
        return ((config.screenWidthDp - 32) / minimum).coerceIn(2, 6)
    }
}
