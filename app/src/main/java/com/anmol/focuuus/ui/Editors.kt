package com.anmol.focuuus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.AppLimit
import com.anmol.focuuus.Apps
import com.anmol.focuuus.Routine
import com.anmol.focuuus.Store
import com.anmol.focuuus.dayLetter
import com.anmol.focuuus.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun EditorScaffold(
    title: String, canSave: Boolean, onSave: () -> Unit, onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(W.Paper).imePadding()) {
        TopBar(title, onBack) { Pill("Save", onSave, enabled = canSave, style = PillStyle.Orange, height = 40.dp) }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding(),
            content = content,
        )
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier) {
    Spacer(Modifier.height(28.dp))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        CapsText(title)
        Spacer(Modifier.width(12.dp))
        Hairline(Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
fun RoutineEditor(id: String?, onDone: () -> Unit) {
    val existing = remember(id) { Store.routines.value.firstOrNull { it.id == id } }
    var r by remember { mutableStateOf(existing ?: Routine()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    if (picking) {
        AppPickerScreen(
            title = "Apps to block", selected = r.apps,
            onDone = { r = r.copy(apps = it); picking = false },
            onBack = { picking = false },
        )
        return
    }

    EditorScaffold(
        title = if (existing == null) "New routine" else "Routine",
        canSave = r.apps.isNotEmpty() && r.days.isNotEmpty(),
        onSave = { Store.saveRoutine(r.copy(name = r.name.trim())); onDone() },
        onBack = onDone,
    ) {
        // Name, set large like the model name on a dial.
        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (r.name.isEmpty()) Text("Deep work", fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 36.sp, color = W.Ink3)
            BasicTextField(
                value = r.name, onValueChange = { r = r.copy(name = it) }, singleLine = true,
                textStyle = TextStyle(fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 36.sp, color = W.Ink),
                cursorBrush = SolidColor(W.Orange),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Hairline(Modifier.padding(top = 6.dp))

        Section("When · drag the handles")
        RangeDial24(r.start, r.end, { s, e -> r = r.copy(start = s, end = e) }, Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (1..7).forEach { day ->
                val on = day in r.days
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (on) W.Orange else W.Paper)
                        .border(1.dp, if (on) W.Orange else W.Ink3, CircleShape)
                        .clickable { r = r.copy(days = if (on) r.days - day else r.days + day) },
                    contentAlignment = Alignment.Center,
                ) { Text(dayLetter(day), style = Caps.copy(fontSize = 13.sp, letterSpacing = 0.sp), color = if (on) W.Black else W.Ink2) }
            }
        }

        Section(if (r.apps.isEmpty()) "Blocked apps" else "Blocked apps · ${r.apps.size}")
        r.apps.sortedBy { Apps.label(context, it).lowercase() }.forEach { pkg ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(pkg, 32.dp)
                Spacer(Modifier.width(16.dp))
                Text(Apps.label(context, pkg), Modifier.weight(1f), fontFamily = Jost, fontSize = 17.sp, color = W.Ink)
                Box(Modifier.size(36.dp).clip(CircleShape).clickable { r = r.copy(apps = r.apps - pkg) }, contentAlignment = Alignment.Center) {
                    Text("×", fontFamily = Jost, fontSize = 22.sp, color = W.Ink2)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Pill(if (r.apps.isEmpty()) "Choose apps" else "Change apps", { picking = true }, style = PillStyle.Outline, modifier = Modifier.fillMaxWidth())
        if (existing != null) {
            Spacer(Modifier.height(36.dp))
            DeleteLink("Delete routine") { Store.deleteRoutine(r.id); onDone() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** A value shown big above a [BezelRuler]. */
@Composable
private fun RulerSetting(
    title: String, hint: String, value: Int, unit: String, step: Int, max: Int, majorEvery: Int,
    label: (Int) -> String, onChange: (Int) -> Unit,
) {
    Section(title)
    Row(verticalAlignment = Alignment.Bottom) {
        if (value == 0) {
            Text("Off", fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 44.sp, color = W.Ink3)
        } else {
            Text(value.toString(), fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 44.sp, color = W.Ink)
            Spacer(Modifier.width(6.dp))
            CapsText(unit, Modifier.padding(bottom = 10.dp), color = W.Orange)
        }
        Spacer(Modifier.weight(1f))
        Text(hint, fontFamily = Jost, fontSize = 13.sp, color = W.Ink2, modifier = Modifier.width(150.dp).padding(bottom = 8.dp))
    }
    BezelRuler(value, step, max, majorEvery, label, onChange)
}

@Composable
fun LimitEditor(pkg: String, resumes: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val existing = remember(pkg) { Store.limitFor(pkg) }
    var l by remember { mutableStateOf(existing ?: AppLimit(pkg)) }
    var used by remember { mutableLongStateOf(0L) }
    LaunchedEffect(resumes) { used = withContext(Dispatchers.IO) { Apps.usageToday(context, pkg) } }

    EditorScaffold(
        title = if (existing == null) "New limit" else "Limit",
        canSave = true,
        onSave = { Store.saveLimit(l); onDone() },
        onBack = onDone,
    ) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(remember(pkg) { Apps.label(context, pkg) }, fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 32.sp, color = W.Ink)
                Text("${formatDuration(used)} used today", fontFamily = Mono, fontSize = 13.sp, color = W.Ink2)
            }
        }

        RulerSetting(
            "Daily limit", "Total time per day", l.dailyMinutes, "min", step = 5, max = 240, majorEvery = 6,
            label = { if (it == 0) "Off" else if (it % 60 == 0) "${it / 60}h" else if (it > 60) "${it / 60}h${it % 60}" else "${it}m" },
        ) { l = l.copy(dailyMinutes = it) }

        RulerSetting(
            "Wait before opening", "A pause to change your mind", l.delaySeconds, "sec", step = 5, max = 60, majorEvery = 3,
            label = { if (it == 0) "Off" else "${it}s" },
        ) { l = l.copy(delaySeconds = it) }

        Section("Each visit")
        ToggleRow("Ask how long", "Choose 5, 10, 15 or 30 minutes on the waiting screen every time you open it", l.askDuration) {
            l = l.copy(askDuration = it)
        }
        if (!l.askDuration) {
            RulerSetting(
                "Visit length", "Each open lasts this long", l.sessionMinutes, "min", step = 5, max = 120, majorEvery = 3,
                label = { if (it == 0) "Off" else "${it}m" },
            ) { l = l.copy(sessionMinutes = it) }
        }

        Section("When time runs out")
        ToggleRow("Floating timer", "Time used and left, over the app. Drag to move, tap to shrink", l.bubble) { l = l.copy(bubble = it) }
        Hairline()
        ToggleRow("Auto-close", "Close the app when time's up, rather than only remind you", l.autoClose) { l = l.copy(autoClose = it) }

        if (existing != null) {
            Spacer(Modifier.height(36.dp))
            DeleteLink("Remove limit") { Store.deleteLimit(pkg); onDone() }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun DeleteLink(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
        CapsText(text, color = W.Orange)
    }
}
