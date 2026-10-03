package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Content
import kotlinx.coroutines.delay

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), color = GK.Muted, maxLines = 1) },
        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = GK.Muted) },
        shape = RoundedCornerShape(50),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GK.Orange, unfocusedBorderColor = GK.Line,
            focusedTextColor = GK.Cream, unfocusedTextColor = GK.Cream, cursorColor = GK.Orange,
        ),
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        color = if (selected) androidx.compose.ui.graphics.Color.Black else GK.Muted,
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) GK.Orange else GK.Surface)
            .border(1.dp, if (selected) GK.Orange else GK.Line, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun MusicScreen(c: AppContainer, nav: NavController) {
    var retry by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var section by remember { mutableStateOf<String?>(null) }
    val premium by c.state.premium.collectAsState()
    val data by rememberLoad(retry, query) {
        if (query.isNotEmpty()) delay(300)
        c.repo.content("music", query)
    }
    var last by remember { mutableStateOf<List<Content>>(emptyList()) }
    LaunchedEffect(data) { (data as? Load.Ok)?.let { last = it.v } }

    val sections = last.mapNotNull { it.section }.distinct()
    val shown = last.filter { section == null || it.section == section }

    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(R.string.music_title), onBack = { nav.popBackStack() })
        SearchField(query, { query = it }, Modifier.padding(horizontal = 16.dp))
        if (sections.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Chip(stringResource(R.string.all), section == null) { section = null } }
                items(sections) { s -> Chip(s, section == s) { section = s } }
            }
        }
        if (data is Load.Err && last.isEmpty()) LoadBox(data, { retry++ }) {}
        if (data is Load.Loading && last.isEmpty()) LoadBox(data, { retry++ }) {}
        if ((data is Load.Ok) && shown.isEmpty()) {
            Text(stringResource(R.string.empty_list), color = GK.Muted, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            items(shown, key = { it.id }) { item ->
                val locked = item.isPremium && !premium
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                        if (locked) {
                            nav.navigate("plans")
                        } else {
                            val queue = shown.filter { it.mediaType == "audio" && (!it.isPremium || premium) }
                            val idx = queue.indexOfFirst { it.id == item.id }
                            if (idx >= 0) c.player.play(queue, idx)
                        }
                    }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Thumb(item.thumbUrl, 1f, Modifier.size(56.dp), 14.dp)
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = GK.Cream, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(item.subtitle ?: "", color = GK.Muted, fontSize = 12.sp, maxLines = 1)
                    }
                    if (locked) Icon(Icons.Rounded.Lock, stringResource(R.string.premium_content), tint = GK.Orange)
                }
            }
        }
    }
}
