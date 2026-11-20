package com.anmol.focuuus.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.Apps
import com.anmol.focuuus.R

/**
 * The palette: a black dial like a diver's watch, warm white print, and neon orange for hands and
 * highlights. [Black] is for anything printed on orange.
 */
object W {
    val Paper = Color(0xFF0B0B0B)    // the dial
    val Dial = Color(0xFF181817)     // bezels and chapter rings, a step up from the dial
    val Ink = Color(0xFFEFEBE3)      // print
    val Ink2 = Color(0xFF928E86)     // secondary print
    val Ink3 = Color(0xFF4C4A46)     // faint print, off states
    val Hair = Color(0xFF282725)     // hairlines
    val Orange = Color(0xFFFF5B14)
    val OrangeSoft = Color(0x33FF5B14)
    val Black = Color(0xFF0B0B0B)
}

@OptIn(ExperimentalTextApi::class)
private fun jost(weight: Int) = Font(R.font.jost, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun mono(weight: Int) = Font(R.font.mono, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Jost: a Futura-like geometric sans, the classic dial face. */
val Jost = FontFamily(jost(300), jost(400), jost(500), jost(600))

/** For running numbers that shouldn't jitter. */
val Mono = FontFamily(mono(400), mono(500))

/** Small print in spaced capitals, like the text on a dial. */
val Caps = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.8.sp)

@Composable
fun FocuuusTheme(content: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.j() = copy(fontFamily = Jost)
    val type = Typography(
        displayLarge = base.displayLarge.j().copy(fontWeight = FontWeight.Light),
        displayMedium = base.displayMedium.j().copy(fontWeight = FontWeight.Light),
        displaySmall = base.displaySmall.j().copy(fontWeight = FontWeight.Light),
        headlineLarge = base.headlineLarge.j().copy(fontWeight = FontWeight.Light),
        headlineMedium = base.headlineMedium.j().copy(fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.j(),
        titleLarge = base.titleLarge.j().copy(fontWeight = FontWeight.Medium),
        titleMedium = base.titleMedium.j().copy(fontWeight = FontWeight.Medium),
        titleSmall = base.titleSmall.j(),
        bodyLarge = base.bodyLarge.j(),
        bodyMedium = base.bodyMedium.j(),
        bodySmall = base.bodySmall.j(),
        labelLarge = base.labelLarge.j().copy(fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp),
        labelMedium = base.labelMedium.j(),
        labelSmall = base.labelSmall.j(),
    )
    val colors = darkColorScheme(
        primary = W.Orange, onPrimary = W.Black,
        secondary = W.Ink, onSecondary = W.Paper,
        background = W.Paper, onBackground = W.Ink,
        surface = W.Paper, onSurface = W.Ink,
        surfaceVariant = W.Dial, onSurfaceVariant = W.Ink2,
        surfaceContainer = W.Dial, surfaceContainerHigh = W.Dial, surfaceContainerHighest = W.Hair,
        outline = W.Ink3, outlineVariant = W.Hair,
        error = W.Orange,
    )
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}

@Composable
fun CapsText(text: String, modifier: Modifier = Modifier, color: Color = W.Ink2) {
    Text(text.uppercase(), modifier, style = Caps, color = color)
}

@Composable
fun AppIcon(pkg: String, size: Dp = 40.dp, modifier: Modifier = Modifier, muted: Boolean = false) {
    val context = LocalContext.current
    val bitmap = remember(pkg) { Apps.icon(context, pkg)?.asImageBitmap() }
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    if (bitmap != null) {
        Image(bitmap, null, modifier.size(size).clip(CircleShape), colorFilter = if (muted) grey else null)
    } else {
        Box(modifier.size(size).clip(CircleShape).background(W.Dial))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(W.Hair))
}

/** A two-position switch: a hairline track and grey knob, orange with a black knob when on. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) W.Orange else W.Paper)
    val knobX by animateDpAsState(if (checked) 22.dp else 2.dp)
    Box(
        modifier
            .size(48.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .border(1.dp, if (checked) W.Orange else W.Ink3, CircleShape)
            .clickable(role = Role.Switch) { onChange(!checked) },
    ) {
        Box(Modifier.offset(x = knobX, y = 2.dp).size(24.dp).clip(CircleShape).background(if (checked) W.Black else W.Ink2))
    }
}

/** [Solid] and [Orange] are both orange on black; [Outline] is a hairline pill. */
enum class PillStyle { Solid, Outline, Orange }

@Composable
fun Pill(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.Solid, enabled: Boolean = true, height: Dp = 52.dp,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val (bg, fg) = when (style) {
        PillStyle.Solid, PillStyle.Orange -> W.Orange to W.Black
        PillStyle.Outline -> W.Paper to W.Ink
    }
    Surface(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        color = if (enabled) bg else W.Dial, contentColor = if (enabled) fg else W.Ink3,
        border = if (style == PillStyle.Outline) BorderStroke(1.dp, if (enabled) W.Ink2 else W.Hair) else null,
        modifier = modifier.height(height),
    ) {
        Row(
            Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.invoke(this)
            Text(text.uppercase(), style = Caps.copy(fontSize = 13.sp, letterSpacing = 2.sp))
        }
    }
}

/** Top bar for inner screens: a back chevron, a spaced-caps title, an optional action. */
@Composable
fun TopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Text("‹", fontFamily = Jost, fontSize = 34.sp, fontWeight = FontWeight.Light, color = W.Ink, modifier = Modifier.offset(y = (-3).dp)) }
        Spacer(Modifier.width(4.dp))
        CapsText(title, Modifier.weight(1f), color = W.Ink)
        action?.invoke()
    }
}

/** A setting with a label and hint on the left and a [Toggle] on the right. */
@Composable
fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CapsText(title, color = W.Ink)
            Spacer(Modifier.height(4.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = W.Ink2)
        }
        Toggle(checked, onChange)
    }
}

/** Padding for a screen's content column. */
val ScreenPadding = PaddingValues(horizontal = 24.dp)

/** A small square window with a value, like a watch's date window. */
@Composable
fun DateWindow(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.border(1.dp, W.Ink, RoundedCornerShape(3.dp)).background(W.Paper).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = W.Ink) }
}
