package com.launch.tv

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvInputManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.launch.tv.data.AppRepository
import com.launch.tv.data.RecommendationHub
import com.launch.tv.data.SettingsRepository
import com.launch.tv.data.SystemStatus
import com.launch.tv.data.VoiceSearch
import com.launch.tv.data.WallpaperLoader
import com.launch.tv.data.WeatherRepository
import com.launch.tv.model.Catalog
import com.launch.tv.model.LaunchUiState
import com.launch.tv.model.Motion
import com.launch.tv.model.MoveTarget
import com.launch.tv.model.Section
import com.launch.tv.model.UserSettings
import com.launch.tv.model.nextMenuIconColor
import com.launch.tv.model.SettingsPage
import com.launch.tv.model.TvApp
import com.launch.tv.model.swapAcross
import com.launch.tv.model.swapNeighbors
import com.launch.tv.model.visibleApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LaunchViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val appsRepo = AppRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val refreshMutex = Mutex()
    private var toastJob: Job? = null
    private var inputCallback: TvInputManager.TvInputCallback? = null
    private var recsBound = false
    private var programsAskedAt = 0L

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(LaunchUiState())
    private fun edit(block: (LaunchUiState) -> LaunchUiState) {
        _state.update(block)
    }
    val state = _state

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                edit { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            edit { it.copy(weatherLoading = true) }
            val cached = weatherRepo.cached()
            if (cached != null) edit { it.copy(weather = cached) }
            val fresh = weatherRepo.refresh(settingsRepo.current().weatherPlace)
            edit { it.copy(weather = fresh ?: it.weather, weatherLoading = false) }
        }
        viewModelScope.launch {
            val gate = async { delay(1700) }
            refreshApps()
            refreshStatus()
            refreshSources()
            refreshWatch()
            gate.await()
            edit { it.copy(booting = false) }
        }
        RecommendationHub.onChanged = { refreshWatch() }
        watchInputs()
        bindRecommendations()
        viewModelScope.launch {
            while (isActive) {
                delay(20_000)
                refreshStatus()
                refreshSources()
                refreshWatch()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L)
                val fresh = weatherRepo.refresh(_state.value.settings.weatherPlace)
                if (fresh != null) edit { it.copy(weather = fresh, weatherLoading = false) }
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch { refreshAppsNow() }
    }

    fun refreshStatus() {
        val context = getApplication<Application>()
        edit {
            it.copy(
                net = SystemStatus.net(context),
                battery = SystemStatus.battery(context),
                homeRole = isHome(context),
                recsAllowed = RecommendationHub.allowed(context),
            )
        }
        bindRecommendations()
    }

    fun refreshWatch() {
        val context = getApplication<Application>()
        askForPrograms(context)
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { com.launch.tv.data.WatchLibrary.load(context) }
            edit { current ->
                val allowed = RecommendationHub.allowed(context)
                if (current.watch == items && current.recsAllowed == allowed) current
                else current.copy(watch = items, recsAllowed = allowed)
            }
        }
    }

    fun openWatch(item: com.launch.tv.model.WatchItem) {
        val context = getApplication<Application>()
        if (RecommendationHub.send(item.launchKey)) return
        try {
            val parsed = item.intentUri?.takeIf { it.isNotBlank() }?.let { raw ->
                if (raw.startsWith("http://") || raw.startsWith("https://")) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(raw))
                } else {
                    Intent.parseUri(raw, Intent.URI_INTENT_SCHEME)
                }
            }
            val intent = parsed ?: context.packageManager.getLeanbackLaunchIntentForPackage(item.packageName)
                ?: context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (intent == null) {
                toast("Не удалось открыть")
                return
            }
            if (item.packageName.isNotBlank() && intent.component == null && intent.`package` == null) {
                intent.setPackage(item.packageName)
            }
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            val fallback = context.packageManager.getLeanbackLaunchIntentForPackage(item.packageName)
                ?: context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (fallback == null) {
                toast("Не удалось открыть")
            } else {
                runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { toast("Не удалось открыть") }
            }
        }
    }

    fun voiceSearch() {
        val message = VoiceSearch.open(getApplication())
        if (message != null) toast(message)
    }

    fun cycleMenuIconColor(forward: Boolean) = toggle { it.copy(menuIconColor = nextMenuIconColor(it.menuIconColor, forward)) }

    fun openRecommendationsAccess() {
        val context = getApplication<Application>()
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { toast("Откройте доступ к уведомлениям в системе") }
    }
    fun refreshSources() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { com.launch.tv.data.TvSources.load(context) }
            edit { it.copy(sources = sources) }
        }
    }

    fun openSource(source: com.launch.tv.model.TvSource) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            settingsRepo.update { it.copy(lastInputId = source.id) }
        }
        try {
            com.launch.tv.data.TvSources.open(context, source.id)
        } catch (_: Exception) {
            toast("Не удалось переключить вход")
        }
    }

    fun section(section: Section) {
        edit { current ->
            current.copy(
                section = section,
                menuPackage = null,
                arrange = if (section == Section.Home || section == Section.Apps) current.arrange else false,
                settingsPage = if (section == Section.Settings) current.settingsPage else SettingsPage.Main,
            )
        }
    }

    fun query(value: String) {
        edit { it.copy(query = value.take(48)) }
    }

    fun categoryFilter(value: String) {
        edit { it.copy(categoryFilter = value) }
    }

    fun toggleArrange() {
        edit { it.copy(arrange = !it.arrange) }
    }

    fun setArrange(enabled: Boolean) {
        edit { it.copy(arrange = enabled) }
    }

    fun openMenu(packageName: String) {
        edit { it.copy(menuPackage = packageName) }
    }

    fun closeMenu() {
        edit { it.copy(menuPackage = null) }
    }

    fun settingsPage(page: SettingsPage) {
        edit { it.copy(settingsPage = page) }
    }

    fun toast(text: String) {
        edit { it.copy(toast = text) }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2600)
            edit { if (it.toast == text) it.copy(toast = null) else it }
        }
    }

    fun openApp(packageName: String) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                val recents = listOf(packageName) + settings.recents.filter { it != packageName }
                settings.copy(recents = recents.take(12))
            }
            val context = getApplication<Application>()
            try {
                AppRepository.launch(context, packageName)
                closeMenu()
            } catch (_: Exception) {
                toast("Не удалось открыть приложение")
            }
        }
    }

    fun toggleFavorite(packageName: String) {
        viewModelScope.launch {
            val current = settingsRepo.current()
            if (packageName !in current.favorites && current.favorites.size >= 12) {
                toast("В доке не больше 12 приложений")
                return@launch
            }
            settingsRepo.update { settings ->
                if (packageName in settings.favorites) {
                    settings.copy(favorites = settings.favorites.filter { it != packageName })
                } else {
                    val next = settings.favorites + packageName
                    settings.copy(
                        favorites = next,
                        dockSlots = maxOf(settings.dockSlots, next.size).coerceIn(4, 12),
                    )
                }
            }
        }
    }

    fun hide(packageName: String) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                settings.copy(
                    hidden = settings.hidden + packageName,
                    favorites = settings.favorites.filter { it != packageName },
                    recents = settings.recents.filter { it != packageName },
                )
            }
            closeMenu()
            toast("Приложение скрыто")
        }
    }

    fun restore(packageName: String) {
        viewModelScope.launch {
            settingsRepo.update { settings -> settings.copy(hidden = settings.hidden - packageName) }
            toast("Приложение снова в списке")
        }
    }

    fun cycleCategory(packageName: String) {
        viewModelScope.launch {
            val app = _state.value.apps.find { it.packageName == packageName } ?: return@launch
            settingsRepo.update { settings ->
                val names = Catalog.all(settings)
                val current = settings.categoryOverrides[packageName] ?: app.category
                val next = names[(names.indexOf(current) + 1).mod(names.size.coerceAtLeast(1))]
                settings.copy(categoryOverrides = settings.categoryOverrides + (packageName to next))
            }
        }
    }

    fun move(target: MoveTarget, displayed: List<String>, packageName: String, delta: Int) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                when (target) {
                    MoveTarget.Favorites -> settings.copy(favorites = swapNeighbors(displayed, packageName, delta))
                    MoveTarget.Recents -> settings.copy(recents = swapNeighbors(displayed, packageName, delta))
                    MoveTarget.Global -> settings.copy(
                        order = swapAcross(settings.order, displayed, packageName, delta),
                    )
                }
            }
        }
    }

    fun moveGlobal(packageName: String, delta: Int) {
        val snapshot = _state.value
        val displayed = visibleApps(snapshot.apps, snapshot.settings).map { it.packageName }
        move(MoveTarget.Global, displayed, packageName, delta)
    }

    fun moveFavorite(packageName: String, delta: Int) {
        val favorites = _state.value.settings.favorites
        move(MoveTarget.Favorites, favorites, packageName, delta)
    }

    fun cycleTheme(forward: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                val themes = pairedThemes
                val index = themes.indexOfFirst { it.first == settings.themeId }.let { if (it < 0) 0 else it }
                val next = if (forward) themes[(index + 1) % themes.size] else themes[(index - 1 + themes.size) % themes.size]
                val keepPhoto = settings.wallpaperUri != null
                val keepGradient = settings.wallpaperId == "gradient" && settings.wallpaperUri == null
                settings.copy(
                    themeId = next.first,
                    wallpaperId = if (keepPhoto || keepGradient) settings.wallpaperId else next.second,
                )
            }
        }
    }

    fun cycleWallpaper(forward: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                val ids = wallpaperIds
                val index = ids.indexOf(settings.wallpaperId).let { if (it < 0) 0 else it }
                val next = if (forward) ids[(index + 1) % ids.size] else ids[(index - 1 + ids.size) % ids.size]
                settings.copy(wallpaperId = next, wallpaperUri = null)
            }
        }
    }

    fun importWallpaper(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { WallpaperLoader.import(context, uri) }
            if (saved == null) {
                toast("Не удалось прочитать изображение")
            } else {
                settingsRepo.update { it.copy(wallpaperUri = saved, wallpaperId = "custom") }
                toast("Свои обои установлены")
            }
        }
    }

    fun setWallpaperUri(uri: String?) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(wallpaperUri = uri) }
            toast(if (uri == null) "Обои сброшены" else "Свои обои установлены")
        }
    }

    fun useScene(id: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(wallpaperId = id, wallpaperUri = null) }
            toast("Обои установлены")
        }
    }

    fun useStudio(category: String, index: Int) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            toast("Ставлю фото")
            val saved = withContext(Dispatchers.IO) {
                com.launch.tv.data.WallpaperStudio.install(context, category, index)
            }
            if (saved == null) {
                toast("Не удалось поставить фото")
                return@launch
            }
            settingsRepo.update { it.copy(wallpaperUri = saved, wallpaperId = "studio:$category:$index") }
            toast("Обои установлены")
        }
    }

    fun changeIconSize(delta: Int) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(iconDp = (it.iconDp + delta).coerceIn(32, 120)) }
        }
    }

    fun changeColumns(delta: Int) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(columns = (it.columns + delta).coerceIn(4, 8)) }
        }
    }

    fun cycleMotion(forward: Boolean) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(motion = if (forward) it.motion.next() else it.motion.prev()) }
        }
    }

    fun toggleClock() = toggle { it.copy(showClock = !it.showClock) }
    fun toggleDate() = toggle { it.copy(showDate = !it.showDate) }
    fun toggleWeather() = toggle { it.copy(showWeather = !it.showWeather) }
    fun toggleClock24() = toggle { it.copy(clock24 = !it.clock24) }
    fun toggleSeconds() = toggle { it.copy(showSeconds = !it.showSeconds) }
    fun changeClockSize(delta: Int) = toggle { it.copy(clockSp = (it.clockSp + delta).coerceIn(72, 144)) }
    fun toggleDock() = toggle { it.copy(showDock = !it.showDock) }
    fun changeDockSlots(delta: Int) = toggle { it.copy(dockSlots = (it.dockSlots + delta).coerceIn(4, 12)) }
    fun cycleDockLabels() = toggle {
        val next = when (it.dockLabels) {
            "always" -> "never"
            "never" -> "focus"
            else -> "always"
        }
        it.copy(dockLabels = next)
    }
    fun toggleWeatherInline() = toggle { it.copy(weatherInline = !it.weatherInline) }
    fun toggleFahrenheit() = toggle { it.copy(fahrenheit = !it.fahrenheit) }
    fun changeDim(delta: Int) = toggle { it.copy(dimPercent = (it.dimPercent + delta).coerceIn(0, 70)) }
    fun toggleClockPlate() = toggle { it.copy(clockPlate = !it.clockPlate) }
    fun toggleHints() = toggle { it.copy(showHints = !it.showHints) }
    fun cycleSort() = toggle {
        val next = when (it.sortMode) {
            "name" -> "recent"
            "recent" -> "favorite"
            else -> "name"
        }
        it.copy(sortMode = next)
    }
    fun toggleRecents() = toggle { it.copy(showRecents = !it.showRecents) }
    fun toggleWatch() = toggle { it.copy(showWatch = !it.showWatch) }
    fun toggleConfirmDisable() = toggle { it.copy(confirmDisable = !it.confirmDisable) }
    fun toggleFocusSound() = toggle { it.copy(focusSound = !it.focusSound, focusChosen = true) }
    fun rememberPlace(raw: String) {
        val name = raw.trim().take(40)
        if (name.length < 2) {
            toast("Название города слишком короткое")
            return
        }
        viewModelScope.launch {
            settingsRepo.update { current ->
                when {
                    name.equals(current.weatherPlace, ignoreCase = true) && name in current.weatherPlaces -> current
                    current.weatherPlaces.any { it.equals(name, ignoreCase = true) } -> current
                    current.weatherPlaces.size >= 6 -> current
                    else -> current.copy(weatherPlaces = current.weatherPlaces + name)
                }
            }
            toast("Город сохранён в списке")
        }
    }
    fun forgetPlace(name: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(weatherPlaces = it.weatherPlaces.filterNot { place -> place.equals(name, ignoreCase = true) }) }
            toast("Город убран из списка")
        }
    }
    fun renameInput(id: String, raw: String) {
        val name = raw.trim().take(24)
        if (id.isBlank()) return
        viewModelScope.launch {
            settingsRepo.update { current ->
                val next = if (name.isBlank()) current.inputNames - id else current.inputNames + (id to name)
                current.copy(inputNames = next)
            }
            toast(if (name.isBlank()) "Имя входа сброшено" else "Вход назван «$name»")
        }
    }
    fun toggleFavoriteInput(id: String) {
        viewModelScope.launch {
            settingsRepo.update { current ->
                val next = if (id in current.favoriteInputs) current.favoriteInputs.filter { it != id } else current.favoriteInputs + id
                current.copy(favoriteInputs = next)
            }
        }
    }
    fun setCategory(packageName: String, name: String) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(categoryOverrides = it.categoryOverrides + (packageName to name)) }
            closeMenu()
            toast("Категория: $name")
        }
    }
    fun exportSettings() {
        val context = getApplication<Application>()
        val settings = _state.value.settings
        val json = org.json.JSONObject()
            .put("favorites", org.json.JSONArray(settings.favorites))
            .put("hidden", org.json.JSONArray(settings.hidden.toList()))
            .put("order", org.json.JSONArray(settings.order))
            .put("places", org.json.JSONArray(settings.weatherPlaces))
            .put("weatherPlace", settings.weatherPlace)
            .put("themeId", settings.themeId)
            .put("wallpaperId", settings.wallpaperId)
            .put("iconDp", settings.iconDp)
            .put("columns", settings.columns)
        val file = java.io.File(context.filesDir, "launch-backup.json")
        file.writeText(json.toString())
        toast("Настройки записаны в файл телевизора")
    }
    fun importSettings() {
        val context = getApplication<Application>()
        val file = java.io.File(context.filesDir, "launch-backup.json")
        if (!file.exists()) {
            toast("Сначала сохраните настройки")
            return
        }
        viewModelScope.launch {
            runCatching {
                val json = org.json.JSONObject(file.readText())
                settingsRepo.update { current ->
                    current.copy(
                        favorites = jsonArray(json, "favorites").ifEmpty { current.favorites },
                        hidden = jsonArray(json, "hidden").toSet().ifEmpty { current.hidden },
                        order = jsonArray(json, "order").ifEmpty { current.order },
                        weatherPlaces = jsonArray(json, "places").ifEmpty { current.weatherPlaces },
                        weatherPlace = json.optString("weatherPlace", current.weatherPlace),
                        themeId = json.optString("themeId", current.themeId),
                        wallpaperId = json.optString("wallpaperId", current.wallpaperId),
                        iconDp = json.optInt("iconDp", current.iconDp),
                        columns = json.optInt("columns", current.columns),
                    )
                }
            }.onSuccess { toast("Настройки прочитаны из файла") }
                .onFailure { toast("Файл настроек повреждён") }
        }
    }

    private fun jsonArray(json: org.json.JSONObject, key: String): List<String> {
        val array = json.optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }

    fun retryWeather() {
        setWeatherPlace(_state.value.settings.weatherPlace)
    }
    fun resetLook() {
        viewModelScope.launch {
            settingsRepo.update { current ->
                UserSettings(
                    favorites = current.favorites,
                    hidden = current.hidden,
                    order = current.order,
                    categoryOverrides = current.categoryOverrides,
                    customCategories = current.customCategories,
                    recents = current.recents,
                    seeded = true,
                )
            }
            toast("Оформление сброшено. Избранное и порядок сохранены")
        }
    }

    fun packageDetails(packageName: String): String {
        val context = getApplication<Application>()
        return runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            val version = info.versionName ?: "—"
            val path = info.applicationInfo?.sourceDir
            val mb = if (path == null) 0 else java.io.File(path).length() / (1024 * 1024)
            "$version · $mb МБ"
        }.getOrDefault("Сведения недоступны")
    }

    fun openPackageSettings(packageName: String) {
        val context = getApplication<Application>()
        val opened = runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
        toast(if (opened) "Открыты сведения системы" else "Система не открыла сведения")
        closeMenu()
    }

    fun requestUninstall(packageName: String) {
        if (packageName == "com.launch.tv") {
            toast("Лаунчер нельзя удалить из его меню")
            return
        }
        val context = getApplication<Application>()
        val opened = runCatching {
            val intent = Intent(Intent.ACTION_DELETE)
                .setData(android.net.Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.isSuccess
        toast(if (opened) "Подтвердите удаление на экране системы" else "Система не разрешила удаление")
        closeMenu()
    }

    fun setWeatherPlace(raw: String) {
        val place = raw.trim().take(40)
        viewModelScope.launch {
            edit { it.copy(weatherLoading = true) }
            settingsRepo.update { it.copy(weatherPlace = place) }
            val fresh = weatherRepo.refresh(place)
            if (fresh == null) {
                edit { it.copy(weatherLoading = false) }
                toast(if (place.isEmpty()) "Не удалось обновить погоду" else "Регион не найден или нет сети")
            } else {
                edit { it.copy(weather = fresh, weatherLoading = false) }
                toast(if (place.isEmpty()) "Регион определяется по сети" else "Погода: ${fresh.city}")
            }
        }
    }

    fun addCategory(raw: String) {
        val name = raw.trim()
        if (name.length < 2) {
            toast("Название слишком короткое")
            return
        }
        if (name.length > 24) {
            toast("Название слишком длинное")
            return
        }
        viewModelScope.launch {
            val current = settingsRepo.current()
            when {
                Catalog.all(current).any { it.equals(name, ignoreCase = true) } -> toast("Такая категория уже есть")
                current.customCategories.size >= 8 -> toast("Можно добавить не больше 8 категорий")
                else -> {
                    settingsRepo.update { it.copy(customCategories = it.customCategories + name) }
                    toast("Категория «$name» добавлена")
                }
            }
        }
    }

    fun deleteCategory(name: String) {
        viewModelScope.launch {
            settingsRepo.update { settings ->
                settings.copy(
                    customCategories = settings.customCategories.filter { it != name },
                    categoryOverrides = settings.categoryOverrides.filterValues { it != name },
                )
            }
            toast("Категория удалена")
        }
    }

    fun requestHome(context: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                        return "Launch уже открывается при включении"
                    }
                    context.startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return "Подтвердите Launch как домашний экран"
                }
            }
            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Выберите Launch в списке домашних экранов"
        } catch (_: Exception) {
            "Система не открыла выбор домашнего экрана"
        }
    }

    private fun toggle(block: (com.launch.tv.model.UserSettings) -> com.launch.tv.model.UserSettings) {
        viewModelScope.launch { settingsRepo.update(block) }
    }

    private suspend fun refreshAppsNow() {
        refreshMutex.withLock {
            val apps = withContext(Dispatchers.IO) { appsRepo.load() }
            settingsRepo.update { settings ->
                if (settings.seeded) {
                    settings
                } else {
                    settings.copy(
                        seeded = true,
                        favorites = seedFavorites(apps),
                        order = apps.map { it.packageName },
                    )
                }
            }
            edit { it.copy(apps = apps) }
        }
    }

    private fun seedFavorites(apps: List<TvApp>): List<String> {
        val known = apps.map { it.packageName }.toSet()
        val preferred = listOf(
            "com.google.android.youtube.tv",
            "com.google.android.youtube",
            "com.netflix.ninja",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.videos",
            "com.android.vending",
            "com.spotify.tv.client",
            "com.google.android.katniss",
            "com.android.tv.settings",
        ).filter { it in known }
        val rest = apps.filter { it.leanback && it.packageName !in preferred }.map { it.packageName }
        return (preferred + rest).distinct().take(8)
    }

    override fun onCleared() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
        inputCallback?.let { manager?.unregisterCallback(it) }
        RecommendationHub.onChanged = null
        super.onCleared()
    }

    private fun askForPrograms(context: Context) {
        val now = System.currentTimeMillis()
        if (now - programsAskedAt < 10 * 60 * 1000L) return
        programsAskedAt = now
        val packages = listOf(
            "ru.kinopoisk.tv",
            "gpm.tnt_premier",
            "ru.ivi.client",
            "ru.start.androidmobile",
            "com.vk.tv",
            "ru.rt.video.app.tv",
            "mobi.zona",
            "com.google.android.videos",
            "com.amazon.amazonvideo.livingroom",
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
        )
        for (pkg in packages) {
            val intent = Intent("android.media.tv.action.INITIALIZE_PROGRAMS").setPackage(pkg)
            runCatching { context.sendBroadcast(intent) }
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_000)
            val items = withContext(Dispatchers.IO) { com.launch.tv.data.WatchLibrary.load(context) }
            edit { current -> if (current.watch == items) current else current.copy(watch = items) }
        }
    }

    private fun bindRecommendations() {
        if (recsBound || Build.VERSION.SDK_INT < 24) return
        val context = getApplication<Application>()
        if (!RecommendationHub.allowed(context)) return
        recsBound = true
        runCatching {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(context, com.launch.tv.data.RecommendationListener::class.java),
            )
        }
    }

    private fun watchInputs() {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) {
                refreshSources()
            }

            override fun onInputAdded(inputId: String) {
                refreshSources()
            }

            override fun onInputRemoved(inputId: String) {
                refreshSources()
            }
        }
        inputCallback = callback
        manager.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    private fun isHome(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = context.getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roles.isRoleHeld(RoleManager.ROLE_HOME)
                }
            }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolved?.activityInfo?.packageName == context.packageName
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        val pairedThemes = listOf(
            "night" to "night",
            "aurora" to "aurora",
            "ocean" to "ocean",
            "ember" to "sunset",
            "forest" to "forest",
            "graphite" to "city",
        )
        val themeIds = pairedThemes.map { it.first }
        val wallpaperIds = listOf("night", "aurora", "ocean", "sunset", "forest", "city", "gradient")
    }
}
