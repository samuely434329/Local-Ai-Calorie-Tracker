package com.imagecaltracker.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

class FoodRepository(private val dao: FoodEntryDao) {

    /** Streams entries logged on the local-time [date]. */
    fun observeEntriesForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<FoodEntry>> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeRange(start, end)
    }

    suspend fun add(entry: FoodEntry): Long = dao.insert(entry)

    suspend fun update(entry: FoodEntry) = dao.update(entry)

    suspend fun delete(entry: FoodEntry) = dao.delete(entry)
}
