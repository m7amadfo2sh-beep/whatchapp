package com.whatchapp.hourlybuzz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads pictures to the app's cache so they show instantly. */
object ImageCache {
    private const val TAG = "ImageCache"
    private const val MAX_FILES = 20

    private fun fileFor(context: Context, url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(File(context.cacheDir, "images").apply { mkdirs() }, hash)
    }

    /** Downloads [url] unless it is already cached. Returns true on success. Blocking. */
    fun download(context: Context, url: String): Boolean {
        val file = fileFor(context, url)
        if (file.length() > 0) return true
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            try {
                if (conn.responseCode !in 200..299) {
                    Log.w(TAG, "HTTP ${conn.responseCode} for $url")
                    return false
                }
                val tmp = File(file.path + ".part")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            } finally {
                conn.disconnect()
            }
            trim(file.parentFile!!)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for $url", e)
            false
        }
    }

    /** Loads [url] (downloading if needed), scaled down to about [size] px. Blocking. */
    fun load(context: Context, url: String, size: Int): Bitmap? {
        if (!download(context, url)) return null
        val path = fileFor(context, url).path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun trim(dir: File) {
        val files = dir.listFiles()?.filter { !it.name.endsWith(".part") } ?: return
        files.sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
    }
}
