package com.hikari.cam.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.cam.HikariApp
import com.hikari.data.Favorite
import com.hikari.data.Preset
import com.hikari.data.Recent
import com.hikari.data.UserSettings
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the app is. The four tabs, plus full-screen pages on top of them. */
sealed interface Route {
    data object Camera : Route
    data object Gallery : Route
    data object Filters : Route
    data object Settings : Route
    data class Editor(val uri: Uri, val lookId: String? = null) : Route
    data object Lab : Route
    data object DaylightLab : Route
}

/** State shared by every screen: settings, favourites, recents, presets and look thumbnails. */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val hikari = app as HikariApp
    private val db = hikari.db
    val looks: List<FilterSpec> = hikari.looks.looks

    var route by mutableStateOf<Route>(Route.Camera)
    /** the tab to return to when a full-screen page closes */
    var tab by mutableStateOf<Route>(Route.Camera)

    val settings: StateFlow<UserSettings> = db.settings().all().map { UserSettings.from(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val favorites: StateFlow<Set<String>> = db.looks().favorites().map { l -> l.map { it.lookId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val recents: StateFlow<List<String>> = db.looks().recents().map { l -> l.map { it.lookId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val presets: StateFlow<List<Preset>> = db.looks().presets()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** null until a camera has been opened once */
    var rawSupported by mutableStateOf<Boolean?>(null)

    /** what natural-detail mode could set on the last opened camera ("" = not known yet / nothing) */
    var naturalApplied by mutableStateOf<String?>(null)

    /** cameras the phone shows to apps (Settings → 相機資訊) */
    var cameraReport by mutableStateOf("")

    /**
     * Look thumbnails: every look rendered by the real engine (same shaders and LUTs as capture
     * and export) on one bundled standard photo, so they can be compared like for like.
     */
    val thumbs = androidx.compose.runtime.mutableStateMapOf<String, android.graphics.Bitmap>()
    val samples by lazy { com.hikari.cam.smart.Samples.list(app) }
    private var thumbJob: kotlinx.coroutines.Job? = null
    private var thumbScene: String? = null

    fun loadThumbs(sceneId: String) {
        if (sceneId == thumbScene) return
        thumbScene = sceneId
        thumbJob?.cancel()
        thumbJob = viewModelScope.launch {
            val sample = samples.firstOrNull { it.id == sceneId } ?: samples.first()
            val src = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val b = com.hikari.cam.smart.Samples.bitmap(getApplication(), sample)
                // centre square, 256 px: the same crop, exposure and size for every look
                val side = minOf(b.width, b.height)
                val sq = android.graphics.Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
                android.graphics.Bitmap.createScaledBitmap(sq, 256, 256, true)
            }
            hikari.renderer.forget("appthumbs")
            for (spec in looks) {
                val req = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.hikari.engine.gl.OffscreenRenderer.Request(spec, hikari.looks.lut(spec), Adjustments.DEFAULT, seed = 7f)
                }
                thumbs[spec.id] = hikari.renderer.render(src, req, 256, 256, cacheKey = "appthumbs")
            }
        }
    }

    /** One look on several standard scenes (sky, people, plants, city ...), rendered by the engine. */
    suspend fun scenePreviews(spec: FilterSpec, ids: List<String> = listOf("sunny", "portrait", "foliage", "city", "overcast", "backlight")): List<Pair<String, android.graphics.Bitmap>> {
        val out = ArrayList<Pair<String, android.graphics.Bitmap>>()
        val req = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.hikari.engine.gl.OffscreenRenderer.Request(spec, hikari.looks.lut(spec), Adjustments.DEFAULT, seed = 7f)
        }
        for (id in ids) {
            val sample = samples.firstOrNull { it.id == id } ?: continue
            val src = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val b = com.hikari.cam.smart.Samples.bitmap(getApplication(), sample)
                val s = 480f / maxOf(b.width, b.height)
                android.graphics.Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
            }
            out += sample.zh to hikari.renderer.render(src, req, src.width, src.height, cacheKey = null)
        }
        return out
    }

    fun look(id: String?): FilterSpec = hikari.looks.look(id)

    fun go(r: Route) {
        if (r is Route.Camera || r is Route.Gallery || r is Route.Filters || r is Route.Settings) tab = r
        route = r
    }

    fun back(): Boolean {
        if (route == tab) return false
        route = tab
        return true
    }

    fun updateSettings(f: (UserSettings) -> UserSettings) = viewModelScope.launch {
        val next = f(settings.value)
        for (row in next.toRows()) db.settings().put(row)
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (id in favorites.value) db.looks().removeFavorite(id) else db.looks().addFavorite(Favorite(id, System.currentTimeMillis()))
    }

    fun touch(id: String) = viewModelScope.launch { db.looks().touch(Recent(id, System.currentTimeMillis())) }

    fun savePreset(name: String, lookId: String, a: Adjustments) = viewModelScope.launch {
        db.looks().addPreset(Preset(name = name, lookId = lookId, adjustJson = a.toJson(), createdAt = System.currentTimeMillis()))
    }

    fun deletePreset(id: Long) = viewModelScope.launch { db.looks().deletePreset(id) }
}
