/*
 * SPDX-FileCopyrightText: The Yasvara Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.yasvara.laras.tempel

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.service.quickaccesswallet.GetWalletCardsCallback
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletService
import android.service.quickaccesswallet.SelectWalletCardRequest
import android.service.quickaccesswallet.WalletCard
import org.yasvara.laras.LarasActivity
import org.yasvara.laras.data.tempel.ProfileKind
import org.yasvara.laras.data.tempel.TempelProfile
import org.yasvara.laras.data.tempel.TempelRepository

/**
 * Lockscreen & Quick Access Wallet carousel integration. Binds strictly while the user views
 * the lockscreen Wallet switcher, rendering each lockscreen-enabled smartdoor/payment profile
 * and arming whichever card the user swipes to.
 */
class TempelWalletService : QuickAccessWalletService() {
    private val repo by lazy { TempelRepository(applicationContext) }

    override fun onWalletCardsRequested(
        request: GetWalletCardsRequest,
        callback: GetWalletCardsCallback,
    ) {
        val profiles = repo.listProfiles().filter { it.showOnLockscreen }.take(request.maxCards)
        val activeId = repo.activeProfileId()
        val w = request.cardWidthPx.coerceIn(240, 720)
        val h = request.cardHeightPx.coerceIn(150, 460)
        val openIntent = Intent(LarasActivity.ACTION_TEMPEL)
            .setClass(this, LarasActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val cards = profiles.map { p ->
            val cardType = if (p.kind == ProfileKind.PAYMENT_WALLET) {
                WalletCard.CARD_TYPE_PAYMENT
            } else {
                WalletCard.CARD_TYPE_NON_PAYMENT
            }
            WalletCard.Builder(
                p.id.toString(),
                cardType,
                Icon.createWithBitmap(renderCardBitmap(p, w, h)),
                p.name,
                pi,
            ).build()
        }
        val selectedIndex = profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
        callback.onSuccess(GetWalletCardsResponse(cards, selectedIndex))
    }

    override fun onWalletCardSelected(request: SelectWalletCardRequest) {
        val id = request.cardId.toIntOrNull() ?: return
        repo.activate(id)
        TempelTile.requestRefresh(applicationContext)
    }

    override fun onWalletDismissed() = Unit

    private fun renderCardBitmap(p: TempelProfile, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = p.colorArgb
        val radius = h * 0.09f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)

        paint.color = 0x26FFFFFF
        c.drawCircle(w * 0.86f, h * 0.25f, h * 0.36f, paint)

        paint.color = 0xFFFFFFFF.toInt()
        paint.textSize = h * 0.14f
        paint.isFakeBoldText = true
        c.drawText(p.name.take(24), w * 0.08f, h * 0.36f, paint)

        paint.isFakeBoldText = false
        paint.textSize = h * 0.095f
        paint.color = 0xD8FFFFFF.toInt()
        val sub = when (p.kind) {
            ProfileKind.PAYMENT_WALLET -> p.walletPkg
            ProfileKind.SHIELD -> "Observe Mode Shield"
            else -> p.uidHex.chunked(2).joinToString(":")
        }
        c.drawText(sub.take(30), w * 0.08f, h * 0.78f, paint)
        return bmp
    }
}
