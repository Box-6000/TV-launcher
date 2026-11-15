@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.launch.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.launch.tv.LaunchViewModel
import com.launch.tv.model.Catalog
import com.launch.tv.model.LaunchUiState
import com.launch.tv.model.Motion
import com.launch.tv.model.MoveTarget
import com.launch.tv.model.Section
import com.launch.tv.model.SettingsPage
import com.launch.tv.model.buildHome
import com.launch.tv.model.effectiveCategory
import com.launch.tv.model.visibleApps
import com.launch.tv.ui.theme.Chrome
import com.launch.tv.ui.theme.LaunchTheme
import com.launch.tv.ui.theme.LocalChrome
import com.launch.tv.ui.theme.LocalFocusSound
import com.launch.tv.ui.theme.LocalPalette
import com.launch.tv.ui.theme.paletteById
import kotlinx.coroutines.delay

@Composable
fun LaunchRoot(viewModel: LaunchViewModel, onPickImage: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val palette = paletteById(state.settings.themeId)
    val widthDp = LocalConfiguration.current.screenWidthDp
    val available = widthDp.dp - 150.dp
    val cardWidth = (available / state.settings.columns).coerceIn(
        124.dp,
        if (widthDp >= 1500) 280.dp else 210.dp,
    )
    val chrome = Chrome(
        cardWidth = cardWidth,
        cardHeight = cardWidth * 0.98f,
        icon = state.settings.iconDp.dp,
        motion = state.settings.motion,
    )
    BackHandler(enabled = state.menuPackage == null) {
        when {
            state.arrange -> viewModel.setArrange(false)
            state.section == Section.Settings && state.settingsPage != SettingsPage.Main ->
                viewModel.settingsPage(SettingsPage.Main)
            state.section != Section.Home -> viewModel.section(Section.Home)
        }
    }
    LaunchTheme(palette) {
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(Unit) { FocusTick.prepare(context) }
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalChrome provides chrome,
            LocalFocusSound provides state.settings.focusSound,
        ) {
            AnimatedContent(
                targetState = state.booting,
                transitionSpec = {
                    if (chrome.motion == Motion.Off) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(320)) togetherWith fadeOut(tween(180))
                    }
                },
                label = "boot",
            ) { booting ->
                if (booting) {
                    Box(Modifier.fillMaxSize()) {
                        Wallpaper(state.settings)
                        Splash()
                    }
                } else {
                    Shell(state, viewModel, onPickImage)
                }
            }
        }
    }
}

@Composable
private fun Shell(state: LaunchUiState, viewModel: LaunchViewModel, onPickImage: () -> Unit) {
    val visible = visibleApps(state.apps, state.settings)
    val feed = buildHome(state.apps, state.settings)
    val sections = listOf(
        Section.Home to ("Главная" to "home"),
        Section.Apps to ("Все" to "apps"),
        Section.Inputs to ("Входы" to "hdmi"),
        Section.Search to ("Поиск" to "search"),
        Section.Settings to ("Настройки" to "settings"),
    )
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val itemFocus = remember {
        sections.associate { it.first to FocusRequester() }
    }
    var drawerWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(drawerState.currentValue) {
        val open = drawerState.currentValue == DrawerValue.Open
        if (open && !drawerWasOpen) {
            val target = itemFocus[state.section] ?: itemFocus.getValue(Section.Home)
            repeat(4) {
                if (runCatching { target.requestFocus() }.isSuccess) return@repeat
                delay(40)
            }
        }
        drawerWasOpen = open
    }
    val glass = Color(0xE61C1C22)
    val glassSoft = Color(0xB31C1C22)
    Box(Modifier.fillMaxSize()) {
        Wallpaper(state.settings)
        NavigationDrawer(
            modifier = Modifier.fillMaxSize(),
            drawerState = drawerState,
            drawerContent = {
                val scope = this
                val open = hasFocus
                Column(
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp, vertical = 18.dp),
                ) {
                    Row(
                        Modifier.padding(start = 10.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Mark()
                        if (open) {
                            Spacer(Modifier.width(10.dp))
                            Text("Launch", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                    with(scope) {
                        sections.forEach { (section, meta) ->
                            NavigationDrawerItem(
                                selected = state.section == section,
                                onClick = { viewModel.section(section) },
                                modifier = Modifier.focusRequester(itemFocus.getValue(section)),
                                leadingContent = { DrawerGlyph(meta.second) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                    contentColor = Color.White,
                                    inactiveContentColor = Color.White.copy(alpha = 0.92f),
                                    focusedContainerColor = glass,
                                    focusedContentColor = Color.White,
                                    pressedContainerColor = glass,
                                    pressedContentColor = Color.White,
                                    selectedContainerColor = glassSoft,
                                    selectedContentColor = Color.White,
                                    focusedSelectedContainerColor = glass,
                                    focusedSelectedContentColor = Color.White,
                                    pressedSelectedContainerColor = glass,
                                    pressedSelectedContentColor = Color.White,
                                ),
                                border = NavigationDrawerItemDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(2.5.dp, Color.White),
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                                    focusedSelectedBorder = Border(
                                        border = BorderStroke(2.5.dp, Color.White),
                                        shape = RoundedCornerShape(14.dp),
                                    ),
                                ),
                                scale = NavigationDrawerItemDefaults.scale(
                                    focusedScale = LocalChrome.current.motion.scale,
                                ),
                            ) {
                                Text(meta.first)
                            }
                        }
                    }
                }
            },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 32.dp, top = 12.dp, bottom = 8.dp),
            ) {
                StatusBar(
                    title = if (state.section == Section.Home) "" else screenTitle(state),
                    settings = state.settings,
                    weather = state.weather,
                    net = state.net,
                    battery = state.battery,
                    compact = state.section == Section.Home,
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (state.section) {
                        Section.Home -> HomeScreen(
                            favorites = feed.favorites,
                            settings = state.settings,
                            weather = state.weather,
                            weatherLoading = state.weatherLoading,
                            net = state.net,
                            onOpen = { viewModel.openApp(it.packageName) },
                            onMenu = { viewModel.openMenu(it.packageName) },
                            onSection = viewModel::section,
                            recents = feed.recents,
                            onRetryWeather = viewModel::retryWeather,
                        )
                        Section.Apps -> AppsScreen(
                            apps = visible,
                            settings = state.settings,
                            filter = state.categoryFilter,
                            arrange = state.arrange,
                            onFilter = viewModel::categoryFilter,
                            onOpen = { viewModel.openApp(it.packageName) },
                            onMenu = { viewModel.openMenu(it.packageName) },
                            onMove = viewModel::move,
                        )
                        Section.Inputs -> InputsScreen(
                            sources = state.sources,
                            lastInputId = state.settings.lastInputId,
                            names = state.settings.inputNames,
                            favorites = state.settings.favoriteInputs,
                            onOpen = viewModel::openSource,
                            onRename = viewModel::renameInput,
                            onToggleFavorite = viewModel::toggleFavoriteInput,
                            onRefresh = viewModel::refreshSources,
                        )
                        Section.Search -> SearchScreen(
                            query = state.query,
                            apps = visible,
                            settings = state.settings,
                            onQuery = viewModel::query,
                            onOpen = { viewModel.openApp(it.packageName) },
                            onMenu = { viewModel.openMenu(it.packageName) },
                        )
                        Section.Settings -> SettingsScreen(state, viewModel, onPickImage)
                    }
                }
            }
        }
        state.toast?.let { ToastBanner(it) }
        val menuApp = state.apps.find { it.packageName == state.menuPackage }
        if (menuApp != null) {
            AppMenu(
                app = menuApp,
                favorite = menuApp.packageName in state.settings.favorites,
                category = menuApp.effectiveCategory(state.settings),
                onOpen = { viewModel.openApp(menuApp.packageName) },
                onFavorite = { viewModel.toggleFavorite(menuApp.packageName) },
                onHide = { viewModel.hide(menuApp.packageName) },
                onCategory = { viewModel.cycleCategory(menuApp.packageName) },
                categories = Catalog.all(state.settings),
                onPickCategory = { viewModel.setCategory(menuApp.packageName, it) },
                onMove = { viewModel.moveGlobal(menuApp.packageName, it) },
                onMoveFavorite = { viewModel.moveFavorite(menuApp.packageName, it) },
                onDetails = { viewModel.openPackageSettings(menuApp.packageName) },
                onUninstall = { viewModel.requestUninstall(menuApp.packageName) },
                details = viewModel.packageDetails(menuApp.packageName),
                onClose = viewModel::closeMenu,
            )
        }
    }
}

private fun screenTitle(state: LaunchUiState): String = when {
    state.section == Section.Settings && state.settingsPage == SettingsPage.Favorites -> "Избранное"
    state.section == Section.Settings && state.settingsPage == SettingsPage.Hidden -> "Скрытые"
    state.section == Section.Settings && state.settingsPage == SettingsPage.Categories -> "Категории"
    state.section == Section.Settings && state.settingsPage == SettingsPage.Weather -> "Погода"
    state.section == Section.Settings && state.settingsPage == SettingsPage.IconSize -> "Размер иконок"
    state.section == Section.Settings && state.settingsPage == SettingsPage.AppControl -> "Приложения"
    state.section == Section.Settings && state.settingsPage == SettingsPage.About -> "О приложении"
    state.section == Section.Settings && state.settingsPage == SettingsPage.Display -> "Экран"
    state.section == Section.Home -> "Главная"
    state.section == Section.Apps -> "Все приложения"
    state.section == Section.Inputs -> "Входы"
    state.section == Section.Search -> "Поиск"
    else -> "Настройки"
}
