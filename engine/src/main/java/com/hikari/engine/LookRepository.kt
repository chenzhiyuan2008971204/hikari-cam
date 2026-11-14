package com.hikari.engine

import android.content.Context
import org.json.JSONArray

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

    fun look(id: String?): FilterSpec = looks.firstOrNull { it.id == id } ?: looks.first()

    fun lut(spec: FilterSpec): CubeLut = synchronized(luts) {
        luts.getOrPut(spec.lut) { CubeLut.parse(assets.open(spec.lut).use { it.readBytes().decodeToString() }) }
    }

    companion object {
        val SERIES = listOf("film" to "Film", "japanese" to "Japanese", "ccd" to "CCD", "special" to "Special")
    }
}
