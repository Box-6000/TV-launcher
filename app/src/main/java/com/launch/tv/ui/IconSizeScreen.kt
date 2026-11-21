package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.launch.tv.LaunchViewModel
import com.launch.tv.model.LaunchUiState
import com.launch.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun IconSizeScreen(state: LaunchUiState, viewModel: LaunchViewModel) {
    val palette = LocalPalette.current
    val size = state.settings.iconDp
    val sample = state.apps.firstOrNull { it.packageName !in state.settings.hidden }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
    }
    val width = LocalConfiguration.current.screenWidthDp.dp
    val cell = (width - 150.dp) / state.settings.columns
    val fitted = size.dp.coerceIn(32.dp, (cell - 28.dp).coerceAtLeast(32.dp))
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val preview = fitted.coerceAtMost(minOf(maxWidth, maxHeight) - 48.dp).coerceAtLeast(32.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (sample != null) {
                    AppIcon(sample.packageName, preview)
                    Text(
                        sample.label,
                        color = palette.muted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        GlassListItem(
            title = "Размер иконки",
            supporting = "Влево меньше, вправо больше. Значок остаётся внутри ряда и дока.",
            value = "$size",
            onClick = { viewModel.changeIconSize(4) },
            modifier = Modifier
                .focusRequester(focus)
                .stepKeys(
                    onLeft = { viewModel.changeIconSize(-4) },
                    onRight = { viewModel.changeIconSize(4) },
                ),
        )
    }
}
