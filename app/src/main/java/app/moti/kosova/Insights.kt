package app.moti.kosova

import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class Cat { PRECIP, UMBRELLA, CLOTHES, TOMORROW, AIR, HUMID, WIND, FOG, ICE, SUNHEAT, TREND, NICE, SUN }

/**
 * One plain-Albanian sentence about the weather.
 * [short] is a tighter version for the widget, [alt] avoids repeating "nëse del tani".
 */
class Msg(
    val text: String,
    val emoji: String,
    val priority: Int,
    val cat: Cat,
    val short: String? = null,
    val alt: String? = null,
) {
    fun withText(t: String) = Msg(t, emoji, priority, cat, null, null)
}

/** Turns the forecast into short, natural sentences: "Më vonë bie shi.", "Nesër do të jetë shumë ftohtë." */
object Insights {

    private const val HOUR = 3600L

    private enum class P { RAIN, SNOW, SLEET, STORM, FREEZE }

    /** Index of the hour that has started most recently (time <= now), or -1. */
    fun hourIndex(h: Hourly, now: Long): Int {
        var ci = -1
        for (i in 0 until h.size) {
            if (h.time[i] <= now) ci = i else break
        }
        return ci
    }

    /** Current conditions; if the saved data is old, "now" is rebuilt from the hourly forecast. */
    fun effectiveCurrent(w: Weather, nowMs: Long): Current {
        val f = w.forecast
        val c = f.current
        val now = nowMs / 1000
        if (now - c.time < 50 * 60) return c
        val h = f.hourly
        val i = hourIndex(h, now)
        if (i < 0 || i + 1 >= h.size) return c
        val j = if (now - h.time[i] > 1800) i + 1 else i
        val code = h.code.at(i + 1).let { if (it >= 0) it else c.code }
        val dayFlag = h.isDay.at(j)
        return Current(
            time = now,
            temp = h.temp.at(j).ifNaN(c.temp),
            feels = h.feels.at(j).ifNaN(c.feels),
            humidity = h.humidity.at(j).ifNaN(c.humidity),
            isDay = if (dayFlag < 0) c.isDay else dayFlag == 1,
            precip = h.precip.at(i + 1).or0() / 4.0,
            snowfall = h.snowfall.at(i + 1).or0() / 4.0,
            code = code,
            wind = c.wind,
            gusts = h.gusts.at(j).ifNaN(c.gusts),
        )
    }

    /** All sentences, most important first. */
    fun build(w: Weather, nowMs: Long): List<Msg> {
        val f = w.forecast
        val h = f.hourly
        val d = f.daily
        val c = effectiveCurrent(w, nowMs)
        val now = nowMs / 1000
        val off = f.utcOffset.toLong()

        fun day(t: Long): Long = Math.floorDiv(t + off, 86400L)
        fun hour(t: Long): Int = (Math.floorMod(t + off, 86400L) / 3600L).toInt()
        fun hm(t: Long): String {
            val s = Math.floorMod(t + off, 86400L)
            return String.format(Locale.ROOT, "%02d:%02d", s / 3600, (s % 3600) / 60)
        }

        val today = day(now)
        val nowHour = hour(now)
        val ci = hourIndex(h, now)
        val hasH = ci >= 0 && ci + 1 < h.size
        var di = -1
        for (k in 0 until d.size) if (day(d.time[k]) == today) { di = k; break }

        val out = ArrayList<Msg>()

        // ---------------- precipitation ----------------
        fun wet(i: Int): Boolean {
            val p = h.precip.at(i)
            if (p.isNaN()) return false
            val pop = h.pop.at(i)
            val code = h.code.at(i)
            if (code in 95..99 && p >= 0.1) return true
            if (p >= 0.3) return true
            if (p >= 0.1 && (pop.isNaN() || pop >= 50)) return true
            return false
        }

        fun type(code: Int, precip: Double, snowfall: Double): P {
            if (code in 95..99) return P.STORM
            if (code == 56 || code == 57 || code == 66 || code == 67) return P.FREEZE
            val p = precip.or0()
            val snowWater = snowfall.or0() * 10.0 / 7.0 // Open-Meteo: 7 cm snow ≈ 10 mm water
            val snowCode = code in 71..77 || code == 85 || code == 86
            if (snowCode || (p > 0 && snowWater >= p * 0.7)) {
                return if (p > 0 && snowWater < p * 0.4) P.SLEET else P.SNOW
            }
            if (p > 0 && snowWater >= p * 0.25) return P.SLEET
            return P.RAIN
        }

        fun hType(i: Int) = type(h.code.at(i), h.precip.at(i), h.snowfall.at(i))

        fun verb(t: P) = when (t) {
            P.RAIN -> "bie shi"
            P.SNOW -> "bie borë"
            P.SLEET -> "bie shi me borë"
            P.STORM -> "vjen stuhia"
            P.FREEZE -> "bie shi që ngrin"
        }

        fun icon(t: P) = when (t) {
            P.RAIN -> "🌧️"
            P.SNOW, P.SLEET -> "🌨️"
            P.STORM -> "⛈️"
            P.FREEZE -> "🧊"
        }

        val cp = c.precip.or0()
        val precipCodeNow = c.code in 61..67 || c.code in 71..77 || c.code in 80..86 || c.code in 95..99
        val strongCodeNow = c.code in 63..67 || c.code in 73..75 || c.code == 81 || c.code == 82 ||
            c.code == 86 || c.code in 95..99
        val rainingNow = cp >= 0.05 || (precipCodeNow && cp > 0.0) || strongCodeNow
        var wetSoonMins = Int.MAX_VALUE

        if (rainingNow) {
            val t = type(c.code, c.precip, c.snowfall)
            wetSoonMins = 0
            val nowTxt = when (t) {
                P.RAIN -> "Tani po bie shi."
                P.SNOW -> "Tani po bie borë."
                P.SLEET -> "Tani po bie shi me borë."
                P.STORM -> "Tani ka stuhi me bubullima."
                P.FREEZE -> "Kujdes! Po bie shi që ngrin."
            }
            out += Msg(nowTxt, icon(t), 100, Cat.PRECIP)
            if (hasH) {
                val noun = when (t) {
                    P.RAIN, P.FREEZE -> "Shiu"
                    P.SNOW -> "Bora"
                    P.SLEET -> "Reshjet"
                    P.STORM -> "Stuhia"
                }
                var stopK = -1
                if (!wet(ci + 1) && (ci + 2 >= h.size || !wet(ci + 2))) {
                    stopK = 1
                } else {
                    for (k in 2..9) {
                        val i = ci + k
                        if (i >= h.size) break
                        if (!wet(i) && (i + 1 >= h.size || !wet(i + 1))) { stopK = k; break }
                    }
                }
                if (stopK > 0) {
                    val stopAt = h.time[ci + stopK] - HOUR
                    val mins = max(10L, (stopAt - now) / 60).toInt()
                    val v = when (t) {
                        P.STORM -> "qetësohet"
                        P.SLEET -> "pushojnë"
                        else -> "pushon"
                    }
                    out += Msg("$noun $v pas rreth ${dur(mins)}.", "⏳", 74, Cat.TREND)
                } else {
                    val v = if (t == P.SLEET) "vazhdojnë" else "vazhdon"
                    out += Msg("$noun $v edhe disa orë.", "", 58, Cat.TREND)
                }
            }
            if (t == P.RAIN || t == P.STORM) {
                out += Msg("Merr çadër nëse del tani.", "☔", 84, Cat.UMBRELLA)
            } else {
                out += Msg("Rrugët mund të jenë të rrëshqitshme.", "⚠️", 78, Cat.ICE)
            }
        } else if (hasH) {
            val maxK = min(h.size - 1 - ci, 30)
            var firstK = -1
            for (k in 1..maxK) if (wet(ci + k)) { firstK = k; break }
            var said = false
            if (firstK > 0) {
                val i = ci + firstK
                val t = hType(i)
                val start = max(now, h.time[i] - HOUR)
                val mins = ((start - now) / 60).toInt()
                val sDay = day(start)
                val sHour = hour(start)
                val v = verb(t)
                var txt: String? = null
                var pr = 0
                when {
                    mins < 45 -> { txt = "Së shpejti $v."; pr = 97 }
                    mins < 90 -> { txt = "Pas një ore $v."; pr = 95 }
                    mins < 150 -> { txt = "Pas dy orësh $v."; pr = 92 }
                    mins < 210 -> { txt = "Pas tri orësh $v."; pr = 88 }
                    mins < 270 -> { txt = "Pas katër orësh $v."; pr = 82 }
                    mins < 330 -> { txt = "Pas pesë orësh $v."; pr = 78 }
                    sDay == today && sHour >= 18 -> { txt = "Sonte $v."; pr = 76 }
                    sDay == today -> { txt = "Më vonë $v."; pr = 78 }
                    sDay == today + 1 && sHour < 6 -> { txt = "Natën $v."; pr = 68 }
                }
                if (t != P.RAIN) pr += 4 // snow, sleet, storms and freezing rain matter more
                if (txt != null) {
                    out += Msg(txt, icon(t), pr, Cat.PRECIP)
                    said = true
                    wetSoonMins = mins
                    if (t == P.RAIN || t == P.STORM) {
                        if (mins < 210) {
                            out += Msg("Merr çadër nëse del tani.", "☔", 84, Cat.UMBRELLA)
                        } else if (sDay == today) {
                            out += Msg("Merr çadër me vete sot.", "☔", 54, Cat.UMBRELLA)
                        }
                    } else if (mins < 360) {
                        out += Msg("Rrugët mund të bëhen të rrëshqitshme.", "⚠️", 62, Cat.ICE)
                    }
                }
            }
            if (!said) {
                var maxPop = 0.0
                var snowy = false
                for (k in 1..min(12, maxK)) {
                    val p = h.pop.at(ci + k)
                    if (!p.isNaN() && p > maxPop) {
                        maxPop = p
                        snowy = hType(ci + k) == P.SNOW
                    }
                }
                if (maxPop >= 50) {
                    out += if (snowy) Msg("Ka mundësi për borë më vonë.", "🌨️", 50, Cat.PRECIP)
                    else Msg("Ka mundësi për shi më vonë.", "🌦️", 50, Cat.PRECIP)
                } else {
                    out += Msg(if (nowHour < 17) "Sot nuk pritet shi." else "Sonte nuk pritet shi.", "👍", 24, Cat.PRECIP)
                }
            }
        }

        // ---------------- what to wear ----------------
        val fl = if (!c.feels.isNaN()) c.feels else c.temp
        if (!fl.isNaN()) {
            when {
                fl <= -10 -> out += Msg("Acar i madh! Vishu shumë trashë.", "🧣", 90, Cat.CLOTHES)
                fl <= -3 -> out += Msg("Jashtë është shumë ftohtë. Vishu trashë.", "🧥", 80, Cat.CLOTHES,
                    short = "Shumë ftohtë, vishu trashë.")
                fl <= 4 -> out += Msg("Nëse del tani, vishu më trashë.", "🧥", 74, Cat.CLOTHES,
                    alt = "Vishu më trashë, jashtë është ftohtë.")
                fl <= 10 -> out += Msg("Jashtë është ftohtë. Merr xhaketën.", "🧥", 60, Cat.CLOTHES)
                fl <= 16 -> out += Msg("Është freskët. Merr një xhaketë të lehtë.", "🧥", 42, Cat.CLOTHES,
                    short = "Merr një xhaketë të lehtë.")
                fl <= 25 -> out += Msg("Temperatura jashtë është e këndshme.", "🙂", 26, Cat.CLOTHES)
                fl <= 30 -> out += Msg("Jashtë është ngrohtë. Vishu lehtë.", "👕", 40, Cat.CLOTHES)
                fl <= 35 -> {
                    out += Msg("Tani është shumë nxehtë jashtë.", "🥵", 80, Cat.CLOTHES)
                    out += Msg("Pi shumë ujë sot.", "💧", 50, Cat.SUNHEAT)
                }
                else -> {
                    out += Msg("Vapë e madhe! Shmang diellin.", "🥵", 90, Cat.CLOTHES)
                    out += Msg("Pi shumë ujë sot.", "💧", 62, Cat.SUNHEAT)
                }
            }
            if (!c.temp.isNaN() && c.temp >= 26 && fl - c.temp >= 3) {
                out += Msg("Vapë mbytëse, ka shumë lagështi.", "💦", 58, Cat.HUMID)
            }
            if (!c.temp.isNaN() && c.temp < 15 && c.temp - fl >= 5 && c.wind.or0() >= 15) {
                out += Msg("Era e bën të ndihet më ftohtë.", "🌬️", 52, Cat.WIND)
            }
        }

        // ---------------- humidity ----------------
        if (!rainingNow && wetSoonMins > 120 && !c.humidity.isNaN()) {
            if (c.humidity >= 92 && c.temp.or0() < 24) {
                out += Msg("Tani ka shumë lagështi.", "💧", 34, Cat.HUMID)
            } else if (hasH && c.humidity < 85) {
                for (k in 1..3) {
                    val i = ci + k
                    if (i >= h.size) break
                    if (h.humidity.at(i) >= 92) {
                        val mins = ((h.time[i] - now) / 60).toInt()
                        val whenTxt = when {
                            mins < 45 -> "Së shpejti"
                            mins < 90 -> "Pas një ore"
                            mins < 150 -> "Pas dy orësh"
                            else -> "Pas tri orësh"
                        }
                        out += Msg("$whenTxt ka shumë lagështi.", "💧", 33, Cat.HUMID)
                        break
                    }
                }
            }
        }

        // ---------------- fog ----------------
        val visNow = min(h.visibility.at(ci).ifNaN(1e9), h.visibility.at(ci + 1).ifNaN(1e9))
        val fogNow = c.code == 45 || c.code == 48 || (hasH && visNow < 800)
        if (fogNow) {
            out += Msg("Ka mjegull. Kujdes në rrugë.", "🌫️", 73, Cat.FOG)
        } else if (hasH) {
            for (k in 1..min(12, h.size - 1 - ci)) {
                val i = ci + k
                val code = h.code.at(i)
                if (code == 45 || code == 48) {
                    val t = h.time[i] - HOUR
                    val txt = when {
                        day(t) == today && hour(t) >= 18 -> "Sonte bie mjegull."
                        day(t) == today -> "Më vonë bie mjegull."
                        hour(t) < 12 -> "Nesër në mëngjes ka mjegull."
                        else -> null
                    }
                    if (txt != null) out += Msg(txt, "🌫️", 48, Cat.FOG)
                    break
                }
            }
        }

        // ---------------- frost, ice, evening cooling ----------------
        if (hasH) {
            var minT = Double.NaN
            var k = 1
            while (ci + k < h.size && k <= 20) {
                val i = ci + k
                val t = h.temp.at(i)
                if (!t.isNaN() && (minT.isNaN() || t < minT)) minT = t
                if (day(h.time[i]) > today && hour(h.time[i]) >= 9) break
                k++
            }
            if (nowHour in 14..23 && c.temp.or0() > 1 && !minT.isNaN() && minT <= -0.5) {
                out += Msg("Sonte ngrin. Kujdes nga akulli në mëngjes.", "🧊", 60, Cat.ICE,
                    short = "Sonte ngrin, kujdes nga akulli.")
            }
            if (nowHour in 15..21 && !minT.isNaN() && !c.temp.isNaN() && c.temp - minT >= 7) {
                out += Msg("Sonte ftohet, deri në ${Wx.deg(minT)}°.", "🌙", 40, Cat.TREND)
            }
        }
        if (nowHour in 0..10 && !c.temp.isNaN() && c.temp <= 0.5) {
            var recent = 0.0
            if (ci >= 0) for (i in max(0, ci - 5)..ci) recent += h.precip.at(i).or0()
            if (recent >= 0.5 || c.humidity.or0() >= 90) {
                out += Msg("Mund të ketë akull në rrugë. Kujdes!", "🧊", 72, Cat.ICE)
            }
        }

        // ---------------- wind ----------------
        val g = c.gusts
        if (!g.isNaN() && g >= 75) {
            out += Msg("Erë shumë e fortë! Kujdes jashtë.", "💨", 88, Cat.WIND)
        } else if (!g.isNaN() && g >= 55) {
            out += Msg("Fryn erë e fortë.", "💨", 66, Cat.WIND)
        } else if (hasH) {
            var mg = 0.0
            for (k in 1..min(6, h.size - 1 - ci)) mg = max(mg, h.gusts.at(ci + k).or0())
            if (mg >= 60) out += Msg("Më vonë fryn erë e fortë.", "💨", 55, Cat.WIND)
        }

        // ---------------- sun strength ----------------
        if (di >= 0 && c.isDay && nowHour < 16) {
            val uv = d.uvMax.at(di)
            if (uv >= 8) {
                out += Msg("Dielli është shumë i fortë. Vendos krem mbrojtës.", "🧴", 60, Cat.SUNHEAT,
                    short = "Dielli është shumë i fortë.")
            } else if (uv >= 6) {
                out += Msg("Dielli është i fortë sot. Vendos krem mbrojtës.", "🧴", 46, Cat.SUNHEAT,
                    short = "Dielli është i fortë sot.")
            }
        }

        // ---------------- warming later today ----------------
        if (di >= 0 && nowHour in 5..12) {
            val tmax = d.tmax.at(di)
            if (!tmax.isNaN() && !c.temp.isNaN() && tmax - c.temp >= 6) {
                out += Msg("Më vonë ngrohet, deri në ${Wx.deg(tmax)}°.", "🌤️", 38, Cat.TREND)
            }
        }

        // ---------------- air quality (European AQI) ----------------
        val air = w.air
        if (air != null && nowMs - w.airFetchedAt < 12 * 3600_000L) {
            val v = air.aqiAt(now)
            if (!v.isNaN()) {
                val night = nowHour >= 18 || nowHour < 6
                out += when {
                    v > 100 -> Msg("Ajri është jashtëzakonisht i ndotur! Qëndro brenda.", "😷", 93, Cat.AIR,
                        short = "Ajri është tepër i ndotur!")
                    v > 80 -> Msg("Ajri është shumë i ndotur. Mbaj dritaret mbyllur.", "😷", 87, Cat.AIR,
                        short = "Ajri është shumë i ndotur.")
                    v > 60 -> Msg("Ajri është i ndotur. Shmang vrapimin jashtë.", "😷", 77, Cat.AIR,
                        short = "Ajri është i ndotur.")
                    v > 40 -> Msg(
                        if (night) "Sonte ajri nuk është shumë i pastër." else "Sot ajri nuk është shumë i pastër.",
                        "😐", 46, Cat.AIR)
                    v > 20 -> Msg("Cilësia e ajrit është e mirë.", "🍃", 16, Cat.AIR)
                    else -> Msg("Ajri është i pastër.", "🍃", 20, Cat.AIR)
                }
            }
        }

        // ---------------- tomorrow ----------------
        val ti = di + 1
        if (di >= 0 && ti < d.size) {
            val tmaxT = d.tmax.at(ti)
            val tminT = d.tmin.at(ti)
            val tmax0 = d.tmax.at(di)
            val snowT = d.snowSum.at(ti).or0() >= 1.0
            val wetT = (d.popMax.at(ti).or0() >= 55 && d.precipSum.at(ti).or0() >= 1.0) || d.precipSum.at(ti).or0() >= 3.0
            val stormT = d.code.at(ti) in 95..99
            var coldKind = 0 // 0 none, 1 colder, 2 much colder, 3 very cold
            if (!tmaxT.isNaN()) {
                val diff = if (tmax0.isNaN()) 0.0 else tmaxT - tmax0
                when {
                    tmaxT <= 4 || d.feelsMin.at(ti) <= -7 -> {
                        coldKind = 3
                        out += Msg("Nesër do të jetë shumë ftohtë.", "🧥", 67, Cat.TOMORROW)
                    }
                    diff <= -6 -> {
                        coldKind = 2
                        out += Msg("Nesër bëhet shumë më ftohtë.", "🧥", 63, Cat.TOMORROW)
                    }
                    diff <= -3.5 -> {
                        coldKind = 1
                        out += Msg("Nesër do të jetë më ftohtë.", "🧥", 48, Cat.TOMORROW)
                    }
                    tmaxT >= 34 -> out += Msg("Nesër do të jetë shumë nxehtë.", "🥵", 62, Cat.TOMORROW)
                    diff >= 6 -> out += Msg("Nesër bëhet shumë më ngrohtë.", "☀️", 52, Cat.TOMORROW)
                    diff >= 3.5 -> out += Msg("Nesër do të jetë më ngrohtë.", "🌤️", 44, Cat.TOMORROW)
                }
            }
            when {
                stormT && (wetT || snowT) -> out += Msg("Nesër ka stuhi.", "⛈️", 64, Cat.TOMORROW)
                snowT && coldKind == 3 -> out += Msg("Nesër bie borë dhe është shumë ftohtë.", "❄️", 70, Cat.TOMORROW,
                    short = "Nesër bie borë, shumë ftohtë.")
                snowT && coldKind > 0 -> out += Msg("Nesër bie borë dhe bëhet më ftohtë.", "❄️", 69, Cat.TOMORROW)
                snowT -> out += Msg("Nesër bie borë.", "❄️", 66, Cat.TOMORROW)
                wetT && coldKind == 3 -> out += Msg("Nesër bie shi dhe është shumë ftohtë.", "☔", 68, Cat.TOMORROW,
                    short = "Nesër bie shi, shumë ftohtë.")
                wetT && coldKind > 0 -> out += Msg("Nesër bie shi dhe bëhet më ftohtë.", "☔", 66, Cat.TOMORROW)
                wetT -> out += Msg("Nesër bie shi.", "☔", 56, Cat.TOMORROW)
            }
            if (!wetT && !snowT && d.code.at(ti) in 0..2 && tmaxT in 15.0..29.0) {
                out += Msg("Nesër pritet mot i bukur.", "☀️", 34, Cat.TOMORROW)
            }
            if (!tmaxT.isNaN() && !tminT.isNaN()) {
                out += Msg("Nesër: nga ${Wx.deg(tminT)}° deri në ${Wx.deg(tmaxT)}°.", "🌡️", 14, Cat.TOMORROW)
            }
        }

        // ---------------- nice weather ----------------
        if (!rainingNow && wetSoonMins > 360 && c.code in 0..1 && !fl.isNaN()) {
            if (c.isDay && fl in 15.0..28.0) {
                out += Msg("Mot i bukur. Shijoje ditën!", "😎", 36, Cat.NICE)
            } else if (!c.isDay) {
                out += Msg("Natë e kthjellët me yje.", "✨", 18, Cat.NICE)
            }
        }

        // ---------------- sunrise / sunset ----------------
        if (di >= 0) {
            val sr = d.sunrise.at(di)
            val ss = d.sunset.at(di)
            if (sr > 0 && now < sr) {
                out += Msg("Dielli lind në orën ${hm(sr)}.", "🌅", 10, Cat.SUN)
            } else if (ss > 0 && now < ss) {
                out += Msg("Dielli perëndon në orën ${hm(ss)}.", "🌇", 10, Cat.SUN)
            } else if (ti < d.size && d.sunrise.at(ti) > 0) {
                out += Msg("Nesër dielli lind në orën ${hm(d.sunrise.at(ti))}.", "🌅", 8, Cat.SUN)
            }
        }

        out.sortByDescending { it.priority }
        return out
    }

    /** The most important [n] sentences, at most one per topic. */
    fun pickTop(all: List<Msg>, n: Int): List<Msg> {
        val res = ArrayList<Msg>()
        val cats = HashSet<Cat>()
        for (m in all) {
            if (m.cat in cats) continue
            var pick = m
            if (m.alt != null && res.any { it.text.contains("nëse del tani", ignoreCase = true) }) {
                pick = m.withText(m.alt)
            }
            res += pick
            cats += m.cat
            if (res.size == n) break
        }
        return res
    }

    private fun dur(mins: Int): String = when {
        mins < 45 -> "gjysmë ore"
        mins < 90 -> "një ore"
        else -> when (val hrs = (mins + 30) / 60) {
            2 -> "dy orësh"
            3 -> "tri orësh"
            4 -> "katër orësh"
            5 -> "pesë orësh"
            6 -> "gjashtë orësh"
            7 -> "shtatë orësh"
            8 -> "tetë orësh"
            else -> "$hrs orësh"
        }
    }
}
