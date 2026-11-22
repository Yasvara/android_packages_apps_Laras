/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.lelap

import androidx.annotation.StringRes
import org.yasvara.laras.R
import org.yasvara.laras.data.lelap.LelapRepository.Companion.COMPONENT
import org.yasvara.laras.data.lelap.LelapRepository.Companion.JOB
import org.yasvara.laras.data.lelap.LelapRepository.Companion.PACKAGE
import org.yasvara.laras.data.lelap.LelapRepository.Companion.SENSOR
import org.yasvara.laras.data.lelap.LelapRepository.Companion.SETTING
import org.yasvara.laras.data.lelap.LelapRepository.Companion.SYNC

/** What turning a cut on costs the owner. */
enum class Risk { SAFE, FEATURE, DANGEROUS }

enum class CutGroup(@StringRes val title: Int, @StringRes val summary: Int) {
    DIAGNOSTICS(R.string.lelap_group_diagnostics, R.string.lelap_group_diagnostics_summary),
    SDK(R.string.lelap_group_sdk, R.string.lelap_group_sdk_summary),
    SYNC(R.string.lelap_group_sync, R.string.lelap_group_sync_summary),
    FEATURES(R.string.lelap_group_features, R.string.lelap_group_features_summary),
    NETWORK(R.string.lelap_group_network, R.string.lelap_group_network_summary),
    DANGEROUS(R.string.lelap_group_dangerous, R.string.lelap_group_dangerous_summary),
}

/**
 * One switch that removes one behaviour. A cut is on when every one of its rules is stored;
 * [warning] is shown before a dangerous one is turned on.
 */
class Cut(
    val id: String,
    val group: CutGroup,
    val risk: Risk,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val rules: List<Rule>,
    @StringRes val warning: Int = 0,
)

object LelapCatalog {
    private const val GMS = "com.google.android.gms"

    private fun gms(vararg classes: String) = classes.map { Rule(COMPONENT, "$GMS/$GMS$it") }
    private fun settings(vararg rules: String) = rules.map { Rule(SETTING, it) }
    private fun sync(authority: String) = listOf(Rule(SYNC, authority))
    private fun jobs(vararg needles: String) = needles.map { Rule(JOB, it) }

    val cuts: List<Cut> = listOf(
        Cut(
            "diag_dumps", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_dumps, R.string.lelap_cut_dumps_summary,
            settings(
                "global:dropbox:dumpsys:usagestats=disabled",
                "global:dropbox:dumpsys:package=disabled",
                "global:dropbox:dumpsys:batterystats=disabled",
                "global:dropbox:dumpsys:account=disabled",
                "global:dropbox:dumpsys:procstats=disabled",
                "global:dropbox:event_log=disabled",
                "global:dropbox:event_data=disabled",
                "global:activity_starts_logging_enabled=0",
                "global:send_action_app_error=0",
            ),
        ),
        Cut(
            "clearcut", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_clearcut, R.string.lelap_cut_clearcut_summary,
            gms(
                ".clearcut.uploader.QosUploaderService",
                ".westworld.WestworldService",
                ".usagereporting.service.UsageReportingIntentService",
            ),
        ),
        Cut(
            "gms_ads", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_ads, R.string.lelap_cut_ads_summary,
            gms(
                ".ads.jams.NegotiationService",
                ".ads.social.GcmSchedulerWakeupService",
                ".measurement.PackageMeasurementTaskService",
            ),
        ),
        Cut(
            "collection", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_collection, R.string.lelap_cut_collection_summary,
            listOf(Rule(SENSOR, "com.google.android.location.collectionlib")),
        ),
        Cut(
            "partner", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_partner, R.string.lelap_cut_partner_summary,
            listOf(Rule(PACKAGE, "com.google.android.partnersetup")),
        ),
        Cut(
            "qcc", CutGroup.DIAGNOSTICS, Risk.SAFE,
            R.string.lelap_cut_qcc, R.string.lelap_cut_qcc_summary,
            listOf(Rule(PACKAGE, "com.qti.qcc")),
        ),
        Cut(
            "apk_upload", CutGroup.DIAGNOSTICS, Risk.FEATURE,
            R.string.lelap_cut_apk_upload, R.string.lelap_cut_apk_upload_summary,
            settings("global:upload_apk_enable=0"),
        ),

        Cut(
            "sdk_jobs", CutGroup.SDK, Risk.SAFE,
            R.string.lelap_cut_sdk_jobs, R.string.lelap_cut_sdk_jobs_summary,
            jobs(
                "*/com.google.android.datatransport",
                "*/ScribesWorker",
                "*/EventBackgroundSenderWorker",
                "*/TrackingSubmissionJobService",
                "*/SurveySubmissionJobService",
                "*/RecommendationHintsUploadWorker",
                "*/ExceptionsUploadService",
                "*/CourierEventFlush",
            ),
        ),
        Cut(
            "madme", CutGroup.SDK, Risk.SAFE,
            R.string.lelap_cut_madme, R.string.lelap_cut_madme_summary,
            jobs("*/com.madme."),
        ),

        Cut("sync_timeline", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_timeline,
            R.string.lelap_cut_sync_timeline_summary, sync("com.google.android.location.reporting")),
        Cut("sync_fit", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_fit,
            R.string.lelap_cut_sync_fit_summary, sync("com.google.android.gms.fitness")),
        Cut("sync_games", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_games,
            R.string.lelap_cut_sync_games_summary, sync("com.google.android.gms.games.background")),
        Cut("sync_reminders", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_reminders,
            R.string.lelap_cut_sync_reminders_summary, sync("com.google.android.gms.reminders")),
        Cut("sync_chrome", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_chrome,
            R.string.lelap_cut_sync_chrome_summary, sync("com.google.android.gms.chromesync")),
        Cut("sync_feeds", CutGroup.SYNC, Risk.FEATURE, R.string.lelap_cut_sync_feeds,
            R.string.lelap_cut_sync_feeds_summary, sync("subscribedfeeds")),

        Cut(
            "fast_pair", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_fast_pair, R.string.lelap_cut_fast_pair_summary,
            gms(".nearby.discovery.service.DiscoveryService", ".nearby.presence.service.LifeCycleService"),
        ),
        Cut(
            "quick_share", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_quick_share, R.string.lelap_cut_quick_share_summary,
            gms(".nearby.sharing.ReceiveSurfaceService", ".nearby.sharing.SendSurfaceService"),
        ),
        Cut(
            "cast", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_cast, R.string.lelap_cut_cast_summary,
            gms(
                ".cast.service.CastPersistentService_Persistent",
                ".cast.devicesuggestions.SuggestionsProviderChimeraService",
            ),
        ),
        Cut(
            "instant_tether", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_tether, R.string.lelap_cut_tether_summary,
            gms(".magictether.service.TetherService", ".magictether.wifisync.WifiSyncService"),
        ),
        Cut(
            "motion", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_motion, R.string.lelap_cut_motion_summary,
            gms(
                ".motionsicknessassist.persistent.MotionSicknessAssistPersistentService",
                ".remotegestures.api.RemoteGesturesService",
                ".gestureexchange.service.GestureExchangeService",
                ".actionlearner.service.PoseService",
            ),
        ),
        Cut(
            "theft", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_theft, R.string.lelap_cut_theft_summary,
            settings("secure:theft_detection_lock_setting=0"),
        ),
        Cut(
            "scanning", CutGroup.FEATURES, Risk.FEATURE,
            R.string.lelap_cut_scanning, R.string.lelap_cut_scanning_summary,
            settings(
                "global:wifi_scan_always_enabled=0",
                "global:ble_scan_always_enabled=0",
                "global:wifi_wakeup_enabled=0",
            ),
        ),

        Cut(
            "dns", CutGroup.NETWORK, Risk.FEATURE,
            R.string.lelap_cut_dns, R.string.lelap_cut_dns_summary,
            settings(
                "global:private_dns_mode=hostname",
                "global:private_dns_specifier=dns.adguard-dns.com",
            ),
        ),

        Cut(
            "find_hub", CutGroup.DANGEROUS, Risk.DANGEROUS,
            R.string.lelap_cut_find_hub, R.string.lelap_cut_find_hub_summary,
            gms(
                ".findmydevice.spot.bleadvertising.OfflineBeaconService_Persistent",
                ".personalsafety.service.SndDetectionService",
            ),
            R.string.lelap_cut_find_hub_warning,
        ),
        Cut(
            "emergency", CutGroup.DANGEROUS, Risk.DANGEROUS,
            R.string.lelap_cut_emergency, R.string.lelap_cut_emergency_summary,
            gms(
                ".thunderbird.EmergencyPersistentService",
                ".crisisalerts.init.CrisisAlertsPersistentService",
            ),
            R.string.lelap_cut_emergency_warning,
        ),
        Cut(
            "fcm", CutGroup.DANGEROUS, Risk.DANGEROUS,
            R.string.lelap_cut_fcm, R.string.lelap_cut_fcm_summary,
            gms(".gcm.GcmService"),
            R.string.lelap_cut_fcm_warning,
        ),
    )
}
