package com.launch.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.launch.tv.model.NetKind
import com.launch.tv.model.NetStatus
import com.launch.tv.model.Section
import com.launch.tv.model.TvApp
import com.launch.tv.model.UserSettings
import com.launch.tv.model.WeatherInfo
import com.launch.tv.model.weatherLabel
import com.launch.tv.model.displayTemp
import com.launch.tv.model.tempUnit
import com.launch.tv.ui.theme.LocalFocusSound
import com.launch.tv.ui.theme.LocalPalette
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    favorites: List<TvApp>,
    settings: UserSettings,
    weather: WeatherInfo?,
    weatherLoading: Boolean,
    net: NetStatus,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onSection: (Section) -> Unit,
    recents: List<TvApp>,
    onRetryWeather: () -> Unit,
) {
    val lead = remember { FocusRequester() }
    LaunchedEffect(favorites.firstOrNull()?.packageName) {
        delay(120)
        runCatching { lead.requestFocus() }
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ClockStage(settings, weather, weatherLoading, net, onRetryWeather)
        }
        if (settings.showRecents && recents.isNotEmpty()) {
            RecentRow(recents, onOpen, onMenu)
        }
        if (settings.showDock) {
            Dock(
                favorites = favorites,
                iconDp = settings.iconDp,
                labels = settings.dockLabels,
                lead = lead,
                onOpen = onOpen,
                onMenu = onMenu,
                onSection = onSection,
            )
        }
    }
}

@Composable
private fun ClockStage(
    settings: UserSettings,
    weather: WeatherInfo?,
    weatherLoading: Boolean,
    net: NetStatus,
    onRetry: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (settings.clockPlate) {
            Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0x99141820))
                .padding(horizontal = 48.dp, vertical = 28.dp)
        } else {
            Modifier
        },
    ) {
        if (settings.showClock) {
            Text(
                text = rememberClock(settings.clock24, settings.showSeconds),
                color = palette.text,
                fontSize = settings.clockSp.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (settings.showDate) {
            Text(
                text = rememberDate(),
                color = palette.muted,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (settings.showWeather) {
            Spacer(Modifier.height(18.dp))
            WeatherBlock(settings, weather, weatherLoading, net, onRetry)
        }
        if (settings.showHints) {
            Text(
                "OK открывает  ·  долгое OK — меню",
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun WeatherBlock(
    settings: UserSettings,
    weather: WeatherInfo?,
    weatherLoading: Boolean,
    net: NetStatus,
    onRetry: () -> Unit,
) {
    val palette = LocalPalette.current
    if (weather != null) {
        val temp = "${displayTemp(weather.tempC, settings.fahrenheit)}${tempUnit(settings.fahrenheit)}"
        val caption = "${weatherLabel(weather.code)} · ${weather.city}"
        if (settings.weatherInline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(weather.code, palette.text)
                Text(
                    "  $temp   $caption",
                    color = palette.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            WeatherGlyph(weather.code, palette.text)
            Text(temp, color = palette.text, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
            Text(caption, color = palette.muted, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
            if (weather.outlook.isNotBlank()) {
                Text(weather.outlook, color = palette.muted, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    } else {
        val place = settings.weatherPlace.ifBlank { "ваш регион" }
        Text(
            text = when {
                net.kind == NetKind.Offline -> "Нет сети · $place"
                weatherLoading -> "Погода обновляется · $place"
                else -> "Погода недоступна · $place"
            },
            color = palette.muted,
            fontSize = 20.sp,
        )
        if (!weatherLoading) {
            androidx.tv.material3.Surface(
                onClick = onRetry,
                modifier = Modifier.padding(top = 10.dp),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                Text("Повторить", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun Dock(
    favorites: List<TvApp>,
    iconDp: Int,
    labels: String,
    lead: FocusRequester,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    onSection: (Section) -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)
    val size = iconDp.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .clip(shape)
                .background(palette.glass)
                .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (favorites.isEmpty()) {
                PlaceholderCard(
                    title = "Добавить",
                    subtitle = "Открыть список приложений",
                    onClick = { onSection(Section.Apps) },
                    modifier = Modifier.focusRequester(lead),
                )
            } else {
                favorites.forEach { app ->
                    DockIcon(
                        app = app,
                        size = size,
                        labels = labels,
                        modifier = if (app.packageName == favorites.first().packageName) {
                            Modifier.focusRequester(lead)
                        } else {
                            Modifier
                        },
                        onOpen = { onOpen(app) },
                        onMenu = { onMenu(app) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DockIcon(
    app: TvApp,
    size: androidx.compose.ui.unit.Dp,
    labels: String,
    modifier: Modifier,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(size * 0.23f)
    var focused by remember { mutableStateOf(false) }
    val sound = LocalFocusSound.current
    LaunchedEffect(focused) {
        if (focused && sound) FocusTick.play()
    }
    val caption = when (labels) {
        "always" -> app.label
        "never" -> ""
        else -> if (focused) app.label else ""
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(size + 12.dp)) {
        Text(
            text = caption.ifBlank { " " },
            color = palette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(20.dp),
        )
        androidx.tv.material3.Surface(
            onClick = {},
            modifier = modifier
                .padding(top = 6.dp)
                .width(size)
                .height(size)
                .iconFocusLift(focused)
                .onFocusChanged { focused = it.isFocused }
                .okPress(onOpen, onMenu)
                .menuAction(onMenu),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                    shape = shape,
                ),
            ),
        ) {
            AppIcon(app.packageName, size, Modifier.clip(shape))
        }
    }
}

@Composable
private fun RecentRow(
    recents: List<TvApp>,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        recents.take(8).forEach { app ->
            DockIcon(
                app = app,
                size = 40.dp,
                labels = "focus",
                modifier = Modifier.padding(horizontal = 6.dp),
                onOpen = { onOpen(app) },
                onMenu = { onMenu(app) },
            )
        }
    }
}

@Composable
private fun PlaceholderCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    androidx.tv.material3.Surface(
        onClick = onClick,
        modifier = modifier.width(220.dp).height(156.dp),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = palette.glassFocus,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
                shape = RoundedCornerShape(22.dp),
            ),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text(title, color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = palette.muted, fontSize = 13.sp)
        }
    }
}
