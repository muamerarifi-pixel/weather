package app.moti.kosova

import org.json.JSONObject

/** Reads Open-Meteo JSON (requested with timeformat=unixtime). */
object Parser {

    fun forecast(json: String): Forecast {
        val o = JSONObject(json)
        val off = o.optInt("utc_offset_seconds", 0)
        val c = o.getJSONObject("current")
        val current = Current(
            time = c.optLong("time", 0L),
            temp = c.num("temperature_2m"),
            feels = c.num("apparent_temperature"),
            humidity = c.num("relative_humidity_2m"),
            isDay = c.optInt("is_day", 1) == 1,
            precip = c.num("precipitation"),
            snowfall = c.num("snowfall"),
            code = c.optInt("weather_code", 0),
            wind = c.num("wind_speed_10m"),
            gusts = c.num("wind_gusts_10m"),
        )
        val h = o.getJSONObject("hourly")
        val hourly = Hourly(
            time = h.longs("time"),
            temp = h.nums("temperature_2m"),
            feels = h.nums("apparent_temperature"),
            humidity = h.nums("relative_humidity_2m"),
            pop = h.nums("precipitation_probability"),
            precip = h.nums("precipitation"),
            snowfall = h.nums("snowfall"),
            code = h.ints("weather_code"),
            gusts = h.nums("wind_gusts_10m"),
            visibility = h.nums("visibility"),
            isDay = h.ints("is_day"),
        )
        val d = o.getJSONObject("daily")
        val daily = Daily(
            time = d.longs("time"),
            code = d.ints("weather_code"),
            tmax = d.nums("temperature_2m_max"),
            tmin = d.nums("temperature_2m_min"),
            feelsMin = d.nums("apparent_temperature_min"),
            precipSum = d.nums("precipitation_sum"),
            snowSum = d.nums("snowfall_sum"),
            popMax = d.nums("precipitation_probability_max"),
            uvMax = d.nums("uv_index_max"),
            sunrise = d.longs("sunrise"),
            sunset = d.longs("sunset"),
        )
        if (hourly.size == 0) throw IllegalStateException("Empty forecast")
        return Forecast(off, current, hourly, daily)
    }

    fun air(json: String): Air {
        val o = JSONObject(json)
        val c = o.optJSONObject("current")
        val h = o.optJSONObject("hourly")
        return Air(
            currentTime = c?.optLong("time", 0L) ?: 0L,
            currentAqi = c?.num("european_aqi") ?: Double.NaN,
            time = h?.longs("time") ?: LongArray(0),
            aqi = h?.nums("european_aqi") ?: DoubleArray(0),
        )
    }

    private fun JSONObject.num(k: String): Double =
        if (!has(k) || isNull(k)) Double.NaN else optDouble(k, Double.NaN)

    private fun JSONObject.nums(k: String): DoubleArray {
        val a = optJSONArray(k) ?: return DoubleArray(0)
        return DoubleArray(a.length()) { i -> if (a.isNull(i)) Double.NaN else a.optDouble(i, Double.NaN) }
    }

    private fun JSONObject.ints(k: String): IntArray {
        val a = optJSONArray(k) ?: return IntArray(0)
        return IntArray(a.length()) { i -> if (a.isNull(i)) -1 else a.optInt(i, -1) }
    }

    private fun JSONObject.longs(k: String): LongArray {
        val a = optJSONArray(k) ?: return LongArray(0)
        return LongArray(a.length()) { i -> a.optLong(i, 0L) }
    }
}
