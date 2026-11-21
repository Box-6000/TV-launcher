package com.launch.tv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object VoiceSearch {
    private val packages = listOf(
        "com.xiaomi.voicecontrol",
        "com.xiaomi.mitv.voice",
        "com.xiaomi.voiceassistant",
        "com.xiaomi.tv.voiceassistant",
        "com.google.android.katniss",
        "com.google.android.googlequicksearchbox",
    )

    private val actions = listOf(
        Intent.ACTION_VOICE_COMMAND,
        Intent.ACTION_ASSIST,
        "android.intent.action.VOICE_ASSIST",
        "com.xiaomi.mitv.voice.START",
        "com.xiaomi.mitv.voice.REQUEST_VOICE",
        "com.xiaomi.mitv.voice.VOICE_KEY",
    )

    fun open(context: Context): String? {
        val pm = context.packageManager
        for (pkg in packages) {
            for (action in actions) {
                val intent = Intent(action).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (pm.resolveActivity(intent, 0) != null && start(context, intent)) return null
            }
            val activities = runCatching {
                pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES).activities?.toList()
            }.getOrNull().orEmpty()
            val named = activities.mapNotNull { info ->
                val name = info.name.lowercase()
                val rank = when {
                    "setting" in name || "guide" in name || "permission" in name -> return@mapNotNull null
                    "quicksearch" in name || "searchactivity" in name -> 0
                    "assist" in name -> 1
                    "voice" in name -> 2
                    else -> return@mapNotNull null
                }
                rank to info.name
            }.sortedBy { it.first }
            for ((_, name) in named) {
                val intent = Intent(Intent.ACTION_MAIN)
                    .setComponent(ComponentName(pkg, name))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (start(context, intent)) return null
            }
        }
        for (action in actions.take(3)) {
            val intent = Intent(action).addCategory(Intent.CATEGORY_DEFAULT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(intent, 0) != null && start(context, intent)) return null
        }
        return "Голосовой поиск не найден"
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
