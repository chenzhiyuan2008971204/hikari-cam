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
import com.hikari.cam.lab.TestChart
import com.hikari.data.Favorite
import com.hikari.data.Preset
import com.hikari.data.Recent
import com.hikari.data.UserSettings
import com.hikari.engine.Adjustments
import com.hikari.engine.FilterSpec
import com.hikari.engine.gl.OffscreenRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the app is. The four tabs, plus full-screen pages on top of them. */
sealed interface Route {
    data object Camera : Route
    data object Gallery : Route
    data object Filters : Route
    data object Settings : Route
    data class Editor(val uri: Uri, val lookId: String? = null) : Route
    data object Lab : Route
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

    /** each look rendered on the built-in test chart (Filters page) */
    val chartThumbs = mutableStateMapOf<String, Bitmap>()

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

    /** favourites first, then recents, then the rest in catalogue order */
    fun ordered(): List<FilterSpec> {
        val fav = favorites.value
        val rec = recents.value
        return looks.sortedWith(compareBy({ if (it.id in fav) 0 else if (it.id in rec) 1 else 2 }, { rec.indexOf(it.id) }))
    }

    init {
        viewModelScope.launch {
            val chart = withContext(Dispatchers.Default) { TestChart.draw(480, 360) }
            for (spec in looks) {
                val lut = withContext(Dispatchers.IO) { hikari.looks.lut(spec) }
                chartThumbs[spec.id] = hikari.renderer.render(chart, OffscreenRenderer.Request(spec, lut, Adjustments.DEFAULT, seed = 3f),
                    chart.width, chart.height, cacheKey = "chart")
            }
            hikari.renderer.forget("chart")
        }
    }
}
