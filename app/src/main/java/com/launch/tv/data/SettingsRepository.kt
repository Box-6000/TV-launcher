package com.launch.tv.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.launch.tv.model.Motion
import com.launch.tv.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsStore by preferencesDataStore(
    name = "launch_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsStore

    val settings: Flow<UserSettings> = store.data
        .catch { emit(emptyPreferences()) }
        .map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(block: (UserSettings) -> UserSettings) {
        store.edit { prefs ->
            val next = block(prefs.toSettings())
            prefs.write(next)
        }
    }

    private fun Preferences.toSettings(): UserSettings {
        return UserSettings(
            themeId = this[Keys.theme] ?: "aurora",
            wallpaperId = this[Keys.wallpaper] ?: "ink",
            wallpaperUri = this[Keys.wallpaperUri],
            iconDp = iconDp(this),
            columns = (this[Keys.columns] ?: 5).coerceIn(4, 8),
            motion = enumOr(this[Keys.motion], Motion.Expressive),
            showClock = this[Keys.clock] ?: true,
            showDate = this[Keys.date] ?: true,
            showWeather = this[Keys.weather] ?: true,
            weatherPlace = this[Keys.weatherPlace].orEmpty(),
            favorites = strings(this[Keys.favorites]),
            hidden = strings(this[Keys.hidden]).toSet(),
            order = strings(this[Keys.order]),
            categoryOverrides = objects(this[Keys.overrides]),
            customCategories = strings(this[Keys.categories]),
            recents = strings(this[Keys.recents]),
            seeded = this[Keys.seeded] ?: false,
            clock24 = this[Keys.clock24] ?: true,
            showSeconds = this[Keys.seconds] ?: false,
            clockSp = (this[Keys.clockSp] ?: 108).coerceIn(72, 144),
            showDock = this[Keys.dock] ?: true,
            dockLabels = this[Keys.dockLabels] ?: "focus",
            weatherInline = this[Keys.weatherInline] ?: false,
            fahrenheit = this[Keys.fahrenheit] ?: false,
            dimPercent = (this[Keys.dim] ?: 28).coerceIn(0, 70),
            clockPlate = this[Keys.clockPlate] ?: true,
            showHints = this[Keys.hints] ?: true,
            sortMode = this[Keys.sortMode] ?: "name",
            showRecents = this[Keys.showRecents] ?: true,
            confirmDisable = this[Keys.confirmDisable] ?: true,
            lastInputId = this[Keys.lastInput].orEmpty(),
            focusSound = if (this[Keys.focusChosen] == true) this[Keys.focusSound] ?: true else true,
            focusChosen = this[Keys.focusChosen] ?: false,
            weatherPlaces = strings(this[Keys.places]),
            inputNames = objects(this[Keys.inputNames]),
            favoriteInputs = strings(this[Keys.favoriteInputs]),
        )
    }

    private fun MutablePreferences.write(settings: UserSettings) {
        this[Keys.theme] = settings.themeId
        this[Keys.wallpaper] = settings.wallpaperId
        if (settings.wallpaperUri == null) remove(Keys.wallpaperUri) else this[Keys.wallpaperUri] = settings.wallpaperUri
        this[Keys.iconDp] = settings.iconDp.coerceIn(24, 180)
        this[Keys.columns] = settings.columns
        this[Keys.motion] = settings.motion.name
        this[Keys.clock] = settings.showClock
        this[Keys.date] = settings.showDate
        this[Keys.weather] = settings.showWeather
        this[Keys.weatherPlace] = settings.weatherPlace
        this[Keys.favorites] = JSONArray(settings.favorites).toString()
        this[Keys.hidden] = JSONArray(settings.hidden.toList()).toString()
        this[Keys.order] = JSONArray(settings.order).toString()
        this[Keys.overrides] = JSONObject(settings.categoryOverrides).toString()
        this[Keys.categories] = JSONArray(settings.customCategories).toString()
        this[Keys.recents] = JSONArray(settings.recents).toString()
        this[Keys.seeded] = settings.seeded
        this[Keys.clock24] = settings.clock24
        this[Keys.seconds] = settings.showSeconds
        this[Keys.clockSp] = settings.clockSp.coerceIn(72, 144)
        this[Keys.dock] = settings.showDock
        this[Keys.dockLabels] = settings.dockLabels
        this[Keys.weatherInline] = settings.weatherInline
        this[Keys.fahrenheit] = settings.fahrenheit
        this[Keys.dim] = settings.dimPercent.coerceIn(0, 70)
        this[Keys.clockPlate] = settings.clockPlate
        this[Keys.showRecents] = settings.showRecents
        this[Keys.hints] = settings.showHints
        this[Keys.sortMode] = settings.sortMode
        this[Keys.confirmDisable] = settings.confirmDisable
        this[Keys.lastInput] = settings.lastInputId
        this[Keys.focusSound] = settings.focusSound
        this[Keys.focusChosen] = settings.focusChosen
        this[Keys.places] = JSONArray(settings.weatherPlaces).toString()
        this[Keys.inputNames] = JSONObject(settings.inputNames).toString()
        this[Keys.favoriteInputs] = JSONArray(settings.favoriteInputs).toString()
    }

    private fun strings(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun objects(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, obj.getString(key))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun iconDp(prefs: Preferences): Int {
        prefs[Keys.iconDp]?.let { return it.coerceIn(24, 180) }
        return when (prefs[Keys.iconSize]) {
            "Small" -> 40
            "Large" -> 72
            else -> 48
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return enumValues<T>().firstOrNull { it.name == raw } ?: fallback
    }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val wallpaper = stringPreferencesKey("wallpaper")
        val wallpaperUri = stringPreferencesKey("wallpaper_uri")
        val iconSize = stringPreferencesKey("icon_size")
        val iconDp = intPreferencesKey("icon_dp")
        val columns = intPreferencesKey("columns")
        val motion = stringPreferencesKey("motion")
        val clock = booleanPreferencesKey("clock")
        val date = booleanPreferencesKey("date")
        val weather = booleanPreferencesKey("weather")
        val weatherPlace = stringPreferencesKey("weather_place")
        val favorites = stringPreferencesKey("favorites")
        val hidden = stringPreferencesKey("hidden")
        val order = stringPreferencesKey("order")
        val overrides = stringPreferencesKey("overrides")
        val categories = stringPreferencesKey("categories")
        val recents = stringPreferencesKey("recents")
        val seeded = booleanPreferencesKey("seeded")
        val clock24 = booleanPreferencesKey("clock_24")
        val seconds = booleanPreferencesKey("clock_seconds")
        val clockSp = intPreferencesKey("clock_sp")
        val dock = booleanPreferencesKey("dock")
        val dockLabels = stringPreferencesKey("dock_labels")
        val weatherInline = booleanPreferencesKey("weather_inline")
        val fahrenheit = booleanPreferencesKey("fahrenheit")
        val dim = intPreferencesKey("dim")
        val clockPlate = booleanPreferencesKey("clock_plate")
        val hints = booleanPreferencesKey("hints")
        val sortMode = stringPreferencesKey("sort_mode")
        val showRecents = booleanPreferencesKey("show_recents")
        val confirmDisable = booleanPreferencesKey("confirm_disable")
        val lastInput = stringPreferencesKey("last_input")
        val focusSound = booleanPreferencesKey("focus_sound")
        val focusChosen = booleanPreferencesKey("focus_chosen")
        val places = stringPreferencesKey("weather_places")
        val inputNames = stringPreferencesKey("input_names")
        val favoriteInputs = stringPreferencesKey("favorite_inputs")
    }
}
