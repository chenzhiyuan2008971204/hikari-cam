package com.hikari.cam.data

import android.app.ActivityManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Photos in and out: MediaStore (Pictures/Hikari) and decoding of any format the device can read. */
object Media {
    const val FOLDER = "Hikari"

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())

    /** Saves a finished photo as JPEG. Original files are never touched; this is always a new file. */
    suspend fun saveJpeg(context: Context, bitmap: Bitmap, quality: Int, suffix: String = ""): Uri =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "HIKARI_${stamp()}$suffix.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("無法建立相片檔")
            try {
                resolver.openOutputStream(uri)!!.use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) }
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        }

    /** A new, still hidden video file in Movies/Hikari and the descriptor to write it through (a seekable one, for the mp4 muxer). */
    class NewVideo(val uri: Uri, val pfd: android.os.ParcelFileDescriptor)

    fun createVideo(context: Context): NewVideo {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "HIKARI_${stamp()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/" + FOLDER)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("無法建立影片檔")
        val pfd = resolver.openFileDescriptor(uri, "rw")
        if (pfd == null) { resolver.delete(uri, null, null); error("無法寫入影片檔") }
        return NewVideo(uri, pfd)
    }

    /** Makes a finished recording visible in the gallery. */
    fun publishVideo(context: Context, uri: Uri) {
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    }

    /** Removes a recording that failed or was empty. */
    fun discardVideo(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    /** Copies a file (the camera's original JPEG, with its EXIF) into the gallery unchanged. */
    suspend fun saveFileCopy(context: Context, file: File, mime: String, name: String): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER + "/Originals")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("無法建立原圖檔")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }

    /** MediaStore values for a RAW (DNG) file written directly by CameraX. */
    fun dngValues(): ContentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "HIKARI_${stamp()}.dng")
        put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER + "/RAW")
    }

    /** Copies date and camera tags from the source photo to the exported one (pixels are already upright). */
    suspend fun copyExif(context: Context, from: Uri?, fromFile: File?, to: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val src = when {
                fromFile != null -> ExifInterface(fromFile)
                from != null -> context.contentResolver.openInputStream(from)!!.use { ExifInterface(it) }
                else -> return@runCatching
            }
            context.contentResolver.openFileDescriptor(to, "rw")!!.use { fd ->
                val dst = ExifInterface(fd.fileDescriptor)
                for (tag in listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME, ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
                )) src.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                dst.setAttribute(ExifInterface.TAG_SOFTWARE, "Hikari")
                dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                dst.saveAttributes()
            }
        }
    }

    class Decoded(val bitmap: Bitmap, val fullWidth: Int, val fullHeight: Int, val downscaled: Boolean)

    /**
     * Decodes any image the device supports (JPEG, PNG, WebP, HEIC/HEIF, DNG...), upright, as a
     * software ARGB bitmap no larger than [maxSide]. A full-resolution request is also limited by
     * free heap: [Decoded.downscaled] then says the result is smaller than the original.
     */
    suspend fun decode(
        context: Context, source: ImageDecoder.Source, maxSide: Int,
        /** full-size bitmaps that must fit in the heap at the same time (1 when the caller hands the
         *  decoded bitmap to the GPU and recycles it before allocating the output) */
        copies: Int = 2,
    ): Decoded = withContext(Dispatchers.IO) {
        var full = Size(0, 0)
        var scaled = false
        val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            full = info.size
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_DEFAULT
            var side = maxOf(info.size.width, info.size.height)
            var target = minOf(side, maxSide)
            // Bitmap pixels live in native memory (Android 8+), not in the Java heap, so the limit
            // is what the system can still give: keep a margin so the app is not killed.
            val mi = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            val free = mi.availMem - mi.threshold - (256L shl 20)
            while (target > 1024) {
                val s = target.toFloat() / side
                val bytes = 4L * (info.size.width * s).toLong() * (info.size.height * s).toLong()
                if (bytes * copies + (48L shl 20) < free) break
                target = (target * 0.85f).toInt()
            }
            if (target < side) {
                val s = target.toFloat() / side
                decoder.setTargetSize(maxOf(1, (info.size.width * s).toInt()), maxOf(1, (info.size.height * s).toInt()))
                scaled = true
            }
        }
        // 10-bit HEIF / HDR sources decode as RGBA_F16; the GPU path takes 8-bit ARGB
        val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }
        Decoded(argb, full.width, full.height, scaled)
    }

    fun source(context: Context, uri: Uri): ImageDecoder.Source = ImageDecoder.createSource(context.contentResolver, uri)
    fun source(file: File): ImageDecoder.Source = ImageDecoder.createSource(file)

    data class Photo(val uri: Uri, val name: String, val dateTaken: Long)

    /**
     * Deleting photos. Android 11+ asks the user in a system dialog ([deleteRequest]); on Android 10
     * files the app created are deleted directly, and a file it no longer owns (e.g. after a
     * reinstall) comes back as the RecoverableSecurityException's confirmation intent.
     */
    fun deleteRequest(context: Context, uris: List<Uri>): android.content.IntentSender? =
        if (android.os.Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender else null

    /** Android 10: deletes directly; returns the confirmation needed for the first file it may not delete, if any. */
    suspend fun deleteDirect(context: Context, uris: List<Uri>): android.content.IntentSender? = withContext(Dispatchers.IO) {
        for (u in uris) {
            try {
                context.contentResolver.delete(u, null, null)
            } catch (e: android.app.RecoverableSecurityException) {
                return@withContext e.userAction.actionIntent.intentSender
            }
        }
        null
    }

    /** Photos this app saved (Pictures/Hikari), newest first. */
    suspend fun list(context: Context): List<Photo> = withContext(Dispatchers.IO) {
        val out = ArrayList<Photo>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.DATE_ADDED)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("${Environment.DIRECTORY_PICTURES}/$FOLDER/%"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val date = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext()) {
                out += Photo(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(id)),
                    c.getString(name), c.getLong(date))
            }
        }
        out
    }
}
