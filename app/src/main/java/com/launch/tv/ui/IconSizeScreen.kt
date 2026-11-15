package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (sample != null) {
                    AppIcon(sample.packageName, size.dp)
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
            supporting = "Влево меньше, вправо больше. Сверху показан настоящий размер.",
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
