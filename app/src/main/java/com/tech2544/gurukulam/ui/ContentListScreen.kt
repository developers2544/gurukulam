package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import kotlinx.coroutines.delay

/** Movies / Kahaniyan / Study / search-all: ek hi screen, kind badalne se. */
@Composable
fun ContentListScreen(c: AppContainer, nav: NavController, kind: String) {
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    val premium by c.state.premium.collectAsState()
    val data by rememberLoad(retry, query, kind) {
        if (query.isNotEmpty()) delay(300)
        c.repo.content(kind, query)
    }
    var last by remember { mutableStateOf<List<Content>>(emptyList()) }
    LaunchedEffect(data) { (data as? Load.Ok)?.let { last = it.v } }

    val titleRes = when (kind) {
        "movie" -> R.string.tile_movies
        "story" -> R.string.tile_stories
        "study" -> R.string.tile_study
        else -> R.string.search_hint
    }
    val ratio = when (kind) { "movie" -> 2f / 3f; "story" -> 3f / 4f; "study" -> 4f / 3f; else -> 1f }

    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(titleRes), onBack = { nav.popBackStack() })
        SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (last.isEmpty()) {
            when (val d = data) {
                is Load.Ok -> Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.padding(24.dp))
                else -> LoadBox(d, { retry++ }) {}
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(last, key = { it.id }) { item ->
                val locked = item.isPremium && !premium
                Column(Modifier.clickable { openContent(c, nav, item) }) {
                    Box {
                        Thumb(item.thumbUrl, ratio, Modifier.fillMaxWidth())
                        if (locked) {
                            Icon(
                                Icons.Rounded.Lock, stringResource(R.string.premium_content), tint = GK.Orange,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp),
                            )
                        }
                        if (item.isAi) {
                            Badge(stringResource(R.string.ai_label), GK.Line, Modifier.align(Alignment.BottomStart).padding(8.dp))
                        }
                    }
                    Text(item.title, color = GK.Cream, fontWeight = FontWeight.Medium, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                    if (!item.subtitle.isNullOrBlank()) Text(item.subtitle, color = GK.Muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
