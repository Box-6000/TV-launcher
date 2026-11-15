package com.launch.tv.data

import android.content.Context
import com.launch.tv.model.WeatherInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

class WeatherRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "weather.json")

    fun cached(): WeatherInfo? = read()?.info

    suspend fun refresh(place: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val query = place.trim()
        try {
            val stored = read()
            val geo = when {
                query.isNotEmpty() -> runCatching { geocode(query) }.getOrNull()
                stored != null && stored.place.isBlank() && stored.geo != null -> stored.geo
                else -> runCatching { locate() }.getOrNull()
            }
            val info = loadForecast(query, geo) ?: return@withContext if (query.isEmpty()) cached() else null
            val city = geo?.city?.takeIf { it.isNotBlank() } ?: info.city.ifBlank { query.ifBlank { "Рядом" } }
            val ready = info.copy(city = city)
            write(geo ?: Geo(0.0, 0.0, city), ready, query)
            ready
        } catch (_: Exception) {
            if (query.isNotEmpty()) null else cached()
        }
    }

    private fun loadForecast(query: String, geo: Geo?): WeatherInfo? {
        if (query.isNotEmpty()) {
            runCatching { wttr(query) }.getOrNull()?.let { return it }
        }
        if (geo != null && !(geo.lat == 0.0 && geo.lon == 0.0)) {
            runCatching { metNo(geo) }.getOrNull()?.let { return it }
            runCatching { openMeteo(geo) }.getOrNull()?.let { return it }
        }
        val name = geo?.city?.takeIf { it.isNotBlank() && it != "Рядом" }
        if (name != null) {
            runCatching { wttr(name) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun wttr(place: String): WeatherInfo {
        val path = URLEncoder.encode(place, "UTF-8").replace("+", "%20")
        val json = JSONObject(http("https://wttr.in/$path?format=j1&lang=ru", 7000))
        val current = json.getJSONArray("current_condition").getJSONObject(0)
        val area = json.optJSONArray("nearest_area")
            ?.optJSONObject(0)
            ?.optJSONArray("areaName")
            ?.optJSONObject(0)
            ?.optString("value")
            .orEmpty()
        return WeatherInfo(
            city = area.ifBlank { place },
            tempC = current.getString("temp_C").toDouble().roundToInt(),
            code = wwo(current.getString("weatherCode").toInt()),
            outlook = outlook(json),
        )
    }

    private fun metNo(geo: Geo): WeatherInfo {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${geo.lat}&lon=${geo.lon}"
        val data = JSONObject(http(url, 7000))
            .getJSONObject("properties")
            .getJSONArray("timeseries")
            .getJSONObject(0)
            .getJSONObject("data")
        val temp = data.getJSONObject("instant").getJSONObject("details").getDouble("air_temperature")
        val symbol = data.optJSONObject("next_1_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: data.optJSONObject("next_6_hours")?.optJSONObject("summary")?.optString("symbol_code")
            ?: ""
        return WeatherInfo(geo.city, temp.roundToInt(), symbolCode(symbol))
    }

    private fun openMeteo(geo: Geo): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${geo.lat}&longitude=${geo.lon}&current=temperature_2m,weather_code"
        val current = JSONObject(http(url, 3500)).getJSONObject("current")
        return WeatherInfo(
            city = geo.city,
            tempC = current.getDouble("temperature_2m").roundToInt(),
            code = current.getInt("weather_code"),
        )
    }

    private fun outlook(json: JSONObject): String {
        val day = json.optJSONArray("weather")?.optJSONObject(1) ?: return ""
        val min = day.optString("mintempC")
        val max = day.optString("maxtempC")
        if (min.isBlank() || max.isBlank()) return ""
        return "Завтра $min…$max°"
    }

    private fun wwo(code: Int): Int = when (code) {
        113 -> 0
        116, 119, 122 -> 2
        143, 248, 260 -> 45
        179, 182, 185, 227, 230, 317, 320, 323, 326, 329, 332, 335, 338, 350, 362, 365, 368, 371, 374, 377 -> 71
        200, 386, 389, 392, 395 -> 95
        else -> 61
    }

    private fun symbolCode(symbol: String): Int {
        val value = symbol.lowercase()
        return when {
            "thunder" in value -> 95
            "snow" in value || "sleet" in value -> 71
            "rain" in value || "drizzle" in value -> 61
            "fog" in value -> 45
            "clear" in value -> 0
            else -> 2
        }
    }

    private fun geocode(place: String): Geo? {
        val query = URLEncoder.encode(place, "UTF-8")
        val results = JSONObject(http(
            "https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=ru",
        )).optJSONArray("results") ?: JSONArray()
        if (results.length() == 0) return null
        val item = results.getJSONObject(0)
        val name = item.optString("name", place).ifBlank { place }
        return Geo(item.getDouble("latitude"), item.getDouble("longitude"), name)
    }

    private fun locate(): Geo {
        val primary = runCatching { geoFrom("https://ipwho.is/") { json ->
            if (!json.optBoolean("success", true)) error("geo")
            Geo(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city", "Рядом"))
        } }.getOrNull()
        if (primary != null) return primary
        return geoFrom("https://ipapi.co/json/") { json ->
            Geo(json.getDouble("latitude"), json.getDouble("longitude"), json.optString("city", "Рядом"))
        }
    }

    private fun geoFrom(url: String, parse: (JSONObject) -> Geo): Geo = parse(JSONObject(http(url)))

    private fun http(url: String, timeoutMs: Int = 5000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "LaunchTV/1.0")
        }
        try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("http ${connection.responseCode}")
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun read(): Stored? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val info = WeatherInfo(json.getString("city"), json.getInt("temp"), json.getInt("code"))
            val geo = if (json.has("lat")) {
                Geo(json.getDouble("lat"), json.getDouble("lon"), json.getString("city"))
            } else {
                null
            }
            Stored(geo, info, json.optString("place", ""))
        } catch (_: Exception) {
            null
        }
    }

    private fun write(geo: Geo, info: WeatherInfo, place: String) {
        val json = JSONObject()
            .put("lat", geo.lat)
            .put("lon", geo.lon)
            .put("city", info.city.ifBlank { "Рядом" })
            .put("temp", info.tempC)
            .put("code", info.code)
            .put("place", place)
        file.writeText(json.toString())
    }

    private data class Geo(val lat: Double, val lon: Double, val city: String)
    private data class Stored(val geo: Geo?, val info: WeatherInfo, val place: String)
}
