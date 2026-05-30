package com.example.smarttext.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smarttext.engine.FuzzyScorer
import com.example.smarttext.engine.PredictorEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Pantalla de análisis de escritura.
 *
 * Muestra estadísticas detalladas sobre el corpus y el comportamiento
 * de escritura, incluyendo distribución de palabras, frecuencias,
 * y correcciones más comunes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritingAnalysisScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var analysisData by remember { mutableStateOf<WritingAnalysisData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val jsonStr = withContext(Dispatchers.IO) {
                context.assets.open("corpus.json").bufferedReader().use { it.readText() }
            }
            val root = JSONObject(jsonStr)

            // Analyze both languages
            val languages = listOf("es" to "Español", "en" to "English")
            val langAnalyses = mutableListOf<LanguageAnalysis>()

            for ((code, name) in languages) {
                val langData = root.optJSONObject(code) ?: continue
                val unigramsObj = langData.optJSONObject("unigrams")
                val bigramsObj = langData.optJSONObject("bigrams")

                if (unigramsObj == null) continue

                val wordFreqs = mutableMapOf<String, Int>()
                val keys: MutableList<String> = mutableListOf()
                unigramsObj.keys().forEach { keys.add(it) }
                for (key in keys) {
                    wordFreqs[key] = unigramsObj.optInt(key, 1)
                }

                val words = wordFreqs.entries.toList()
                val totalWords = words.size
                val totalFreq = words.sumOf { it.value }

                // Top 10 most frequent words
                val topWords = words.sortedByDescending { it.value }.take(10)

                // Word length distribution
                val lengthDist = mutableMapOf<Int, Int>()
                for ((word, _) in words) {
                    lengthDist[word.length] = (lengthDist[word.length] ?: 0) + 1
                }
                val lengthDistSorted = lengthDist.entries.sortedBy { it.key }

                // Average word length
                val avgLength = if (totalWords > 0) {
                    words.sumOf { it.key.length } / totalWords.toDouble()
                } else 0.0

                // Vowel analysis
                val vowels = setOf('a', 'e', 'i', 'o', 'u', 'á', 'é', 'í', 'ó', 'ú', 'ü')
                var totalChars = 0
                var totalVowels = 0
                for ((word, _) in words) {
                    totalChars += word.length
                    totalVowels += word.count { it in vowels }
                }
                val vowelRatio = if (totalChars > 0) totalVowels.toDouble() / totalChars else 0.0

                // Bigram analysis
                val bigramCount = if (bigramsObj != null) {
                    val bgKeys: MutableList<String> = mutableListOf()
                    bigramsObj.keys().forEach { bgKeys.add(it) }
                    bgKeys.size
                } else 0

                // First letter frequency
                val firstLetterFreq = mutableMapOf<Char, Int>()
                for ((word, _) in words) {
                    val first = word.firstOrNull() ?: continue
                    firstLetterFreq[first] = (firstLetterFreq[first] ?: 0) + 1
                }
                val topLetters = firstLetterFreq.entries
                    .sortedByDescending { it.value }
                    .take(5)

                langAnalyses.add(
                    LanguageAnalysis(
                        name = name,
                        code = code,
                        wordCount = totalWords,
                        totalFrequency = totalFreq,
                        avgLength = avgLength,
                        avgVowelRatio = vowelRatio,
                        topWords = topWords.map { Pair(it.key, it.value) },
                        lengthDistribution = lengthDistSorted.map { Pair(it.key, it.value) },
                        bigramCount = bigramCount,
                        topFirstLetters = topLetters.map { Pair(it.key, it.value) }
                    )
                )
            }

            analysisData = WritingAnalysisData(languages = langAnalyses)
            isLoading = false
        } catch (e: Exception) {
            errorMessage = "Error al cargar datos: ${e.message}"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análisis de Escritura") },
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
                    Text("Analizando corpus...")
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
                    text = "📝 Análisis de Escritura",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Estadísticas detalladas del corpus lingüístico",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                analysisData?.let { data ->
                    for (langAnalysis in data.languages) {
                        LanguageAnalysisCard(langAnalysis)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ═══ Cross-language comparison ═══
                    if (data.languages.size == 2) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "🌐 Comparación entre idiomas",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val es = data.languages[0]
                                val en = data.languages[1]

                                ComparisonRow("Palabras en corpus", "${es.wordCount}", "${en.wordCount}")
                                ComparisonRow("Longitud promedio", "%.2f".format(es.avgLength), "%.2f".format(en.avgLength))
                                ComparisonRow("Razón de vocales", "%.1f%%".format(es.avgVowelRatio * 100), "%.1f%%".format(en.avgVowelRatio * 100))
                                ComparisonRow("Bigramas", "${es.bigramCount}", "${en.bigramCount}")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ═══ Writing Tips ═══
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "💡 Tips de escritura",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "• El teclado aprende de tus palabras más frecuentes y las sugiere primero.\n" +
                                        "• Usa el deslizamiento (glide typing) para escribir más rápido.\n" +
                                        "• El contexto (palabra anterior) mejora la precisión de predicción.\n" +
                                        "• Las correcciones automáticas se guardan para futuras sesiones.\n" +
                                        "• El SOM agrupa palabras similares para mejorar sugerencias.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Version info
                    Text(
                        text = "SmartText v1.2 — Proyecto Final Computación Blanda\nMayo 2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LanguageAnalysisCard(analysis: LanguageAnalysis) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🌍 ${analysis.name} (${analysis.code})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // General stats
            AnalysisStatRow("Palabras únicas", "${analysis.wordCount}")
            AnalysisStatRow("Frecuencia total", "${analysis.totalFrequency}")
            AnalysisStatRow("Longitud promedio", "%.2f caracteres".format(analysis.avgLength))
            AnalysisStatRow("Razón de vocales", "%.1f%%".format(analysis.avgVowelRatio * 100))
            AnalysisStatRow("Bigramas distintos", "${analysis.bigramCount}")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Top words
            Text(
                text = "Palabras más frecuentes:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            for ((i, pair) in analysis.topWords.withIndex()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${i + 1}. ${pair.first}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "freq: ${pair.second}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Word length distribution
            Text(
                text = "Distribución de longitud:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            val maxLenCount = analysis.lengthDistribution.maxOfOrNull { it.second } ?: 1
            for ((length, count) in analysis.lengthDistribution.take(12)) {
                LengthBar(
                    label = "${length} letras",
                    count = count,
                    maxCount = maxLenCount,
                    totalWords = analysis.wordCount
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // First letter frequency
            Text(
                text = "Letras iniciales más comunes:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            for ((char, count) in analysis.topFirstLetters) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "\"$char\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$count palabras (%.1f%%)".format(count * 100.0 / analysis.wordCount),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LengthBar(label: String, count: Int, maxCount: Int, totalWords: Int) {
    val pct = count * 100.0 / totalWords.coerceAtLeast(1)
    val barWidth = (count.toFloat() / maxCount.coerceAtLeast(1))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barWidth.coerceIn(0f, 1f)),
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.extraSmall
            ) {}
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count (%.1f%%)".format(pct),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ComparisonRow(label: String, esValue: String, enValue: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "ES: $esValue",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = "EN: $enValue",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(100.dp)
        )
    }
}

/** Análisis de un idioma del corpus. */
data class LanguageAnalysis(
    val name: String,
    val code: String,
    val wordCount: Int,
    val totalFrequency: Int,
    val avgLength: Double,
    val avgVowelRatio: Double,
    val topWords: List<Pair<String, Int>>,
    val lengthDistribution: List<Pair<Int, Int>>,
    val bigramCount: Int,
    val topFirstLetters: List<Pair<Char, Int>>
)

/** Datos completos de análisis de escritura. */
data class WritingAnalysisData(
    val languages: List<LanguageAnalysis>
)
