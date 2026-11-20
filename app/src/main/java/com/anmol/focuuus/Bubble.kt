package com.anmol.focuuus

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs

/**
 * The floating timer over a limited app: time used today and time left. Drag to move; tap to switch
 * between the full and the compact (time left only) look. An accessibility overlay, so it needs no
 * "draw over other apps" permission.
 */
class Bubble(private val service: FocusService) {
    private companion object {
        const val BLACK = 0xFF0B0B0B.toInt()
        const val PRINT = 0xFFEFEBE3.toInt()
        const val RIM = 0xFF3A3936.toInt()
        const val ORANGE = 0xFFFF5B14.toInt()
    }

    private val wm = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var compact = false
    private var lastUsed = 0L
    private var lastLeft: Long? = null
    private val dp = service.resources.displayMetrics.density

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val v = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = ResourcesCompat.getFont(service, R.font.mono) ?: Typeface.MONOSPACE
            compoundDrawablePadding = (8 * dp).toInt()
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            elevation = 8 * dp
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > 8 * dp || abs(dy) > 8 * dp) moved = true
                    if (moved) {
                        params.x = (startX + dx).toInt().coerceAtLeast(0)
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        wm.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) { Store.bubbleX = params.x; Store.bubbleY = params.y }
                    else { compact = !compact; render() }
                }
            }
            true
        }
        params.x = Store.bubbleX
        params.y = Store.bubbleY
        runCatching { wm.addView(v, params) }.onSuccess { view = v; render() }
    }

    fun update(used: Long, left: Long?) {
        lastUsed = used; lastLeft = left
        render()
    }

    private fun render() {
        val v = view ?: return
        val left = lastLeft
        val over = left != null && left <= 0
        v.text = when {
            left == null -> formatDuration(lastUsed) + " today"
            over -> if (compact) "0:00" else "${formatDuration(lastUsed)} · time's up"
            compact -> formatLeft(left)
            else -> "${formatLeft(left)} left · ${formatDuration(lastUsed)}"
        }
        // Black pill with an orange lume dot, like a dial; orange in the last minute; orange print once time's up.
        val (bg, fg, lume) = when {
            over -> Triple(BLACK, ORANGE, ORANGE)
            left != null && left < 60_000 -> Triple(ORANGE, BLACK, BLACK)
            else -> Triple(BLACK, PRINT, ORANGE)
        }
        val rim = if (over) ORANGE else RIM
        v.setTextColor(fg)
        v.background = GradientDrawable().apply {
            cornerRadius = 100 * dp
            setColor(bg)
            setStroke(dp.toInt().coerceAtLeast(1), rim)
        }
        v.setCompoundDrawablesRelative(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(lume)
            setBounds(0, 0, (8 * dp).toInt(), (8 * dp).toInt())
        }, null, null, null)
    }

    /** Time left, counting down in seconds over the last minute and in m:ss below ten minutes. */
    private fun formatLeft(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s < 600) "%d:%02d".format(s / 60, s % 60) else formatDuration(ms)
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
