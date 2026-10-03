package com.tech2544.gurukulam.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tech2544.gurukulam.AppContainer
import com.tech2544.gurukulam.GurukulamApp
import com.tech2544.gurukulam.R
import kotlinx.coroutines.CancellationException

// ---------- Load state (loading / ok / error) ----------
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val v: T) : Load<T>
    data class Err(val msg: String) : Load<Nothing>
}

@Composable
fun <T> rememberLoad(vararg keys: Any?, block: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Loading, *keys) {
        value = Load.Loading
        value = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(e.message ?: "")
        }
    }

@Composable
fun <T> LoadBox(
    state: Load<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is Load.Loading -> Box(modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
            CircularProgressIndicator(color = GK.Red)
        }
        is Load.Err -> Column(
            modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.loading_error), color = GK.Muted)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = GK.Orange) }
        }
        is Load.Ok -> content(state.v)
    }
}

// ---------- Small helpers ----------
@Composable
fun rememberContainer(): AppContainer =
    (LocalContext.current.applicationContext as GurukulamApp).container

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun Context.toast(@StringRes id: Int) = Toast.makeText(this, id, Toast.LENGTH_SHORT).show()
fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun iconFor(name: String): ImageVector = when (name) {
    "film" -> Icons.Rounded.Movie
    "book" -> Icons.Rounded.AutoStories
    "music" -> Icons.Rounded.MusicNote
    "status" -> Icons.Rounded.PlayCircle
    "wall" -> Icons.Rounded.Wallpaper
    "study" -> Icons.Rounded.School
    "calendar" -> Icons.Rounded.CalendarMonth
    else -> Icons.Rounded.Star
}

// ---------- UI pieces ----------
@Composable
fun GkTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = GK.Cream)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = GK.Cream, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Thumb(url: String?, ratio: Float, modifier: Modifier = Modifier, radius: Dp = 16.dp) {
    Box(modifier.aspectRatio(ratio).clip(RoundedCornerShape(radius)).background(GK.Surface2)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun Badge(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Text(
        text, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Hero poster ke charon taraf ghoomti hui glow line. */
@Composable
fun Modifier.glowBorder(shape: Shape, width: Dp = 2.dp): Modifier {
    val t = rememberInfiniteTransition(label = "glow")
    val x by t.animateFloat(
        initialValue = -500f,
        targetValue = 1800f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    val brush = Brush.linearGradient(
        colors = listOf(GK.Line, GK.Red, GK.Orange, GK.Line),
        start = Offset(x - 450f, 0f),
        end = Offset(x, 450f),
    )
    return this
        .shadow(10.dp, shape, ambientColor = GK.Red, spotColor = GK.Orange)
        .border(width, brush, shape)
}
