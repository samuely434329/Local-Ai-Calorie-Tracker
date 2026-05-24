package com.imagecaltracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    /**
     * Streams all entries whose timestamp falls within `[startMillis, endMillis)`,
     * newest first. Used to display "today's" log.
     */
    @Query(
        """
        SELECT * FROM food_entries
        WHERE timestampMillis >= :startMillis AND timestampMillis < :endMillis
        ORDER BY timestampMillis DESC
        """
    )
    fun observeRange(startMillis: Long, endMillis: Long): Flow<List<FoodEntry>>

    /** Streams every entry in the table, newest first. Used to build the history view. */
    @Query("SELECT * FROM food_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<FoodEntry>>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)
}
