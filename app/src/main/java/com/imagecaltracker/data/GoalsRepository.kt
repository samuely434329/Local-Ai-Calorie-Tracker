package com.imagecaltracker.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * User-editable daily targets. Stored in a Preferences DataStore because
 * they are simple key-value scalars and we want to update them transactionally.
 */
data class DailyGoals(
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
) {
    companion object {
        // Defaults match the reference image.
        val Default = DailyGoals(calories = 2500, proteinG = 150, carbsG = 300, fatsG = 80)
    }
}

private val Context.goalsDataStore by preferencesDataStore(name = "daily_goals")

class GoalsRepository(private val context: Context) {

    private object Keys {
        val Calories = intPreferencesKey("calories")
        val Protein = intPreferencesKey("protein_g")
        val Carbs = intPreferencesKey("carbs_g")
        val Fats = intPreferencesKey("fats_g")
    }

    val goalsFlow: Flow<DailyGoals> = context.goalsDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e("GoalsRepository", "Error reading preferences", exception)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            Log.d("GoalsRepository", "Goals emitted from DataStore")
            DailyGoals(
                calories = prefs[Keys.Calories] ?: DailyGoals.Default.calories,
                proteinG = prefs[Keys.Protein] ?: DailyGoals.Default.proteinG,
                carbsG = prefs[Keys.Carbs] ?: DailyGoals.Default.carbsG,
                fatsG = prefs[Keys.Fats] ?: DailyGoals.Default.fatsG,
            )
        }

    suspend fun setGoals(goals: DailyGoals) {
        context.goalsDataStore.edit { prefs ->
            prefs[Keys.Calories] = goals.calories
            prefs[Keys.Protein] = goals.proteinG
            prefs[Keys.Carbs] = goals.carbsG
            prefs[Keys.Fats] = goals.fatsG
        }
    }
}
