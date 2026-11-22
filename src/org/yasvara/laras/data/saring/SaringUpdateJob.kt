/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.saring

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlin.concurrent.thread

/** Refreshes the lists once a day, on Wi-Fi while charging. */
class SaringUpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "saring-update") {
            val repository = SaringRepository(applicationContext)
            if (repository.layer(SaringRepository.NAMES)) repository.update(download = true)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val ID = 0x5a41
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun schedule(context: Context, on: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!on) {
                scheduler.cancel(ID)
                return
            }
            if (scheduler.getPendingJob(ID) != null) return
            scheduler.schedule(
                JobInfo.Builder(ID, ComponentName(context, SaringUpdateJob::class.java))
                    .setPeriodic(DAY_MS)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                    .setRequiresCharging(true)
                    .setPersisted(true)
                    .build(),
            )
        }
    }
}
