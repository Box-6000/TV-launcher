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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as AndroidKeyEvent
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
    val compact = settings.showWatch && watch.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ClockStage(settings, weather, weatherLoading, net, onRetryWeather, compact)
        }
        if (settings.showWatch && watch.isNotEmpty()) {
            WatchShelf(watch, onOpenWatch)
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
    compact: Boolean,
) {
    val palette = LocalPalette.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (settings.clockPlate) {
            Modifier
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0x99141820))
                .padding(horizontal = if (compact) 28.dp else 48.dp, vertical = if (compact) 14.dp else 28.dp)
        } else {
            Modifier
        },
    ) {
        if (settings.showClock) {
            Text(
                text = rememberClock(settings.clock24, settings.showSeconds),
                color = palette.text,
                fontSize = (if (compact) settings.clockSp.coerceAtMost(68) else settings.clockSp).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        if (settings.showDate) {
            Text(
                text = rememberDate(),
                color = palette.muted,
                fontSize = if (compact) 16.sp else 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (settings.showWeather) {
            Spacer(Modifier.height(if (compact) 8.dp else 18.dp))
            WeatherBlock(settings, weather, weatherLoading, net, onRetry, compact)
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
) {
    val palette = LocalPalette.current
    if (weather != null) {
        val temp = "${displayTemp(weather.tempC, settings.fahrenheit)}${tempUnit(settings.fahrenheit)}"
        val caption = "${weatherLabel(weather.code)} · ${weather.city}"
        val facts = weatherFacts(weather)
        if (settings.weatherInline || compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherMotion(weather.code, settings.motion, if (compact) 36.dp else 48.dp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "$temp   $caption",
                        color = palette.text,
                        fontSize = if (compact) 20.sp else 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (facts.isNotBlank()) {
                        Text(facts, color = palette.muted, fontSize = if (compact) 13.sp else 16.sp)
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherMotion(weather.code, settings.motion, 64.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(temp, color = palette.text, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                    Text(caption, color = palette.muted, fontSize = 20.sp)
                    if (facts.isNotBlank()) {
                        Text(facts, color = palette.text, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    if (weather.outlook.isNotBlank()) {
                        Text(weather.outlook, color = palette.muted, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
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
private fun WatchShelf(items: List<WatchItem>, onOpen: (WatchItem) -> Unit) {
    val palette = LocalPalette.current
    val stacks = remember(items) { items.groupBy { it.packageName.ifBlank { it.title } }.values.map { it.take(6) } }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            "Недавние фильмы и передачи",
            color = palette.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 28.dp),
        ) {
            items(stacks, key = { it.first().packageName }) { stack ->
                WatchStack(stack, onOpen)
            }
        }
    }
}

@Composable
private fun WatchStack(stack: List<WatchItem>, onOpen: (WatchItem) -> Unit) {
    val context = LocalContext.current
    var index by remember(stack) { mutableIntStateOf(0) }
    val item = stack[index.coerceIn(stack.indices)]
    val appName = remember(item.packageName) { com.launch.tv.data.WatchLibrary.launchLabel(context, item.packageName) }
    Column(Modifier.width(168.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || stack.size < 2) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                            index = (index + 1) % stack.size
                            true
                        }
                        else -> false
                    }
                },
        ) {
            val depth = (stack.size - 1).coerceAtMost(2)
            for (layer in depth downTo 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (layer * 8).dp, y = -(layer * 7).dp)
                        .width(148.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A3344)),
                )
            }
            WatchCard(
                item,
                onOpen,
                Modifier.align(Alignment.BottomStart).width(148.dp).height(84.dp),
            )
        }
        Text(
            appName.ifBlank { item.title },
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (stack.size > 1) {
            Text(
                "${index + 1} из ${stack.size}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun WatchCard(item: WatchItem, onOpen: (WatchItem) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val poster by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, item.poster) {
        val uri = item.poster
        value = if (uri.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { WallpaperLoader.poster(context, uri) }
        }
    }
    val appName = remember(item.packageName) { com.launch.tv.data.WatchLibrary.launchLabel(context, item.packageName) }
    val shape = RoundedCornerShape(12.dp)
    androidx.tv.material3.Surface(
        onClick = {},
        modifier = modifier.okPress({ onOpen(item) }) {},
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF161C28),
            focusedContainerColor = Color(0xFF161C28),
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = shape,
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                shape = shape,
            ),
        ),
    ) {
        Box(Modifier.fillMaxSize().clip(shape)) {
            if (poster != null) {
                Image(poster!!, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (!item.title.equals(appName, ignoreCase = true)) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC10141C)))),
                    )
                    Text(
                        item.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (item.packageName.isNotBlank()) AppIcon(item.packageName, 52.dp)
                }
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val count = favorites.size.coerceAtLeast(1)
        val gap = 8.dp
        val slot = (maxWidth - 24.dp - gap * (count - 1)) / count
        val size = iconDp.dp.coerceIn(28.dp, (slot - 8.dp).coerceAtLeast(28.dp))
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
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.glass, shape)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    .padding(vertical = 8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            if (favorites.isEmpty()) {
                item {
                    PlaceholderCard(
                        title = "Добавить",
                        subtitle = "Открыть список приложений",
                        onClick = { onSection(Section.Apps) },
                        modifier = Modifier.focusRequester(lead),
                    )
                }
            } else {
                items(favorites, key = { it.packageName }) { app ->
                    DockIcon(
                        app = app,
                        size = size,
                        labels = "never",
                        modifier = if (app.packageName == favorites.first().packageName) {
                            Modifier.focusRequester(lead).padding(horizontal = 6.dp)
                        } else {
                            Modifier.padding(horizontal = 6.dp)
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
                .iconFocusLift(focused)
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
