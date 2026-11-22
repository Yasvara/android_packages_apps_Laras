/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import org.yasvara.laras.AkarActivity
import org.yasvara.laras.R

/**
 * Manages Akar's direct entry points on the launcher: pinned home-screen shortcuts
 * (always enabled, targeting [AkarActivity] in its own task) and the optional app-drawer icon.
 */
object AkarShortcuts {
    private const val SHORTCUT_ID = "akar_home"
    private const val LAUNCHER_ALIAS = "org.yasvara.laras.AkarLauncherAlias"

    fun isPinSupported(context: Context): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        return sm.isRequestPinShortcutSupported
    }

    fun isPinned(context: Context): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        return sm.pinnedShortcuts.any { it.id == SHORTCUT_ID }
    }

    fun requestPin(context: Context): Boolean {
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        val intent = Intent(context, AkarActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val info = ShortcutInfo.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.akar_title))
            .setLongLabel(context.getString(R.string.akar_hero_title))
            .setIcon(Icon.createWithResource(context, R.mipmap.ic_akar_launcher))
            .setIntent(intent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    fun isDrawerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, LAUNCHER_ALIAS)
        return context.packageManager.getComponentEnabledSetting(cn) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setDrawerEnabled(context: Context, enabled: Boolean) {
        val cn = ComponentName(context, LAUNCHER_ALIAS)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(cn, state, PackageManager.DONT_KILL_APP)
    }
}
