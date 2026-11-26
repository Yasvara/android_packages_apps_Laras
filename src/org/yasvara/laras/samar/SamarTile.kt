/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.samar

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
import org.yasvara.laras.data.samar.SamarRepository

object SamarPrefs {
    private const val FILE = "samar"
    private const val PANEL_FROM_TILE = "panel_from_tile"

    fun panelFromTile(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(PANEL_FROM_TILE, true)

    fun setPanelFromTile(context: Context, on: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(PANEL_FROM_TILE, on).apply()
    }
}

/**
 * Tap turns the override on or off (and shows the floating map when it turns on, if the owner
 * wants that); a long press opens Laras › Samar through the tile's preferences activity.
 */
class SamarTile : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() = refresh()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        // Changing what apps are told about location is a privacy setting: as the stock
        // location tile does, ask for the device to be unlocked first.
        if (isSecure && isLocked) unlockAndRun(::toggle) else toggle()
    }

    private fun toggle() {
        scope.launch {
            val repo = SamarRepository(applicationContext)
            val (available, on, hasPlace) = withContext(Dispatchers.IO) {
                Triple(repo.available, repo.isOn(), repo.position()?.hasPlace == true)
            }
            if (!available) return@launch
            if (!on && !hasPlace) {
                // Nothing to pretend yet: open the page where a place is picked.
                openSettings()
                return@launch
            }
            withContext(Dispatchers.IO) { repo.setOn(!on) }
            if (!on && SamarPrefs.panelFromTile(applicationContext)) SamarPanel.show(applicationContext)
            if (on) SamarPanel.hide()
            refresh()
        }
    }

    private fun openSettings() {
        val intent = Intent(LarasActivity.ACTION_SAMAR).setClass(this, LarasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    }

    private fun refresh() {
        scope.launch {
            val repo = SamarRepository(applicationContext)
            val snapshot = withContext(Dispatchers.IO) {
                if (!repo.available) null else Triple(repo.isOn(), repo.position(), repo.places())
            }
            val tile = qsTile ?: return@launch
            tile.label = getString(R.string.samar_tile)
            tile.icon = Icon.createWithResource(this@SamarTile, R.drawable.ic_samar)
            if (snapshot == null) {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = null
            } else {
                val (on, position, places) = snapshot
                tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = when {
                    position?.hasPlace != true -> getString(R.string.samar_tile_no_place)
                    !on -> getString(R.string.samar_tile_off)
                    else -> places.firstOrNull {
                        SamarRepository.distance(it.lat, it.lng, position.lat, position.lng) < 40
                    }?.name ?: SamarRepository.format(position.lat, position.lng)
                }
            }
            tile.updateTile()
        }
    }

    companion object {
        fun requestRefresh(context: Context) {
            requestListeningState(context, ComponentName(context, SamarTile::class.java))
        }
    }
}
