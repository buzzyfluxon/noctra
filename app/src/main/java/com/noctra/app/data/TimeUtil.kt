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

    fun duration(min: Long): String = when {
        min < 60 -> "${min}m"
        min % 60 == 0L -> "${min / 60}h"
        else -> "${min / 60}h ${min % 60}m"
    }

    fun startOfDay(date: String): Long =
        LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli()
}
