package com.tech2544.gurukulam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import com.tech2544.gurukulam.push.fcmToken
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val c = rememberContainer()
    val loggedIn by c.supa.loggedIn.collectAsState()
    Box(Modifier.fillMaxSize().background(GK.Bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (loggedIn) MainNav(c) else AuthScreen(c)
    }
}

private val noMiniPlayer = setOf("player", "feed/{start}", "watch/{id}", "plans")

@Composable
fun MainNav(c: AppContainer) {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val playing by c.player.current.collectAsState()
    val needPremium by c.player.needPremium.collectAsState()

    // App khulte hi: user state, purane purchase ka sync, plan popup (sirf ek baar), push token
    LaunchedEffect(Unit) {
        c.state.refresh()
        launch { if (c.billing.syncExisting()) c.state.refresh() }
        val p = c.state.profile.value
        if (p != null && !p.planShown) {
            nav.navigate("plans")
            runCatching { c.repo.markPlanShown() }
        }
        val t = fcmToken()
        if (t != null) runCatching { c.repo.saveFcm(t, c.state.profile.value?.notifyHour ?: 8) }
    }

    val playerError by c.player.error.collectAsState()
    LaunchedEffect(playerError) {
        if (playerError != null) { ctx.toast(R.string.failed); c.player.error.value = null }
    }

    LaunchedEffect(needPremium) {
        if (needPremium) {
            c.player.needPremium.value = false
            nav.navigate("plans")
        }
    }

    LaunchedEffect(Unit) {
        c.billing.events.collect { ev ->
            when {
                ev == "ok" -> { ctx.toast(R.string.purchase_ok); c.state.refresh() }
                ev == "pending" -> ctx.toast(R.string.purchase_pending)
                ev == "cancel" -> ctx.toast(R.string.purchase_cancel)
                else -> ctx.toast(R.string.purchase_error)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home") {
            composable("home") { HomeScreen(c, nav) }
            composable("music") { MusicScreen(c, nav) }
            composable("list/{kind}") { e -> ContentListScreen(c, nav, e.arguments?.getString("kind") ?: "all") }
            composable("watch/{id}") { e -> WatchScreen(c, nav, e.arguments?.getString("id") ?: "") }
            composable("feed/{start}", arguments = listOf(navArgument("start") { type = NavType.IntType })) { e ->
                FeedScreen(c, nav, e.arguments?.getInt("start") ?: 0)
            }
            composable("others") { OthersScreen(c, nav) }
            composable("plans") { PlansScreen(c, nav) }
            composable("settings") { SettingsScreen(c, nav) }
            composable("chat") { ChatScreen(c, nav) }
            composable("player") { FullPlayerScreen(c, nav) }
        }
        if (playing != null && route !in noMiniPlayer) {
            MiniPlayer(c, onOpen = { nav.navigate("player") }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** Kisi bhi content par tap: premium lock, audio -> player, video -> watch. */
fun openContent(c: AppContainer, nav: NavController, item: Content) {
    if (item.isPremium && !c.state.premium.value) {
        nav.navigate("plans")
        return
    }
    when (item.mediaType) {
        "audio" -> { c.player.play(listOf(item), 0); nav.navigate("player") }
        "video" -> nav.navigate("watch/${item.id}")
        else -> {}
    }
}
