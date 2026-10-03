package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import com.tech2544.gurukulam.data.PremiumRequired
import com.tech2544.gurukulam.util.DeviceActions
import kotlinx.coroutines.launch

/** Others = Wallpaper + Ringtone + Calendar, ek hi jagah. */
@Composable
fun OthersScreen(c: AppContainer, nav: NavController) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf(R.string.tab_wallpaper, R.string.tab_ringtone, R.string.tab_calendar)
    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(R.string.others_title), onBack = { nav.popBackStack() })
        TabRow(selectedTabIndex = tab, containerColor = GK.Bg, contentColor = GK.Orange) {
            tabs.forEachIndexed { i, r ->
                Tab(
                    selected = tab == i, onClick = { tab = i },
                    selectedContentColor = GK.Orange, unselectedContentColor = GK.Muted,
                    text = { Text(stringResource(r), fontWeight = FontWeight.Medium) },
                )
            }
        }
        when (tab) {
            0 -> WallpaperTab(c, nav)
            1 -> RingtoneTab(c, nav)
            else -> CalendarTab(c)
        }
    }
}

@Composable
private fun WallpaperTab(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val premium by c.state.premium.collectAsState()
    var retry by remember { mutableIntStateOf(0) }
    val data by rememberLoad(retry) { c.repo.content("wallpaper") }
    var selected by remember { mutableStateOf<Content?>(null) }

    LoadBox(data, { retry++ }) { list ->
        if (list.isEmpty()) Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.padding(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.id }) { item ->
                Box(Modifier.clickable {
                    if (item.isPremium && !premium) nav.navigate("plans") else selected = item
                }) {
                    Thumb(item.thumbUrl, 9f / 16f, Modifier.fillMaxWidth(), 14.dp)
                    if (item.mediaType == "video") {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp))
                    }
                    if (item.isPremium && !premium) {
                        Icon(Icons.Rounded.Lock, null, tint = GK.Orange, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(18.dp))
                    }
                }
            }
        }
    }

    selected?.let { item ->
        val live = item.mediaType == "video"
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = GK.Surface,
            text = { Thumb(item.thumbUrl, 9f / 16f, Modifier.fillMaxWidth(0.7f), 16.dp) },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    scope.launch {
                        ctx.toast(R.string.working)
                        try {
                            val url = c.repo.mediaUrl(item)
                            val f = DeviceActions.download(ctx, c.supa.http, url, "wp_${item.id}." + if (live) "mp4" else "jpg")
                            if (live) ctx.startActivity(DeviceActions.startLiveWallpaper(ctx, f)) else DeviceActions.setWallpaper(ctx, f)
                            ctx.toast(R.string.done)
                        } catch (e: PremiumRequired) {
                            nav.navigate("plans")
                        } catch (e: Exception) {
                            ctx.toast(R.string.failed)
                        }
                    }
                }) { Text(stringResource(if (live) R.string.set_live_wallpaper else R.string.set_wallpaper), color = GK.Orange) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel), color = GK.Muted) } },
        )
    }
}

@Composable
private fun RingtoneTab(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val premium by c.state.premium.collectAsState()
    var retry by remember { mutableIntStateOf(0) }
    val data by rememberLoad(retry) { c.repo.content("ringtone") }

    LoadBox(data, { retry++ }) { list ->
        if (list.isEmpty()) Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.padding(24.dp))
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            items(list, key = { it.id }) { item ->
                val locked = item.isPremium && !premium
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconButton(
                        onClick = { if (locked) nav.navigate("plans") else c.player.play(listOf(item), 0) },
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(GK.Surface2),
                    ) { Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.PlayArrow, null, tint = GK.Orange) }
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = GK.Cream, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(item.subtitle ?: "", color = GK.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    TextButton(onClick = {
                        if (locked) { nav.navigate("plans"); return@TextButton }
                        if (!DeviceActions.canWriteSettings(ctx)) {
                            ctx.toast(R.string.allow_write_settings)
                            DeviceActions.openWriteSettings(ctx)
                            return@TextButton
                        }
                        scope.launch {
                            ctx.toast(R.string.working)
                            try {
                                val f = DeviceActions.download(ctx, c.supa.http, c.repo.mediaUrl(item), "rt_${item.id}.mp3")
                                DeviceActions.setRingtone(ctx, f, item.title)
                                ctx.toast(R.string.done)
                            } catch (e: Exception) {
                                ctx.toast(R.string.failed)
                            }
                        }
                    }) { Text(stringResource(R.string.set_ringtone), color = GK.Orange) }
                }
            }
        }
    }
}

@Composable
private fun CalendarTab(c: AppContainer) {
    var retry by remember { mutableIntStateOf(0) }
    val data by rememberLoad(retry) { c.repo.content("calendar") }
    LoadBox(data, { retry++ }) { list ->
        if (list.isEmpty()) {
            Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.padding(24.dp))
        } else {
            val pager = rememberPagerState(pageCount = { list.size })
            HorizontalPager(pager, Modifier.fillMaxSize().padding(bottom = 80.dp)) { i ->
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(list[i].title, color = GK.Cream, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    AsyncImage(
                        list[i].thumbUrl, null, Modifier.fillMaxSize().padding(top = 8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}
