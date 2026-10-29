package app.moti.kosova

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the widget shows, already as text. */
object Present {

    class Model(
        val hasData: Boolean,
        val city: String,
        val cityLong: String,
        val temp: String,
        val icon: String,
        val condition: String,
        val headline: String,
        val line2: String?,
        val line3: String?,
        val updated: String,
    )

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))

    fun condition(c: Current): String {
        val d = Wx.describe(c.code, c.isDay)
        val feels = "Ndihet si ${Wx.deg(c.feels)}°"
        return if (d.isEmpty()) feels else "$d · $feels"
    }

    fun model(ctx: Context, city: City, w: Weather?, nowMs: Long, refreshing: Boolean): Model {
        val cityLong = "${city.name}, Kosovë"
        if (w == null) {
            val failed = Prefs.lastFailed(ctx) && !refreshing
            return Model(
                hasData = false,
                city = city.name,
                cityLong = cityLong,
                temp = "--",
                icon = "⏳",
                condition = "",
                headline = if (failed) "S'ka lidhje me internetin." else "Duke marrë motin…",
                line2 = if (failed) "Prek ↻ për ta provuar sërish." else null,
                line3 = null,
                updated = if (refreshing) "Duke rifreskuar…" else "↻",
            )
        }
        val c = Insights.effectiveCurrent(w, nowMs)
        val top = Insights.pickTop(Insights.build(w, nowMs), 3)

        fun line(m: Msg?): String? {
            if (m == null) return null
            val t = m.short ?: m.text
            return if (m.emoji.isEmpty()) t else "$t ${m.emoji}"
        }

        val headline = top.getOrNull(0)?.let { it.short ?: it.text } ?: Wx.describe(c.code, c.isDay)
        val updated = if (refreshing) "Duke rifreskuar…" else "↻ ${time(w.fetchedAt)}"
        return Model(
            hasData = true,
            city = city.name,
            cityLong = cityLong,
            temp = Wx.deg(c.temp),
            icon = Wx.emoji(c.code, c.isDay),
            condition = condition(c),
            headline = headline,
            line2 = line(top.getOrNull(1)),
            line3 = line(top.getOrNull(2)),
            updated = updated,
        )
    }
}
