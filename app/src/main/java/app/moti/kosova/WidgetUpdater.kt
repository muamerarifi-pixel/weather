package app.moti.kosova

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

/** Draws the widget. On Android 12+ the launcher picks the layout that fits the widget's size. */
object WidgetUpdater {

    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))

    fun updateAll(ctx: Context, refreshing: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in widgetIds(ctx)) update(ctx, mgr, id, refreshing)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, refreshing: Boolean = false) {
        val city = Prefs.cityForWidget(ctx, id)
        val m = Present.model(ctx, city, Repo.load(ctx, city.id), System.currentTimeMillis(), refreshing)
        val alpha = Prefs.glassAlpha(ctx)
        val views: RemoteViews = if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews(
                mapOf(
                    SizeF(60f, 40f) to small(ctx, m, alpha),
                    SizeF(130f, 100f) to medium(ctx, m, alpha, false),
                    SizeF(130f, 150f) to medium(ctx, m, alpha, true),
                    SizeF(240f, 150f) to large(ctx, id, m, alpha, false),
                    SizeF(240f, 215f) to large(ctx, id, m, alpha, true),
                )
            )
        } else {
            val o = mgr.getAppWidgetOptions(id)
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            when {
                w == 0 || h == 0 -> large(ctx, id, m, alpha, true)
                w >= 240 && h >= 215 -> large(ctx, id, m, alpha, true)
                w >= 240 && h >= 150 -> large(ctx, id, m, alpha, false)
                w >= 130 && h >= 150 -> medium(ctx, m, alpha, true)
                w >= 130 && h >= 100 -> medium(ctx, m, alpha, false)
                else -> small(ctx, m, alpha)
            }
        }
        mgr.updateAppWidget(id, views)
    }

    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, FLAGS)
    }

    private fun pickCity(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, CityActivity::class.java)
            .setData(Uri.parse("moti://city/$id"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(ctx, 1000 + id, i, FLAGS)
    }

    private fun refresh(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, WeatherWidget::class.java)
            .setAction(WeatherWidget.ACTION_REFRESH)
            .setData(Uri.parse("moti://refresh/$id"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return PendingIntent.getBroadcast(ctx, 2000 + id, i, FLAGS)
    }

    private fun base(ctx: Context, layout: Int, alpha: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, layout)
        rv.setInt(R.id.glass, "setImageAlpha", alpha)
        rv.setOnClickPendingIntent(android.R.id.background, openApp(ctx))
        return rv
    }

    private fun small(ctx: Context, m: Present.Model, alpha: Int): RemoteViews {
        val rv = base(ctx, R.layout.widget_small, alpha)
        rv.setTextViewText(R.id.tv_icon, m.icon)
        rv.setTextViewText(R.id.tv_temp, if (m.hasData) "${m.temp}°" else m.temp)
        rv.setTextViewText(R.id.tv_headline, m.headline)
        return rv
    }

    private fun medium(ctx: Context, m: Present.Model, alpha: Int, showLine2: Boolean): RemoteViews {
        val rv = base(ctx, R.layout.widget_medium, alpha)
        rv.setTextViewText(R.id.tv_city, m.city)
        rv.setTextViewText(R.id.tv_icon, m.icon)
        rv.setTextViewText(R.id.tv_temp, if (m.hasData) "${m.temp}°" else m.temp)
        rv.setTextViewText(R.id.tv_headline, m.headline)
        setLine(rv, R.id.tv_line2, if (showLine2) m.line2 else null)
        return rv
    }

    private fun large(ctx: Context, id: Int, m: Present.Model, alpha: Int, showLine3: Boolean): RemoteViews {
        val rv = base(ctx, R.layout.widget_large, alpha)
        rv.setTextViewText(R.id.tv_city, m.cityLong)
        setLine(rv, R.id.tv_cond, m.condition)
        rv.setTextViewText(R.id.tv_temp, m.temp)
        rv.setTextViewText(R.id.tv_unit, if (m.hasData) "°C" else "")
        rv.setTextViewText(R.id.tv_icon, m.icon)
        rv.setTextViewText(R.id.tv_headline, m.headline)
        setLine(rv, R.id.tv_line2, m.line2)
        setLine(rv, R.id.tv_line3, if (showLine3) m.line3 else null)
        rv.setTextViewText(R.id.tv_updated, m.updated)
        rv.setOnClickPendingIntent(R.id.btn_city, pickCity(ctx, id))
        rv.setOnClickPendingIntent(R.id.tv_updated, refresh(ctx, id))
        return rv
    }

    private fun setLine(rv: RemoteViews, viewId: Int, text: String?) {
        if (text.isNullOrEmpty()) {
            rv.setViewVisibility(viewId, View.GONE)
        } else {
            rv.setViewVisibility(viewId, View.VISIBLE)
            rv.setTextViewText(viewId, text)
        }
    }
}
