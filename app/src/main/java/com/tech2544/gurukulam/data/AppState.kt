package com.tech2544.gurukulam.data

import kotlinx.coroutines.flow.MutableStateFlow
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Login ke baad user ki live state: premium, wallet, profile, chat open/closed. */
class AppState(private val repo: Repo) {
    val premium = MutableStateFlow(false)
    val expiresText = MutableStateFlow<String?>(null)
    val planId = MutableStateFlow<String?>(null)
    val wallet = MutableStateFlow(Wallet())
    val profile = MutableStateFlow<Profile?>(null)
    val chatOpen = MutableStateFlow(false)

    suspend fun refresh() {
        runCatching { profile.value = repo.profile() }
        runCatching {
            val e = repo.entitlement()
            val exp = e?.expiresAt?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }
            val active = exp != null && exp.isAfter(OffsetDateTime.now())
            premium.value = active
            planId.value = if (active) e?.planId else null
            expiresText.value = if (active) exp!!.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())) else null
        }
        runCatching { wallet.value = repo.wallet() }
        runCatching { chatOpen.value = repo.chatOpen() }
    }

    fun clear() {
        premium.value = false; expiresText.value = null; planId.value = null
        wallet.value = Wallet(); profile.value = null; chatOpen.value = false
    }
}
