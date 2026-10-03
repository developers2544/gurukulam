package com.tech2544.gurukulam.ui

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import com.tech2544.gurukulam.data.PremiumRequired
import com.tech2544.gurukulam.media.MediaCache

/** Kahani / movie ka video player. Jahan chhoda tha wahin se resume. 403 (link expire) par naya link. */
@Composable
fun WatchScreen(c: AppContainer, nav: NavController, id: String) {
    var retry by remember { mutableIntStateOf(0) }
    val item by rememberLoad(id, retry) { c.repo.contentById(id) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = item) {
            is Load.Ok -> {
                val content = s.v
                if (content == null) {
                    Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.align(Alignment.Center))
                } else {
                    WatchPlayer(c, nav, content)
                }
            }
            else -> LoadBox(s, { retry++ }, Modifier.align(Alignment.Center)) {}
        }
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}

@Composable
private fun WatchPlayer(c: AppContainer, nav: NavController, content: Content) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val prefs = remember { ctx.getSharedPreferences("gk_resume", Context.MODE_PRIVATE) }
    val player = remember { MediaCache.newPlayer(ctx) }
    var attempts by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val posKey = "pos_${content.id}"

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            if (player.duration > 0 && player.currentPosition < player.duration - 3000) {
                prefs.edit().putLong(posKey, player.currentPosition).apply()
            } else {
                prefs.edit().remove(posKey).apply()
            }
            player.release()
            ctx.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // App background mein jaye to video ruk jaye
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                if (attempts < 2) attempts++ else error = e.message ?: "Playback error"
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }

    LaunchedEffect(content.id, attempts) {
        try {
            val url = c.repo.mediaUrl(content)
            val start = if (attempts > 0) player.currentPosition else prefs.getLong(posKey, 0L)
            player.setMediaItem(MediaItem.Builder().setUri(url).setCustomCacheKey(content.mediaKey ?: content.id).build(), start)
            player.prepare()
            player.playWhenReady = true
            error = null
        } catch (e: PremiumRequired) {
            nav.popBackStack()
            nav.navigate("plans")
        } catch (e: Exception) {
            error = e.message ?: "Error"
        }
    }

    AndroidView(
        factory = { ctx2 -> PlayerView(ctx2).also { v -> v.player = player; v.useController = true } },
        modifier = Modifier.fillMaxSize(),
    )
    if (content.isAi) {
        Badge(stringResource(R.string.ai_label), GK.Line, Modifier.padding(start = 56.dp, top = 14.dp))
    }
    // Landscape / portrait badalne ka button (movies ke liye)
    var landscape by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    IconButton(
        onClick = {
            landscape = !landscape
            ctx.findActivity()?.requestedOrientation =
                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        },
        modifier = Modifier.padding(top = 4.dp),
    ) { Icon(Icons.Rounded.ScreenRotation, null, tint = Color.White) }
    }
    error?.let {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.loading_error), color = Color.White)
            IconButton(onClick = { error = null; attempts = 0; attempts++ }) {
                Icon(Icons.Rounded.Refresh, stringResource(R.string.retry), tint = GK.Orange)
            }
        }
    }
}
