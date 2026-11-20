package com.anmol.focuuus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.AppInfo
import com.anmol.focuuus.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists launchable apps with a search line. With [onPick], tapping an app picks it; otherwise apps are
 * ticked and [onDone] gets the ticked set.
 */
@Composable
fun AppPickerScreen(
    title: String,
    selected: Set<String> = emptySet(),
    exclude: Set<String> = emptySet(),
    onPick: ((String) -> Unit)? = null,
    onDone: (Set<String>) -> Unit = {},
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var ticked by remember { mutableStateOf(selected) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { Apps.launchable(context) } }

    Column(Modifier.fillMaxSize().background(W.Paper).imePadding()) {
        TopBar(title, onBack) {
            if (onPick == null) Pill("Done · ${ticked.size}", { onDone(ticked) }, style = PillStyle.Orange, height = 40.dp)
        }
        // Search on a hairline, no box.
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (query.isEmpty()) Text("Search apps", fontFamily = Jost, fontSize = 20.sp, color = W.Ink3)
            BasicTextField(
                query, { query = it }, singleLine = true,
                textStyle = TextStyle(fontFamily = Jost, fontSize = 20.sp, color = W.Ink),
                cursorBrush = SolidColor(W.Orange), modifier = Modifier.fillMaxWidth(),
            )
        }
        Hairline(Modifier.padding(horizontal = 24.dp))
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CapsText("Loading apps") }
            return@Column
        }
        val shown = list.filter { it.pkg !in exclude && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
            // Ticked apps first, so the current choice is easy to review.
            .sortedByDescending { onPick == null && it.pkg in selected }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.pkg }) { app ->
                val on = app.pkg in ticked
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { if (onPick != null) onPick(app.pkg) else ticked = if (on) ticked - app.pkg else ticked + app.pkg }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.pkg, 36.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, Modifier.weight(1f), fontFamily = Jost, fontSize = 17.sp, color = W.Ink)
                    if (onPick == null) {
                        // A small dial-style marker: hollow when off, orange with an ink centre when on.
                        Box(
                            Modifier.size(22.dp).clip(CircleShape).background(if (on) W.Orange else W.Paper)
                                .border(1.dp, if (on) W.Orange else W.Ink3, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) Box(Modifier.size(8.dp).clip(CircleShape).background(W.Black)) }
                    } else Text("›", fontFamily = Jost, fontSize = 24.sp, color = W.Ink3)
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}
