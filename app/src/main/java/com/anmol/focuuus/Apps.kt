package com.anmol.focuuus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Process
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import java.time.LocalDate
import java.time.ZoneId

data class AppInfo(val pkg: String, val label: String)

/** Installed apps, their names and icons, and how long each was used today. */
object Apps {
    private val labels = HashMap<String, String>()
    private val icons = HashMap<String, Bitmap>()

    /** Launchable apps other than Focuuus, sorted by name. Slow: call off the main thread. */
    fun launchable(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { AppInfo(it, label(context, it)) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = synchronized(labels) {
        labels.getOrPut(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            }.getOrDefault(pkg)
        }
    }

    fun icon(context: Context, pkg: String): Bitmap? = synchronized(icons) {
        icons[pkg] ?: runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96)
        }.getOrNull()?.also { icons[pkg] = it }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    fun isServiceEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(context, FocusService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Foreground time today per package, counting an app that is open right now up to this moment. */
    fun usageToday(context: Context, only: Set<String>? = null): Map<String, Long> {
        if (!hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(dayStart, now)
        val total = HashMap<String, Long>()
        val resumed = HashMap<String, MutableSet<String>>() // package -> its resumed activities
        val since = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            if (only != null && pkg !in only) continue
            val open = resumed.getOrPut(pkg) { mutableSetOf() }
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (open.isEmpty()) since[pkg] = e.timeStamp
                    open += e.className ?: ""
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val wasOpen = open.isNotEmpty()
                    open -= e.className ?: ""
                    if (wasOpen && open.isEmpty()) {
                        total[pkg] = (total[pkg] ?: 0) + (e.timeStamp - (since[pkg] ?: e.timeStamp))
                    } else if (!wasOpen && e.eventType == UsageEvents.Event.ACTIVITY_PAUSED && pkg !in since) {
                        // Opened before midnight and still open then: count from the start of the day.
                        total[pkg] = (total[pkg] ?: 0) + (e.timeStamp - dayStart)
                        since[pkg] = e.timeStamp
                    }
                }
                UsageEvents.Event.DEVICE_SHUTDOWN -> {
                    resumed.forEach { (p, set) ->
                        if (set.isNotEmpty()) total[p] = (total[p] ?: 0) + (e.timeStamp - (since[p] ?: e.timeStamp))
                        set.clear()
                    }
                }
            }
        }
        resumed.forEach { (p, set) -> if (set.isNotEmpty()) total[p] = (total[p] ?: 0) + (now - (since[p] ?: now)) }
        return total
    }

    fun usageToday(context: Context, pkg: String): Long = usageToday(context, setOf(pkg))[pkg] ?: 0L
}
