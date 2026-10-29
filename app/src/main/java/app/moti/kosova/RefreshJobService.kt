package app.moti.kosova

import android.app.job.JobParameters
import android.app.job.JobService

class RefreshJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val maxAge = if (params.jobId == Scheduler.JOB_NOW) 60_000L else 4 * 60_000L
        Refresher.refreshAsync(this, maxAge) { jobFinished(params, false) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}
