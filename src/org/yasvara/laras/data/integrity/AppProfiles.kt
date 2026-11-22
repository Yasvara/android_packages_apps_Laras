/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.integrity

import android.content.Context
import android.security.yasvara.YasvaraSpoofProfiles
import android.security.yasvara.YasvaraSpoofProfiles.Profile

/** Per-app spoofing outside Play Integrity: Photos, Snapchat and user-chosen apps. */
class AppProfilesRepository(private val context: Context, private val store: SecureStore) {
    var photos: Boolean
        get() = store.getBoolean(IntegrityKeys.PHOTOS_SPOOF, true)
        set(value) {
            store.putBoolean(IntegrityKeys.PHOTOS_SPOOF, value)
            PlayServices.kill(context, listOf(PHOTOS))
        }

    var snapchat: Boolean
        get() = store.getBoolean(IntegrityKeys.SNAP_SPOOF, true)
        set(value) {
            store.putBoolean(IntegrityKeys.SNAP_SPOOF, value)
            PlayServices.kill(context, listOf(SNAPCHAT))
        }

    var spoofMode: Int
        get() = store.getInt(IntegrityKeys.SPOOF_MODE, IntegrityKeys.MODE_AUTO)
        set(value) {
            store.putInt(IntegrityKeys.SPOOF_MODE, value)
            val targets = buildList {
                if (photos) add(PHOTOS)
                if (snapchat) add(SNAPCHAT)
                addAll(assignments().keys)
            }
            if (targets.isNotEmpty()) PlayServices.kill(context, targets)
        }

    /** Every identity an app can be shown (the engine's table, shared with Pentas). */
    fun profiles(): List<Profile> = YasvaraSpoofProfiles.all(context.contentResolver)

    fun profile(id: String): Profile? = YasvaraSpoofProfiles.find(context.contentResolver, id)

    fun assignments(): Map<String, String> = YasvaraSpoofProfiles.assignments(context.contentResolver)

    fun assign(packageName: String, profileId: String) =
        YasvaraSpoofProfiles.assign(context, packageName, profileId)

    fun unassign(packageName: String) = YasvaraSpoofProfiles.assign(context, packageName, null)

    companion object {
        const val PHOTOS = "com.google.android.apps.photos"
        const val SNAPCHAT = "com.snapchat.android"
    }
}
