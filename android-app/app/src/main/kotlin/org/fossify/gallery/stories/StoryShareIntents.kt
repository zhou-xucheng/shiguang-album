package org.fossify.shiguang.stories

import android.content.ClipData
import android.content.Intent
import android.net.Uri

object StoryShareIntents {
    fun media(uris: ArrayList<Uri>, type: String, clip: ClipData): Intent {
        require(uris.isNotEmpty())
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            setType(type)
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = clip
        }
    }
}
