package com.hikari.cam.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.cam.recipe.RecipeLogic
import com.hikari.data.FilmRecipe
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.studio.FrameOptions
import com.hikari.studio.Frames
import com.hikari.studio.StampSpec

/**
 * The camera's recipe switch: 預設 (the camera's own settings) and C1..C7. Tap a recipe to use it (look,
 * strength, exposure, grain / halation / vignette and the rest are applied to the live preview at once);
 * tap an empty slot or long-press a recipe to save the current camera state into it or edit it.
 * A "*" after the name means the look or an adjustment was changed by hand after the recipe was applied.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipeBar(app: AppViewModel, look: FilterSpec, adjust: Adjustments) {
    val recipes by app.recipes.collectAsState()
    val settings by app.settings.collectAsState()
    var edit by remember { mutableStateOf<Int?>(null) }
    LazyRow(Modifier.fillMaxWidth().testTag("recipeBar"), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        item(key = "r0") { RecipeChip("預設", settings.recipeSlot == 0, "recipe-0", onClick = { app.applyRecipe(0) }, onLong = null) }
        items((1..RecipeLogic.SLOTS).toList(), key = { "r$it" }) { slot ->
            val r = recipes.firstOrNull { it.slot == slot }
            val on = settings.recipeSlot == slot && r != null
            val mod = on && RecipeLogic.modified(r!!, app.look(r.lookId).id, adjust)
            RecipeChip(
                if (r == null) "${RecipeLogic.label(slot)} ＋" else RecipeLogic.title(r) + if (mod) " *" else "",
                on, "recipe-$slot", empty = r == null,
                onClick = { if (r == null) edit = slot else app.applyRecipe(slot) },
                onLong = { edit = slot },
            )
        }
    }
    edit?.let { slot -> RecipeDialog(app, look, adjust, settings.stampSpec, "", slot) { edit = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecipeChip(text: String, on: Boolean, tag: String, empty: Boolean = false, onClick: () -> Unit, onLong: (() -> Unit)?) {
    Text(text, color = if (on) Color.Black else if (empty) Color(0xFF8A8A8A) else Color.White, fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) HikariColors.Accent else Color(0xFF242424))
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 10.dp, vertical = 5.dp).testTag(tag))
}

/**
 * Save the given look and adjustments as a recipe, or edit / delete one that exists.
 * [stampSpec] and [frameOptions] are the encoded studio settings to start from ("" = none).
 * [slot] null = choose the slot in the dialog.
 */
@Composable
fun RecipeDialog(app: AppViewModel, look: FilterSpec, adjust: Adjustments, stampSpec: String, frameOptions: String, slot: Int?, onClose: () -> Unit) {
    val recipes by app.recipes.collectAsState()
    var pick by remember { mutableStateOf(slot ?: (1..RecipeLogic.SLOTS).firstOrNull { s -> recipes.none { it.slot == s } } ?: 1) }
    val existing = recipes.firstOrNull { it.slot == pick }
    // fields start from the recipe being edited, else from the state handed in
    var name by remember(pick) { mutableStateOf(existing?.name ?: "") }
    var stampOn by remember(pick) { mutableStateOf(existing?.stampOn ?: StampSpec.decode(stampSpec).enabled) }
    var frameId by remember(pick) { mutableStateOf(existing?.let { FrameOptions.decode(it.frameOptions).frame } ?: FrameOptions.decode(frameOptions).frame) }
    var frameOn by remember(pick) { mutableStateOf(existing?.frameOn ?: FrameOptions.decode(frameOptions).frame.isNotEmpty()) }
    var clean by remember(pick) { mutableStateOf(existing?.saveClean ?: false) }
    var frameMenu by remember { mutableStateOf(false) }
    // editing an existing recipe from the camera keeps its own look and adjustments unless "update" is chosen
    var useCurrent by remember(pick) { mutableStateOf(existing == null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (existing == null) "存成相機配方" else "編輯 ${RecipeLogic.title(existing)}") },
        text = {
            Column(Modifier.testTag("recipeDialog")) {
                if (slot == null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    for (s in 1..RecipeLogic.SLOTS) Chip(RecipeLogic.label(s) + if (recipes.any { it.slot == s }) "•" else "", pick == s, "recipeSlot-$s") { pick = s }
                }
                Text("濾鏡：${if (useCurrent || existing == null) look.name else app.look(existing.lookId).name}", color = Color.White, fontSize = 13.sp)
                if (existing != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("改用相機目前的濾鏡與調整", color = HikariColors.Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(useCurrent, { useCurrent = it })
                }
                Box2 {
                    BasicTextField(name, { name = it.take(24) }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(HikariColors.Accent), modifier = Modifier.fillMaxWidth().testTag("recipeName"))
                    if (name.isEmpty()) Text("名稱，例如 街拍負片", color = Color(0xFF6A6A6A), fontSize = 14.sp)
                }
                SwitchRow("加時間戳", stampOn, "recipeStamp") { stampOn = it }
                SwitchRow("加邊框", frameOn, "recipeFrame") { frameOn = it }
                if (frameOn) {
                    TextButton({ frameMenu = true }, modifier = Modifier.testTag("recipeFramePick")) {
                        Text("邊框：${Frames.get(frameId)?.zh ?: "選擇邊框"}", fontSize = 13.sp)
                    }
                    androidx.compose.material3.DropdownMenu(frameMenu, { frameMenu = false }) {
                        for (f in Frames.all) androidx.compose.material3.DropdownMenuItem(text = { Text(f.zh) }, onClick = { frameId = f.id; frameMenu = false })
                    }
                }
                SwitchRow("另存只有濾鏡的乾淨版", clean, "recipeClean") { clean = it }
                Text("快門：" + RecipeLogic.pipeline(RecipeLogic.make(pick, name, look.id, adjust, stampOn, "", frameOn && frameId.isNotEmpty(), "", clean, 0)).joinToString(" → "),
                    color = HikariColors.Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val baseLook = if (useCurrent || existing == null) look.id else existing.lookId
                val baseAdjust = if (useCurrent || existing == null) adjust else RecipeLogic.adjustments(existing)
                val studioStamp = (if (existing != null && existing.stampSpec.isNotEmpty()) existing.stampSpec else stampSpec)
                val frame = (if (existing != null) FrameOptions.decode(existing.frameOptions) else FrameOptions.decode(frameOptions)).copy(frame = frameId)
                app.saveRecipe(RecipeLogic.make(pick, name, baseLook, baseAdjust, stampOn, StampSpec.decode(studioStamp).copy(enabled = stampOn).encode(),
                    frameOn && frameId.isNotEmpty(), frame.encode(), clean, System.currentTimeMillis()))
                onClose()
            }, modifier = Modifier.testTag("recipeSave")) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton({ app.deleteRecipe(pick); onClose() }, modifier = Modifier.testTag("recipeDelete")) { Text("刪除", color = Color(0xFFFF6B6B)) }
                TextButton(onClose) { Text("取消") }
            }
        },
    )
}

@Composable
private fun Box2(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E1E)).padding(horizontal = 10.dp, vertical = 8.dp)) { content() }
}

@Composable
private fun SwitchRow(label: String, on: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(on, onChange, modifier = Modifier.testTag(tag))
    }
}
