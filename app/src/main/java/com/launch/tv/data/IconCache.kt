package com.launch.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

object IconCache {
    private val cache = LruCache<String, ImageBitmap>(48)
    private val gate = Semaphore(3)

    suspend fun get(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return gate.withPermit {
            cache.get(packageName)?.let { return@withPermit it }
            val image = withContext(Dispatchers.IO) {
                try {
                    val drawable = context.packageManager.getApplicationIcon(packageName).mutate()
                    val size = 128
                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, size, size)
                    drawable.draw(canvas)
                    bitmap.asImageBitmap()
                } catch (_: Throwable) {
                    null
                }
            }
            if (image != null) cache.put(packageName, image)
            image
        }
    }
}
