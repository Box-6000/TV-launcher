package com.launch.tv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.delay

@Composable
fun InputsScreen(
    sources: List<TvSource>,
    lastInputId: String,
    favorites: List<String>,
    onOpen: (TvSource) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val lead = remember { FocusRequester() }
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
            Text("Входы не найдены", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Телевизор не сообщил HDMI и другие порты.",
                color = Color.White.copy(alpha = 0.75f),
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
        Text("Входы", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "OK переключает порт. Долгое OK добавляет в избранные.",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            itemsIndexed(ordered, key = { _, source -> source.id }) { index, source ->
                val shape = RoundedCornerShape(16.dp)
                Surface(
                    onClick = {},
                    modifier = Modifier
                        .width(92.dp)
                        .height(78.dp)
                        .then(if (index == 0) Modifier.focusRequester(lead) else Modifier)
                        .okPress({ onOpen(source) }, { onToggleFavorite(source.id) }),
                    shape = ClickableSurfaceDefaults.shape(shape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0xCC12161E),
                        focusedContainerColor = Color(0xF02A3348),
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            shape = shape,
                        ),
                    ),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DrawerGlyph(source.kind, Modifier.size(22.dp), Color.White)
                        Text(
                            text = if (source.id in favorites) "★ ${source.label}" else source.label,
                            color = if (source.connected) Color.White else Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
