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
import androidx.compose.runtime.remember
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
import com.reiniertutoriales.vireolumatv.activity.main.remote.RemoteLinkServer
import com.reiniertutoriales.vireolumatv.utils.QrCode
import kotlinx.coroutines.CoroutineScope

/**
 * Shows a scannable QR code and local address for sending a link from a phone on the same Wi-Fi
 * or LAN. The embedded server runs only while this dialog is open and stops as soon as a valid
 * link arrives or the dialog is dismissed.
 */
internal class RemoteLinkDialog(
    context: Context,
    scope: CoroutineScope,
    searchTemplate: String,
    private val onOpenUrl: (String) -> Unit,
) : ComponentDialog(context, R.style.TvDialog) {

    private val server = RemoteLinkServer(
        scope = scope,
        searchTemplate = searchTemplate,
        labels = RemoteLinkServer.Labels(
            title = context.getString(R.string.remote_link_title),
            hint = context.getString(R.string.address_hint),
            submit = context.getString(R.string.remote_link_send),
            sent = context.getString(R.string.remote_link_sent),
            invalidUrl = context.getString(R.string.remote_link_invalid),
            error = context.getString(R.string.remote_link_error),
        ),
    ) { receivedUrl ->
        if (isShowing) dismiss()
        onOpenUrl(receivedUrl)
    }

    private val localUrl: String? = server.start()
    private val qr: QrCode? = localUrl?.let { runCatching { QrCode.encode(it) }.getOrNull() }

    init {
        setContentView(ComposeView(context).apply {
            setContent { VireoTvTheme { Content() } }
        })
        setOnDismissListener { server.close() }
    }

    override fun onStop() {
        server.close()
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
            if (localUrl != null && qr != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QrBox(qr)
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.remote_link_instructions),
                            color = Color.LightGray,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            localUrl,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .testTag("remote_link_url"),
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.remote_link_no_network),
                    color = Color.LightGray,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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
            Modifier.size(204.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(180.dp).testTag("remote_link_qr")) {
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
