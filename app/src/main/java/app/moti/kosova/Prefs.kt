package app.moti.kosova

import android.content.Context
import android.content.SharedPreferences

/** Settings and the last downloaded weather, per city. */
object Prefs {

    const val DEFAULT_INTERVAL = 30
    const val DEFAULT_TRANSPARENCY = 20

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("moti", Context.MODE_PRIVATE)

    // ---- city ----
    fun defaultCity(ctx: Context): City = Cities.byId(sp(ctx).getString("city", null)) ?: Cities.default

    fun setDefaultCity(ctx: Context, id: String) = sp(ctx).edit().putString("city", id).apply()

    fun cityForWidget(ctx: Context, widgetId: Int): City =
        Cities.byId(sp(ctx).getString("w_$widgetId", null)) ?: defaultCity(ctx)

    fun setWidgetCity(ctx: Context, widgetId: Int, id: String) =
        sp(ctx).edit().putString("w_$widgetId", id).apply()

    fun clearWidgetCity(ctx: Context, widgetId: Int) = sp(ctx).edit().remove("w_$widgetId").apply()

    fun removeWidgets(ctx: Context, ids: IntArray) {
        val e = sp(ctx).edit()
        for (id in ids) e.remove("w_$id")
        e.apply()
    }

    // ---- refresh interval (minutes) ----
    fun interval(ctx: Context): Int = sp(ctx).getInt("interval", DEFAULT_INTERVAL)

    fun setInterval(ctx: Context, minutes: Int) = sp(ctx).edit().putInt("interval", minutes).apply()

    // ---- widget glass transparency, 0..90 % ----
    fun transparency(ctx: Context): Int = sp(ctx).getInt("transparency", DEFAULT_TRANSPARENCY)

    fun setTransparency(ctx: Context, percent: Int) = sp(ctx).edit().putInt("transparency", percent).apply()

    fun glassAlpha(ctx: Context): Int = ((100 - transparency(ctx)) * 255 / 100).coerceIn(0, 255)

    // ---- status ----
    fun lastFailed(ctx: Context): Boolean = sp(ctx).getBoolean("failed", false)

    fun setLastFailed(ctx: Context, failed: Boolean) = sp(ctx).edit().putBoolean("failed", failed).apply()

    // ---- cached data ----
    fun saveForecast(ctx: Context, cityId: String, json: String, at: Long) =
        sp(ctx).edit().putString("f_$cityId", json).putLong("ft_$cityId", at).apply()

    fun saveAir(ctx: Context, cityId: String, json: String, at: Long) =
        sp(ctx).edit().putString("a_$cityId", json).putLong("at_$cityId", at).apply()

    fun forecastJson(ctx: Context, cityId: String): String? = sp(ctx).getString("f_$cityId", null)

    fun forecastAt(ctx: Context, cityId: String): Long = sp(ctx).getLong("ft_$cityId", 0L)

    fun airJson(ctx: Context, cityId: String): String? = sp(ctx).getString("a_$cityId", null)

    fun airAt(ctx: Context, cityId: String): Long = sp(ctx).getLong("at_$cityId", 0L)
}
