package com.anmol.focuuus

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * Watches which app is in front. Blocks apps that a routine covers right now, and for apps with a limit
 * shows the waiting screen, enforces the visit and daily limits, and shows the floating timer.
 *
 * The system restarts this service if its process is killed. The visit chosen on the waiting screen is
 * saved in [Store], so a restart mid-visit carries on with the same length and start time.
 */
class FocusService : AccessibilityService() {

    companion object {
        private var instance: FocusService? = null

        /** From [BlockActivity]: the user opened the app for [minutes] (0 = no visit limit), or left it. */
        fun onBlockChoice(pkg: String, open: Boolean, minutes: Int = 0) {
            if (open) Store.grantPass(pkg, minutes) else Store.removePass(pkg)
            instance?.let { if (!open && it.current == pkg) it.current = null }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bubble: Bubble
    private var ignored = setOf<String>()

    /** The app in front (not Focuuus, the system UI or a keyboard). */
    private var current: String? = null

    /** Our block screen is over [current]: stop checking until the user is back in an app. */
    private var covered = false

    // The running visit in [current], if it has a limit and is allowed.
    private var session: AppLimit? = null
    private var visitMinutes = 0      // 0 = no visit limit
    private var visitStart = 0L       // wall clock, so it survives a restart
    private var usedBefore = 0L       // today's use when we last looked
    private var lookedAt = 0L         // wall clock of that look
    private var warned = false

    private var lastBlock: String? = null
    private var lastBlockAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            check()
            if (current != null && !covered) handler.postDelayed(this, 1000)
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    endSession()
                    current = null
                    Store.keepOnlyPass(null)
                }
                Intent.ACTION_USER_PRESENT -> lookAtScreen()
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        Store.init(this)
        bubble = Bubble(this)
        ignored = buildSet {
            add("com.android.systemui")
            add("android")
            // Permission prompts appear over the app that asked; they shouldn't end its visit.
            add("com.google.android.permissioncontroller")
            add("com.android.permissioncontroller")
            getSystemService(InputMethodManager::class.java).enabledInputMethodList.forEach { add(it.packageName) }
        }
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        // After a restart no event may come while the user stays in the same app, so look now.
        handler.postDelayed(::lookAtScreen, 500)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screen) }
        if (::bubble.isInitialized) bubble.hide()
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        onForeground(pkg, event.className?.toString())
    }

    private fun lookAtScreen() {
        rootInActiveWindow?.packageName?.toString()?.let { if (it != packageName) onForeground(it, null) }
    }

    private fun onForeground(pkg: String, className: String?) {
        if (pkg == packageName) {
            // Our own screens. The bubble's window also reports here, so only react to our activities.
            when (className) {
                BlockActivity::class.java.name -> {
                    endSession()
                    covered = true
                }
                MainActivity::class.java.name -> {
                    endSession()
                    current = null
                    Store.keepOnlyPass(null)
                }
            }
            return
        }
        if (pkg in ignored) return
        // The blocked app often reports a window again just after we cover it.
        if (pkg == lastBlock && SystemClock.elapsedRealtime() - lastBlockAt < 1500) return

        if (pkg != current) {
            endSession()
            Store.keepOnlyPass(pkg)
            current = pkg
        }
        covered = false
        if (session == null) enter(pkg)
        if (!covered) {
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 1000)
        }
    }

    /** [pkg] just came to the front: block it, make it wait, or start its visit. */
    private fun enter(pkg: String) {
        Store.blockingRoutine(pkg)?.let { block(BlockActivity.routine(this, pkg, it)); return }
        val limit = Store.limitFor(pkg) ?: return
        val now = System.currentTimeMillis()
        val used = Apps.usageToday(this, pkg)
        if (limit.autoClose && limit.dailyMinutes > 0 && used >= limit.dailyMinutes * 60_000L) {
            block(BlockActivity.dailyLimit(this, pkg, limit, used)); return
        }
        val pass = Store.pass(pkg)
        if (limit.waits() && pass == null) {
            block(BlockActivity.waiting(this, pkg, limit, used)); return
        }
        session = limit
        visitMinutes = pass?.minutes ?: limit.sessionMinutes
        visitStart = pass?.startedAt?.takeIf { it > 0 } ?: now
        if (pass != null && pass.startedAt == 0L) Store.startPass(pkg, now)
        usedBefore = used
        lookedAt = now
        warned = false
        if (limit.bubble) bubble.show()
        check()
    }

    /** Every second while an app is in front: routines starting, limits running out, the bubble. */
    private fun check() {
        val pkg = current ?: return
        Store.blockingRoutine(pkg)?.let { block(BlockActivity.routine(this, pkg, it)); return }
        // Limits may have been edited or removed while the app was open.
        val limit = Store.limitFor(pkg)
        if (limit == null) { endSession(); return }
        if (session == null) return
        session = limit

        val now = System.currentTimeMillis()
        val used = usedBefore + (now - lookedAt)
        val inVisit = now - visitStart
        val dailyLeft = if (limit.dailyMinutes > 0) limit.dailyMinutes * 60_000L - used else null
        val visitLeft = if (visitMinutes > 0) visitMinutes * 60_000L - inVisit else null
        val left = listOfNotNull(dailyLeft, visitLeft).minOrNull()

        if (left != null && left <= 0) {
            if (limit.autoClose) {
                val intent = if (dailyLeft != null && dailyLeft <= 0) BlockActivity.dailyLimit(this, pkg, limit, used)
                else BlockActivity.sessionOver(this, pkg, visitMinutes, used)
                block(intent)
                return
            }
            if (!warned) {
                warned = true
                Toast.makeText(this, "Time's up for ${Apps.label(this, pkg)}", Toast.LENGTH_LONG).show()
            }
        }
        if (limit.bubble) {
            bubble.show()
            bubble.update(used, left)
        } else bubble.hide()
    }

    private fun block(intent: Intent) {
        val pkg = intent.getStringExtra(BlockActivity.EXTRA_PKG)
        endSession()
        covered = true
        handler.removeCallbacks(tick)
        // A visit that ends needs the waiting screen again next time.
        pkg?.let(Store::removePass)
        lastBlock = pkg
        lastBlockAt = SystemClock.elapsedRealtime()
        startActivity(intent)
    }

    private fun endSession() {
        session = null
        bubble.hide()
    }
}
