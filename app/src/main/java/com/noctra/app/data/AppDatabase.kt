package com.noctra.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN runningSince INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN workedMs INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN pausedAt INTEGER")
            db.execSQL("ALTER TABLE tasks ADD COLUMN pauseReason TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE activity_logs ADD COLUMN subject TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `noc_messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`role` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
        }
    }

    // Biology was split into Botany and Zoology. Old Biology tasks keep their name as a custom subject.
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE tasks SET subject = 'CUSTOM', customSubject = 'Biology' WHERE subject = 'BIOLOGY'")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}

@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        DaySession::class,
        ActivityLog::class,
        CheckIn::class,
        DailyReport::class,
        NocMessageEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun sessions(): SessionDao
    abstract fun noc(): NocDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "noctra.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
