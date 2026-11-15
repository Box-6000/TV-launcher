package com.launch.tv.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.launch.tv.model.TvApp

class AppRepository(context: Context) {
    private val packageManager = context.packageManager
    private val self = context.packageName

    fun load(): List<TvApp> {
        val merged = LinkedHashMap<String, TvApp>()
        for (app in query(Intent.CATEGORY_LAUNCHER)) merged[app.packageName] = app
        for (app in query(Intent.CATEGORY_LEANBACK_LAUNCHER)) {
            merged[app.packageName] = app.copy(leanback = true)
        }
        return merged.values.toList()
    }

    private fun query(category: String): List<TvApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { info ->
            try {
                val activity = info.activityInfo ?: return@mapNotNull null
                val packageName = activity.packageName ?: return@mapNotNull null
                if (packageName == self) return@mapNotNull null
                val appInfo = activity.applicationInfo ?: return@mapNotNull null
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty().ifBlank { packageName }
                TvApp(
                    packageName = packageName,
                    label = label,
                    leanback = category == Intent.CATEGORY_LEANBACK_LAUNCHER,
                    system = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                    category = categoryOf(appInfo),
                    fresh = fresh(packageName),
                )
            } catch (_: Exception) {
                null
            }
        }.distinctBy { it.packageName }
    }

    private fun categoryOf(info: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT < 26) return "Другое"
        return when (info.category) {
            ApplicationInfo.CATEGORY_VIDEO -> "Видео"
            ApplicationInfo.CATEGORY_AUDIO -> "Музыка"
            ApplicationInfo.CATEGORY_GAME -> "Игры"
            ApplicationInfo.CATEGORY_SOCIAL -> "Общение"
            ApplicationInfo.CATEGORY_NEWS -> "Новости"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Инструменты"
            else -> "Другое"
        }
    }

    private fun fresh(packageName: String): Boolean = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        System.currentTimeMillis() - info.firstInstallTime < 3L * 24 * 60 * 60 * 1000
    }.getOrDefault(false)

    companion object {
        fun launch(context: Context, packageName: String) {
            val manager = context.packageManager
            val intent = manager.getLeanbackLaunchIntentForPackage(packageName)
                ?: manager.getLaunchIntentForPackage(packageName)
                ?: error("Нет экрана запуска")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
