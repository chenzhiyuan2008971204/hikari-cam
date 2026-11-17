package com.hikari.cam.smart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.hikari.engine.FilterSpec
import com.hikari.vision.Box
import com.hikari.vision.Detection
import com.hikari.vision.Frame
import com.hikari.vision.LookTraits
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import kotlin.coroutines.resume

/** One bundled standard test photo (assets/samples, licences in samples.json). */
class Sample(val id: String, val zh: String, val file: String, val source: String, val license: String)

/** The standard test photos: the same pictures feed thumbnails, the virtual camera and the tests. */
object Samples {
    @Volatile private var cached: List<Sample>? = null

    fun list(context: Context): List<Sample> = cached ?: run {
        val a = JSONArray(context.assets.open("samples/samples.json").bufferedReader().use { it.readText() })
        (0 until a.length()).map { a.getJSONObject(it).let { j -> Sample(j.getString("id"), j.getString("zh"), j.getString("file"), j.getString("source"), j.getString("license")) } }
            .also { cached = it }
    }

    fun bitmap(context: Context, s: Sample): Bitmap = context.assets.open("samples/${s.file}").use { BitmapFactory.decodeStream(it)!! }
    fun bytes(context: Context, s: Sample): ByteArray = context.assets.open("samples/${s.file}").use { it.readBytes() }
}

/** Bridges Android bitmaps and ML Kit to the pure-Kotlin vision module. */
object Smart {
    /**
     * The analysis frame. Photos up to 1280 px are box-averaged directly from their own pixels, exactly
     * as the JVM tests do; larger ones are first scaled to 1280 px.
     */
    fun frame(b: Bitmap, maxSide: Int = 256): Frame {
        val s = 1280f / maxOf(b.width, b.height)
        val work = if (s < 1f) Bitmap.createScaledBitmap(b, maxOf(1, (b.width * s).toInt()), maxOf(1, (b.height * s).toInt()), true) else b
        val px = IntArray(work.width * work.height)
        work.getPixels(px, 0, work.width, 0, 0, work.width, work.height)
        return Frame.fromArgb(px, work.width, work.height, maxSide).also { if (work !== b) work.recycle() }
    }

    fun traits(specs: List<FilterSpec>) = specs.map { LookTraits(it.id, it.name, it.series, it.traits) }

    /** What a look changes compared with the unfiltered photo, in words, from its measured traits. */
    fun difference(k: LookTraits): String {
        if (k["mono"] > 0.5f) return "轉為黑白" + (if (k["contrast"] > 0.08f) "、反差提高" else "") + (if (k["grain"] > 0.2f) "、有顆粒" else "")
        val parts = ArrayList<String>()
        fun pct(v: Float) = (if (v > 0) "+" else "−") + kotlin.math.abs((v * 100).toInt()) + "%"
        if (kotlin.math.abs(k["contrast"]) >= 0.03f) parts += "反差 " + pct(k["contrast"])
        if (kotlin.math.abs(k["sat"]) >= 0.03f) parts += "飽和 " + pct(k["sat"])
        if (k["warmth"] >= 0.15f) parts += "偏暖" else if (k["warmth"] <= -0.15f) parts += "偏冷"
        if (k["shadowLift"] >= 0.02f) parts += "暗部提亮"
        if (k["hlSoft"] >= 0.1f) parts += "高光柔和"
        if (k["fade"] >= 0.03f) parts += "褪色黑位"
        if (k["glow"] >= 0.3f) parts += "光暈"
        return if (parts.isEmpty()) "與原圖非常接近，只做細微的色彩整理" else parts.take(4).joinToString("、")
    }

    private val faces by lazy {
        FaceDetection.getClient(FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE).setMinFaceSize(0.06f).build())
    }
    private val objects by lazy {
        ObjectDetection.getClient(ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE).enableMultipleObjects().build())
    }

    /** Faces and objects in a still photo, as boxes in fractions of the picture. Never throws: no detector = empty list. */
    suspend fun detect(b: Bitmap): List<Detection> {
        val work = if (maxOf(b.width, b.height) > 1280) {
            val s = 1280f / maxOf(b.width, b.height)
            Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
        } else b
        val w = work.width.toFloat(); val h = work.height.toFloat()
        fun box(r: android.graphics.Rect) = Box((r.left / w).coerceIn(0f, 1f), (r.top / h).coerceIn(0f, 1f), (r.right / w).coerceIn(0f, 1f), (r.bottom / h).coerceIn(0f, 1f))
        val input = InputImage.fromBitmap(work, 0)
        val out = ArrayList<Detection>()
        runCatching {
            suspendCancellableCoroutine<Unit> { c ->
                faces.process(input).addOnSuccessListener { fs -> for (f in fs) out += Detection(Detection.Kind.FACE, box(f.boundingBox), f.headEulerAngleY) }
                    .addOnCompleteListener { if (c.isActive) c.resume(Unit) }
            }
            suspendCancellableCoroutine<Unit> { c ->
                objects.process(input).addOnSuccessListener { os -> for (o in os) out += Detection(Detection.Kind.OBJECT, box(o.boundingBox)) }
                    .addOnCompleteListener { if (c.isActive) c.resume(Unit) }
            }
        }
        if (work !== b) work.recycle()
        return out
    }
}
