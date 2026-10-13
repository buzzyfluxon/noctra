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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

@Database(
    entities = [
        TaskEntity::class,
        SubtaskEntity::class,
        DaySession::class,
        ActivityLog::class,
        CheckIn::class,
        DailyReport::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun sessions(): SessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "noctra.db")
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
