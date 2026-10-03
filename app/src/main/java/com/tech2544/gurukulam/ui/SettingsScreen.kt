package com.tech2544.gurukulam.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.BuildConfig
import com.tech2544.gurukulam.R
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by c.state.profile.collectAsState()
    val premium by c.state.premium.collectAsState()
    val expires by c.state.expiresText.collectAsState()
    val wallet by c.state.wallet.collectAsState()
    val lang = LocalConfiguration.current.locales[0].language
    var confirmDelete by remember { mutableStateOf(false) }

    fun logout() {
        c.player.stop()
        c.state.clear()
        c.supa.signOut()
    }

    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(R.string.settings), onBack = { nav.popBackStack() })
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                val number = c.supa.userId?.filter { it.isLetterOrDigit() }?.take(12)?.uppercase()?.chunked(4)?.joinToString("  ") ?: ""
                MembershipCard(
                    name = profile?.displayName ?: "",
                    number = number,
                    valid = expires.let { e -> if (premium && e != null) stringResource(R.string.valid_till, e) else stringResource(R.string.free_member) },
                    credits = stringResource(R.string.credits_fmt, wallet.superChats, wallet.frames),
                    onClick = { nav.navigate("plans") },
                )
            }
            item {
                Text(stringResource(R.string.language), color = GK.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.lang_en), lang != "hi") {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                        scope.launch { runCatching { c.repo.updateProfile("en", null) } }
                    }
                    Chip(stringResource(R.string.lang_hi), lang == "hi") {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
                        scope.launch { runCatching { c.repo.updateProfile("hi", null) } }
                    }
                }
            }
            item {
                Text(stringResource(R.string.notify_time), color = GK.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val hours = listOf(6, 7, 8, 9, 12, 18, 20, 21)
                    items(hours) { h ->
                        Chip("%02d:00".format(h), (profile?.notifyHour ?: 8) == h) {
                            scope.launch {
                                runCatching { c.repo.updateProfile(null, h) }
                                c.state.refresh()
                            }
                        }
                    }
                }
            }
            item { SettingRow(stringResource(R.string.manage_sub)) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions"))) } }
            if (BuildConfig.PRIVACY_URL.isNotBlank()) {
                item { SettingRow(stringResource(R.string.privacy)) { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) } }
            }
            item { SettingRow(stringResource(R.string.logout)) { logout() } }
            item { SettingRow(stringResource(R.string.delete_account), danger = true) { confirmDelete = true } }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = GK.Surface,
            title = { Text(stringResource(R.string.delete_account), color = GK.Cream) },
            text = { Text(stringResource(R.string.delete_confirm), color = GK.Muted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        try {
                            c.repo.deleteAccount()
                            logout()
                        } catch (e: Exception) {
                            ctx.toast(R.string.failed)
                        }
                    }
                }) { Text(stringResource(R.string.delete), color = GK.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel), color = GK.Muted) } },
        )
    }
}

@Composable
private fun SettingRow(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        text, color = if (danger) GK.Red else GK.Cream, fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(GK.Surface)
            .clickable(onClick = onClick).padding(16.dp),
    )
}

/** Credit-card jaisa membership card (Sanatan VIP+ jaisa). */
@Composable
private fun MembershipCard(name: String, number: String, valid: String, credits: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.586f).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7A1018), GK.Red, GK.Orange)))
            .clickable(onClick = onClick).padding(18.dp),
    ) {
        Text(stringResource(R.string.membership_card_title), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.align(Alignment.TopStart))
        androidx.compose.foundation.Image(painterResource(R.drawable.logo_g), null, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).align(Alignment.TopEnd))
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(number, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(valid, color = Color(0xFFFFE9D6), fontSize = 12.sp)
            Text(credits, color = Color(0xFFFFE9D6), fontSize = 12.sp)
        }
    }
}
