package com.syedtechdevelopers.imageenhancerpro.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours taken from the app logo: deep night background, neon purple/pink, electric blue, gold. */
object C {
    val bg = Color(0xFF07060F)
    val card = Color(0xFF141128)
    val card2 = Color(0xFF1E1A3A)
    val line = Color(0xFF2E2856)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFA9A3C7)
    val purple = Color(0xFFB44CFF)
    val pink = Color(0xFFE040FB)
    val blue = Color(0xFF4F7CFF)
    val gold = Color(0xFFFFC83D)
    val green = Color(0xFF2ED47A)
    val red = Color(0xFFFF4D6D)
    val grad = Brush.horizontalGradient(listOf(pink, purple, blue))
    val gold2 = Brush.horizontalGradient(listOf(Color(0xFFFFE07A), gold, Color(0xFFFFA62B)))
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.purple,
            onPrimary = Color.White,
            secondary = C.blue,
            background = C.bg,
            onBackground = C.text,
            surface = C.card,
            onSurface = C.text,
            surfaceVariant = C.card2,
            onSurfaceVariant = C.muted,
            outline = C.line,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides C.text, content = content)
    }
}

/** Big glowing gradient button. */
@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    brush: Brush = C.grad,
    textColor: Color = Color.White,
    height: Dp = 58.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .shadow(if (enabled) 18.dp else 0.dp, RoundedCornerShape(18.dp), ambientColor = C.purple, spotColor = C.purple)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(C.card2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SpacedRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
