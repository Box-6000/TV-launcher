package com.launch.tv.data

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import com.launch.tv.model.TvSource

object TvSources {
    fun load(context: Context): List<TvSource> {
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val infos = manager.tvInputList.filter { info ->
            runCatching { info.isPassthroughInput }.getOrDefault(false)
        }
        val hdmi = infos.filter { it.id.contains("hdmi", ignoreCase = true) }.sortedBy { hardware(it.id) }
        val hdmiIds = hdmi.map { it.id }.toSet()
        val ports = hdmi.mapIndexed { index, info ->
            source(context, manager, info, "HDMI ${index + 1}")
        }
        val rest = infos
            .filter { it.id !in hdmiIds }
            .sortedBy { hardware(it.id) }
            .map { source(context, manager, it, fallback(it)) }
        return ports + rest
    }

    fun open(context: Context, id: String) {
        val uri = TvContract.buildChannelUriForPassthroughInput(id)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun source(context: Context, manager: TvInputManager, info: TvInputInfo, generated: String): TvSource {
        val loaded = info.loadLabel(context)?.toString()?.trim().orEmpty()
        val label = if (loaded.isBlank() || loaded.contains("Service", ignoreCase = true)) generated else loaded
        val state = runCatching { manager.getInputState(info.id) }.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
        val connected = state == TvInputManager.INPUT_STATE_CONNECTED
        val status = when (state) {
            TvInputManager.INPUT_STATE_CONNECTED -> "Сигнал есть"
            TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Нет сигнала"
            else -> "Не подключён"
        }
        return TvSource(info.id, label, connected, status)
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

    private fun hardware(id: String): Int =
        Regex("""HW(\d+)$""").find(id)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: Int.MAX_VALUE
}
