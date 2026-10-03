package com.tech2544.gurukulam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Logo ke rang: kaala base, laal (G), kesariya (chehra), cream (text). */
object GK {
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF111111)
    val Surface2 = Color(0xFF1B1B1B)
    val Line = Color(0xFF2B2B2B)
    val Red = Color(0xFFFF4B55)
    val Orange = Color(0xFFF9A03F)
    val Cream = Color(0xFFFFF4E8)
    val Muted = Color(0xFF9E9488)
    val Green = Color(0xFF2BB673)
}

private val scheme = darkColorScheme(
    primary = GK.Red,
    onPrimary = Color.White,
    secondary = GK.Orange,
    onSecondary = Color.Black,
    background = GK.Bg,
    onBackground = GK.Cream,
    surface = GK.Surface,
    onSurface = GK.Cream,
    surfaceVariant = GK.Surface2,
    onSurfaceVariant = GK.Muted,
    outline = GK.Line,
)

@Composable
fun GurukulamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

fun hex(s: String): Color = try {
    Color(android.graphics.Color.parseColor(s))
} catch (e: Exception) {
    GK.Surface2
}
