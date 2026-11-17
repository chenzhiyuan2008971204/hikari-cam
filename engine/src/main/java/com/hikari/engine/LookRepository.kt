package com.hikari.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Loads the bundled film looks (assets/filters, assets/luts) once and keeps them in memory. */
class LookRepository(context: Context) {
    private val assets = context.applicationContext.assets
    private val luts = HashMap<String, CubeLut>()

    val looks: List<FilterSpec> by lazy {
        val ids = JSONArray(assets.open("filters/index.json").use { it.readBytes().decodeToString() })
        (0 until ids.length()).map { i ->
            FilterSpec.parse(assets.open("filters/${ids.getString(i)}.json").use { it.readBytes().decodeToString() })
        }
    }

    /**
     * Looks taken out because another look gives practically the same picture: retired id -> the
     * look that stays (filters/retired.json). Saved favourites, recents, presets and edits that
     * name a retired look open the look that replaced it.
     */
    val retired: Map<String, String> by lazy {
        runCatching {
            val o = JSONObject(assets.open("filters/retired.json").use { it.readBytes().decodeToString() })
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    /** the shipped id for a look id that may be retired */
    fun canonical(id: String?): String? = id?.let { retired[it] ?: it }

    /** a shipped id and the retired ids that now open it */
    fun aliases(id: String): List<String> = listOf(id) + retired.filterValues { it == id }.keys

    fun look(id: String?): FilterSpec = canonical(id).let { c -> looks.firstOrNull { it.id == c } } ?: looks.first()

    fun lut(spec: FilterSpec): CubeLut = synchronized(luts) {
        luts.getOrPut(spec.lut) {
            val bytes = assets.open(spec.lut).use { it.readBytes() }
            if (spec.lut.endsWith(".lut")) CubeLut.parseBinary(bytes) else CubeLut.parse(bytes.decodeToString())
        }
    }

    companion object {
        val SERIES = listOf("film" to "Film", "japanese" to "Japanese", "ccd" to "CCD", "special" to "Special")
    }
}
