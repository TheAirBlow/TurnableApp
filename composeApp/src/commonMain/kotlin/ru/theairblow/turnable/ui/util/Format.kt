package ru.theairblow.turnable.ui.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import turnable.composeapp.generated.resources.*

@Composable
fun formatBytes(bytes: Long): String {
    if (abs(bytes) < 1024) return stringResource(Res.string.unit_byte, bytes.toString())
    val units = listOf(Res.string.unit_kb, Res.string.unit_mb, Res.string.unit_gb, Res.string.unit_tb)
    var value = bytes / 1024.0
    var unit = 0
    while (abs(value) >= 1024 && unit < units.lastIndex) {
        value /= 1024; unit++
    }
    val rounded = if (value >= 100) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
    return stringResource(units[unit], rounded)
}

fun nowUnixSeconds(): Long = Clock.System.now().epochSeconds

/** "3d 4h", "12m 5s", ... for a span in seconds. */
@Composable
fun formatDuration(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    val d = s / 86400; val h = s % 86400 / 3600; val m = s % 3600 / 60; val sec = s % 60
    return when {
        d > 0 -> stringResource(Res.string.duration_days_hours, d, h)
        h > 0 -> stringResource(Res.string.duration_hours_minutes, h, m)
        m > 0 -> stringResource(Res.string.duration_minutes_seconds, m, sec)
        else -> stringResource(Res.string.duration_seconds, sec)
    }
}

/** "5m ago" style relative time from a unix-seconds timestamp; "never" for 0. */
@Composable
fun formatAgo(unixSeconds: Long): String =
    if (unixSeconds <= 0) stringResource(Res.string.time_never)
    else stringResource(Res.string.time_ago, formatDuration(nowUnixSeconds() - unixSeconds))

/** Local HH:mm:ss.SSS from unix nanoseconds. */
fun formatClock(unixNanos: Long): String {
    val t = Instant.fromEpochMilliseconds(unixNanos / 1_000_000).toLocalDateTime(TimeZone.currentSystemDefault())
    fun two(v: Int) = v.toString().padStart(2, '0')
    return "${two(t.hour)}:${two(t.minute)}:${two(t.second)}.${(t.nanosecond / 1_000_000).toString().padStart(3, '0')}"
}

/** First 8 characters of an id, for compact display. */
fun String.shortId(): String = if (length > 8) take(8) else this
