package com.launch.installer

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.launch.installer.adb.AdbException
import com.launch.installer.adb.AdbKeys
import com.launch.installer.adb.AdbSession
import com.launch.installer.discover.TvFinder
import com.launch.installer.safety.buildSuggestions
import com.launch.installer.safety.classifyDevice
import com.launch.installer.safety.componentLines
import com.launch.installer.safety.deviceNote
import com.launch.installer.safety.disabledPackages
import com.launch.installer.safety.explainInstallFailure
import com.launch.installer.safety.homeComponent
import com.launch.installer.safety.identifyBrand
import com.launch.installer.safety.installedPackages
import com.launch.installer.safety.isProtected
import com.launch.installer.safety.safeComponent
import com.launch.installer.safety.safePackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class InstallViewModel(application: Application) : AndroidViewModel(application) {
    private val finder = TvFinder(application)
    private val keys = AdbKeys(application)
    private val restoreFile = File(application.filesDir, "restore.json")
    private val stateFlow = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = stateFlow

    private var session: AdbSession? = null
    private var scanJob: Job? = null
    private var workJob: Job? = null
    private var autoConnected = false

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val version = runCatching { readBundledVersion() }.getOrDefault("")
            stateFlow.update { it.copy(bundledVersion = version) }
        }
        rescan()
    }

    fun rescan() {
        session?.close()
        session = null
        autoConnected = false
        scanJob?.cancel()
        stateFlow.update {
            it.copy(phase = Phase.Scanning, status = "Ищу телевизоры в сети…", profile = null, error = null, wifiMissing = false)
        }
        scanJob = viewModelScope.launch {
            val online = finder.scan { devices ->
                stateFlow.update { current -> current.copy(devices = devices) }
            }
            stateFlow.update { current ->
                val status = when {
                    !online -> "Телефон не в Wi‑Fi. Подключите его к сети телевизора."
                    current.devices.isEmpty() -> "В сети пока нет телевизора с открытой отладкой."
                    else -> "Найдено устройств: ${current.devices.size}."
                }
                current.copy(
                    status = status,
                    wifiMissing = !online,
                    phase = if (current.phase == Phase.Scanning) Phase.Scanning else current.phase,
                )
            }
            val ready = stateFlow.value.devices.filter { it.kind == DeviceKind.Ready && it.port != null }
            if (!autoConnected && ready.size == 1 && stateFlow.value.phase == Phase.Scanning) {
                autoConnected = true
                connect(ready.first())
            }
        }
    }

    fun connect(device: FoundTv) {
        val port = device.port
        if (port == null || device.kind != DeviceKind.Ready) {
            stateFlow.update {
                it.copy(
                    phase = Phase.Failed,
                    error = device.hint.ifBlank { "На этом устройстве нет обычной отладки Android." },
                    status = device.name,
                )
            }
            return
        }
        workJob?.cancel()
        workJob = viewModelScope.launch {
            connectAndInspect(device.host, port, device.name)
        }
    }

    fun connectManual(host: String, portText: String) {
        val clean = host.trim()
        val port = portText.trim().toIntOrNull() ?: 5555
        val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        if (!ipv4.matches(clean) || clean.split('.').any { it.toIntOrNull() !in 0..255 } || port !in 1..65535) {
            stateFlow.update { it.copy(phase = Phase.Failed, error = "Нужен адрес вида 192.168.1.20 и порт, обычно 5555.") }
            return
        }
        connect(FoundTv(clean, port, null, "Телевизор", "$clean:$port", DeviceKind.Ready, ""))
    }

    fun toggle(packageName: String) {
        stateFlow.update { current ->
            current.copy(
                suggestions = current.suggestions.map { item ->
                    if (item.packageName == packageName && !item.alreadyOff) item.copy(checked = !item.checked) else item
                },
            )
        }
    }

    fun confirmUnknown(checked: Boolean) {
        stateFlow.update { it.copy(confirmUnknown = checked) }
    }

    fun install() {
        val current = stateFlow.value
        val profile = current.profile ?: return
        if (current.phase != Phase.Review) return
        if (profile.kind == TvKind.Phone) return
        if (profile.kind == TvKind.Unknown && !current.confirmUnknown) return
        val chosen = current.suggestions.filter { it.checked && !it.alreadyOff && safePackage(it.packageName) && !isProtected(it.packageName) }
        if (chosen.size > 8) {
            stateFlow.update { it.copy(phase = Phase.Failed, error = "Слишком много отмеченных приложений. Оставьте только оболочки домашнего экрана.") }
            return
        }
        workJob?.cancel()
        workJob = viewModelScope.launch {
            installLauncher(profile, chosen)
        }
    }

    fun restore() {
        val point = stateFlow.value.restore ?: return
        val profile = stateFlow.value.profile
        workJob?.cancel()
        workJob = viewModelScope.launch {
            val active = session
            if (active == null || profile == null) {
                stateFlow.update { it.copy(phase = Phase.Failed, error = "Сначала подключитесь к тому же телевизору.") }
                return@launch
            }
            stateFlow.update { it.copy(phase = Phase.Working, status = "Возвращаю выключенные приложения…", error = null) }
            try {
                point.disabled.forEach { packageName ->
                    if (!safePackage(packageName) || isProtected(packageName)) return@forEach
                    active.shell("pm enable --user 0 $packageName")
                }
                val previous = point.previousHome
                if (previous != null && safeComponent(previous) && !previous.startsWith("com.launch.tv/")) {
                    active.shell("cmd package set-home-activity --user 0 $previous")
                }
                clearRestore(point.serial)
                stateFlow.update {
                    it.copy(
                        phase = Phase.Done,
                        status = "Выключенные приложения снова включены.",
                        restore = null,
                        milestones = listOf(Milestone("Возврат", "Пакеты включены обратно", done = true, warn = false)),
                    )
                }
            } catch (error: Exception) {
                fail(error, "Не удалось вернуть приложения.")
            }
        }
    }

    fun backToList() {
        session?.close()
        session = null
        stateFlow.update {
            it.copy(phase = Phase.Scanning, profile = null, error = null, milestones = emptyList(), status = "Можно выбрать телевизор или искать снова.")
        }
    }

    override fun onCleared() {
        session?.close()
        super.onCleared()
    }

    private suspend fun connectAndInspect(host: String, port: Int, fallbackName: String) {
        session?.close()
        stateFlow.update { it.copy(phase = Phase.Connecting, status = "Подключаюсь к $host:$port…", error = null) }
        val connected = try {
            withContext(Dispatchers.IO) {
                AdbSession.connect(host, port, keys) {
                    stateFlow.update { it.copy(phase = Phase.WaitAllow, status = "На телевизоре нажмите «Разрешить» для отладки с этого телефона.") }
                }
            }
        } catch (error: Exception) {
            fail(error, "Не удалось подключиться.")
            return
        }
        session = connected
        stateFlow.update { it.copy(phase = Phase.Connecting, status = "Смотрю, что это за телевизор…") }
        try {
            val report = withContext(Dispatchers.IO) { inspect(connected) }
            val brand = identifyBrand(report.manufacturer, report.brand, report.model).let { info ->
                if (info.foreign) info.copy(foreign = false, title = info.title) else info
            }
            var kind = classifyDevice(report.characteristics, report.television, report.leanback, brand)
            if (report.model.equals(Build.MODEL, true) && report.manufacturer.equals(Build.MANUFACTURER, true)) {
                kind = TvKind.Phone
            }
            if (report.sdk in 1..22) {
                connected.close()
                session = null
                stateFlow.update {
                    it.copy(phase = Phase.Failed, error = "На телевизоре Android ${report.androidRelease}. Launch нужен Android 6 или новее. Ничего не изменено.")
                }
                return
            }
            if (kind == TvKind.Phone) {
                connected.close()
                session = null
                stateFlow.update {
                    it.copy(phase = Phase.Failed, error = "Это телефон или планшет, не телевизор. Установщик к нему ничего не ставит.")
                }
                return
            }
            val launcherVersion = stateFlow.value.bundledVersion.ifBlank {
                withContext(Dispatchers.IO) { runCatching { readBundledVersion() }.getOrDefault("из комплекта") }
            }.ifBlank { "из комплекта" }
            val profile = TvProfile(
                host = host,
                port = port,
                manufacturer = report.manufacturer,
                brand = report.brand,
                model = report.model.ifBlank { fallbackName },
                androidRelease = report.androidRelease,
                sdk = report.sdk,
                serial = report.serial,
                kind = kind,
                brandTitle = brand.title,
                previousHome = report.previousHome,
                note = deviceNote(kind, brand),
                launcherVersion = launcherVersion,
            )
            val suggestions = buildSuggestions(report.homes, report.extras, report.disabled, kind)
            val saved = loadRestore(report.serial)
            stateFlow.update {
                it.copy(
                    phase = Phase.Review,
                    status = "Телевизор найден. Можно ставить Launch.",
                    profile = profile,
                    suggestions = suggestions,
                    restore = saved,
                    confirmUnknown = false,
                    error = null,
                )
            }
        } catch (error: Exception) {
            fail(error, "Не удалось прочитать данные телевизора.")
        }
    }

    private fun inspect(session: AdbSession): Inspection {
        val dollar = "${'$'}"
        val props = listOf(
            "ro.product.manufacturer",
            "ro.product.brand",
            "ro.product.model",
            "ro.build.version.release",
            "ro.build.version.sdk",
            "ro.build.characteristics",
            "ro.serialno",
        )
        val command = buildString {
            props.forEach { append("echo PROP $it=$dollar(getprop $it); ") }
            append("echo PROP leanback=$dollar(cmd package has-feature android.software.leanback); ")
            append("echo PROP television=$dollar(cmd package has-feature android.hardware.type.television); ")
            append("echo __RESOLVE__; ")
            append("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; ")
            append("echo __QUERY__; ")
            append("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME; ")
            append("echo __DISABLED__; pm list packages -d; ")
            append("echo PKG com.mitv.tvhome.atv; pm path com.mitv.tvhome.atv; ")
            append("echo PKG com.mitv.tvhome; pm path com.mitv.tvhome")
        }
        val text = session.shell(command, 25_000).text
        val values = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("PROP ") && trimmed.contains('=')) {
                val body = trimmed.removePrefix("PROP ")
                values[body.substringBefore('=')] = body.substringAfter('=')
            }
        }
        val resolve = text.substringAfter("__RESOLVE__", "").substringBefore("__QUERY__")
        val query = text.substringAfter("__QUERY__", "").substringBefore("__DISABLED__")
        val disabledBlock = text.substringAfter("__DISABLED__", "")
        return Inspection(
            manufacturer = values["ro.product.manufacturer"].orEmpty(),
            brand = values["ro.product.brand"].orEmpty(),
            model = values["ro.product.model"].orEmpty(),
            androidRelease = values["ro.build.version.release"].orEmpty().ifBlank { "?" },
            sdk = values["ro.build.version.sdk"]?.toIntOrNull() ?: 0,
            characteristics = values["ro.build.characteristics"].orEmpty(),
            serial = values["ro.serialno"].orEmpty().ifBlank { values["ro.product.model"].orEmpty() },
            leanback = values["leanback"]?.contains("true") == true,
            television = values["television"]?.contains("true") == true,
            previousHome = homeComponent(resolve),
            homes = componentLines(query),
            disabled = disabledPackages(disabledBlock),
            extras = installedPackages(text),
        )
    }

    private suspend fun installLauncher(profile: TvProfile, chosen: List<Suggestion>) {
        val active = session ?: run {
            stateFlow.update { it.copy(phase = Phase.Failed, error = "Соединение с телевизором закрылось. Подключитесь снова.") }
            return
        }
        val steps = mutableListOf(
            Milestone("Установка Launch", "Ждёт", false, false),
            Milestone("Запуск", "Ждёт", false, false),
            Milestone("Кнопка Домой", "Ждёт", false, false),
            Milestone("Мешающие оболочки", if (chosen.isEmpty()) "Не выбраны" else "Ждёт", chosen.isEmpty(), false),
        )
        fun show(status: String) {
            stateFlow.update { it.copy(phase = Phase.Working, status = status, milestones = steps.toList(), error = null) }
        }
        show("Передаю лаунчер на телевизор…")
        try {
            val apk = withContext(Dispatchers.IO) { copyLauncher() }
            withContext(Dispatchers.IO) {
                active.push(apk, "/data/local/tmp/launch.apk") { progress ->
                    show("Передаю лаунчер, $progress%")
                }
                show("Устанавливаю Launch…")
                var installed = active.shell("pm install -r --user 0 /data/local/tmp/launch.apk", 120_000)
                if (!installed.text.contains("Success")) {
                    installed = active.shell("pm install -r /data/local/tmp/launch.apk", 120_000)
                }
                if (!installed.text.contains("Success")) {
                    throw AdbException(explainInstallFailure(installed.text))
                }
                val path = active.shell("pm path com.launch.tv").text
                if (!path.contains("package:")) throw AdbException("Пакет Launch на телевизоре не появился.")
                steps[0] = steps[0].copy(detail = "Версия ${profile.launcherVersion}", done = true)
                show("Открываю Launch…")
                val started = active.shell("am start -n com.launch.tv/.MainActivity")
                val startedOk = started.code == 0 && !started.text.contains("Error", true)
                steps[1] = steps[1].copy(
                    detail = if (startedOk) "Экран открыт" else "Установлен, но сам не открылся",
                    done = true,
                    warn = !startedOk,
                )
                show("Назначаю Launch домашним экраном…")
                active.shell("cmd package set-home-activity --user 0 com.launch.tv/.MainActivity")
                var resolved = homeComponent(active.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").text)
                if (resolved?.startsWith("com.launch.tv/") != true) {
                    active.shell("cmd package set-home-activity com.launch.tv/.MainActivity")
                    resolved = homeComponent(active.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").text)
                }
                val homeOk = resolved?.startsWith("com.launch.tv/") == true
                steps[2] = steps[2].copy(
                    detail = if (homeOk) "Домой открывает Launch" else "Система оставила прежний экран",
                    done = true,
                    warn = !homeOk,
                )
                val disabledNow = mutableListOf<String>()
                if (!homeOk) {
                    steps[3] = steps[3].copy(detail = "Не выключал: нет запасного домашнего экрана", done = true, warn = true)
                } else {
                    var stopDisabling = false
                    chosen.forEach { item ->
                        if (stopDisabling) return@forEach
                        if (isProtected(item.packageName) || !safePackage(item.packageName)) return@forEach
                        show("Выключаю ${item.title}…")
                        val result = active.shell("pm disable-user --user 0 ${item.packageName}")
                        val stillHome = homeComponent(
                            active.shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").text,
                        )
                        if (stillHome?.startsWith("com.launch.tv/") != true) {
                            active.shell("pm enable --user 0 ${item.packageName}")
                            steps[3] = steps[3].copy(detail = "${item.title} вернул: без него Домой ломается", done = true, warn = true)
                            stopDisabling = true
                            return@forEach
                        }
                        val accepted = result.text.contains("disabled", true) && !result.text.contains("SecurityException", true)
                        if (accepted) {
                            disabledNow += item.packageName
                            saveRestore(
                                RestorePoint(
                                    serial = profile.serial,
                                    host = profile.host,
                                    previousHome = profile.previousHome,
                                    disabled = disabledNow.toList(),
                                ),
                            )
                        }
                    }
                    if (steps[3].detail == "Ждёт" || steps[3].detail == "Не выбраны") {
                        steps[3] = steps[3].copy(
                            detail = if (disabledNow.isEmpty()) "Ничего выключать не пришлось" else "Выключено: ${disabledNow.size}",
                            done = true,
                            warn = false,
                        )
                    }
                }
                active.shell("rm -f /data/local/tmp/launch.apk")
                if (disabledNow.isNotEmpty()) {
                    saveRestore(
                        RestorePoint(
                            serial = profile.serial,
                            host = profile.host,
                            previousHome = profile.previousHome,
                            disabled = disabledNow,
                        ),
                    )
                }
                val saved = loadRestore(profile.serial)
                val headline = when {
                    !homeOk -> "Launch установлен. Кнопку Домой выберите на телевизоре, когда система спросит. Мешающие приложения не выключал."
                    disabledNow.isEmpty() -> "Launch установлен и открывается по кнопке Домой."
                    else -> "Launch установлен. Выключены оболочки, которые мешали кнопке Домой."
                }
                stateFlow.update {
                    it.copy(phase = Phase.Done, status = headline, milestones = steps.toList(), restore = saved, error = null)
                }
            }
        } catch (error: Exception) {
            runCatching { session?.shell("rm -f /data/local/tmp/launch.apk") }
            fail(error, "Установка остановилась. Телевизор не остался без домашнего экрана: чужие оболочки не выключались после сбоя.")
        }
    }

    private fun copyLauncher(): File {
        val app = getApplication<Application>()
        val out = File(app.cacheDir, "launcher.apk")
        app.assets.open("launcher.apk").use { input ->
            out.outputStream().use { input.copyTo(it) }
        }
        if (out.length() < 10_000) throw AdbException("В установщике нет файла лаунчера.")
        return out
    }

    private fun readBundledVersion(): String {
        val text = getApplication<Application>().assets.open("launcher-version.txt").bufferedReader().use { it.readText() }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.getOrNull(0).orEmpty()
        val code = lines.getOrNull(1).orEmpty()
        if (name.isBlank()) return ""
        return if (code.isBlank()) name else "$name · сборка $code"
    }

    private fun fail(error: Exception, fallback: String) {
        Log.e(TAG, fallback, error)
        val message = when (error) {
            is AdbException -> error.message ?: fallback
            else -> error.message?.takeIf { it.length < 180 } ?: fallback
        }
        stateFlow.update { it.copy(phase = Phase.Failed, error = message, status = "Остановка") }
    }

    private fun saveRestore(point: RestorePoint) {
        val all = readRestores().filterNot { it.serial == point.serial } + point
        writeRestores(all)
    }

    private fun loadRestore(serial: String): RestorePoint? =
        readRestores().firstOrNull { it.serial == serial && it.disabled.isNotEmpty() }

    private fun clearRestore(serial: String) {
        writeRestores(readRestores().filterNot { it.serial == serial })
    }

    private fun readRestores(): List<RestorePoint> {
        if (!restoreFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(restoreFile.readText())
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val disabled = item.optJSONArray("disabled") ?: JSONArray()
                    add(
                        RestorePoint(
                            serial = item.optString("serial"),
                            host = item.optString("host"),
                            previousHome = item.optString("previousHome").takeIf { it.isNotBlank() },
                            disabled = buildList {
                                for (i in 0 until disabled.length()) {
                                    val name = disabled.optString(i)
                                    if (safePackage(name)) add(name)
                                }
                            },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRestores(points: List<RestorePoint>) {
        val array = JSONArray()
        points.forEach { point ->
            array.put(
                JSONObject()
                    .put("serial", point.serial)
                    .put("host", point.host)
                    .put("previousHome", point.previousHome.orEmpty())
                    .put("disabled", JSONArray(point.disabled)),
            )
        }
        restoreFile.writeText(array.toString())
    }

    private data class Inspection(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val androidRelease: String,
        val sdk: Int,
        val characteristics: String,
        val serial: String,
        val leanback: Boolean,
        val television: Boolean,
        val previousHome: String?,
        val homes: List<String>,
        val disabled: Set<String>,
        val extras: Set<String>,
    )

    companion object {
        private const val TAG = "LaunchInstaller"
    }
}
