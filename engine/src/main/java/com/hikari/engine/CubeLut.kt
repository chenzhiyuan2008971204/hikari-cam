package com.hikari.engine

/**
 * A 3D colour lookup table: from an Adobe/Resolve .cube file, or from the app's compact binary form
 * ("HLUT", see [parseBinary]). Data is RGB, red fastest.
 */
class CubeLut(val size: Int, val data: FloatArray, val domainMin: FloatArray, val domainMax: FloatArray) {
    /**
     * The table as standard .cube text (33^3 points, 4 decimals), loadable by Lightroom Classic
     * (Creative Profile) and DaVinci Resolve. Written exactly as the build wrote it before the assets
     * went binary, so parse(toCubeText()) gives back the same floats.
     */
    fun toCubeText(title: String): String {
        val sb = StringBuilder(size * size * size * 22 + 64)
        sb.append("TITLE \"").append(title.replace('"', '\'')).append("\"\nLUT_3D_SIZE ").append(size).append('\n')
        val f = java.util.Locale.ROOT
        for (i in 0 until size * size * size) {
            sb.append(String.format(f, "%.4f %.4f %.4f\n", data[i * 3].toDouble(), data[i * 3 + 1].toDouble(), data[i * 3 + 2].toDouble()))
        }
        return sb.toString()
    }

    companion object {
        private val WS = Regex("\\s+")

        private val BIN_MAGIC = byteArrayOf('H'.code.toByte(), 'L'.code.toByte(), 'U'.code.toByte(), 'T'.code.toByte())
        private const val BIN_HEADER = 16

        /**
         * Binary LUT (tools/looks/lutbin.py): "HLUT", u16 version 1, u16 size, u32 scale 10000, u32 0, then
         * size^3 x 3 int16 (little endian), red fastest, each row of red values stored as differences.
         * A stored number q is the value q / 10000, computed exactly like the .cube text parser computes
         * "0.dddd", so the floats are bit-identical to what the text file gave.
         */
        fun parseBinary(b: ByteArray): CubeLut {
            require(b.size >= BIN_HEADER && (0..3).all { b[it] == BIN_MAGIC[it] }) { "不是 HLUT 檔案" }
            fun u16(at: Int) = (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)
            val version = u16(4)
            val size = u16(6)
            val scale = u16(8).toLong() or (u16(10).toLong() shl 16)
            require(version == 1 && scale == 10000L) { "HLUT 版本或縮放不支援：$version / $scale" }
            require(size in 2..128) { "LUT 尺寸不支援：$size" }
            require(b.size == BIN_HEADER + size * size * size * 6) { "HLUT 長度與尺寸不符" }
            val out = FloatArray(size * size * size * 3)
            val k = Math.pow(10.0, -4.0)
            var p = BIN_HEADER
            var o = 0
            val acc = IntArray(3)
            for (row in 0 until size * size) {
                acc[0] = 0; acc[1] = 0; acc[2] = 0
                for (r in 0 until size) for (c in 0..2) {
                    acc[c] += (u16(p).toShort()).toInt(); p += 2
                    out[o++] = (acc[c].toDouble() * k).toFloat()
                }
            }
            return CubeLut(size, out, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f))
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
