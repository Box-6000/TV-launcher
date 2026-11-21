package com.launch.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.launch.tv.R
import com.launch.tv.model.Motion

data class Palette(
    val id: String,
    val title: String,
    val scene: String,
    val accent: Color,
    val accentSoft: Color,
    val text: Color,
    val muted: Color,
    val glass: Color,
    val glassFocus: Color,
)

data class WallpaperStyle(
    val id: String,
    val title: String,
    val colors: List<Color>,
    val glowA: Color,
    val glowB: Color,
)

data class Chrome(
    val cardWidth: Dp = 180.dp,
    val cardHeight: Dp = 168.dp,
    val icon: Dp = 64.dp,
    val motion: Motion = Motion.Expressive,
)

val Palettes = listOf(
    Palette("night", "Ночь", "night", Color(0xFFD7E2FF), Color(0xFF9BB0D6), Color(0xFFF5F7FB), Color(0xFFA7B0C4), Color(0x14FFFFFF), Color(0x24FFFFFF)),
    Palette("aurora", "Аврора", "aurora", Color(0xFF8EB4FF), Color(0xFFC9A6FF), Color(0xFFF5F7FB), Color(0xFFA7B0C4), Color(0x14FFFFFF), Color(0x24FFFFFF)),
    Palette("ocean", "Океан", "ocean", Color(0xFF5EE0D0), Color(0xFF6AA8FF), Color(0xFFF4FBFA), Color(0xFFA9C2C4), Color(0x14FFFFFF), Color(0x24FFFFFF)),
    Palette("ember", "Закат", "sunset", Color(0xFFFFB25A), Color(0xFFFF7A8A), Color(0xFFFFF8F3), Color(0xFFD2B8AE), Color(0x14FFFFFF), Color(0x24FFFFFF)),
    Palette("forest", "Лес", "forest", Color(0xFF9BE7A8), Color(0xFF7ED0C0), Color(0xFFF4FBF6), Color(0xFFB7C9BE), Color(0x14FFFFFF), Color(0x24FFFFFF)),
    Palette("graphite", "Город", "city", Color(0xFFE6E8EE), Color(0xFF9AA4B8), Color(0xFFF7F8FA), Color(0xFFB4BAC6), Color(0x14FFFFFF), Color(0x24FFFFFF)),
)

val Wallpapers = listOf(
    WallpaperStyle("ink", "Чернила", listOf(Color(0xFF16182A), Color(0xFF0C101C), Color(0xFF07080D)), Color(0xFF3A4DFF), Color(0xFF7A4DFF)),
    WallpaperStyle("aurora", "Аврора", listOf(Color(0xFF24143A), Color(0xFF12182E), Color(0xFF081018)), Color(0xFF7C5CFF), Color(0xFF3E8BFF)),
    WallpaperStyle("dusk", "Сумерки", listOf(Color(0xFF2A1430), Color(0xFF1A1428), Color(0xFF0C0E16)), Color(0xFFFF6B8A), Color(0xFF7A5CFF)),
    WallpaperStyle("lagoon", "Лагуна", listOf(Color(0xFF0E2A32), Color(0xFF0C1824), Color(0xFF071016)), Color(0xFF2ED3C6), Color(0xFF3D7DFF)),
    WallpaperStyle("ember", "Угли", listOf(Color(0xFF2C140E), Color(0xFF1A1214), Color(0xFF0C0A0E)), Color(0xFFFF8A3D), Color(0xFFFF4D6A)),
    WallpaperStyle("forest", "Чаща", listOf(Color(0xFF10281C), Color(0xFF0E1814), Color(0xFF070C0A)), Color(0xFF3DDC97), Color(0xFF1E8F7A)),
)

fun paletteById(id: String): Palette = Palettes.firstOrNull { it.id == id } ?: Palettes.first()

fun menuIconColor(id: String, accent: Color): Color = when (id) {
    "accent" -> accent
    "amber" -> Color(0xFFFFC56E)
    "cyan" -> Color(0xFF7EE0E0)
    "green" -> Color(0xFF9BE7A8)
    "blue" -> Color(0xFF8EB4FF)
    "pink" -> Color(0xFFFF9BB5)
    else -> Color.White
}

fun sceneTitle(id: String): String = com.launch.tv.data.WallpaperStudio.liveTitle(id)
    ?: com.launch.tv.data.WallpaperStudio.title(id)
    ?: when (sceneId(id)) {
    "aurora" -> "Аврора"
    "ocean" -> "Океан"
    "sunset" -> "Закат"
    "forest" -> "Лес"
    "city" -> "Город"
    "gradient" -> "Градиент"
    else -> "Ночь"
}

fun sceneId(id: String): String = when (id) {
    "aurora" -> "aurora"
    "ocean", "lagoon" -> "ocean"
    "ember", "dusk", "sunset" -> "sunset"
    "forest" -> "forest"
    "graphite", "city" -> "city"
    "gradient" -> "gradient"
    else -> "night"
}

fun wallpaperDrawable(id: String): Int = when (sceneId(id)) {
    "aurora" -> R.drawable.wall_aurora
    "ocean" -> R.drawable.wall_ocean
    "sunset" -> R.drawable.wall_sunset
    "forest" -> R.drawable.wall_forest
    "city" -> R.drawable.wall_city
    else -> R.drawable.wall_night
}

val LocalPalette = staticCompositionLocalOf { Palettes.first() }
val LocalChrome = staticCompositionLocalOf { Chrome() }
val LocalFocusSound = staticCompositionLocalOf { false }

private val Manrope = FontFamily(
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private fun Typography.withManrope(): Typography = copy(
    displayLarge = displayLarge.manrope(),
    displayMedium = displayMedium.manrope(),
    displaySmall = displaySmall.manrope(),
    headlineLarge = headlineLarge.manrope(),
    headlineMedium = headlineMedium.manrope(),
    headlineSmall = headlineSmall.manrope(),
    titleLarge = titleLarge.manrope(),
    titleMedium = titleMedium.manrope(),
    titleSmall = titleSmall.manrope(),
    bodyLarge = bodyLarge.manrope(),
    bodyMedium = bodyMedium.manrope(),
    bodySmall = bodySmall.manrope(),
    labelLarge = labelLarge.manrope(),
    labelMedium = labelMedium.manrope(),
    labelSmall = labelSmall.manrope(),
)

private fun TextStyle.manrope(): TextStyle = copy(fontFamily = Manrope)

@Composable
fun LaunchTheme(palette: Palette, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF081018),
            secondary = palette.accentSoft,
            onSecondary = Color(0xFF081018),
            background = Color(0xFF07080D),
            onBackground = palette.text,
            surface = Color(0xFF161922),
            onSurface = palette.text,
            surfaceVariant = Color(0xFF222633),
            onSurfaceVariant = palette.muted,
            border = Color.White,
            inverseSurface = Color(0xFFE8ECF5),
            inverseOnSurface = Color(0xFF12141C),
        ),
        typography = Typography().withManrope(),
        content = content,
    )
}
