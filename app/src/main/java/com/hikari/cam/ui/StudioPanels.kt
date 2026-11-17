package com.hikari.cam.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.engine.FilterSpec
import com.hikari.studio.Browse
import com.hikari.studio.FitMode
import com.hikari.studio.FrameCat
import com.hikari.studio.FrameDef
import com.hikari.studio.FrameOptions
import com.hikari.studio.Frames
import com.hikari.studio.LookItem
import com.hikari.studio.LookSort
import com.hikari.studio.StampContent
import com.hikari.studio.StampPos
import com.hikari.studio.StampSpec
import com.hikari.studio.StampStyles
import com.hikari.studio.TimeSource
import java.time.LocalDateTime
import kotlin.math.roundToInt

// ------------------------------------------------------------------ small shared pieces

@Composable
fun Chip(text: String, on: Boolean, tag: String = "", onClick: () -> Unit) {
    Text(text, color = if (on) Color.Black else Darkroom.Text, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(Darkroom.R6).background(if (on) HikariColors.Accent else Darkroom.CardHi)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 5.dp).then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier))
}

@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
private fun Label(text: String) = Text(text, color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))

@Composable
private fun Field(value: String, hint: String, tag: String = "", onChange: (String) -> Unit) {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        if (value.isEmpty()) Text(hint, color = Color(0xFF6A6A6A), fontSize = 13.sp)
        BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 13.sp), cursorBrush = SolidColor(HikariColors.Accent),
            modifier = Modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier))
    }
}

@Composable
private fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, show: String, onChange: (Float) -> Unit) {
    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(64.dp))
        Slider(value, onChange, valueRange = range, modifier = Modifier.weight(1f).height(30.dp))
        Text(show, color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun Swatches(colors: List<Int>, selected: Int, labels: Map<Int, String> = emptyMap(), onPick: (Int) -> Unit) {
    ChipRow {
        for (c in colors) {
            val on = c == selected
            Box(Modifier.size(26.dp).clip(CircleShape).background(if (c == 0) Color(0xFF242424) else Color(c)).border(if (on) 2.dp else 1.dp, if (on) HikariColors.Accent else Color(0xFF555555), CircleShape)
                .clickable { onPick(c) }, contentAlignment = Alignment.Center) { labels[c]?.let { Text(it, color = Color.White, fontSize = 9.sp) } }
        }
    }
}

// ------------------------------------------------------------------ filter categories (one category at a time)

fun lookItems(looks: List<FilterSpec>) = looks.map { LookItem(it.id, it.name, it.zh, it.series) }

/** Horizontal category navigation; the selected category is remembered in the settings. */
@Composable
fun LookCategoryBar(app: AppViewModel, looks: List<FilterSpec>, modifier: Modifier = Modifier, short: Boolean = false, favFirst: Boolean = false, tagPrefix: String = "cat-") {
    val settings by app.settings.collectAsState()
    val favorites by app.favorites.collectAsState()
    val cats = remember(looks, favorites, favFirst) {
        Browse.categories(SERIES_ZH.toList(), lookItems(looks), favorites).let { l -> if (favFirst) l.sortedBy { if (it.id == Browse.FAV) 0 else 1 } else l }
    }
    val cur = Browse.valid(settings.filterCategory, cats)
    ChipRow(modifier) {
        for (c in cats) Chip((if (short) c.title.substringAfterLast(' ') else c.title) + " ${c.count}", c.id == cur, "$tagPrefix${c.id}") { app.updateSettings { it.copy(filterCategory = c.id) } }
    }
}

/** The filters of the category that is selected now. */
@Composable
fun looksInCategory(app: AppViewModel, looks: List<FilterSpec>, query: String = "", sort: LookSort = LookSort.DEFAULT): List<FilterSpec> {
    val settings by app.settings.collectAsState()
    val favorites by app.favorites.collectAsState()
    val recents by app.recents.collectAsState()
    return remember(looks, settings.filterCategory, favorites, query, sort, recents) {
        val items = lookItems(looks); val cats = Browse.categories(SERIES_ZH.toList(), items, favorites)
        val byId = looks.associateBy { it.id }
        Browse.list(items, Browse.valid(settings.filterCategory, cats), favorites, query, sort, recents).mapNotNull { byId[it.id] }
    }
}

// ------------------------------------------------------------------ camera: my looks + "more"

/**
 * The camera's look panel. Top: a card with the three looks recommended for what the camera sees.
 * Below: one row with every look, whose first cell is 收藏 (favourites only), and a "more" tile that
 * opens the library by category, with search and the ★ to favourite a look.
 */
@Composable
fun CameraLookPanel(app: AppViewModel, look: FilterSpec, favorites: Set<String>, recs: List<com.hikari.vision.Recommendation>, sceneLabel: String, onPick: (String) -> Unit) {
    var more by remember { mutableStateOf(false) }
    val camSettings by app.settings.collectAsState()
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        // camera recipes: one tap switches the whole style
        RecipeBar(app, look, remember(camSettings.cameraAdjustJson) { com.hikari.engine.Adjustments.fromJson(camSettings.cameraAdjustJson) })
        if (recs.isNotEmpty()) Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(Darkroom.R12)
            .background(Color(0xCC1C1C1E)).border(1.dp, Color(0x33F59E0B), Darkroom.R12).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = HikariColors.Accent, fontSize = 13.sp)
                Text(" 為這個畫面推薦", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (sceneLabel.isNotEmpty()) Text(sceneLabel, color = HikariColors.Dim, fontSize = 11.sp)
            }
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rc in recs.take(3)) {
                    val on = rc.look.id == look.id
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) HikariColors.Accent else Color(0xFF2A2824)).clickable { onPick(rc.look.id) }
                        .padding(4.dp).testTag("rec"), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(Color.Black)) {
                            app.thumbs[rc.look.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, rc.look.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        }
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(rc.look.name, color = if (on) Color.Black else Color.White, fontSize = 10.sp, maxLines = 2, lineHeight = 12.sp, fontWeight = FontWeight.Bold)
                            Text("${rc.score}", color = if (on) Color(0xFF3A3A3A) else HikariColors.Accent, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        // every look in one row; the first cell is 收藏: tap it to show only the favourites, tap again for all
        var favOnly by remember { mutableStateOf(false) }
        val mine = remember(favOnly, favorites, app.looks) { if (favOnly) app.looks.filter { it.id in favorites } else app.looks }
        if (favOnly && mine.isEmpty()) Text("還沒有收藏。按右邊「更多」，在濾鏡上按 ★ 就會放到這裡", color = Color(0xFF6A6A6A), fontSize = 10.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            item(key = "favs") {
                Column(Modifier.width(64.dp).clickable { favOnly = !favOnly }.testTag("favTile"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(if (favOnly) HikariColors.Accent else Color(0xFF242424))
                        .border(1.dp, if (favOnly) HikariColors.Accent else Color(0xFF4A4A4A), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("★", color = if (favOnly) Color.Black else HikariColors.Accent, fontSize = 22.sp)
                    }
                    Text("收藏 ${favorites.size}", color = if (favOnly) HikariColors.Accent else Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
            items(mine, key = { it.id }) { spec ->
                val sel = spec.id == look.id
                Column(Modifier.width(64.dp).clickable { onPick(spec.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(HikariColors.Panel).border(if (sel) 2.dp else 0.dp, if (sel) HikariColors.Accent else Color.Transparent, RoundedCornerShape(8.dp))) {
                        app.thumbs[spec.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                    Text(spec.name, color = if (sel) HikariColors.Accent else Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
            item(key = "more") {
                Column(Modifier.width(64.dp).clickable { more = true }.testTag("moreLooks"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF242424)).border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("＋", color = Color.White, fontSize = 22.sp)
                    }
                    Text("更多", color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
    if (more) AllLooksSheet(app, look.id, favorites, { more = false }) { id -> onPick(id); more = false }
}

/** The whole library, one category at a time: tap to use a look, tap its star to keep it in "my looks". */
@Composable
fun AllLooksSheet(app: AppViewModel, selectedId: String, favorites: Set<String>, onClose: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = looksInCategory(app, app.looks, query)
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).clip(Darkroom.R12).background(Darkroom.Card).padding(top = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("全部濾鏡", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${shown.size} 款", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.testTag("moreCount"))
                Text("關閉", color = HikariColors.Accent, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onClose).padding(start = 14.dp, top = 6.dp, bottom = 6.dp))
            }
            LookCategoryBar(app, app.looks)
            Field(query, "在這個分類中搜尋", "moreSearch") { query = it }
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { spec ->
                    val sel = spec.id == selectedId
                    Column(Modifier.clickable { onPick(spec.id) }.testTag("more-${spec.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(HikariColors.Panel)
                            .border(if (sel) 2.dp else 0.dp, if (sel) HikariColors.Accent else Color.Transparent, RoundedCornerShape(10.dp))) {
                            app.thumbs[spec.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, spec.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Text(if (spec.id in favorites) "★" else "☆", color = if (spec.id in favorites) HikariColors.Accent else Color.White, fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Color(0x66000000)).clickable { app.toggleFavorite(spec.id) }.padding(horizontal = 6.dp, vertical = 2.dp).testTag("star-${spec.id}"))
                        }
                        Text(spec.name, color = if (sel) HikariColors.Accent else Color.White, fontSize = 11.sp, maxLines = 1)
                        Text(spec.zh, color = HikariColors.Dim, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Timestamp Studio

private val STAMP_COLORS = listOf(0xFFFF8A1E, 0xFFFF2A1A, 0xFFFFD84A, 0xFFFFB347, 0xFFFFFFFF, 0xFF111111, 0xFFF6F0E2, 0xFF38E8FF, 0xFF7CE38B).map { it.toInt() }
private val FONTS = listOf("led" to "數位 LED", "sans" to "無襯線", "serif" to "襯線", "mono" to "等寬", "condensed" to "窄體", "hand" to "手寫", "casual" to "隨筆")

@Composable
fun StampPanel(vm: EditorViewModel, app: AppViewModel) {
    val s = vm.stamp
    val ctx = LocalContext.current
    fun set(n: StampSpec) = vm.updateStamp(n)
    Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("時間戳", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("設為拍照預設", color = HikariColors.Accent, fontSize = 12.sp,
                modifier = Modifier.clickable { app.updateSettings { it.copy(stampSpec = s.copy(enabled = true, source = TimeSource.NOW).encode(), dateStamp = true) }; vm.message = "之後拍照會用這個時間戳樣式（設定可關閉日期戳）" }.padding(8.dp))
            Switch(s.enabled, { set(s.copy(enabled = it)) }, modifier = Modifier.testTag("stampSwitch"))
        }
        vm.stampWarning?.let { Text(it, color = Color(0xFFFFB347), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).testTag("stampWarning")) }
        if (!s.enabled) { Text("開啟後，時間戳會合成進匯出的照片；原始照片不會被修改，隨時可以關閉或重新編輯。", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(14.dp)); return@Column }
        Label("樣式")
        ChipRow { for (st in StampStyles.all) Chip(st.zh, s.style == st.id, "stampStyle-${st.id}") { set(st.apply(s).copy(style = st.id)) } }
        Label("時間來源（優先使用照片的 EXIF 拍攝時間；沒有時必須由你選擇，不會自動代入其他時間）")
        ChipRow { for (src in TimeSource.values()) Chip(src.zh, s.source == src, "stampSource-${src.name}") { set(s.copy(source = src)) } }
        val inf = vm.info
        Text("EXIF：${inf?.exifTime?.toString()?.replace('T', ' ') ?: "無"}　建立：${inf?.created?.toString()?.replace('T', ' ')?.take(16) ?: "無"}", color = HikariColors.Dim, fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 14.dp))
        if (s.source == TimeSource.CUSTOM) ChipRow {
            val cur = s.customTime() ?: inf?.exifTime ?: LocalDateTime.now().withSecond(0).withNano(0)
            Chip("日期 ${cur.toLocalDate()}", false, "stampPickDate") {
                android.app.DatePickerDialog(ctx, { _, y, m, d -> set(s.copy(custom = cur.withYear(y).withMonth(m + 1).withDayOfMonth(d).toString())) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show() }
            Chip("時間 %02d:%02d".format(cur.hour, cur.minute), false) {
                android.app.TimePickerDialog(ctx, { _, h, mi -> set(s.copy(custom = cur.withHour(h).withMinute(mi).toString())) }, cur.hour, cur.minute, true).show() }
        }
        Label("內容")
        ChipRow { for (c in StampContent.values()) Chip(c.zh, s.content == c) { set(s.copy(content = c)) } }
        if (s.content == StampContent.TEXT || s.content == StampContent.DATE_TEXT) Field(s.text, "自訂文字", "stampText") { set(s.copy(text = it)) }
        if (s.content == StampContent.PLACE_DATE) Field(s.place, "地點（由你輸入，App 不會自行取得位置）") { set(s.copy(place = it)) }
        if (s.content != StampContent.TEXT) {
            Label("日期／時間格式")
            ChipRow { for (f in com.hikari.studio.DateFormats.all) Chip(f.label, s.format == f.id, "stampFmt-${f.id}") { set(s.copy(format = f.id)) }
                Chip(if (s.seconds) "顯示秒 ✓" else "顯示秒", s.seconds) { set(s.copy(seconds = !s.seconds)) } }
        }
        Label("位置（也可以直接在照片上拖曳）")
        ChipRow { for (p in StampPos.values()) Chip(p.zh, s.pos == p, "stampPos-${p.name}") { set(s.copy(pos = p)) } }
        Label("字體")
        ChipRow { for ((id, zh) in FONTS) Chip(zh, s.font == id) { set(s.copy(font = id)) }
            Chip(if (s.weight >= 600) "粗體 ✓" else "粗體", s.weight >= 600) { set(s.copy(weight = if (s.weight >= 600) 400 else 700)) } }
        Label("顏色")
        Swatches(STAMP_COLORS, s.color) { set(s.copy(color = it)) }
        Label("文字背景")
        Swatches(listOf(0, 0x73000000, 0xB3000000.toInt(), 0x73FFFFFF, 0xFFFFFFFF.toInt()), s.bg, mapOf(0 to "無")) { set(s.copy(bg = it)) }
        Slide("大小", s.size, 0.012f..0.09f, "%.1f%%".format(s.size * 100)) { set(s.copy(size = it)) }
        Slide("不透明度", s.opacity, 0.2f..1f, "${(s.opacity * 100).roundToInt()}%") { set(s.copy(opacity = it)) }
        Slide("字距", s.spacing, -0.05f..0.6f, "%.2f".format(s.spacing)) { set(s.copy(spacing = it)) }
        Slide("邊距", s.margin, 0f..0.15f, "%.1f%%".format(s.margin * 100)) { set(s.copy(margin = it)) }
        Slide("旋轉", s.rotation, -90f..90f, "${s.rotation.roundToInt()}°") { set(s.copy(rotation = (it / 5f).roundToInt() * 5f)) }
        Box(Modifier.height(8.dp))
    }
}

/** The composed preview (photo + stamp + frame). In the timestamp tab a drag or tap on the photo places the stamp. */
@Composable
fun FramedView(b: Bitmap, vm: EditorViewModel, placing: Boolean) {
    val ratio = b.width.toFloat() / b.height
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitWide = maxWidth / maxHeight > ratio
        var m = if (fitWide) Modifier.fillMaxHeight().aspectRatio(ratio) else Modifier.fillMaxWidth().aspectRatio(ratio)
        if (placing && vm.stamp.enabled) {
            fun place(x: Float, y: Float, w: Int, h: Int) { val r = vm.photoRect
                val fx = ((x / w - r[0]) / (r[2] - r[0])).coerceIn(0.02f, 0.98f); val fy = ((y / h - r[1]) / (r[3] - r[1])).coerceIn(0.04f, 0.99f)
                vm.updateStamp(vm.stamp.copy(pos = StampPos.CUSTOM, x = fx, y = fy)) }
            m = m.pointerInput(Unit) { detectDragGestures { ch, _ -> place(ch.position.x, ch.position.y, size.width, size.height) } }
                .pointerInput(Unit) { detectTapGestures { p -> place(p.x, p.y, size.width, size.height) } }
        }
        Image(remember(b) { b.asImageBitmap() }, "預覽", m.testTag("framedPreview"))
    }
}

// ------------------------------------------------------------------ Frame Studio

private val PAPER_COLORS = listOf(0, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFF6F0E2, 0xFFEDEBE7, 0xFFC8A77C, 0xFF7B7B7B, 0xFFD9A441, 0xFF2F4858, 0xFFF6E9E4, 0xFF1C1F2A).map { it.toInt() }
private val RATIOS = listOf(0f to "依照片", 1f to "1:1", 0.8f to "4:5", 1.5f to "3:2", 4f / 3f to "4:3", 0.75f to "3:4", 16f / 9f to "16:9", 9f / 16f to "9:16")

/** which frames to list: a category, favourites, recently used or everything, narrowed by search */
fun framesFor(tab: String, favs: List<String>, recents: List<String>, query: String): List<FrameDef> {
    val base = when (tab) {
        "all" -> Frames.all
        "fav" -> Frames.all.filter { it.id in favs }
        "recent" -> recents.mapNotNull { Frames.get(it) }
        else -> Frames.inCategory(FrameCat.valueOf(tab))
    }
    return Frames.search(query, base)
}

@Composable
fun FrameCategoryBar(tab: String, favCount: Int, onPick: (String) -> Unit) {
    ChipRow {
        Chip("全部 ${Frames.all.size}", tab == "all", "frameCat-all") { onPick("all") }
        Chip("收藏 $favCount", tab == "fav", "frameCat-fav") { onPick("fav") }
        Chip("最近", tab == "recent") { onPick("recent") }
        for (c in FrameCat.values()) Chip("${c.zh} ${Frames.inCategory(c).size}", tab == c.name, "frameCat-${c.name}") { onPick(c.name) }
    }
}

@Composable
fun FramePanel(vm: EditorViewModel, app: AppViewModel) {
    val settings by app.settings.collectAsState()
    val favs = Browse.csvList(settings.frameFavs); val recents = Browse.csvList(settings.frameRecents)
    var tab by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    val o = vm.frame
    fun set(n: FrameOptions) = vm.updateFrame(n)
    val list = remember(tab, query, settings.frameFavs, settings.frameRecents) { framesFor(tab, favs, recents, query) }
    Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
        FrameCategoryBar(tab, favs.size) { tab = it }
        Field(query, "搜尋邊框（例：拍立得、35mm、雜誌）", "frameSearch") { query = it }
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
            item {
                Column(Modifier.width(84.dp).clickable { set(o.copy(frame = "")) }.testTag("frameNone"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(HikariColors.Panel).border(if (o.frame.isEmpty()) 2.dp else 0.dp, if (o.frame.isEmpty()) HikariColors.Accent else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) { Text("無邊框", color = Color.White, fontSize = 12.sp) }
                    Text("原圖", color = HikariColors.Dim, fontSize = 10.sp)
                }
            }
            items(list, key = { it.id }) { f ->
                val sel = o.frame == f.id
                Column(Modifier.width(84.dp).clickable { set(o.copy(frame = f.id)); app.updateSettings { it.copy(frameRecents = Browse.csvAdd(it.frameRecents, f.id)) } }.testTag("frame-${f.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3C)).border(if (sel) 2.dp else 0.dp, if (sel) HikariColors.Accent else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center) {
                        vm.frameThumbs[f.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, f.zh, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) }
                        if (f.id in favs) Text("★", color = HikariColors.Accent, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp))
                    }
                    Text(f.zh, color = if (sel) HikariColors.Accent else Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        if (list.isEmpty()) Text("這個分類沒有符合的邊框。", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        val def = Frames.get(o.frame)
        if (def != null) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${def.zh} · ${def.name}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(if (def.id in favs) "★ 已收藏" else "☆ 收藏", color = HikariColors.Accent, fontSize = 12.sp,
                    modifier = Modifier.clickable { app.updateSettings { it.copy(frameFavs = Browse.csvToggle(it.frameFavs, def.id)) } }.padding(8.dp).testTag("frameFav"))
            }
            Slide("厚度", o.thickness, 0.5f..2f, "×%.2f".format(o.thickness)) { set(o.copy(thickness = (it * 20).roundToInt() / 20f)) }
            Label("邊框顏色（第一個＝這款邊框原本的顏色）")
            Swatches(PAPER_COLORS, o.color, mapOf(0 to "原")) { set(o.copy(color = it)) }
            Field(o.title, "標題文字（留空＝這款邊框的預設）", "frameTitle") { set(o.copy(title = it)) }
            Field(o.caption, "說明／手寫文字（留空＝預設）") { set(o.copy(caption = it)) }
            ChipRow {
                Chip(if (o.showDate) "顯示日期 ✓" else "顯示日期", o.showDate, "frameDate") { set(o.copy(showDate = !o.showDate)) }
                Chip(if (o.showInfo) "顯示拍攝資訊 ✓" else "顯示拍攝資訊", o.showInfo) { set(o.copy(showInfo = !o.showInfo)) }
            }
            Label("輸出比例（照片不會被拉伸：Fit 加留白、Fill 裁切照片）")
            ChipRow { for ((r, name) in RATIOS) Chip(name, o.ratio == r, "frameRatio-$name") { set(o.copy(ratio = r)) } }
            if (o.ratio > 0f) {
                ChipRow { for (m in FitMode.values()) Chip(m.zh, o.mode == m) { set(o.copy(mode = m)) } }
                if (o.mode == FitMode.FIT) { Slide("左右位置", o.offsetX, -1f..1f, "%.1f".format(o.offsetX)) { set(o.copy(offsetX = it)) }
                    Slide("上下位置", o.offsetY, -1f..1f, "%.1f".format(o.offsetY)) { set(o.copy(offsetY = it)) } }
            }
        }
        Label("照片裁切（Crop）")
        ChipRow { for ((r, name) in listOf(0f to "不裁切") + RATIOS.drop(1)) Chip(name, o.photoAspect == r) { set(o.copy(photoAspect = r)) } }
        Box(Modifier.height(8.dp))
    }
}

/** Frame library: every frame shown on a real photo that suits it. */
@Composable
fun FramesScreen(app: AppViewModel) {
    androidx.activity.compose.BackHandler { app.back() }
    val settings by app.settings.collectAsState()
    val favs = Browse.csvList(settings.frameFavs); val recents = Browse.csvList(settings.frameRecents)
    var tab by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var big by remember { mutableStateOf<FrameDef?>(null) }
    val list = remember(tab, query, settings.frameFavs, settings.frameRecents) { framesFor(tab, favs, recents, query) }
    LaunchedEffect(Unit) { app.loadFrameDemos() }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(top = 28.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Color.White, fontSize = 26.sp, modifier = Modifier.clickable { app.back() }.padding(end = 12.dp))
            Text("Frame Studio 邊框圖庫", color = Color.White, fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("${list.size} 款", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.testTag("frameCount"))
        }
        FrameCategoryBar(tab, favs.size) { tab = it }
        Field(query, "搜尋邊框", "frameLibSearch") { query = it }
        Text("在相簿開啟照片 → 編輯 → 邊框，即可套用並匯出。", color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 14.dp))
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { f ->
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E)).clickable { big = f }.padding(6.dp).testTag("lib-${f.id}"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                        app.frameDemos[f.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, f.zh, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        if (f.id in favs) Text("★", color = HikariColors.Accent, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopEnd))
                    }
                    Text(f.zh, color = Color.White, fontSize = 12.sp, maxLines = 1)
                    Text(f.name, color = HikariColors.Dim, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
    big?.let { f ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { big = null },
            title = { Text("${f.zh} · ${f.name}") },
            text = { Column { app.frameDemos[f.id]?.let { b -> Image(remember(b) { b.asImageBitmap() }, f.zh, Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit) }
                Text("${f.cat.zh}　關鍵字：${f.keywords}", color = HikariColors.Dim, fontSize = 11.sp) } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { app.updateSettings { it.copy(frameFavs = Browse.csvToggle(it.frameFavs, f.id)) } }) { Text(if (f.id in favs) "取消收藏" else "加入收藏") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { big = null }) { Text("關閉") } })
    }
}
