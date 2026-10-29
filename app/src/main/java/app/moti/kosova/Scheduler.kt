package app.moti.kosova

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import kotlin.math.max

/** Background refresh with the interval the user picked (Android allows 15 minutes at the shortest). */
object Scheduler {

    const val JOB_PERIODIC = 4101
    const val JOB_NOW = 4102

    private fun js(ctx: Context): JobScheduler? = ctx.getSystemService(JobScheduler::class.java)

    private fun component(ctx: Context) = ComponentName(ctx, RefreshJobService::class.java)

    fun ensurePeriodic(ctx: Context, force: Boolean = false) {
        val js = js(ctx) ?: return
        if (WidgetUpdater.widgetIds(ctx).isEmpty()) {
            js.cancel(JOB_PERIODIC)
            return
        }
        val interval = max(15, Prefs.interval(ctx)) * 60_000L
        val existing = js.getPendingJob(JOB_PERIODIC)
        if (!force && existing != null && existing.intervalMillis == interval) return
        val flex = max(JobInfo.getMinFlexMillis(), interval / 5)
        val job = JobInfo.Builder(JOB_PERIODIC, component(ctx))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setPeriodic(interval, flex)
            .build()
        try {
            js.schedule(job)
        } catch (e: Exception) {
            // ignore: next app open will try again
        }
    }

    /** One quick refresh as soon as there is internet. */
    fun runNow(ctx: Context) {
        val js = js(ctx) ?: return
        val b = JobInfo.Builder(JOB_NOW, component(ctx))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        if (Build.VERSION.SDK_INT >= 31) b.setExpedited(true)
        try {
            js.schedule(b.build())
        } catch (e: Exception) {
            try {
                js.schedule(
                    JobInfo.Builder(JOB_NOW, component(ctx))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build()
                )
            } catch (ignored: Exception) {
            }
        }
    }

    fun cancelAll(ctx: Context) {
        val js = js(ctx) ?: return
        js.cancel(JOB_PERIODIC)
        js.cancel(JOB_NOW)
    }
}
