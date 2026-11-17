package com.hikari.studio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater

/**
 * Writes a look as an Adobe Lightroom / Camera Raw creative profile (.xmp with an embedded RGB table),
 * so the same colour can be used on RAW files on a computer. The table carries the look's 3D LUT
 * exactly (resampled to 32 points per axis, the size Camera Raw accepts); grain, halation, bloom and
 * vignette are not part of a profile.
 *
 * Adobe does not publish the table format; this follows the layout used by independent tools:
 * header {1, 1, 3, n} u32, n^3 x 3 u16 deltas from the identity ramp (R slowest, B fastest),
 * footer {primaries, gamma, gamut} u32 + amount range 2 x f64; then u32 length + zlib, as Adobe base85.
 */
object XmpProfile {
    private const val B85 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-:+=^!/*?`'|()[]{}@%$#"
    const val SIZE = 32

    fun b85(data: ByteArray): String {
        val sb = StringBuilder(data.size * 5 / 4 + 5); val full = data.size - data.size % 4
        fun u32(i: Int, n: Int): Long { var x = 0L; for (k in 0 until n) x = x or ((data[i + k].toLong() and 255) shl (8 * k)); return x }
        var i = 0
        while (i < full) { var x = u32(i, 4); repeat(5) { sb.append(B85[(x % 85).toInt()]); x /= 85 }; i += 4 }
        val rest = data.size - full
        if (rest > 0) { var x = u32(full, rest); repeat(rest + 1) { sb.append(B85[(x % 85).toInt()]); x /= 85 } }
        return sb.toString()
    }

    fun b85Decode(text: String): ByteArray {
        val out = ByteArrayOutputStream(); var i = 0
        while (i < text.length) { val chunk = text.substring(i, minOf(i + 5, text.length)); var x = 0L; var m = 1L
            for (ch in chunk) { x += B85.indexOf(ch) * m; m *= 85 }
            val n = if (chunk.length < 5) chunk.length - 1 else 4
            for (k in 0 until n) out.write(((x shr (8 * k)) and 255).toInt()); i += 5 }
        return out.toByteArray()
    }

    /** Trilinear sample of a .cube table ([lut] = n^3 x 3 floats, R fastest, B slowest) at r, g, b in 0..1. */
    fun sample(lut: FloatArray, n: Int, r: Float, g: Float, b: Float, out: FloatArray) {
        val fr = r * (n - 1); val fg = g * (n - 1); val fb = b * (n - 1)
        val r0 = fr.toInt().coerceIn(0, n - 2); val g0 = fg.toInt().coerceIn(0, n - 2); val b0 = fb.toInt().coerceIn(0, n - 2)
        val tr = fr - r0; val tg = fg - g0; val tb = fb - b0
        for (c in 0..2) {
            fun v(ri: Int, gi: Int, bi: Int) = lut[((bi * n + gi) * n + ri) * 3 + c]
            val c00 = v(r0, g0, b0) * (1 - tr) + v(r0 + 1, g0, b0) * tr; val c10 = v(r0, g0 + 1, b0) * (1 - tr) + v(r0 + 1, g0 + 1, b0) * tr
            val c01 = v(r0, g0, b0 + 1) * (1 - tr) + v(r0 + 1, g0, b0 + 1) * tr; val c11 = v(r0, g0 + 1, b0 + 1) * (1 - tr) + v(r0 + 1, g0 + 1, b0 + 1) * tr
            out[c] = (c00 * (1 - tg) + c10 * tg) * (1 - tb) + (c01 * (1 - tg) + c11 * tg) * tb
        }
    }

    private fun nop(i: Int) = (i * 0xFFFF + (SIZE shr 1)) / (SIZE - 1)

    /** The uncompressed table block for a .cube LUT of [n] points per axis. */
    fun block(lut: FloatArray, n: Int): ByteArray {
        val buf = ByteBuffer.allocate(16 + SIZE * SIZE * SIZE * 6 + 12 + 16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(1).putInt(1).putInt(3).putInt(SIZE)
        val px = FloatArray(3)
        for (ri in 0 until SIZE) for (gi in 0 until SIZE) for (bi in 0 until SIZE) {
            sample(lut, n, ri / (SIZE - 1f), gi / (SIZE - 1f), bi / (SIZE - 1f), px)
            val idx = intArrayOf(ri, gi, bi)
            for (c in 0..2) buf.putShort(((Math.round(px[c].coerceIn(0f, 1f) * 65535f) - nop(idx[c])) and 0xFFFF).toShort())
        }
        buf.putInt(0).putInt(1).putInt(0)          // sRGB primaries, sRGB gamma, clip
        buf.putDouble(0.0).putDouble(2.0)          // amount slider range
        return buf.array()
    }

    /** Values of the table back from a block (r, g, b order as written), for checks. */
    fun decode(block: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN); buf.position(16)
        val out = FloatArray(SIZE * SIZE * SIZE * 3); var k = 0
        for (ri in 0 until SIZE) for (gi in 0 until SIZE) for (bi in 0 until SIZE) { val idx = intArrayOf(ri, gi, bi)
            for (c in 0..2) out[k++] = (((buf.short.toInt() and 0xFFFF) + nop(idx[c])) and 0xFFFF) / 65535f }
        return out
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun xmp(name: String, group: String, description: String, lut: FloatArray, n: Int): String {
        val block = block(lut, n)
        val md5 = MessageDigest.getInstance("MD5").digest(block).joinToString("") { "%02X".format(it) }
        val def = Deflater(9); def.setInput(block); def.finish()
        val z = ByteArrayOutputStream(); val tmp = ByteArray(65536)
        while (!def.finished()) z.write(tmp, 0, def.deflate(tmp))
        val payload = ByteBuffer.allocate(4 + z.size()).order(ByteOrder.LITTLE_ENDIAN).putInt(block.size).put(z.toByteArray()).array()
        val uid = UUID.nameUUIDFromBytes("hikari-cam/$name/$md5".toByteArray()).toString().replace("-", "").uppercase()
        fun lang(s: String) = "<rdf:Alt><rdf:li xml:lang=\"x-default\">${esc(s)}</rdf:li></rdf:Alt>"
        return """<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 7.0">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/"
   crs:PresetType="Look"
   crs:Cluster=""
   crs:UUID="$uid"
   crs:SupportsAmount="True"
   crs:SupportsColor="True"
   crs:SupportsMonochrome="True"
   crs:SupportsHighDynamicRange="True"
   crs:SupportsNormalDynamicRange="True"
   crs:SupportsSceneReferred="True"
   crs:SupportsOutputReferred="True"
   crs:RequiresRGBTables="False"
   crs:CameraModelRestriction=""
   crs:Copyright=""
   crs:ContactInfo=""
   crs:Version="14.3"
   crs:ProcessVersion="11.0"
   crs:ConvertToGrayscale="False"
   crs:RGBTable="$md5"
   crs:Table_$md5="${b85(payload)}"
   crs:HasSettings="True">
   <crs:Name>
    ${lang(name)}
   </crs:Name>
   <crs:Group>
    ${lang(group)}
   </crs:Group>
   <crs:Description>
    ${lang(description)}
   </crs:Description>
  </rdf:Description>
 </rdf:RDF>
</x:xmpmeta>
"""
    }
}
