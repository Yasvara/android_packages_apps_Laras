/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.tempel

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.LarasActivity
import org.yasvara.laras.R
import org.yasvara.laras.data.tempel.ProfileKind
import org.yasvara.laras.data.tempel.TempelRepository

/**
 * Quick Settings tile for Tempel NFC profile switching.
 * Binds strictly on-demand while Quick Settings is expanded (0 MB idle).
 * Tap cycles cleanly between saved profiles (or toggles Shield / opens Laras if empty);
 * long-press opens the full Tempel manager in Laras.
 */
class TempelTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() = refresh()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        if (isSecure && isLocked) unlockAndRun(::cycleNextProfile) else cycleNextProfile()
    }

    private fun cycleNextProfile() {
        scope.launch {
            val repo = TempelRepository(applicationContext)
            val result = withContext(Dispatchers.IO) {
                if (!repo.available) return@withContext false
                val profiles = repo.listProfiles()
                if (profiles.size <= 1) return@withContext null
                val activeId = repo.activeProfileId()
                val idx = profiles.indexOfFirst { it.id == activeId }
                val next = profiles[(idx + 1) % profiles.size]
                repo.activate(next.id)
            }
            if (result == null) {
                openManager()
            } else {
                refresh()
            }
        }
    }

    private fun openManager() {
        val intent = Intent(LarasActivity.ACTION_TEMPEL)
            .setClass(this, LarasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
        )
    }

    private fun refresh() {
        scope.launch {
            val repo = TempelRepository(applicationContext)
            val state = withContext(Dispatchers.IO) {
                if (!repo.available) null else {
                    val profiles = repo.listProfiles()
                    val activeId = repo.activeProfileId()
                    val caps = repo.capabilities()
                    Triple(profiles.firstOrNull { it.id == activeId }, caps, profiles.size)
                }
            }
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.tempel_title)
            tile.icon = Icon.createWithResource(this@TempelTile, R.drawable.ic_tempel)
            if (state == null) {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = null
            } else {
                val (active, caps, count) = state
                when {
                    !caps.nfcOn -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.subtitle = getString(R.string.tempel_tile_nfc_off)
                    }
                    caps.observeEnabled -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.subtitle = getString(R.string.tempel_shield)
                    }
                    active != null -> {
                        tile.state = if (active.kind == ProfileKind.PAYMENT_WALLET) {
                            Tile.STATE_INACTIVE
                        } else {
                            Tile.STATE_ACTIVE
                        }
                        tile.subtitle = active.name
                    }
                    count == 0 -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.subtitle = getString(R.string.tempel_tile_empty)
                    }
                    else -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.subtitle = getString(R.string.tempel_tile_ready)
                    }
                }
            }
            tile.updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, TempelTile::class.java))
        }
    }
}
