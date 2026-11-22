/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.data.saring

import androidx.annotation.StringRes
import org.yasvara.laras.R

/** A filter list the phone downloads. Nothing of it ships in the ROM. */
data class Source(val id: String, val name: String, val url: String)

enum class Category(
    val id: String,
    @StringRes val title: Int,
    @StringRes val summary: Int,
    val sources: List<Source>,
    val default: Boolean,
) {
    ADS(
        "ads", R.string.saring_cat_ads, R.string.saring_cat_ads_summary,
        listOf(
            Source(
                "adguard-dns", "AdGuard DNS filter",
                "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
            ),
            Source("hagezi-pro", "HaGeZi Pro", HAGEZI + "pro.txt"),
        ),
        true,
    ),
    LOCAL(
        "local", R.string.saring_cat_local, R.string.saring_cat_local_summary,
        listOf(
            Source(
                "abpindo", "ABPindo",
                "https://raw.githubusercontent.com/ABPindo/indonesianadblockrules/master/subscriptions/abpindo.txt",
            ),
        ),
        true,
    ),
    POPUPS(
        "popups", R.string.saring_cat_popups, R.string.saring_cat_popups_summary,
        listOf(Source("hagezi-popupads", "HaGeZi Pop-up ads", HAGEZI + "popupads.txt")),
        true,
    ),
    THREATS(
        "threats", R.string.saring_cat_threats, R.string.saring_cat_threats_summary,
        listOf(Source("hagezi-tif", "HaGeZi Threat intelligence", HAGEZI + "tif.medium.txt")),
        true,
    ),
    GAMBLING(
        "gambling", R.string.saring_cat_gambling, R.string.saring_cat_gambling_summary,
        listOf(Source("hagezi-gambling", "HaGeZi Gambling", HAGEZI + "gambling.medium.txt")),
        true,
    ),
    BYPASS(
        "bypass", R.string.saring_cat_bypass, R.string.saring_cat_bypass_summary,
        listOf(Source("hagezi-doh", "HaGeZi Encrypted DNS servers", HAGEZI + "doh.txt")),
        false,
    ),
}

private const val HAGEZI = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/adblock/"
