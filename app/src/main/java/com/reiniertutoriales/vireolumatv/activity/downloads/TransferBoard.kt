package com.reiniertutoriales.vireolumatv.activity.downloads

import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.Transfer
import com.reiniertutoriales.vireolumatv.utils.activemodel.ScreenModel
import com.reiniertutoriales.vireolumatv.utils.observable.LiveList
import com.reiniertutoriales.vireolumatv.utils.observable.LiveValue

/**
 * What the download service is doing in this process, read and written on the main thread.
 * [transfers] lists the tasks that have not ended; [changed] carries every download whose state
 * moved (progress, success or failure), so a screen redraws that one row.
 */
class TransferBoard : ScreenModel() {
    val transfers = LiveList<Transfer>()
    val changed = LiveValue<Download?>(null)

    /** Stops the task working on [download]; the task itself removes its partial output. */
    fun cancel(download: Download) {
        transfers.firstOrNull { it.job.matches(download) }?.job?.cancelled = true
    }

    fun publish(task: Transfer) {
        changed.value = task.job
    }

    fun end(task: Transfer) {
        transfers.remove(task)
        publish(task)
    }
}
