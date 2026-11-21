package com.launch.installer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.launch.installer.DeviceKind
import com.launch.installer.FoundTv
import com.launch.installer.InstallViewModel
import com.launch.installer.Phase
import com.launch.installer.Suggestion
import com.launch.installer.TvKind
import com.launch.installer.UiState

private val Ink = Color(0xFF0C1016)
private val Card = Color(0xFF171C26)
private val Line = Color(0xFF2A3140)
private val Paper = Color(0xFFF4F6FB)
private val Muted = Color(0xFFA7B0C4)
private val Accent = Color(0xFFE7F27A)
private val InkOnAccent = Color(0xFF16180C)
private val Warn = Color(0xFFFFB089)
private val Good = Color(0xFF8EE7B5)

@Composable
fun InstallerScreen(model: InstallViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        model.rescan()
    }
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT <= 32) add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
    BackHandler(enabled = state.phase == Phase.Review || state.phase == Phase.Done || state.phase == Phase.Failed) {
        model.backToList()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "Установка Launch",
            style = TextStyle(color = Paper, fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(8.dp))
        val version = state.bundledVersion.ifBlank { "…" }
        Body("В комплекте Launch $version. Ищет телевизор в Wi‑Fi и ставит эту сборку. Подходит для 32- и 64-битных Android TV. Система, настройки, изображение и пульт не трогаются.")
        Spacer(Modifier.height(18.dp))
        when (state.phase) {
            Phase.Scanning, Phase.Connecting, Phase.WaitAllow -> Discover(state, model)
            Phase.Review -> Review(state, model)
            Phase.Working, Phase.Done -> Progress(state, model)
            Phase.Failed -> Failure(state, model)
        }
        Spacer(Modifier.height(28.dp))
        Body("Подходит для Android TV и Google TV: Xiaomi, Sony, TCL, Hisense, Philips, Sharp, Haier, Skyworth, Dell и приставок. Samsung на Tizen и LG на webOS это приложение не меняет.")
    }
}

@Composable
private fun Discover(state: UiState, model: InstallViewModel) {
    StatusCard(
        title = state.status,
        body = when (state.phase) {
            Phase.WaitAllow -> "Разрешение нужно один раз. Пока на экране нет «Разрешить», установка не начнётся."
            Phase.Connecting -> "Проверяю, что это телевизор, и какие оболочки отвечают на кнопку Домой."
            else -> "Телефон и телевизор должны быть в одной сети. Отладка по сети на телевизоре должна быть включена."
        },
    )
    if (state.phase == Phase.Scanning) {
        Spacer(Modifier.height(12.dp))
        state.devices.forEach { device ->
            DeviceCard(device) {
                if (device.kind == DeviceKind.Ready) model.connect(device) else model.connect(device)
            }
            Spacer(Modifier.height(8.dp))
        }
        ManualConnect(model)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Искать снова") { model.rescan() }
    }
}

@Composable
private fun DeviceCard(device: FoundTv, onClick: () -> Unit) {
    val label = when (device.kind) {
        DeviceKind.Ready -> "Можно ставить"
        DeviceKind.NeedsDebug -> "Нужна отладка"
        DeviceKind.EncryptedDebug -> "Только код сопряжения"
        DeviceKind.OtherSystem -> "Не Android"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(
                text = device.name,
                style = TextStyle(color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f),
            )
            androidx.compose.foundation.text.BasicText(
                text = label,
                style = TextStyle(color = if (device.kind == DeviceKind.Ready) Accent else Muted, fontSize = 12.sp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Body(device.detail)
        if (device.kind != DeviceKind.Ready && device.hint.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Body(device.hint)
        }
    }
}

@Composable
private fun ManualConnect(model: InstallViewModel) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = "Адрес вручную",
            style = TextStyle(color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(host, "192.168.1.20", Modifier.weight(1f)) { host = it }
            Field(port, "5555", Modifier.weight(0.4f), KeyboardType.Number) { port = it.filter(Char::isDigit).take(5) }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Подключиться") { model.connectManual(host, port) }
    }
}

@Composable
private fun Review(state: UiState, model: InstallViewModel) {
    val profile = state.profile ?: return
    StatusCard(
        title = "${profile.brandTitle} ${profile.model}",
        body = "Android ${profile.androidRelease} · ${profile.host}:${profile.port}\nПоставится Launch ${profile.launcherVersion}\n${profile.note}",
    )
    Spacer(Modifier.height(12.dp))
    if (profile.kind == TvKind.Unknown) {
        CheckRow(
            title = "Это телевизор или приставка",
            reason = "Без этой отметки установка закрыта.",
            checked = state.confirmUnknown,
            enabled = true,
        ) { model.confirmUnknown(!state.confirmUnknown) }
        Spacer(Modifier.height(8.dp))
    }
    if (state.suggestions.isEmpty()) {
        StatusCard("Мешающих оболочек не видно", "Можно просто поставить Launch. Кнопка Домой откроет его, если система разрешит.")
    } else {
        androidx.compose.foundation.text.BasicText(
            text = "Что мешает кнопке Домой",
            style = TextStyle(color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(8.dp))
        state.suggestions.forEach { item ->
            SuggestionRow(item) { model.toggle(item.packageName) }
            Spacer(Modifier.height(8.dp))
        }
    }
    state.restore?.let {
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Вернуть ранее выключенные") { model.restore() }
    }
    Spacer(Modifier.height(12.dp))
    val ready = profile.kind != TvKind.Unknown || state.confirmUnknown
    PrimaryButton(if (state.suggestions.any { it.checked }) "Установить и выключить выбранное" else "Установить Launch", ready) {
        model.install()
    }
    Spacer(Modifier.height(8.dp))
    SecondaryButton("К списку") { model.backToList() }
}

@Composable
private fun SuggestionRow(item: Suggestion, onToggle: () -> Unit) {
    CheckRow(item.title, item.reason, item.checked, !item.alreadyOff, onToggle)
}

@Composable
private fun CheckRow(title: String, reason: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Accent else Color.Transparent)
                .border(1.dp, if (checked) Accent else Line, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                androidx.compose.foundation.text.BasicText("✓", style = TextStyle(color = InkOnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            }
        }
        Column(Modifier.padding(start = 12.dp)) {
            androidx.compose.foundation.text.BasicText(
                text = title,
                style = TextStyle(color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            Body(reason)
        }
    }
}

@Composable
private fun Progress(state: UiState, model: InstallViewModel) {
    StatusCard(state.status, if (state.phase == Phase.Working) "Не закрывайте приложение, пока шаги не закончатся." else "На телевизоре должен открыться Launch.")
    Spacer(Modifier.height(12.dp))
    state.milestones.forEach { step ->
        val color = when {
            step.warn -> Warn
            step.done -> Good
            else -> Muted
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .padding(14.dp),
        ) {
            androidx.compose.foundation.text.BasicText(step.title, style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium))
            if (step.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Body(step.detail)
            }
        }
    }
    if (state.phase == Phase.Done) {
        state.restore?.let {
            SecondaryButton("Вернуть выключенные") { model.restore() }
            Spacer(Modifier.height(8.dp))
        }
        SecondaryButton("Готово") { model.backToList() }
    }
}

@Composable
private fun Failure(state: UiState, model: InstallViewModel) {
    StatusCard("Остановка", state.error ?: "Не получилось продолжить. На телевизоре ничего лишнего не выключено.")
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Искать снова") { model.rescan() }
    Spacer(Modifier.height(8.dp))
    SecondaryButton("К списку") { model.backToList() }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .padding(16.dp),
    ) {
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = TextStyle(color = Paper, fontSize = 18.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(6.dp))
        Body(body)
    }
}

@Composable
private fun Field(
    value: String,
    hint: String,
    modifier: Modifier,
    keyboard: KeyboardType = KeyboardType.Decimal,
    onChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = Paper, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        cursorBrush = SolidColor(Accent),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ink)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(hint, style = TextStyle(color = Muted, fontSize = 16.sp))
                }
                inner()
            }
        },
    )
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Accent else Line)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = if (enabled) InkOnAccent else Muted,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(color = Paper, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun Body(text: String) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = TextStyle(color = Muted, fontSize = 14.sp, lineHeight = 20.sp),
    )
}
