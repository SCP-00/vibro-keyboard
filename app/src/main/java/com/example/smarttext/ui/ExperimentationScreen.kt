package com.example.smarttext.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smarttext.engine.FuzzyScorer
import com.example.smarttext.engine.SOM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pantalla de experimentación y visualización del proyecto.
 *
 * Contiene:
 * - SOM Grid: Visualización del mapa auto-organizado con clusters de palabras
 * - Error de cuantización: Gráfica de convergencia del SOM
 * - Análisis de clusters: palabras agrupadas por neurona
 * - Sistema de Lógica Difusa: reglas Mamdani con ejemplos de scoring
 * - Estadísticas del corpus: métricas generales
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentationScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var som by remember { mutableStateOf<SOM?>(null) }
    var wordClusters by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    var clusterLabels by remember { mutableStateOf<Map<Pair<Int, Int>, String>>(emptyMap()) }
    var quantizationErrors by remember { mutableStateOf<List<Double>>(emptyList()) }
    var corpusStats by remember { mutableStateOf<CorpusStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val jsonStr = withContext(Dispatchers.IO) {
                context.assets.open("corpus.json").bufferedReader().use { it.readText() }
            }
            val root = JSONObject(jsonStr)
            val esData = root.optJSONObject("es") ?: run {
                errorMessage = "No se encontraron datos para español en el corpus"
                isLoading = false
                return@LaunchedEffect
            }
            val unigramsObj = esData.optJSONObject("unigrams") ?: run {
                errorMessage = "No se encontraron unigramas en el corpus"
                isLoading = false
                return@LaunchedEffect
            }

            val words = mutableListOf<String>()
            val wordFreqs = mutableMapOf<String, Int>()
            val keys: MutableList<String> = mutableListOf()
            unigramsObj.keys().forEach { keys.add(it) }
            for (key in keys) {
                words.add(key)
                wordFreqs[key] = unigramsObj.optInt(key, 1)
            }

            // Estadísticas del corpus
            val totalWords = words.size
            val totalFreq = wordFreqs.values.sum()
            val avgFreq = if (totalWords > 0) totalFreq / totalWords else 0
            val avgLength = if (totalWords > 0) words.sumOf { it.length } / totalWords.toDouble() else 0.0
            val uniqueFirstLetters = words.map { it.firstOrNull() }.distinct().size

            corpusStats = CorpusStats(
                wordCount = totalWords,
                totalFrequency = totalFreq,
                avgFrequency = avgFreq,
                avgLength = avgLength,
                uniqueFirstLetters = uniqueFirstLetters
            )

            // Entrenar SOM
            val somModel = SOM(gridWidth = 8, gridHeight = 8, epochs = 100)
            withContext(Dispatchers.Default) {
                somModel.train(words)
            }
            som = somModel
            wordClusters = somModel.clusterWords(words)
            quantizationErrors = somModel.getQuantizationErrors()

            // Labels para neuronas con palabras representativas
            val labels = mutableMapOf<Pair<Int, Int>, String>()
            for ((word, pos) in wordClusters) {
                if (pos !in labels) {
                    labels[pos] = word
                }
            }
            clusterLabels = labels

            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Experimentación") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando datos y entrenando SOM...")
                    Text(
                        text = "Esto puede tomar unos segundos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🧪 Experimentación y Visualización",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SOM + Métricas del corpus + Lógica Difusa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ═══ Corpus Stats ═══
                corpusStats?.let { stats ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "📊 Estadísticas del Corpus",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            StatRow("Palabras únicas", "${stats.wordCount}")
                            StatRow("Frecuencia total acumulada", "${stats.totalFrequency}")
                            StatRow("Frecuencia promedio", "${stats.avgFrequency}")
                            StatRow("Longitud promedio", "%.2f caracteres".format(stats.avgLength))
                            StatRow("Primeras letras distintas", "${stats.uniqueFirstLetters}")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ═══ SOM Grid Visualization (Box-based) ═══
                som?.let { somModel ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🧠 SOM — Mapa Auto-Organizado (${somModel.gridWidth}×${somModel.gridHeight})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cada celda representa una neurona. Palabras similares se agrupan en regiones cercanas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // SOM Grid as multiple Rows of colored Boxes
                            val cellSize = 40.dp
                            val cellSpacing = 2.dp

                            for (y in 0 until somModel.gridHeight) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(cellSpacing),
                                    modifier = Modifier.padding(vertical = cellSpacing / 2)
                                ) {
                                    for (x in 0 until somModel.gridWidth) {
                                        val weights = somModel.getNeuronWeights(x, y)
                                        val avgFeature = weights.average()
                                        val bgColor = when {
                                            avgFeature > 0.6 -> Color(0xFF4CAF50)
                                            avgFeature > 0.4 -> Color(0xFF2196F3)
                                            avgFeature > 0.25 -> Color(0xFFFF9800)
                                            else -> Color(0xFFE91E63)
                                        }
                                        val label = clusterLabels[x to y]

                                        Surface(
                                            modifier = Modifier.size(cellSize),
                                            color = bgColor,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (label != null) {
                                                    val displayText = if (label.length > 5) label.take(4) + "…" else label
                                                    Text(
                                                        text = displayText,
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ═══ Quantization Error Chart ═══
                    if (quantizationErrors.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "📈 Error de Cuantización por Época",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Mide la convergencia del SOM: el error disminuye con cada época.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Simple line chart using Canvas + composable labels
                                val maxError = quantizationErrors.maxOrNull() ?: 1.0
                                val minError = quantizationErrors.minOrNull() ?: 0.0
                                val range = (maxError - minError).coerceAtLeast(0.001)

                                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(start = 40.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)
                                    ) {
                                        val chartLeft = 0f
                                        val chartTop = 0f
                                        val chartW = size.width
                                        val chartH = size.height

                                        // Axis lines
                                        drawLine(Color.Gray, Offset(chartLeft, chartTop), Offset(chartLeft, chartTop + chartH))
                                        drawLine(Color.Gray, Offset(chartLeft, chartTop + chartH), Offset(chartLeft + chartW, chartTop + chartH))

                                        // Data line
                                        if (quantizationErrors.size >= 2) {
                                            val stepX = chartW / (quantizationErrors.size - 1)
                                            for (i in 0 until quantizationErrors.size - 1) {
                                                val y1 = chartTop + chartH - ((quantizationErrors[i] - minError) / range * chartH).toFloat()
                                                val y2 = chartTop + chartH - ((quantizationErrors[i + 1] - minError) / range * chartH).toFloat()
                                                val x1 = chartLeft + i * stepX
                                                val x2 = chartLeft + (i + 1) * stepX
                                                drawLine(Color(0xFF4CAF50), Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
                                            }
                                            // Draw points
                                            for (i in quantizationErrors.indices) {
                                                val y = chartTop + chartH - ((quantizationErrors[i] - minError) / range * chartH).toFloat()
                                                val x = chartLeft + i * stepX
                                                drawCircle(Color(0xFF4CAF50), radius = 3f, center = Offset(x, y))
                                            }
                                        }
                                    }
                                    // Axis label: 0
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.BottomStart).offset(x = 32.dp, y = 0.dp)
                                    )
                                    // Axis label: N (epochs)
                                    Text(
                                        text = "${quantizationErrors.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 0.dp)
                                    )
                                    // Y-axis title
                                    Text(
                                        text = "↑ Error",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.TopStart).offset(x = 4.dp, y = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Error inicial: %.4f → Error final: %.4f".format(
                                        quantizationErrors.firstOrNull() ?: 0.0,
                                        quantizationErrors.lastOrNull() ?: 0.0
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ═══ Cluster analysis ═══
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🔍 Análisis de Clusters SOM",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val neuronWords = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
                            for ((word, pos) in wordClusters.entries.take(500)) {
                                neuronWords.getOrPut(pos) { mutableListOf() }.add(word)
                            }

                            val sortedNeurons = neuronWords.entries
                                .filter { it.value.size >= 2 }
                                .sortedByDescending { it.value.size }
                                .take(5)

                            if (sortedNeurons.isNotEmpty()) {
                                Text(
                                    text = "Top 5 clusters más poblados:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                for ((pos, words) in sortedNeurons) {
                                    val sample = words.take(5).joinToString(", ")
                                    Text(
                                        text = "Neurona (${pos.first},${pos.second}): ${words.size} → $sample…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "No se encontraron clusters significativos con los datos actuales.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ═══ FuzzyScorer Rules ═══
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚙️ Sistema de Lógica Difusa (Mamdani)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FuzzyRuleCard("R1: SI lev ES baja Y freq ES alta → Excelente", "Palabra idéntica de alta frecuencia")
                        FuzzyRuleCard("R2: SI lev ES baja Y ctx ES alto → Excelente", "Palabra idéntica con contexto previo")
                        FuzzyRuleCard("R3: SI lev ES baja Y freq ES media → Buena", "Palabra cercana de frecuencia moderada")
                        FuzzyRuleCard("R4: SI lev ES media Y freq ES alta → Buena", "Palabra común con pequeña variación")
                        FuzzyRuleCard("R5: SI lev ES media Y freq ES media → Aceptable", "Palabra parcialmente similar")
                        FuzzyRuleCard("R6: SI lev ES alta → Mala", "Palabra muy diferente al input")

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Ejemplos de scoring:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ScoreExample("casa → casa", FuzzyScorer.getScore("casa", "casa", 5000, false))
                        ScoreExample("casa → cazo", FuzzyScorer.getScore("casa", "cazo", 5000, false))
                        ScoreExample("casa → casa (ctx)", FuzzyScorer.getScore("casa", "casa", 5000, true))
                        ScoreExample("casa → perro", FuzzyScorer.getScore("casa", "perro", 5000, false))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FuzzyRuleCard(rule: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = rule, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScoreExample(label: String, score: Double) {
    val color = when {
        score >= 80.0 -> Color(0xFF4CAF50)
        score >= 50.0 -> Color(0xFFFF9800)
        else -> Color(0xFFE91E63)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "%.1f".format(score), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}

data class CorpusStats(
    val wordCount: Int,
    val totalFrequency: Int,
    val avgFrequency: Int,
    val avgLength: Double,
    val uniqueFirstLetters: Int
)
