package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.launch.tv.model.TvSource
import com.launch.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun InputsScreen(
    sources: List<TvSource>,
    lastInputId: String,
    names: Map<String, String>,
    favorites: List<String>,
    onOpen: (TvSource) -> Unit,
    onRename: (String, String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val palette = LocalPalette.current
    val lead = remember { FocusRequester() }
    var picked by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    val ordered = remember(sources, lastInputId, favorites) {
        sources.sortedWith(compareByDescending<TvSource> { it.id in favorites }.thenByDescending { it.id == lastInputId })
    }
    LaunchedEffect(ordered.firstOrNull()?.id) {
        delay(80)
        runCatching { lead.requestFocus() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            onRefresh()
        }
    }
    if (sources.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Входы не найдены", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Телевизор не сообщил HDMI и другие порты.",
                color = palette.muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        return
    }
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Входы", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "OK переключает порт. Долгое OK добавляет в избранные. Имя пишется снизу.",
            color = palette.muted,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ordered.forEachIndexed { index, source ->
                val title = names[source.id]?.takeIf { it.isNotBlank() } ?: source.label
                val shape = RoundedCornerShape(24.dp)
                Surface(
                    onClick = { onOpen(source) },
                    onLongClick = { onToggleFavorite(source.id) },
                    modifier = Modifier
                        .width(188.dp)
                        .height(132.dp)
                        .onFocusChanged {
                            if (it.isFocused) {
                                picked = source.id
                                draft = names[source.id].orEmpty()
                            }
                        }
                        .then(if (index == 0) Modifier.focusRequester(lead) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.18f),
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                            shape = shape,
                        ),
                    ),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (source.id in favorites) "★ $title" else title,
                            color = if (source.connected) palette.text else palette.muted,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            if (source.connected) "Сигнал есть" else source.status,
                            color = palette.muted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        if (picked.isNotBlank()) {
            SearchField(draft, { draft = it.take(24) }, "Имя входа")
            Surface(
                onClick = { onRename(picked, draft) },
                modifier = Modifier.padding(top = 10.dp).width(360.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                Text(
                    "Сохранить имя",
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
