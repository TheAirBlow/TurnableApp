package ru.theairblow.turnable.ui.util

import kotlin.math.abs
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

fun formatBytes(bytes: Long): String {
    if (abs(bytes) < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (abs(value) >= 1024 && unit < units.lastIndex) {
        value /= 1024; unit++
    }
    val rounded = if (value >= 100) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
    return "$rounded ${units[unit]}"
}

fun nowUnixSeconds(): Long = Clock.System.now().epochSeconds

/** "3d 4h", "12m 5s", ... for a span in seconds. */
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60; val sec = s % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${sec}s"
        else -> "${sec}s"
    }
}

/** "5m ago" style relative time from a unix-seconds timestamp; "never" for 0. */
fun formatAgo(unixSeconds: Long): String =
    if (unixSeconds <= 0) "never" else formatDuration(nowUnixSeconds() - unixSeconds) + " ago"

/** Local HH:mm:ss.SSS from unix nanoseconds. */
fun formatClock(unixNanos: Long): String {
    val t = Instant.fromEpochMilliseconds(unixNanos / 1_000_000).toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(v: Int) = v.toString().padStart(2, '0')
    return "${two(t.hour)}:${two(t.minute)}:${two(t.second)}.${(t.nanosecond / 1_000_000).toString().padStart(3, '0')}"
}

/** First 8 characters of an id, for compact display. */
fun String.shortId(): String = if (length > 8) take(8) else this
