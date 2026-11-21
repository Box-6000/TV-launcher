package com.launch.installer.safety

import com.launch.installer.Suggestion
import com.launch.installer.TvKind

data class BrandInfo(
    val id: String,
    val title: String,
    val debugHint: String,
    val foreign: Boolean = false,
    val fire: Boolean = false,
)

private val componentPattern =
    Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+/\\.?[a-zA-Z][a-zA-Z0-9_$.]+$")

private val packagePattern =
    Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

private val recommendedHomes = setOf(
    "com.mitv.tvhome.atv",
    "com.mitv.tvhome",
    "com.google.android.tvlauncher",
    "com.google.android.apps.tv.launcherx",
    "com.google.android.leanbacklauncher",
    "com.tcl.waterfall.overseas",
    "com.tcl.cyberui",
    "com.hisense.tv.launcher",
    "com.jamdeo.tv.launcher",
    "org.droidtv.defaultdashboard",
    "com.coocaa.tv",
    "com.tianci.movieplatform",
)

private val titles = mapOf(
    "com.mitv.tvhome.atv" to "PatchWall",
    "com.mitv.tvhome" to "PatchWall",
    "com.google.android.tvlauncher" to "Лаунчер Android TV",
    "com.google.android.apps.tv.launcherx" to "Google TV",
    "com.google.android.leanbacklauncher" to "Leanback",
    "com.tcl.waterfall.overseas" to "Домашний экран TCL",
    "com.tcl.cyberui" to "Оболочка TCL",
    "com.hisense.tv.launcher" to "Hisense",
    "com.jamdeo.tv.launcher" to "Vidaa / Hisense",
    "org.droidtv.defaultdashboard" to "Philips",
    "com.coocaa.tv" to "Coocaa",
    "com.tianci.movieplatform" to "Skyworth",
    "com.spocky.projengmenu" to "Projectivy",
    "com.android.launcher3" to "Системный лаунчер",
    "com.google.android.apps.nexuslauncher" to "Лаунчер Pixel",
)

private val protectedExact = setOf(
    "android",
    "com.launch.tv",
    "com.launch.installer",
    "com.android.systemui",
    "com.android.settings",
    "com.android.tv.settings",
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.google.android.permissioncontroller",
    "com.android.providers.settings",
    "com.android.providers.media",
    "com.android.providers.media.module",
    "com.android.providers.tv",
    "com.android.providers.downloads",
    "com.android.bluetooth",
    "com.android.shell",
    "com.android.phone",
    "com.android.server.telecom",
    "com.android.networkstack",
    "com.android.captiveportallogin",
    "com.android.certinstaller",
    "com.android.vending",
    "com.android.providers.downloads.ui",
    "com.google.android.gms",
    "com.google.android.gsf",
    "com.google.android.ext.services",
    "com.google.android.setupwizard",
    "com.google.android.tungsten.setupwraith",
    "com.google.android.katniss",
    "com.google.android.backdrop",
    "com.google.android.tvrecommendations",
    "com.mediatek.tv.service",
    "com.mediatek.tvinput",
    "com.droidlogic.tvinput",
    "mitv.service",
    "com.xiaomi.mitv.tvmanager",
    "com.xiaomi.android.tvsetup.partnercustomizer",
    "com.mitv.livetv.ci_mediatek",
    "com.mitv.videoplayer",
    "com.mitv.tvlock",
    "com.mitv.milinkservice",
    "com.mitv.overseaservice",
    "com.xiaomi.mitv.updateservice",
    "com.amazon.tv.launcher",
    "com.amazon.firebat",
)

fun safePackage(name: String): Boolean = packagePattern.matches(name)

fun safeComponent(name: String): Boolean = componentPattern.matches(name)

fun isProtected(packageName: String): Boolean {
    val name = packageName.lowercase()
    if (packageName in protectedExact || name in protectedExact) return true
    if (name == "android" || name.startsWith("com.android.providers.")) return true
    if (name.contains("systemui") || name.contains("fallbackhome")) return true
    if (name.contains("setupwizard") || name.contains("provision") || name.contains(".setup")) return true
    if (name.contains("permissioncontroller") || name.contains("packageinstaller")) return true
    if (name.contains("tvinput") || name.contains("keyguard") || name.contains("bluetooth")) return true
    if (name.contains("inputmethod") || name.contains(".ime")) return true
    if (name.startsWith("com.mediatek.") || name.startsWith("com.droidlogic.") || name.startsWith("com.amlogic.")) return true
    if (name.startsWith("com.google.android.gms") || name.startsWith("com.android.networkstack")) return true
    if (name.endsWith(".settings") || name.contains(".settings.")) return true
    if (name.startsWith("com.amazon.") || name.startsWith("com.samsung.") || name.startsWith("com.sec.")) return true
    if (name.startsWith("com.lge.") || name.startsWith("com.sony.dtv.") || name.startsWith("com.dell.")) return true
    if (name.startsWith("com.xiaomi.tv.") || name.startsWith("com.mitv.tv.")) return true
    return false
}

fun componentLines(text: String): List<String> =
    text.lineSequence().map { it.trim() }.filter { safeComponent(it) }.distinct().toList()

fun homeComponent(text: String): String? = componentLines(text).firstOrNull()

fun packageOf(component: String): String = component.substringBefore('/')

fun identifyBrand(manufacturer: String, brand: String, model: String, extra: String = ""): BrandInfo {
    val text = "$manufacturer $brand $model $extra".lowercase()
    return when {
        "tizen" in text || (("samsung" in text || " tizen" in text) && "android" !in text && "mitv" !in text && "google tv" !in text && "bravia" !in text) &&
            ("tizen" in text || "samsung" in extra.lowercase()) ->
            BrandInfo("samsung", "Samsung", samsungHint, foreign = true)
        "webos" in text || "web0s" in text || "lge" in text || "lg electronics" in text ->
            BrandInfo("lg", "LG", "Телевизоры LG на webOS не принимают Android-лаунчер. Приложение к ним не подключается и ничего не меняет.", foreign = true)
        "roku" in text ->
            BrandInfo("roku", "Roku", "Roku — не Android. Launch на него не ставится, устройство не меняется.", foreign = true)
        "vidaa" in text && "android" !in text ->
            BrandInfo("vidaa", "Vidaa", "Это система Vidaa, не Android TV. Лаунчер на неё не ставится.", foreign = true)
        "apple tv" in text || "airplay" in text && "android" !in text ->
            BrandInfo("apple", "Apple TV", "Apple TV — не Android. Установщик к нему не подключается.", foreign = true)
        "amazon" in text || model.startsWith("AFT", true) || "fire tv" in text || "firetv" in text ->
            BrandInfo("fire", "Fire TV", fireHint, fire = true)
        "xiaomi" in text || "redmi" in text || "mitv" in text || brand.equals("mi", true) ->
            BrandInfo("xiaomi", "Xiaomi", xiaomiHint)
        "sony" in text || "bravia" in text ->
            BrandInfo("sony", "Sony", googleTvHint)
        "tcl" in text ->
            BrandInfo("tcl", "TCL", googleTvHint)
        "hisense" in text ->
            BrandInfo("hisense", "Hisense", googleTvHint)
        "philips" in text || "tp vision" in text || "tpvision" in text ->
            BrandInfo("philips", "Philips", googleTvHint)
        "sharp" in text || "aquos" in text ->
            BrandInfo("sharp", "Sharp", googleTvHint)
        "panasonic" in text ->
            BrandInfo("panasonic", "Panasonic", googleTvHint)
        "toshiba" in text ->
            BrandInfo("toshiba", "Toshiba", googleTvHint)
        "dell" in text ->
            BrandInfo("dell", "Dell", dellHint)
        "haier" in text ->
            BrandInfo("haier", "Haier", genericHint)
        "skyworth" in text || "coocaa" in text ->
            BrandInfo("skyworth", "Skyworth", genericHint)
        "changhong" in text ->
            BrandInfo("changhong", "Changhong", genericHint)
        "realme" in text ->
            BrandInfo("realme", "Realme", genericHint)
        "oneplus" in text ->
            BrandInfo("oneplus", "OnePlus", genericHint)
        "nokia" in text ->
            BrandInfo("nokia", "Nokia", googleTvHint)
        "nvidia" in text || "shield" in text ->
            BrandInfo("shield", "NVIDIA Shield", "На Shield: Настройки → Об устройстве → Сборка, 7 нажатий. Затем Для разработчиков → Отладка по сети.")
        "google" in text || "chromecast" in text ->
            BrandInfo("google", "Google TV", googleTvHint)
        "grundig" in text || "thomson" in text || "metz" in text || "jvc" in text || "vestel" in text ||
            "kivi" in text || "bbk" in text || "dexp" in text || "harper" in text || "hyundai" in text ->
            BrandInfo("tv", manufacturer.ifBlank { "Телевизор" }, genericHint)
        "mecool" in text || "ugoos" in text || "beelink" in text || "tanix" in text || "x96" in text || "h96" in text ->
            BrandInfo("box", "Андроид-приставка", genericHint)
        "samsung" in text ->
            BrandInfo("samsung", "Samsung", samsungHint, foreign = "android" !in text)
        else -> BrandInfo("generic", manufacturer.ifBlank { "Устройство" }, genericHint)
    }
}

fun classifyDevice(
    characteristics: String,
    television: Boolean,
    leanback: Boolean,
    brand: BrandInfo,
): TvKind {
    val traits = characteristics.lowercase()
    if (traits.contains("phone") || traits.contains("watch")) return TvKind.Phone
    if (traits.contains("tablet") && !traits.contains("tv") && !television && !leanback) return TvKind.Phone
    if (brand.fire) return TvKind.Fire
    if (traits.contains("tv") || television || leanback) return TvKind.Tv
    if (brand.id == "dell" || brand.id == "box") return TvKind.Box
    if (brand.id != "generic" && !brand.foreign) return TvKind.Tv
    return TvKind.Unknown
}

fun deviceNote(kind: TvKind, brand: BrandInfo): String = when (kind) {
    TvKind.Phone -> "Это похоже на телефон или планшет. Лаунчер для телевизора сюда ставить нельзя."
    TvKind.Fire -> "Fire TV принимает установку, но свой лаунчер Amazon не отдаёт. Его выключать нельзя: экран может пропасть. Launch будет в списке приложений."
    TvKind.Box -> "Похоже на приставку или панель. Ставлю только Launch и предлагаю выключить лишь то, что отвечает на кнопку Домой."
    TvKind.Unknown -> "Система не назвала устройство телевизором. Продолжить можно только если это точно телевизор или приставка."
    TvKind.Tv -> when (brand.id) {
        "dell" -> "Панель Dell: системные службы не трогаю. Можно выключить только подтверждённый домашний экран."
        "xiaomi" -> "Xiaomi: после установки можно выключить PatchWall, если он ещё стоит. Настройки, изображение и служба телевизора остаются."
        else -> "Сначала Launch становится домашним экраном. Только потом можно выключить оболочки, которые забирают кнопку Домой."
    }
}

fun buildSuggestions(
    homeComponents: List<String>,
    installedExtras: Set<String>,
    disabled: Set<String>,
    kind: TvKind,
): List<Suggestion> {
    if (kind == TvKind.Fire || kind == TvKind.Phone) return emptyList()
    val items = linkedMapOf<String, Suggestion>()
    homeComponents.forEach { component ->
        val packageName = packageOf(component)
        if (!safePackage(packageName) || packageName == "com.launch.tv" || isProtected(packageName)) return@forEach
        if (component.contains("FallbackHome", true)) return@forEach
        val recommended = packageName in recommendedHomes
        val off = packageName in disabled
        items[packageName] = Suggestion(
            packageName = packageName,
            title = titles[packageName] ?: packageName.substringAfterLast('.'),
            reason = if (off) {
                "Уже выключено."
            } else if (recommended) {
                "Отвечает на кнопку Домой. Выключение обратимо, вернуть можно из этого приложения."
            } else {
                "Тоже домашний экран. Отметьте его, только если он вам не нужен."
            },
            checked = recommended && !off,
            alreadyOff = off,
            recommended = recommended,
        )
    }
    listOf("com.mitv.tvhome.atv", "com.mitv.tvhome").forEach { packageName ->
        if (packageName !in installedExtras || packageName in items || isProtected(packageName)) return@forEach
        val off = packageName in disabled
        items[packageName] = Suggestion(
            packageName = packageName,
            title = "PatchWall",
            reason = if (off) "Уже выключено." else "На Xiaomi забирает свою кнопку. Выключение обратимо.",
            checked = !off,
            alreadyOff = off,
            recommended = true,
        )
    }
    return items.values.sortedWith(compareByDescending<Suggestion> { it.recommended }.thenBy { it.title })
}

fun explainInstallFailure(text: String): String = when {
    "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text ->
        "На телевизоре уже стоит Launch с другой подписью. Я его не удалял, чтобы не стереть настройки. Удалите старый Launch на телевизоре и повторите."
    "INSTALL_FAILED_VERSION_DOWNGRADE" in text ->
        "На телевизоре уже стоит более новая версия Launch."
    "INSTALL_FAILED_INSUFFICIENT_STORAGE" in text ->
        "На телевизоре не хватает места."
    "INSTALL_FAILED_OLDER_SDK" in text || "INSTALL_FAILED_NO_MATCHING_ABIS" in text ->
        "Этот телевизор не подходит для сборки Launch."
    else -> "Телевизор не принял установку. ${text.lineSequence().firstOrNull().orEmpty()}"
}

fun installedPackages(script: String): Set<String> {
    val found = mutableSetOf<String>()
    var current: String? = null
    script.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.startsWith("PKG ")) {
            current = line.removePrefix("PKG ").trim()
        } else if (line.startsWith("package:") && current != null && safePackage(current!!)) {
            found += current!!
        }
    }
    return found
}

fun disabledPackages(text: String): Set<String> =
    text.lineSequence()
        .map { it.trim().removePrefix("package:") }
        .filter { safePackage(it) }
        .toSet()

private const val genericHint =
    "Настройки → Об устройстве → 7 нажатий на номер сборки. Затем раздел для разработчиков: отладка по USB и отладка по сети. Телефон и телевизор должны быть в одном Wi‑Fi."

private const val xiaomiHint =
    "Xiaomi: Настройки → Об устройстве → 7 нажатий на версию Android. Затем Настройки → Дополнительно → Для разработчиков → Отладка по USB и Отладка по сети."

private const val googleTvHint =
    "Google TV, Sony, TCL, Hisense, Philips, Sharp и Nokia: Настройки → Система → Об устройстве → 7 нажатий на сборку. Для разработчиков → Отладка по USB. Если есть «Отладка по сети», включите её. Пункт только с кодом сопряжения — это шифрованный порт, через него установщик не ставит лаунчер."

private const val samsungHint =
    "Большинство Samsung работают на Tizen, не на Android. На них Launch не ставится, и это приложение ничего не меняет. Редкая Android-модель появится в списке только после отладки по сети."

private const val dellHint =
    "На Android-панели Dell включите режим разработчика и отладку по сети. Системные службы панели не отключаются."

private const val fireHint =
    "Fire TV: Настройки → My Fire TV → About → 7 нажатий. Developer options → ADB debugging и Network debugging. Лаунчер Amazon не выключается."
