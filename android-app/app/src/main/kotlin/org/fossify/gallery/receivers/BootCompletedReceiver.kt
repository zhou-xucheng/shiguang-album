package org.fossify.shiguang.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.shiguang.extensions.updateDirectoryPath
import org.fossify.shiguang.helpers.MediaFetcher

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ensureBackgroundThread {
            MediaFetcher(context).getFoldersToScan().forEach {
                context.updateDirectoryPath(it)
            }
        }
    }
}
