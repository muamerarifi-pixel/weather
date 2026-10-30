package app.moti.kosova

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the widget (and the app card) shows, already as text. */
object Present {

    class Hour(val label: String, val emoji: String, val temp: String)

    class Model(
        val hasData: Boolean,
        val tomorrow: Boolean,
        val city: String,
        val title: String,      // "Prishtinë, Kosovë" or "Prishtinë · E shtunë"
        val temp: String,       // "12"
        val unit: String,       // "°C" or "° / 5°"
        val tempShort: String,  // "12°"
        val icon: String,
        val condition: String,
        val headline: String,
        val lines: List<String>, // up to 2 for the widget
        val all: List<Msg>,     // every sentence (app list)
        val updated: String,
        val hours: List<Hour>,
    )

    fun time(ms: Long): String = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(ms))

    fun condition(c: Current): String {
        val d = Wx.describe(c.code, c.isDay)
        val feels = "Ndihet si ${Wx.deg(c.feels)}°"
        return if (d.isEmpty()) feels else "$d · $feels"
    }

    fun line(m: Msg, emojis: Boolean, short: Boolean = true): String {
        val t = if (short) m.short ?: m.text else m.text
        return if (!emojis || m.emoji.isEmpty()) t else "$t ${m.emoji}"
    }

    fun model(ctx: Context, city: City, w: Weather?, nowMs: Long, refreshing: Boolean, tomorrow: Boolean): Model {
        val emojis = Prefs.emojis(ctx)
        if (w == null) {
            val failed = Prefs.lastFailed(ctx) && !refreshing
            return Model(
                hasData = false, tomorrow = false, city = city.name, title = "${city.name}, Kosovë",
                temp = "--", unit = "", tempShort = "--", icon = "⏳", condition = "",
                headline = if (failed) "S'ka lidhje me internetin." else "Duke marrë motin…",
                lines = if (failed) listOf("Prek ↻ për ta provuar sërish.") else emptyList(),
                all = emptyList(),
                updated = if (refreshing) "Duke rifreskuar…" else "↻", hours = emptyList(),
            )
        }
        val f = w.forecast
        val off = f.utcOffset.toLong()
        val now = nowMs / 1000
        val today = Math.floorDiv(now + off, 86400L)
        fun localHour(t: Long) = (Math.floorMod(t + off, 86400L) / 3600L).toInt()
        val updated = if (refreshing) "Duke rifreskuar…" else "↻ ${time(w.fetchedAt)}"
        val h = f.hourly
        val d = f.daily
        var di = -1
        for (k in 0 until d.size) if (Math.floorDiv(d.time[k] + off, 86400L) == today) { di = k; break }
        val ti = di + 1
        val showTomorrow = tomorrow && di >= 0 && ti < d.size

        fun hour(i: Int, label: String): Hour? {
            if (i < 0 || i + 1 >= h.size) return null
            return Hour(label, Wx.emoji(h.code.at(i + 1), h.isDay.at(i) == 1), "${Wx.deg(h.temp.at(i))}°")
        }

        if (showTomorrow) {
            val all = Insights.buildTomorrow(w, nowMs)
            val top = Insights.pickTop(all, 3)
            val code = d.code.at(ti)
            val pop = d.popMax.at(ti)
            var cond = Wx.describe(code, true)
            if (!pop.isNaN() && pop >= 20) cond += " · Shi ${pop.toInt()}%"
            val hours = ArrayList<Hour>()
            for (hr in intArrayOf(8, 11, 14, 17, 20)) {
                var idx = -1
                for (i in 0 until h.size) {
                    if (Math.floorDiv(h.time[i] + off, 86400L) == today + 1 && localHour(h.time[i]) == hr) { idx = i; break }
                }
                hour(idx, String.format(Locale.ROOT, "%02d", hr))?.let { hours += it }
            }
            return Model(
                hasData = true, tomorrow = true, city = city.name,
                title = "${city.name} · ${Wx.dayName(today + 1)}",
                temp = Wx.deg(d.tmax.at(ti)), unit = "° / ${Wx.deg(d.tmin.at(ti))}°",
                tempShort = "${Wx.deg(d.tmax.at(ti))}°",
                icon = Wx.emoji(code, true), condition = cond,
                headline = top.getOrNull(0)?.let { it.short ?: it.text } ?: cond,
                lines = top.drop(1).map { line(it, emojis) },
                all = all, updated = updated, hours = hours,
            )
        }

        val c = Insights.effectiveCurrent(w, nowMs)
        val all = Insights.build(w, nowMs)
        val top = Insights.pickTop(all, 3)
        val hours = ArrayList<Hour>()
        val ci = Insights.hourIndex(h, now)
        if (ci >= 0) for (k in 1..5) {
            val i = ci + k
            if (i >= h.size) break
            hour(i, String.format(Locale.ROOT, "%02d", localHour(h.time[i])))?.let { hours += it }
        }
        return Model(
            hasData = true, tomorrow = false, city = city.name, title = "${city.name}, Kosovë",
            temp = Wx.deg(c.temp), unit = "°C", tempShort = "${Wx.deg(c.temp)}°",
            icon = Wx.emoji(c.code, c.isDay), condition = condition(c),
            headline = top.getOrNull(0)?.let { it.short ?: it.text } ?: Wx.describe(c.code, c.isDay),
            lines = top.drop(1).map { line(it, emojis) },
            all = all, updated = updated, hours = hours,
        )
    }
}
