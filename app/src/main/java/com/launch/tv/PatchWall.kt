package com.launch.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent

object PatchWall {
    private val packages = listOf(
        "com.mitv.tvhome.atv",
        "com.mitv.tvhome",
    )

    private val knownActivities = listOf(
        "com.mitv.tvhome.atv.app.PatchWallActivity",
        "com.mitv.tvhome.atv.app.HomeIntentActivity",
        "com.mitv.tvhome.atv.app.BannerLogoA",
        "com.mitv.tvhome.atv.app.BannerLogoB",
        "com.mitv.tvhome.atv.app.BannerLogoC",
        "com.mitv.tvhome.atv.app.BannerLogoD",
        "com.mitv.tvhome.MainActivityUserMode",
        "com.mitv.tvhome.MainActivity",
    )

    private val buttonActions = setOf(
        "com.mitv.tvhome.intent.action.HOME",
        "com.mitv.tvhome.action.PathWallIntent",
        "com.mitv.tvhome.action.HotKeyDealIntent",
    )

    fun isButtonIntent(intent: Intent?): Boolean = intent?.action in buttonActions

    fun isRemoteKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_TV,
        KeyEvent.KEYCODE_TV_INPUT,
        KeyEvent.KEYCODE_GUIDE,
        -> true
        else -> false
    }

    fun isDisabled(context: Context): Boolean {
        val pm = context.packageManager
        val present = packages.filter { installed(pm, it) }
        if (present.isEmpty()) return true
        return present.all { packageName ->
            if (applicationOff(pm, packageName)) return@all true
            val home = activities(pm, packageName)
            home.isNotEmpty() && home.all { componentOff(pm, it) }
        }
    }

    fun disable(context: Context): String {
        val pm = context.packageManager
        val present = packages.filter { installed(pm, it) }
        if (present.isEmpty()) return "PatchWall не установлен"
        return try {
            present.forEach { packageName ->
                activities(pm, packageName).forEach { component ->
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            "PatchWall выключен. Кнопка открывает Launch"
        } catch (_: SecurityException) {
            "Нет прав выключить PatchWall из приложения"
        }
    }

    fun restore(context: Context): String {
        val pm = context.packageManager
        val present = packages.filter { installed(pm, it) }
        if (present.isEmpty()) {
            val back = packages.any { packageName ->
                runCatching {
                    ProcessBuilder("cmd", "package", "install-existing", packageName)
                        .redirectErrorStream(true)
                        .start()
                        .waitFor() == 0 && installed(pm, packageName)
                }.getOrDefault(false)
            }
            return if (back) "PatchWall возвращён" else "Система не вернула PatchWall"
        }
        return try {
            present.forEach { packageName ->
                if (applicationOff(pm, packageName)) {
                    pm.setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
                activities(pm, packageName).forEach { component ->
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
            "PatchWall включён"
        } catch (_: SecurityException) {
            "Нет прав включить PatchWall из приложения"
        }
    }

    private fun activities(pm: PackageManager, packageName: String): List<ComponentName> {
        val found = LinkedHashSet<ComponentName>()
        knownActivities
            .filter { it == packageName || it.startsWith("$packageName.") }
            .forEach { className ->
                if (componentExists(pm, packageName, className)) {
                    found.add(ComponentName(packageName, className))
                }
            }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName)
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_ALL
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, flags)
        }
        resolved.forEach { info ->
            val activity = info.activityInfo ?: return@forEach
            if (activity.packageName == packageName) {
                found.add(ComponentName(packageName, activity.name))
            }
        }
        return found.toList()
    }

    private fun componentExists(pm: PackageManager, packageName: String, className: String): Boolean =
        runCatching {
            val component = ComponentName(packageName, className)
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(component, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
            true
        }.getOrDefault(false)

    private fun applicationOff(pm: PackageManager, packageName: String): Boolean = runCatching {
        when (pm.getApplicationEnabledSetting(packageName)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
            -> true
            else -> false
        }
    }.getOrDefault(false)

    private fun componentOff(pm: PackageManager, component: ComponentName): Boolean {
        val state = pm.getComponentEnabledSetting(component)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
    }

    private fun installed(pm: PackageManager, packageName: String): Boolean = runCatching {
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, flags)
        }
        true
    }.getOrDefault(false)
}
