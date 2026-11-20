package com.anmol.focuuus.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.Routine
import com.anmol.focuuus.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ---- Geometry -----------------------------------------------------------------------------------

/** Canvas angle (0° = 3 o'clock, clockwise) for a fraction of a turn starting at 12 o'clock. */
private fun turnToDeg(turn: Float) = turn * 360f - 90f

private fun DrawScope.polar(r: Float, turn: Float): Offset {
    val a = (turnToDeg(turn) * PI / 180f).toFloat()
    return Offset(center.x + r * cos(a), center.y + r * sin(a))
}

/** A radial tick from radius [r1] to [r2] at [turn]. */
private fun DrawScope.tick(turn: Float, r1: Float, r2: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) =
    drawLine(color, polar(r1, turn), polar(r2, turn), strokeWidth = width, cap = cap)

/** An arc centred on the canvas centre, radius [r], from [startTurn] for [sweepTurn]. */
private fun DrawScope.ring(r: Float, startTurn: Float, sweepTurn: Float, color: Color, width: Float, cap: StrokeCap = StrokeCap.Butt) =
    drawArc(
        color, turnToDeg(startTurn), sweepTurn * 360f, useCenter = false,
        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
        style = Stroke(width, cap = cap),
    )

private fun DrawScope.textAt(m: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = m.measure(text, style)
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

/** A hand from slightly behind the centre to [length] at [turn]. */
private fun DrawScope.hand(turn: Float, length: Float, tail: Float, width: Float, color: Color) =
    drawLine(color, polar(-tail, turn), polar(length, turn), strokeWidth = width, cap = StrokeCap.Round)

private fun routineSweep(r: Routine): Float {
    val len = ((r.end - r.start) % 1440 + 1440) % 1440
    return (if (len == 0) 1440 else len) / 1440f
}

@Composable
private fun rememberNow(everyMs: Long = 1000): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(everyMs - System.currentTimeMillis() % everyMs)
        }
    }
    return now
}

// ---- Home watch face ----------------------------------------------------------------------------

/**
 * A GMT watch: a 24-hour bezel with today's routines marked in orange, a 12-hour dial with ink hands,
 * an orange GMT hand pointing at the time on the bezel, and an orange seconds hand.
 */
@Composable
fun WatchFace(routines: List<Routine>, caption: String, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val m = rememberTextMeasurer()
    val today = now.dayOfWeek.value
    Canvas(modifier.fillMaxWidth().aspectRatio(1f)) {
        val R = size.minDimension / 2f
        val px = 1.dp.toPx()
        val bezelIn = R * 0.80f

        // Bezel: a warm ring with 24-hour numerals.
        drawCircle(W.Dial, R)
        drawCircle(W.Ink, R - px / 2, style = Stroke(px))
        drawCircle(W.Ink, bezelIn, style = Stroke(px))
        // Today's routines as orange arcs on the bezel.
        val band = (R - bezelIn)
        routines.filter { it.enabled && today in it.days && it.apps.isNotEmpty() }.forEach { r ->
            val active = r.isActive(now)
            ring(bezelIn + band * 0.18f, r.start / 1440f, routineSweep(r), if (active) W.Orange else W.Orange.copy(alpha = 0.45f), band * 0.22f)
        }
        val bezelText = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = (R * 0.068f).toSp(), color = W.Ink)
        for (h in 0 until 24) {
            val turn = h / 24f
            val mid = bezelIn + band * 0.58f
            when {
                h == 0 -> {
                    // A triangle at 24, as on a GMT bezel.
                    val tip = polar(bezelIn + band * 0.36f, 0f)
                    val s = band * 0.2f
                    drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(tip.x, tip.y); lineTo(tip.x - s, tip.y - s * 1.5f); lineTo(tip.x + s, tip.y - s * 1.5f); close()
                    }, W.Ink)
                }
                h % 3 == 0 -> textAt(m, h.toString(), polar(mid, turn), bezelText)
                else -> drawCircle(W.Ink, px * 1.6f, polar(mid, turn))
            }
        }

        // Dial.
        val dialR = bezelIn - px * 6
        drawCircle(W.Paper, dialR)
        for (i in 0 until 60) {
            val turn = i / 60f
            if (i % 5 == 0) {
                val w = if (i == 0) px * 2.2f else px * 3.2f
                if (i == 0) {
                    // Doubled index at 12.
                    val off = 0.006f
                    tick(turn - off, dialR * 0.80f, dialR * 0.95f, W.Ink, w)
                    tick(turn + off, dialR * 0.80f, dialR * 0.95f, W.Ink, w)
                } else tick(turn, dialR * 0.82f, dialR * 0.95f, W.Ink, w)
            } else tick(turn, dialR * 0.91f, dialR * 0.95f, W.Ink2, px)
        }
        // Print.
        textAt(m, "FOCUUUS", Offset(center.x, center.y - dialR * 0.42f),
            TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = (dialR * 0.085f).toSp(), letterSpacing = (dialR * 0.025f).toSp(), color = W.Ink))
        textAt(m, "GMT · AUTOMATIC", Offset(center.x, center.y - dialR * 0.30f),
            TextStyle(fontFamily = Jost, fontSize = (dialR * 0.045f).toSp(), letterSpacing = (dialR * 0.012f).toSp(), color = W.Ink2))
        textAt(m, caption.uppercase(), Offset(center.x, center.y + dialR * 0.38f),
            TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = (dialR * 0.05f).toSp(), letterSpacing = (dialR * 0.012f).toSp(), color = W.Orange))
        // Date window at 3 o'clock.
        val dw = dialR * 0.16f; val dh = dialR * 0.11f
        val dx = center.x + dialR * 0.62f
        drawRect(W.Paper, Offset(dx - dw / 2, center.y - dh / 2), Size(dw, dh))
        drawRect(W.Ink, Offset(dx - dw / 2, center.y - dh / 2), Size(dw, dh), style = Stroke(px))
        textAt(m, now.dayOfMonth.toString(), Offset(dx, center.y),
            TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = (dialR * 0.07f).toSp(), color = W.Ink))

        // Hands.
        val sec = now.second / 60f
        val minute = (now.minute + now.second / 60f) / 60f
        val hour12 = ((now.hour % 12) + minute) / 12f
        val hour24 = (now.hour + minute) / 24f
        // GMT hand: orange, reaching the bezel, with an arrow tip.
        hand(hour24, bezelIn - px * 8, 0f, px * 2f, W.Orange)
        val tip = polar(bezelIn - px * 4, hour24)
        rotate(hour24 * 360f, pivot = tip) {
            drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y - px * 2); lineTo(tip.x - px * 6, tip.y + px * 9); lineTo(tip.x + px * 6, tip.y + px * 9); close()
            }, W.Orange)
        }
        hand(hour12, dialR * 0.52f, dialR * 0.1f, px * 7f, W.Ink)
        hand(hour12, dialR * 0.48f, 0f, px * 2.2f, W.Orange) // lume strip
        hand(minute, dialR * 0.80f, dialR * 0.1f, px * 5f, W.Ink)
        hand(minute, dialR * 0.76f, 0f, px * 1.6f, W.Orange)
        hand(sec, dialR * 0.88f, dialR * 0.22f, px * 1.4f, W.Orange)
        drawCircle(W.Orange, dialR * 0.035f, polar(-dialR * 0.16f, sec))
        drawCircle(W.Ink, px * 6)
        drawCircle(W.Orange, px * 3)
    }
}

// ---- Small dials --------------------------------------------------------------------------------

/** A 24-hour ring with one routine's window in orange and a dot for now. */
@Composable
fun MiniRing24(r: Routine, size: Dp, modifier: Modifier = Modifier) {
    val now = rememberNow(30_000)
    Canvas(modifier.size(size)) {
        val R = this.size.minDimension / 2f
        val px = 1.dp.toPx()
        drawCircle(W.Dial, R)
        for (h in 0 until 24 step 6) tick(h / 24f, R * 0.78f, R * 0.98f, W.Ink2, px)
        ring(R * 0.72f, r.start / 1440f, routineSweep(r), if (r.enabled) W.Orange else W.Ink3, R * 0.2f)
        val t = (now.hour * 60 + now.minute) / 1440f
        hand(t, R * 0.62f, 0f, px * 1.6f, W.Ink)
        drawCircle(W.Ink, px * 2.5f)
    }
}

/** A subdial gauge: orange arc for the [fraction] used, an ink hand pointing at it. */
@Composable
fun Subdial(fraction: Float?, size: Dp, label: String, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    Canvas(modifier.size(size)) {
        val R = this.size.minDimension / 2f
        val px = 1.dp.toPx()
        drawCircle(W.Dial, R)
        for (i in 0 until 12) tick(i / 12f, R * 0.80f, R * 0.96f, if (i % 3 == 0) W.Ink else W.Ink3, if (i % 3 == 0) px * 1.6f else px)
        val f = fraction?.coerceIn(0f, 1f)
        if (f != null) {
            ring(R * 0.68f, 0f, f, if (f >= 1f) W.Ink else W.Orange, R * 0.14f)
        }
        textAt(m, label, Offset(center.x, center.y + R * 0.38f), TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = (R * 0.24f).toSp(), color = W.Ink))
        hand(f ?: 0f, R * 0.70f, R * 0.12f, px * 1.8f, if (f != null && f >= 1f) W.Orange else W.Ink)
        drawCircle(W.Ink, px * 3)
    }
}

/**
 * The waiting screen's dial: sixty ticks, an orange sweep and seconds hand for the countdown, and the
 * app's icon (drawn by the caller) in the middle.
 */
@Composable
fun CountdownDial(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val R = size.minDimension / 2f
        val px = 1.dp.toPx()
        drawCircle(W.Dial, R)
        drawCircle(W.Paper, R * 0.84f)
        for (i in 0 until 60) {
            val passed = i / 60f < progress
            val major = i % 5 == 0
            tick(i / 60f, R * (if (major) 0.86f else 0.90f), R * 0.97f, if (passed) W.Orange else if (major) W.Ink else W.Ink3, if (major) px * 2.4f else px * 1.2f)
        }
        ring(R * 0.80f, 0f, progress, W.Orange, px * 2)
        if (progress < 1f) {
            hand(progress, R * 0.80f, R * 0.12f, px * 1.6f, W.Orange)
        }
    }
}

// ---- Inputs -------------------------------------------------------------------------------------

/**
 * Pick a time window on a 24-hour bezel: drag the ink handle (start) or the orange one (end) around
 * the ring. Snaps to quarter hours with a tick of haptics.
 */
@Composable
fun RangeDial24(start: Int, end: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val s by rememberUpdatedState(start)
    val e by rememberUpdatedState(end)
    val change by rememberUpdatedState(onChange)
    var dragging by remember { mutableStateOf<Boolean?>(null) } // true = start
    val now = rememberNow(30_000)

    Canvas(
        modifier.fillMaxWidth().aspectRatio(1f).pointerInput(Unit) {
            fun minuteAt(p: Offset): Int {
                val a = atan2(p.y - size.height / 2f, p.x - size.width / 2f) * 180f / PI.toFloat() + 90f
                val turn = ((a % 360f) + 360f) % 360f / 360f
                return ((turn * 1440f / 15f).roundToInt() * 15) % 1440
            }
            fun dist(a: Int, b: Int) = min(abs(a - b), 1440 - abs(a - b))
            awaitEachGesture {
                val down = awaitFirstDown()
                val at = minuteAt(down.position)
                val pickStart = dist(at, s) <= dist(at, e)
                dragging = pickStart
                drag(down.id) { c ->
                    val v = minuteAt(c.position)
                    val old = if (pickStart) s else e
                    if (v != old) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pickStart) change(v, e) else change(s, v)
                    }
                    c.consume()
                }
                dragging = null
            }
        },
    ) {
        val R = size.minDimension / 2f
        val px = 1.dp.toPx()
        val inner = R * 0.74f
        val band = R - inner
        drawCircle(W.Dial, R)
        drawCircle(W.Ink, R - px / 2, style = Stroke(px))
        drawCircle(W.Ink, inner, style = Stroke(px))
        drawCircle(W.Paper, inner - px)
        // Window.
        val sweep = (((e - s) % 1440 + 1440) % 1440).let { if (it == 0) 1440 else it } / 1440f
        ring(inner + band * 0.5f, s / 1440f, sweep, W.OrangeSoft, band * 0.92f)
        ring(inner + band * 0.12f, s / 1440f, sweep, W.Orange, band * 0.16f)
        // Bezel ticks and numerals.
        val numStyle = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = (R * 0.06f).toSp(), color = W.Ink)
        for (q in 0 until 96) {
            val turn = q / 96f
            when {
                q % 12 == 0 -> textAt(m, (q / 4).toString(), polar(inner + band * 0.55f, turn), numStyle)
                q % 4 == 0 -> tick(turn, inner + band * 0.42f, inner + band * 0.68f, W.Ink, px * 1.4f)
                else -> tick(turn, inner + band * 0.5f, inner + band * 0.6f, W.Ink3, px)
            }
        }
        // Inner dial: hour marks and the current time.
        for (h in 0 until 24) tick(h / 24f, inner * 0.88f, inner * 0.95f, if (h % 6 == 0) W.Ink else W.Ink3, if (h % 6 == 0) px * 2 else px)
        val t = (now.hour * 60 + now.minute) / 1440f
        // Now: a short marker at the rim, clear of the readout.
        tick(t, inner * 0.72f, inner * 0.97f, W.Ink, px * 2.2f, StrokeCap.Round)
        drawCircle(W.Ink, px * 3, polar(inner * 0.72f, t))
        // Centre readout.
        val big = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = (R * 0.17f).toSp(), color = W.Ink)
        val cap = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = (R * 0.045f).toSp(), letterSpacing = (R * 0.012f).toSp(), color = W.Ink2)
        textAt(m, "FROM", Offset(center.x, center.y - R * 0.33f), cap)
        textAt(m, formatClock(s), Offset(center.x, center.y - R * 0.19f), big.copy(color = if (dragging == true) W.Orange else W.Ink))
        textAt(m, formatClock(e), Offset(center.x, center.y + R * 0.15f), big.copy(color = if (dragging == false) W.Orange else W.Ink))
        textAt(m, "UNTIL", Offset(center.x, center.y + R * 0.29f), cap)
        val hours = (sweep * 24f)
        val dur = if (sweep >= 1f) "ALL DAY" else if (hours % 1f == 0f) "${hours.toInt()} H" else "%.2f H".format(hours).replace(".50", ".5").replace(".25", "¼").replace(".75", "¾")
        textAt(m, dur, Offset(center.x, center.y - R * 0.02f), cap.copy(color = W.Orange))
        // Handles.
        val hs = polar(inner + band * 0.5f, s / 1440f)
        val he = polar(inner + band * 0.5f, e / 1440f)
        drawCircle(W.Paper, band * 0.42f, hs); drawCircle(W.Ink, band * 0.42f, hs, style = Stroke(px * 2))
        drawCircle(W.Ink, band * 0.16f, hs)
        drawCircle(W.Orange, band * 0.42f, he); drawCircle(W.Black, band * 0.16f, he)
    }
}

/**
 * A horizontal scale like a rotating bezel: drag it under the fixed orange index to choose a value.
 * Each step clicks with haptics and the scale springs onto the nearest mark when let go.
 */
@Composable
fun BezelRuler(value: Int, step: Int, max: Int, majorEvery: Int, label: (Int) -> String, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val m = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val spacing = with(LocalDensity.current) { 11.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(value / step * spacing) }
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    // Follow outside changes when not being dragged.
    LaunchedEffect(value) { if (!offset.isRunning) offset.snapTo(value / step * spacing) }
    val maxOffset = max / step * spacing

    Canvas(
        modifier.fillMaxWidth().height(64.dp).pointerInput(step, max) {
            detectHorizontalDragGestures(
                onDragEnd = { scope.launch { offset.animateTo((offset.value / spacing).roundToInt() * spacing, spring(stiffness = 600f)) } },
            ) { c, dx ->
                c.consume()
                scope.launch {
                    offset.snapTo((offset.value - dx).coerceIn(0f, maxOffset))
                    val v = (offset.value / spacing).roundToInt() * step
                    if (v != current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        change(v)
                    }
                }
            }
        },
    ) {
        val px = 1.dp.toPx()
        val cx = size.width / 2f
        val base = size.height * 0.62f
        drawLine(W.Hair, Offset(0f, base), Offset(size.width, base), px)
        val first = ((offset.value - cx) / spacing).toInt() - 1
        val last = ((offset.value + cx) / spacing).toInt() + 1
        val numStyle = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = W.Ink2)
        for (i in first.coerceAtLeast(0)..last.coerceAtMost(max / step)) {
            val x = cx + i * spacing - offset.value
            val fade = (1f - abs(x - cx) / cx).coerceIn(0f, 1f)
            val major = i % majorEvery == 0
            val h = if (major) size.height * 0.30f else size.height * 0.16f
            drawLine(W.Ink.copy(alpha = 0.15f + 0.85f * fade), Offset(x, base - h), Offset(x, base), if (major) px * 1.6f else px)
            if (major) {
                val layout = m.measure(label(i * step), numStyle.copy(color = W.Ink2.copy(alpha = fade)))
                drawText(layout, topLeft = Offset(x - layout.size.width / 2f, base + px * 6))
            }
        }
        // The fixed index.
        drawLine(W.Orange, Offset(cx, base - size.height * 0.48f), Offset(cx, base + px * 2), px * 3, cap = StrokeCap.Round)
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, base - size.height * 0.40f); lineTo(cx - px * 6, base - size.height * 0.56f); lineTo(cx + px * 6, base - size.height * 0.56f); close()
        }, W.Orange)
    }
}
