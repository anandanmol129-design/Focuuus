package com.anmol.focuuus.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.AppLimit
import com.anmol.focuuus.Apps
import com.anmol.focuuus.Routine
import com.anmol.focuuus.Screen
import com.anmol.focuuus.Store
import com.anmol.focuuus.dayLetter
import com.anmol.focuuus.formatClock
import com.anmol.focuuus.formatDuration
import com.anmol.focuuus.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(tab: Int, onTab: (Int) -> Unit, resumes: Int, open: (Screen) -> Unit) {
    val context = LocalContext.current
    val routines by Store.routines.collectAsState()
    val limits by Store.limits.collectAsState()
    // Re-read every half minute so "active" follows the clock.
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); minute++ } }
    val usage = remember { mutableStateMapOf<String, Long>() }
    LaunchedEffect(limits, resumes) {
        while (true) {
            val today = withContext(Dispatchers.IO) { Apps.usageToday(context, limits.map { it.pkg }.toSet()) }
            usage.clear(); usage.putAll(today)
            delay(30_000)
        }
    }
    val caption = remember(routines, minute) { dialCaption(routines) }

    Box(Modifier.fillMaxSize().background(W.Paper)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        // Room for the floating bar.
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsText("Focuuus", Modifier.weight(1f), color = W.Ink)
                CapsText(remember(minute) { LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE · d MMM")) })
            }
        }
        item { WatchFace(routines, caption, Modifier.padding(horizontal = 36.dp)) }
        item { SetupCard(resumes) }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CapsText(if (tab == 0) "Routines" else "Limits", color = W.Ink)
                Spacer(Modifier.width(8.dp))
                Text((if (tab == 0) routines.size else limits.size).toString(), fontFamily = Mono, fontSize = 12.sp, color = W.Orange)
            }
        }
        item { Hairline(Modifier.padding(horizontal = 24.dp)) }
        if (tab == 0) {
            if (routines.isEmpty()) item {
                Empty("No routines", "A routine blocks apps at set times, like social media during work hours. They show on the bezel in orange.")
            }
            items(routines, key = { it.id }) { r ->
                RoutineRow(r, active = remember(r, minute) { r.isActive() }) { open(Screen.EditRoutine(r.id)) }
                Hairline(Modifier.padding(horizontal = 24.dp))
            }
        } else {
            if (limits.isEmpty()) item {
                Empty("No limits", "Cap an app's daily time, make it wait before opening, and choose how long each visit lasts.")
            }
            items(limits, key = { it.pkg }) { l ->
                LimitRow(l, usage[l.pkg] ?: 0L) { open(Screen.EditLimit(l.pkg)) }
                Hairline(Modifier.padding(horizontal = 24.dp))
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
    BottomBar(
        tab, routines.size, limits.size, onTab,
        onNew = { open(if (tab == 0) Screen.EditRoutine(null) else Screen.PickLimitApp) },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    }
}

/**
 * Floating at the bottom: a pill with the two lists, the chosen one in orange, and a round orange
 * button to add to it. The list fades out underneath.
 */
@Composable
private fun BottomBar(tab: Int, routines: Int, limits: Int, onTab: (Int) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.35f to W.Paper.copy(alpha = 0.92f), 1f to W.Paper))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(60.dp).clip(CircleShape).background(W.Dial)
                    .border(1.dp, W.Hair, CircleShape).padding(5.dp),
            ) {
                Segment("Routines", routines, tab == 0, Modifier.weight(1f)) { onTab(0) }
                Segment("Limits", limits, tab == 1, Modifier.weight(1f)) { onTab(1) }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(W.Orange).clickable(onClick = onNew),
                contentAlignment = Alignment.Center,
            ) { Text("+", fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 34.sp, color = W.Black, modifier = Modifier.padding(bottom = 3.dp)) }
        }
    }
}

@Composable
private fun Segment(text: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) W.Orange else W.Dial)
    val fg by animateColorAsState(if (selected) W.Black else W.Ink2)
    Row(
        modifier.fillMaxHeight().clip(CircleShape).background(bg).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Text(text.uppercase(), style = Caps.copy(fontSize = 12.sp), color = fg)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), fontFamily = Mono, fontSize = 12.sp, color = if (selected) W.Black else W.Orange)
    }
}

/** What the dial says: locked until when, or free until when. */
private fun dialCaption(routines: List<Routine>): String {
    val now = LocalDateTime.now()
    val active = routines.filter { it.isActive(now) }
    if (active.isNotEmpty()) {
        val r = active.first()
        return if (r.start == r.end) "Locked all day" else "Locked until ${formatClock(r.end)}"
    }
    val t = now.hour * 60 + now.minute
    val next = routines.filter { it.enabled && it.apps.isNotEmpty() && now.dayOfWeek.value in it.days && it.start > t }.minByOrNull { it.start }
    return if (next != null) "Free until ${formatClock(next.start)}" else "Free today"
}

@Composable
private fun RoutineRow(r: Routine, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniRing24(r, 56.dp)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name.ifBlank { "Routine" }, fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = if (r.enabled) W.Ink else W.Ink2)
                if (active) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(6.dp).clip(CircleShape).background(W.Orange))
                    Spacer(Modifier.width(5.dp))
                    CapsText("Active", color = W.Orange)
                }
            }
            Spacer(Modifier.height(2.dp))
            val time = if (r.start == r.end) "All day" else "${formatClock(r.start)} — ${formatClock(r.end)}"
            Text(time, fontFamily = Mono, fontSize = 13.sp, color = W.Ink2)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..7).forEach { d ->
                    Text(dayLetter(d), style = Caps.copy(letterSpacing = 0.sp), textAlign = TextAlign.Center, color = if (d in r.days) W.Ink else W.Ink3, modifier = Modifier.width(14.dp))
                }
                Spacer(Modifier.width(10.dp))
                r.apps.take(5).forEach { AppIcon(it, 18.dp, Modifier.padding(end = 4.dp), muted = !r.enabled) }
                if (r.apps.size > 5) Text("+${r.apps.size - 5}", style = Caps, color = W.Ink2)
            }
        }
        Toggle(r.enabled, { Store.saveRoutine(r.copy(enabled = it)) })
    }
}

@Composable
private fun LimitRow(l: AppLimit, used: Long, onClick: () -> Unit) {
    val context = LocalContext.current
    val fraction = if (l.dailyMinutes > 0) used / (l.dailyMinutes * 60_000f) else null
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Subdial(fraction, 56.dp, (used / 60_000).toString())
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(l.pkg, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(remember(l.pkg) { Apps.label(context, l.pkg) }, fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 19.sp, color = W.Ink)
            }
            Spacer(Modifier.height(2.dp))
            val usedText = if (l.dailyMinutes > 0) "${formatDuration(used)} / ${formatMinutes(l.dailyMinutes)}" else "${formatDuration(used)} today"
            Text(usedText, fontFamily = Mono, fontSize = 13.sp, color = if (fraction != null && fraction >= 1f) W.Orange else W.Ink2)
            val tags = listOfNotNull(
                if (l.delaySeconds > 0) "Wait ${l.delaySeconds}s" else null,
                if (l.askDuration) "Asks how long" else if (l.sessionMinutes > 0) "${formatMinutes(l.sessionMinutes)} visits" else null,
                if (l.bubble) "Timer" else null,
                if (l.autoClose) "Auto-close" else null,
            )
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                CapsText(tags.joinToString("  ·  "))
            }
        }
        Text("›", fontFamily = Jost, fontSize = 28.sp, fontWeight = FontWeight.Light, color = W.Ink3)
    }
}

@Composable
private fun Empty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CapsText(title, color = W.Ink)
        Spacer(Modifier.height(10.dp))
        Text(text, fontFamily = Jost, fontSize = 15.sp, textAlign = TextAlign.Center, color = W.Ink2)
    }
}

@SuppressLint("BatteryLife")
private fun askBattery(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
    }.onFailure { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}

/** The three things Focuuus needs from the system, until all are done. */
@Composable
private fun SetupCard(resumes: Int) {
    val context = LocalContext.current
    val service = remember(resumes) { Apps.isServiceEnabled(context) }
    val usage = remember(resumes) { Apps.hasUsageAccess(context) }
    val battery = remember(resumes) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    if (service && usage && battery) return
    Column(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp).fillMaxWidth()
            .border(1.dp, W.Ink3, RoundedCornerShape(6.dp)).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(W.Orange))
            Spacer(Modifier.width(8.dp))
            CapsText("Wind it up", color = W.Ink)
        }
        Spacer(Modifier.height(6.dp))
        Text("Three switches keep Focuuus running. Nothing leaves your phone.", fontFamily = Jost, fontSize = 15.sp, color = W.Ink2)
        SetupStep("01", "Accessibility", "Sees which app is open, to block it and show the timer. If the switch is greyed out: App info › ⋮ › Allow restricted settings.", service) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        SetupStep("02", "Usage access", "Reads how long you've used each app today.", usage) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }.onFailure { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        SetupStep("03", "Battery: unrestricted", "Stops your phone's battery saver from stopping Focuuus.", battery) { askBattery(context) }
    }
}

@Composable
private fun SetupStep(number: String, title: String, hint: String, done: Boolean, onClick: () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Hairline()
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        Text(number, fontFamily = Mono, fontSize = 13.sp, color = if (done) W.Ink3 else W.Orange, modifier = Modifier.width(32.dp).padding(top = 2.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Jost, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = if (done) W.Ink3 else W.Ink)
            if (!done) {
                Text(hint, fontFamily = Jost, fontSize = 13.sp, color = W.Ink2)
                Spacer(Modifier.height(10.dp))
                Pill("Turn on", onClick, height = 38.dp, style = PillStyle.Orange)
            }
        }
        if (done) CapsText("On", color = W.Ink)
    }
}
