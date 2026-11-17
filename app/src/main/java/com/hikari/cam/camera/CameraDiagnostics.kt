package com.hikari.cam.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import kotlin.math.sqrt

/**
 * One camera as the phone describes it: a logical camera, a physical lens behind one, or an id the
 * system does not list. Read with CameraManager.getCameraCharacteristics, nothing is opened.
 */
data class LensDetail(
    val id: String,
    /** the logical camera this lens sits behind, null for a top-level camera */
    val parent: String?,
    /** false for ids found only by probing (vendors hide aux cameras from apps outside a whitelist) */
    val listed: Boolean,
    val facing: String,
    val logical: Boolean,
    val physicalIds: List<String>,
    val focalMm: List<Float>,
    /** 35 mm-equivalent of the first focal length */
    val focal35: Float?,
    val apertures: List<Float>,
    val sensorMm: Pair<Float, Float>?,
    val pixels: Pair<Int, Int>?,
    val zoomMin: Float?,
    val zoomMax: Float?,
    val maxDigital: Float?,
    val raw: Boolean,
    val level: String,
    val error: String? = null,
)

object CameraDiagnostics {
    /**
     * Everything reachable by Camera2: every listed camera and the physical lenses behind it, and, when
     * [deep], ids 0..[deepMax] the system does not list (some vendors keep the telephoto there).
     */
    fun scan(context: Context, deep: Boolean, deepMax: Int = 99): List<LensDetail> {
        val m = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val out = LinkedHashMap<String, LensDetail>()
        val listed = runCatching { m.cameraIdList.toList() }.getOrDefault(emptyList())
        for (id in listed) {
            val d = read(m, id, null, true)
            out[id] = d
            for (pid in d.physicalIds) if (pid !in out) out[pid] = read(m, pid, id, pid in listed)
        }
        if (deep) for (i in 0..deepMax) {
            val id = "$i"
            if (id in out) continue
            val c = runCatching { m.getCameraCharacteristics(id) }.getOrNull() ?: continue
            out[id] = describe(id, null, false, c)
        }
        return out.values.toList()
    }

    private fun read(m: CameraManager, id: String, parent: String?, listed: Boolean): LensDetail =
        runCatching { describe(id, parent, listed, m.getCameraCharacteristics(id)) }
            .getOrElse { LensDetail(id, parent, listed, "?", false, emptyList(), emptyList(), null, emptyList(), null, null, null, null, null, false, "?", error = it.javaClass.simpleName + (it.message?.let { m -> ": $m" } ?: "")) }

    private fun describe(id: String, parent: String?, listed: Boolean, c: CameraCharacteristics): LensDetail {
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val px = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val zoom = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
        return LensDetail(
            id = id, parent = parent, listed = listed,
            facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "後"
                CameraCharacteristics.LENS_FACING_FRONT -> "前"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "外接"
                else -> "?"
            },
            logical = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in caps,
            physicalIds = c.physicalCameraIds.toList().sorted(),
            focalMm = focals,
            focal35 = if (focals.isNotEmpty() && size != null) focals[0] * 43.27f / sqrt(size.width * size.width + size.height * size.height) else null,
            apertures = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.toList().orEmpty(),
            sensorMm = size?.let { it.width to it.height },
            pixels = px?.let { it.width to it.height },
            zoomMin = zoom?.lower, zoomMax = zoom?.upper,
            maxDigital = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM),
            raw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps,
            level = when (c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "?"
            },
        )
    }

    /** A back lens that is clearly longer than the main one: the telephoto, by how the phone reaches it. */
    data class Tele(val lens: LensDetail, val ratio: Float, val route: String)

    /** The lenses longer than 1.5x the main lens, and how each can be reached (pure, tested). */
    fun telephotos(all: List<LensDetail>): List<Tele> {
        val back = all.filter { it.facing == "後" && it.error == null && it.focal35 != null }
        val main = back.firstOrNull { it.parent == null && it.listed }?.focal35 ?: back.firstOrNull()?.focal35 ?: return emptyList()
        return back.filter { it.focal35!! / main >= 1.5f }.map {
            Tele(it, it.focal35!! / main, when {
                !it.listed -> "系統未列出（只能用 Camera2 直接開，實驗）"
                it.parent != null -> "多鏡頭 #${it.parent} 裡的實體鏡頭"
                else -> "系統開放的獨立相機"
            })
        }.sortedBy { it.ratio }
    }

    private fun f1(v: Float) = "%.1f".format(v)

    /** Compact text for a screenshot: one block per camera, indented lenses, then a verdict on the telephoto. */
    fun format(all: List<LensDetail>): String = buildString {
        val back = all.filter { it.facing == "後" && it.error == null }
        append("裝置：${Build.MANUFACTURER} ${Build.MODEL}（Android ${Build.VERSION.RELEASE}）\n")
        append("系統列出 ${all.count { it.listed && it.parent == null }} 顆相機、實體鏡頭 ${all.count { it.parent != null }} 顆")
        val hidden = all.count { !it.listed }
        if (hidden > 0) append("、隱藏 ID $hidden 顆")
        append("\n")
        val tops = all.filter { it.parent == null }
        for (d in tops) {
            lens(d, "")
            for (p in all.filter { it.parent == d.id }) lens(p, "   └ ")
        }
        // physical lenses whose parent was not in the scan
        for (p in all.filter { it.parent != null && tops.none { t -> t.id == it.parent } }) lens(p, "   └ ")
        append("\n【長焦判讀】")
        val tele = telephotos(all)
        if (tele.isEmpty()) {
            append("沒有找到比主鏡長 1.5 倍以上的鏡頭")
            if (back.isNotEmpty() && all.none { !it.listed }) append("（還沒做深度掃描 ID 0–99）")
            append("。這支手機沒有把長焦開放給第三方 App，2× 以上只能數位裁切。")
        } else for (t in tele) {
            append("\n  #${t.lens.id} 約 ${f1(t.ratio)}×（${t.lens.focal35?.let { Math.round(it) }}mm）— ${t.route}")
        }
    }

    private fun StringBuilder.lens(d: LensDetail, indent: String) {
        append("\n$indent#${d.id} ${d.facing}")
        if (d.logical) append(" 多鏡頭(${d.physicalIds.joinToString("、") { "#$it" }})")
        if (!d.listed) append(" 未列出")
        if (d.error != null) { append("  讀取失敗：${d.error}\n"); return }
        d.focal35?.let { append(" ${Math.round(it)}mm") }
        append("\n$indent  焦距 ${d.focalMm.joinToString("/") { f1(it) }}mm")
        if (d.apertures.isNotEmpty()) append("  光圈 f/${d.apertures.joinToString("/") { f1(it) }}")
        d.sensorMm?.let { append("  感光元件 ${"%.2f".format(it.first)}×${"%.2f".format(it.second)}mm") }
        d.pixels?.let { append("  ${"%.1f".format(it.first * it.second / 1e6)}MP") }
        append("\n$indent  變焦 ")
        append(if (d.zoomMin != null && d.zoomMax != null) "${f1(d.zoomMin)}–${f1(d.zoomMax)}×" else "無 ZoomRatio")
        d.maxDigital?.let { append("  數位上限 ${f1(it)}×") }
        append("  RAW ${if (d.raw) "支援" else "不支援"}  ${d.level}\n")
    }
}
