package org.fossify.shiguang.activities

import android.content.Intent
import org.fossify.commons.activities.BaseSplashActivity
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.shiguang.extensions.config
import org.fossify.shiguang.extensions.favoritesDB
import org.fossify.shiguang.extensions.getFavoriteFromPath
import org.fossify.shiguang.extensions.mediaDB
import org.fossify.shiguang.models.Favorite

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        // check if previously selected favorite items have been properly migrated into the new Favorites table
        if (config.wereFavoritesMigrated) {
            launchActivity()
        } else {
            if (config.appRunCount == 0) {
                config.wereFavoritesMigrated = true
                launchActivity()
            } else {
                config.wereFavoritesMigrated = true
                ensureBackgroundThread {
                    val favorites = ArrayList<Favorite>()
                    val favoritePaths = mediaDB.getFavorites().map { it.path }.toMutableList() as ArrayList<String>
                    favoritePaths.forEach {
                        favorites.add(getFavoriteFromPath(it))
                    }
                    favoritesDB.insertAll(favorites)

                    runOnUiThread {
                        launchActivity()
                    }
                }
            }
        }
    }

    private fun launchActivity() {
        startActivity(Intent(this, org.fossify.shiguang.stories.StoriesActivity::class.java))
        finish()
    }
}
