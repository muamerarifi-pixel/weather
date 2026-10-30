package app.moti.kosova

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.moti.kosova.Fonts.Role

/** Draws the widget. On Android 12+ the launcher picks the layout that fits the widget's size. */
object WidgetUpdater {

    /** The user's look settings. */
    class Look(ctx: Context) {
        val scale = Prefs.textScale(ctx) / 100f
        val font = Prefs.font(ctx)
        val bg = Prefs.background(ctx)
        val corner = Prefs.corner(ctx)
        val alpha = Prefs.glassAlpha(ctx)
        val icon = Prefs.showIcon(ctx)
        val text = ctx.getColor(R.color.text)
        val dim = ctx.getColor(R.color.text3)
        val ink = ctx.getColor(R.color.tab_ink)
    }

    fun glassRes(corner: Int) = when (corner) {
        0 -> R.drawable.glass_s
        2 -> R.drawable.glass_l
        else -> R.drawable.glass_m
    }

    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WeatherWidget::class.java))

    fun updateAll(ctx: Context, refreshing: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        for (id in widgetIds(ctx)) update(ctx, mgr, id, refreshing)
    }

    fun update(ctx: Context, mgr: AppWidgetManager, id: Int, refreshing: Boolean = false) {
        val city = Prefs.cityForWidget(ctx, id)
        val w = Repo.load(ctx, city.id)
        val now = System.currentTimeMillis()
        val tomorrow = Prefs.showTomorrow(ctx, "w$id")
        val m = Present.model(ctx, city, w, now, refreshing, tomorrow)
        // The small widget has no tabs, so it always shows today.
        val today = if (m.tomorrow) Present.model(ctx, city, w, now, refreshing, false) else m
        val look = Look(ctx)
        val s = look.scale.coerceIn(0.8f, 1.7f)
        val views: RemoteViews = if (Build.VERSION.SDK_INT >= 31) {
            RemoteViews(
                mapOf(
                    SizeF(60f, 40f) to small(ctx, today, look),
                    SizeF(130f, 100f * s) to medium(ctx, id, m, look, false),
                    SizeF(130f, 145f * s) to medium(ctx, id, m, look, true),
                    SizeF(240f, 170f * s) to large(ctx, id, m, look, 1, false),
                    SizeF(240f, 215f * s) to large(ctx, id, m, look, 2, false),
                    SizeF(240f, 290f * s) to large(ctx, id, m, look, 2, true),
                )
            )
        } else {
            val o = mgr.getAppWidgetOptions(id)
            val wd = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val ht = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            when {
                wd == 0 || ht == 0 -> large(ctx, id, m, look, 2, false)
                wd >= 240 && ht >= 290 * s -> large(ctx, id, m, look, 2, true)
                wd >= 240 && ht >= 215 * s -> large(ctx, id, m, look, 2, false)
                wd >= 240 && ht >= 170 * s -> large(ctx, id, m, look, 1, false)
                wd >= 130 && ht >= 145 * s -> medium(ctx, id, m, look, true)
                wd >= 130 && ht >= 100 * s -> medium(ctx, id, m, look, false)
                else -> small(ctx, today, look)
            }
        }
        mgr.updateAppWidget(id, views)
    }

    // ---------------------------------------------------------------- intents

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

    private fun tab(ctx: Context, id: Int, tomorrow: Boolean): PendingIntent {
        val t = if (tomorrow) 1 else 0
        val i = Intent(ctx, WeatherWidget::class.java)
            .setAction(WeatherWidget.ACTION_TAB)
            .setData(Uri.parse("moti://tab/$id/$t"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(WeatherWidget.EXTRA_TOMORROW, tomorrow)
        return PendingIntent.getBroadcast(ctx, 3000 + id * 2 + t, i, FLAGS)
    }

    // ---------------------------------------------------------------- helpers

    private fun base(ctx: Context, layout: Int, look: Look): RemoteViews {
        val rv = RemoteViews(ctx.packageName, layout)
        if (look.bg) {
            rv.setViewVisibility(R.id.glass, View.VISIBLE)
            rv.setImageViewResource(R.id.glass, glassRes(look.corner))
            rv.setInt(R.id.glass, "setImageAlpha", look.alpha)
        } else {
            rv.setViewVisibility(R.id.glass, View.GONE)
        }
        rv.setOnClickPendingIntent(R.id.root, openApp(ctx))
        return rv
    }

    /** Sets text with the chosen font and size. */
    private fun t(rv: RemoteViews, id: Int, text: CharSequence, sp: Float, look: Look, role: Role = Role.BODY) {
        rv.setTextViewText(id, Fonts.span(text, look.font, role))
        rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * look.scale)
    }

    private fun line(rv: RemoteViews, id: Int, text: String?, sp: Float, look: Look) {
        if (text.isNullOrEmpty()) {
            rv.setViewVisibility(id, View.GONE)
        } else {
            rv.setViewVisibility(id, View.VISIBLE)
            t(rv, id, text, sp, look)
        }
    }

    private fun icon(rv: RemoteViews, id: Int, m: Present.Model, sp: Float, look: Look) {
        if (look.icon) {
            rv.setViewVisibility(id, View.VISIBLE)
            rv.setTextViewText(id, m.icon)
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, sp * look.scale)
        } else {
            rv.setViewVisibility(id, View.GONE)
        }
    }

    // ---------------------------------------------------------------- layouts

    private fun small(ctx: Context, m: Present.Model, look: Look): RemoteViews {
        val rv = base(ctx, R.layout.widget_small, look)
        icon(rv, R.id.tv_icon, m, 24f, look)
        t(rv, R.id.tv_temp, m.tempShort, 26f, look, Role.DISPLAY)
        t(rv, R.id.tv_headline, m.headline, 14f, look, Role.HEAD)
        return rv
    }

    private fun medium(ctx: Context, id: Int, m: Present.Model, look: Look, showLine2: Boolean): RemoteViews {
        val rv = base(ctx, R.layout.widget_medium, look)
        t(rv, R.id.tv_temp, m.tempShort, 34f, look, Role.DISPLAY)
        icon(rv, R.id.tv_icon, m, 26f, look)
        t(rv, R.id.tv_city, if (m.tomorrow) m.title else m.city, 12.5f, look, Role.STRONG)
        t(rv, R.id.tv_headline, m.headline, 18f, look, Role.HEAD)
        line(rv, R.id.tv_line2, if (showLine2) m.lines.getOrNull(0) else null, 13.5f, look)
        if (m.hasData) {
            rv.setViewVisibility(R.id.tab_toggle, View.VISIBLE)
            t(rv, R.id.tab_toggle, if (m.tomorrow) "‹ Sot" else "Nesër ›", 12f, look, Role.STRONG)
            rv.setOnClickPendingIntent(R.id.tab_toggle, tab(ctx, id, !m.tomorrow))
        } else {
            rv.setViewVisibility(R.id.tab_toggle, View.GONE)
        }
        return rv
    }

    private val hourIds = listOf(
        intArrayOf(R.id.h0_t, R.id.h0_e, R.id.h0_v),
        intArrayOf(R.id.h1_t, R.id.h1_e, R.id.h1_v),
        intArrayOf(R.id.h2_t, R.id.h2_e, R.id.h2_v),
        intArrayOf(R.id.h3_t, R.id.h3_e, R.id.h3_v),
        intArrayOf(R.id.h4_t, R.id.h4_e, R.id.h4_v),
    )

    private fun large(ctx: Context, id: Int, m: Present.Model, look: Look, lines: Int, hours: Boolean): RemoteViews {
        val rv = base(ctx, R.layout.widget_large, look)

        // Tabs: Sot | Nesër
        t(rv, R.id.tab_today, "Sot", 12.5f, look, Role.STRONG)
        t(rv, R.id.tab_tomorrow, "Nesër", 12.5f, look, Role.STRONG)
        rv.setInt(R.id.tab_today, "setBackgroundResource", if (m.tomorrow) R.drawable.tab_off else R.drawable.tab_on)
        rv.setInt(R.id.tab_tomorrow, "setBackgroundResource", if (m.tomorrow) R.drawable.tab_on else R.drawable.tab_off)
        rv.setTextColor(R.id.tab_today, if (m.tomorrow) look.text else look.ink)
        rv.setTextColor(R.id.tab_tomorrow, if (m.tomorrow) look.ink else look.text)
        rv.setOnClickPendingIntent(R.id.tab_today, tab(ctx, id, false))
        rv.setOnClickPendingIntent(R.id.tab_tomorrow, tab(ctx, id, true))
        t(rv, R.id.tv_updated, m.updated, 12f, look)
        rv.setOnClickPendingIntent(R.id.tv_updated, refresh(ctx, id))

        // Header
        t(rv, R.id.tv_city, m.title, 16.5f, look, Role.STRONG)
        line(rv, R.id.tv_cond, m.condition, 13f, look)
        t(rv, R.id.tv_temp, m.temp, 46f, look, Role.DISPLAY)
        t(rv, R.id.tv_unit, m.unit, 17f, look, Role.DISPLAY)
        icon(rv, R.id.tv_icon, m, 36f, look)

        // Messages
        t(rv, R.id.tv_headline, m.headline, 24f, look, Role.HEAD)
        line(rv, R.id.tv_line2, m.lines.getOrNull(0), 15.5f, look)
        line(rv, R.id.tv_line3, if (lines >= 2) m.lines.getOrNull(1) else null, 15.5f, look)

        // Hour strip
        if (hours && m.hours.size == 5) {
            rv.setViewVisibility(R.id.hours, View.VISIBLE)
            for (k in 0 until 5) {
                val hr = m.hours[k]
                val ids = hourIds[k]
                t(rv, ids[0], hr.label, 12f, look)
                rv.setTextColor(ids[0], look.dim)
                if (look.icon) {
                    rv.setViewVisibility(ids[1], View.VISIBLE)
                    rv.setTextViewText(ids[1], hr.emoji)
                    rv.setTextViewTextSize(ids[1], TypedValue.COMPLEX_UNIT_SP, 18f * look.scale)
                } else {
                    rv.setViewVisibility(ids[1], View.GONE)
                }
                t(rv, ids[2], hr.temp, 14f, look, Role.STRONG)
            }
        } else {
            rv.setViewVisibility(R.id.hours, View.GONE)
        }

        // Footer
        t(rv, R.id.tv_pick, "Zgjidh qytetin…", 13.5f, look)
        rv.setOnClickPendingIntent(R.id.btn_city, pickCity(ctx, id))
        return rv
    }
}
