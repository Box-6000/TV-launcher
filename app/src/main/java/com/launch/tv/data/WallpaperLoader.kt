package com.launch.tv.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.InputStream
import kotlin.math.max

object WallpaperLoader {
    fun import(context: Context, uri: Uri): String? {
        return try {
            val dest = File(context.filesDir, "wallpaper.jpg")
            open(context, uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (dest.length() == 0L) return null
            "local:${dest.absolutePath}"
        } catch (_: Throwable) {
            null
        }
    }

    fun load(context: Context, uriString: String): ImageBitmap? {
        return try {
            if (uriString.startsWith("local:")) {
                val file = File(uriString.removePrefix("local:"))
                if (!file.isFile) return null
                file.inputStream().use { decode(it, 3840) }
            } else {
                open(context, Uri.parse(uriString))?.use { decode(it, 3840) }
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun preview(context: Context, uri: Uri): ImageBitmap? {
        return try {
            open(context, uri)?.use { decode(it, 360) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun open(context: Context, uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            uri.path?.let { path -> File(path).takeIf { it.isFile }?.inputStream() }
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    fun loadResource(context: Context, resId: Int, limit: Int = 3840): ImageBitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(context.resources, resId, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (longest / sample > limit) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeResource(context.resources, resId, options)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun decode(stream: InputStream, limit: Int): ImageBitmap? {
        val bytes = stream.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > limit) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    fun poster(context: Context, uri: String?): ImageBitmap? {
        if (uri.isNullOrBlank()) return null
        return try {
            when {
                uri.startsWith("https://") || uri.startsWith("http://") -> previewUrl(uri)
                uri.startsWith("/") -> File(uri).takeIf { it.isFile }?.inputStream()?.use { decode(it, 720) }
                uri.startsWith("file:") -> {
                    val path = Uri.parse(uri).path ?: return null
                    File(path).takeIf { it.isFile }?.inputStream()?.use { decode(it, 720) }
                }
                else -> preview(context, Uri.parse(uri))
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun previewUrl(url: String): ImageBitmap? {
        return try {
            var current = url
            repeat(4) {
                val connection = java.net.URL(current).openConnection() as java.net.HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000
                connection.readTimeout = 12000
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 11; Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                )
                connection.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*")
                val code = connection.responseCode
                if (code in 300..399) {
                    val next = connection.getHeaderField("Location") ?: return null
                    connection.disconnect()
                    current = if (next.startsWith("http")) next else java.net.URL(java.net.URL(current), next).toString()
                    return@repeat
                }
                if (code !in 200..299) {
                    connection.disconnect()
                    return null
                }
                return connection.inputStream.use { decode(it, 720) }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun download(context: Context, url: String): String? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "LaunchTV")
            connection.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.size < 32 || bytes.size > 25 * 1024 * 1024) return null
                val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
                if (probe.outWidth <= 0 || probe.outHeight <= 0) return null
                val dest = File(context.filesDir, "wallpaper.jpg")
                dest.writeBytes(bytes)
                "local:${dest.absolutePath}"
            }
        } catch (_: Throwable) {
            null
        }
    }
}
