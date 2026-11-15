package com.launch.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.launch.tv.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AboutScreen() {
    val sections = listOf(
        "Приложение" to "Launch ${BuildConfig.VERSION_NAME}. Независимый лаунчер для Android TV. Он показывает установленные программы, часы, погоду и входы телевизора. Это не продукт Google, Xiaomi, Apple или их партнёров.",
        "Создатель" to "Автор и правообладатель программы Launch — Mr.Box-600. Исключительные права на оригинальный код, оформление и подборку этого лаунчера принадлежат Mr.Box-600, если ниже не сказано иное.",
        "Как есть" to "Программа предоставляется «как есть», без каких-либо явных или подразумеваемых гарантий, включая пригодность для конкретной цели и бесперебойную работу. Mr.Box-600 не отвечает за сбои телевизора, потерю данных и последствия отключения программ, которые пользователь включает сам.",
        "Чужие обозначения" to "Android, Google, Android TV, Google TV, Xiaomi, Mi TV, PatchWall, Apple, macOS и другие названия — товарные знаки их владельцев. Упоминание нужно только чтобы описать совместимость. Launch их не использует как свои и не связан с правообладателями.",
        "Иконки программ" to "Названия и значки установленных приложений принадлежат их разработчикам. Лаунчер показывает их только для запуска уже установленной программы и не получает на них прав.",
        "Что входит в сборку" to "Язык Kotlin и библиотеки AndroidX, включая Jetpack Compose и AndroidX TV Material, распространяются на условиях Apache License 2.0. Шрифт Manrope используется на условиях SIL Open Font License 1.1. Эти компоненты остаются собственностью их авторов.",
        "Фото тем" to "Фотографии тем взяты с Unsplash и используются по лицензии Unsplash. Права на снимки остаются у их авторов. Mr.Box-600 не заявляет на них авторство. Градиентный фон рисует само приложение.",
        "Погода" to "Сведения о погоде запрашиваются у сторонних служб wttr.in, MET Norway и Open-Meteo. Запрос может содержать название выбранного места. Эти службы не входят в состав Launch и работают по своим правилам. Точность прогноза Mr.Box-600 не гарантирует.",
        "Данные на телевизоре" to "Настройки, избранное и порядок программ хранятся локально на устройстве. Отдельный аккаунт создателя для этого не нужен.",
        "Отключение программ" to "Пункт «Отключить приложения» меняет состояние пакета средствами Android. Важные системные части, без которых телевизор может не включиться, помечены и не отключаются. Остальные встроенные программы помечены как важные: их отключает сам пользователь, и он принимает последствия.",
    )
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(22.dp)
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { focus.requestFocus() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val down = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN
                val up = event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP
                when {
                    down && scroll.canScrollForward -> {
                        scope.launch { scroll.animateScrollBy(260f) }
                        true
                    }
                    up && scroll.canScrollBackward -> {
                        scope.launch { scroll.animateScrollBy(-260f) }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .verticalScroll(scroll)
            .padding(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { (title, body) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xE61C1C22))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
                    .padding(16.dp),
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
