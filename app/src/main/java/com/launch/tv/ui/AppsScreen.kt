@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text
import android.view.KeyEvent as AndroidKeyEvent
import com.launch.tv.model.Catalog
import com.launch.tv.model.MoveTarget
import com.launch.tv.model.TvApp
import com.launch.tv.model.UserSettings
import com.launch.tv.model.effectiveCategory
import com.launch.tv.ui.theme.LocalChrome
import com.launch.tv.ui.theme.LocalPalette

@Composable
fun AppsScreen(
    apps: List<TvApp>,
    settings: UserSettings,
    filter: String,
    arrange: Boolean,
    onFilter: (String) -> Unit,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onMove: (MoveTarget, List<String>, String, Int) -> Unit,
) {
    val palette = LocalPalette.current
    val favoriteIds = settings.favorites.toSet()
    val categories = listOf("Все") + Catalog.all(settings)
    val shown = if (filter == "Все") apps else apps.filter { it.effectiveCategory(settings) == filter }
    val sorted = when (settings.sortMode) {
        "recent" -> shown.sortedBy { app ->
            val index = settings.recents.indexOf(app.packageName)
            if (index < 0) Int.MAX_VALUE else index
        }
        "favorite" -> shown.sortedWith(
            compareByDescending<TvApp> { it.packageName in favoriteIds }.thenBy { it.label.lowercase() },
        )
        else -> shown
    }
    val ids = sorted.map { it.packageName }
    Column(Modifier.fillMaxSize()) {
        Text("${sorted.size}", color = palette.muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { name ->
                FilterChip(selected = name == filter, onClick = { onFilter(name) }) {
                    Text(name)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (sorted.isEmpty()) {
            Text("В этой категории пусто", color = palette.muted, fontSize = 18.sp)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize().focusProperties {
                    onExit = {
                        if (requestedFocusDirection == FocusDirection.Left) cancelFocusChange()
                    }
                },
            ) {
                items(sorted, key = { it.packageName }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        AppTile(
                            app = app,
                            favorite = app.packageName in favoriteIds,
                            icon = LocalChrome.current.icon,
                            modifier = Modifier
                                .menuAction { onMenu(app) }
                                .arrangeMove(
                                    enabled = arrange,
                                    onLeft = { onMove(MoveTarget.Global, ids, app.packageName, -1) },
                                    onRight = { onMove(MoveTarget.Global, ids, app.packageName, 1) },
                                ),
                            onClick = { if (!arrange) onOpen(app) },
                            onLongClick = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    apps: List<TvApp>,
    settings: UserSettings,
    onQuery: (String) -> Unit,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
) {
    val palette = LocalPalette.current
    val favoriteIds = settings.favorites.toSet()
    val results = if (query.isBlank()) {
        apps.take(24)
    } else {
        apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, onQuery)
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (query.isBlank()) "Недавние и установленные" else "Найдено: ${results.size}",
            color = palette.muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (results.isEmpty()) {
            Text("Ничего не найдено", color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.packageName }) { app ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        AppTile(
                            app = app,
                            favorite = app.packageName in favoriteIds,
                            icon = LocalChrome.current.icon,
                            modifier = Modifier.menuAction { onMenu(app) },
                            onClick = { onOpen(app) },
                            onLongClick = { onMenu(app) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchField(value: String, onValue: (String) -> Unit, hint: String = "Название приложения") {
    val palette = LocalPalette.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    BackHandler(enabled = focused) { focusManager.clearFocus() }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (focused) 0.28f else 0.14f),
                        Color(0xE61C1C22),
                    ),
                ),
            )
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.22f),
                shape = RoundedCornerShape(22.dp),
            )
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val center = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                    event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_ENTER
                if (center && event.type == KeyEventType.KeyUp) {
                    keyboard?.show()
                    true
                } else {
                    false
                }
            },
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, color = Color.White.copy(alpha = 0.72f), fontSize = 16.sp)
                inner()
            }
        },
    )
}
