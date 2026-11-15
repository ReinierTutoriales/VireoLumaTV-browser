package com.reiniertutoriales.vireolumatv.activity.main.dialogs.remote

import android.content.Context
import androidx.activity.ComponentDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.reiniertutoriales.vireolumatv.R
import com.reiniertutoriales.vireolumatv.activity.main.compose.TvTextButton
import com.reiniertutoriales.vireolumatv.activity.main.compose.VireoTvTheme
import com.reiniertutoriales.vireolumatv.utils.QrCode

/**
 * Displays the persistent pairing QR code and local address for sending links from a phone
 * (including iPhone Share Sheet / Shortcuts). The underlying server stays active while the app
 * is open in the foreground until the user unpairs.
 */
internal class RemoteLinkDialog(
    context: Context,
    ensureLocalNetwork: ((Boolean) -> Unit) -> Unit = { it(true) },
    startServer: () -> String?,
    autoplayEnabled: Boolean = true,
    private val endpointChanged: () -> Boolean = { false },
    private val onEnableAutoplay: () -> Unit = {},
    private val onUnpair: () -> Unit = {},
) : ComponentDialog(context, R.style.TvDialog) {

    private var closed = false
    private var localUrl by mutableStateOf<String?>(null)
    private var qr by mutableStateOf<QrCode?>(null)
    private var ready by mutableStateOf(false)
    private var unpaired by mutableStateOf(false)
    private var autoplay by mutableStateOf(autoplayEnabled)

    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { Content() } }
        })
        setOnDismissListener { closed = true }
        ensureLocalNetwork { granted ->
            if (!closed && granted) {
                val startedUrl = startServer()
                localUrl = startedUrl
                qr = startedUrl?.let { runCatching { QrCode.encode(it) }.getOrNull() }
            }
            ready = true
        }
    }

    override fun onStop() {
        closed = true
        super.onStop()
    }

    @Composable
    private fun Content() {
        val closeFocus = remember { FocusRequester() }
        val shape = RoundedCornerShape(16.dp)
        Column(
            Modifier.semantics { testTagsAsResourceId = true }
                .widthIn(min = 380.dp, max = 720.dp)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(1.dp, Color.DarkGray, shape)
                .padding(24.dp),
        ) {
            Text(
                stringResource(R.string.remote_link_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            val activeUrl = localUrl
            val activeQr = qr
            when {
                unpaired -> {
                    Text(
                        stringResource(R.string.remote_link_unpaired),
                        color = Color.LightGray,
                        fontSize = 16.sp,
                    )
                }
                activeUrl != null && activeQr != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QrBox(activeQr)
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.remote_link_instructions),
                                color = Color.LightGray,
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                activeUrl,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("remote_link_url"),
                            )
                            if (endpointChanged()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    stringResource(R.string.remote_link_address_changed),
                                    color = Color(0xFFFFD580),
                                    fontSize = 13.sp,
                                )
                            }
                            if (!autoplay) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(R.string.remote_link_autoplay_hint),
                                    color = Color(0xFFFFD580),
                                    fontSize = 13.sp,
                                )
                                Spacer(Modifier.height(8.dp))
                                TvTextButton(
                                    text = stringResource(R.string.remote_link_enable_autoplay),
                                    onClick = {
                                        onEnableAutoplay()
                                        autoplay = true
                                    },
                                    modifier = Modifier.testTag("remote_link_autoplay"),
                                )
                            }
                        }
                    }
                }
                ready -> {
                    Text(
                        stringResource(R.string.remote_link_no_network),
                        color = Color.LightGray,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                if (!unpaired && activeUrl != null) {
                    TvTextButton(
                        text = stringResource(R.string.remote_link_unpair),
                        onClick = {
                            onUnpair()
                            unpaired = true
                            localUrl = null
                            qr = null
                        },
                        modifier = Modifier.testTag("remote_link_unpair"),
                    )
                }
                TvTextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { dismiss() },
                    modifier = Modifier.focusRequester(closeFocus).testTag("remote_link_close"),
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
    }

    @Composable
    private fun QrBox(code: QrCode) {
        val quietZone = 3
        val total = code.size + quietZone * 2
        Box(
            Modifier.size(220.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(200.dp)) {
                val cell = size.width / total
                for (y in 0 until code.size) {
                    for (x in 0 until code.size) {
                        if (code[x, y]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset((x + quietZone) * cell, (y + quietZone) * cell),
                                size = Size(cell + 0.5f, cell + 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}
