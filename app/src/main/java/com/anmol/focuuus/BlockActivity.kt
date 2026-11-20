package com.anmol.focuuus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anmol.focuuus.ui.AppIcon
import com.anmol.focuuus.ui.Caps
import com.anmol.focuuus.ui.CapsText
import com.anmol.focuuus.ui.CountdownDial
import com.anmol.focuuus.ui.FocuuusTheme
import com.anmol.focuuus.ui.Jost
import com.anmol.focuuus.ui.Mono
import com.anmol.focuuus.ui.Pill
import com.anmol.focuuus.ui.PillStyle
import com.anmol.focuuus.ui.W
import kotlinx.coroutines.delay

/**
 * Covers an app that is blocked by a routine or out of time, or makes it wait: a countdown, then a
 * choice of how long to use it.
 */
class BlockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_DELAY = "delay"
        private const val EXTRA_ASK = "ask"
        private const val EXTRA_CHOICES = "choices"
        private const val EXTRA_LEFT = "left"

        private const val KIND_BLOCK = 0
        private const val KIND_WAIT = 1

        private fun intent(context: Context, pkg: String, kind: Int, title: String, text: String) =
            Intent(context, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)

        fun routine(context: Context, pkg: String, r: Routine): Intent {
            val name = r.name.ifBlank { "your routine" }
            val until = if (r.start == r.end) "for the rest of the day" else "until ${formatClock(r.end)}"
            return intent(context, pkg, KIND_BLOCK, "Locked", "${Apps.label(context, pkg)} is blocked $until by $name.")
        }

        fun dailyLimit(context: Context, pkg: String, l: AppLimit, used: Long) = intent(
            context, pkg, KIND_BLOCK, "Wound down",
            "${formatDuration(used)} of ${Apps.label(context, pkg)} today, your limit is ${formatMinutes(l.dailyMinutes)}. It resets at midnight.",
        ).putExtra(EXTRA_LEFT, 0f)

        fun sessionOver(context: Context, pkg: String, minutes: Int, used: Long) = intent(
            context, pkg, KIND_BLOCK, "Time's up",
            "That was your ${formatMinutes(minutes)} of ${Apps.label(context, pkg)}. ${formatDuration(used)} used today.",
        ).putExtra(EXTRA_LEFT, 0f)

        fun waiting(context: Context, pkg: String, l: AppLimit, used: Long): Intent {
            val today = if (l.dailyMinutes > 0) "${formatDuration(used)} of ${formatMinutes(l.dailyMinutes)} used today."
            else "${formatDuration(used)} used today."
            return intent(context, pkg, KIND_WAIT, "Breathe", "Do you need ${Apps.label(context, pkg)} right now? $today")
                .putExtra(EXTRA_DELAY, l.delaySeconds)
                .putExtra(EXTRA_ASK, l.askDuration)
                .putExtra(EXTRA_CHOICES, sessionChoices(l, used).toIntArray())
                .putExtra(EXTRA_LEFT, if (l.dailyMinutes > 0) ((l.dailyMinutes * 60_000L - used) / 60_000L).toInt() else -1)
        }
    }

    private var current by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bars = SystemBarStyle.dark(W.Paper.toArgb())
        enableEdgeToEdge(bars, bars)
        current = intent
        setContent {
            FocuuusTheme {
                current?.let { i ->
                    // Keyed so a new block (another app) restarts the countdown.
                    key(i) { Screen(i) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        current = intent
    }

    private fun open(minutes: Int) {
        current?.getStringExtra(EXTRA_PKG)?.let { FocusService.onBlockChoice(it, open = true, minutes = minutes) }
        finish()
    }

    private fun leave() {
        current?.getStringExtra(EXTRA_PKG)?.let { FocusService.onBlockChoice(it, open = false) }
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Composable
    private fun Screen(i: Intent) {
        val pkg = i.getStringExtra(EXTRA_PKG) ?: ""
        val waiting = i.getIntExtra(EXTRA_KIND, KIND_BLOCK) == KIND_WAIT
        val delaySeconds = i.getIntExtra(EXTRA_DELAY, 0)
        val ask = i.getBooleanExtra(EXTRA_ASK, false)
        val choices = i.getIntArrayExtra(EXTRA_CHOICES)?.toList() ?: emptyList()
        val left = i.getIntExtra(EXTRA_LEFT, -1)
        val context = LocalContext.current
        BackHandler(onBack = ::leave)

        var secondsLeft by remember { mutableIntStateOf(if (waiting) delaySeconds else 0) }
        LaunchedEffect(Unit) { while (secondsLeft > 0) { delay(1000); secondsLeft-- } }
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        val sweep by animateFloatAsState(
            targetValue = if (started || !waiting) 1f else 0f,
            animationSpec = tween(if (waiting) delaySeconds * 1000 else 0, easing = LinearEasing),
        )
        val ready = secondsLeft == 0

        Column(
            Modifier.fillMaxSize().background(W.Paper).safeDrawingPadding().padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CapsText("Focuuus", color = W.Ink)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                // Waiting: the sweep fills as the seconds pass. Blocked: the dial is spent (all orange) or locked (blank).
                CountdownDial(if (waiting) sweep else if (i.hasExtra(EXTRA_LEFT)) 1f else 0f, Modifier.size(240.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIcon(pkg, 56.dp)
                    Spacer(Modifier.height(10.dp))
                    when {
                        waiting && !ready -> Text(secondsLeft.toString(), fontFamily = Mono, fontSize = 22.sp, color = W.Orange)
                        waiting -> CapsText("Ready", color = W.Orange)
                        else -> CapsText(if (i.hasExtra(EXTRA_LEFT)) "0 left" else "Locked", color = W.Orange)
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(i.getStringExtra(EXTRA_TITLE) ?: "", fontFamily = Jost, fontWeight = FontWeight.Light, fontSize = 40.sp, color = W.Ink)
            Spacer(Modifier.height(8.dp))
            Text(
                i.getStringExtra(EXTRA_TEXT) ?: "", fontFamily = Jost, fontSize = 16.sp,
                textAlign = TextAlign.Center, color = W.Ink2,
            )
            Spacer(Modifier.weight(1f))

            if (waiting) {
                if (ask && choices.isNotEmpty()) {
                    CapsText(if (ready) "How long?" else "How long? · in $secondsLeft", color = if (ready) W.Ink else W.Ink3)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        choices.forEach { m ->
                            DurationButton(m, isRest = m == left, enabled = ready) { open(m) }
                        }
                    }
                } else {
                    // No choice to make (or under a minute left today): just open.
                    Pill(
                        if (ready) "Open ${Apps.label(context, pkg)}" else "Open in $secondsLeft",
                        { open(0) }, enabled = ready, style = PillStyle.Outline, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
            Pill(if (waiting) "Not now" else "Close app", ::leave, modifier = Modifier.fillMaxWidth())
        }
    }

    /** A round "complication" with a number of minutes. The last one, everything left today, is orange. */
    @Composable
    private fun DurationButton(minutes: Int, isRest: Boolean, enabled: Boolean, onClick: () -> Unit) {
        val border = when {
            !enabled -> W.Hair
            isRest -> W.Orange
            else -> W.Ink
        }
        Column(
            Modifier.size(60.dp).clip(CircleShape).border(1.dp, border, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Text(minutes.toString(), fontFamily = Jost, fontSize = 20.sp, color = if (enabled) W.Ink else W.Ink3)
            Text(if (isRest) "ALL" else "MIN", style = Caps.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = if (enabled && isRest) W.Orange else W.Ink3)
        }
    }
}
