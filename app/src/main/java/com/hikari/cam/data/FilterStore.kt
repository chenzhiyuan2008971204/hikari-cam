package com.hikari.cam.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import com.hikari.cam.filter.Category
import com.hikari.cam.filter.CubeLut
import com.hikari.cam.filter.Filter
import com.hikari.cam.filter.FilterParams
import com.hikari.cam.filter.Presets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Built-in looks plus the user's own (saved tweaks and imported LUTs), persisted as JSON. */
class FilterStore(private val context: Context) {
    val custom = mutableStateListOf<Filter>()
    private val file = File(context.filesDir, "my_filters.json")
    private val lutDir = File(context.filesDir, "luts").apply { mkdirs() }

    init {
        runCatching {
            if (file.exists()) {
                val arr = JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    custom += Filter(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        subtitle = o.optString("subtitle", "My"),
                        category = Category.MINE,
                        params = FilterParams.fromJson(o.getJSONObject("params")),
                        lutFile = if (o.has("lut")) o.getString("lut") else null,
                    )
                }
            }
        }
    }

    fun all(): List<Filter> = Presets.all + custom

    fun saveCustom(name: String, params: FilterParams, lutFile: String?): Filter {
        val f = Filter("my_" + UUID.randomUUID(), name, "My", Category.MINE, params, lutFile)
        custom += f
        persist()
        return f
    }

    fun delete(f: Filter) {
        custom.remove(f)
        // Only remove the LUT if no other filter uses it.
        f.lutFile?.let { path -> if (custom.none { it.lutFile == path }) File(path).delete() }
        persist()
    }

    /** Copies a .cube file into app storage and creates a filter for it. */
    suspend fun importLut(uri: Uri, name: String): Filter = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        CubeLut.parse(text) // validate before saving
        val dest = File(lutDir, UUID.randomUUID().toString() + ".cube")
        dest.writeText(text)
        withContext(Dispatchers.Main) {
            saveCustom(name, FilterParams(lutAmount = 1f), dest.absolutePath)
        }
    }

    private fun persist() {
        val arr = JSONArray()
        for (f in custom) {
            arr.put(JSONObject().apply {
                put("id", f.id); put("name", f.name); put("subtitle", f.subtitle)
                put("params", f.params.toJson())
                f.lutFile?.let { put("lut", it) }
            })
        }
        file.writeText(arr.toString())
    }
}
