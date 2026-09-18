package com.imagecaltracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single food log entry. Macros are stored in grams as integers
 * (matches the reference UI which shows whole-gram amounts).
 *
 * [timestampMillis] is the wall-clock instant the entry was created and
 * is used for both ordering ("Today's Log") and filtering by day.
 */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
    val timestampMillis: Long,
)
