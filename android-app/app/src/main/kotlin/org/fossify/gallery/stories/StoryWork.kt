package org.fossify.shiguang.stories

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/** Retains a single in-flight operation across Activity recreation. UI callbacks stay in the Activity. */
class StoryWork : ViewModel() {
    data class State(val title: String, val message: String, val result: Result<Any?>? = null)
    val state = MutableLiveData<State?>(null)
    private val main = Handler(Looper.getMainLooper())
    private var worker: Thread? = null
    @Volatile private var cleared = false

    fun start(title: String, work: ((String) -> Unit) -> Any?) {
        check(state.value == null) { "上一项操作还未完成" }
        state.value = State(title, "准备中…")
        worker = Thread {
            val result = runCatching { work { text -> main.post { if (!cleared) state.value = State(title, text) } } }
            main.post { if (!cleared) state.value = State(title, "", result) }
        }.apply { start() }
    }

    fun consume() { state.value = null; worker = null }
    override fun onCleared() { cleared = true; worker?.interrupt(); main.removeCallbacksAndMessages(null) }
}

data class StoryImportResult(val added: Int, val duplicates: Int, val failures: List<String>)
