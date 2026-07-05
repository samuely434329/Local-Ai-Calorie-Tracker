package com.imagecaltracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imagecaltracker.assistant.AssistantDialog
import com.imagecaltracker.data.FoodEntry
import com.imagecaltracker.ui.sketch.PaperBackground
import com.imagecaltracker.ui.sketch.SketchyButton
import com.imagecaltracker.ui.sketch.SketchyCalorieRing
import com.imagecaltracker.ui.sketch.SketchyMacroBar
import com.imagecaltracker.ui.sketch.SketchyTextField
import com.imagecaltracker.ui.sketch.sketchyBorder
import com.imagecaltracker.ui.theme.SketchColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.historyFlow.collectAsStateWithLifecycle()

    var showGoalsDialog by rememberSaveable { mutableStateOf(false) }
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showAssistantDialog by rememberSaveable { mutableStateOf(false) }
    var entryBeingEdited by remember { mutableStateOf<FoodEntry?>(null) }

    PaperBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            TopBar(
                date = uiState.date,
                onEditGoals = { showGoalsDialog = true },
                onShowHistory = { showHistoryDialog = true },
                onOpenAssistant = { showAssistantDialog = true },
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SketchyCalorieRing(
                            current = uiState.totalCalories,
                            target = uiState.goals.calories,
                        )
                    }
                }
                item {
                    MacroRow(state = uiState)
                }
                item {
                    AddEntryForm(
                        onSubmit = viewModel::addEntry,
                        onQuickScan = { showAssistantDialog = true }
                    )
                }
                item {
                    SectionTitle("RECENT LOG")
                }
                if (uiState.entries.isEmpty()) {
                    item {
                        Text(
                            text = "No entries yet today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SketchColors.InkLight,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                } else {
                    items(uiState.entries, key = { it.id }) { entry ->
                        LogEntryRow(
                            entry = entry,
                            onClick = { entryBeingEdited = entry },
                        )
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (showGoalsDialog) {
        GoalsDialog(
            current = uiState.goals,
            onDismiss = { showGoalsDialog = false },
            onSave = {
                viewModel.setGoals(it)
                showGoalsDialog = false
            },
        )
    }

    if (showHistoryDialog) {
        HistoryDialog(
            history = history,
            onDismiss = { showHistoryDialog = false },
            onSelectDate = { date ->
                viewModel.setDate(date)
                showHistoryDialog = false
            },
        )
    }

    if (showAssistantDialog) {
        AssistantDialog(
            onDismiss = { showAssistantDialog = false },
            onAddToLog = { estimate ->
                viewModel.addEntry(
                    name = estimate.name,
                    calories = estimate.calories,
                    proteinG = estimate.proteinG,
                    carbsG = estimate.carbsG,
                    fatsG = estimate.fatsG,
                )
            },
        )
    }

    entryBeingEdited?.let { entry ->
        EditEntryDialog(
            entry = entry,
            onDismiss = { entryBeingEdited = null },
            onSave = {
                viewModel.updateEntry(it)
                entryBeingEdited = null
            },
            onDelete = {
                viewModel.deleteEntry(it)
                entryBeingEdited = null
            },
        )
    }
}

@Composable
private fun TopBar(
    date: LocalDate,
    onEditGoals: () -> Unit,
    onShowHistory: () -> Unit,
    onOpenAssistant: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE")),
                style = MaterialTheme.typography.titleMedium,
                color = SketchColors.InkMid,
            )
            Text(
                text = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = SketchColors.InkDark,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenAssistant) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = "Open assistant",
                    tint = SketchColors.InkDark,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = SketchColors.InkDark,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("History") },
                        onClick = {
                            menuOpen = false
                            onShowHistory()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit goals") },
                        onClick = {
                            menuOpen = false
                            onEditGoals()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MacroRow(state: MainUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SketchyMacroBar(
            label = "PROTEIN (g)",
            current = state.totalProtein,
            target = state.goals.proteinG,
            modifier = Modifier.weight(1f),
            seed = 201,
        )
        SketchyMacroBar(
            label = "CARBS (g)",
            current = state.totalCarbs,
            target = state.goals.carbsG,
            modifier = Modifier.weight(1f),
            seed = 211,
        )
        SketchyMacroBar(
            label = "FATS (g)",
            current = state.totalFats,
            target = state.goals.fatsG,
            modifier = Modifier.weight(1f),
            seed = 221,
        )
    }
}

@Composable
private fun AddEntryForm(
    onSubmit: (name: String, calories: Int, protein: Int, carbs: Int, fats: Int) -> Unit,
    onQuickScan: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fats by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sketchyBorder(seed = 301, cornerRadius = 10.dp, strokeWidth = 1.6.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("ADD MEAL / FOOD")
            SketchyButton(
                text = "Quick Scan",
                onClick = onQuickScan
                //modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(2f)) {
                FieldLabel("FOOD ITEM NAME")
                SketchyTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "e.g. Oatmeal + Coffee",
                    seed = 311,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(modifier = Modifier.widthIn(min = 92.dp).weight(1f)) {
                FieldLabel("CALORIES")
                SketchyTextField(
                    value = calories,
                    onValueChange = { calories = it.filter(Char::isDigit).take(5) },
                    keyboardType = KeyboardType.Number,
                    seed = 321,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            MacroEntryColumn(
                label = "PROTEIN (g)",
                value = protein,
                onValueChange = { protein = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                seed = 331,
            )
            MacroEntryColumn(
                label = "CARBS (g)",
                value = carbs,
                onValueChange = { carbs = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                seed = 341,
            )
            MacroEntryColumn(
                label = "FATS (g)",
                value = fats,
                onValueChange = { fats = it.filter(Char::isDigit).take(4) },
                modifier = Modifier.weight(1f),
                seed = 351,
            )
            Column(modifier = Modifier.width(72.dp)) {
                FieldLabel("ADD")
                SketchyButton(
                    text = "+",
                    enabled = name.isNotBlank() && (calories.toIntOrNull() ?: 0) > 0,
                    seed = 361,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSubmit(
                            name,
                            calories.toIntOrNull() ?: 0,
                            protein.toIntOrNull() ?: 0,
                            carbs.toIntOrNull() ?: 0,
                            fats.toIntOrNull() ?: 0,
                        )
                        name = ""; calories = ""; protein = ""; carbs = ""; fats = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun MacroEntryColumn(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    seed: Int,
) {
    Column(modifier = modifier) {
        FieldLabel(label)
        SketchyTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardType = KeyboardType.Number,
            seed = seed,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = SketchColors.InkMid,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = SketchColors.InkDark,
    )
}

@Composable
private fun LogEntryRow(entry: FoodEntry, onClick: () -> Unit) {
    val time = remember(entry.timestampMillis) {
        Instant.ofEpochMilli(entry.timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mma"))
            .lowercase()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyMedium,
                color = SketchColors.InkMid,
                modifier = Modifier.width(72.dp),
            )
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                color = SketchColors.InkDark,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            Text(
                text = "${entry.calories} kcal",
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
