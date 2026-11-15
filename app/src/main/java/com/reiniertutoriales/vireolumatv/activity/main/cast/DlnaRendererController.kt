package com.reiniertutoriales.vireolumatv.activity.main.cast

import com.reiniertutoriales.vireolumatv.Config
import com.reiniertutoriales.vireolumatv.activity.main.MainActivity
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.DlnaCommand
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

/**
 * Coordinates the optional UPnP/DLNA MediaRenderer server and the fullscreen player dialog
 * while the browser is open in the foreground.
 */
internal class DlnaRendererController(
    private val activity: MainActivity,
    private val config: Config,
    private val scope: CoroutineScope,
) {
    private var server: DlnaMediaRendererServer? = null
    private var playerDialog: DlnaPlayerDialog? = null

    fun start() {
        if (server != null) return
        val uuid = config.dlnaRendererUuid.ifBlank {
            UUID.randomUUID().toString().also { config.dlnaRendererUuid = it }
        }
        val renderer = DlnaMediaRendererServer(
            context = activity,
            scope = scope,
            deviceUuid = uuid,
            stateProvider = { playerDialog?.snapshot() ?: PlaybackSnapshot() },
            onCommand = ::handleCommand,
        )
        if (!renderer.start()) {
            renderer.close()
            return
        }
        server = renderer
    }

    fun stop() {
        playerDialog?.dismiss()
        playerDialog = null
        server?.close()
        server = null
    }

    private fun handleCommand(command: DlnaCommand) {
        when (command) {
            is DlnaCommand.Load -> {
                val dialog = ensurePlayerDialog()
                dialog.load(command.uri, command.title, command.kind)
            }
            DlnaCommand.Play -> playerDialog?.play()
            DlnaCommand.Pause -> playerDialog?.pause()
            DlnaCommand.Stop -> {
                playerDialog?.dismiss()
                playerDialog = null
            }
            is DlnaCommand.Seek -> playerDialog?.seekTo(command.positionSeconds)
            is DlnaCommand.SetVolume -> playerDialog?.setVolumePercent(command.volume)
            is DlnaCommand.SetMute -> playerDialog?.setMutedState(command.muted)
        }
    }

    private fun ensurePlayerDialog(): DlnaPlayerDialog {
        playerDialog?.takeIf { it.isShowing }?.let { return it }
        return DlnaPlayerDialog(activity, scope) {
            playerDialog = null
        }.also {
            playerDialog = it
            it.show()
        }
    }
}
