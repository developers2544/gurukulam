package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(c: AppContainer, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val cur by c.player.current.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    val item = cur ?: return
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.padding(10.dp).fillMaxWidth().height(60.dp).clip(shape)
            .background(GK.Surface2).border(1.dp, GK.Line, shape)
            .clickable(onClick = onOpen).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Thumb(item.thumbUrl, 1f, Modifier.size(42.dp), 12.dp)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = GK.Cream, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.Medium)
            Text(item.subtitle ?: "", color = GK.Muted, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(
            onClick = { c.player.toggle() },
            modifier = Modifier.size(40.dp).clip(CircleShape).background(GK.Red),
        ) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White)
        }
        IconButton(onClick = { c.player.stop() }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Close, null, tint = GK.Muted)
        }
    }
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun FullPlayerScreen(c: AppContainer, nav: NavController) {
    val cur by c.player.current.collectAsState()
    val playing by c.player.isPlaying.collectAsState()
    val sleep by c.player.sleepMinutes.collectAsState()
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(cur) {
        while (true) {
            if (!dragging) {
                pos = c.player.positionMs()
                dur = c.player.durationMs()
            }
            delay(500)
        }
    }
    val item = cur
    if (item == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val arch = RoundedCornerShape(topStart = 999.dp, topEnd = 999.dp, bottomStart = 28.dp, bottomEnd = 28.dp)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GkTopBar(stringResource(R.string.now_playing), onBack = { nav.popBackStack() })
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth(0.68f).aspectRatio(3f / 4f).clip(arch)
                .border(2.dp, GK.Orange, arch).background(GK.Surface2),
        ) {
            if (!item.thumbUrl.isNullOrBlank()) {
                AsyncImage(item.thumbUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(item.title, color = GK.Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        Text(item.subtitle ?: "", color = GK.Muted, textAlign = TextAlign.Center, maxLines = 1)
        Spacer(Modifier.height(14.dp))
        Slider(
            value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
            onValueChange = { dragging = true; pos = (it * dur).toLong() },
            onValueChangeFinished = { c.player.seekTo(pos); dragging = false },
            colors = SliderDefaults.colors(thumbColor = GK.Orange, activeTrackColor = GK.Red, inactiveTrackColor = GK.Line),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt(pos), color = GK.Muted, fontSize = 12.sp)
            Text(fmt(dur), color = GK.Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = { c.player.previous() }) { Icon(Icons.Rounded.SkipPrevious, null, tint = GK.Cream, modifier = Modifier.size(36.dp)) }
            IconButton(
                onClick = { c.player.toggle() },
                modifier = Modifier.size(72.dp).clip(CircleShape).background(GK.Red),
            ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(38.dp)) }
            IconButton(onClick = { c.player.next() }) { Icon(Icons.Rounded.SkipNext, null, tint = GK.Cream, modifier = Modifier.size(36.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(stringResource(R.string.sleep_timer), color = GK.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.sleep_off), sleep == 0) { c.player.setSleepTimer(0) }
            listOf(15, 30, 60).forEach { m ->
                Chip(stringResource(R.string.minutes_fmt, m), sleep == m) { c.player.setSleepTimer(m) }
            }
        }
    }
}
