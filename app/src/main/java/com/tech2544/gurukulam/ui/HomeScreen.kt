package com.tech2544.gurukulam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Banner
import com.tech2544.gurukulam.data.Tile
import kotlinx.coroutines.delay

/** Server se tiles na aaye (pehli baar offline) tab ke liye. Normal mein tiles database se aate hain. */
private val fallbackTiles = listOf(
    Tile("movies", "Movies", "मूवीज़", "film", "#7A1E2A", "#C23A3A", "list/movie"),
    Tile("stories", "Kahaniyan", "कहानियाँ", "book", "#8A4B10", "#D98A2B", "list/story"),
    Tile("music", "Music", "म्यूज़िक", "music", "#4A2A7A", "#8A4BC2", "music"),
    Tile("status", "Status", "स्टेटस", "status", "#14683A", "#2BB673", "feed/0"),
    Tile("others", "Others", "अन्य", "wall", "#1F4E8A", "#3F8AD9", "others"),
    Tile("study", "Study", "स्टडी", "study", "#7A3A10", "#D9672B", "list/study"),
)

@Composable
fun HomeScreen(c: AppContainer, nav: NavController) {
    var retry by remember { mutableIntStateOf(0) }
    val banners by rememberLoad(retry) { c.repo.banners() }
    val tiles by rememberLoad(retry) { c.repo.tiles() }
    val status by rememberLoad(retry) { c.repo.content("status", limit = 12) }
    val premium by c.state.premium.collectAsState()
    val chatOpen by c.state.chatOpen.collectAsState()
    val profile by c.state.profile.collectAsState()
    val ctx = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun onBanner(b: Banner) {
        when (b.targetType) {
            "content" -> b.targetId?.let { nav.navigate("watch/$it") }
            "list" -> nav.navigate("list/${b.targetId ?: "movie"}")
            "plans" -> nav.navigate("plans")
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).border(2.dp, GK.Orange, CircleShape)
                        .background(GK.Surface2).clickable { nav.navigate("settings") },
                    contentAlignment = Alignment.Center,
                ) {
                    Text((profile?.displayName?.firstOrNull() ?: 'G').uppercase(), color = GK.Cream, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50)).background(GK.Surface2)
                        .clickable { nav.navigate("list/all") }.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Search, null, tint = GK.Muted)
                    Text(stringResource(R.string.search_hint), color = GK.Muted, fontSize = 14.sp, maxLines = 1)
                }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .then(if (premium) Modifier.background(Brush.linearGradient(listOf(GK.Red, GK.Orange))) else Modifier.border(1.5.dp, GK.Orange, CircleShape))
                        .clickable { nav.navigate("plans") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, stringResource(R.string.plans_title), tint = if (premium) Color.White else GK.Orange)
                }
            }
        }
        item {
            when (val b = banners) {
                is Load.Ok -> Hero(b.v, ::onBanner)
                else -> Hero(emptyList(), ::onBanner)
            }
        }
        item {
            val list = (tiles as? Load.Ok)?.v?.takeIf { it.isNotEmpty() } ?: fallbackTiles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { t ->
                            TileCard(t, Modifier.weight(1f)) { nav.navigate(t.route.ifBlank { "list/${t.key}" }) }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(50))
                    .border(1.5.dp, GK.Orange, RoundedCornerShape(50))
                    .clickable { nav.navigate("chat") }.padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (chatOpen) R.string.chat_band_open else R.string.chat_band_closed),
                    color = GK.Cream, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1,
                )
                Text(
                    stringResource(if (chatOpen) R.string.chip_open else R.string.chip_sat),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (chatOpen) GK.Green else GK.Line)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        item {
            when (val s = status) {
                is Load.Ok -> if (s.v.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.status_today), color = GK.Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(s.v) { i, item ->
                                Box(Modifier.width(130.dp).clickable { nav.navigate("feed/$i") }) {
                                    Thumb(item.thumbUrl, 4f / 5f, Modifier.fillMaxWidth())
                                    if (item.mediaType == "video") {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is Load.Err -> LoadBox(s, { retry++ }) {}
                else -> {}
            }
        }
    }
}

@Composable
private fun Hero(banners: List<Banner>, onClick: (Banner) -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    if (banners.isEmpty()) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f).glowBorder(shape).clip(shape).background(GK.Surface2))
        return
    }
    val pager = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(banners.size) {
        while (banners.size > 1) {
            delay(4000)
            pager.animateScrollToPage((pager.currentPage + 1) % banners.size)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp) { i ->
            val b = banners[i]
            Box(
                Modifier.fillMaxWidth().aspectRatio(2f).glowBorder(shape).clip(shape)
                    .background(GK.Surface2).clickable { onClick(b) },
            ) {
                if (!b.imageUrl.isNullOrBlank()) {
                    AsyncImage(b.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                if (!b.title.isNullOrBlank()) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(14.dp)) {
                        Text(b.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
        if (banners.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(banners.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 18.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(50)).background(if (pager.currentPage == i) GK.Red else GK.Line),
                    )
                }
            }
        }
    }
}

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    val hi = LocalConfiguration.current.locales[0].language == "hi"
    Box(
        modifier.aspectRatio(1f / 1.08f).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(hex(t.colorFrom), hex(t.colorTo))))
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(
            if (hi) t.labelHi else t.labelEn, color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, modifier = Modifier.align(Alignment.TopStart),
        )
        Icon(iconFor(t.icon), null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(38.dp).align(Alignment.BottomEnd))
    }
}
