package com.hikari.cam.lab

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.cam.data.Media
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.engine.LookRepository
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Filter Lab: one photo, the shipped looks, live parameters, full-resolution export and a
 * preview-vs-export check. Preview, thumbnails, export and the check all go through the same
 * [OffscreenRenderer] (same shaders, same LUTs); only the output size differs.
 */
class LabViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LookRepository(app)
    private val renderer = OffscreenRenderer(app.assets)
    val looks: List<FilterSpec> = repo.looks

    class Source(
        val id: Int,
        val label: String,
        /** null = the built-in test chart */
        val uri: Uri?,
        val fullW: Int,
        val fullH: Int,
        /** decoded at up to [PREVIEW_SRC] px, the preview's input */
        val previewSrc: Bitmap,
        /** the original at exactly the preview's output size, for comparing */
        val original: Bitmap,
        val thumbSrc: Bitmap,
    ) {
        val outW: Int get() = original.width
        val outH: Int get() = original.height
    }

    var source by mutableStateOf<Source?>(null); private set
    var lookIndex by mutableIntStateOf(0); private set
    private val adjustByLook = mutableStateMapOf<String, Adjustments>()
    val look: FilterSpec get() = looks[lookIndex]
    val adjust: Adjustments get() = adjustByLook[look.id] ?: Adjustments.DEFAULT

    var preview by mutableStateOf<Bitmap?>(null); private set
    val thumbs = mutableStateMapOf<String, Bitmap>()
    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)

    // performance numbers shown in the Lab
    var gpu by mutableStateOf(""); private set
    var lastPreview by mutableStateOf<OffscreenRenderer.Timing?>(null); private set
    var lastPreviewWallMs by mutableStateOf(0.0); private set
    private val previewHistory = ArrayDeque<Double>()
    var previewAvgMs by mutableStateOf(0.0); private set
    var previewCount by mutableIntStateOf(0); private set
    var thumbsMs by mutableStateOf(0.0); private set
    var decodeMs by mutableStateOf(0.0); private set
    var export by mutableStateOf<ExportReport?>(null); private set
    var check by mutableStateOf<CheckReport?>(null); private set
    val log = mutableStateListOf<String>()

    class ExportReport(
        val w: Int, val h: Int, val downscaled: Boolean,
        val decodeMs: Double, val render: OffscreenRenderer.Timing, val encodeMs: Double, val totalMs: Double,
        val heapPeakMb: Long, val heapMaxMb: Long, val uri: Uri?,
        /** the export scaled to the preview's size vs the preview itself (grain on) */
        val vsPreview: ImageStats.Result?,
        val look: String,
    )

    class CheckReport(val look: String, val fullW: Int, val fullH: Int, val prevW: Int, val prevH: Int,
                      val result: ImageStats.Result, val ms: Double)

    private data class PreviewReq(val sourceId: Int, val lookId: String, val adjust: Adjustments)

    private val requests = MutableStateFlow<PreviewReq?>(null)
    private var nextSourceId = 1

    init {
        // StateFlow is conflated: while one preview renders, slider moves collapse into the latest
        viewModelScope.launch { requests.filterNotNull().collect { renderPreview(it) } }
        viewModelScope.launch {
            renderer.maxTexture()
            gpu = renderer.gpuName
            loadChart()
        }
    }

    // ---------------------------------------------------------------- sources

    fun loadChart() = viewModelScope.launch {
        busy = "產生測試圖卡…"
        try {
            val (prev, thumb) = withContext(Dispatchers.Default) {
                val pw = PREVIEW_SRC
                val ph = pw * TestChart.FULL_H / TestChart.FULL_W
                TestChart.draw(pw, ph) to TestChart.draw(THUMB, THUMB * TestChart.FULL_H / TestChart.FULL_W)
            }
            setSource("內建測試圖卡", null, TestChart.FULL_W, TestChart.FULL_H, prev, thumb)
        } finally {
            busy = null
        }
    }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        busy = "讀取照片…"
        try {
            val ctx = getApplication<Application>()
            val t0 = System.nanoTime()
            val d = Media.decode(ctx, Media.source(ctx, uri), PREVIEW_SRC)
            decodeMs = ms(t0)
            val thumb = withContext(Dispatchers.Default) { fit(d.bitmap, THUMB) }
            setSource(uri.lastPathSegment ?: "照片", uri, d.fullWidth, d.fullHeight, d.bitmap, thumb)
        } catch (e: Throwable) {
            message = "無法讀取：${e.message}"
        } finally {
            busy = null
        }
    }

    private suspend fun setSource(label: String, uri: Uri?, fullW: Int, fullH: Int, prev: Bitmap, thumb: Bitmap) {
        val old = source
        val original = withContext(Dispatchers.Default) {
            val long = maxOf(prev.width, prev.height)
            val target = minOf(PREVIEW_OUT, long)
            if (target == long) prev else fit(prev, target)
        }
        val s = Source(nextSourceId++, label, uri, fullW, fullH, prev, original, thumb)
        source = s
        check = null
        export = null
        if (old != null) {
            renderer.forget("p${old.id}")
            renderer.forget("t${old.id}")
        }
        log.add(0, "載入 $label：原始 ${fullW}×${fullH}，預覽 ${s.outW}×${s.outH}")
        renderThumbs(s)
        refresh()
    }

    // ---------------------------------------------------------------- looks and parameters

    fun selectLook(i: Int) {
        lookIndex = i
        refresh()
    }

    fun setAdjust(a: Adjustments) {
        adjustByLook[look.id] = a
        refresh()
    }

    fun resetAdjust() {
        adjustByLook.remove(look.id)
        refresh()
    }

    private fun refresh() {
        val s = source ?: return
        requests.value = PreviewReq(s.id, look.id, adjust)
    }

    private fun request(spec: FilterSpec, a: Adjustments) =
        OffscreenRenderer.Request(spec, repo.lut(spec), a, seed = SEED)

    private suspend fun renderPreview(r: PreviewReq) {
        val s = source ?: return
        if (s.id != r.sourceId) return
        val spec = looks.first { it.id == r.lookId }
        val req = withContext(Dispatchers.IO) { request(spec, r.adjust) }
        val t0 = System.nanoTime()
        val bmp = renderer.render(s.previewSrc, req, s.outW, s.outH, cacheKey = "p${s.id}")
        val wall = ms(t0)
        if (source?.id != s.id) return
        preview = bmp
        lastPreview = renderer.lastTiming
        lastPreviewWallMs = wall
        previewHistory.addLast(wall)
        while (previewHistory.size > 20) previewHistory.removeFirst()
        previewAvgMs = previewHistory.average()
        previewCount++
    }

    private suspend fun renderThumbs(s: Source) {
        val t0 = System.nanoTime()
        for (spec in looks) {
            val req = withContext(Dispatchers.IO) { request(spec, Adjustments.DEFAULT) }
            thumbs[spec.id] = renderer.render(s.thumbSrc, req, s.thumbSrc.width, s.thumbSrc.height, cacheKey = "t${s.id}")
        }
        thumbsMs = ms(t0)
    }

    // ---------------------------------------------------------------- full resolution

    /** Decodes the source at full size (or draws the chart at full size). */
    private suspend fun full(s: Source): Media.Decoded {
        val ctx = getApplication<Application>()
        return if (s.uri == null) {
            withContext(Dispatchers.Default) {
                Media.Decoded(TestChart.draw(TestChart.FULL_W, TestChart.FULL_H), TestChart.FULL_W, TestChart.FULL_H, false)
            }
        } else {
            Media.decode(ctx, Media.source(ctx, s.uri), renderer.maxTexture())
        }
    }

    fun exportFull() = viewModelScope.launch {
        val s = source ?: return@launch
        if (busy != null) return@launch
        val spec = look
        val a = adjust
        val ctx = getApplication<Application>()
        val rt = Runtime.getRuntime()
        var peak = 0L
        fun sample() { peak = maxOf(peak, rt.totalMemory() - rt.freeMemory()) }
        try {
            val t0 = System.nanoTime()
            busy = "解碼原圖（完整解析度）…"
            val d = full(s)
            val decode = ms(t0)
            sample()
            busy = "GPU 渲染 ${d.bitmap.width}×${d.bitmap.height}…"
            val w = d.bitmap.width
            val h = d.bitmap.height
            val req = withContext(Dispatchers.IO) { request(spec, a) }
            val out = renderer.render(d.bitmap, req, w, h, releaseSource = true)
            val timing = renderer.lastTiming
            sample()
            busy = "寫入 JPEG…"
            val t2 = System.nanoTime()
            val uri = Media.saveJpeg(ctx, out, 95, "_" + spec.id)
            s.uri?.let { Media.copyExif(ctx, it, null, uri) }
            val encode = ms(t2)
            busy = "比對匯出與預覽…"
            val prev = preview
            val vs = withContext(Dispatchers.Default) {
                val small = ImageStats.downscale(out, s.outW, s.outH)
                sample()
                out.recycle()
                val r = if (prev != null && prev.width == small.width && prev.height == small.height &&
                    adjust == a && look.id == spec.id) ImageStats.compare(prev, small) else null
                small.recycle()
                r
            }
            val total = ms(t0)
            export = ExportReport(w, h, d.downscaled, decode, timing, encode, total,
                peak shr 20, rt.maxMemory() shr 20, uri, vs, spec.name)
            log.add(0, "匯出 ${spec.name} ${w}×${h}：總計 ${total.roundToInt()} ms（渲染 ${timing.totalMs.roundToInt()} ms）→ 相簿 Pictures/Hikari")
            message = "已儲存 ${w}×${h} 到 Pictures/Hikari"
        } catch (e: Throwable) {
            message = "匯出失敗：${e.message}"
            log.add(0, "匯出失敗：$e")
        } finally {
            busy = null
        }
    }

    /**
     * Strict check: the full-resolution render (the export path) scaled down must equal the preview
     * render. Grain is turned off for both because grain is per-pixel randomness; colour, tone,
     * halation, bloom, vignette and lens effects are all compared.
     */
    fun runCheck() = viewModelScope.launch {
        val s = source ?: return@launch
        if (busy != null) return@launch
        val spec = look
        val a = adjust.copy(grain = 0f)
        try {
            val t0 = System.nanoTime()
            busy = "一致性檢查：預覽尺寸渲染…"
            val req = withContext(Dispatchers.IO) { request(spec, a) }
            val small = renderer.render(s.previewSrc, req, s.outW, s.outH, cacheKey = "p${s.id}")
            busy = "一致性檢查：完整解析度渲染…"
            val d = full(s)
            val w = d.bitmap.width
            val h = d.bitmap.height
            val big = renderer.render(d.bitmap, req, w, h, releaseSource = true)
            busy = "一致性檢查：比對…"
            val r = withContext(Dispatchers.Default) {
                val down = ImageStats.downscale(big, s.outW, s.outH)
                big.recycle()
                ImageStats.compare(small, down).also { down.recycle() }
            }
            check = CheckReport(spec.name, w, h, s.outW, s.outH, r, ms(t0))
            log.add(0, "一致性 ${spec.name}：平均 ${"%.2f".format(r.meanAbs)}、99% ${r.p99}、最大 ${r.max} 級 → ${if (r.pass) "通過" else "未通過"}")
        } catch (e: Throwable) {
            message = "檢查失敗：${e.message}"
        } finally {
            busy = null
        }
    }

    companion object {
        const val PREVIEW_SRC = 2048
        const val PREVIEW_OUT = 1600
        const val THUMB = 240
        const val SEED = 7f

        private fun ms(t0: Long) = (System.nanoTime() - t0) / 1e6

        fun fit(b: Bitmap, longSide: Int): Bitmap {
            val s = longSide.toFloat() / maxOf(b.width, b.height)
            val w = maxOf(1, (b.width * s).roundToInt())
            val h = maxOf(1, (b.height * s).roundToInt())
            return ImageStats.downscale(b, w, h)
        }
    }
}
