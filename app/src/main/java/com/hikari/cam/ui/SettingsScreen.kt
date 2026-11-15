package com.hikari.cam.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.BuildConfig
import com.hikari.data.UserSettings
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(app: AppViewModel, rawSupported: Boolean?) {
    val s by app.settings.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Settings", color = Color.White, fontSize = 22.sp, modifier = Modifier.padding(16.dp))

        SectionTitle("拍攝與儲存")
        var q by remember(s.jpegQuality) { mutableFloatStateOf(s.jpegQuality.toFloat()) }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("JPEG 品質 ${q.roundToInt()}", color = Color.White, modifier = Modifier.weight(1f))
            Slider(q, { q = it }, valueRange = 80f..100f,
                onValueChangeFinished = { app.updateSettings { it.copy(jpegQuality = q.roundToInt()) } },
                modifier = Modifier.weight(1.4f))
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
        Info("相機資訊", app.cameraReport.ifEmpty { "開啟一次相機後顯示這支手機開放給 App 的鏡頭" })
        Toggle("九宮格", "相機取景格線", s.grid) { v -> app.updateSettings { it.copy(grid = v) } }
        Text("預設長寬比", color = Color.White, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        Row(Modifier.padding(horizontal = 12.dp)) {
            for (a in UserSettings.ASPECTS) {
                FilterChip(selected = s.aspect == a, onClick = { app.updateSettings { it.copy(aspect = a) } },
                    label = { Text(a) }, modifier = Modifier.padding(4.dp))
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HikariColors.Line)
        SectionTitle("智慧功能")
        Toggle("即時濾鏡推薦", "相機依畫面量測到的場景，從全部濾鏡中排出最適合的三款", s.recommend) { v -> app.updateSettings { it.copy(recommend = v) } }
        Toggle("虛擬相機（測試用）", "不開實體鏡頭，改用內建的標準測試照片當作相機畫面；預覽、濾鏡、構圖、推薦與拍照都走同一條流程。切換鏡頭鍵會換下一張照片",
            s.virtualCamera) { v -> app.updateSettings { it.copy(virtualCamera = v) } }
        Info("標準測試照片", app.samples.joinToString("\n") { "${it.zh}：${it.source}（${it.license}）" })

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HikariColors.Line)
        SectionTitle("濾鏡品質工具")
        Row(Modifier.fillMaxWidth().clickable { app.go(Route.DaylightLab) }.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Daylight Filter Lab", color = Color.White)
                Text("同一組白天測試照片（天空、樹木、街道、建築、白衣、人像、逆光）比較日光系列 8 款", color = HikariColors.Dim, fontSize = 12.sp)
            }
            Text("›", color = HikariColors.Dim, fontSize = 20.sp)
        }
        Row(Modifier.fillMaxWidth().clickable { app.go(Route.Lab) }.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Filter Lab", color = Color.White)
                Text("匯入任意照片測試濾鏡、檢查預覽與完整解析度匯出是否一致、看效能數據", color = HikariColors.Dim, fontSize = 12.sp)
            }
            Text("›", color = HikariColors.Dim, fontSize = 20.sp)
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = HikariColors.Line)
        Info("版本", "${BuildConfig.VERSION_NAME} · ${app.looks.size} 款原創濾鏡 · GPU 底片引擎")
    }
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(sub, color = HikariColors.Dim, fontSize = 12.sp)
        }
        Switch(value, onChange)
    }
}

@Composable
private fun Info(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, color = Color.White)
        Text(sub, color = HikariColors.Dim, fontSize = 12.sp)
    }
}
