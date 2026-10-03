package com.tech2544.gurukulam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import com.tech2544.gurukulam.media.MediaCache
import com.tech2544.gurukulam.util.DeviceActions
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(c: AppContainer, nav: NavController, start: Int) {
    var retry by remember { mutableIntStateOf(0) }
    val data by rememberLoad(retry) { c.repo.content("status", limit = 100) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = data) {
            is Load.Ok -> if (s.v.isEmpty()) {
                Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.align(Alignment.Center))
            } else {
                FeedPager(c, nav, s.v, start)
            }
            else -> LoadBox(s, { retry++ }, Modifier.align(Alignment.Center)) {}
        }
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
    }
}

/** Reels jaisa: jo video screen par hai wahi chalegi (shuru mein mute), baaki ruki rehti hain. */
@Composable
private fun FeedPager(c: AppContainer, nav: NavController, items: List<Content>, start: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val premium by c.state.premium.collectAsState()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, items.lastIndex), pageCount = { items.size })
    val player = remember { MediaCache.newPlayer(ctx).apply { repeatMode = Player.REPEAT_MODE_ONE; volume = 0f } }
    var muted by remember { mutableStateOf(true) }
    var liked by remember { mutableStateOf(setOf<String>()) }
    val counts = remember { mutableStateMapOf<String, Int>() }

    LaunchedEffect(Unit) { liked = runCatching { c.repo.myLikes() }.getOrDefault(emptySet()) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() } // app background mein jaye to video ruk jaye
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }

    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val cur = items[page]
            player.pause()
            if (cur.mediaType == "video" && !(cur.isPremium && !premium)) {
                try {
                    val url = c.repo.mediaUrl(cur)
                    player.setMediaItem(MediaItem.Builder().setUri(url).setCustomCacheKey(cur.mediaKey ?: cur.id).build())
                    player.prepare()
                    player.playWhenReady = true
                } catch (_: Exception) {
                }
            } else {
                player.clearMediaItems()
            }
        }
    }

    VerticalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
        val item = items[page]
        val locked = item.isPremium && !premium
        Box(Modifier.fillMaxSize().clickable {
            if (locked) nav.navigate("plans") else if (player.isPlaying) player.pause() else player.play()
        }) {
            if (!item.thumbUrl.isNullOrBlank()) {
                AsyncImage(item.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (item.mediaType == "video" && !locked && pager.currentPage == page) {
                AndroidView(
                    factory = {
                        PlayerView(it).also { v ->
                            v.player = player
                            v.useController = false
                            v.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            v.setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (locked) {
                Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Lock, null, tint = GK.Orange, modifier = Modifier.size(40.dp))
                        Text(stringResource(R.string.premium_content), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .padding(start = 16.dp, end = 80.dp, top = 40.dp, bottom = 24.dp),
            ) {
                Column {
                    if (item.isAi) Badge(stringResource(R.string.ai_label), GK.Line)
                    Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    if (!item.subtitle.isNullOrBlank()) Text(item.subtitle, color = Color(0xFFDDDDDD), fontSize = 13.sp)
                }
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isLiked = item.id in liked
                IconButton(onClick = {
                    scope.launch {
                        runCatching { c.repo.toggleLike(item.id) }.onSuccess { (n, l) ->
                            liked = if (l) liked + item.id else liked - item.id
                            counts[item.id] = n
                        }
                    }
                }) {
                    Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.share), tint = if (isLiked) GK.Red else Color.White)
                }
                Text("${counts[item.id] ?: item.likes}", color = Color.White, fontSize = 12.sp)
                IconButton(onClick = {
                    if (locked) { nav.navigate("plans"); return@IconButton }
                    scope.launch {
                        try {
                            val url = c.repo.mediaUrl(item)
                            val video = item.mediaType == "video"
                            val f = DeviceActions.download(ctx, c.supa.http, url, "status_${item.id}." + if (video) "mp4" else "jpg")
                            if (!DeviceActions.shareToWhatsApp(ctx, f, if (video) "video/mp4" else "image/jpeg")) ctx.toast(R.string.whatsapp_missing)
                        } catch (e: Exception) {
                            ctx.toast(R.string.failed)
                        }
                    }
                }) { Icon(Icons.Rounded.Share, stringResource(R.string.whatsapp_status), tint = Color.White) }
                if (item.mediaType == "video") {
                    IconButton(onClick = { muted = !muted }) {
                        Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null, tint = Color.White)
                    }
                }
            }
        }
    }
}
