package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.ApiException
import com.tech2544.gurukulam.data.ChatMsg
import com.tech2544.gurukulam.data.Poll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Saturday Chat: do section (About Child / Discussion). Sirf text, photo nahi. Khulna-band server tay karta hai. */
@Composable
fun ChatScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val open by c.state.chatOpen.collectAsState()
    val wallet by c.state.wallet.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val room = if (tab == 0) "child" else "world"
    var msgs by remember { mutableStateOf<List<ChatMsg>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var adminOnly by remember { mutableStateOf(false) }
    var useSuper by remember { mutableStateOf(false) }
    var poll by remember { mutableStateOf<Poll?>(null) }
    var voted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        c.state.refresh()
        poll = runCatching { c.repo.activePoll() }.getOrNull()
    }
    LaunchedEffect(room) {
        msgs = emptyList()
        while (true) {
            msgs = runCatching { c.repo.chat(room) }.getOrDefault(msgs)
            delay(4000)
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty()) return
        scope.launch {
            try {
                if (useSuper) {
                    if (wallet.superChats <= 0) { ctx.toast(R.string.chat_no_credit); nav.navigate("plans"); return@launch }
                    c.repo.sendSuper(room, text)
                } else {
                    c.repo.sendChat(room, text, if (room == "child" && adminOnly) "admin" else "public")
                }
                input = ""; useSuper = false
                msgs = runCatching { c.repo.chat(room) }.getOrDefault(msgs)
                c.state.refresh()
            } catch (e: ApiException) {
                when {
                    e.message?.contains("slow_down") == true -> ctx.toast(R.string.chat_slow)
                    e.message?.contains("no_credits") == true -> ctx.toast(R.string.chat_no_credit)
                    else -> { ctx.toast(R.string.failed); c.state.refresh() }
                }
            } catch (e: Exception) {
                ctx.toast(R.string.failed)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(R.string.chat_title), onBack = { nav.popBackStack() })
        TabRow(selectedTabIndex = tab, containerColor = GK.Bg, contentColor = GK.Orange) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.chat_tab_child)) }, selectedContentColor = GK.Orange, unselectedContentColor = GK.Muted)
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.chat_tab_world)) }, selectedContentColor = GK.Orange, unselectedContentColor = GK.Muted)
        }
        if (tab == 0) Text(stringResource(R.string.chat_child_tip), color = GK.Muted, fontSize = 12.sp, modifier = Modifier.padding(16.dp, 8.dp))

        if (!open) {
            Text(stringResource(R.string.chat_closed), color = GK.Cream, modifier = Modifier.padding(16.dp, 8.dp))
            poll?.let { p ->
                PollCard(p, voted) { optionId ->
                    scope.launch {
                        runCatching { c.repo.vote(optionId) }.onSuccess {
                            voted = true; ctx.toast(R.string.voted)
                            poll = runCatching { c.repo.activePoll() }.getOrNull()
                        }
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(msgs.reversed(), key = { it.id }) { m ->
                MessageBubble(m, mine = m.userId == c.supa.userId) {
                    scope.launch { runCatching { c.repo.reportMessage(m.id) }.onSuccess { ctx.toast(R.string.reported) } }
                }
            }
        }

        if (open) {
            if (tab == 0) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.chat_visible_all), !adminOnly) { adminOnly = false }
                    Chip(stringResource(R.string.chat_visible_admin), adminOnly) { adminOnly = true }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(
                    onClick = { useSuper = !useSuper },
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50))
                        .background(if (useSuper) GK.Orange else GK.Surface2),
                ) { Icon(Icons.Rounded.Star, stringResource(R.string.chat_super, wallet.superChats), tint = if (useSuper) Color.Black else GK.Orange) }
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 400) input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (useSuper) stringResource(R.string.chat_super, wallet.superChats) else stringResource(R.string.chat_hint), color = GK.Muted, maxLines = 1) },
                    maxLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GK.Orange, unfocusedBorderColor = GK.Line,
                        focusedTextColor = GK.Cream, unfocusedTextColor = GK.Cream, cursorColor = GK.Orange,
                    ),
                )
                IconButton(
                    onClick = { send() },
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(GK.Red),
                ) { Icon(Icons.AutoMirrored.Rounded.Send, null, tint = Color.White) }
            }
        }
    }
}

@Composable
private fun MessageBubble(m: ChatMsg, mine: Boolean, onReport: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = when (m.kind) {
        "super" -> Modifier.background(Brush.linearGradient(listOf(GK.Red, GK.Orange)), shape)
        "admin" -> Modifier.background(GK.Surface2, shape).border(1.5.dp, GK.Green, shape)
        else -> Modifier.background(if (mine) Color(0xFF2A1A1C) else GK.Surface2, shape)
    }
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Column(Modifier.fillMaxWidth(0.85f).then(bg).padding(12.dp)) {
            val label = when (m.kind) { "super" -> "★ ${m.authorName ?: ""}"; "admin" -> "Admin"; else -> m.authorName ?: "" }
            Text(label, color = if (m.kind == "super") Color.White else GK.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(m.body, color = Color.White, fontSize = 15.sp)
        }
        if (!mine && m.kind == "normal") {
            Text(
                stringResource(R.string.report), color = GK.Muted, fontSize = 11.sp,
                modifier = Modifier.clickable(onClick = onReport).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PollCard(poll: Poll, voted: Boolean, onVote: (Long) -> Unit) {
    val total = poll.options.sumOf { it.votes }.coerceAtLeast(1)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(GK.Surface).border(1.dp, GK.Line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.poll_title), color = GK.Orange, fontSize = 12.sp)
        Text(poll.question, color = GK.Cream, fontWeight = FontWeight.SemiBold)
        poll.options.forEach { o ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GK.Surface2)
                    .clickable(enabled = !voted) { onVote(o.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(o.label, color = GK.Cream, modifier = Modifier.weight(1f))
                if (voted) Text("${o.votes * 100 / total}%", color = GK.Muted, fontSize = 12.sp)
            }
        }
    }
}
