package com.launch.tv.model

import java.util.Locale

enum class Section { Home, Apps, Inputs, Search, Settings }

enum class SettingsPage { Main, Favorites, Hidden, Categories, Weather, IconSize, AppControl, About, Display, Wallpaper, Time }

enum class MoveTarget { Favorites, Recents, Global }

enum class Motion(val scale: Float, val label: String) {
    Off(1f, "Выкл"),
    Soft(1.04f, "Мягко"),
    Expressive(1.08f, "Выразительно"),
    ;

    fun next(): Motion = entries[(ordinal + 1) % entries.size]
    fun prev(): Motion = entries[(ordinal - 1 + entries.size) % entries.size]
}

enum class NetKind { Offline, Wifi, Ethernet }

data class TvApp(
    val packageName: String,
    val label: String,
    val leanback: Boolean,
    val system: Boolean,
    val category: String,
    val fresh: Boolean = false,
)

data class UserSettings(
    val themeId: String = "aurora",
    val wallpaperId: String = "ink",
    val wallpaperUri: String? = null,
    val iconDp: Int = 48,
    val columns: Int = 6,
    val motion: Motion = Motion.Expressive,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showWeather: Boolean = true,
    val weatherPlace: String = "",
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val order: List<String> = emptyList(),
    val categoryOverrides: Map<String, String> = emptyMap(),
    val customCategories: List<String> = emptyList(),
    val recents: List<String> = emptyList(),
    val seeded: Boolean = false,
    val clock24: Boolean = true,
    val showSeconds: Boolean = false,
    val clockSp: Int = 108,
    val showDock: Boolean = true,
    val dockSlots: Int = 8,
    val dockLabels: String = "focus",
    val weatherInline: Boolean = false,
    val fahrenheit: Boolean = false,
    val dimPercent: Int = 28,
    val clockPlate: Boolean = true,
    val showHints: Boolean = true,
    val sortMode: String = "name",
    val showRecents: Boolean = true,
    val showWatch: Boolean = true,
    val confirmDisable: Boolean = true,
    val lastInputId: String = "",
    val focusSound: Boolean = true,
    val focusChosen: Boolean = false,
    val weatherPlaces: List<String> = emptyList(),
    val inputNames: Map<String, String> = emptyMap(),
    val favoriteInputs: List<String> = emptyList(),
    val menuIconColor: String = "white",
)

data class WeatherInfo(
    val city: String,
    val tempC: Int,
    val code: Int,
    val outlook: String = "",
    val windMps: Int? = null,
    val pressureHpa: Int? = null,
)

fun weatherFacts(info: WeatherInfo): String {
    val wind = info.windMps?.let { "Ветер $it м/с" }
    val pressure = info.pressureHpa?.let { "${kotlin.math.round(it * 0.750062).toInt()} мм" }
    return listOfNotNull(wind, pressure).joinToString(" · ")
}

data class WatchItem(
    val title: String,
    val poster: String?,
    val intentUri: String?,
    val packageName: String,
    val launchKey: String? = null,
)

data class NetStatus(
    val kind: NetKind = NetKind.Offline,
    val level: Int = 0,
)

data class TvSource(
    val id: String,
    val label: String,
    val connected: Boolean,
    val status: String = if (connected) "Сигнал есть" else "Нет сигнала",
    val plugged: Boolean = connected,
    val kind: String = "hdmi",
)
data class CategoryRow(
    val title: String,
    val apps: List<TvApp>,
)

data class HomeFeed(
    val hero: TvApp?,
    val recents: List<TvApp>,
    val favorites: List<TvApp>,
    val recommended: List<TvApp>,
    val categories: List<CategoryRow>,
)

data class LaunchUiState(
    val booting: Boolean = true,
    val apps: List<TvApp> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val weather: WeatherInfo? = null,
    val weatherLoading: Boolean = false,
    val net: NetStatus = NetStatus(),
    val battery: Int? = null,
    val section: Section = Section.Home,
    val query: String = "",
    val arrange: Boolean = false,
    val menuPackage: String? = null,
    val settingsPage: SettingsPage = SettingsPage.Main,
    val toast: String? = null,
    val categoryFilter: String = "Все",
    val homeRole: Boolean = false,
    val sources: List<TvSource> = emptyList(),
    val watch: List<WatchItem> = emptyList(),
    val recsAllowed: Boolean = false,
)

val MenuIconColors = listOf("white", "accent", "amber", "cyan", "green", "blue", "pink")

fun menuIconTitle(id: String): String = when (id) {
    "accent" -> "Как тема"
    "amber" -> "Янтарный"
    "cyan" -> "Бирюза"
    "green" -> "Зелёный"
    "blue" -> "Синий"
    "pink" -> "Розовый"
    else -> "Белый"
}

fun nextMenuIconColor(id: String, forward: Boolean): String {
    val index = MenuIconColors.indexOf(id).let { if (it < 0) 0 else it }
    val next = if (forward) (index + 1) % MenuIconColors.size else (index - 1 + MenuIconColors.size) % MenuIconColors.size
    return MenuIconColors[next]
}

object Catalog {
    val builtin = listOf(
        "Видео",
        "Игры",
        "Музыка",
        "Общение",
        "Новости",
        "Инструменты",
        "Другое",
    )

    fun all(settings: UserSettings): List<String> = (builtin + settings.customCategories).distinct()
}

fun TvApp.effectiveCategory(settings: UserSettings): String =
    settings.categoryOverrides[packageName] ?: category

fun displayTemp(celsius: Int, fahrenheit: Boolean): Int =
    if (fahrenheit) celsius * 9 / 5 + 32 else celsius

fun tempUnit(fahrenheit: Boolean): String = if (fahrenheit) "°F" else "°C"

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Ясно"
    1, 2, 3 -> "Облачно"
    45, 48 -> "Туман"
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "Дождь"
    71, 73, 75, 77, 85, 86 -> "Снег"
    95, 96, 99 -> "Гроза"
    else -> "Погода"
}

private fun sortApps(apps: List<TvApp>): List<TvApp> {
    val locale = Locale("ru")
    return apps.sortedWith(
        compareByDescending<TvApp> { it.leanback }.thenBy { it.label.lowercase(locale) },
    )
}

fun orderedApps(pool: List<TvApp>, order: List<String>): List<TvApp> {
    if (pool.isEmpty()) return emptyList()
    val map = pool.associateBy { it.packageName }
    val placed = LinkedHashSet<String>()
    val result = ArrayList<TvApp>(pool.size)
    for (id in order) {
        val app = map[id] ?: continue
        if (placed.add(id)) result.add(app)
    }
    result.addAll(sortApps(pool.filter { it.packageName !in placed }))
    return result
}

fun visibleApps(apps: List<TvApp>, settings: UserSettings): List<TvApp> =
    orderedApps(apps.filter { it.packageName !in settings.hidden }, settings.order)

fun buildHome(apps: List<TvApp>, settings: UserSettings): HomeFeed {
    val visible = apps.filter { it.packageName !in settings.hidden }
    val byId = visible.associateBy { it.packageName }
    val favoriteIds = settings.favorites.toSet()
    val favorites = orderedApps(visible.filter { it.packageName in favoriteIds }, settings.favorites)
    val recents = settings.recents.mapNotNull { byId[it] }.distinctBy { it.packageName }.take(10)
    val hero = recents.firstOrNull() ?: favorites.firstOrNull() ?: visible.firstOrNull()
    val recentIds = recents.map { it.packageName }.toSet()
    fun pool(category: String) = orderedApps(
        visible.filter { app ->
            app.effectiveCategory(settings) == category &&
                app.packageName !in favoriteIds &&
                app.packageName !in recentIds &&
                app.packageName != hero?.packageName
        },
        settings.order,
    )
    val recommended = pool("Видео").ifEmpty { pool("Игры") }.take(12)
    val rows = Catalog.all(settings).mapNotNull { name ->
        val list = orderedApps(visible.filter { it.effectiveCategory(settings) == name }, settings.order)
        if (list.isEmpty()) null else CategoryRow(name, list)
    }
    return HomeFeed(hero, recents, favorites, recommended, rows)
}

fun swapNeighbors(list: List<String>, packageName: String, delta: Int): List<String> {
    val index = list.indexOf(packageName)
    if (index < 0) return list
    val target = index + delta
    if (target !in list.indices) return list
    val next = list.toMutableList()
    val item = next[index]
    next[index] = next[target]
    next[target] = item
    return next
}

fun swapAcross(order: List<String>, displayed: List<String>, packageName: String, delta: Int): List<String> {
    val index = displayed.indexOf(packageName)
    val target = index + delta
    if (index < 0 || target !in displayed.indices) return order
    val other = displayed[target]
    val full = order.toMutableList()
    for (id in displayed) if (id !in full) full.add(id)
    val left = full.indexOf(packageName)
    val right = full.indexOf(other)
    if (left < 0 || right < 0) return full
    full[left] = other
    full[right] = packageName
    return full
}
