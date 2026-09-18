package com.imagecaltracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.imagecaltracker.data.DailyGoals
import com.imagecaltracker.data.DaySummary
import com.imagecaltracker.data.FoodEntry
import com.imagecaltracker.ui.sketch.SketchyButton
import com.imagecaltracker.ui.sketch.SketchyTextField
import com.imagecaltracker.ui.sketch.sketchyBorder
import com.imagecaltracker.ui.theme.SketchColors
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

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
    var protein by remember { mutableStateOf(current.proteinG.toString()) }
    var carbs by remember { mutableStateOf(current.carbsG.toString()) }
    var fats by remember { mutableStateOf(current.fatsG.toString()) }

    val calculatedCalories = remember(protein, carbs, fats) {
        val p = protein.toIntOrNull() ?: 0
        val c = carbs.toIntOrNull() ?: 0
        val f = fats.toIntOrNull() ?: 0
        (p * 4) + (c * 4) + (f * 9)
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("Daily Goals")

            CalculatedCalorieDisplay("Calories (kcal)", calculatedCalories)
            LabeledNumberField("Protein (g)", protein) { protein = it.filter(Char::isDigit).take(4) }
            LabeledNumberField("Carbs (g)", carbs) { carbs = it.filter(Char::isDigit).take(4) }
            LabeledNumberField("Fats (g)", fats) { fats = it.filter(Char::isDigit).take(4) }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SketchyButton(text = "Cancel", onClick = onDismiss, seed = 511)
                SketchyButton(
                    text = "Save",
                    onClick = {
                        onSave(
                            DailyGoals(
                                calories = calculatedCalories.coerceAtLeast(1),
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
    var protein by remember { mutableStateOf(entry.proteinG.toString()) }
    var carbs by remember { mutableStateOf(entry.carbsG.toString()) }
    var fats by remember { mutableStateOf(entry.fatsG.toString()) }

    val calculatedCalories = remember(protein, carbs, fats) {
        val p = protein.toIntOrNull() ?: 0
        val c = carbs.toIntOrNull() ?: 0
        val f = fats.toIntOrNull() ?: 0
        (p * 4) + (c * 4) + (f * 9)
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("Edit Entry")

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledTextField("Food name", name) { name = it }
                CalculatedCalorieDisplay("Calories (kcal)", calculatedCalories)
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
                        enabled = name.isNotBlank() && calculatedCalories > 0,
                        onClick = {
                            onSave(
                                entry.copy(
                                    name = name.trim(),
                                    calories = calculatedCalories,
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
fun CalculatorDialog(onDismiss: () -> Unit) {
    var operand1 by remember { mutableStateOf("") }
    var operand2 by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Double?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("Calculator")

            LabeledNumberField("Value 1", operand1) { operand1 = it.filter { c -> c.isDigit() || c == '.' } }
            LabeledNumberField("Value 2", operand2) { operand2 = it.filter { c -> c.isDigit() || c == '.' } }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SketchyButton(
                    text = "+",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val v1 = operand1.toDoubleOrNull() ?: 0.0
                        val v2 = operand2.toDoubleOrNull() ?: 0.0
                        result = v1 + v2
                    },
                    seed = 1001
                )
                SketchyButton(
                    text = "×",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val v1 = operand1.toDoubleOrNull() ?: 0.0
                        val v2 = operand2.toDoubleOrNull() ?: 0.0
                        result = v1 * v2
                    },
                    seed = 1002
                )
                SketchyButton(
                    text = "÷",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val v1 = operand1.toDoubleOrNull() ?: 0.0
                        val v2 = operand2.toDoubleOrNull() ?: 1.0
                        result = if (v2 != 0.0) v1 / v2 else null
                    },
                    seed = 1003
                )
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (result != null) {
                    val displayResult = if (result!! % 1.0 == 0.0) {
                        result!!.toLong().toString()
                    } else {
                        "%.2f".format(result)
                    }
                    Text(
                        text = "Result: $displayResult",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = SketchColors.InkDark,
                    )
                } else if (operand2 == "0") {
                    Text(
                        text = "Cannot divide by zero",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red,
                    )
                } else {
                    Text(
                        text = "Enter values and pick an operation",
                        style = MaterialTheme.typography.bodySmall,
                        color = SketchColors.InkLight,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SketchyButton(text = "Close", onClick = onDismiss, seed = 1004)
            }
        }
    }
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

@Composable
private fun CalculatedCalorieDisplay(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SketchColors.InkMid,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = SketchColors.InkDark.copy(alpha = 0.4f),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        )
    }
}

/**
 * History dialog. Lists past days that have logged entries, newest first,
 * showing the date plus a count of entries and the total kcal for that day.
 * Tapping a row calls [onSelectDate] with the picked date so the caller can
 * load that day's log.
 */
@Composable
fun HistoryDialog(
    history: List<DaySummary>,
    onDismiss: () -> Unit,
    onSelectDate: (java.time.LocalDate) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            DialogTitle("History")

            if (history.isEmpty()) {
                Text(
                    text = "No history yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SketchColors.InkLight,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                // Cap the list height so the dialog stays a reasonable size on long histories.
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                ) {
                    items(history, key = { it.date.toEpochDay() }) { day ->
                        HistoryRow(day = day, onClick = { onSelectDate(day.date) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SketchyButton(text = "Close", onClick = onDismiss, seed = 711)
            }
        }
    }
}

@Composable
private fun HistoryRow(day: DaySummary, onClick: () -> Unit) {
    val dateLabel = remember(day.date) {
        day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    val weekday = remember(day.date) {
        day.date.format(DateTimeFormatter.ofPattern("EEE"))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$weekday, $dateLabel",
                    style = MaterialTheme.typography.bodyLarge,
                    color = SketchColors.InkDark,
                )
                Text(
                    text = "${day.entryCount} ${if (day.entryCount == 1) "entry" else "entries"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SketchColors.InkMid,
                )
            }
            Text(
                text = "${day.totalCalories} kcal",
                style = MaterialTheme.typography.bodyLarge,
                color = SketchColors.InkDark,
            )
        }
        HorizontalDivider(
            color = SketchColors.GridLine,
            thickness = 0.6.dp,
        )
    }
}
