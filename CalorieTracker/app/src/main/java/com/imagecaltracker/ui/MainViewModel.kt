package com.imagecaltracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.imagecaltracker.data.CalorieDatabase
import com.imagecaltracker.data.DailyGoals
import com.imagecaltracker.data.FoodEntry
import com.imagecaltracker.data.FoodRepository
import com.imagecaltracker.data.GoalsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Aggregate UI state for the main screen. The screen renders directly from
 * this — no separate state needed.
 */
data class MainUiState(
    val date: LocalDate,
    val goals: DailyGoals,
    val entries: List<FoodEntry>,
) {
    val totalCalories: Int get() = entries.sumOf { it.calories }
    val totalProtein: Int get() = entries.sumOf { it.proteinG }
    val totalCarbs: Int get() = entries.sumOf { it.carbsG }
    val totalFats: Int get() = entries.sumOf { it.fatsG }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val foodRepo = FoodRepository(CalorieDatabase.get(application).foodEntryDao())
    private val goalsRepo = GoalsRepository(application)

    /** Currently displayed day. Always today; exposed for potential future history view. */
    private val dateFlow = MutableStateFlow(LocalDate.now())

    private val entriesFlow = dateFlow.flatMapLatest { foodRepo.observeEntriesForDate(it) }

    val uiState: StateFlow<MainUiState> = combine(
        dateFlow,
        goalsRepo.goalsFlow,
        entriesFlow,
    ) { date, goals, entries ->
        MainUiState(date = date, goals = goals, entries = entries)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(LocalDate.now(), DailyGoals.Default, emptyList()),
    )

    fun addEntry(name: String, calories: Int, proteinG: Int, carbsG: Int, fatsG: Int) {
        if (name.isBlank() || calories <= 0) return
        val entry = FoodEntry(
            name = name.trim(),
            calories = calories,
            proteinG = proteinG.coerceAtLeast(0),
            carbsG = carbsG.coerceAtLeast(0),
            fatsG = fatsG.coerceAtLeast(0),
            timestampMillis = System.currentTimeMillis(),
        )
        viewModelScope.launch { foodRepo.add(entry) }
    }

    fun updateEntry(entry: FoodEntry) {
        viewModelScope.launch { foodRepo.update(entry) }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch { foodRepo.delete(entry) }
    }

    fun setGoals(goals: DailyGoals) {
        viewModelScope.launch { goalsRepo.setGoals(goals) }
    }
}
