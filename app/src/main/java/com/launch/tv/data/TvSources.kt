package com.launch.tv.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import com.launch.tv.model.TvSource

object TvSources {
    fun load(context: Context): List<TvSource> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val infos = manager.tvInputList.filter { info ->
            val own = info.id.startsWith("com.mediatek.tvinput/")
            val passthrough = runCatching { info.isPassthroughInput }.getOrDefault(false)
            val preview = info.id.contains("Preview", ignoreCase = true)
            (own || passthrough) && !preview
        }
        val hdmi = infos.filter { it.id.contains("hdmi", ignoreCase = true) }
        val numbered = hdmi.filter { hardware(it.id) != Int.MAX_VALUE }.sortedBy { hardware(it.id) }
        val extra = hdmi.filter { hardware(it.id) == Int.MAX_VALUE }
        val ports = numbered.mapIndexed { index, info ->
            source(context, manager, info, "HDMI ${index + 1}")
        } + extra.map { source(context, manager, it, "HDMI") }
        val used = ports.map { it.id }.toSet()
        val rest = infos
            .filter { it.id !in used }
            .sortedBy { hardware(it.id) }
        val tuners = rest.filter {
            it.type == TvInputInfo.TYPE_TUNER || it.id.contains("tuner", ignoreCase = true)
        }
        val others = rest.filter { it.id !in tuners.map { tuner -> tuner.id }.toSet() }
        val tunerSources = tuners.mapIndexed { index, info ->
            source(context, manager, info, if (tuners.size == 1) "ТВ" else "ТВ ${index + 1}")
        }
        val otherSources = others.map { source(context, manager, it, fallback(it)) }
        return ports + otherSources + tunerSources
    }

    fun open(context: Context, id: String) {
        val xiaomi = xiaomiIntent(id)
        if (xiaomi != null && hasPlayer(context)) {
            context.startActivity(xiaomi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = TvContract.buildChannelUriForPassthroughInput(id)
        val live = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "vnd.android.cursor.item/channel")
            .putExtra("android.media.tv.extra.INPUT_ID", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (hasLiveTv(context)) {
            context.startActivity(live.setComponent(liveTv))
            return
        }
        context.startActivity(live)
    }

    private val liveTv = ComponentName(
        "com.mediatek.wwtv.tvcenter",
        "com.mitv.livetv.tv.MainActivity",
    )

    private fun hasLiveTv(context: Context): Boolean = runCatching {
        context.packageManager.getActivityInfo(liveTv, 0)
    }.isSuccess

    private fun hasPlayer(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.xiaomi.mitv.tvplayer", PackageManager.GET_ACTIVITIES)
    }.isSuccess

    private fun xiaomiIntent(id: String): Intent? {
        val lower = id.lowercase()
        val hw = hardware(id)
        val external = ComponentName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.ExternalSourceActivity")
        return when {
            "tuner" in lower && hw == 0 -> Intent("com.xiaomi.mitv.tvplayer.DTMB_PLAY")
                .setClassName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.dtmb.DTMBActivity")
            "tuner" in lower -> Intent("com.xiaomi.mitv.tvplayer.ATV_PLAY")
                .setClassName("com.xiaomi.mitv.tvplayer", "com.xiaomi.mitv.tvplayer.AtvActivity")
            "composite" in lower || "scart" in lower -> Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
                .setComponent(external)
                .putExtra("input", 2)
            "hdmi" in lower && hw in 5..12 -> Intent("com.xiaomi.mitv.tvplayer.EXTSRC_PLAY")
                .setComponent(external)
                .putExtra("input", hw + 18)
            else -> null
        }
    }

    private fun source(context: Context, manager: TvInputManager, info: TvInputInfo, generated: String): TvSource {
        val loaded = info.loadLabel(context)?.toString()?.trim().orEmpty()
        val label = if (loaded.isBlank() || loaded.contains("Service", ignoreCase = true)) generated else loaded
        val state = runCatching { manager.getInputState(info.id) }.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
        val connected = state == TvInputManager.INPUT_STATE_CONNECTED
        val plugged = connected || state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY
        val status = when (state) {
            TvInputManager.INPUT_STATE_CONNECTED -> "Сигнал есть"
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Нет сигнала"
            else -> "Не подключён"
        }
        return TvSource(info.id, label, connected, status, plugged, kind(info, generated))
    }

    private fun fallback(info: TvInputInfo): String {
        val id = info.id.lowercase()
        return when {
            "composite" in id -> "AV"
            "component" in id -> "Компонент"
            "scart" in id -> "SCART"
            "vga" in id -> "VGA"
            "dvi" in id -> "DVI"
            else -> "Вход"
        }
    }

    private fun kind(info: TvInputInfo, generated: String): String {
        val id = info.id.lowercase()
        val name = generated.lowercase()
        return when {
            info.type == TvInputInfo.TYPE_TUNER || "tuner" in id || name.startsWith("тв") -> "tuner"
            "hdmi" in id || name.startsWith("hdmi") -> "hdmi"
            "composite" in id || name == "av" -> "av"
            else -> "other"
        }
    }

    private fun hardware(id: String): Int =
        Regex("""HW(\d+)$""").find(id)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
}
