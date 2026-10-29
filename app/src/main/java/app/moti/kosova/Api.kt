package app.moti.kosova

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Open-Meteo: free, no API key. Weather models (ECMWF, ICON, …) + Copernicus CAMS air quality. */
object Api {

    private const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    private const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"

    fun forecastUrl(c: City): String = FORECAST +
        "?latitude=${c.lat}&longitude=${c.lon}" +
        "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,snowfall," +
        "weather_code,wind_speed_10m,wind_gusts_10m" +
        "&hourly=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation_probability,precipitation," +
        "snowfall,weather_code,visibility,wind_gusts_10m,is_day" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_min,sunrise,sunset," +
        "uv_index_max,precipitation_sum,snowfall_sum,precipitation_probability_max" +
        "&timezone=auto&timeformat=unixtime&forecast_days=3"

    fun airUrl(c: City): String = AIR +
        "?latitude=${c.lat}&longitude=${c.lon}" +
        "&current=european_aqi&hourly=european_aqi" +
        "&timezone=auto&timeformat=unixtime&forecast_days=2"

    fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Moti-Android")
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
