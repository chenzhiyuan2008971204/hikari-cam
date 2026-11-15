package com.hikari.cam.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.cam.HikariApp
import com.hikari.cam.data.Media
import com.hikari.cam.lab.LabViewModel
import com.hikari.data.Edit
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Non-destructive photo editor. The original is only ever read; the recipe (look + adjustments)
 * lives in the database, and exports are new files rendered from the full-size original.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val hikari = app as HikariApp
    private val renderer = hikari.renderer
    val looks: List<FilterSpec> = hikari.looks.looks

    var uri by mutableStateOf<Uri?>(null); private set
    var fullW by mutableStateOf(0); private set
    var fullH by mutableStateOf(0); private set
    private var previewSrc: Bitmap? = null
    var original by mutableStateOf<Bitmap?>(null); private set
    var preview by mutableStateOf<Bitmap?>(null); private set
    val thumbs = mutableStateMapOf<String, Bitmap>()

    var look by mutableStateOf(looks.first()); private set
    var adjust by mutableStateOf(Adjustments.DEFAULT); private set
    private val undo = ArrayDeque<Pair<String, Adjustments>>()
    private val redo = ArrayDeque<Pair<String, Adjustments>>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var lastRenderMs by mutableStateOf(0.0); private set

    // smart analysis of the opened photo: measured scene, ranked looks, composition findings
    var recs by mutableStateOf<List<com.hikari.vision.Recommendation>>(emptyList()); private set
    var sceneLabel by mutableStateOf(""); private set
    var report by mutableStateOf<com.hikari.vision.CompositionReport?>(null); private set
    var analysing by mutableStateOf(false); private set
    var analysisMs by mutableStateOf(0.0); private set
    var detectorNote by mutableStateOf(""); private set

    /** Runs on the unfiltered photo, off the main thread; the preview keeps working meanwhile. */
    private fun analyse(src: Bitmap) = viewModelScope.launch {
        analysing = true
        try {
            val t0 = System.nanoTime()
            val det = com.hikari.cam.smart.Smart.detect(src)
            val (scene, rep) = withContext(Dispatchers.Default) {
                val f = com.hikari.cam.smart.Smart.frame(src)
                val r = com.hikari.vision.CompositionAnalyzer.analyze(f, det)
                r.findings.scene to r
            }
            recs = com.hikari.vision.FilterRecommender.rank(scene, com.hikari.cam.smart.Smart.traits(looks)).take(3)
            sceneLabel = scene.top.take(3).joinToString("・") { it.zh }.ifEmpty { "一般場景" }
            report = rep
            detectorNote = "偵測到 ${det.count { it.kind == com.hikari.vision.Detection.Kind.FACE }} 張臉、${det.count { it.kind != com.hikari.vision.Detection.Kind.FACE }} 個物體"
            analysisMs = (System.nanoTime() - t0) / 1e6
        } catch (e: Throwable) {
            report = null; recs = emptyList(); detectorNote = "分析失敗：${e.message}"
        } finally { analysing = false }
    }

    private data class Req(val key: String, val look: String, val adjust: Adjustments)
    private val requests = MutableStateFlow<Req?>(null)
    private var cacheKey = "ed0"

    init {
        viewModelScope.launch { requests.filterNotNull().collect { render(it) } }
    }

    fun open(u: Uri, lookId: String?) {
        if (u == uri) return
        uri = u
        viewModelScope.launch {
            busy = "讀取照片…"
            try {
                val ctx = getApplication<Application>()
                // full-size decode, then our own downscale: the preview starts from the same pixels as the export
                val d = Media.decode(ctx, Media.source(ctx, u), renderer.maxTexture(), copies = 2)
                fullW = d.fullWidth
                fullH = d.fullHeight
                val (prev, orig, thumb) = withContext(Dispatchers.Default) {
                    val p = if (maxOf(d.bitmap.width, d.bitmap.height) > LabViewModel.PREVIEW_SRC) LabViewModel.fit(d.bitmap, LabViewModel.PREVIEW_SRC) else d.bitmap
                    if (p !== d.bitmap) d.bitmap.recycle()
                    val o = if (maxOf(p.width, p.height) > LabViewModel.PREVIEW_OUT) LabViewModel.fit(p, LabViewModel.PREVIEW_OUT) else p
                    Triple(p, o, LabViewModel.fit(p, 320))
                }
                renderer.forget(cacheKey)
                cacheKey = "ed${System.nanoTime()}"
                previewSrc = prev
                original = orig
                val saved = hikari.db.edits().get(u.toString())
                look = hikari.looks.look(saved?.lookId ?: lookId ?: look.id)
                adjust = Adjustments.fromJson(saved?.adjustJson)
                undo.clear(); redo.clear(); syncHistory()
                refresh()
                recs = emptyList(); report = null
                analyse(orig)
                for (spec in looks) {
                    val req = withContext(Dispatchers.IO) { OffscreenRenderer.Request(spec, hikari.looks.lut(spec), Adjustments.DEFAULT, seed = SEED) }
                    thumbs[spec.id] = renderer.render(thumb, req, thumb.width, thumb.height, cacheKey = "$cacheKey-t")
                }
                renderer.forget("$cacheKey-t")
            } catch (e: Throwable) {
                message = "無法開啟：${e.message}"
            } finally {
                busy = null
            }
        }
    }

    private fun refresh() {
        if (previewSrc == null) return
        requests.value = Req(cacheKey, look.id, adjust)
    }

    private suspend fun render(r: Req) {
        val src = previewSrc ?: return
        val o = original ?: return
        val spec = hikari.looks.look(r.look)
        val req = withContext(Dispatchers.IO) { OffscreenRenderer.Request(spec, hikari.looks.lut(spec), r.adjust, seed = SEED) }
        val t0 = System.nanoTime()
        val out = renderer.render(src, req, o.width, o.height, cacheKey = r.key)
        lastRenderMs = (System.nanoTime() - t0) / 1e6
        preview = out
    }

    private fun pushUndo() {
        undo.addLast(look.id to adjust)
        while (undo.size > 50) undo.removeFirst()
        redo.clear()
        syncHistory()
    }

    private fun syncHistory() { canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty() }

    fun selectLook(spec: FilterSpec) {
        if (spec.id == look.id) return
        pushUndo()
        look = spec
        // look-specific overrides (grain, halation...) belong to the old look; keep the global edits
        adjust = adjust.copy(grain = null, grainSize = null, halation = null, bloom = null, vignette = null,
            aberration = null, softFocus = null, mist = null, edgeSoft = null, dust = null, scratches = null,
            leak = null, uneven = null, drift = null)
        refresh(); persist()
    }

    private var dragStart: Adjustments? = null

    /** slider moves: preview only; history and storage happen in [commit] */
    fun updateAdjust(a: Adjustments) {
        if (dragStart == null) dragStart = adjust
        adjust = a
        refresh()
    }

    fun commit() {
        val start = dragStart ?: return
        dragStart = null
        if (start != adjust) {
            undo.addLast(look.id to start)
            redo.clear(); syncHistory()
            persist()
        }
    }

    fun apply(lookId: String, a: Adjustments) {
        pushUndo()
        look = hikari.looks.look(lookId)
        adjust = a
        refresh(); persist()
    }

    fun reset() = apply(look.id, Adjustments.DEFAULT)

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(look.id to adjust)
        look = hikari.looks.look(prev.first); adjust = prev.second
        syncHistory(); refresh(); persist()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(look.id to adjust)
        look = hikari.looks.look(next.first); adjust = next.second
        syncHistory(); refresh(); persist()
    }

    private fun persist(exportUri: String? = null) {
        val u = uri ?: return
        val l = look.id
        val a = adjust.toJson()
        viewModelScope.launch {
            val prev = hikari.db.edits().get(u.toString())
            hikari.db.edits().put(Edit(u.toString(), l, a, System.currentTimeMillis(), exportUri ?: prev?.exportUri))
        }
    }

    /** Renders the full-size original through the current recipe into a new JPEG. */
    fun export(quality: Int) = viewModelScope.launch {
        val u = uri ?: return@launch
        if (busy != null) return@launch
        val spec = look
        val a = adjust
        try {
            val ctx = getApplication<Application>()
            busy = "解碼原圖…"
            System.gc()
            val d = Media.decode(ctx, Media.source(ctx, u), renderer.maxTexture(), copies = 1)
            val w = d.bitmap.width
            val h = d.bitmap.height
            busy = "完整解析度渲染 ${w}×${h}…"
            val req = withContext(Dispatchers.IO) { OffscreenRenderer.Request(spec, hikari.looks.lut(spec), a, seed = SEED) }
            val out = renderer.render(d.bitmap, req, w, h, releaseSource = true)
            busy = "儲存…"
            val saved = Media.saveJpeg(ctx, out, quality, "_" + spec.id)
            Media.copyExif(ctx, u, null, saved)
            out.recycle()
            persist(saved.toString())
            message = "已儲存 ${w}×${h}${if (d.downscaled) "（記憶體不足，已縮小）" else ""} 到 Pictures/Hikari"
        } catch (e: Throwable) {
            message = "匯出失敗：${e.message}"
        } finally {
            busy = null
        }
    }

    companion object {
        const val SEED = 7f
    }
}
