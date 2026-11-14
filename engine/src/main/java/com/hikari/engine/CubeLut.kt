package com.hikari.engine

/** A 3D colour lookup table from an Adobe/Resolve .cube file. Data is RGB, red fastest. */
class CubeLut(val size: Int, val data: FloatArray, val domainMin: FloatArray, val domainMax: FloatArray) {
    companion object {
        private val WS = Regex("\\s+")

        fun parse(text: String): CubeLut {
            var size = 0
            val min = floatArrayOf(0f, 0f, 0f)
            val max = floatArrayOf(1f, 1f, 1f)
            var values: FloatArray? = null
            var n = 0
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val c0 = line[0]
                if (c0.isDigit() || c0 == '-' || c0 == '.') {
                    val v = values ?: continue
                    if (n + 3 > v.size) break
                    if (parseRow(line, v, n)) { n += 3; continue }
                }
                val parts = line.split(WS)
                when (parts[0].uppercase()) {
                    "LUT_1D_SIZE" -> throw IllegalArgumentException("只支援 3D LUT")
                    "LUT_3D_SIZE" -> {
                        size = parts[1].toInt()
                        require(size in 2..128) { "LUT 尺寸不支援：$size" }
                        values = FloatArray(size * size * size * 3)
                    }
                    "DOMAIN_MIN" -> for (i in 0..2) min[i] = parts[i + 1].toFloat()
                    "DOMAIN_MAX" -> for (i in 0..2) max[i] = parts[i + 1].toFloat()
                }
            }
            val v = values ?: throw IllegalArgumentException("找不到 LUT_3D_SIZE")
            require(n == v.size) { "LUT 資料不完整" }
            return CubeLut(size, v, min, max)
        }

        private fun parseRow(line: String, out: FloatArray, at: Int): Boolean {
            var i = 0
            for (k in 0..2) {
                while (i < line.length && line[i].isWhitespace()) i++
                val start = i
                while (i < line.length && !line[i].isWhitespace()) i++
                if (start == i) return false
                out[at + k] = line.substring(start, i).toFloatOrNull() ?: return false
            }
            return true
        }
    }
}
