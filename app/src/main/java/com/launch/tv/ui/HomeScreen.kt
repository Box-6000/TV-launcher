package com.launch.tv.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.launch.tv.model.Motion
import com.launch.tv.model.NetKind
import com.launch.tv.model.NetStatus
import com.launch.tv.model.Section
import com.launch.tv.model.TvApp
import com.launch.tv.model.UserSettings
import com.launch.tv.model.WeatherInfo
import com.launch.tv.model.weatherFacts
import com.launch.tv.model.weatherLabel
import com.launch.tv.model.displayTemp
import com.launch.tv.model.tempUnit
import com.launch.tv.ui.theme.LocalFocusSound
import com.launch.tv.ui.theme.LocalPalette
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.launch.tv.data.WallpaperLoader
import com.launch.tv.model.WatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    watch: List<WatchItem>,
    onRetryWeather: () -> Unit,
    onOpenWatch: (WatchItem) -> Unit,
    dockFocus: FocusRequester,
) {
    val lead = dockFocus
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { lead.requestFocus() }
    }
    val posters = watch.filter { !it.poster.isNullOrBlank() }
    val showShelf = settings.showWatch && posters.isNotEmpty()
    val showRecent = settings.showRecents && recents.isNotEmpty()
    val crowded = showShelf && showRecent
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            val room = maxHeight
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ClockStage(
                    settings,
                    weather,
                    weatherLoading,
                    net,
                    onRetryWeather,
                    available = room,
                )
            }
        }
        if (showShelf) {
            WatchShelf(posters, onOpenWatch, short = crowded)
        }
        if (showRecent) {
            RecentRow(recents, onOpen, onMenu, compact = crowded)
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
                maxIcon = if (crowded) 56 else 76,
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
    available: androidx.compose.ui.unit.Dp,
) {
    val palette = LocalPalette.current
    val tight = available < 300.dp
    val tiny = available < 190.dp
    val clockSize = when {
        tiny -> 46
        tight -> 60
        else -> settings.clockSp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (settings.clockPlate) {
            Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0x99141820))
                .padding(horizontal = if (tight) 28.dp else 48.dp, vertical = if (tiny) 4.dp else if (tight) 8.dp else 24.dp)
        } else {
            Modifier
        },
    ) {
        if (settings.showClock) {
            Text(
                text = rememberClock(settings.clock24, settings.showSeconds),
                color = palette.text,
                fontSize = clockSize.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (settings.showDate) {
            Text(
                text = rememberDate(),
                color = palette.muted,
                fontSize = if (tiny) 13.sp else if (tight) 15.sp else 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (settings.showWeather) {
            Spacer(Modifier.height(if (tiny) 2.dp else if (tight) 4.dp else 14.dp))
            WeatherBlock(settings, weather, weatherLoading, net, onRetry, tight, tiny)
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
    compact: Boolean,
    tiny: Boolean = false,
) {
    val palette = LocalPalette.current
    if (weather != null) {
        val temp = "${displayTemp(weather.tempC, settings.fahrenheit)}${tempUnit(settings.fahrenheit)}"
        val caption = "${weatherLabel(weather.code)} · ${weather.city}"
        val facts = weatherFacts(weather)
        val headline = when {
            tiny -> listOf(temp, facts).filter { it.isNotBlank() }.joinToString("  ")
            settings.weatherInline -> "$temp  $caption"
            else -> temp
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WeatherMotion(weather.code, settings.motion, if (tiny) 22.dp else if (compact) 28.dp else 52.dp)
            Text(
                text = headline,
                color = palette.text,
                fontSize = if (tiny) 16.sp else if (compact) 20.sp else 40.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (!tiny && !settings.weatherInline) {
                Text(
                    caption,
                    color = palette.muted,
                    fontSize = if (compact) 14.sp else 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
            if (!tiny && facts.isNotBlank()) {
                Text(
                    facts,
                    color = palette.text,
                    fontSize = if (compact) 14.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (!compact && !tiny && !settings.weatherInline && weather.outlook.isNotBlank()) {
                Text(
                    weather.outlook,
                    color = palette.muted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
                onClick = {},
                modifier = Modifier.padding(top = 10.dp).okPress(onRetry) {},
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                Text("Повторить", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun WeatherMotion(code: Int, motion: Motion, size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "weather")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (code == 0 || code in 1..3 || code == 45 || code == 48) 8000 else 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val tick = if (motion == Motion.Off) 0f else phase
    Canvas(Modifier.size(size).clipToBounds()) {
        val sun = androidx.compose.ui.graphics.Color(0xFFFFE7A3)
        val cloud = Color.White.copy(alpha = 0.92f)
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        when (code) {
            0 -> {
                drawCircle(sun, radius = this.size.minDimension * 0.16f, center = center)
                rotate(tick * 360f, center) {
                    repeat(8) { index ->
                        rotate(index * 45f, center) {
                            drawLine(
                                sun,
                                Offset(center.x, center.y - this.size.minDimension * 0.28f),
                                Offset(center.x, center.y - this.size.minDimension * 0.42f),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
            45, 48 -> {
                repeat(3) { index ->
                    val shift = sin((tick + index * 0.2f) * PI.toFloat() * 2f) * this.size.width * 0.08f
                    drawLine(
                        Color.White.copy(alpha = 0.45f),
                        Offset(this.size.width * 0.18f + shift, this.size.height * (0.38f + index * 0.14f)),
                        Offset(this.size.width * 0.82f + shift, this.size.height * (0.38f + index * 0.14f)),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            71, 73, 75, 77, 85, 86 -> {
                cloud(center.x, this.size.height * 0.34f, cloud)
                repeat(6) { index ->
                    val progress = (tick + index * 0.16f) % 1f
                    val sway = sin((tick * 2f + index) * PI.toFloat()) * 6.dp.toPx()
                    val x = this.size.width * (0.28f + (index % 3) * 0.18f) + sway
                    val y = this.size.height * (0.48f + progress * 0.42f)
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = Offset(x, y))
                }
            }
            95, 96, 99 -> {
                cloud(center.x, this.size.height * 0.32f, cloud)
                rain(tick)
                if (tick < 0.12f) {
                    drawCircle(Color.White.copy(alpha = 0.85f), radius = 4.dp.toPx(), center = Offset(center.x, this.size.height * 0.62f))
                }
            }
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> {
                cloud(center.x, this.size.height * 0.32f, cloud)
                rain(tick)
            }
            else -> {
                val drift = sin(tick * PI.toFloat() * 2f) * this.size.width * 0.04f
                cloud(center.x + drift, this.size.height * 0.48f, cloud)
                cloud(center.x - this.size.width * 0.16f + drift, this.size.height * 0.58f, Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.cloud(cx: Float, cy: Float, color: Color) {
    val scale = size.minDimension / 120.dp.toPx()
    drawCircle(color, 18.dp.toPx() * scale, Offset(cx, cy))
    drawCircle(color, 13.dp.toPx() * scale, Offset(cx - 16.dp.toPx() * scale, cy + 6.dp.toPx() * scale))
    drawCircle(color, 11.dp.toPx() * scale, Offset(cx + 16.dp.toPx() * scale, cy + 5.dp.toPx() * scale))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rain(tick: Float) {
    repeat(5) { index ->
        val progress = (tick + index * 0.18f) % 1f
        val x = size.width * (0.32f + index * 0.09f)
        val y = size.height * (0.46f + progress * 0.42f)
        drawLine(
            Color.White.copy(alpha = 0.9f),
            Offset(x, y),
            Offset(x - 4.dp.toPx(), y + 14.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WatchShelf(items: List<WatchItem>, onOpen: (WatchItem) -> Unit, short: Boolean) {
    val palette = LocalPalette.current
    Column(Modifier.fillMaxWidth().height(if (short) 112.dp else 144.dp)) {
        Text(
            "Недавние фильмы и передачи",
            color = palette.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 20.dp),
        ) {
            items(items, key = { it.packageName + "|" + it.title }) { item ->
                WatchPoster(item, onOpen, short)
            }
        }
    }
}

@Composable
private fun WatchPoster(item: WatchItem, onOpen: (WatchItem) -> Unit, short: Boolean) {
    val context = LocalContext.current
    val poster by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.poster) {
        value = withContext(Dispatchers.IO) { WallpaperLoader.poster(context, item.poster) }
    }
    val image = poster ?: return
    val shape = RoundedCornerShape(10.dp)
    val cardW = if (short) 68.dp else 86.dp
    val cardH = if (short) 78.dp else 104.dp
    Column(Modifier.width(cardW)) {
        androidx.tv.material3.Surface(
            onClick = {},
            modifier = Modifier
                .width(cardW)
                .height(cardH)
                .okPress({ onOpen(item) }) {},
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
            ),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    shape = shape,
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.72f)),
                    shape = shape,
                ),
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        ) {
            Image(image, item.title, Modifier.fillMaxSize().clip(shape), contentScale = ContentScale.Crop)
        }
        Text(
            item.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
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
    maxIcon: Int = 76,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val count = favorites.size.coerceAtLeast(1)
        val gap = 10.dp
        val pad = 14.dp
        val preferred = iconDp.dp.coerceIn(52.dp, maxIcon.dp)
        val room = maxWidth - pad * 2 - gap * (count - 1)
        val size = (room / count).coerceIn(52.dp, preferred)
        var caption by remember(favorites) { mutableStateOf(favorites.firstOrNull()?.label.orEmpty()) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (labels != "never") {
                Text(
                    text = caption.ifBlank { " " },
                    color = palette.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
            }
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(palette.glass, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .padding(horizontal = pad, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
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
                            labels = "never",
                            modifier = if (app.packageName == favorites.first().packageName) {
                                Modifier.focusRequester(lead)
                            } else {
                                Modifier
                            },
                            onOpen = { onOpen(app) },
                            onMenu = { onMenu(app) },
                            onFocused = { caption = app.label },
                        )
                    }
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
    onFocused: (String) -> Unit = {},
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(if (labels == "never") size else size + 28.dp)) {
        if (labels != "never") {
            Text(
                text = caption.ifBlank { " " },
                color = palette.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().height(18.dp),
            )
        }
        androidx.tv.material3.Surface(
            onClick = {},
            modifier = modifier
                .padding(top = if (labels == "never") 0.dp else 6.dp)
                .width(size)
                .height(size)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused(app.label)
                }
                .okPress(onOpen, onMenu)
                .menuAction(onMenu),
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.08f),
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    shape = shape,
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
                    shape = shape,
                ),
            ),
        ) {
            AppIcon(app.packageName, size - 6.dp, Modifier.padding(3.dp))
        }
    }
}

@Composable
private fun RecentRow(
    recents: List<TvApp>,
    onOpen: (TvApp) -> Unit,
    onMenu: (TvApp) -> Unit,
    compact: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        recents.take(8).forEach { app ->
            DockIcon(
                app = app,
                size = if (compact) 36.dp else 40.dp,
                labels = if (compact) "never" else "focus",
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
