package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.launch.tv.AppControl
import com.launch.tv.LaunchViewModel
import com.launch.tv.ManagedApp
import com.launch.tv.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DisableAppsScreen(viewModel: LaunchViewModel) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ManagedApp>>(emptyList()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("Все") }
    var tick by remember { mutableIntStateOf(0) }
    val confirmDisable = viewModel.state.collectAsState().value.settings.confirmDisable
    LaunchedEffect(tick) {
        apps = withContext(Dispatchers.IO) { AppControl.list(context) }
    }
    val shown = apps
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .filter {
            when (kind) {
                "Свои" -> !it.system
                "Встроенные" -> it.system
                else -> true
            }
        }
        .sortedBy { it.importance.ordinal }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                "Важные приложения лучше не трогать. Критичные стоят внизу и не отключаются.",
                color = palette.muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
            )
            SearchField(query, { query = it }, "Найти приложение")
            RowKind(kind) { kind = it }
        }
        items(shown, key = { it.packageName }) { app ->
            GlassListItem(
                title = app.label,
                supporting = AppControl.note(app),
                value = if (app.enabled) AppControl.badge(app.importance) else "Отключено",
                onClick = {
                    when {
                        !app.enabled -> {
                            viewModel.toast(AppControl.setEnabled(context, app.packageName, true))
                            confirm = null
                            viewModel.refreshApps()
                            tick++
                        }
                        app.importance == AppControl.Importance.Critical -> {
                            viewModel.toast("Это важное приложение. Его нельзя отключать.")
                            confirm = null
                        }
                        confirmDisable && confirm != app.packageName -> {
                            confirm = app.packageName
                            viewModel.toast("Нажмите OK ещё раз, чтобы отключить.")
                        }
                        else -> {
                            viewModel.toast(AppControl.setEnabled(context, app.packageName, false))
                            confirm = null
                            viewModel.refreshApps()
                            tick++
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun RowKind(current: String, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Все", "Свои", "Встроенные").forEach { name ->
            Surface(
                onClick = { onPick(name) },
                modifier = Modifier.weight(1f),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0xCC1C1C22),
                    focusedContainerColor = Color(0xF02A2A32),
                    contentColor = Color.White,
                    focusedContentColor = Color.White,
                ),
            ) {
                Text(
                    if (name == current) "$name · да" else name,
                    color = Color.White,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}
