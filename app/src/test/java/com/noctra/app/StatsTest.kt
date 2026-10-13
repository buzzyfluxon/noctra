package com.noctra.app

import com.noctra.app.data.ActivityKind
import com.noctra.app.data.ActivityLog
import com.noctra.app.data.Stats
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TimeUtil
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {
    private val min = 60_000L

    private fun log(kind: ActivityKind, start: Long, minutes: Long?) =
        ActivityLog(sessionId = 1, kind = kind, startAt = start, endAt = minutes?.let { start + it * min })

    @Test
    fun separatesStudyBreakDistractionAndUnclassified() {
        val wake = 1_000_000L
        val logs = listOf(
            log(ActivityKind.STUDY, wake, 40),
            log(ActivityKind.DISTRACTION, wake + 40 * min, 10),
            log(ActivityKind.BREAK, wake + 50 * min, 15)
        )
        val s = Stats.day(wake, null, wake + 120 * min, logs)
        assertEquals(120, s.elapsedMin)
        assertEquals(40, s.studyMin)
        assertEquals(10, s.distractionMin)
        assertEquals(15, s.breakMin)
        assertEquals(55, s.unclassifiedMin)
    }

    @Test
    fun openBreakCountsUntilNow() {
        val wake = 0L
        val s = Stats.day(wake, null, 90 * min, listOf(log(ActivityKind.BREAK, 60 * min, null)))
        assertEquals(30, s.breakMin)
        assertEquals(60, s.unclassifiedMin)
    }

    @Test
    fun noWakeMeansNoStats() {
        val s = Stats.day(null, null, 100 * min, emptyList())
        assertEquals(0, s.elapsedMin)
        assertEquals(0, s.unclassifiedMin)
    }

    @Test
    fun unclassifiedNeverNegative() {
        val s = Stats.day(0L, 30 * min, 30 * min, listOf(log(ActivityKind.STUDY, 0, 45)))
        assertEquals(0, s.unclassifiedMin)
    }

    @Test
    fun elapsedSurvivesMidnight() {
        val zone = ZoneId.systemDefault()
        val wake = ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 13, 1, 15, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(105, Stats.day(wake, end, end, emptyList()).elapsedMin)
    }

    @Test
    fun tallyCountsCompletionsSinceWakeAndOpenTasks() {
        val tasks = listOf(
            TaskEntity(id = 1, title = "a", planDate = "2026-10-13", status = TaskStatus.COMPLETED, completedAt = 500),
            TaskEntity(id = 2, title = "b", planDate = "2026-10-13", status = TaskStatus.COMPLETED, completedAt = 50),
            TaskEntity(id = 3, title = "c", planDate = "2026-10-13"),
            TaskEntity(id = 4, title = "d", planDate = "2026-10-20")
        )
        val t = Stats.tally(tasks, "2026-10-13", since = 100)
        assertEquals(1, t.completed)
        assertEquals(2, t.planned)
    }

    @Test
    fun nextOccurrenceRollsToTomorrowWhenPassed() {
        val zone = ZoneId.systemDefault()
        val after = ZonedDateTime.of(2026, 10, 13, 10, 0, 1, 0, zone).toInstant().toEpochMilli()
        val next = TimeUtil.nextOccurrence(10, 0, after)
        val expected = ZonedDateTime.of(2026, 10, 14, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, next)
        assertTrue(next > after)
    }

    @Test
    fun nextOccurrenceUsesTodayWhenUpcoming() {
        val zone = ZoneId.systemDefault()
        val after = ZonedDateTime.of(2026, 10, 13, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 10, 13, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, TimeUtil.nextOccurrence(10, 0, after))
    }

    @Test
    fun durationFormatting() {
        assertEquals("45m", TimeUtil.duration(45))
        assertEquals("2h", TimeUtil.duration(120))
        assertEquals("1h 30m", TimeUtil.duration(90))
    }
}
