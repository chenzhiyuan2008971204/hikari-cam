package com.hikari.cam.filter

import java.io.File

/** A 3D colour lookup table parsed from an Adobe/Resolve .cube file. Data is RGB, red fastest. */
class CubeLut(val size: Int, val data: FloatArray, val domainMin: FloatArray, val domainMax: FloatArray) {

    companion object {
        private val cache = HashMap<String, CubeLut>()

        fun load(path: String): CubeLut? = synchronized(cache) {
            cache[path] ?: runCatching { parse(File(path).readText()) }.getOrNull()?.also { cache[path] = it }
        }

        fun parse(text: String): CubeLut {
            var size = 0
            val min = floatArrayOf(0f, 0f, 0f)
            val max = floatArrayOf(1f, 1f, 1f)
            var values: FloatArray? = null
            var n = 0
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val parts = line.split(Regex("\\s+"))
                when (parts[0].uppercase()) {
                    "TITLE" -> {}
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("只支援 3D LUT")
                    "LUT_3D_SIZE" -> {
                        size = parts[1].toInt()
                        require(size in 2..128) { "LUT 尺寸不支援：$size" }
                        values = FloatArray(size * size * size * 3)
                    }
                    "DOMAIN_MIN" -> for (i in 0..2) min[i] = parts[i + 1].toFloat()
                    "DOMAIN_MAX" -> for (i in 0..2) max[i] = parts[i + 1].toFloat()
                    else -> {
                        val v = values ?: continue
                        val r = parts[0].toFloatOrNull() ?: continue
                        if (n + 3 > v.size) break
                        v[n] = r; v[n + 1] = parts[1].toFloat(); v[n + 2] = parts[2].toFloat()
                        n += 3
                    }
                }
            }
            val v = values ?: throw IllegalArgumentException("找不到 LUT_3D_SIZE")
            require(n == v.size) { "LUT 資料不完整" }
            return CubeLut(size, v, min, max)
        }
    }
}
