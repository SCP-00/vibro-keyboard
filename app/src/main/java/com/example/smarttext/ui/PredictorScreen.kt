package com.example.smarttext.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PredictorScreen() {
    var text by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var currentLang by remember { mutableStateOf("es") }
    var predictorError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    // Initialize Python Predictor on a background thread (prevents ANR)
    val predictor = remember { mutableStateOf<PyObject?>(null) }

    LaunchedEffect(currentLang) {
        isLoading = true
        predictorError = null
        predictor.value = null
        try {
            val pred = withContext(Dispatchers.IO) {
                val py = Python.getInstance()
                val predictorModule = py.getModule("engine.predictor")
                predictorModule.callAttr("Predictor", context.filesDir.absolutePath, currentLang)
            }
            predictor.value = pred
        } catch (e: Exception) {
            predictorError = "Error al cargar predictor: ${e.message}"
            Log.e("SmartText", "Predictor init error", e)
        } finally {
            isLoading = false
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

            // Loading indicator
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (currentLang == "es") "Cargando predictor..." else "Loading predictor...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

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
            val pred = predictor.value
            OutlinedTextField(
                value = text,
                enabled = !isLoading && pred != null,
                onValueChange = { newText ->
                    text = newText
                    // Extract current word and previous word for context-aware prediction
                    val words = newText.split(" ")
                    val currentWord = if (words.isNotEmpty() && newText.isNotEmpty()) words.last() else ""
                    val previousWord = if (words.size >= 2) words[words.size - 2] else null

                    val p = predictor.value
                    if (p != null && currentWord.isNotEmpty()) {
                        try {
                            val result = p.callAttr("predict", currentWord, previousWord ?: "", 3)
                            suggestions = result.asList().map { it.toString() }
                            Log.d("SmartText", "Prefix predict: '$currentWord' ctx='${previousWord ?: ""}' -> $suggestions")
                        } catch (e: Exception) {
                            Log.e("SmartText", "Predict error", e)
                            suggestions = emptyList()
                        }
                    } else if (p != null && previousWord != null && currentWord.isEmpty()) {
                        try {
                            val raw = p.callAttr("predict", "", previousWord, 3)
                            suggestions = raw.asList().map { it.toString() }
                            Log.d("SmartText", "Bigram predict: '' ctx='$previousWord' -> $suggestions")
                        } catch (e: Exception) {
                            Log.e("SmartText", "Bigram predict error", e)
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
                        if (isLoading) {
                            if (currentLang == "es") "Inicializando..." else "Initializing..."
                        } else {
                            if (currentLang == "es") "Comienza a escribir para ver sugerencias..."
                            else "Start typing to see suggestions..."
                        }
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
                                    predictor.value?.callAttr("update_frequency", sug)
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
                            "Idioma: Español • ${if (predictor.value != null) "Predictor activo" else "Predictor no disponible"}"
                        } else {
                            "Language: English • ${if (predictor.value != null) "Predictor active" else "Predictor unavailable"}"
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
