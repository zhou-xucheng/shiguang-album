package org.fossify.shiguang

import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp
import org.fossify.shiguang.extensions.config

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        runCatching { org.fossify.shiguang.stories.StoryMedia.cleanupInterrupted(this) }
        val preferences = getSharedPreferences("memory_defaults", MODE_PRIVATE)
        if (!preferences.getBoolean("initialized", false)) {
            config.isSystemThemeEnabled = false
            config.primaryColor = android.graphics.Color.rgb(47, 100, 85)
            config.backgroundColor = android.graphics.Color.rgb(249, 247, 242)
            config.textColor = android.graphics.Color.rgb(35, 49, 44)
            preferences.edit().putBoolean("initialized", true).apply()
        }
        if (!preferences.getBoolean("editorial_palette", false)) {
            if (config.primaryColor in intArrayOf(0xFF2F6455.toInt(), 0xFF106D1F.toInt())) config.primaryColor = 0xFF8A593F.toInt()
            if (config.backgroundColor == 0xFFF9F7F2.toInt()) config.backgroundColor = 0xFFF8F5EF.toInt()
            if (config.textColor == 0xFF23312C.toInt()) config.textColor = 0xFF342E29.toInt()
            preferences.edit().putBoolean("editorial_palette", true).apply()
        }
        if (!preferences.getBoolean("gallery_refinement_v3", false)) {
            config.folderStyle = org.fossify.shiguang.helpers.FOLDER_STYLE_ROUNDED_CORNERS
            config.fileRoundedCorners = true
            if (config.thumbnailSpacing <= 1) config.thumbnailSpacing = (6 * resources.displayMetrics.density).toInt()
            preferences.edit().putBoolean("gallery_refinement_v3", true).apply()
        }
        if (!preferences.getBoolean("compact_palette_v1", false)) {
            // Update only the former default palette. Other themes and custom colors are retained.
            if (config.backgroundColor == 0xFFF8F5EF.toInt() && config.primaryColor == 0xFF8A593F.toInt() && config.textColor == 0xFF342E29.toInt()) {
                config.backgroundColor = 0xFFF7F8FA.toInt(); config.textColor = 0xFF252D34.toInt(); config.primaryColor = 0xFF355B77.toInt()
            }
            preferences.edit().putBoolean("compact_palette_v1", true).apply()
        }
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
