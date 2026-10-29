package app.moti.kosova

import android.content.Context
import java.util.concurrent.Executors

object Refresher {

    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    /** The app's city plus every city shown on a widget. */
    fun citiesInUse(ctx: Context): List<City> {
        val map = LinkedHashMap<String, City>()
        val def = Prefs.defaultCity(ctx)
        map[def.id] = def
        for (id in WidgetUpdater.widgetIds(ctx)) {
            val c = Prefs.cityForWidget(ctx, id)
            map[c.id] = c
        }
        return map.values.toList()
    }

    /** Downloads every city whose data is older than [maxAgeMs], then redraws widgets. False if a download failed. */
    fun refreshAll(ctx: Context, maxAgeMs: Long): Boolean = synchronized(lock) {
        var ok = true
        for (city in citiesInUse(ctx)) {
            if (Repo.age(ctx, city.id) < maxAgeMs) continue
            try {
                Repo.fetch(ctx, city)
            } catch (e: Exception) {
                ok = false
            }
        }
        Prefs.setLastFailed(ctx, !ok)
        WidgetUpdater.updateAll(ctx)
        ok
    }

    fun refreshAsync(ctx: Context, maxAgeMs: Long, done: ((Boolean) -> Unit)? = null) {
        val app = ctx.applicationContext
        executor.execute {
            val ok = try {
                refreshAll(app, maxAgeMs)
            } catch (e: Exception) {
                false
            }
            done?.invoke(ok)
        }
    }
}
