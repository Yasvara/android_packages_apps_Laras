/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.ui.integrity

import android.content.Context
import androidx.annotation.StringRes
import org.yasvara.laras.R
import org.yasvara.laras.data.integrity.Check
import org.yasvara.laras.data.integrity.CheckResult
import org.yasvara.laras.data.integrity.FailureKind
import org.yasvara.laras.data.integrity.FetchFailure
import org.yasvara.laras.data.integrity.KeyboxProblem
import org.yasvara.laras.data.integrity.PifSource

/** Words for failures, so every screen explains an error the same way. */
fun Context.describe(failure: FetchFailure): String = when (failure.kind) {
    FailureKind.NO_NETWORK -> getString(R.string.fail_no_network)
    FailureKind.DNS -> getString(R.string.fail_dns)
    FailureKind.TIMEOUT -> getString(R.string.fail_timeout)
    FailureKind.TLS -> getString(R.string.fail_tls)
    FailureKind.HTTP -> getString(R.string.fail_http, failure.detail.orEmpty())
    FailureKind.TOO_LARGE -> getString(R.string.fail_too_large)
    FailureKind.OTHER -> getString(R.string.fail_other)
}

@StringRes
fun PifSource.label(): Int = when (this) {
    PifSource.CUSTOM -> R.string.source_custom
    PifSource.GOOGLE -> R.string.source_google
    PifSource.YASVARA -> R.string.source_yasvara
    PifSource.COMMUNITY -> R.string.source_community
    PifSource.IMPORTED -> R.string.source_imported
    PifSource.BUILT_IN -> R.string.source_built_in
}

@StringRes
fun KeyboxProblem.message(): Int = when (this) {
    KeyboxProblem.EMPTY -> R.string.keybox_problem_empty
    KeyboxProblem.NOT_XML -> R.string.keybox_problem_not_xml
    KeyboxProblem.NO_CERTIFICATE -> R.string.keybox_problem_no_certificate
    KeyboxProblem.BAD_CERTIFICATE -> R.string.keybox_problem_bad_certificate
    KeyboxProblem.CHAIN_TOO_SHORT -> R.string.keybox_problem_chain_short
    KeyboxProblem.TOO_LARGE -> R.string.keybox_problem_too_large
    KeyboxProblem.NO_PRIVATE_KEY -> R.string.keybox_problem_no_private_key
}

@StringRes
fun Check.title(): Int = when (this) {
    Check.CHAIN -> R.string.check_chain
    Check.GOOGLE_ROOT -> R.string.check_root
    Check.HARDWARE -> R.string.check_hardware
    Check.BOOT -> R.string.check_boot
    Check.PATCH -> R.string.check_patch
    Check.REVOCATION -> R.string.check_revocation
    Check.KEY_BINDING -> R.string.check_binding
}

/** The explanation shown under a check row, or null when the title says it all. */
fun Context.detail(result: CheckResult): String? = when (result.check) {
    Check.CHAIN -> when (result.detail) {
        null -> null
        "no-attestation" -> getString(R.string.detail_no_attestation)
        "short" -> getString(R.string.detail_chain_short)
        else -> result.detail
    }
    Check.HARDWARE -> when (result.detail) {
        "strongbox" -> getString(R.string.detail_strongbox)
        "tee" -> getString(R.string.detail_tee)
        "software" -> getString(R.string.detail_software)
        else -> null
    }
    Check.BOOT -> when {
        result.detail == "unlocked" -> getString(R.string.detail_unlocked)
        result.detail != null -> getString(R.string.detail_boot_state)
        else -> null
    }
    Check.PATCH, Check.REVOCATION -> result.detail
    Check.GOOGLE_ROOT, Check.KEY_BINDING -> null
}
