package com.launch.tv.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.File

data class WallpaperImage(val uri: Uri, val label: String)

object WallpaperSources {
    private val extensions = setOf("jpg", "jpeg", "png", "webp")

    fun removableMounted(context: Context): Boolean = volumes(context).isNotEmpty()

    fun list(context: Context): List<WallpaperImage> {
        val found = LinkedHashMap<String, WallpaperImage>()
        volumes(context).forEach { volume ->
            val root = if (Build.VERSION.SDK_INT >= 30) volume.directory else null
            if (root != null) walk(root, found, 0)
        }
        return found.values.toList()
    }

    private fun volumes(context: Context): List<StorageVolume> {
        if (Build.VERSION.SDK_INT < 24) return emptyList()
        val manager = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return manager.storageVolumes.filter { volume ->
            if (!volume.isRemovable) return@filter false
            val state = volume.state
            state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
        }
    }

    private fun walk(dir: File, found: LinkedHashMap<String, WallpaperImage>, depth: Int) {
        if (depth > 3 || found.size >= 80 || !dir.isDirectory) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        children.sortedBy { it.name.lowercase() }.forEach { child ->
            if (found.size >= 80) return
            if (child.isDirectory) {
                walk(child, found, depth + 1)
            } else if (child.extension.lowercase() in extensions && child.length() > 0L) {
                val uri = Uri.fromFile(child)
                found.putIfAbsent(uri.toString(), WallpaperImage(uri, child.name))
            }
        }
    }
}
