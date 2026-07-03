package com.imagecaltracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Aggregate stats for a single day in the history view. */
data class DaySummary(
    val date: LocalDate,
    val entryCount: Int,
    val totalCalories: Int,
)

class FoodRepository(private val dao: FoodEntryDao) {

    /** Streams entries logged on the local-time [date]. */
    fun observeEntriesForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<FoodEntry>> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeRange(start, end).flowOn(Dispatchers.IO)
    }

    /**
     * Streams a per-day summary of all logged entries, excluding [today], newest day first.
     * Buckets by local-time date in the supplied [zone].
     */
    fun observeHistory(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<DaySummary>> =
        dao.observeAll()
            .map { entries ->
                entries
                    .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
                    .filterKeys { it != today }
                    .map { (date, dayEntries) ->
                        DaySummary(
                            date = date,
                            entryCount = dayEntries.size,
                            totalCalories = dayEntries.sumOf { it.calories },
                        )
                    }
                    .sortedByDescending { it.date }
            }
            .flowOn(Dispatchers.Default)

    suspend fun add(entry: FoodEntry): Long = dao.insert(entry)

    suspend fun update(entry: FoodEntry) = dao.update(entry)

    suspend fun delete(entry: FoodEntry) = dao.delete(entry)
}
