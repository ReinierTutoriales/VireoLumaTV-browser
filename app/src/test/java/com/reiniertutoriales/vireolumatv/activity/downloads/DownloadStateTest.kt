package com.reiniertutoriales.vireolumatv.activity.downloads

import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.service.downloads.StreamTransfer
import com.reiniertutoriales.vireolumatv.service.downloads.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [28])
class DownloadStateTest {
    private fun download(size: Long, received: Long, name: String = "file.bin") = Download().apply {
        this.size = size
        bytesReceived = received
        filename = name
    }

    @Test fun stateFollowsTheStoredSizes() {
        assertEquals(DownloadState.SAVED, DownloadState.of(download(10, 10)))
        assertEquals(DownloadState.RUNNING, DownloadState.of(download(10, 4)))
        assertEquals(DownloadState.RUNNING, DownloadState.of(download(-1, 4)))
        assertEquals(DownloadState.FAILED, DownloadState.of(download(Download.SIZE_FAILED, 4)))
        assertEquals(DownloadState.CANCELLED, DownloadState.of(download(Download.SIZE_CANCELLED, 0)))
    }

    @Test fun aRunningDownloadOffersOnlyCancel() {
        assertEquals(listOf(DownloadAction.CANCEL), DownloadAction.available(download(10, 4, "app.apk")))
    }

    @Test fun onlyASavedApkOffersInstall() {
        assertEquals(listOf(DownloadAction.INSTALL, DownloadAction.SHOW_FOLDER, DownloadAction.DELETE),
            DownloadAction.available(download(10, 10, "App.APK")))
        assertEquals(listOf(DownloadAction.SHOW_FOLDER, DownloadAction.DELETE),
            DownloadAction.available(download(Download.SIZE_FAILED, 3, "app.apk")))
        assertEquals(listOf(DownloadAction.SHOW_FOLDER, DownloadAction.DELETE),
            DownloadAction.available(download(10, 10, "video.mp4")))
    }

    @Test fun theBoardAnnouncesEveryChangeAndForgetsEndedTasks() {
        val board = TransferBoard()
        val seen = mutableListOf<Download?>()
        board.changed.observe({ seen += it }, sendCurrent = false)
        val job = Download().apply { incognito = true }
        val task = StreamTransfer(job, "x".byteInputStream(), object : Transfer.Callback {
            override fun onProgress(task: Transfer) {}
            override fun onError(task: Transfer, responseCode: Int, responseMessage: String) {}
            override fun onDone(task: Transfer) {}
        })
        board.transfers.add(task)
        board.publish(task)
        board.end(task)
        assertEquals(listOf(job, job), seen)
        assertTrue(board.transfers.isEmpty())
    }
}
