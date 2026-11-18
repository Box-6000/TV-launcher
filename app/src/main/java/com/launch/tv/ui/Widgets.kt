package com.launch.tv.ui

import androidx.activity.compose.BackHandler
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.launch.tv.data.IconCache
import com.launch.tv.data.WallpaperLoader
import com.launch.tv.model.Motion
import com.launch.tv.model.NetKind
import com.launch.tv.model.NetStatus
import com.launch.tv.model.TvApp
import com.launch.tv.model.TvSource
import com.launch.tv.model.UserSettings
import com.launch.tv.model.WeatherInfo
import com.launch.tv.model.weatherFacts
import com.launch.tv.model.weatherLabel
import com.launch.tv.model.displayTemp
import com.launch.tv.model.tempUnit
import com.launch.tv.ui.theme.Chrome
import com.launch.tv.ui.theme.LocalChrome
import com.launch.tv.ui.theme.LocalFocusSound
import com.launch.tv.ui.theme.LocalPalette
import com.launch.tv.ui.theme.drawPaletteGradient
import com.launch.tv.ui.theme.sceneId
import com.launch.tv.ui.theme.wallpaperDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Modifier.arrangeMove(enabled: Boolean, onLeft: () -> Unit, onRight: () -> Unit): Modifier {
    if (!enabled) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                onLeft()
                true
            }
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                onRight()
                true
            }
            else -> false
        }
    }
}

fun Modifier.menuAction(onMenu: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    val code = event.nativeKeyEvent.keyCode
    val menu = code == AndroidKeyEvent.KEYCODE_MENU || code == AndroidKeyEvent.KEYCODE_INFO
    if (menu && event.type == KeyEventType.KeyUp) {
        onMenu()
        true
    } else {
        false
    }
}

private class PressGate {
    var down = false
    var long = false
}

private val OkKeys = intArrayOf(
    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
    AndroidKeyEvent.KEYCODE_ENTER,
    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
)

@Composable
fun Modifier.okPress(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val gate = remember { PressGate() }
    return onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (!OkKeys.contains(key.keyCode)) return@onPreviewKeyEvent false
        when (key.action) {
            AndroidKeyEvent.ACTION_DOWN -> {
                if (key.repeatCount == 0 && !key.isLongPress) {
                    gate.down = true
                    gate.long = false
                } else if (gate.down && !gate.long) {
                    gate.long = true
                    onLongClick()
                }
                true
            }
            AndroidKeyEvent.ACTION_UP -> {
                if (gate.down && !gate.long && !key.isCanceled) onClick()
                gate.down = false
                gate.long = false
                true
            }
            else -> false
        }
    }
}

private class MenuArm {
    var ready = false
    var sawKey = false
}

@Composable
fun Modifier.swallowOpeningKey(): Modifier {
    val arm = remember { MenuArm() }
    LaunchedEffect(Unit) {
        delay(450)
        if (!arm.sawKey) arm.ready = true
    }
    return onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if (!OkKeys.contains(key.keyCode)) return@onPreviewKeyEvent false
        arm.sawKey = true
        if (arm.ready) return@onPreviewKeyEvent false
        val freshDown = key.action == AndroidKeyEvent.ACTION_DOWN && key.repeatCount == 0 && !key.isLongPress
        if (freshDown) {
            arm.ready = true
            return@onPreviewKeyEvent false
        }
        if (key.action == AndroidKeyEvent.ACTION_UP) arm.ready = true
        true
    }
}

fun Modifier.stepKeys(onLeft: (() -> Unit)?, onRight: (() -> Unit)?): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
            onLeft?.invoke()
            onLeft != null
        }
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
            onRight?.invoke()
            onRight != null
        }
        else -> false
    }
}

@Composable
fun Wallpaper(settings: UserSettings) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val photo by produceState<ImageBitmap?>(null, settings.wallpaperUri, settings.wallpaperId, settings.themeId) {
        value = withContext(Dispatchers.IO) {
            val custom = settings.wallpaperUri?.let { WallpaperLoader.load(context, it) }
            if (custom != null) return@withContext custom
            val id = settings.wallpaperId.ifBlank { settings.themeId }
            if (sceneId(id) == "gradient") null else WallpaperLoader.loadResource(context, wallpaperDrawable(id))
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val image = photo
        if (image != null) {
            drawCover(image)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = (settings.dimPercent / 180f).coerceIn(0.02f, 0.45f)),
                        Color.Black.copy(alpha = (settings.dimPercent / 420f).coerceIn(0f, 0.22f)),
                        Color.Black.copy(alpha = ((settings.dimPercent + 4) / 170f).coerceIn(0.04f, 0.5f)),
                    ),
                ),
            )
        } else {
            drawPaletteGradient(palette)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCover(image: ImageBitmap) {
    val scale = max(size.width / image.width.toFloat(), size.height / image.height.toFloat())
    val width = image.width * scale
    val height = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - width) / 2f).toInt(), ((size.height - height) / 2f).toInt()),
        dstSize = IntSize(width.toInt().coerceAtLeast(1), height.toInt().coerceAtLeast(1)),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
    )
}

@Composable
fun AppIcon(packageName: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, packageName) {
        value = IconCache.get(context, packageName)
    }
    val palette = LocalPalette.current
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier.size(size).clip(RoundedCornerShape(18.dp)).background(palette.glassFocus),
            contentAlignment = Alignment.Center,
        ) {
            Text("•", color = palette.accent, fontSize = 22.sp)
        }
    }
}

@Composable
fun AppTile(
    app: TvApp,
    favorite: Boolean,
    icon: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    framed: Boolean = false,
    labelWidth: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(icon * 0.22f)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hair = Color.White.copy(alpha = if (focused) 0.55f else 0.30f)
    val sound = LocalFocusSound.current
    LaunchedEffect(focused) {
        if (focused && sound) FocusTick.play()
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.tv.material3.Surface(
            onClick = {},
            modifier = Modifier.size(icon).iconFocusLift(focused).okPress(onClick, onLongClick),
            interactionSource = interaction,
            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.10f),
            ),
            scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                border = androidx.tv.material3.Border(
                    border = BorderStroke(if (framed) 1.5.dp else 0.dp, if (framed) hair else Color.Transparent),
                    shape = shape,
                ),
                focusedBorder = androidx.tv.material3.Border(
                    border = BorderStroke(if (framed) 1.5.dp else 2.dp, if (framed) hair else Color.White.copy(alpha = 0.7f)),
                    shape = shape,
                ),
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AppIcon(app.packageName, icon * 0.82f, Modifier.clip(shape))
                if (favorite) {
                    Text(
                        "★",
                        color = palette.accent,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                    )
                }
            }
        }
        Text(
            text = if (app.fresh) "${app.label} · новое" else app.label,
            color = palette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp).width(labelWidth ?: (icon + 12.dp)),
        )
    }
}

@Composable
fun AppCard(
    app: TvApp,
    favorite: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val chrome = LocalChrome.current
    val shape = RoundedCornerShape(22.dp)
    val focus = Border(
        border = BorderStroke(3.dp, Color.White),
        shape = shape,
    )
    Card(
        onClick = {},
        modifier = modifier
            .okPress(onClick, onLongClick)
            .semantics { contentDescription = app.label },
        shape = CardDefaults.shape(shape = shape),
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            contentColor = palette.text,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
            pressedContainerColor = Color.White.copy(alpha = 0.12f),
        ),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = focus,
            pressedBorder = focus,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppIcon(app.packageName, chrome.icon)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = app.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            if (favorite) {
                Text(
                    text = "★",
                    color = palette.accent,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )
            }
        }
    }
}

@Composable
fun StatusBar(
    title: String,
    settings: UserSettings,
    weather: WeatherInfo?,
    net: NetStatus,
    battery: Int?,
    sources: List<TvSource> = emptyList(),
    onOpenSource: (TvSource) -> Unit = {},
    compact: Boolean = false,
) {
    val palette = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(if (compact) 48.dp else 72.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact && title.isNotEmpty()) {
            Column {
                Text(title, color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                if (settings.showDate) {
                    Text(rememberDate(), color = palette.muted, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (!compact && settings.showWeather && weather != null && net.kind != NetKind.Offline) {
            WeatherBadge(weather, settings.fahrenheit)
            Spacer(Modifier.width(18.dp))
        }
        if (battery != null) {
            Text("$battery%", color = palette.muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(14.dp))
        }
        if (!compact && settings.showClock) {
            Text(
                text = rememberClock(settings.clock24, settings.showSeconds),
                color = palette.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(16.dp))
        }
        InputPills(sources, onOpenSource)
        Spacer(Modifier.width(12.dp))
        NetBadge(net)
    }
}

@Composable
private fun InputPills(
    sources: List<TvSource>,
    onOpen: (TvSource) -> Unit,
) {
    val live = sources.filter { it.connected && it.kind != "tuner" }.take(6)
    if (live.isEmpty()) return
    Row(
        modifier = Modifier.padding(start = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        live.forEach { source ->
            val shape = RoundedCornerShape(10.dp)
            Surface(
                onClick = {},
                modifier = Modifier.width(46.dp).height(38.dp).okPress({ onOpen(source) }) {},
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x9912161E),
                    focusedContainerColor = Color(0xE02A3348),
                ),
                border = androidx.tv.material3.ClickableSurfaceDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(
                        border = BorderStroke(1.5.dp, Color.White),
                        shape = shape,
                    ),
                ),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DrawerGlyph(source.kind, Modifier.size(13.dp), Color.White)
                    Text(
                        shortInput(source.label),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp, start = 2.dp, end = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = {},
        modifier = modifier.size(48.dp).okPress(onClick) {},
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White.copy(alpha = 0.28f),
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                shape = shape,
            ),
            focusedBorder = androidx.tv.material3.Border(
                border = BorderStroke(2.dp, Color.White),
                shape = shape,
            ),
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MicGlyph(Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MicGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.32f, size.height * 0.08f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.36f, size.height * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.18f, size.width * 0.18f),
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.46f),
            style = stroke,
        )
        drawLine(color, Offset(size.width * 0.5f, size.height * 0.74f), Offset(size.width * 0.5f, size.height * 0.88f), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.9f), Offset(size.width * 0.68f, size.height * 0.9f), stroke.width, StrokeCap.Round)
    }
}

@Composable
internal fun rememberClock(use24: Boolean = true, seconds: Boolean = false): String {
    val pattern = when {
        seconds && use24 -> "HH:mm:ss"
        seconds -> "h:mm:ss"
        use24 -> "HH:mm"
        else -> "h:mm"
    }
    val format = remember(pattern) { SimpleDateFormat(pattern, Locale("ru")) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(seconds) {
        while (true) {
            now = System.currentTimeMillis()
            val step = if (seconds) 1_000L else (60_000L - (System.currentTimeMillis() % 60_000L)).coerceAtLeast(1_000L)
            delay(step)
        }
    }
    return format.format(Date(now))
}

@Composable
internal fun rememberDate(): String {
    val format = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - (now % 60_000L)).coerceAtLeast(1_000L))
        }
    }
    return format.format(Date(now)).replaceFirstChar { it.titlecase(Locale("ru")) }
}

@Composable
private fun WeatherBadge(weather: WeatherInfo, fahrenheit: Boolean) {
    val palette = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherGlyph(weather.code, palette.accent)
        Spacer(Modifier.width(8.dp))
        Column {
            Text("${displayTemp(weather.tempC, fahrenheit)}${tempUnit(fahrenheit)}", color = palette.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "${weather.city} · ${weatherLabel(weather.code)}",
                color = palette.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val facts = weatherFacts(weather)
            if (facts.isNotBlank()) {
                Text(facts, color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun WeatherGlyph(code: Int, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (code) {
            0 -> drawCircle(color, radius = size.minDimension * 0.28f, style = stroke)
            71, 73, 75, 77, 85, 86 -> drawCircle(color, radius = size.minDimension * 0.22f)
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99 -> {
                drawLine(color, Offset(size.width * 0.35f, size.height * 0.2f), Offset(size.width * 0.25f, size.height * 0.85f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.6f, size.height * 0.15f), Offset(size.width * 0.5f, size.height * 0.8f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            else -> drawCircle(color.copy(alpha = 0.9f), radius = size.minDimension * 0.32f)
        }
    }
}

@Composable
private fun NetBadge(net: NetStatus) {
    val palette = LocalPalette.current
    val active = if (net.kind == NetKind.Offline) palette.muted.copy(alpha = 0.4f) else palette.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = when (net.kind) {
                NetKind.Offline -> "Нет сети"
                NetKind.Wifi -> "Wi‑Fi"
                NetKind.Ethernet -> "Кабель"
            },
            color = palette.muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(16.dp))
        Canvas(Modifier.size(22.dp)) {
            when (net.kind) {
                NetKind.Ethernet -> {
                    val plug = androidx.compose.ui.geometry.Rect(
                        size.width * 0.18f,
                        size.height * 0.34f,
                        size.width * 0.62f,
                        size.height * 0.78f,
                    )
                    drawRoundRect(
                        color = active,
                        topLeft = plug.topLeft,
                        size = plug.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = Stroke(1.7.dp.toPx()),
                    )
                    drawLine(active, Offset(size.width * 0.62f, size.height * 0.56f), Offset(size.width * 0.88f, size.height * 0.56f), 1.7.dp.toPx(), StrokeCap.Round)
                    drawLine(active, Offset(size.width * 0.32f, size.height * 0.34f), Offset(size.width * 0.32f, size.height * 0.18f), 1.7.dp.toPx(), StrokeCap.Round)
                    drawLine(active, Offset(size.width * 0.48f, size.height * 0.34f), Offset(size.width * 0.48f, size.height * 0.18f), 1.7.dp.toPx(), StrokeCap.Round)
                }
                else -> {
                    val bars = if (net.kind == NetKind.Offline) 0 else (net.level + 1).coerceIn(1, 3)
                    val origin = Offset(size.width * 0.18f, size.height * 0.78f)
                    drawCircle(if (bars > 0) active else active.copy(alpha = 0.25f), 1.6.dp.toPx(), origin)
                    repeat(3) { index ->
                        val radius = size.minDimension * (0.28f + index * 0.22f)
                        drawArc(
                            color = if (index < bars) active else active.copy(alpha = 0.25f),
                            startAngle = 225f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(origin.x - radius, origin.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
        }
    }
}

private fun shortInput(label: String): String {
    val trimmed = label.trim()
    val hdmi = Regex("(?i)hdmi\\s*(\\d+)").find(trimmed)
    if (hdmi != null) return "HDMI ${hdmi.groupValues[1]}"
    return trimmed.take(8)
}

@Composable
fun Mark(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    Canvas(modifier.size(28.dp)) {
        drawRoundRect(
            color = palette.accent,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        )
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.38f, size.height * 0.3f)
            lineTo(size.width * 0.74f, size.height * 0.5f)
            lineTo(size.width * 0.38f, size.height * 0.7f)
            close()
        }
        drawPath(path, Color.White)
    }
}

@Composable
fun DrawerGlyph(kind: String, modifier: Modifier = Modifier, tint: Color? = null) {
    val palette = LocalPalette.current
    val color = tint ?: palette.text
    Canvas(modifier.fillMaxSize()) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (kind) {
            "home" -> {
                drawLine(color, Offset(size.width * 0.2f, size.height * 0.48f), Offset(size.width * 0.5f, size.height * 0.22f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.22f), Offset(size.width * 0.8f, size.height * 0.48f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.3f, size.height * 0.46f), Offset(size.width * 0.3f, size.height * 0.78f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.7f, size.height * 0.46f), Offset(size.width * 0.7f, size.height * 0.78f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.3f, size.height * 0.78f), Offset(size.width * 0.7f, size.height * 0.78f), stroke.width, StrokeCap.Round)
            }
            "apps" -> {
                repeat(2) { row ->
                    repeat(2) { col ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(size.width * (0.18f + col * 0.36f), size.height * (0.18f + row * 0.36f)),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.24f, size.height * 0.24f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        )
                    }
                }
            }
            "search" -> {
                drawCircle(color, radius = size.minDimension * 0.24f, center = Offset(size.width * 0.42f, size.height * 0.42f), style = stroke)
                drawLine(color, Offset(size.width * 0.6f, size.height * 0.6f), Offset(size.width * 0.8f, size.height * 0.8f), stroke.width, StrokeCap.Round)
            }
            "av" -> {
                drawCircle(color, radius = size.minDimension * 0.22f, center = Offset(size.width * 0.38f, size.height * 0.42f), style = stroke)
                drawLine(color, Offset(size.width * 0.58f, size.height * 0.42f), Offset(size.width * 0.82f, size.height * 0.42f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.7f, size.height * 0.28f), Offset(size.width * 0.7f, size.height * 0.56f), stroke.width, StrokeCap.Round)
            }
            "hdmi" -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke,
                )
                drawLine(color, Offset(size.width * 0.32f, size.height * 0.42f), Offset(size.width * 0.32f, size.height * 0.58f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.5f, size.height * 0.42f), Offset(size.width * 0.5f, size.height * 0.58f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.68f, size.height * 0.42f), Offset(size.width * 0.68f, size.height * 0.58f), stroke.width, StrokeCap.Round)
            }
            else -> {
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.3f), Offset(size.width * 0.78f, size.height * 0.3f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.5f), Offset(size.width * 0.78f, size.height * 0.5f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.7f), Offset(size.width * 0.78f, size.height * 0.7f), stroke.width, StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.62f, size.height * 0.3f))
                drawCircle(color, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.38f, size.height * 0.5f))
                drawCircle(color, radius = size.minDimension * 0.08f, center = Offset(size.width * 0.68f, size.height * 0.7f))
            }
        }
    }
}

@Composable
fun AppMenu(
    app: TvApp,
    favorite: Boolean,
    category: String,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onCategory: () -> Unit,
    categories: List<String>,
    onPickCategory: (String) -> Unit,
    onMove: (Int) -> Unit,
    onMoveFavorite: (Int) -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
    details: String,
    onClose: () -> Unit,
) {
    val palette = LocalPalette.current
    val first = remember { FocusRequester() }
    var picking by remember { mutableStateOf(false) }
    BackHandler(onBack = { if (picking) picking = false else onClose() })
    val actions = if (picking) {
        categories.map { name -> name to { onPickCategory(name) } } + ("Назад" to { picking = false })
    } else {
        buildList {
            add("Открыть" to onOpen)
            add((if (favorite) "Убрать из избранного" else "В избранное") to onFavorite)
            add("Скрыть" to onHide)
            add("Категория: $category" to { picking = true })
            add("Сдвинуть влево" to { onMove(-1) })
            add("Сдвинуть вправо" to { onMove(1) })
            if (favorite) {
                add("Избранное влево" to { onMoveFavorite(-1) })
                add("Избранное вправо" to { onMoveFavorite(1) })
            }
            add("Сведения: $details" to onDetails)
            add("Удалить" to onUninstall)
            add("Закрыть" to onClose)
        }
    }
    LaunchedEffect(app.packageName) { runCatching { first.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .swallowOpeningKey(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF2161924))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(18.dp)
                .focusGroup(),
        ) {
            Text(app.label, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Удерживайте OK или нажмите Menu", color = palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            actions.forEachIndexed { index, (label, action) ->
                MenuButton(
                    label = label,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    lockUp = index == 0,
                    lockDown = index == actions.lastIndex,
                    onClick = action,
                )
            }
        }
    }
}

@Composable
private fun MenuButton(
    label: String,
    modifier: Modifier,
    lockUp: Boolean,
    lockDown: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                if (lockUp) up = FocusRequester.Cancel
                if (lockDown) down = FocusRequester.Cancel
            },
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.04f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            contentColor = LocalPalette.current.text,
            focusedContentColor = Color.White,
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)), shape = shape),
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal object FocusTick {
    private var pool: android.media.SoundPool? = null
    private var clickId = 0
    private var openId = 0
    private var clickReady = false
    private var openReady = false
    private var pendingOpen = false

    fun prepare(context: android.content.Context) {
        if (pool != null) return
        val audio = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = android.media.SoundPool.Builder().setMaxStreams(3).setAudioAttributes(audio).build()
        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) return@setOnLoadCompleteListener
            if (sampleId == clickId) clickReady = true
            if (sampleId == openId) {
                openReady = true
                if (pendingOpen) {
                    pendingOpen = false
                    created.play(openId, 0.85f, 0.85f, 1, 0, 1f)
                }
            }
        }
        clickId = created.load(context, com.launch.tv.R.raw.ui_click, 1)
        openId = created.load(context, com.launch.tv.R.raw.ui_open, 1)
        pool = created
    }

    fun play() {
        if (clickReady) pool?.play(clickId, 0.7f, 0.7f, 1, 0, 1f)
    }

    fun playOpen() {
        if (openReady) pool?.play(openId, 0.85f, 0.85f, 1, 0, 1f) else pendingOpen = true
    }
}

@Composable
fun Modifier.iconFocusLift(focused: Boolean): Modifier {
    val motion = LocalChrome.current.motion
    val target = when {
        !focused || motion == Motion.Off -> 1f
        motion == Motion.Soft -> 1.08f
        else -> 1.16f
    }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.62f,
            stiffness = 420f,
        ),
        label = "icon-lift",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

class PageLead {
    val requester = FocusRequester()
    private var owner: String? = null

    fun attach(key: String): Modifier {
        if (owner == null) owner = key
        return if (owner == key) Modifier.focusRequester(requester) else Modifier
    }
}

val LocalPageLead = androidx.compose.runtime.staticCompositionLocalOf<PageLead?> { null }

@Composable
fun Modifier.pageLead(key: String): Modifier {
    val lead = LocalPageLead.current ?: return this
    return this.then(lead.attach(key))
}

@Composable
fun GlassListItem(
    title: String,
    supporting: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(22.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val sound = LocalFocusSound.current
    LaunchedEffect(focused) {
        if (focused && sound) FocusTick.play()
    }
    val plate = when {
        focused -> Color(0xFF31405C)
        selected -> Color(0xFF28344A)
        else -> Color(0xFF1E2838)
    }
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().pageLead(title).okPress(onClick) {},
        interactionSource = interaction,
        shape = SelectableSurfaceDefaults.shape(shape = shape),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color.White,
            pressedContainerColor = Color.Transparent,
            pressedContentColor = Color.White,
            selectedContainerColor = Color.Transparent,
            selectedContentColor = Color.White,
            focusedSelectedContainerColor = Color.Transparent,
            focusedSelectedContentColor = Color.White,
            pressedSelectedContainerColor = Color.Transparent,
            pressedSelectedContentColor = Color.White,
        ),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f, focusedSelectedScale = 1f),
        border = SelectableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = shape),
            pressedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = shape),
            selectedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), shape = shape),
            focusedSelectedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = shape),
            pressedSelectedBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = shape),
        ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(plate)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (supporting.isNotBlank()) {
                        Text(
                            supporting,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (value.isNotBlank()) {
                    Text(
                        value,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ToastBanner(text: String) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = text,
            color = Color(0xFF12141C),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(bottom = 28.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.accent)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun Splash() {
    val palette = LocalPalette.current
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    val scale = remember { androidx.compose.animation.core.Animatable(0.82f) }
    LaunchedEffect(Unit) {
        FocusTick.playOpen()
        alpha.animateTo(1f, androidx.compose.animation.core.tween(640))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(
            1f,
            androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 280f),
        )
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                this.alpha = alpha.value
                scaleX = scale.value
                scaleY = scale.value
            },
        ) {
            Mark(Modifier.size(92.dp))
            Spacer(Modifier.height(18.dp))
            Text("Launch", color = palette.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Экран телевизора", color = palette.muted, fontSize = 18.sp)
        }
    }
}
