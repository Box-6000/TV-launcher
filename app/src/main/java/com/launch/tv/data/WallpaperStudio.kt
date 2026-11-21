package com.launch.tv.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max

data class WallpaperCategory(val id: String, val title: String)

object WallpaperStudio {
    val categories = listOf(
        WallpaperCategory("night", "Ночь"),
        WallpaperCategory("dawn", "Рассвет"),
        WallpaperCategory("sunset", "Закат"),
        WallpaperCategory("ocean", "Океан"),
        WallpaperCategory("forest", "Лес"),
        WallpaperCategory("peaks", "Горы"),
        WallpaperCategory("city", "Город"),
        WallpaperCategory("space", "Космос"),
        WallpaperCategory("abstract", "Абстракция"),
        WallpaperCategory("minimal", "Минимализм"),
    )
    const val COUNT = 10

    data class LiveWallpaper(val id: String, val title: String, val asset: String, val thumb: String)

    val live = listOf(
        LiveWallpaper("grove", "Лес", "walls/live/grove.mp4", "forest"),
        LiveWallpaper("lake", "Рассвет", "walls/live/lake.mp4", "dawn"),
    )

    fun liveKey(raw: String): String = when (raw) {
        "mist", "grove" -> "grove"
        "stars", "lake" -> "lake"
        else -> raw
    }

    fun liveTitle(id: String): String? {
        if (!id.startsWith("live:")) return null
        val key = liveKey(id.removePrefix("live:"))
        val name = live.firstOrNull { it.id == key }?.title ?: return null
        return "Живые · $name"
    }

    fun title(id: String): String? {
        if (!id.startsWith("studio:")) return null
        val parts = id.split(":")
        val name = categories.firstOrNull { it.id == parts.getOrNull(1) }?.title ?: return null
        val number = parts.getOrNull(2)?.toIntOrNull()?.plus(1) ?: 1
        return "$name $number"
    }

    fun thumb(context: Context, category: String, index: Int): ImageBitmap? {
        val key = "$category:$index"
        cache.get(key)?.let { return it }
        val image = decode(context, category, index, 720) ?: return null
        cache.put(key, image)
        return image
    }

    fun install(context: Context, category: String, index: Int): String? {
        return copyAsset(context, assetName(category, index), "jpg")
    }

    fun installLive(context: Context, id: String): String? {
        val item = live.firstOrNull { it.id == id } ?: return null
        return copyAsset(context, item.asset, "mp4")
    }

    private fun copyAsset(context: Context, asset: String, ext: String): String? {
        return try {
            context.filesDir.listFiles()?.filter { it.name.startsWith("wallpaper.") }?.forEach { it.delete() }
            val dest = File(context.filesDir, "wallpaper.$ext")
            context.assets.open(asset).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.length() == 0L) return null
            "local:${dest.absolutePath}"
        } catch (_: Throwable) {
            null
        }
    }

    private val cache = android.util.LruCache<String, ImageBitmap>(24)

    private fun decode(context: Context, category: String, index: Int, limit: Int): ImageBitmap? {
        return try {
            val bytes = context.assets.open(assetName(category, index)).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sample = 1
            while (longest / sample > limit) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    private fun assetName(category: String, index: Int): String =
        "walls/$category/${index.coerceIn(0, COUNT - 1)}.jpg"
}
