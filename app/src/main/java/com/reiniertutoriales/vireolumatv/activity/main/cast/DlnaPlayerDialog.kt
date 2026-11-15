package com.reiniertutoriales.vireolumatv.activity.main.cast

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.view.KeyEvent
import android.widget.VideoView
import androidx.activity.ComponentDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.MediaKind
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.PlaybackSnapshot
import com.reiniertutoriales.vireolumatv.activity.main.cast.DlnaMediaRendererServer.TransportState
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTextButton
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

/**
 * Fullscreen DLNA media player (video, audio, and photos) controlled by both the TV remote
 * (D-pad) and incoming UPnP AVTransport / RenderingControl commands.
 */
internal class DlnaPlayerDialog(
    context: Context,
    private val scope: CoroutineScope,
    private val onClosed: () -> Unit,
) : ComponentDialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private var mediaUri by mutableStateOf("")
    private var mediaTitle by mutableStateOf("")
    private var mediaKind by mutableStateOf(MediaKind.VIDEO)
    private var transportState by mutableStateOf(TransportState.STOPPED)
    private var positionSeconds by mutableIntStateOf(0)
    private var durationSeconds by mutableIntStateOf(0)
    private var volume by mutableIntStateOf(100)
    private var muted by mutableStateOf(false)
    private var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var hudVisible by mutableStateOf(true)
    private var hudInteractionTick by mutableIntStateOf(0)

    private var videoView: VideoView? = null
    private var mediaPlayer: MediaPlayer? = null

    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { PlayerContent() } }
        })
        setOnDismissListener {
            stopMedia()
            onClosed()
        }
    }

    fun snapshot(): PlaybackSnapshot = PlaybackSnapshot(
        transportState = transportState,
        currentUri = mediaUri,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        volume = volume,
        muted = muted,
    )

    fun load(uri: String, title: String, kind: MediaKind) {
        mediaUri = uri
        mediaTitle = title
        mediaKind = kind
        errorMessage = null
        imageBitmap = null
        positionSeconds = 0
        durationSeconds = 0
        transportState = TransportState.TRANSITIONING
        revealHud()

        if (kind == MediaKind.IMAGE) {
            stopMedia()
            transportState = TransportState.PLAYING
            loadRemoteImage(uri)
        } else {
            videoView?.let { view ->
                view.setVideoURI(uri.toUri())
                view.start()
            }
        }
    }

    fun play() {
        if (mediaKind == MediaKind.IMAGE) return
        videoView?.start()
        transportState = TransportState.PLAYING
        revealHud()
    }

    fun pause() {
        if (mediaKind == MediaKind.IMAGE) return
        videoView?.let { if (it.isPlaying) it.pause() }
        transportState = TransportState.PAUSED_PLAYBACK
        revealHud()
    }

    fun seekTo(seconds: Int) {
        if (mediaKind == MediaKind.IMAGE) return
        val clamped = seconds.coerceAtLeast(0)
        videoView?.seekTo(clamped * 1000)
        positionSeconds = clamped
        revealHud()
    }

    fun setVolumePercent(percent: Int) {
        volume = percent.coerceIn(0, 100)
        applyPlayerVolume()
    }

    fun setMutedState(isMuted: Boolean) {
        muted = isMuted
        applyPlayerVolume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!hudVisible) {
                    revealHud()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!hudVisible && mediaKind != MediaKind.IMAGE) {
                    seekTo((positionSeconds - 10).coerceAtLeast(0))
                    return true
                }
                revealHud()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!hudVisible && mediaKind != MediaKind.IMAGE) {
                    seekTo(positionSeconds + 10)
                    return true
                }
                revealHud()
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> revealHud()
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlayPause() {
        if (mediaKind == MediaKind.IMAGE) {
            hudVisible = !hudVisible
            if (hudVisible) hudInteractionTick++
            return
        }
        if (transportState == TransportState.PLAYING) pause() else play()
    }

    private fun revealHud() {
        hudVisible = true
        hudInteractionTick++
    }

    private fun applyPlayerVolume() {
        val level = if (muted) 0f else volume / 100f
        runCatching { mediaPlayer?.setVolume(level, level) }
    }

    private fun stopMedia() {
        runCatching { videoView?.stopPlayback() }
        mediaPlayer = null
        transportState = TransportState.STOPPED
    }

    private fun loadRemoteImage(url: String) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URI.create(url).toURL().openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 12_000
                    }
                    try {
                        if (conn.responseCode !in 200..299) return@runCatching null
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        conn.inputStream.use { input ->
                            while (out.size() < MAX_IMAGE_BYTES) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                            }
                        }
                        val bytes = out.toByteArray()
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) {
                            sample *= 2
                        }
                        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()
            }
            if (mediaUri == url) {
                if (loaded != null) imageBitmap = loaded
                else errorMessage = context.getString(R.string.dlna_load_error)
            }
        }
    }

    @Composable
    private fun PlayerContent() {
        val playFocus = remember { FocusRequester() }

        LaunchedEffect(hudInteractionTick, hudVisible) {
            if (hudVisible) {
                delay(5_000L)
                hudVisible = false
            }
        }

        LaunchedEffect(mediaUri, mediaKind) {
            if (mediaKind != MediaKind.IMAGE) {
                while (true) {
                    delay(1_000L)
                    val view = videoView ?: continue
                    runCatching {
                        if (view.isPlaying) {
                            positionSeconds = (view.currentPosition / 1000).coerceAtLeast(0)
                            durationSeconds = (view.duration / 1000).coerceAtLeast(0)
                            transportState = TransportState.PLAYING
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when (mediaKind) {
                MediaKind.IMAGE -> {
                    val bmp = imageBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = mediaTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                MediaKind.VIDEO, MediaKind.AUDIO -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                videoView = this
                                setOnPreparedListener { mp ->
                                    mediaPlayer = mp
                                    applyPlayerVolume()
                                    durationSeconds = (duration / 1000).coerceAtLeast(0)
                                    transportState = TransportState.PLAYING
                                    start()
                                }
                                setOnCompletionListener {
                                    transportState = TransportState.STOPPED
                                    positionSeconds = durationSeconds
                                    revealHud()
                                }
                                setOnErrorListener { _, _, _ ->
                                    transportState = TransportState.STOPPED
                                    errorMessage = context.getString(R.string.dlna_load_error)
                                    revealHud()
                                    true
                                }
                                if (mediaUri.isNotEmpty()) {
                                    setVideoURI(mediaUri.toUri())
                                    start()
                                }
                            }
                        },
                    )
                    if (mediaKind == MediaKind.AUDIO) {
                        Text(
                            mediaTitle,
                            color = Color.White,
                            fontSize = 24.sp,
                            modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        )
                    }
                }
            }

            errorMessage?.let { err ->
                Text(
                    err,
                    color = Color(0xFFFF8A80),
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            }

            if (hudVisible) {
                Column(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(Color(0xCC11161F), RoundedCornerShape(16.dp))
                        .padding(20.dp),
                ) {
                    Text(mediaTitle, color = Color.White, fontSize = 18.sp)
                    if (mediaKind != MediaKind.IMAGE) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${DlnaMediaRendererServer.formatHmsSeconds(positionSeconds)} / ${DlnaMediaRendererServer.formatHmsSeconds(durationSeconds)}",
                            color = Color.LightGray,
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (mediaKind != MediaKind.IMAGE) {
                            TvTextButton(
                                text = "-10s",
                                onClick = { seekTo((positionSeconds - 10).coerceAtLeast(0)) },
                            )
                            TvTextButton(
                                text = if (transportState == TransportState.PLAYING) {
                                    stringResource(R.string.dlna_pause)
                                } else {
                                    stringResource(R.string.dlna_play)
                                },
                                onClick = { togglePlayPause() },
                                modifier = Modifier.focusRequester(playFocus),
                            )
                            TvTextButton(
                                text = "+10s",
                                onClick = { seekTo(positionSeconds + 10) },
                            )
                        }
                        TvTextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = { dismiss() },
                            modifier = if (mediaKind == MediaKind.IMAGE) Modifier.focusRequester(playFocus) else Modifier,
                        )
                    }
                }
                LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
            }
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
    }
}
