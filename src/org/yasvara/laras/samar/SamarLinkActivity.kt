/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.samar

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.yasvara.laras.R
import org.yasvara.laras.data.samar.GLOBAL
import org.yasvara.laras.data.samar.SamarPosition
import org.yasvara.laras.data.samar.SamarRepository
import org.yasvara.laras.ui.theme.LarasTheme

/**
 * "Share → Samar location" from a maps app, or a geo: link: offers to move there or keep the
 * point as a saved place.
 */
class SamarLinkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent?.dataString
        }
        if (text.isNullOrBlank()) return finish()
        val repo = SamarRepository(this)
        lifecycleScope.launch {
            val (point, current) = withContext(Dispatchers.IO) {
                val current = repo.position()
                repo.parseOrResolve(text, current) to current
            }
            if (point == null) {
                Toast.makeText(this@SamarLinkActivity, R.string.samar_notice_not_understood, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            // Maps apps share "Place name\nlink"; the name makes a good default.
            val label = text.lineSequence().map(String::trim).firstOrNull { it.isNotEmpty() && "://" !in it }.orEmpty()
            setContent { LarasTheme { LinkDialog(repo, point, current, label.take(60)) } }
        }
    }

    @androidx.compose.runtime.Composable
    private fun LinkDialog(repo: SamarRepository, point: Pair<Double, Double>, current: SamarPosition?, label: String) {
        var naming by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf(label) }
        val (lat, lng) = point
        val away = current?.takeIf { it.hasPlace }?.let { SamarRepository.distance(it.lat, it.lng, lat, lng) }
        AlertDialog(
            onDismissRequest = ::finish,
            title = { Text(stringResource(if (naming) R.string.samar_save_place else R.string.samar_link_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SamarRepository.format(lat, lng), style = MaterialTheme.typography.titleMedium)
                    if (away != null) {
                        Text(
                            stringResource(R.string.samar_link_away, SamarRepository.formatDistance(away)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (naming) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.samar_place_name)) },
                        )
                    }
                }
            },
            confirmButton = {
                if (naming) {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) { repo.savePlace(name, lat, lng) }
                            finish()
                        }
                    }) { Text(stringResource(R.string.samar_save)) }
                } else {
                    TextButton(onClick = {
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                repo.setPlace(GLOBAL, lat, lng)
                                repo.setOn(true)
                            }
                            SamarTile.requestRefresh(this@SamarLinkActivity)
                            Toast.makeText(this@SamarLinkActivity, R.string.samar_link_done, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }) { Text(stringResource(R.string.samar_link_go)) }
                }
            },
            dismissButton = {
                if (naming) {
                    TextButton(onClick = { naming = false }) { Text(stringResource(android.R.string.cancel)) }
                } else {
                    TextButton(onClick = { naming = true }) { Text(stringResource(R.string.samar_save_place)) }
                }
            },
        )
    }
}
