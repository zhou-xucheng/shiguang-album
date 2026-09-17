package org.fossify.shiguang.activities

import android.os.Bundle
import org.fossify.shiguang.helpers.ColorModeHelper

class PhotoActivity : PhotoVideoActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        mIsVideo = false
        super.onCreate(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        ColorModeHelper.resetColorMode(this)
    }
}
