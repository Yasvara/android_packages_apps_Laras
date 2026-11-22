/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.saring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.yasvara.laras.data.saring.Category
import org.yasvara.laras.data.saring.CategoryState
import org.yasvara.laras.data.saring.OwnerRule
import org.yasvara.laras.data.saring.RadioCeirStatus
import org.yasvara.laras.data.saring.Refusal
import org.yasvara.laras.data.saring.SaringApp
import org.yasvara.laras.data.saring.SaringRepository
import org.yasvara.laras.data.saring.SaringStats
import org.yasvara.laras.data.saring.SaringUpdateJob

enum class Notice { UPDATED, UPDATE_FAILED, BAD_NAME }

data class SaringUi(
    val available: Boolean = true,
    val loading: Boolean = true,
    val names: Boolean = false,
    val browser: Boolean = false,
    val web: Boolean = false,
    val dnsEncrypted: Boolean = false,
    val safesearch: Boolean = false,
    val radioGuard: Boolean = false,
    val boost: Boolean = false,
    val ceir: RadioCeirStatus = RadioCeirStatus(),
    val stats: SaringStats = SaringStats(),
    val categories: List<CategoryState> = emptyList(),
    val owner: List<OwnerRule> = emptyList(),
    val sites: List<String> = emptyList(),
    val apps: List<SaringApp> = emptyList(),
    val log: List<Refusal> = emptyList(),
    /** Lists are being downloaded or compiled. */
    val busy: Boolean = false,
    val notice: Notice? = null,
)

class SaringViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = SaringRepository(app)
    private val _state = MutableStateFlow(SaringUi())
    val state: StateFlow<SaringUi> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() = run { null }

    fun setNames(on: Boolean) = run {
        repository.setLayer(SaringRepository.NAMES, on)
        SaringUpdateJob.schedule(getApplication(), on)
        // The first switch-on has no table yet.
        if (on && repository.stats().names == 0L) rebuild(download = true) else null
    }

    fun setBrowser(on: Boolean) = run {
        repository.setLayer(SaringRepository.BROWSER, on)
        null
    }

    fun setWeb(on: Boolean) = run {
        repository.setLayer(SaringRepository.WEB, on)
        null
    }

    fun setDnsEncrypted(on: Boolean) = run {
        repository.setLayer(SaringRepository.DNS_ENCRYPTED, on)
        null
    }

    fun setSafeSearch(on: Boolean) = run {
        repository.setLayer(SaringRepository.SAFESEARCH, on)
        null
    }

    fun setRadioGuard(on: Boolean) = run {
        repository.setLayer(SaringRepository.RADIO_GUARD, on)
        null
    }

    fun setBoost(on: Boolean) = run {
        repository.setLayer(SaringRepository.BOOST, on)
        null
    }

    fun pause(seconds: Int) = run {
        repository.pause(seconds)
        null
    }

    fun update() = run { rebuild(download = true) }

    fun setCategory(category: Category, on: Boolean) = run {
        repository.setCategory(category, on)
        rebuild(download = on)
    }

    /** [allow] null forgets the name. */
    fun setOwnerRule(name: String, allow: Boolean?) = run {
        if (repository.setOwnerRule(name, allow)) rebuild(download = false) else Notice.BAD_NAME
    }

    fun setSite(site: String, allowed: Boolean) = run {
        val clean = site.trim().lowercase().removePrefix("https://").removePrefix("http://").substringBefore('/')
        if (clean.isEmpty() || !clean.contains('.')) return@run Notice.BAD_NAME
        val pattern = if (clean.startsWith("[*.]")) clean else "[*.]$clean"
        val now = repository.sites().toMutableSet()
        if (allowed) now += pattern else now -= setOf(pattern, site)
        repository.setSites(now.sorted())
        null
    }

    fun setExempt(packageName: String, exempt: Boolean) = run {
        repository.setExempt(packageName, exempt)
        null
    }

    fun clearLog() = run {
        repository.clearLog()
        null
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    private fun rebuild(download: Boolean): Notice? {
        _state.update { it.copy(busy = true) }
        val names = repository.update(download)
        return when {
            names < 0 -> Notice.UPDATE_FAILED
            download -> Notice.UPDATED
            else -> null
        }
    }

    private fun run(block: () -> Notice?) {
        viewModelScope.launch(Dispatchers.IO) {
            val notice = block()
            val log = repository.log()
            _state.update {
                SaringUi(
                    available = repository.available,
                    loading = false,
                    names = repository.layer(SaringRepository.NAMES),
                    browser = repository.layer(SaringRepository.BROWSER),
                    web = repository.layer(SaringRepository.WEB),
                    dnsEncrypted = repository.layer(SaringRepository.DNS_ENCRYPTED),
                    safesearch = repository.layer(SaringRepository.SAFESEARCH),
                    radioGuard = repository.layer(SaringRepository.RADIO_GUARD),
                    boost = repository.layer(SaringRepository.BOOST),
                    ceir = repository.ceirStatus(),
                    stats = repository.stats(),
                    categories = repository.categories(),
                    owner = repository.ownerRules(),
                    sites = repository.sites(),
                    apps = repository.apps(log),
                    log = log,
                    busy = false,
                    notice = notice ?: it.notice,
                )
            }
        }
    }
}
