package com.anmol.focuuus

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.util.UUID

/**
 * Blocks [apps] from [start] to [end] (minutes since midnight) on [days] (1 = Monday … 7 = Sunday).
 * An end at or before the start runs past midnight; start == end means the whole day.
 */
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val start: Int = 9 * 60,
    val end: Int = 17 * 60,
    val days: Set<Int> = setOf(1, 2, 3, 4, 5),
    val apps: Set<String> = emptySet(),
    val enabled: Boolean = true,
) {
    fun isActive(now: LocalDateTime = LocalDateTime.now()): Boolean {
        if (!enabled || apps.isEmpty() || days.isEmpty()) return false
        val t = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.dayOfWeek.minus(1).value
        return when {
            start == end -> today in days
            start < end -> today in days && t >= start && t < end
            else -> (today in days && t >= start) || (yesterday in days && t < end)
        }
    }

    fun blocks(pkg: String, now: LocalDateTime = LocalDateTime.now()) = pkg in apps && isActive(now)
}

/**
 * Limits on one app. A zero minute or second value means that limit is off. With [askDuration], the
 * waiting screen asks how long this visit will be; otherwise each visit lasts [sessionMinutes].
 */
data class AppLimit(
    val pkg: String,
    val dailyMinutes: Int = 30,
    val sessionMinutes: Int = 0,
    val delaySeconds: Int = 10,
    val askDuration: Boolean = true,
    val bubble: Boolean = true,
    val autoClose: Boolean = true,
)

fun formatClock(minutes: Int): String = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
fun dayLetter(day: Int) = dayLetters[day - 1]

fun formatDays(days: Set<Int>): String = when (days) {
    (1..7).toSet() -> "Every day"
    (1..5).toSet() -> "Weekdays"
    setOf(6, 7) -> "Weekends"
    emptySet<Int>() -> "No days"
    else -> days.sorted().joinToString(" ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercase) }
}

/** "45s", "12m", "1h 5m". */
fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        else -> if (s / 60 % 60 == 0L) "${s / 3600}h" else "${s / 3600}h ${s / 60 % 60}m"
    }
}

fun formatMinutes(minutes: Int) = formatDuration(minutes * 60_000L)

/** Let through the waiting screen for [minutes] (0 = no session limit); [startedAt] once the visit began. */
data class Pass(val minutes: Int, val startedAt: Long = 0L)

/** The visit lengths offered on the waiting screen, none longer than what's left today. */
fun sessionChoices(limit: AppLimit, usedMs: Long): List<Int> {
    val base = listOf(5, 10, 15, 30, 60)
    if (limit.dailyMinutes <= 0) return base
    val left = ((limit.dailyMinutes * 60_000L - usedMs) / 60_000L).toInt()
    if (left <= 0) return emptyList()
    return base.filter { it < left } + left
}

/** Whether [limit] puts a waiting screen in front of the app. */
fun AppLimit.waits() = delaySeconds > 0 || askDuration
