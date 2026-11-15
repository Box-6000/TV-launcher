package com.launch.tv

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

data class ManagedApp(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val importance: AppControl.Importance,
    val system: Boolean,
)

object AppControl {
    enum class Importance { Ordinary, Important, Critical }

    private val critical = setOf(
        "android",
        "com.android.systemui",
        "com.launch.tv",
        "com.android.settings",
        "com.android.tv.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.android.bluetooth",
        "com.mediatek.tv.service",
        "com.mediatek.tvinput",
    )

    fun list(context: Context): List<ManagedApp> {
        val pm = context.packageManager
        val locale = Locale("ru")
        return pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS).mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
            if (label.isBlank()) return@mapNotNull null
            val system = info.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            val rank = importance(info.packageName, system)
            val launchable = pm.getLaunchIntentForPackage(info.packageName) != null ||
                pm.getLeanbackLaunchIntentForPackage(info.packageName) != null
            val enabled = isEnabled(pm, info.packageName)
            if (!launchable && enabled && rank == Importance.Ordinary) return@mapNotNull null
            if (!launchable && enabled && rank == Importance.Important) return@mapNotNull null
            ManagedApp(info.packageName, label, enabled, rank, system)
        }.distinctBy { it.packageName }
            .sortedWith(compareBy<ManagedApp> { it.importance.ordinal }.thenBy { it.label.lowercase(locale) })
    }

    fun importance(packageName: String, system: Boolean): Importance = when {
        packageName in critical -> Importance.Critical
        system -> Importance.Important
        else -> Importance.Ordinary
    }

    fun badge(importance: Importance): String = when (importance) {
        Importance.Critical -> "Важное"
        Importance.Important -> "Важное"
        Importance.Ordinary -> "Не важное"
    }

    fun note(app: ManagedApp): String = when {
        !app.enabled -> "Сейчас отключено. OK включает снова."
        app.importance == Importance.Critical -> "Важное. Отключение заблокировано: без него телевизор может перестать работать."
        app.importance == Importance.Important -> "Важное встроенное. OK отключает его на этом телевизоре."
        else -> "Не важное. OK отключает приложение."
    }

    fun setEnabled(context: Context, packageName: String, enabled: Boolean): String {
        val pm = context.packageManager
        val system = runCatching { pm.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS) }
            .getOrNull()
            ?.let {
                it.flags and ApplicationInfo.FLAG_SYSTEM != 0 ||
                    it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0
            } ?: false
        if (!enabled && importance(packageName, system) == Importance.Critical) {
            return "Это важное приложение. Его нельзя отключать."
        }
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }
        val changed = runCatching {
            pm.setApplicationEnabledSetting(packageName, state, PackageManager.DONT_KILL_APP)
            isEnabled(pm, packageName) == enabled
        }.getOrDefault(false)
        if (changed) return if (enabled) "Приложение включено" else "Приложение отключено"
        val shell = shellChange(packageName, enabled)
        if (shell && isEnabled(pm, packageName) == enabled) {
            return if (enabled) "Приложение включено" else "Приложение отключено"
        }
        return "Система не разрешила изменить это приложение."
    }

    private fun isEnabled(pm: PackageManager, packageName: String): Boolean {
        return when (runCatching { pm.getApplicationEnabledSetting(packageName) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> false
            else -> true
        }
    }

    private fun shellChange(packageName: String, enabled: Boolean): Boolean {
        if (!packageName.matches(Regex("[A-Za-z0-9._]+"))) return false
        val command = if (enabled) {
            listOf("pm", "enable", "--user", "0", packageName)
        } else {
            listOf("pm", "disable-user", "--user", "0", packageName)
        }
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor() == 0 && !text.contains("SecurityException", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
}
