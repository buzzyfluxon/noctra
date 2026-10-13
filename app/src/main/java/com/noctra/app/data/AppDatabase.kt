package com.noctra.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

object Migrations {
    val ALL: Array<Migration> = emptyArray()
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
    version = 1,
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
