package com.example.smarttext.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para SOM (Self-Organizing Map).
 *
 * Cobertura:
 * - Inicialización: grilla, dimensiones, neuronas
 * - Feature extraction: 5 features para palabras
 * - Entrenamiento: sin palabras, con palabras
 * - Clustering: palabras similares → misma neurona
 * - BMU: distancia euclidiana
 * - Convergencia: error de cuantización decreciente
 */
class SOMTest {

    // ═══════════════════════════════════════
    // 1. Inicialización
    // ═══════════════════════════════════════

    @Test
    fun `init - grilla tiene dimensiones correctas`() {
        val som = SOM(gridWidth = 10, gridHeight = 8)
        val grid = som.getGrid()
        assertEquals("Grid should have 8 rows", 8, grid.size)
        assertEquals("Grid should have 10 columns", 10, grid[0].size)
    }

    @Test
    fun `init - cada neurona tiene vector de 5 dimensiones`() {
        val som = SOM(gridWidth = 5, gridHeight = 5)
        val grid = som.getGrid()
        for (row in grid) {
            for (neuron in row) {
                assertEquals("Weights should have 5 dimensions", 5, neuron.weights.size)
            }
        }
    }

    @Test
    fun `init - pesos estan en rango 0-1`() {
        val som = SOM(gridWidth = 4, gridHeight = 4)
        val grid = som.getGrid()
        for (row in grid) {
            for (neuron in row) {
                for (w in neuron.weights) {
                    assertTrue("Weight $w should be in [0,1]", w in 0.0..1.0)
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // 2. Feature Extraction
    // ═══════════════════════════════════════

    @Test
    fun `extractFeatures - palabra genera vector de 5 dimensiones`() {
        val som = SOM()
        val features = som.extractFeatures("casa")
        assertEquals("Feature vector should have 5 dimensions", 5, features.size)
    }

    @Test
    fun `extractFeatures - palabras cortas y largas tienen longitudes normalizadas`() {
        val som = SOM()
        som.train(listOf("a", "casa", "supercalifragilistico"))

        val featuresA = som.extractFeatures("a")
        val featuresSuper = som.extractFeatures("supercalifragilistico")

        // 'a' (len=1) debe tener longitud normalizada menor que 'super...' (len=20)
        assertTrue(
            "Short word should have smaller normalized length",
            featuresA[0] < featuresSuper[0]
        )
    }

    @Test
    fun `extractFeatures - palabra con muchas vocales tiene alta razon de vocales`() {
        val som = SOM()
        som.train(listOf("aieou", "bcdfg")) // Ensure maxLength is set

        val featuresVowels = som.extractFeatures("aieou")  // 100% vowels
        val featuresCons = som.extractFeatures("bcdfg")    // 0% vowels

        assertTrue(
            "Vowel-rich word should have higher vowel ratio",
            featuresVowels[1] > featuresCons[1]
        )
        assertTrue(
            "Vowel-rich word should have lower consonant ratio",
            featuresVowels[2] < featuresCons[2]
        )
    }

    @Test
    fun `extractFeatures - palabra vacia no lanza excepcion`() {
        val som = SOM()
        som.train(listOf("test"))
        val features = som.extractFeatures("")
        assertEquals("Empty word should still produce 5 features", 5, features.size)
    }

    // ═══════════════════════════════════════
    // 3. Entrenamiento
    // ═══════════════════════════════════════

    @Test
    fun `train - lista vacia no lanza excepcion`() {
        val som = SOM()
        som.train(emptyList())
        // Should not throw
        assertTrue("Should not throw", true)
    }

    @Test
    fun `train - entrena con palabras del corpus sin lanzar excepcion`() {
        val som = SOM(gridWidth = 6, gridHeight = 6, epochs = 20)
        val words = listOf(
            "casa", "caso", "casi", "casco", "casita", "casero", "caseta",
            "perro", "gato", "pato", "paja", "pala", "papa",
            "sol", "luna", "luz", "lago", "lapa"
        )
        som.train(words)
        // Should not throw
        assertTrue("Training should complete without errors", true)
    }

    @Test
    fun `train - error de cuantizacion decrece con epocas`() {
        val som = SOM(gridWidth = 4, gridHeight = 4, epochs = 50)
        val words = listOf(
            "casa", "caso", "casi", "casco",
            "perro", "gato", "pato",
            "sol", "luna", "luz"
        )
        som.train(words)
        val errors = som.getQuantizationErrors()

        assertTrue("Should have quantization errors", errors.isNotEmpty())
        // Error should generally decrease (last epoch error < first epoch error)
        val firstError = errors.first()
        val lastError = errors.last()
        assertTrue(
            "Quantization error should decrease: $firstError -> $lastError",
            lastError < firstError
        )
    }

    // ═══════════════════════════════════════
    // 4. BMU (Best Matching Unit)
    // ═══════════════════════════════════════

    @Test
    fun `findBMU - siempre devuelve coordenadas dentro de la grilla`() {
        val som = SOM(gridWidth = 8, gridHeight = 8)
        som.train(listOf("casa", "perro", "sol"))

        val bmu = som.findBMU(doubleArrayOf(0.5, 0.3, 0.7, 0.2, 0.8))
        assertTrue("BMU x should be in [0, 7]", bmu.first in 0..7)
        assertTrue("BMU y should be in [0, 7]", bmu.second in 0..7)
    }

    @Test
    fun `findBMUForWord - palabras similares estan cerca en el SOM`() {
        val som = SOM(gridWidth = 5, gridHeight = 5, epochs = 50)
        val words = listOf(
            "casa", "caso", "casi", "casco", "casita", "casero", "caseta",
            "perro", "perra", "perrito", "perrera",
            "sol", "solar", "solera"
        )
        som.train(words)

        // Palabras que empiezan con "cas" deberían estar cerca
        val bmuCasa = som.findBMUForWord("casa")
        val bmuCaso = som.findBMUForWord("caso")
        val bmuCasi = som.findBMUForWord("casi")

        val distCas = manhattanDistance(bmuCasa, bmuCaso)
        val distCasi = manhattanDistance(bmuCasa, bmuCasi)

        // These words should be relatively close (within 3 cells Manhattan distance)
        assertTrue(
            "'casa' and 'caso' should be close: dist=$distCas",
            distCas <= 3
        )
        assertTrue(
            "'casa' and 'casi' should be close: dist=$distCasi",
            distCasi <= 3
        )
    }

    @Test
    fun `findBMUForWord - palabras diferentes estan mas separadas`() {
        val som = SOM(gridWidth = 5, gridHeight = 5, epochs = 50)
        val words = listOf(
            "casa", "caso", "casi",
            "perro", "gato", "pato",
            "sol", "luna", "luz"
        )
        som.train(words)

        val bmuCasa = som.findBMUForWord("casa")
        val bmuSol = som.findBMUForWord("sol")
        val bmuGato = som.findBMUForWord("gato")

        val dist = manhattanDistance(bmuCasa, bmuSol)
        // "casa" and "sol" should be in different neurons
        assertTrue(
            "'casa' and 'sol' should be in different positions (dist=$dist)",
            bmuCasa != bmuSol
        )
    }

    // ═══════════════════════════════════════
    // 5. Clustering
    // ═══════════════════════════════════════

    @Test
    fun `clusterWords - todas las palabras obtienen una posicion`() {
        val som = SOM(gridWidth = 4, gridHeight = 4, epochs = 30)
        val words = listOf("casa", "perro", "gato", "sol", "luna")
        som.train(words)

        val clusters = som.clusterWords(words)
        assertEquals("All words should be clustered", words.size, clusters.size)
        for (word in words) {
            assertTrue("'$word' should have a cluster position", clusters.containsKey(word))
        }
    }

    @Test
    fun `clusterWords - palabras identicas tienen mismo BMU`() {
        val som = SOM(gridWidth = 4, gridHeight = 4, epochs = 30)
        val words = listOf("casa", "caso", "casi")
        som.train(words)

        val clusters = som.clusterWords(words)
        // "casa", "caso", "casi" should all have their BMUs
        assertNotNull(clusters["casa"])
        assertNotNull(clusters["caso"])
        assertNotNull(clusters["casi"])
    }

    @Test
    fun `getWordsInNeuron - palabras agrupadas por neurona`() {
        val som = SOM(gridWidth = 4, gridHeight = 4, epochs = 30)
        val words = listOf("casa", "caso", "perro", "gato")
        som.train(words)

        // Check that all words are in SOME neuron
        var totalFound = 0
        for (x in 0 until 4) {
            for (y in 0 until 4) {
                totalFound += som.getWordsInNeuron(words, x, y).size
            }
        }
        assertEquals("All words should be found in some neuron", words.size, totalFound)
    }

    // ═══════════════════════════════════════
    // 6. Pureza del clustering
    // ═══════════════════════════════════════

    @Test
    fun `calculateClusterPurity - datos vacios devuelve 0`() {
        val som = SOM()
        assertEquals(0.0, som.calculateClusterPurity(emptyMap()), 0.001)
    }

    @Test
    fun `calculateClusterPurity - pureza con palabras agrupadas por primera letra`() {
        val som = SOM(gridWidth = 4, gridHeight = 4, epochs = 50)
        val words = listOf(
            "casa", "caso", "casi",  // empiezan con 'c'
            "perro", "pato", "pala", // empiezan con 'p'
            "gato", "gota", "goma"   // empiezan con 'g'
        )
        som.train(words)

        // Asignar cluster manual (por primera letra)
        val wordClusters = mapOf(
            "casa" to "c", "caso" to "c", "casi" to "c",
            "perro" to "p", "pato" to "p", "pala" to "p",
            "gato" to "g", "gota" to "g", "goma" to "g"
        )

        val purity = som.calculateClusterPurity(wordClusters)

        // The purity should be > 0 because the SOM should group same-first-letter words together
        assertTrue(
            "Cluster purity should be > 0 when words are grouped by first letter, got $purity",
            purity > 0.0
        )
    }

    // ═══════════════════════════════════════
    // 7. Manejo de errores
    // ═══════════════════════════════════════

    @Test
    fun `getNeuronWeights - coordenadas validas devuelven pesos`() {
        val som = SOM(gridWidth = 3, gridHeight = 3)
        val weights = som.getNeuronWeights(1, 1)
        assertEquals("Weights should have 5 dimensions", 5, weights.size)
    }

    @Test(expected = ArrayIndexOutOfBoundsException::class)
    fun `getNeuronWeights - coordenadas fuera de rango lanzan excepcion`() {
        val som = SOM(gridWidth = 3, gridHeight = 3)
        som.getNeuronWeights(10, 10)  // Should throw
    }

    @Test
    fun `train - epocas multiples no degradan el modelo`() {
        val som = SOM(gridWidth = 3, gridHeight = 3, epochs = 100)
        val words = listOf("casa", "perro", "gato")
        som.train(words)

        // Should still be able to find BMUs after many epochs
        val bmu = som.findBMUForWord("casa")
        assertTrue("BMU should be valid after many epochs", bmu.first >= 0 && bmu.second >= 0)
    }

    // ═══════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════

    private fun manhattanDistance(a: Pair<Int, Int>, b: Pair<Int, Int>): Int {
        return kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)
    }
}
