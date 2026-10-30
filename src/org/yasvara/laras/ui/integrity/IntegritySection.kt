/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private enum class OpenSheet { PROFILE, KEYBOX }

/** The Integrity group: the level apps will get, then what gives it, each with its own sheet. */
@Composable
fun IntegritySection(onOpenTargets: () -> Unit, viewModel: IntegrityViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.reload() }
    var sheet by rememberSaveable { mutableStateOf<OpenSheet?>(null) }

    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importProfile)
    }
    val keyboxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let(viewModel::importKeybox)
    }
    val anyFile = arrayOf("*/*")

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        IntegrityHero(
            state = state,
            onRunCheck = viewModel::runCheck,
            onRestart = viewModel::restartPlayServices,
            onFix = { issue ->
                when (issue) {
                    Issue.STALE_PROFILE -> viewModel.updateProfile()
                    Issue.KEYBOX_REVOKED, Issue.KEYBOX_EXPIRED -> keyboxPicker.launch(anyFile)
                }
            },
        )
        ProtectionList(
            state = state,
            onProfile = { sheet = OpenSheet.PROFILE },
            onPifEnabled = viewModel::setPifEnabled,
            onKeybox = { sheet = OpenSheet.KEYBOX },
            onKeyboxEnabled = viewModel::setKeyboxEnabled,
            onTargets = onOpenTargets,
        )
    }

    when (sheet) {
        OpenSheet.PROFILE -> ProfileSheet(
            ui = state.pif,
            onDismiss = { sheet = null },
            onMode = viewModel::setSpoofMode,
            onUpdate = viewModel::updateProfile,
            onImport = { profilePicker.launch(anyFile) },
            onReset = viewModel::resetProfile,
            onCustomUrl = viewModel::setCustomUrl,
            onChannel = viewModel::setChannel,
            onActivateDevice = viewModel::activateDevice,
            onGoogleDevice = viewModel::setGoogleDevice,
            onRefreshDevices = { viewModel.refreshGoogleDevices() },
        )
        OpenSheet.KEYBOX -> KeyboxSheet(
            ui = state.keybox,
            onDismiss = { sheet = null },
            onImport = { keyboxPicker.launch(anyFile) },
            onCheck = viewModel::checkRevocation,
            onDelete = viewModel::deleteKeybox,
        )
        null -> Unit
    }
}
