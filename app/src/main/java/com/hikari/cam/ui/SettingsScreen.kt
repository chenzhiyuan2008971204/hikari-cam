package com.hikari.cam.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.BuildConfig
import com.hikari.data.UserSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(app: AppViewModel, rawSupported: Boolean?) {
    val s by app.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Text("Settings", color = Darkroom.Text, fontSize = 22.sp, fontWeight = FontWeight.Light, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))

        DarkCard("拍攝與儲存") {
            var q by remember(s.jpegQuality) { mutableFloatStateOf(s.jpegQuality.toFloat()) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("JPEG 品質 ${q.roundToInt()}", color = Darkroom.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                ThinSlider(q, { q = Math.round(it).toFloat() }, 80f..100f, steps = 19, modifier = Modifier.weight(1.5f).testTag("jpegSlider"),
                    onFinished = { app.updateSettings { it.copy(jpegQuality = q.roundToInt()) } })
            }
            Toggle("自然畫質（不銳化）",
                when {
                    !s.naturalDetail -> "使用手機相機原本的處理（含銳化）"
                    app.naturalApplied == null -> "關閉相機銳化、使用最高品質降噪；開啟一次相機後顯示這支手機支援哪些"
                    app.naturalApplied!!.isEmpty() -> "這支手機的相機沒有提供可調整的銳化／降噪模式"
                    else -> "已套用：${app.naturalApplied}"
                }, s.naturalDetail) { v -> app.updateSettings { it.copy(naturalDetail = v) } }
            Toggle("同時保存未套濾鏡的原圖", "Pictures/Hikari/Originals", s.saveOriginal) { v -> app.updateSettings { it.copy(saveOriginal = v) } }
            when (rawSupported) {
                true -> Toggle("同時保存 DNG RAW", "Pictures/Hikari/RAW", s.saveRaw) { v -> app.updateSettings { it.copy(saveRaw = v) } }
                false -> Info("DNG RAW", "這支手機的相機不提供 RAW 輸出，使用高品質 JPEG。")
                null -> Info("DNG RAW", "開啟一次相機後才知道這支手機是否支援。")
            }
            Toggle("九宮格", "相機取景格線", s.grid) { v -> app.updateSettings { it.copy(grid = v) } }
            Text("預設長寬比", color = Darkroom.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in UserSettings.ASPECTS) SegmentButton(a, s.aspect == a, { app.updateSettings { it.copy(aspect = a) } }, tag = "aspect-$a")
            }
        }

        DarkCard("相機資訊") {
            Text(app.cameraReport.ifEmpty { "開啟一次相機後顯示這支手機開放給 App 的鏡頭" }, color = Darkroom.Dim, fontSize = 12.sp, lineHeight = 17.sp)
            var diag by remember { mutableStateOf(false) }
            Text("完整相機診斷　鏡頭・實體 ID・焦距・光圈・感光元件・變焦・RAW ›", color = Darkroom.Amber, fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp).clip(Darkroom.R6).clickable { diag = true }.padding(vertical = 4.dp).testTag("openCameraDiag"))
            if (diag) CameraDiagDialog { diag = false }
        }

        DarkCard("智慧功能") {
            Toggle("即時濾鏡推薦", "相機依畫面量測到的場景，從全部濾鏡中排出最適合的三款", s.recommend) { v -> app.updateSettings { it.copy(recommend = v) } }
            Toggle("虛擬相機（測試用）", "不開實體鏡頭，改用內建的標準測試照片當作相機畫面；預覽、濾鏡、構圖、推薦與拍照都走同一條流程。切換鏡頭鍵會換下一張照片",
                s.virtualCamera) { v -> app.updateSettings { it.copy(virtualCamera = v) } }
            Info("標準測試照片", app.samples.joinToString("\n") { "${it.zh}：${it.source}（${it.license}）" })
        }

        DarkCard("濾鏡品質工具") {
            LinkRow("Daylight Filter Lab", "同一組白天測試照片（天空、樹木、街道、建築、白衣、人像、逆光）比較日光系列 8 款") { app.go(Route.DaylightLab) }
            LinkRow("Filter Lab", "匯入任意照片測試濾鏡、檢查預覽與完整解析度匯出是否一致、看效能數據") { app.go(Route.Lab) }
        }

        Text("${BuildConfig.VERSION_NAME} · ${app.looks.size} 款原創濾鏡 · GPU 底片引擎", color = Darkroom.Dim, fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Darkroom.R6).clickable { onChange(!value) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = Darkroom.Text, fontSize = 15.sp)
            Text(sub, color = Darkroom.Dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        QuietSwitch(value, onChange)
    }
}

@Composable
private fun Info(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = Darkroom.Text, fontSize = 15.sp)
        Text(sub, color = Darkroom.Dim, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun LinkRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(Darkroom.R6).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Darkroom.Text, fontSize = 15.sp)
            Text(sub, color = Darkroom.Dim, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Text("›", color = Darkroom.Dim, fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp))
    }
}
