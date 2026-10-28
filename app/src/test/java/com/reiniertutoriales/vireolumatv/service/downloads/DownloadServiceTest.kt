package com.reiniertutoriales.vireolumatv.service.downloads

import android.content.pm.ServiceInfo
import com.reiniertutoriales.vireolumatv.VireoLumaTVApp
import com.reiniertutoriales.vireolumatv.activity.downloads.ActiveDownloadsModel
import com.reiniertutoriales.vireolumatv.model.Download
import com.reiniertutoriales.vireolumatv.utils.activemodel.ActiveModelsRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = VireoLumaTVApp::class, sdk = [35])
class DownloadServiceTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun freeFileKeepsTheExtensionAndTreatsALeadingDotAsPartOfTheName() {
        val dir = folder.root
        assertEquals("a.txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve("a.txt").createNewFile()
        assertEquals("a_(1).txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve("a_(1).txt").createNewFile()
        assertEquals("a_(2).txt", DownloadService.freeFile(dir, "a.txt").name)
        dir.resolve(".config").createNewFile()
        assertEquals(".config_(1)", DownloadService.freeFile(dir, ".config").name)
        dir.resolve("README").createNewFile()
        assertEquals("README_(1)", DownloadService.freeFile(dir, "README").name)
    }

    @Test fun foregroundTimeoutCancelsDownloadsAndStopsTheService() {
        val controller = Robolectric.buildService(DownloadService::class.java).create()
        val service = controller.get()
        val model = ActiveModelsRepository.get(ActiveDownloadsModel::class, service)
        val info = Download().apply { incognito = true }
        model.activeDownloads.add(StreamDownloadTask(info, "x".byteInputStream(), service.downloadTasksListener))
        service.onTimeout(1, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertTrue("A running download must be cancelled", info.cancelled)
        assertTrue("The service must stop itself within the timeout", shadowOf(service).isStoppedBySelf)
        model.activeDownloads.clear()
        controller.destroy()
    }
}
