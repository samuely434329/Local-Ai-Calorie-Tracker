package com.imagecaltracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FoodEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class CalorieDatabase : RoomDatabase() {

    abstract fun foodEntryDao(): FoodEntryDao

    companion object {
        @Volatile
        private var instance: CalorieDatabase? = null

        fun get(context: Context): CalorieDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalorieDatabase::class.java,
                    "calorie-tracker.db",
                ).build().also { instance = it }
            }
    }
}
