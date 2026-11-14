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
                val f = parseDecimal(line, start, i)
                if (f.isNaN()) return false
                out[at + k] = f
            }
            return true
        }

        /**
         * Plain decimal ("-0.1234", "1", "1e-3") without allocating; NaN if it is not one.
         * String.toFloatOrNull validates with a regex on Android/JVM and was most of the LUT load time.
         */
        internal fun parseDecimal(s: String, from: Int, to: Int): Float {
            var i = from
            var neg = false
            if (i < to && (s[i] == '-' || s[i] == '+')) { neg = s[i] == '-'; i++ }
            var mant = 0L
            var digits = 0
            var scale = 0
            var seenDot = false
            while (i < to) {
                val c = s[i]
                if (c in '0'..'9') {
                    if (digits < 18) { mant = mant * 10 + (c - '0'); digits++; if (seenDot) scale-- }
                    else if (!seenDot) scale++
                } else if (c == '.' && !seenDot) seenDot = true
                else break
                i++
            }
            if (digits == 0) return Float.NaN
            if (i < to && (s[i] == 'e' || s[i] == 'E')) {
                i++
                var eneg = false
                if (i < to && (s[i] == '-' || s[i] == '+')) { eneg = s[i] == '-'; i++ }
                var e = 0
                var ed = 0
                while (i < to && s[i] in '0'..'9') { e = e * 10 + (s[i] - '0'); i++; ed++ }
                if (ed == 0) return Float.NaN
                scale += if (eneg) -e else e
            }
            if (i != to) return Float.NaN
            val v = mant.toDouble() * Math.pow(10.0, scale.toDouble())
            return (if (neg) -v else v).toFloat()
        }
    }
}
