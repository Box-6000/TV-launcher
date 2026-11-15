package com.launch.tv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.KeyEvent

object PatchWall {
    const val PACKAGE = "com.mitv.tvhome.atv"

    private val activities = listOf(
        "com.mitv.tvhome.atv.app.PatchWallActivity",
        "com.mitv.tvhome.atv.app.HomeIntentActivity",
        "com.mitv.tvhome.atv.app.BannerLogoA",
        "com.mitv.tvhome.atv.app.BannerLogoB",
        "com.mitv.tvhome.atv.app.BannerLogoC",
        "com.mitv.tvhome.atv.app.BannerLogoD",
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
        if (!installed(pm)) return true
        return activities.all { className ->
            val state = pm.getComponentEnabledSetting(ComponentName(PACKAGE, className))
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }
    }

    fun disable(context: Context): String {
        val pm = context.packageManager
        if (!installed(pm)) return "PatchWall не установлен"
        return try {
            activities.forEach { className ->
                pm.setComponentEnabledSetting(
                    ComponentName(PACKAGE, className),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            "PatchWall выключен. Кнопка открывает Launch"
        } catch (_: SecurityException) {
            "Нет прав выключить PatchWall из приложения"
        }
    }

    fun restore(context: Context): String {
        val pm = context.packageManager
        if (!installed(pm)) {
            val back = runCatching {
                ProcessBuilder("cmd", "package", "install-existing", PACKAGE)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0
            }.getOrDefault(false)
            return if (back) "PatchWall возвращён" else "Система не вернула PatchWall"
        }
        return try {
            activities.forEach { className ->
                pm.setComponentEnabledSetting(
                    ComponentName(PACKAGE, className),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            "PatchWall включён"
        } catch (_: SecurityException) {
            "Нет прав включить PatchWall из приложения"
        }
    }

    private fun installed(pm: PackageManager): Boolean = try {
        pm.getPackageInfo(PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
