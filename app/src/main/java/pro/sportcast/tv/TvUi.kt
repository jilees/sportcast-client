package pro.sportcast.tv

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object TvUi {
    val Bg = Color(0xff101113)
    val Panel = Color(0xff1a1c20)
    val Text = Color(0xfff4f4f1)
    val Muted = Color(0xffb0b2bb)
    val Accent = Color(0xff6287ff)
    val Agency = FontFamily(Font(R.font.agency_fb))
    fun immersive(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
fun UiText(
    text: String, size: Int, color: Color = TvUi.Text, modifier: Modifier = Modifier,
    display: Boolean = false, maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null, bold: Boolean = false, shadow: Int = 0,
    tracking: Float = if (display) .04f else 0f,
) {
    val density = LocalDensity.current
    Text(text, modifier, color = color, fontSize = size.sp, fontFamily = if (display) TvUi.Agency else FontFamily.SansSerif,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        letterSpacing = (size * tracking).sp,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = true),
            shadow = if (shadow > 0) with(density) { Shadow(Color.Black, Offset(0f, 1.dp.toPx()), shadow.dp.toPx()) } else null))
}

fun Modifier.navButton(selected: Boolean, onClick: () -> Unit): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(0f) }
    val margin = with(LocalDensity.current) { 6.dp.toPx() }
    val scale by animateFloatAsState(if (focused && width > 0) 1f + minOf(.025f, margin / width) else 1f, animationSpec = tween(120), label = "focus")
    val shape = RoundedCornerShape(5.dp)
    this.onSizeChanged { width = maxOf(it.width, it.height).toFloat() }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onFocusChanged { focused = it.isFocused }
        .background(when { focused -> Color(0xff2c3a57); selected -> Color(0xff283552); else -> TvUi.Bg }, shape)
        .border(BorderStroke(if (focused) 2.dp else 1.dp, when { focused -> TvUi.Accent; selected -> Color(0xff3a4c78); else -> Color.Transparent }), shape)
        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}

fun eventTint(event: Event): Color = Color(when (event.sport) {
    "f1" -> 0xff503141
    "hockey" -> 0xff473629
    "basketball" -> 0xff343956
    else -> if (event.id.hashCode() and 1 == 0) 0xff263955 else 0xff472a38
})

fun Modifier.eventBackground(event: Event, home: Int?, away: Int?, radius: Int = 11, focused: Boolean = false, border: Boolean = true): Modifier = drawBehind {
    val brush = if (home != null && away != null) Brush.horizontalGradient(listOf(Color(home), Color(away)))
    else Brush.radialGradient(listOf(eventTint(event), TvUi.Panel), Offset(size.width * .12f, size.height * .3f), maxOf(1f, size.width * 1.05f))
    drawRoundRect(brush, cornerRadius = CornerRadius(radius.dp.toPx()))
    if (border) {
        val stroke = (if (focused) 2.dp else 1.dp).toPx()
        drawRoundRect(if (focused) TvUi.Accent else Color(0xff34363e), Offset(stroke / 2, stroke / 2),
            size.copy(width = size.width - stroke, height = size.height - stroke), CornerRadius(radius.dp.toPx()), style = Stroke(stroke))
    }
}
