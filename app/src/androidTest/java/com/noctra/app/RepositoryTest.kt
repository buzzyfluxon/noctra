package com.noctra.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.noctra.app.data.AppDatabase
import com.noctra.app.data.CheckInInput
import com.noctra.app.data.CheckInResponse
import com.noctra.app.data.NoctraRepository
import com.noctra.app.data.SettingsStore
import com.noctra.app.data.TaskEntity
import com.noctra.app.data.TaskStatus
import com.noctra.app.data.TimeUtil
import com.noctra.app.notify.Scheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: NoctraRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = NoctraRepository(ctx, db, SettingsStore(ctx), Scheduler(ctx))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(title: String) = TaskEntity(title = title, planDate = TimeUtil.todayStr())

    @Test
    fun taskCreateEditPersist() = runBlocking {
        val id = repo.saveTask(task("Laws of Motion"), emptyList())
        val saved = repo.getTask(id)!!
        repo.saveTask(saved.copy(title = "Laws of Motion 2"), emptyList())
        assertEquals("Laws of Motion 2", repo.getTask(id)!!.title)
    }

    @Test
    fun wakeConfirmationIsNotDuplicated() = runBlocking {
        assertTrue(repo.confirmWake())
        assertFalse(repo.confirmWake())
        val session = repo.openSession().first()
        assertNotNull(session?.wakeAt)
        assertNotNull(session?.nextCheckInAt)
    }

    @Test
    fun checkInDoesNotCompleteTaskWithoutConfirmation() = runBlocking {
        val id = repo.saveTask(task("Organic Chemistry"), emptyList())
        repo.startTask(id)
        repo.confirmWake()
        repo.respond(0, CheckInInput(CheckInResponse.COMPLETED, confirmComplete = false))
        assertEquals(TaskStatus.IN_PROGRESS, repo.getTask(id)!!.status)
        repo.respond(0, CheckInInput(CheckInResponse.COMPLETED, confirmComplete = true))
        assertEquals(TaskStatus.COMPLETED, repo.getTask(id)!!.status)
    }

    @Test
    fun breakStartAndEndAreRecorded() = runBlocking {
        repo.confirmWake()
        repo.startBreak()
        val s = repo.openSession().first()!!
        assertNull(s.nextCheckInAt)
        repo.endBreak()
        val after = repo.openSession().first()!!
        assertNotNull(after.nextCheckInAt)
        assertTrue(repo.logs(after.id).first().all { it.endAt != null })
    }

    @Test
    fun finalizeDaySavesReportAndClosesSession() = runBlocking {
        repo.confirmWake()
        val sessionId = repo.openSession().first()!!.id
        repo.finalizeDay(sessionId)
        assertNull(repo.openSession().first())
        assertEquals(1, repo.reports().first().size)
    }

    @Test
    fun postponeMovesTaskAndCountsIt() = runBlocking {
        val id = repo.saveTask(task("Biology NCERT"), emptyList())
        repo.postpone(id)
        val t = repo.getTask(id)!!
        assertEquals(TaskStatus.POSTPONED, t.status)
        assertEquals(1, t.postponeCount)
        assertEquals(TimeUtil.tomorrowStr(), t.planDate)
    }

    @Test
    fun pastStudyIsLoggedOnItsOwnDayAndCanBeDeleted() = runBlocking {
        val day = java.time.LocalDate.now().minusDays(3)
        assertTrue(repo.logStudy(day, 150, "physics", "mechanics"))
        assertTrue(repo.logStudy(day, 30, "Chemistry"))
        val logs = repo.manualStudy().first()
        assertEquals(2, logs.size)
        assertEquals(180L, logs.sumOf { (it.endAt!! - it.startAt) / 60_000L })
        assertTrue(logs.all { TimeUtil.dateOf(it.startAt) == day })
        assertTrue(logs.any { it.subject == "Physics" })
        repo.deleteManualStudy(logs.first().id)
        assertEquals(1, repo.manualStudy().first().size)
        assertNull(repo.openSessionOnce())
    }

    @Test
    fun futureStudyIsRejected() = runBlocking {
        assertFalse(repo.logStudy(java.time.LocalDate.now().plusDays(1), 60, "Physics"))
        assertTrue(repo.manualStudy().first().isEmpty())
    }

    @Test
    fun nocChatHistoryIsSavedInOrderAndCleared() = runBlocking {
        repo.saveNocMessage("USER", "hi")
        repo.saveNocMessage("NOC", "hello")
        assertEquals(listOf("hi", "hello"), repo.nocMessages().map { it.text })
        repo.clearNocMessages()
        assertTrue(repo.nocMessages().isEmpty())
    }
}
