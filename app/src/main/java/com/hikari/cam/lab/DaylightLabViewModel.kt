package com.hikari.cam.lab

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.cam.data.Media
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Daylight Filter Lab: one daylight test photo per scene, all Daylight Collection looks rendered on
 * it with the app's own GPU pipeline (same LUTs, grain and glow as the camera and export).
 */
class DaylightLabViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as com.hikari.cam.HikariApp).looks
    private val renderer = (app as com.hikari.cam.HikariApp).renderer
    val looks: List<FilterSpec> = repo.looks.filter { it.series == "daylight" }

    var scene by mutableStateOf(DaylightScene.SKY); private set
    /** the photo each scene uses (drawn stand-in until the user picks one) */
    val sources = mutableStateMapOf<DaylightScene, Bitmap>()
    val imported = mutableStateMapOf<DaylightScene, Boolean>()
    /** rendered looks for the current scene */
    val results = mutableStateMapOf<String, Bitmap>()
    var busy by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null)
    var renderMs by mutableStateOf(0.0); private set
    private var job: Job? = null

    init { select(DaylightScene.SKY) }

    fun select(s: DaylightScene) {
        scene = s
        job?.cancel()
        job = viewModelScope.launch {
            val src = sources[s] ?: withContext(Dispatchers.Default) { DaylightScenes.draw(s, 1200, 900) }.also { sources[s] = it }
            renderAll(s, src)
        }
    }

    fun importPhoto(uri: Uri) = viewModelScope.launch {
        val s = scene
        busy = "讀取照片…"
        try {
            val ctx = getApplication<Application>()
            val d = Media.decode(ctx, Media.source(ctx, uri), renderer.maxTexture(), copies = 2)
            val src = withContext(Dispatchers.Default) {
                if (maxOf(d.bitmap.width, d.bitmap.height) > SIZE) LabViewModel.fit(d.bitmap, SIZE).also { d.bitmap.recycle() } else d.bitmap
            }
            sources[s] = src
            imported[s] = true
            renderer.forget("day${s.ordinal}")
            if (scene == s) select(s)
        } catch (e: Throwable) {
            message = "無法讀取：${e.message}"
        } finally {
            busy = null
        }
    }

    fun resetScene() = viewModelScope.launch {
        val s = scene
        sources.remove(s); imported.remove(s)
        renderer.forget("day${s.ordinal}")
        select(s)
    }

    private suspend fun renderAll(s: DaylightScene, src: Bitmap) {
        busy = "套用 ${looks.size} 款濾鏡…"
        results.clear()
        val t0 = System.nanoTime()
        try {
            for (spec in looks) {
                val req = withContext(Dispatchers.IO) { OffscreenRenderer.Request(spec, repo.lut(spec), Adjustments.DEFAULT, seed = 7f) }
                results[spec.id] = renderer.render(src, req, src.width, src.height, cacheKey = "day${s.ordinal}")
            }
            renderMs = (System.nanoTime() - t0) / 1e6
        } finally {
            busy = null
        }
    }

    companion object {
        const val SIZE = 1600
    }
}
