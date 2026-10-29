package app.moti.kosova

import kotlin.math.roundToInt

/** WMO weather codes to Albanian words and emoji. */
object Wx {

    fun describe(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Qiell i kthjellët" else "Natë e kthjellët"
        1 -> "Kryesisht i kthjellët"
        2 -> "Pjesërisht me re"
        3 -> "Mot i vranët"
        45 -> "Mjegull"
        48 -> "Mjegull me brymë"
        51, 53 -> "Shi i imët"
        55 -> "Shi i imët i dendur"
        56, 57 -> "Shi i imët që ngrin"
        61 -> "Shi i lehtë"
        63 -> "Shi"
        65 -> "Shi i fortë"
        66, 67 -> "Shi që ngrin"
        71 -> "Borë e lehtë"
        73 -> "Borë"
        75 -> "Borë e dendur"
        77 -> "Kokrriza bore"
        80 -> "Rrebesh i lehtë"
        81 -> "Rrebesh shiu"
        82 -> "Rrebesh i fortë"
        85, 86 -> "Rrebesh bore"
        95 -> "Stuhi me bubullima"
        96, 99 -> "Stuhi me breshër"
        else -> ""
    }

    fun emoji(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** Rounded whole degrees, e.g. "12" or "-3". */
    fun deg(v: Double): String = if (v.isNaN()) "--" else v.roundToInt().toString()
}
