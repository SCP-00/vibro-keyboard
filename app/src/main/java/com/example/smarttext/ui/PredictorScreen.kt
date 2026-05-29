package com.example.smarttext.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chaquo.python.Python

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PredictorScreen() {
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var currentLang by remember { mutableStateOf("es") }
    var predictorError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Initialize Python Predictor (one per language change)
    val predictor = remember(currentLang) {
        try {
            val py = Python.getInstance()
            val predictorModule = py.getModule("engine.predictor")
            val pred = predictorModule.callAttr("Predictor", context.filesDir.absolutePath, currentLang)
            predictorError = null
            pred
        } catch (e: Exception) {
            predictorError = "Error al cargar predictor: ${e.message}"
            null
        }
    }

    val focusRequester = remember { FocusRequester() }

    // Request focus on first composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartText") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Language selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = currentLang == "es",
                    onClick = { currentLang = "es" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("Español") }
                SegmentedButton(
                    selected = currentLang == "en",
                    onClick = { currentLang = "en" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("English") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            predictorError?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Text input field
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    // Extract current word and previous word for context-aware prediction
                    val words = newText.trim().split(" ")
                    val currentWord = if (words.isNotEmpty() && newText.isNotEmpty()) words.last() else ""
                    val previousWord = if (words.size >= 2) words[words.size - 2] else null

                    if (predictor != null && currentWord.isNotEmpty()) {
                        try {
                                    // Pass empty string for previous_word when null (Python treats "" as falsy)
                            val result = predictor.callAttr("predict", currentWord, previousWord ?: "", 3)
                            suggestions = result.asList().map { it.toString() }
                        } catch (e: Exception) {
                            suggestions = emptyList()
                        }
                    } else if (predictor != null && previousWord != null && currentWord.isEmpty()) {
                        // Context-only: suggest bigrams for previous word
                        try {
                            suggestions = predictor.callAttr("predict", "", previousWord, 3)
                                .asList().map { it.toString() }
                        } catch (_: Exception) {
                            suggestions = emptyList()
                        }
                    } else {
                        suggestions = emptyList()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = false,
                minLines = 3,
                maxLines = 8,
                label = { Text(if (currentLang == "es") "Escribe aquí..." else "Type here...") },
                placeholder = {
                    Text(
                        if (currentLang == "es") "Comienza a escribir para ver sugerencias..."
                        else "Start typing to see suggestions..."
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Suggestions chips row
            if (suggestions.isNotEmpty()) {
                Text(
                    text = if (currentLang == "es") "Sugerencias:" else "Suggestions:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    suggestions.forEach { sug ->
                        AssistChip(
                            onClick = {
                                // Autocomplete: replace last word with selected suggestion
                                val words = text.trimEnd().split(" ").dropLast(1).toMutableList()
                                words.add(sug)
                                text = words.joinToString(" ") + " "
                                suggestions = emptyList()
                                // Boost frequency of selected word
                                try {
                                    predictor?.callAttr("update_frequency", sug)
                                } catch (_: Exception) {}
                                // Request focus back to text field
                                focusRequester.requestFocus()
                            },
                            label = { Text(sug) },
                            leadingIcon = {
                                Text(
                                    "→",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Stats / info footer
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (currentLang == "es") {
                            "Idioma: Español • ${
                                if (predictor != null) "Predictor activo" else "Predictor no disponible"
                            }"
                        } else {
                            "Language: English • ${
                                if (predictor != null) "Predictor active" else "Predictor unavailable"
                            }"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (currentLang == "es") {
                            "Sistema de predicción basado en Lógica Difusa y Trie"
                        } else {
                            "Prediction system based on Fuzzy Logic and Trie"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
