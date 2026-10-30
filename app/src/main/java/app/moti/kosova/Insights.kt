package app.moti.kosova

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Cat {
    PRECIP, UMBRELLA, PLAN, GEAR, CLOTHES, MORNING, TOMORROW, AIR, HOME, OUTDOOR, CAR,
    HUMID, WIND, FOG, ICE, SUNHEAT, TREND, NICE, SUN
}

/** One plain-Albanian sentence. [short] is a tighter version for the widget. */
class Msg(
    val text: String,
    val emoji: String,
    val priority: Int,
    val cat: Cat,
    val short: String? = null,
)

/**
 * Turns the forecast into practical advice for the moment you leave the house:
 * what to wear, umbrella or hood, dry windows, the car, air, best time for a walk…
 */
object Insights {

    private const val HOUR = 3600L

    private enum class P { RAIN, SNOW, SLEET, STORM, FREEZE }

    /** Shared helpers for one forecast at one moment. */
    private class Ctx(val w: Weather, nowMs: Long) {
        val f = w.forecast
        val h = f.hourly
        val d = f.daily
        val c = effectiveCurrent(w, nowMs)
        val now = nowMs / 1000
        val off = f.utcOffset.toLong()
        val airOk = w.air != null && nowMs - w.airFetchedAt < 12 * 3600_000L

        fun day(t: Long): Long = Math.floorDiv(t + off, 86400L)
        fun hour(t: Long): Int = (Math.floorMod(t + off, 86400L) / 3600L).toInt()
        fun hm(t: Long): String {
            val s = Math.floorMod(t + off, 86400L)
            return String.format(Locale.ROOT, "%02d:%02d", s / 3600, (s % 3600) / 60)
        }
        fun hh(t: Long): String = String.format(Locale.ROOT, "%02d:00", hour(t + 1800))

        val today = day(now)
        val nowHour = hour(now)
        val ci = hourIndex(h, now)
        val hasH = ci >= 0 && ci + 1 < h.size
        val di: Int = (0 until d.size).firstOrNull { day(d.time[it]) == today } ?: -1

        /** Hourly index for a local hour, [dayOffset] days from today. */
        fun idx(dayOffset: Int, hr: Int): Int {
            for (i in 0 until h.size) if (day(h.time[i]) == today + dayOffset && hour(h.time[i]) == hr) return i
            return -1
        }

        fun aqi(t: Long): Double = if (airOk) w.air!!.aqiAt(t) else Double.NaN

        /** Slot i = the hour that ENDS at h.time[i]. */
        fun wet(i: Int): Boolean {
            val p = h.precip.at(i)
            if (p.isNaN()) return false
            val pop = h.pop.at(i)
            val code = h.code.at(i)
            if (code in 95..99 && p >= 0.1) return true
            if (p >= 0.3) return true
            return p >= 0.1 && (pop.isNaN() || pop >= 50)
        }

        /** First slot after the wet run starting at [i] (single dry hours inside rain are bridged). */
        fun runEnd(i: Int): Int {
            var j = i
            while (j < h.size && (wet(j) || (j + 1 < h.size && wet(j + 1)))) j++
            return j
        }
    }

    // ------------------------------------------------------------------ shared helpers

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

    private fun type(code: Int, precip: Double, snowfall: Double): P {
        if (code in 95..99) return P.STORM
        if (code == 56 || code == 57 || code == 66 || code == 67) return P.FREEZE
        val p = precip.or0()
        val snowWater = snowfall.or0() * 10.0 / 7.0 // 7 cm snow ≈ 10 mm water
        val snowCode = code in 71..77 || code == 85 || code == 86
        if (snowCode || (p > 0 && snowWater >= p * 0.7)) {
            return if (p > 0 && snowWater < p * 0.4) P.SLEET else P.SNOW
        }
        if (p > 0 && snowWater >= p * 0.25) return P.SLEET
        return P.RAIN
    }

    private fun Ctx.hType(i: Int) = type(h.code.at(i), h.precip.at(i), h.snowfall.at(i))

    private fun verb(t: P) = when (t) {
        P.RAIN -> "bie shi"
        P.SNOW -> "bie borë"
        P.SLEET -> "bie shi me borë"
        P.STORM -> "vjen stuhia"
        P.FREEZE -> "bie shi që ngrin"
    }

    private fun rangeVerb(t: P) = when (t) {
        P.RAIN -> "Bie shi"
        P.SNOW -> "Bie borë"
        P.SLEET -> "Bie shi me borë"
        P.STORM -> "Ka stuhi"
        P.FREEZE -> "Bie shi që ngrin"
    }

    private fun icon(t: P) = when (t) {
        P.RAIN -> "🌧️"
        P.SNOW, P.SLEET -> "🌨️"
        P.STORM -> "⛈️"
        P.FREEZE -> "🧊"
    }

    private fun noun(t: P) = when (t) {
        P.RAIN, P.FREEZE -> "Shiu"
        P.SNOW -> "Bora"
        P.SLEET -> "Reshjet"
        P.STORM -> "Stuhia"
    }

    private class Wear(val text: String, val emoji: String, val priority: Int)

    /** What to wear for a "feels like" temperature. */
    private fun wear(fl: Double): Wear = when {
        fl <= -10 -> Wear("Acar i madh – pallto e trashë, kapelë, shall dhe doreza.", "🧣", 90)
        fl <= -3 -> Wear("Shumë ftohtë – pallto, kapelë dhe doreza.", "🧤", 80)
        fl <= 4 -> Wear("Vishu trashë – pallto dhe shall.", "🧥", 74)
        fl <= 10 -> Wear("Ftohtë – vish një xhaketë të trashë.", "🧥", 60)
        fl <= 16 -> Wear("Freskët – xhaketë e lehtë ose duks.", "🧥", 44)
        fl <= 22 -> Wear("Mjafton një bluzë me mëngë të gjata.", "👕", 30)
        fl <= 27 -> Wear("Ngrohtë – bluzë me mëngë të shkurtra.", "👕", 34)
        fl <= 32 -> Wear("Nxehtë – rroba të lehta dhe ujë me vete.", "🥵", 70)
        else -> Wear("Vapë e madhe – qëndro në hije, pi shumë ujë.", "🥵", 88)
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
            else -> "$hrs orësh"
        }
    }

    private fun mm(v: Double) = Math.round(v).toString()

    private fun lowerFirst(s: String) = s.replaceFirstChar { it.lowercase(Locale.ROOT) }

    /** Score of an hour for being outside (lower = nicer). Slot of [i] = hour starting at h.time[i]. */
    private fun Ctx.outdoorScore(i: Int): Double {
        val fl = h.feels.at(i).ifNaN(h.temp.at(i))
        if (fl.isNaN()) return 1000.0
        var s = abs(fl - 21) * 1.5
        if (wet(i + 1)) s += 100
        s += max(0.0, h.gusts.at(i).or0() - 25) * 0.6
        val a = aqi(h.time[i])
        if (!a.isNaN()) s += max(0.0, a - 40) * 0.4
        if (h.isDay.at(i) == 0) s += 30
        return s
    }

    /** Best two-hour window from slot [from] that ends before [limit]; returns its start time. */
    private fun Ctx.bestWindow(from: Int, limit: Long): Long? {
        if (from < 0) return null
        var best = -1
        var bestScore = Double.MAX_VALUE
        var i = from
        while (i + 2 < h.size && h.time[i] + 2 * HOUR <= limit) {
            val s = outdoorScore(i) + outdoorScore(i + 1)
            if (s < bestScore) { bestScore = s; best = i }
            i++
        }
        return if (best < 0 || bestScore > 75) null else h.time[best]
    }

    private fun airMsg(v: Double, tomorrow: Boolean, night: Boolean): Msg? {
        if (v.isNaN()) return null
        return if (tomorrow) when {
            v > 80 -> Msg("Ajri do të jetë shumë i ndotur – shmang vrapimin jashtë.", "😷", 85, Cat.AIR, "Ajri do të jetë shumë i ndotur.")
            v > 60 -> Msg("Ajri do të jetë i ndotur.", "😷", 74, Cat.AIR)
            v > 40 -> Msg("Ajri nuk do të jetë shumë i pastër.", "😐", 44, Cat.AIR)
            else -> Msg("Ajri do të jetë i pastër.", "🍃", 20, Cat.AIR)
        } else when {
            v > 100 -> Msg("Ajri është jashtëzakonisht i ndotur! Qëndro brenda.", "😷", 93, Cat.AIR, "Ajri është tepër i ndotur!")
            v > 80 -> Msg("Ajri është shumë i ndotur – mbaj dritaret mbyllur.", "😷", 87, Cat.AIR, "Ajri është shumë i ndotur.")
            v > 60 -> Msg("Ajri është i ndotur – shmang vrapimin jashtë.", "😷", 77, Cat.AIR, "Ajri është i ndotur.")
            v > 40 -> Msg(if (night) "Sonte ajri nuk është shumë i pastër." else "Sot ajri nuk është shumë i pastër.", "😐", 46, Cat.AIR)
            v > 20 -> Msg("Cilësia e ajrit është e mirë.", "🍃", 16, Cat.AIR)
            else -> Msg("Ajri është i pastër.", "🍃", 20, Cat.AIR)
        }
    }

    // ------------------------------------------------------------------ TODAY

    fun build(w: Weather, nowMs: Long): List<Msg> {
        val x = Ctx(w, nowMs)
        val c = x.c
        val h = x.h
        val d = x.d
        val now = x.now
        val out = ArrayList<Msg>()

        val cp = c.precip.or0()
        val precipCodeNow = c.code in 61..67 || c.code in 71..77 || c.code in 80..86 || c.code in 95..99
        val strongCodeNow = c.code in 63..67 || c.code in 73..75 || c.code == 81 || c.code == 82 ||
            c.code == 86 || c.code in 95..99
        val rainingNow = cp >= 0.05 || (precipCodeNow && cp > 0.0) || strongCodeNow
        var wetSoonMins = Int.MAX_VALUE
        var gustSoon = c.gusts.or0()
        if (x.hasH) for (k in 1..3) gustSoon = max(gustSoon, h.gusts.at(x.ci + k).or0())
        val windy = gustSoon >= 45
        val sunset = if (x.di >= 0) d.sunset.at(x.di) else 0L

        // ---------------- rain, snow, storms ----------------
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
            if (x.hasH) {
                val start = x.ci + 1
                val end = if (x.wet(start)) x.runEnd(start) else start
                if (end - start <= 9) {
                    val stopAt = h.time.at(end) - HOUR
                    val mins = max(10L, (stopAt - now) / 60).toInt()
                    val v = when (t) {
                        P.STORM -> "qetësohet"
                        P.SLEET -> "pushojnë"
                        else -> "pushon"
                    }
                    out += if (mins < 90) Msg("${noun(t)} $v pas rreth ${dur(mins)}.", "⏳", 74, Cat.TREND)
                    else Msg("${noun(t)} $v rreth orës ${x.hh(stopAt)}.", "⏳", 74, Cat.TREND)
                } else {
                    out += Msg("${noun(t)} ${if (t == P.SLEET) "vazhdojnë" else "vazhdon"} edhe shumë orë.", "", 58, Cat.TREND)
                }
                var sum = 0.0
                var snow = 0.0
                for (i in start until max(end, start + 1)) { sum += h.precip.at(i).or0(); snow += h.snowfall.at(i).or0() }
                if ((t == P.SNOW || t == P.SLEET) && snow >= 2) {
                    out += Msg("Pritet borë rreth ${mm(snow)} cm – vish këpucë dimri.", "🥾", 76, Cat.GEAR)
                } else if (sum >= 8) {
                    out += Msg("Shi i fortë, rreth ${mm(sum)} mm – merr këpucë që s'lagen.", "🥾", 70, Cat.GEAR, "Shi i fortë – këpucë që s'lagen.")
                }
            }
            when (t) {
                P.RAIN, P.STORM -> out += if (windy) Msg("Erë me shi – më mirë kapuç se çadër.", "🧥", 84, Cat.UMBRELLA)
                else Msg("Merr çadër nëse del tani.", "☔", 84, Cat.UMBRELLA)
                else -> out += Msg("Rrugët mund të jenë të rrëshqitshme.", "⚠️", 78, Cat.ICE)
            }
        } else if (x.hasH) {
            val maxK = min(h.size - 1 - x.ci, 30)
            var firstK = -1
            for (k in 1..maxK) if (x.wet(x.ci + k)) { firstK = k; break }
            var said = false
            if (firstK > 0) {
                val i = x.ci + firstK
                val t = x.hType(i)
                val start = max(now, h.time[i] - HOUR)
                val mins = ((start - now) / 60).toInt()
                val sDay = x.day(start)
                val sHour = x.hour(start)
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
                    sDay == x.today && sHour >= 18 -> { txt = "Sonte $v."; pr = 76 }
                    sDay == x.today -> { txt = "Më vonë $v."; pr = 78 }
                    sDay == x.today + 1 && sHour < 6 -> { txt = "Natën $v."; pr = 68 }
                }
                if (t != P.RAIN) pr += 4
                if (txt != null) {
                    out += Msg(txt, icon(t), pr, Cat.PRECIP)
                    said = true
                    wetSoonMins = mins
                    val end = x.runEnd(i)
                    val endT = h.time.at(end - 1)
                    if (end - i <= 12 && endT > start) {
                        out += Msg("${rangeVerb(t)} nga ora ${x.hh(start)} deri në ${x.hh(endT)}.", "🕒", 60, Cat.TREND)
                    }
                    if (mins >= 90 && sDay == x.today) {
                        out += Msg(
                            "E thatë deri rreth orës ${x.hh(start)} – kryej punët jashtë tani.", "✅",
                            if (mins <= 330) 72 else 46, Cat.PLAN, "Thatë deri në orën ${x.hh(start)}.",
                        )
                    }
                    var sum = 0.0
                    var snow = 0.0
                    for (j in i until end) { sum += h.precip.at(j).or0(); snow += h.snowfall.at(j).or0() }
                    if (t == P.RAIN || t == P.STORM) {
                        when {
                            mins < 90 -> out += if (windy) Msg("Erë me shi – më mirë kapuç se çadër.", "🧥", 84, Cat.UMBRELLA)
                            else Msg("Merr çadër nëse del tani.", "☔", 84, Cat.UMBRELLA)
                            mins < 330 -> out += Msg("Nëse rri gjatë jashtë, merr çadër.", "☔", 76, Cat.UMBRELLA)
                            sDay == x.today -> out += Msg("Merr çadër me vete sot.", "☔", 54, Cat.UMBRELLA)
                        }
                        if (sum >= 8) out += Msg("Shi i fortë, rreth ${mm(sum)} mm – merr këpucë që s'lagen.", "🥾", 66, Cat.GEAR, "Shi i fortë – këpucë që s'lagen.")
                    } else {
                        if (snow >= 2) out += Msg("Pritet borë rreth ${mm(snow)} cm – vish këpucë dimri.", "🥾", 70, Cat.GEAR)
                        if (mins < 360) out += Msg("Rrugët mund të bëhen të rrëshqitshme.", "⚠️", 62, Cat.ICE)
                    }
                }
            }
            if (!said) {
                var maxPop = 0.0
                var snowy = false
                for (k in 1..min(12, maxK)) {
                    val p = h.pop.at(x.ci + k)
                    if (!p.isNaN() && p > maxPop) { maxPop = p; snowy = x.hType(x.ci + k) == P.SNOW }
                }
                out += when {
                    maxPop >= 50 && snowy -> Msg("Ka mundësi për borë më vonë.", "🌨️", 50, Cat.PRECIP)
                    maxPop >= 50 -> Msg("Ka mundësi për shi më vonë – çadra e vogël në çantë.", "🌦️", 50, Cat.PRECIP, "Ka mundësi për shi më vonë.")
                    else -> Msg(if (x.nowHour < 17) "Sot nuk pritet shi." else "Sonte nuk pritet shi.", "👍", 24, Cat.PRECIP)
                }
            }
        }

        // ---------------- what to wear ----------------
        val fl = if (!c.feels.isNaN()) c.feels else c.temp
        if (!fl.isNaN()) {
            val wr = wear(fl)
            out += Msg(wr.text, wr.emoji, wr.priority, Cat.CLOTHES)
            if (x.di >= 0 && x.nowHour in 5..11) {
                val tmax = d.tmax.at(x.di)
                if (!tmax.isNaN() && tmax - fl >= 9) {
                    out += Msg("Vishu me shtresa: tani ${Wx.deg(fl)}°, në mesditë ${Wx.deg(tmax)}°.", "🧥", 76, Cat.CLOTHES)
                }
            }
            if (x.nowHour in 7..15) {
                val i19 = x.idx(0, 19)
                val f19 = h.feels.at(i19)
                if (!f19.isNaN() && fl - f19 >= 5) {
                    out += Msg("Nëse kthehesh pas orës 18, merr xhaketë – ndihet si ${Wx.deg(f19)}°.", "🌆", 63, Cat.TREND,
                        "Në mbrëmje ndihet si ${Wx.deg(f19)}° – merr xhaketë.")
                }
            }
            if (!c.temp.isNaN() && c.temp >= 26 && fl - c.temp >= 3) {
                out += Msg("Vapë mbytëse, ka shumë lagështi.", "💦", 58, Cat.HUMID)
            }
            if (!c.temp.isNaN() && c.temp < 15 && c.temp - fl >= 5 && c.wind.or0() >= 15) {
                out += Msg("Era e bën të ndihet ${Wx.deg(c.temp - fl)}° më ftohtë.", "🌬️", 52, Cat.WIND)
            }
        }

        // ---------------- humidity ----------------
        if (!rainingNow && wetSoonMins > 120 && !c.humidity.isNaN()) {
            if (c.humidity >= 92 && c.temp.or0() < 24) {
                out += Msg("Tani ka shumë lagështi.", "💧", 34, Cat.HUMID)
            } else if (x.hasH && c.humidity < 85) {
                for (k in 1..3) {
                    val i = x.ci + k
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
        val visNow = min(h.visibility.at(x.ci).ifNaN(1e9), h.visibility.at(x.ci + 1).ifNaN(1e9))
        if (c.code == 45 || c.code == 48 || (x.hasH && visNow < 800)) {
            out += Msg("Ka mjegull – vozit ngadalë dhe ndiz dritat.", "🌫️", 73, Cat.FOG, "Mjegull – vozit ngadalë.")
        } else if (x.hasH) {
            for (k in 1..min(12, h.size - 1 - x.ci)) {
                val i = x.ci + k
                val code = h.code.at(i)
                if (code == 45 || code == 48) {
                    val t = h.time[i] - HOUR
                    val txt = when {
                        x.day(t) == x.today && x.hour(t) >= 18 -> "Sonte bie mjegull."
                        x.day(t) == x.today -> "Më vonë bie mjegull."
                        x.hour(t) < 12 -> "Nesër në mëngjes ka mjegull – nisu më herët."
                        else -> null
                    }
                    if (txt != null) out += Msg(txt, "🌫️", 48, Cat.FOG)
                    break
                }
            }
        }

        // ---------------- frost, ice, car ----------------
        if (x.nowHour in 3..10 && !c.temp.isNaN() && c.temp <= 1.5 && c.humidity.or0() >= 70 && !rainingNow) {
            out += Msg("Mund të ketë brymë në xhamat e makinës – nisu pak më herët.", "🚗", 68, Cat.CAR, "Brymë në makinë – nisu më herët.")
        }
        if (x.hasH) {
            var minT = Double.NaN
            var k = 1
            while (x.ci + k < h.size && k <= 20) {
                val i = x.ci + k
                val t = h.temp.at(i)
                if (!t.isNaN() && (minT.isNaN() || t < minT)) minT = t
                if (x.day(h.time[i]) > x.today && x.hour(h.time[i]) >= 9) break
                k++
            }
            if (x.nowHour in 14..23 && c.temp.or0() > 1 && !minT.isNaN() && minT <= -0.5) {
                out += Msg("Sonte ngrin – nesër në mëngjes kujdes nga akulli.", "🧊", 60, Cat.ICE, "Sonte ngrin, kujdes nga akulli.")
            }
            if (x.nowHour in 15..21 && !minT.isNaN() && !c.temp.isNaN() && c.temp - minT >= 7) {
                out += Msg("Sonte ftohet, deri në ${Wx.deg(minT)}°.", "🌙", 40, Cat.TREND)
            }
        }
        if (x.nowHour in 0..10 && !c.temp.isNaN() && c.temp <= 0.5) {
            var recent = 0.0
            if (x.ci >= 0) for (i in max(0, x.ci - 5)..x.ci) recent += h.precip.at(i).or0()
            if (recent >= 0.5 || c.humidity.or0() >= 90) out += Msg("Mund të ketë akull në rrugë – ec me kujdes.", "🧊", 72, Cat.ICE)
        }

        // ---------------- wind ----------------
        val g = c.gusts
        if (!g.isNaN() && g >= 75) {
            out += Msg("Erë shumë e fortë, deri në ${mm(g)} km/h! Kujdes jashtë.", "💨", 88, Cat.WIND, "Erë shumë e fortë!")
        } else if (!g.isNaN() && g >= 55) {
            out += Msg("Fryn erë e fortë, deri në ${mm(g)} km/h.", "💨", 66, Cat.WIND, "Fryn erë e fortë.")
        } else if (x.hasH) {
            var mg = 0.0
            for (k in 1..min(6, h.size - 1 - x.ci)) mg = max(mg, h.gusts.at(x.ci + k).or0())
            if (mg >= 60) out += Msg("Më vonë fryn erë e fortë, deri në ${mm(mg)} km/h.", "💨", 55, Cat.WIND, "Më vonë fryn erë e fortë.")
        }

        // ---------------- sun & heat ----------------
        if (x.di >= 0 && x.nowHour < 17 && d.tmax.at(x.di) >= 30 && x.hasH) {
            var a = -1
            var b = -1
            for (i in x.ci until min(h.size, x.ci + 14)) {
                if (x.day(h.time[i]) != x.today) break
                if (h.temp.at(i) >= 30) { if (a < 0) a = i; b = i }
            }
            if (a >= 0 && b > a) {
                out += Msg("Shmang diellin nga ora ${x.hh(h.time[a])} deri në ${x.hh(h.time[b])}.", "⛱️", 58, Cat.SUNHEAT)
            }
        }
        if (x.di >= 0 && c.isDay && x.nowHour < 16) {
            val uv = d.uvMax.at(x.di)
            when {
                uv >= 8 -> out += Msg("Dielli është shumë i fortë – vendos krem mbrojtës.", "🧴", 60, Cat.SUNHEAT, "Dielli është shumë i fortë.")
                uv >= 6 -> out += Msg("Dielli është i fortë sot – vendos krem mbrojtës.", "🧴", 46, Cat.SUNHEAT, "Dielli është i fortë sot.")
                uv >= 3 && c.code <= 1 && x.nowHour >= 7 -> out += Msg("Merr syzet e diellit.", "🕶️", 30, Cat.SUNHEAT)
            }
        }
        if (x.di >= 0 && x.nowHour in 5..12) {
            val tmax = d.tmax.at(x.di)
            if (!tmax.isNaN() && !c.temp.isNaN() && tmax - c.temp >= 6) {
                out += Msg("Më vonë ngrohet, deri në ${Wx.deg(tmax)}°.", "🌤️", 38, Cat.TREND)
            }
        }

        // ---------------- air & home ----------------
        val night = x.nowHour >= 18 || x.nowHour < 6
        airMsg(x.aqi(now), false, night)?.let { out += it }
        if (x.airOk && x.hasH) {
            val cur = x.aqi(now)
            var bestT = 0L
            var bestV = Double.MAX_VALUE
            for (k in 1..min(12, h.size - 1 - x.ci)) {
                val t = h.time[x.ci + k]
                val v = x.aqi(t)
                if (!v.isNaN() && v < bestV) { bestV = v; bestT = t }
            }
            if (!cur.isNaN() && cur >= 40 && bestV <= cur - 12) {
                out += Msg("Ajrose shtëpinë rreth orës ${x.hh(bestT)} – atëherë ajri është më i pastër.", "🪟", 42, Cat.HOME,
                    "Ajrose shtëpinë rreth orës ${x.hh(bestT)}.")
            } else if (!cur.isNaN() && cur <= 25 && !rainingNow && c.temp.or0() >= 5 && c.isDay) {
                out += Msg("Ajri është i pastër – hap dritaret pak.", "🪟", 19, Cat.HOME)
            }
        }
        if (x.nowHour in 7..13 && !rainingNow && c.code <= 2 && c.temp.or0() >= 10 && sunset > now &&
            wetSoonMins.toLong() * 60 > sunset - now && x.hasH
        ) {
            var rh = 0.0
            for (k in 1..4) rh += h.humidity.at(x.ci + k).ifNaN(80.0)
            if (rh / 4 < 70) out += Msg("Ditë e mirë për t'i tharë rrobat jashtë.", "👚", 27, Cat.HOME)
        }

        // ---------------- best time outside ----------------
        if (x.hasH && x.nowHour < 16 && sunset > now) {
            x.bestWindow(x.ci + 1, sunset)?.let { s ->
                out += if (s - now <= HOUR) Msg("Tani është koha më e mirë për një shëtitje.", "🚶", 32, Cat.OUTDOOR)
                else Msg("Koha më e mirë për shëtitje: ${x.hh(s)}–${x.hh(s + 2 * HOUR)}.", "🚶", 32, Cat.OUTDOOR)
            }
        }

        // ---------------- car wash ----------------
        if (x.di >= 0 && x.di + 2 < d.size && !rainingNow) {
            val p1 = d.precipSum.at(x.di + 1).or0()
            val p2 = d.precipSum.at(x.di + 2).or0()
            val dryToday = wetSoonMins == Int.MAX_VALUE || wetSoonMins > (24 - x.nowHour) * 60
            if (dryToday && p1 < 0.5 && p2 < 0.5 && x.nowHour < 17) {
                out += Msg("Ditë e mirë për ta larë makinën – s'bie shi deri pasnesër.", "🚗", 20, Cat.CAR, "Ditë e mirë për ta larë makinën.")
            } else if (dryToday && p1 >= 2) {
                out += Msg("Mos e laj makinën sot – nesër bie shi.", "🚗", 25, Cat.CAR)
            }
        }

        // ---------------- tomorrow in one line ----------------
        val ti = x.di + 1
        if (x.di >= 0 && ti < d.size) {
            val tmaxT = d.tmax.at(ti)
            val tmax0 = d.tmax.at(x.di)
            val snowT = d.snowSum.at(ti).or0() >= 1.0
            val wetT = (d.popMax.at(ti).or0() >= 55 && d.precipSum.at(ti).or0() >= 1.0) || d.precipSum.at(ti).or0() >= 3.0
            val stormT = d.code.at(ti) in 95..99
            var cold = 0
            if (!tmaxT.isNaN()) {
                val diff = if (tmax0.isNaN()) 0.0 else tmaxT - tmax0
                when {
                    tmaxT <= 4 || d.feelsMin.at(ti) <= -7 -> { cold = 3; out += Msg("Nesër do të jetë shumë ftohtë.", "🧥", 67, Cat.TOMORROW) }
                    diff <= -6 -> { cold = 2; out += Msg("Nesër bëhet shumë më ftohtë.", "🧥", 63, Cat.TOMORROW) }
                    diff <= -3.5 -> { cold = 1; out += Msg("Nesër do të jetë më ftohtë.", "🧥", 48, Cat.TOMORROW) }
                    tmaxT >= 34 -> out += Msg("Nesër do të jetë shumë nxehtë.", "🥵", 62, Cat.TOMORROW)
                    diff >= 6 -> out += Msg("Nesër bëhet shumë më ngrohtë.", "☀️", 52, Cat.TOMORROW)
                    diff >= 3.5 -> out += Msg("Nesër do të jetë më ngrohtë.", "🌤️", 44, Cat.TOMORROW)
                }
            }
            when {
                stormT && (wetT || snowT) -> out += Msg("Nesër ka stuhi.", "⛈️", 64, Cat.TOMORROW)
                snowT && cold > 0 -> out += Msg("Nesër bie borë dhe bëhet ftohtë.", "❄️", 70, Cat.TOMORROW)
                snowT -> out += Msg("Nesër bie borë.", "❄️", 66, Cat.TOMORROW)
                wetT && cold > 0 -> out += Msg("Nesër bie shi dhe bëhet më ftohtë.", "☔", 66, Cat.TOMORROW)
                wetT -> out += Msg("Nesër bie shi.", "☔", 56, Cat.TOMORROW)
            }
            if (!wetT && !snowT && d.code.at(ti) in 0..2 && tmaxT in 15.0..29.0) {
                out += Msg("Nesër pritet mot i bukur.", "☀️", 34, Cat.TOMORROW)
            }
            val tminT = d.tmin.at(ti)
            if (!tmaxT.isNaN() && !tminT.isNaN()) {
                out += Msg("Nesër: nga ${Wx.deg(tminT)}° deri në ${Wx.deg(tmaxT)}°.", "🌡️", 14, Cat.TOMORROW)
            }
        }

        // ---------------- nice weather ----------------
        if (!rainingNow && wetSoonMins > 360 && c.code in 0..1 && !fl.isNaN()) {
            if (c.isDay && fl in 15.0..28.0) out += Msg("Mot i bukur – shijoje ditën!", "😎", 36, Cat.NICE)
            else if (!c.isDay) out += Msg("Natë e kthjellët me yje.", "✨", 18, Cat.NICE)
        }

        // ---------------- daylight ----------------
        if (x.di >= 0) {
            val sr = d.sunrise.at(x.di)
            when {
                sr > 0 && now < sr -> out += Msg("Dielli lind në orën ${x.hm(sr)}.", "🌅", 10, Cat.SUN)
                sunset > now && sunset - now <= 5400 -> out += Msg("Errësohet në orën ${x.hm(sunset)} – merr parasysh dritën.", "🌇", 34, Cat.SUN, "Errësohet në orën ${x.hm(sunset)}.")
                sunset > now -> out += Msg("Dielli perëndon në orën ${x.hm(sunset)}.", "🌇", 10, Cat.SUN)
                ti < d.size && d.sunrise.at(ti) > 0 -> out += Msg("Nesër dielli lind në orën ${x.hm(d.sunrise.at(ti))}.", "🌅", 8, Cat.SUN)
            }
        }

        out.sortByDescending { it.priority }
        return out
    }

    // ------------------------------------------------------------------ TOMORROW

    fun buildTomorrow(w: Weather, nowMs: Long): List<Msg> {
        val x = Ctx(w, nowMs)
        val h = x.h
        val d = x.d
        val out = ArrayList<Msg>()
        val ti = x.di + 1
        if (x.di < 0 || ti >= d.size) return out
        val tom = x.today + 1
        val tmaxT = d.tmax.at(ti)
        val tminT = d.tmin.at(ti)
        val sunsetT = d.sunset.at(ti)

        // ---------------- precipitation, by the hour ----------------
        var i = 0
        while (i < h.size && !(x.day(h.time[i] - HOUR) == tom && x.hour(h.time[i] - HOUR) >= 6)) i++
        val runs = ArrayList<IntArray>()
        while (i < h.size && x.day(h.time[i] - HOUR) == tom) {
            if (x.wet(i)) {
                var end = x.runEnd(i)
                while (end > i && x.day(h.time[end - 1] - HOUR) != tom) end--
                runs += intArrayOf(i, end)
                i = max(end, i + 1)
            } else i++
        }
        if (runs.isNotEmpty()) {
            val r = runs[0]
            val t = x.hType(r[0])
            var hours = 0
            var sum = 0.0
            var snow = 0.0
            var gust = 0.0
            for (run in runs) for (j in run[0] until run[1]) {
                hours++
                sum += h.precip.at(j).or0()
                snow += h.snowfall.at(j).or0()
                gust = max(gust, h.gusts.at(j).or0())
            }
            val start = h.time[r[0]] - HOUR
            val end = h.time.at(r[1] - 1)
            out += if (hours >= 12) Msg("${rangeVerb(t)} gjatë gjithë ditës.", icon(t), 92, Cat.PRECIP)
            else Msg("${rangeVerb(t)} nga ora ${x.hh(start)} deri në ${x.hh(end)}.", icon(t), 92, Cat.PRECIP,
                "${rangeVerb(t)} ${x.hh(start)}–${x.hh(end)}.")
            if (runs.size > 1 && hours < 12) {
                out += Msg("Edhe një herë nga ora ${x.hh(h.time[runs[1][0]] - HOUR)}.", "🔁", 40, Cat.TREND)
            }
            when (t) {
                P.RAIN, P.STORM -> out += if (gust >= 45) Msg("Erë me shi – më mirë xhaketë me kapuç se çadër.", "🧥", 80, Cat.UMBRELLA, "Më mirë kapuç se çadër.")
                else Msg("Merr çadër me vete.", "☔", 80, Cat.UMBRELLA)
                else -> out += Msg("Rrugët mund të jenë të rrëshqitshme.", "⚠️", 78, Cat.ICE)
            }
            if ((t == P.SNOW || t == P.SLEET) && snow >= 2) {
                out += Msg("Pritet borë rreth ${mm(snow)} cm – vish këpucë dimri.", "🥾", 84, Cat.GEAR)
            } else if (sum >= 10) {
                out += Msg("Shi i fortë, rreth ${mm(sum)} mm – merr këpucë që s'lagen.", "🥾", 70, Cat.GEAR, "Shi i fortë – këpucë që s'lagen.")
            }
        } else {
            out += if (d.popMax.at(ti).or0() >= 40) Msg("Ka pak mundësi për shi.", "🌦️", 48, Cat.PRECIP)
            else Msg("Nuk pritet shi.", "👍", 44, Cat.PRECIP)
        }

        // ---------------- morning & clothes ----------------
        val i8 = x.idx(1, 8)
        val t8 = h.temp.at(i8)
        val f8 = h.feels.at(i8).ifNaN(t8)
        if (!t8.isNaN()) {
            val wr = wear(f8)
            out += Msg(
                "Në mëngjes ${Wx.deg(t8)}°, ndihet si ${Wx.deg(f8)}° – ${lowerFirst(wr.text)}", wr.emoji,
                max(72, wr.priority - 2), Cat.MORNING, "Mëngjesi ${Wx.deg(t8)}°: ${lowerFirst(wr.text)}",
            )
            if (!tmaxT.isNaN() && tmaxT - f8 >= 9) {
                out += Msg("Vishu me shtresa: në mëngjes ${Wx.deg(f8)}°, në mesditë ${Wx.deg(tmaxT)}°.", "🧥", 75, Cat.CLOTHES)
            }
        }
        val i20 = x.idx(1, 20)
        val t20 = h.temp.at(i20)
        if (!t20.isNaN() && !tmaxT.isNaN() && tmaxT - t20 >= 6) {
            out += Msg("Mbrëmja ftohet në ${Wx.deg(t20)}°.", "🌙", 40, Cat.TREND)
        }

        // ---------------- frost, fog, wind ----------------
        var minMorning = Double.NaN
        var fogMorning = false
        var gustMax = 0.0
        for (j in 0 until h.size) {
            if (x.day(h.time[j]) != tom) continue
            val hr = x.hour(h.time[j])
            val t = h.temp.at(j)
            if (hr <= 9 && !t.isNaN() && (minMorning.isNaN() || t < minMorning)) minMorning = t
            if (hr in 5..10 && (h.code.at(j) == 45 || h.code.at(j) == 48)) fogMorning = true
            if (hr in 7..22) gustMax = max(gustMax, h.gusts.at(j).or0())
        }
        if (!minMorning.isNaN() && minMorning <= 0) {
            out += Msg("Ngrin në mëngjes – pastro xhamat e makinës para se të nisesh.", "🧊", 76, Cat.ICE, "Ngrin në mëngjes – brymë në makinë.")
        }
        if (fogMorning) out += Msg("Mjegull në mëngjes – nisu më herët dhe vozit ngadalë.", "🌫️", 66, Cat.FOG, "Mjegull në mëngjes – nisu më herët.")
        if (gustMax >= 55) out += Msg("Fryn erë e fortë, deri në ${mm(gustMax)} km/h.", "💨", 64, Cat.WIND, "Fryn erë e fortë.")

        // ---------------- sun ----------------
        val uv = d.uvMax.at(ti)
        if (uv >= 6) out += Msg("Dielli i fortë – vendos krem mbrojtës.", "🧴", 46, Cat.SUNHEAT)
        else if (uv >= 3 && d.code.at(ti) <= 1) out += Msg("Merr syzet e diellit.", "🕶️", 28, Cat.SUNHEAT)

        // ---------------- air ----------------
        if (x.airOk) {
            var s = 0.0
            var n = 0
            for (j in 0 until h.size) {
                if (x.day(h.time[j]) != tom || x.hour(h.time[j]) !in 8..20) continue
                val v = x.aqi(h.time[j])
                if (!v.isNaN()) { s += v; n++ }
            }
            if (n >= 4) airMsg(s / n, true, false)?.let { out += it }
        }

        // ---------------- compared with today ----------------
        val tmax0 = d.tmax.at(x.di)
        if (!tmaxT.isNaN() && !tmax0.isNaN()) {
            val diff = tmaxT - tmax0
            if (diff <= -3.5) out += Msg("Më ftohtë se sot – ${Wx.deg(-diff)}° më pak.", "📉", 58, Cat.TREND)
            else if (diff >= 3.5) out += Msg("Më ngrohtë se sot – ${Wx.deg(diff)}° më shumë.", "📈", 44, Cat.TREND)
        }

        // ---------------- plans ----------------
        if (sunsetT > 0) {
            x.bestWindow(x.idx(1, 8), sunsetT)?.let { s ->
                out += Msg("Koha më e mirë për shëtitje: ${x.hh(s)}–${x.hh(s + 2 * HOUR)}.", "🚶", 36, Cat.OUTDOOR)
            }
        }
        val dryT = runs.isEmpty() && d.precipSum.at(ti).or0() < 0.5
        if (dryT && ti + 1 < d.size && d.precipSum.at(ti + 1).or0() < 0.5) {
            out += Msg("Ditë e mirë për ta larë makinën.", "🚗", 18, Cat.CAR)
        }
        if (dryT && d.code.at(ti) in 0..2 && tmaxT >= 12) {
            out += Msg("Ditë e mirë për t'i tharë rrobat jashtë.", "👚", 24, Cat.HOME)
        }
        if (dryT && d.code.at(ti) in 0..1 && tmaxT in 15.0..29.0) {
            out += Msg("Pritet ditë e bukur – planifiko diçka jashtë.", "😎", 38, Cat.NICE)
        }

        val sr = d.sunrise.at(ti)
        if (sr > 0 && sunsetT > 0) {
            out += Msg("Dielli lind në ${x.hm(sr)} dhe perëndon në ${x.hm(sunsetT)}.", "🌅", 10, Cat.SUN)
        }
        if (!tmaxT.isNaN() && !tminT.isNaN()) {
            out += Msg("Nga ${Wx.deg(tminT)}° deri në ${Wx.deg(tmaxT)}°.", "🌡️", 12, Cat.MORNING)
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
            res += m
            cats += m.cat
            if (res.size == n) break
        }
        return res
    }
}
