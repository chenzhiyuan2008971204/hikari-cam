package com.hikari.cam.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlin.math.sqrt

/**
 * Every way this phone might let an app reach a lens, found through the Camera2 API directly:
 *  - LISTED: a camera id the system lists (CameraX can bind it)
 *  - PHYSICAL: a physical lens behind a listed logical camera (streamed by physical id)
 *  - HIDDEN: an id the system does not list but still describes (vendor-restricted aux cameras);
 *    opened with plain Camera2, experimental
 */
data class LensOption(
    val cameraId: String,
    val physicalId: String?,
    val kind: Kind,
    val focal35: Float?,
    val logical: Boolean,
) {
    enum class Kind { LISTED, PHYSICAL, HIDDEN }

    val key get() = "${kind.name}:$cameraId:${physicalId ?: ""}"

    /** e.g. "長焦 75mm" relative to the main lens */
    fun label(mainFocal: Float?): String {
        val f = focal35
        val role = when {
            f == null || mainFocal == null -> "鏡頭"
            f < mainFocal * 0.8f -> "超廣"
            f > mainFocal * 1.6f -> "長焦"
            else -> "主鏡"
        }
        return role + (f?.let { " ${Math.round(it)}mm" } ?: "")
    }

    val source get() = when (kind) {
        Kind.LISTED -> "系統開放"
        Kind.PHYSICAL -> "實體鏡頭"
        Kind.HIDDEN -> "隱藏鏡頭・實驗"
    }
}

object LensProbe {
    fun focal35(c: CameraCharacteristics): Float? {
        val f = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return null
        val s = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        return f * 43.27f / sqrt(s.width * s.width + s.height * s.height)
    }

    private fun isBack(c: CameraCharacteristics) = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK

    private fun isLogical(c: CameraCharacteristics) =
        c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true

    /** All back lenses reachable by any route, plus a text report of what was found. */
    fun discover(context: Context): Pair<List<LensOption>, String> {
        val m = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val listed = runCatching { m.cameraIdList.toList() }.getOrDefault(emptyList())
        val out = mutableListOf<LensOption>()
        val report = StringBuilder()
        report.append("系統列出的相機：${listed.joinToString(", ").ifEmpty { "無" }}\n")
        for (id in listed) {
            val c = runCatching { m.getCameraCharacteristics(id) }.getOrNull() ?: continue
            val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "後"
                CameraCharacteristics.LENS_FACING_FRONT -> "前"
                else -> "外接"
            }
            val logical = isLogical(c)
            report.append("  #$id $facing ${focal35(c)?.let { "${Math.round(it)}mm" } ?: "?"}${if (logical) "（多鏡頭）" else ""}\n")
            if (!isBack(c)) continue
            out += LensOption(id, null, LensOption.Kind.LISTED, focal35(c), logical)
            if (logical) for (pid in c.physicalCameraIds) {
                val pc = runCatching { m.getCameraCharacteristics(pid) }.getOrNull()
                val f = pc?.let { focal35(it) }
                report.append("     └ 實體 #$pid ${f?.let { "${Math.round(it)}mm" } ?: "?"}\n")
                out += LensOption(id, pid, LensOption.Kind.PHYSICAL, f, false)
            }
        }
        // ids the system does not list: vendors hide aux cameras from apps outside a whitelist
        val hidden = mutableListOf<String>()
        for (i in 0..31) {
            val id = "$i"
            if (id in listed || out.any { it.physicalId == id }) continue
            val c = runCatching { m.getCameraCharacteristics(id) }.getOrNull() ?: continue
            hidden += "#$id ${focal35(c)?.let { "${Math.round(it)}mm" } ?: "?"}${if (isBack(c)) "" else "（非後鏡頭）"}"
            if (isBack(c)) out += LensOption(id, null, LensOption.Kind.HIDDEN, focal35(c), isLogical(c))
        }
        report.append("未列出但可讀取的相機：${hidden.joinToString("、").ifEmpty { "無" }}")
        return out to report.toString()
    }
}
