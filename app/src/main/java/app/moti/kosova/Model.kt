package app.moti.kosova

import kotlin.math.abs

/** Times are Unix seconds (UTC). Missing numbers are NaN, missing codes are -1. */
class Current(
    val time: Long,
    val temp: Double,
    val feels: Double,
    val humidity: Double,
    val isDay: Boolean,
    val precip: Double,
    val snowfall: Double,
    val code: Int,
    val wind: Double,
    val gusts: Double,
)

/** Open-Meteo hourly data: precipitation, its probability and the weather code describe the PRECEDING hour. */
class Hourly(
    val time: LongArray,
    val temp: DoubleArray,
    val feels: DoubleArray,
    val humidity: DoubleArray,
    val pop: DoubleArray,
    val precip: DoubleArray,
    val snowfall: DoubleArray,
    val code: IntArray,
    val gusts: DoubleArray,
    val visibility: DoubleArray,
    val isDay: IntArray,
) {
    val size: Int get() = time.size
}

class Daily(
    val time: LongArray,
    val code: IntArray,
    val tmax: DoubleArray,
    val tmin: DoubleArray,
    val feelsMin: DoubleArray,
    val precipSum: DoubleArray,
    val snowSum: DoubleArray,
    val popMax: DoubleArray,
    val uvMax: DoubleArray,
    val sunrise: LongArray,
    val sunset: LongArray,
) {
    val size: Int get() = time.size
}

class Forecast(val utcOffset: Int, val current: Current, val hourly: Hourly, val daily: Daily)

class Air(val currentTime: Long, val currentAqi: Double, val time: LongArray, val aqi: DoubleArray) {
    /** European AQI at [now] (Unix seconds). */
    fun aqiAt(now: Long): Double {
        if (!currentAqi.isNaN() && abs(now - currentTime) < 5400) return currentAqi
        var best = Double.NaN
        for (i in time.indices) {
            if (time[i] <= now) best = aqi.at(i) else break
        }
        return if (best.isNaN()) currentAqi else best
    }
}

class Weather(val fetchedAt: Long, val forecast: Forecast, val air: Air?, val airFetchedAt: Long)

fun DoubleArray.at(i: Int): Double = if (i in indices) this[i] else Double.NaN
fun IntArray.at(i: Int): Int = if (i in indices) this[i] else -1
fun LongArray.at(i: Int): Long = if (i in indices) this[i] else 0L
fun Double.or0(): Double = if (isNaN()) 0.0 else this
fun Double.ifNaN(other: Double): Double = if (isNaN()) other else this
