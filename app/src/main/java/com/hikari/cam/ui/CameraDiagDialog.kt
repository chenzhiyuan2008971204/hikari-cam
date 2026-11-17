package com.hikari.cam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hikari.cam.camera.CameraDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full camera diagnosis for a screenshot: every camera the phone lists, the physical lenses behind each
 * (hidden ids too, with the deep scan), their real focal length, aperture, sensor size, zoom range and RAW
 * support, and a verdict on whether a telephoto is reachable.
 */
@Composable
fun CameraDiagDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var deep by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("掃描中…") }
    LaunchedEffect(deep) {
        text = "掃描中…"
        text = withContext(Dispatchers.IO) {
            runCatching { CameraDiagnostics.format(CameraDiagnostics.scan(ctx, deep)) }.getOrElse { "掃描失敗：${it.javaClass.simpleName} ${it.message.orEmpty()}" }
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111)).padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("相機診斷", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClose) { Text("關閉", color = HikariColors.Accent) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ deep = !deep }, modifier = Modifier.testTag("diagDeep")) { Text(if (deep) "深度掃描：開（ID 0–99）" else "深度掃描 ID 0–99", fontSize = 12.sp) }
                TextButton({ clip.setText(AnnotatedString(text)) }) { Text("複製", fontSize = 12.sp) }
                TextButton({
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
                    ctx.startActivity(android.content.Intent.createChooser(send, "分享相機診斷").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("分享", fontSize = 12.sp) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).testTag("diagText")) {
                Text(text, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
