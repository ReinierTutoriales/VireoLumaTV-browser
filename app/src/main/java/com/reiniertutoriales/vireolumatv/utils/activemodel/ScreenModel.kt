package com.reiniertutoriales.vireolumatv.utils.activemodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * State shared by every screen or service that asks [ScreenModels] for it, kept until
 * its last user lets go. [workScope] runs on the main thread and is cancelled at that point.
 */
abstract class ScreenModel {
    private var scope: CoroutineScope? = null

    val workScope: CoroutineScope
        get() = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope = it }

    fun clear() {
        scope?.cancel()
        onClear()
    }

    /** Releases what the model holds; called once, after its scope is cancelled. */
    open fun onClear() {}
}
