package com.example.smarttext.engine

import kotlin.math.*
import kotlin.random.Random

/**
 * Self-Organizing Map (SOM) / Mapa Auto-Organizado de Kohonen.
 *
 * Implementación para clustering de palabras según sus características
 * léxicas. Entrena una grilla 2D de neuronas que aprenden a representar
 * la estructura del espacio de entrada, agrupando palabras similares
 * en regiones cercanas.
 *
 * ## Algoritmo
 *
 * 1. Inicializar neuronas con vectores de peso aleatorios en [0,1]
 * 2. Para cada vector de entrada (época):
 *    a. Encontrar la Best Matching Unit (BMU) — neurona más cercana
 *    b. Actualizar BMU y vecinos hacia el vector de entrada
 * 3. El radio de vecindad y la tasa de aprendizaje decaen con el tiempo
 *
 * ## Feature Vector (5 dimensiones)
 *
 * | Índice | Feature | Descripción |
 * |--------|---------|-------------|
 * | 0 | Longitud normalizada | word.length / maxLength (0–1) |
 * | 1 | Razón de vocales | vowelCount / word.length (0–1) |
 * | 2 | Razón de consonantes | consonantCount / word.length (0–1) |
 * | 3 | Primera letra | letra → 0..25, normalizado a 0–1 |
 * | 4 | Última letra | letra → 0..25, normalizado a 0–1 |
 *
 * ## Complejidad
 *
 * - Tiempo: O(epochs × gridSize² × vocabSize × dims)
 * - Memoria: O(gridSize² × dims)
 *
 * @property gridWidth Ancho de la grilla SOM en neuronas.
 * @property gridHeight Alto de la grilla SOM en neuronas.
 * @property epochs Número de iteraciones de entrenamiento.
 * @property learningRate Tasa de aprendizaje inicial (α₀).
 * @property initialRadius Radio de vecindad inicial (σ₀).
 */
class SOM(
    val gridWidth: Int = 8,
    val gridHeight: Int = 8,
    val epochs: Int = 100,
    val learningRate: Double = 0.5,
    val initialRadius: Double = 3.0
) {

    /** Dimensión del vector de entrada (feature vector). */
    private val dims = 5

    /**
     * Una neurona en la grilla SOM.
     *
     * @property x Posición X en la grilla.
     * @property y Posición Y en la grilla.
     * @property weights Vector de peso de la neurona (dimensión [dims]).
     */
    data class Neuron(
        val x: Int,
        val y: Int,
        val weights: DoubleArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Neuron) return false
            return x == other.x && y == other.y && weights.contentEquals(other.weights)
        }
        override fun hashCode(): Int {
            var result = x
            result = 31 * result + y
            result = 31 * result + weights.contentHashCode()
            return result
        }
    }

    /** Grilla de neuronas entrenadas. */
    private val grid = Array(gridHeight) { y ->
        Array(gridWidth) { x ->
            Neuron(x, y, DoubleArray(dims) { Random.nextDouble() })
        }
    }

    /** Historial de error de cuantización por época (para visualización). */
    private val quantizationErrors = mutableListOf<Double>()

    /** Máxima longitud de palabra observada (para normalización). */
    private var maxLength: Int = 1

    /** Conjunto de vocales para feature extraction. */
    companion object {
        private val VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'á', 'é', 'í', 'ó', 'ú', 'ü')
    }

    // ────────  Feature Extraction  ────────

    /**
     * Extrae el vector de características de una palabra.
     *
     * @param word Palabra en minúsculas.
     * @return Vector de 5 dimensiones con valores en [0, 1].
     */
    fun extractFeatures(word: String): DoubleArray {
        val lower = word.lowercase().trim()
        val len = lower.length.coerceAtLeast(1)
        val vowelCount = lower.count { it in VOWELS }
        val consonantCount = len - vowelCount
        val firstLetter = if (lower.isNotEmpty()) (lower[0] - 'a').coerceIn(0, 25) else 0
        val lastLetter = if (lower.isNotEmpty()) (lower.last() - 'a').coerceIn(0, 25) else 0

        return doubleArrayOf(
            len.toDouble() / maxLength.coerceAtLeast(len),  // Longitud normalizada
            vowelCount.toDouble() / len,                     // Razón de vocales
            consonantCount.toDouble() / len,                 // Razón de consonantes
            firstLetter.toDouble() / 25.0,                   // Primera letra normalizada
            lastLetter.toDouble() / 25.0                     // Última letra normalizada
        )
    }

    // ────────  Training  ────────

    /**
     * Entrena el SOM con una lista de palabras.
     *
     * @param words Lista de palabras para entrenar.
     */
    fun train(words: List<String>) {
        if (words.isEmpty()) return
        maxLength = words.maxOfOrNull { it.length } ?: 1

        val features = words.map { extractFeatures(it) }
        val totalTime = epochs.toDouble()

        quantizationErrors.clear()

        for (epoch in 0 until epochs) {
            val t = epoch.toDouble()
            val progress = t / totalTime

            // Decaimiento exponencial de tasa de aprendizaje y radio
            val currentLr = learningRate * exp(-progress * 3.0)
            val currentRadius = initialRadius * exp(-progress * 3.0)

            var epochError = 0.0

            for (fv in features) {
                // Encontrar BMU
                val (bmuX, bmuY) = findBMU(fv)

                // Calcular distancia de entrada a BMU para error de cuantización
                val bmuWeights = grid[bmuY][bmuX].weights
                var sqDist = 0.0
                for (i in 0 until dims) {
                    val diff = fv[i] - bmuWeights[i]
                    sqDist += diff * diff
                }
                epochError += sqrt(sqDist)

                // Actualizar pesos de BMU y vecinos
                for (y in 0 until gridHeight) {
                    for (x in 0 until gridWidth) {
                        val distToBMU = sqrt(
                            (x - bmuX).toDouble().pow(2) + (y - bmuY).toDouble().pow(2)
                        )
                        if (distToBMU <= currentRadius) {
                            // Función de vecindad gaussiana
                            val influence = exp(-(distToBMU * distToBMU) / (2 * currentRadius * currentRadius))
                            val theta = currentLr * influence

                            // Actualizar pesos
                            for (i in 0 until dims) {
                                grid[y][x].weights[i] += theta * (fv[i] - grid[y][x].weights[i])
                            }
                        }
                    }
                }
            }

            quantizationErrors.add(epochError / features.size)
        }
    }

    // ────────  Inference  ────────

    /**
     * Encuentra la Best Matching Unit (neurona más cercana) para un vector de entrada.
     *
     * @param features Vector de características de entrada.
     * @return Coordenadas (x, y) de la BMU en la grilla.
     */
    fun findBMU(features: DoubleArray): Pair<Int, Int> {
        var bestX = 0
        var bestY = 0
        var bestDist = Double.MAX_VALUE

        for (y in 0 until gridHeight) {
            for (x in 0 until gridWidth) {
                var dist = 0.0
                for (i in 0 until dims) {
                    val diff = features[i] - grid[y][x].weights[i]
                    dist += diff * diff
                }
                if (dist < bestDist) {
                    bestDist = dist
                    bestX = x
                    bestY = y
                }
            }
        }

        return bestX to bestY
    }

    /**
     * Encuentra la BMU para una palabra (extrae features automáticamente).
     *
     * @param word Palabra a clasificar.
     * @return Coordenadas (x, y) de la BMU.
     */
    fun findBMUForWord(word: String): Pair<Int, Int> {
        return findBMU(extractFeatures(word))
    }

    /**
     * Obtiene el mapa de clusters: para cada palabra, su posición en la grilla SOM.
     *
     * @param words Lista de palabras a mapear.
     * @return Mapa de palabra → coordenadas (x, y) en la grilla.
     */
    fun clusterWords(words: List<String>): Map<String, Pair<Int, Int>> {
        return words.associateWith { findBMUForWord(it) }
    }

    /**
     * Obtiene el peso de una neurona en una coordenada específica.
     *
     * @param x Coordenada X.
     * @param y Coordenada Y.
     * @return Vector de peso de la neurona.
     */
    fun getNeuronWeights(x: Int, y: Int): DoubleArray {
        return grid[y][x].weights.copyOf()
    }

    /**
     * Obtiene la grilla completa de neuronas.
     */
    fun getGrid(): List<List<Neuron>> {
        return (0 until gridHeight).map { y ->
            (0 until gridWidth).map { x -> grid[y][x] }
        }
    }

    /**
     * Obtiene la lista de errores de cuantización por época.
     */
    fun getQuantizationErrors(): List<Double> = quantizationErrors.toList()

    /**
     * Encuentra todas las palabras que pertenecen a una neurona específica.
     *
     * @param words Lista de palabras a clasificar.
     * @param x Coordenada X de la neurona.
     * @param y Coordenada Y de la neurona.
     * @return Lista de palabras cuyo BMU es (x, y).
     */
    fun getWordsInNeuron(words: List<String>, x: Int, y: Int): List<String> {
        return words.filter { findBMUForWord(it) == x to y }
    }

    /**
     * Calcula la pureza del clustering (qué tan bien separa diferentes tipos de palabras).
     *
     * @param wordClusters Mapa de palabra → cluster (categoría semántica).
     * @return Pureza promedio (0–1), donde 1 = cada neurona contiene un solo tipo de palabra.
     */
    fun calculateClusterPurity(wordClusters: Map<String, String>): Double {
        if (wordClusters.isEmpty()) return 0.0

        // Agrupar palabras por neurona
        val neuronsWords = mutableMapOf<Pair<Int, Int>, MutableList<String>>()
        for ((word, _) in wordClusters) {
            val bmu = findBMUForWord(word)
            neuronsWords.getOrPut(bmu) { mutableListOf() }.add(word)
        }

        if (neuronsWords.isEmpty()) return 0.0

        var totalPurity = 0.0
        var totalWords = 0

        for ((_, words) in neuronsWords) {
            if (words.isEmpty()) continue
            // Contar cuántas palabras del cluster mayoritario hay en esta neurona
            val clusterCounts = mutableMapOf<String, Int>()
            for (w in words) {
                val cluster = wordClusters[w] ?: "unknown"
                clusterCounts[cluster] = (clusterCounts[cluster] ?: 0) + 1
            }
            val maxCount = clusterCounts.values.maxOrNull() ?: 0
            totalPurity += maxCount.toDouble()
            totalWords += words.size
        }

        return totalPurity / totalWords
    }
}
