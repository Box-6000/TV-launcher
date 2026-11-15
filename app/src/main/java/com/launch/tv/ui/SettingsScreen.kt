package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.launch.tv.BuildConfig
import com.launch.tv.LaunchViewModel
import com.launch.tv.PatchWall
import com.launch.tv.model.LaunchUiState
import com.launch.tv.model.SettingsPage
import com.launch.tv.model.effectiveCategory
import com.launch.tv.model.visibleApps
import com.launch.tv.ui.theme.LocalPalette
import com.launch.tv.ui.theme.paletteById
import com.launch.tv.ui.theme.sceneTitle

@Composable
fun SettingsScreen(
    state: LaunchUiState,
    viewModel: LaunchViewModel,
    onPickImage: () -> Unit,
) {
    when (state.settingsPage) {
        SettingsPage.Main -> MainSettings(state, viewModel, onPickImage)
        SettingsPage.Favorites -> AppToggleList(
            title = "OK добавляет в избранное. Влево и вправо меняют порядок.",
            apps = visibleApps(state.apps, state.settings),
            marked = state.settings.favorites.toSet(),
            onActivate = viewModel::toggleFavorite,
            onMove = { pkg, delta -> viewModel.moveFavorite(pkg, delta) },
        )
        SettingsPage.Hidden -> {
            val hidden = state.apps.filter { it.packageName in state.settings.hidden }
            if (hidden.isEmpty()) {
                Text("Скрытых приложений нет", color = LocalPalette.current.text, fontSize = 22.sp)
            } else {
                AppToggleList(
                    title = "OK возвращает приложение на главный экран.",
                    apps = hidden,
                    marked = emptySet(),
                    onActivate = viewModel::restore,
                    onMove = null,
                )
            }
        }
        SettingsPage.Categories -> CategorySettings(state, viewModel)
        SettingsPage.Weather -> WeatherSettings(state, viewModel)
        SettingsPage.IconSize -> IconSizeScreen(state, viewModel)
        SettingsPage.AppControl -> DisableAppsScreen(viewModel)
        SettingsPage.About -> AboutScreen()
        SettingsPage.Display -> DisplaySettings(state, viewModel)
    }
}

@Composable
private fun WeatherSettings(state: LaunchUiState, viewModel: LaunchViewModel) {
    val palette = LocalPalette.current
    var draft by rememberSaveable { mutableStateOf(state.settings.weatherPlace) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Город или регион. OK в поле открывает клавиатуру.",
            color = palette.muted,
            fontSize = 16.sp,
        )
        SearchField(value = draft, onValue = { draft = it.take(40) }, hint = "Например, Казань")
        ActionRow(
            title = "Сохранить",
            value = draft.ifBlank { "Авто" },
            supporting = "Погода для этого места",
            onClick = { viewModel.setWeatherPlace(draft) },
        )
        ActionRow(
            title = "Автоматически",
            value = "По сети",
            supporting = "Определять регион по подключению",
            onClick = {
                draft = ""
                viewModel.setWeatherPlace("")
            },
        )
        ActionRow(
            title = "Запомнить город",
            value = state.settings.weatherPlaces.size.toString(),
            supporting = "До шести городов в списке",
            onClick = { viewModel.rememberPlace(draft) },
        )
        state.settings.weatherPlaces.forEach { place ->
            ActionRow(place, "Выбрать", "OK ставит этот город. Влево убирает из списка") {
                viewModel.setWeatherPlace(place)
            }
        }
    }
}

@Composable
private fun MainSettings(
    state: LaunchUiState,
    viewModel: LaunchViewModel,
    onPickImage: () -> Unit,
) {
    val context = LocalContext.current
    val settings = state.settings
    val wallpaperLabel = if (settings.wallpaperUri != null) {
        "Своё изображение"
    } else {
        sceneTitle(settings.wallpaperId)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { CycleRow("Тема", paletteById(settings.themeId).title, "Меняет фото и цвет. На градиенте меняется только цвет", { viewModel.cycleTheme(false) }, { viewModel.cycleTheme(true) }) }
        item { CycleRow("Обои", wallpaperLabel, "Фотографии или градиент. Свой файл заменяет выбранное", { viewModel.cycleWallpaper(false) }, { viewModel.cycleWallpaper(true) }) }
        item { ActionRow("Выбрать изображение", "Файл", "Открыть изображение с телевизора", onPickImage) }
        item { ActionRow("Сбросить изображение", if (settings.wallpaperUri == null) "Нет" else "Есть", "Вернуться к выбранным обоям", { viewModel.setWallpaperUri(null) }) }
        item { ActionRow("Размер иконок", "${settings.iconDp}", "На экране видно, какого размера станет иконка", { viewModel.settingsPage(SettingsPage.IconSize) }) }
        item { CycleRow("Карточек в ряду", settings.columns.toString(), "От 4 до 8", { viewModel.changeColumns(-1) }, { viewModel.changeColumns(1) }) }
        item { CycleRow("Анимации", settings.motion.label, "Только меню. Иконки не увеличиваются", { viewModel.cycleMotion(false) }, { viewModel.cycleMotion(true) }) }
        item { CycleRow("Часы", if (settings.showClock) "Вкл" else "Выкл", "Центр главного экрана", { viewModel.toggleClock() }, { viewModel.toggleClock() }) }
        item { CycleRow("Дата", if (settings.showDate) "Вкл" else "Выкл", "Под часами", { viewModel.toggleDate() }, { viewModel.toggleDate() }) }
        item { CycleRow("Погода", if (settings.showWeather) "Вкл" else "Выкл", "Центр главного экрана", { viewModel.toggleWeather() }, { viewModel.toggleWeather() }) }
        item {
            ActionRow(
                title = "Регион погоды",
                value = settings.weatherPlace.ifBlank { "Авто" },
                supporting = "Город или регион. Пусто — по сети",
                onClick = { viewModel.settingsPage(SettingsPage.Weather) },
            )
        }
        item { ActionRow("Избранное", state.settings.favorites.size.toString(), "Добавление и порядок", { viewModel.settingsPage(SettingsPage.Favorites) }) }
        item { ActionRow("Скрытые", state.settings.hidden.size.toString(), "Вернуть убранные приложения", { viewModel.settingsPage(SettingsPage.Hidden) }) }
        item { ActionRow("Категории", state.settings.customCategories.size.toString(), "Свои полки на главном экране", { viewModel.settingsPage(SettingsPage.Categories) }) }
        item {
            ActionRow(
                title = "Домашний экран",
                value = if (state.homeRole) "Включено" else "Выбрать",
                supporting = "Тогда Launch откроется при включении телевизора",
                onClick = {
                    viewModel.toast(viewModel.requestHome(context))
                    viewModel.refreshStatus()
                },
            )
        }
        item {
            val patchWallOff = PatchWall.isDisabled(context)
            ActionRow(
                title = "PatchWall",
                value = if (patchWallOff) "Вернуть" else "Выключить",
                supporting = if (patchWallOff) "OK пытается вернуть PatchWall" else "Кнопка на пульте открывает Launch",
                onClick = {
                    viewModel.toast(
                        if (patchWallOff) PatchWall.restore(context) else PatchWall.disable(context),
                    )
                    viewModel.refreshApps()
                },
            )
        }
        item { ActionRow("Отключить приложения", "Список", "Важные помечены. Не важные можно выключить", { viewModel.settingsPage(SettingsPage.AppControl) }) }
        item { ActionRow("Экран", "Часы и док", "Размер часов, подписи, затемнение, сортировка", { viewModel.settingsPage(SettingsPage.Display) }) }
        item { ActionRow("О приложении", BuildConfig.VERSION_NAME, "Автор, состав и условия", { viewModel.settingsPage(SettingsPage.About) }) }
        item { ActionRow("Сбросить оформление", "OK", "Избранное и порядок останутся", { viewModel.resetLook() }) }
        item { ActionRow("Сохранить настройки", "Файл", "Запись остаётся на телевизоре", { viewModel.exportSettings() }) }
        item { ActionRow("Прочитать настройки", "Файл", "Берёт последнюю запись", { viewModel.importSettings() }) }
    }
}

@Composable
private fun DisplaySettings(state: LaunchUiState, viewModel: LaunchViewModel) {
    val settings = state.settings
    val labels = when (settings.dockLabels) {
        "always" -> "Всегда"
        "never" -> "Скрыты"
        else -> "В фокусе"
    }
    val sort = when (settings.sortMode) {
        "recent" -> "Недавние"
        "favorite" -> "Избранное"
        else -> "Имя"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { CycleRow("Размер часов", settings.clockSp.toString(), "Влево меньше, вправо больше", { viewModel.changeClockSize(-8) }, { viewModel.changeClockSize(8) }) }
        item { CycleRow("Формат времени", if (settings.clock24) "24 часа" else "12 часов", "Циферблат на главном экране", { viewModel.toggleClock24() }, { viewModel.toggleClock24() }) }
        item { CycleRow("Секунды", if (settings.showSeconds) "Вкл" else "Выкл", "Обновление каждую секунду", { viewModel.toggleSeconds() }, { viewModel.toggleSeconds() }) }
        item { CycleRow("Подложка часов", if (settings.clockPlate) "Вкл" else "Выкл", "Тёмное стекло под часами и погодой", { viewModel.toggleClockPlate() }, { viewModel.toggleClockPlate() }) }
        item { CycleRow("Подсказка", if (settings.showHints) "Вкл" else "Выкл", "OK и долгое OK на главном экране", { viewModel.toggleHints() }, { viewModel.toggleHints() }) }
        item { CycleRow("Док", if (settings.showDock) "Вкл" else "Выкл", "Избранное внизу экрана", { viewModel.toggleDock() }, { viewModel.toggleDock() }) }
        item { CycleRow("Подписи дока", labels, "Всегда, только в фокусе или скрыты", { viewModel.cycleDockLabels() }, { viewModel.cycleDockLabels() }) }
        item { CycleRow("Недавние", if (settings.showRecents) "Вкл" else "Выкл", "Полка над доком", { viewModel.toggleRecents() }, { viewModel.toggleRecents() }) }
        item { CycleRow("Погода в строку", if (settings.weatherInline) "Вкл" else "Выкл", "Градусы и город в одной строке", { viewModel.toggleWeatherInline() }, { viewModel.toggleWeatherInline() }) }
        item { CycleRow("Единицы", if (settings.fahrenheit) "°F" else "°C", "Только показ, запрос остаётся в Цельсиях", { viewModel.toggleFahrenheit() }, { viewModel.toggleFahrenheit() }) }
        item { CycleRow("Затемнение", "${settings.dimPercent}", "Насколько фото уступает тексту", { viewModel.changeDim(-4) }, { viewModel.changeDim(4) }) }
        item { CycleRow("Сортировка «Все»", sort, "Имя, недавние или избранное сверху", { viewModel.cycleSort() }, { viewModel.cycleSort() }) }
        item { CycleRow("Подтверждение", if (settings.confirmDisable) "Вкл" else "Выкл", "Повторный OK перед отключением программы", { viewModel.toggleConfirmDisable() }, { viewModel.toggleConfirmDisable() }) }
        item { CycleRow("Звук фокуса", if (settings.focusSound) "Вкл" else "Выкл", "Щелчок при переходе между иконками и строками", { viewModel.toggleFocusSound() }, { viewModel.toggleFocusSound() }) }
    }
}

@Composable
private fun CycleRow(
    title: String,
    value: String,
    supporting: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    SettingRow(
        title = title,
        value = value,
        supporting = supporting,
        onClick = onNext,
        modifier = Modifier.stepKeys(onPrev, onNext),
    )
}

@Composable
private fun ActionRow(title: String, value: String, supporting: String, onClick: () -> Unit) {
    SettingRow(title, value, supporting, onClick)
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassListItem(
        title = title,
        supporting = supporting,
        value = value,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun AppToggleList(
    title: String,
    apps: List<com.launch.tv.model.TvApp>,
    marked: Set<String>,
    onActivate: (String) -> Unit,
    onMove: ((String, Int) -> Unit)?,
) {
    val palette = LocalPalette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { Text(title, color = palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp)) }
        items(apps, key = { it.packageName }) { app ->
            GlassListItem(
                title = app.label,
                supporting = if (app.packageName in marked) "В избранном" else app.category,
                value = if (app.packageName in marked) "Да" else "",
                selected = app.packageName in marked,
                onClick = { onActivate(app.packageName) },
                modifier = Modifier.stepKeys(
                    onLeft = if (onMove != null && app.packageName in marked) ({ onMove(app.packageName, -1) }) else null,
                    onRight = if (onMove != null && app.packageName in marked) ({ onMove(app.packageName, 1) }) else null,
                ),
                leading = { AppIcon(app.packageName, 36.dp) },
            )
        }
    }
}

@Composable
private fun CategorySettings(state: LaunchUiState, viewModel: LaunchViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    val palette = LocalPalette.current
    Column(Modifier.fillMaxSize()) {
        Text("Свои категории появляются полками на главной.", color = palette.muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        SearchField(draft, { draft = it }, "Новая категория")
        Spacer(Modifier.height(10.dp))
        ActionRow("Добавить категорию", "OK", draft.ifBlank { "Введите название" }) {
            viewModel.addCategory(draft)
            draft = ""
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxSize()) {
            items(state.settings.customCategories, key = { it }) { name ->
                ActionRow(name, "Удалить", "OK удаляет категорию") { viewModel.deleteCategory(name) }
            }
        }
    }
}
