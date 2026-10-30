package app.moti.kosova

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class WeatherWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "app.moti.kosova.REFRESH"
        const val ACTION_TAB = "app.moti.kosova.TAB"
        const val EXTRA_TOMORROW = "tomorrow"
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) WidgetUpdater.update(ctx, mgr, id)
        Scheduler.ensurePeriodic(ctx)
        val maxAge = Prefs.interval(ctx) * 60_000L
        if (Refresher.citiesInUse(ctx).any { Repo.age(ctx, it.id) > maxAge }) {
            val pending = goAsync()
            Refresher.refreshAsync(ctx, 2 * 60_000L) { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        WidgetUpdater.update(ctx, mgr, id)
    }

    override fun onEnabled(ctx: Context) {
        Scheduler.ensurePeriodic(ctx)
    }

    override fun onDisabled(ctx: Context) {
        Scheduler.cancelAll(ctx)
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        Prefs.removeWidgets(ctx, ids)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            WidgetUpdater.updateAll(ctx, refreshing = true)
            val pending = goAsync()
            Refresher.refreshAsync(ctx, 15_000L) { pending.finish() }
            return
        }
        if (intent.action == ACTION_TAB) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Prefs.setTab(ctx, "w$id", intent.getBooleanExtra(EXTRA_TOMORROW, false))
                WidgetUpdater.update(ctx, AppWidgetManager.getInstance(ctx), id)
            }
            return
        }
        super.onReceive(ctx, intent)
    }
}
