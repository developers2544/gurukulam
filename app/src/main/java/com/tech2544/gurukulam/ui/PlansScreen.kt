package com.tech2544.gurukulam.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.R
import com.tech2544.gurukulam.data.Plan
import kotlinx.coroutines.launch

/** Sab plans database se. Upar instruction video, neeche fayde (photo ke saath), phir Buy. */
@Composable
fun PlansScreen(c: AppContainer, nav: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var retry by remember { mutableIntStateOf(0) }
    val plans by rememberLoad(retry) { c.repo.plans().also { runCatching { c.billing.loadProducts(it) } } }
    val premium by c.state.premium.collectAsState()
    val expires by c.state.expiresText.collectAsState()
    val activePlan by c.state.planId.collectAsState()
    val products by c.billing.products.collectAsState() // price aane par screen refresh ho

    // Purchase poora hote hi screen band
    LaunchedEffect(Unit) { c.billing.events.collect { if (it == "ok") nav.popBackStack() } }

    Column(Modifier.fillMaxSize()) {
        GkTopBar(stringResource(R.string.plans_title), onBack = { nav.popBackStack() })
        LoadBox(plans, { retry++ }) { list ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(list, key = { it.id }) { plan ->
                    PlanCard(
                        plan = plan,
                        active = premium && activePlan == plan.id,
                        expires = expires,
                        priceOf = { auto -> (if (products.isNotEmpty()) c.billing.priceText(plan, auto) else null) ?: "₹${plan.priceInr}" },
                        onBuy = { auto ->
                            val a = ctx.findActivity()
                            if (a != null) c.billing.launch(a, plan, auto)
                        },
                    )
                }
                item {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (c.billing.syncExisting()) { c.state.refresh(); ctx.toast(R.string.purchase_ok) } else ctx.toast(R.string.failed)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.restore), color = GK.Muted) }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Plan,
    active: Boolean,
    expires: String?,
    priceOf: (Boolean) -> String,
    onBuy: (Boolean) -> Unit,
) {
    var auto by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val price = priceOf(auto)
    val period = stringResource(if (plan.durationDays >= 300) R.string.period_year else R.string.period_month)

    Column(
        Modifier.fillMaxWidth().clip(shape).background(GK.Surface).border(1.dp, GK.Line, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!plan.videoUrl.isNullOrBlank()) PlanVideo(plan.videoUrl)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(plan.title, color = GK.Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (!plan.subtitle.isNullOrBlank()) Text(plan.subtitle, color = GK.Muted, fontSize = 13.sp)
            }
            Text(
                "₹${plan.priceInr}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(GK.Red, GK.Orange))).padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        if (active && expires != null) Badge(stringResource(R.string.plan_active_until, expires), GK.Green)

        // Fayde: pehle app ke standard, phir admin ke likhe hue (photo ke saath)
        Text(stringResource(R.string.plan_all), color = GK.Cream, fontSize = 14.sp)
        if (plan.superChats > 0) Text("• " + stringResource(R.string.plan_super, plan.superChats), color = GK.Cream, fontSize = 14.sp)
        if (plan.frames > 0) Text("• " + stringResource(R.string.plan_frames, plan.frames), color = GK.Cream, fontSize = 14.sp)
        Text(stringResource(R.string.plan_duration_fmt, plan.durationDays), color = GK.Muted, fontSize = 12.sp)

        plan.benefits.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!b.imageUrl.isNullOrBlank()) Thumb(b.imageUrl, 1f, Modifier.size(56.dp), 14.dp)
                Column {
                    Text(b.title, color = GK.Cream, fontWeight = FontWeight.Medium)
                    if (!b.text.isNullOrBlank()) Text(b.text, color = GK.Muted, fontSize = 12.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.plan_autopay), color = GK.Cream, modifier = Modifier.weight(1f))
            Switch(
                checked = auto, onCheckedChange = { auto = it },
                colors = SwitchDefaults.colors(checkedTrackColor = GK.Red, checkedThumbColor = Color.White),
            )
        }
        Text(
            if (auto) stringResource(R.string.plan_autopay_note, price, period) else stringResource(R.string.plan_oneoff),
            color = GK.Muted, fontSize = 12.sp,
        )
        Button(
            onClick = { onBuy(auto) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = GK.Red, contentColor = Color.White),
        ) { Text(stringResource(R.string.plan_buy, price), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PlanVideo(url: String) {
    val ctx = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx2 -> PlayerView(ctx2).also { v -> v.player = player; v.useController = true } },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
    )
}
