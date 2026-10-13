package com.noctra.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtil {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val clockFmt: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun todayStr(): String = LocalDate.now(zone).toString()

    fun tomorrowStr(): String = LocalDate.now(zone).plusDays(1).toString()

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    fun clock(ms: Long): String = clockFmt.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun clockOf(hour: Int, minute: Int): String =
        clockFmt.format(LocalDate.now(zone).atTime(hour, minute))

    fun nextOccurrence(hour: Int, minute: Int, after: Long): Long {
        var t = Instant.ofEpochMilli(after).atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone)
        if (t.toInstant().toEpochMilli() <= after) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun lastOccurrence(hour: Int, minute: Int, now: Long): Long {
        var t = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atTime(hour, minute).atZone(zone)
        if (t.toInstant().toEpochMilli() > now) t = t.minusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun duration(min: Long): String = when {
        min < 60 -> "${min}m"
        min % 60 == 0L -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    fun clockDuration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun startOfDay(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
}
