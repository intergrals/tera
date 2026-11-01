package com.tera.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class,
        CategoryGroupEntity::class,
        CategoryEntity::class,
        TargetSnoozeEntity::class,
        AllocationEntity::class,
        TransactionEntity::class,
        ScheduledEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun targetSnoozeDao(): TargetSnoozeDao
    abstract fun allocationDao(): AllocationDao
    abstract fun transactionDao(): TransactionDao
    abstract fun scheduledDao(): ScheduledDao

    companion object {
        private const val NAME = "tera.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
