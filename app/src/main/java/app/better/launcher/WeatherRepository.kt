package app.better.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import kotlin.math.roundToInt

data class WeatherCity(val name: String, val latitude: Double, val longitude: Double)
data class Weather(val temperature: Int, val code: Int, val city: String) {
    val descriptionRes: Int get() = when (code) {
        0 -> R.string.weather_clear; 1, 2 -> R.string.weather_partly_cloudy; 3 -> R.string.weather_cloudy; 45, 48 -> R.string.weather_fog
        in 51..67, in 80..82 -> R.string.weather_rain; in 71..77, 85, 86 -> R.string.weather_snow; in 95..99 -> R.string.weather_thunderstorm; else -> R.string.weather
    }
}
object WeatherRepository {
    private fun fetch(url: String): JSONObject {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000; connection.readTimeout = 8_000
        return try { connection.inputStream.bufferedReader().use { JSONObject(it.readText()) } } finally { connection.disconnect() }
    }
    fun cities(query: String, language: String): List<WeatherCity> {
        val responseLanguage = if (language == "ru") "ru" else "en"
        val results = fetch("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query, "UTF-8")}&count=5&language=$responseLanguage&format=json").optJSONArray("results") ?: return emptyList()
        return List(results.length()) { i -> results.getJSONObject(i).let { WeatherCity("${it.getString("name")}, ${it.optString("country")}", it.getDouble("latitude"), it.getDouble("longitude")) } }
    }
    fun current(city: WeatherCity): Weather {
        val current = fetch("https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}&current=temperature_2m,weather_code&timezone=auto").getJSONObject("current")
        return Weather(current.getDouble("temperature_2m").roundToInt(), current.getInt("weather_code"), city.name)
    }
}
