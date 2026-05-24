package com.imagecaltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imagecaltracker.data.DailyGoals
import com.imagecaltracker.data.FoodEntry
import com.imagecaltracker.ui.sketch.SketchyButton
import com.imagecaltracker.ui.sketch.SketchyTextField
import com.imagecaltracker.ui.sketch.sketchyBorder
import com.imagecaltracker.ui.theme.SketchColors

/**
 * Edit-goals dialog. Lets the user set the daily calorie target and the
 * three macro targets, all in grams.
 */
@Composable
fun GoalsDialog(
    current: DailyGoals,
    onDismiss: () -> Unit,
    onSave: (DailyGoals) -> Unit,
) {
    var calories by remember { mutableStateOf(current.calories.toString()) }
    var protein by remember { mutableStateOf(current.proteinG.toString()) }
    var carbs by remember { mutableStateOf(current.carbsG.toString()) }
    var fats by remember { mutableStateOf(current.fatsG.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("Daily Goals")

            LabeledNumberField("Calories (kcal)", calories) { calories = it.filter(Char::isDigit).take(5) }
            LabeledNumberField("Protein (g)", protein) { protein = it.filter(Char::isDigit).take(4) }
            LabeledNumberField("Carbs (g)", carbs) { carbs = it.filter(Char::isDigit).take(4) }
            LabeledNumberField("Fats (g)", fats) { fats = it.filter(Char::isDigit).take(4) }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Arrangement.End),
            ) {
                SketchyButton(text = "Cancel", onClick = onDismiss, seed = 511)
                SketchyButton(
                    text = "Save",
                    onClick = {
                        onSave(
                            DailyGoals(
                                calories = calories.toIntOrNull()?.coerceAtLeast(1) ?: current.calories,
                                proteinG = protein.toIntOrNull()?.coerceAtLeast(0) ?: current.proteinG,
                                carbsG = carbs.toIntOrNull()?.coerceAtLeast(0) ?: current.carbsG,
                                fatsG = fats.toIntOrNull()?.coerceAtLeast(0) ?: current.fatsG,
                            )
                        )
                    },
                    seed = 521,
                )
            }
        }
    }
}

/**
 * Edit-entry dialog. Lets the user change name, calories, and macros for an
 * existing log entry, or delete it. Timestamp is preserved.
 */
@Composable
fun EditEntryDialog(
    entry: FoodEntry,
    onDismiss: () -> Unit,
    onSave: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var calories by remember { mutableStateOf(entry.calories.toString()) }
    var protein by remember { mutableStateOf(entry.proteinG.toString()) }
    var carbs by remember { mutableStateOf(entry.carbsG.toString()) }
    var fats by remember { mutableStateOf(entry.fatsG.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("Edit Entry")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledTextField("Food name", name) { name = it }
                LabeledNumberField("Calories (kcal)", calories) { calories = it.filter(Char::isDigit).take(5) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledNumberField(
                        label = "Protein (g)",
                        value = protein,
                        onValueChange = { protein = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                    )
                    LabeledNumberField(
                        label = "Carbs (g)",
                        value = carbs,
                        onValueChange = { carbs = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                    )
                    LabeledNumberField(
                        label = "Fats (g)",
                        value = fats,
                        onValueChange = { fats = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SketchyButton(
                    text = "Delete",
                    onClick = { onDelete(entry) },
                    seed = 611,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SketchyButton(text = "Cancel", onClick = onDismiss, seed = 621)
                    SketchyButton(
                        text = "Save",
                        enabled = name.isNotBlank() && (calories.toIntOrNull() ?: 0) > 0,
                        onClick = {
                            onSave(
                                entry.copy(
                                    name = name.trim(),
                                    calories = calories.toIntOrNull()?.coerceAtLeast(0) ?: entry.calories,
                                    proteinG = protein.toIntOrNull()?.coerceAtLeast(0) ?: entry.proteinG,
                                    carbsG = carbs.toIntOrNull()?.coerceAtLeast(0) ?: entry.carbsG,
                                    fatsG = fats.toIntOrNull()?.coerceAtLeast(0) ?: entry.fatsG,
                                )
                            )
                        },
                        seed = 631,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared dialog primitives
// ---------------------------------------------------------------------------

@Composable
private fun DialogSurface(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = SketchColors.Paper, shape = RoundedCornerShape(12.dp))
            .sketchyBorder(
                color = SketchColors.InkDark,
                strokeWidth = 1.8.dp,
                cornerRadius = 12.dp,
                seed = 461,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = SketchColors.InkDark,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkMid,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SketchyTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabeledNumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkMid,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SketchyTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
