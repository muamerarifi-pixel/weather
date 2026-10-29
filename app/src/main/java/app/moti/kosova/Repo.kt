package app.moti.kosova

import android.content.Context

/** Downloads and caches weather per city. */
object Repo {

    private class Memo(val key: String, val weather: Weather)

    private val memo = HashMap<String, Memo>()

    /** Downloads fresh data for [city]. Throws if the forecast can't be fetched. */
    fun fetch(ctx: Context, city: City) {
        val fJson = Api.get(Api.forecastUrl(city))
        Parser.forecast(fJson) // make sure it is valid before saving
        val now = System.currentTimeMillis()
        Prefs.saveForecast(ctx, city.id, fJson, now)
        try {
            val aJson = Api.get(Api.airUrl(city))
            Parser.air(aJson)
            Prefs.saveAir(ctx, city.id, aJson, now)
        } catch (e: Exception) {
            // Air quality is optional; keep the previous value.
        }
    }

    fun load(ctx: Context, cityId: String): Weather? {
        val fJson = Prefs.forecastJson(ctx, cityId) ?: return null
        val fAt = Prefs.forecastAt(ctx, cityId)
        val aJson = Prefs.airJson(ctx, cityId)
        val aAt = Prefs.airAt(ctx, cityId)
        val key = "$fAt/$aAt"
        synchronized(this) {
            val m = memo[cityId]
            if (m != null && m.key == key) return m.weather
        }
        return try {
            val f = Parser.forecast(fJson)
            val a = aJson?.let { runCatching { Parser.air(it) }.getOrNull() }
            val w = Weather(fAt, f, a, aAt)
            synchronized(this) { memo[cityId] = Memo(key, w) }
            w
        } catch (e: Exception) {
            null
        }
    }

    /** Milliseconds since this city's data was downloaded (very large if never). */
    fun age(ctx: Context, cityId: String): Long = System.currentTimeMillis() - Prefs.forecastAt(ctx, cityId)
}
