@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.launch.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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
import com.launch.tv.ui.theme.menuIconColor
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
    val cell = available / state.settings.columns
    val fittedIcon = state.settings.iconDp.dp.coerceIn(32.dp, (cell - 28.dp).coerceAtLeast(32.dp))
    val chrome = Chrome(
        cardWidth = cardWidth,
        cardHeight = cardWidth * 0.98f,
        icon = fittedIcon,
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
        Section.Settings to ("Настройки" to "settings"),
    )
    val focusManager = LocalFocusManager.current
    var menuOpen by remember { mutableStateOf(false) }
    var enterPage by remember { mutableStateOf(false) }
    val voiceFocus = remember { FocusRequester() }
    val menuLead = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    val pageLead = remember(state.section, state.settingsPage) { PageLead() }
    var holdMic by remember { mutableStateOf(false) }
    val iconTint = menuIconColor(state.settings.menuIconColor, LocalPalette.current.accent)
    val startPad by animateDpAsState(
        targetValue = if (menuOpen) 260.dp else 104.dp,
        animationSpec = tween(160),
        label = "menuPad",
    )
    LaunchedEffect(pageLead) {
        if (state.section != Section.Settings) return@LaunchedEffect
        holdMic = true
        delay(80)
        runCatching { pageLead.requester.requestFocus() }
        delay(400)
        holdMic = false
    }
    BackHandler(enabled = state.section == Section.Home && menuOpen) {
        runCatching { dockFocus.requestFocus() }
    }
    LaunchedEffect(enterPage, state.section) {
        if (enterPage) {
            delay(40)
            focusManager.moveFocus(FocusDirection.Right)
            enterPage = false
        }
    }
    CompositionLocalProvider(LocalPageLead provides pageLead) {
    Box(Modifier.fillMaxSize()) {
        Wallpaper(state.settings)
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = startPad, end = 36.dp, top = 12.dp, bottom = 8.dp),
        ) {
            StatusBar(
                title = if (state.section == Section.Home) "" else screenTitle(state),
                settings = state.settings,
                weather = state.weather,
                net = state.net,
                battery = state.battery,
                sources = state.sources,
                onOpenSource = viewModel::openSource,
                compact = state.section == Section.Home,
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (state.section) {
                    Section.Home -> HomeScreen(
                        favorites = feed.favorites.take(state.settings.dockSlots),
                        settings = state.settings,
                        weather = state.weather,
                        weatherLoading = state.weatherLoading,
                        net = state.net,
                        onOpen = { viewModel.openApp(it.packageName) },
                        onMenu = { viewModel.openMenu(it.packageName) },
                        onSection = viewModel::section,
                        recents = feed.recents,
                        watch = state.watch,
                        onRetryWeather = viewModel::retryWeather,
                        onOpenWatch = viewModel::openWatch,
                        dockFocus = dockFocus,
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
                        favorites = state.settings.favoriteInputs,
                        onOpen = viewModel::openSource,
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
        VoiceButton(
            onClick = viewModel::voiceSearch,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp)
                .focusRequester(voiceFocus)
                .onFocusChanged {
                    if (it.isFocused && holdMic) runCatching { pageLead.requester.requestFocus() }
                }
                .focusProperties { down = menuLead },
        )
        SideMenu(
            sections = sections,
            current = state.section,
            open = menuOpen,
            iconTint = iconTint,
            voiceFocus = voiceFocus,
            menuLead = menuLead,
            onOpenChange = { menuOpen = it },
            onPick = { section ->
                viewModel.section(section)
                enterPage = true
            },
            modifier = Modifier.align(Alignment.CenterStart),
        )
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
}

@Composable
private fun SideMenu(
    sections: List<Pair<Section, Pair<String, String>>>,
    current: Section,
    open: Boolean,
    iconTint: Color,
    voiceFocus: FocusRequester,
    menuLead: FocusRequester,
    onOpenChange: (Boolean) -> Unit,
    onPick: (Section) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember(sections.size) { List(sections.size) { FocusRequester() } }
    Column(
        modifier
            .width(if (open) 240.dp else 76.dp)
            .fillMaxHeight()
            .onFocusChanged { onOpenChange(it.hasFocus) }
            .padding(start = 14.dp, end = 8.dp, top = 72.dp, bottom = 12.dp),
    ) {
        sections.forEachIndexed { index, (section, meta) ->
            val shape = RoundedCornerShape(18.dp)
            val requester = if (index == 0) menuLead else requesters[index]
            val selected = section == current
            Surface(
                onClick = { onPick(section) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .height(48.dp)
                    .focusRequester(requester)
                    .focusProperties {
                        up = if (index == 0) voiceFocus else if (index == 1) menuLead else requesters[index - 1]
                        down = if (index == sections.lastIndex) FocusRequester.Cancel else requesters[index + 1]
                    }
                    .okPress({ onPick(section) }) {},
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.14f),
                ),
                border = ClickableSurfaceDefaults.border(
                    border = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = if (selected) 0.16f else 0f),
                        ),
                        shape = shape,
                    ),
                    focusedBorder = androidx.tv.material3.Border(
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
                        shape = shape,
                    ),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (open) 12.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (open) {
                        androidx.compose.foundation.layout.Arrangement.Start
                    } else {
                        androidx.compose.foundation.layout.Arrangement.Center
                    },
                ) {
                    DrawerGlyph(meta.second, Modifier.size(28.dp), iconTint)
                    if (open) {
                        Text(
                            meta.first,
                            color = Color.White,
                            fontSize = 18.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
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
    state.section == Section.Settings && state.settingsPage == SettingsPage.Wallpaper -> "Обои"
    state.section == Section.Settings && state.settingsPage == SettingsPage.Time -> "Время и погода"
    state.section == Section.Home -> "Главная"
    state.section == Section.Apps -> "Все приложения"
    state.section == Section.Inputs -> "Входы"
    state.section == Section.Search -> "Поиск"
    else -> "Настройки"
}
