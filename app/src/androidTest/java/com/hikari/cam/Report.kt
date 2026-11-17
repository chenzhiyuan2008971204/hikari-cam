package com.hikari.cam

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * Test output that survives the test APK being uninstalled: files go to Download/HikariLab through
 * MediaStore (the CI job pulls that folder), every line also goes to logcat under "LabReport".
 */
object Report {
    private const val DIR = "HikariLab"
    private val lines = StringBuilder()

    fun line(text: String) {
        Log.i("LabReport", text)
        synchronized(lines) { lines.append(text).append('\n') }
    }

    fun png(context: Context, name: String, bmp: Bitmap) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        file(context, name, "image/png", out.toByteArray())
    }

    fun jpeg(context: Context, name: String, bmp: Bitmap) {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        file(context, name, "image/jpeg", out.toByteArray())
    }

    fun flush(context: Context, name: String) {
        val text = synchronized(lines) { lines.toString().also { lines.setLength(0) } }
        file(context, name, "text/plain", text.toByteArray())
    }

    private fun file(context: Context, name: String, mime: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DIR)
        }
        val r = context.contentResolver
        val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        r.openOutputStream(uri)!!.use { it.write(bytes) }
    }
}
