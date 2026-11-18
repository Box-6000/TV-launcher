package com.launch.tv.data

import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.io.File

class RecommendationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val row = parse(sbn) ?: return
        RecommendationHub.merge(this, listOf(row))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    private fun publish() {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        val rows = active.mapNotNull { parse(it) }
        if (rows.isNotEmpty()) RecommendationHub.merge(this, rows)
    }

    private fun parse(sbn: StatusBarNotification): RecommendationRow? {
        val notification = sbn.notification ?: return null
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        if (title.isBlank()) return null
        val recommendation = notification.category == Notification.CATEGORY_RECOMMENDATION
        if (!recommendation && !leanback(sbn.packageName)) return null
        if (sbn.packageName == packageName) return null
        val bitmap = picture(notification)
        if (bitmap == null && !recommendation) return null
        if (bitmap != null && !recommendation && bitmap.width < 64) return null
        val key = sbn.packageName + ":" + title
        val poster = bitmap?.let { save(it, key) } ?: imageUri(notification)
        return RecommendationRow(
            key = key,
            title = title,
            packageName = sbn.packageName,
            poster = poster,
            pending = notification.contentIntent,
        )
    }

    private fun leanback(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setPackage(packageName)
        return runCatching { packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) }
            .getOrNull()
            .orEmpty()
            .isNotEmpty()
    }

    private fun imageUri(notification: Notification): String? {
        val keys = notification.extras.keySet()
        for (key in keys) {
            val value = notification.extras.get(key) as? String ?: continue
            val picture = value.startsWith("http://") || value.startsWith("https://") || value.startsWith("content://")
            val named = key.contains("image", true) || key.contains("poster", true) || key.contains("art", true) || key.contains("picture", true)
            if (picture && named) return value
        }
        return null
    }

    private fun picture(notification: Notification): Bitmap? {
        val keys = listOf("android.picture", Notification.EXTRA_LARGE_ICON_BIG, Notification.EXTRA_LARGE_ICON)
        for (key in keys) {
            when (val value = notification.extras.get(key)) {
                is Bitmap -> if (value.width >= 48) return value
                is android.graphics.drawable.Icon -> raster(value.loadDrawable(this) ?: continue)?.let { return it }
            }
        }
        val icon = notification.getLargeIcon() ?: return null
        val drawable = icon.loadDrawable(this) ?: return null
        return raster(drawable)
    }

    private fun raster(drawable: Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        if (width < 48 || height < 48) return null
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun save(bitmap: Bitmap, key: String): String? {
        return try {
            val dir = File(filesDir, "recs").apply { mkdirs() }
            val file = File(dir, key.hashCode().toUInt().toString(16) + ".jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file.absolutePath
        } catch (_: Throwable) {
            null
        }
    }
}
