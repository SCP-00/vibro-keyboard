package com.example.smarttext.engine

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Tests unitarios para PredictorEngine.
 *
 * Mockea Android Context para cargar corpus JSON desde un string simulado
 * y usar un directorio temporal para user_data.json.
 *
 * Cobertura:
 * - Inicialización con corpus simulado
 * - searchPrefix: búsqueda binaria con prefijos existentes/inexistentes
 * - predict: 4 estrategias (bigrama, prefijo, Levenshtein, fallback)
 * - updateFrequency: aprendizaje incremental
 * - allWords: cache de frecuencias
 */
class PredictorEngineTest {

    private val mockCorpusJson = """
    {
      "es": {
        "unigrams": {
          "de": 50000,
          "la": 48000,
          "que": 45000,
          "el": 44000,
          "en": 42000,
          "un": 40000,
          "por": 38000,
          "con": 36000,
          "no": 34000,
          "su": 32000,
          "casa": 5000,
          "caso": 4000,
          "casi": 3000,
          "casco": 200,
          "casita": 100,
          "casero": 80,
          "caseta": 60,
          "zam": 10,
          "zambo": 5,
          "zambra": 3
        },
        "bigrams": {
          "de": {
            "la": 3000,
            "los": 2500,
            "las": 2000
          },
          "en": {
            "el": 2800,
            "la": 2500,
            "un": 2000
          },
          "la": {
            "casa": 800,
            "noche": 500
          }
        }
      }
    }
    """.trimIndent()

    private lateinit var engine: PredictorEngine
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        // Crear directorio temporal para user_data.json
        tempDir = Files.createTempDirectory("predictor_test_").toFile()
        tempDir.deleteOnExit()

        // Crear mock de Context
        val mockContext = mockk<Context>()

        // Mockear AssetManager para devolver nuestro JSON simulado
        val mockAssets = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())

        // Mockear filesDir para usar directorio temporal
        every { mockContext.filesDir } returns tempDir

        // Crear engine con el contexto mockeado
        engine = PredictorEngine(mockContext, "es")
    }

    // ═══════════════════════════════════════
    // 1. Inicialización
    // ═══════════════════════════════════════

    @Test
    fun `init - carga correctamente el corpus espanol`() {
        val all = engine.allWords
        assertTrue("Should have loaded ~20 words, got ${all.size}", all.size >= 15)
        // Verificar que las palabras más frecuentes están presentes
        val words = all.map { it.word }
        assertTrue("de should be in corpus", words.contains("de"))
        assertTrue("la should be in corpus", words.contains("la"))
        assertTrue("casa should be in corpus", words.contains("casa"))
    }

    // ═══════════════════════════════════════
    // 2. searchPrefix (búsqueda binaria)
    // ═══════════════════════════════════════

    @Test
    fun `searchPrefix - prefijo existente encuentra palabras`() {
        val results = engine.searchPrefix("cas")
        val words = results.map { it.word }
        assertTrue("Should find 'casa'", words.contains("casa"))
        assertTrue("Should find 'caso'", words.contains("caso"))
        assertTrue("Should find 'casi'", words.contains("casi"))
        assertTrue("Should find 'casco'", words.contains("casco"))
        assertTrue("Should find 'casita'", words.contains("casita"))
        assertTrue("Should find 'casero'", words.contains("casero"))
        assertTrue("Should find 'caseta'", words.contains("caseta"))
        assertEquals("Should find 7 words with prefix 'cas'", 7, results.size)
    }

    @Test
    fun `searchPrefix - prefijo inexistente devuelve vacio`() {
        val results = engine.searchPrefix("xyz")
        assertTrue("Non-existent prefix should return empty", results.isEmpty())
    }

    @Test
    fun `searchPrefix - prefijo vacio devuelve todas las palabras (minLength 3)`() {
        val results = engine.searchPrefix("")
        // Con prefijo vacío, lowerBound=0 y upperBound=size, así que
        // devuelve todas las palabras del corpus con length >= minLength(3)
        assertTrue("Empty prefix should return all words (length >= 3), got ${results.size}", results.size >= 10)
        // Verificar que palabras de 3+ letras están incluidas
        val words = results.map { it.word }
        assertTrue("'que' should be found", words.contains("que"))
        assertTrue("'por' should be found", words.contains("por"))
    }

    @Test
    fun `searchPrefix - resultados ordenados por frecuencia descendente`() {
        val results = engine.searchPrefix("cas")
        assertTrue("Should have at least 2 results", results.size >= 2)
        for (i in 0 until results.size - 1) {
            assertTrue(
                "Results should be sorted by frequency descending: " +
                    "${results[i].word}(${results[i].frequency}) >= ${results[i+1].word}(${results[i+1].frequency})",
                results[i].frequency >= results[i + 1].frequency
            )
        }
    }

    @Test
    fun `searchPrefix - palabras cortas son filtradas por minLength`() {
        // Buscar con minLength=2 - "el", "en", "un" tienen 2 letras
        val results = engine.searchPrefix("e", minLength = 2)
        val words = results.map { it.word }
        // "de" tiene 2 letras pero no empieza con 'e'
        assertTrue("'el' should be found", words.contains("el"))
        assertTrue("'en' should be found", words.contains("en"))
        // Verificar todas las palabras tienen length >= 2
        for (word in words) {
            assertTrue("All results should have length >= 2: '$word' has ${word.length}", word.length >= 2)
        }
    }

    @Test
    fun `searchPrefix - prefijo al final del alfabeto (zam-)`() {
        val results = engine.searchPrefix("zam")
        assertEquals("Should find 3 words with prefix 'zam'", 3, results.size)
        val words = results.map { it.word }
        assertTrue("Should find 'zam'", words.contains("zam"))
        assertTrue("Should find 'zambo'", words.contains("zambo"))
        assertTrue("Should find 'zambra'", words.contains("zambra"))
    }

    // ═══════════════════════════════════════
    // 3. predict (4 estrategias)
    // ═══════════════════════════════════════

    @Test
    fun `predict - estrategia 1 bigrama con palabras siguientes`() {
        // previousWord="la" + currentWord="" → debe predecir "casa", "noche"
        val results = engine.predict("", "la", 3)
        assertTrue("Bigram 'la' should predict something", results.isNotEmpty())
        assertTrue("Bigram 'la' should predict 'casa'", results.contains("casa"))
    }

    @Test
    fun `predict - estrategia 1 bigrama miss usa fallback top-K`() {
        // previousWord="xyz" (no existe en bigramas) + currentWord=""
        val results = engine.predict("", "xyz", 3)
        assertEquals("Bigram miss should return top-3 frequent words", 3, results.size)
        // Las palabras más frecuentes son "de", "la", "que"
        assertTrue("Top word should be 'de'", results.contains("de"))
    }

    @Test
    fun `predict - estrategia 2 prefijo con fuzzy scoring`() {
        // currentWord="cas"
        val results = engine.predict("cas", null, 3)
        assertTrue("Prefix 'cas' should return suggestions", results.isNotEmpty())
        // "casa" (freq=5000) debe rankear más alto que "casco" (freq=200)
        assertTrue("Result should include 'casa'", results.contains("casa"))
        assertTrue("Result should include 'caso'", results.contains("caso"))
    }

    @Test
    fun `predict - estrategia 3 Levenshtein para palabras mal escritas`() {
        // "cazz" no existe, Levenshtein con "casa"=2, "caso"=2, "casi"=2
        val results = engine.predict("cazz", null, 5)
        // Fallback a Levenshtein: "casa" (score ~65) debe superar threshold 5.0
        assertTrue("Levenshtein fallback should find suggestions for 'cazz', got $results", results.isNotEmpty())
        // Debe sugerir palabras cercanas como "casa" o "caso"
        assertTrue("Should suggest 'casa' or 'caso', got $results",
            results.contains("casa") || results.contains("caso"))
    }

    @Test
    fun `predict - estrategia 4 ultimate fallback palabras mas frecuentes`() {
        // Palabra que no coincide con nada en el corpus
        val results = engine.predict("xyzwxyz", null, 3)
        assertEquals("Ultimate fallback should return top-3 frequent words", 3, results.size)
        assertTrue("Should include 'de' (top freq)", results.contains("de"))
    }

    @Test
    fun `predict - previousWord nulo sin currentWord devuelve palabras frecuentes`() {
        val results = engine.predict("", null, 3)
        assertEquals("No context should return top-3", 3, results.size)
        assertTrue("Should include 'de'", results.contains("de"))
    }

    // ═══════════════════════════════════════
    // 4. updateFrequency (aprendizaje)
    // ═══════════════════════════════════════

    @Test
    fun `updateFrequency - incrementa frecuencia de palabra existente`() {
        // Verificar frecuencia original de "casa"
        val before = engine.searchPrefix("cas").first { it.word == "casa" }
        val origFreq = before.frequency

        engine.updateFrequency("casa")

        val after = engine.searchPrefix("cas").first { it.word == "casa" }
        assertEquals("Frequency should increase by USER_BOOST(10)", origFreq + 10, after.frequency)
    }

    @Test
    fun `updateFrequency - palabra nueva no aparece en searchPrefix`() {
        engine.updateFrequency("nuevapalabra")

        // updateFrequency solo actualiza userFreqs para palabras del corpus,
        // no para palabras completamente nuevas (no aparecen en sortedWords)
        assertTrue("updateFrequency should not add new words to searchPrefix",
            engine.searchPrefix("nueva").isEmpty())
    }

    @Test
    fun `updateFrequency - multiples actualizaciones se acumulan`() {
        engine.updateFrequency("casa")
        engine.updateFrequency("casa")
        engine.updateFrequency("casa")

        val results = engine.searchPrefix("cas")
        val entry = results.first { it.word == "casa" }
        assertEquals("3 updates = +30 boost", 5030, entry.frequency) // 5000 + 30
    }

    @Test
    fun `updateFrequency - invalida cache de prefijo`() {
        // Primera búsqueda calienta el cache
        engine.searchPrefix("cas")

        // Actualizar frecuencia
        engine.updateFrequency("casa")

        // Segunda búsqueda debe reflejar el cambio (cache invalidado)
        val after = engine.searchPrefix("cas")
        val entry = after.first { it.word == "casa" }
        assertEquals("Should see updated frequency after cache invalidation", 5010, entry.frequency)
    }

    // ═══════════════════════════════════════
    // 5. allWords
    // ═══════════════════════════════════════

    @Test
    fun `allWords - lista completa ordenada por frecuencia`() {
        val all = engine.allWords
        assertTrue("Should have all corpus words", all.size >= 15)

        // Verificar ordenamiento descendente
        for (i in 0 until all.size - 1) {
            assertTrue(
                "allWords should be sorted by frequency: ${all[i].word}(${all[i].frequency}) >= ${all[i+1].word}(${all[i+1].frequency})",
                all[i].frequency >= all[i + 1].frequency
            )
        }
    }

    @Test
    fun `allWords - incluye frecuencias de usuario`() {
        engine.updateFrequency("casita")
        val all = engine.allWords
        val entry = all.first { it.word == "casita" }
        // freq original 100 + 10 = 110
        assertEquals("Should include user boost", 110, entry.frequency)
    }

    // ═══════════════════════════════════════
    // 6. Persistencia de user_data.json
    // ═══════════════════════════════════════

    @Test
    fun `persistencia - user_data se guarda en disco`() {
        // El archivo user_data.json no debería existir antes de updateFrequency
        val userDataFile = File(tempDir, "user_data.json")
        // updateFrequency crea el archivo
        engine.updateFrequency("casa")
        assertTrue("user_data.json should exist after updateFrequency", userDataFile.exists())
        val content = userDataFile.readText()
        assertTrue("user_data.json should contain 'casa'", content.contains("casa"))
    }

    @Test
    fun `persistencia - frecuencias se cargan entre instancias`() {
        // Primera instancia: actualizar frecuencia
        engine.updateFrequency("casita")

        // Segunda instancia: debe cargar las frecuencias guardadas
        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "es")

        // Verificar que la frecuencia de "casita" incluye el boost de la sesión anterior
        val results = engine2.searchPrefix("cas")
        val entry = results.first { it.word == "casita" }
        assertEquals("Persistence: casita freq should be 100 + 10 = 110", 110, entry.frequency)
    }

    @Test
    fun `persistencia - multiples actualizaciones persisten entre instancias`() {
        engine.updateFrequency("casa")
        engine.updateFrequency("casa")
        engine.updateFrequency("casa")
        engine.updateFrequency("casita")

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "es")

        val results = engine2.searchPrefix("cas")
        val casaEntry = results.first { it.word == "casa" }
        val casitaEntry = results.first { it.word == "casita" }
        assertEquals("casa with 3 updates = 5030", 5030, casaEntry.frequency)
        assertEquals("casita with 1 update = 110", 110, casitaEntry.frequency)
    }

    @Test
    fun `persistencia - user_data corrupto no lanza excepcion`() {
        // Escribir JSON corrupto en user_data.json
        val userDataFile = File(tempDir, "user_data.json")
        userDataFile.writeText("{malformed json!!!}")

        // Crear nueva instancia — no debe lanzar excepción
        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        // No debe lanzar excepción
        val engine2 = PredictorEngine(mockContext2, "es")

        // El engine debe seguir funcionando con datos del corpus
        val results = engine2.searchPrefix("cas")
        assertTrue("Engine should still work after corrupted user data", results.isNotEmpty())
    }

    @Test
    fun `persistencia - user_data vacio no lanza excepcion`() {
        val userDataFile = File(tempDir, "user_data.json")
        userDataFile.writeText("")

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "es")
        val results = engine2.searchPrefix("cas")
        assertTrue("Engine should still work with empty user data", results.isNotEmpty())
    }

    // ═══════════════════════════════════════
    // 7. Manejo de errores (corpus corrupto)
    // ═══════════════════════════════════════

    @Test
    fun `corpus corrupto - JSON malformado no lanza excepcion`() {
        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream("{broken json!!!}".toByteArray())
        every { mockContext2.filesDir } returns tempDir

        // No debe lanzar excepción — loadCorpus captura Exception
        val engine2 = PredictorEngine(mockContext2, "es")

        // El engine debe estar vacío pero no roto
        assertTrue("Engine with broken corpus should have empty words", engine2.allWords.isEmpty())
        assertTrue("searchPrefix on broken corpus should return empty", engine2.searchPrefix("cas").isEmpty())
    }

    @Test
    fun `corpus corrupto - idioma inexistente en corpus devuelve vacio`() {
        // El mockCorpusJson solo tiene "es", no "fr"
        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(mockCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "fr")

        // Debe tratar el idioma faltante gracefully
        assertTrue("Engine with non-existent language should have empty words", engine2.allWords.isEmpty())
        assertTrue("searchPrefix should return empty for missing language", engine2.searchPrefix("cas").isEmpty())
    }

    @Test
    fun `corpus corrupto - sin unigrams ni bigrams no lanza excepcion`() {
        val minimalJson = """{"es":{}}""".trimIndent()

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(minimalJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "es")

        assertTrue("Engine with empty corpus should have empty words", engine2.allWords.isEmpty())
        assertTrue("predict should return empty for empty corpus", engine2.predict("cas", null, 3).isEmpty())
    }

    // ═══════════════════════════════════════
    // 8. Concurrencia (@Synchronized multi-thread)
    // ═══════════════════════════════════════

    @Test
    fun `concurrencia - searchPrefix desde multiples hilos no lanza excepcion`() {
        val numThreads = 10
        val numIterations = 20
        val prefixes = listOf("cas", "de", "la", "por", "zam", "a", "con", "no", "su", "que")
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until numIterations) {
                        val prefix = prefixes[(t + i) % prefixes.size]
                        val results = engine.searchPrefix(prefix)
                        // No importa el resultado, solo que no lance excepción
                        assertNotNull(results)
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val timedOut = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("Timed out waiting for threads", timedOut)
        assertEquals("No thread should have thrown an exception", 0, errors.get())
    }

    @Test
    fun `concurrencia - updateFrequency desde multiples hilos no lanza excepcion`() {
        val numThreads = 10
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(numThreads)
        val words = listOf("casa", "caso", "casi", "casco", "casita")

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until 10) {
                        engine.updateFrequency(words[(t + i) % words.size])
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val timedOut = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("Timed out waiting for threads", timedOut)
        assertEquals("No thread should have thrown an exception during updateFrequency", 0, errors.get())

        // Verificar que las frecuencias se acumularon correctamente
        val results = engine.searchPrefix("cas")
        val casaEntry = results.first { it.word == "casa" }
        // 10 threads × algunos updates = frecuencia debe ser > 5000
        assertTrue("Concurrent updates should accumulate: casa freq = ${casaEntry.frequency}",
            casaEntry.frequency > 5000)
    }

    @Test
    fun `concurrencia - allWords accedido concurrentemente no lanza excepcion`() {
        val numThreads = 10
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            Thread {
                try {
                    for (i in 0 until 20) {
                        val all = engine.allWords
                        assertNotNull(all)
                        assertTrue(all.isNotEmpty())
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val timedOut = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("Timed out waiting for threads", timedOut)
        assertEquals("No thread should have thrown an exception accessing allWords", 0, errors.get())
    }

    @Test
    fun `concurrencia - operaciones mixtas search y update no lanzan excepcion`() {
        val numThreads = 20
        val errors = java.util.concurrent.atomic.AtomicInteger(0)
        val latch = java.util.concurrent.CountDownLatch(numThreads)

        for (t in 0 until numThreads) {
            val isReader = t % 2 == 0
            Thread {
                try {
                    repeat(15) {
                        if (isReader) {
                            engine.searchPrefix("cas")
                            engine.allWords
                        } else {
                            engine.updateFrequency("casa")
                            engine.predict("cas", null, 3)
                        }
                    }
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val timedOut = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue("Timed out waiting for threads", timedOut)
        assertEquals("Mixed concurrent operations should not throw", 0, errors.get())
    }

    // ═══════════════════════════════════════
    // 9. Cambio de idioma (carga 'en')
    // ═══════════════════════════════════════

    @Test
    fun `idioma ingles - carga corpus ingles con unigrams`() {
        val enCorpusJson = """
        {
          "en": {
            "unigrams": {
              "the": 100000,
              "be": 80000,
              "to": 70000,
              "of": 65000,
              "and": 60000,
              "a": 55000,
              "in": 50000,
              "that": 45000,
              "have": 40000,
              "it": 35000,
              "house": 5000,
              "home": 4000,
              "hello": 3000,
              "world": 2000
            },
            "bigrams": {
              "the": {
                "house": 1500,
                "world": 1000,
                "best": 800
              },
              "in": {
                "the": 3000,
                "a": 2500
              }
            }
          }
        }
        """.trimIndent()

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(enCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "en")

        val all = engine2.allWords
        assertTrue("English corpus should have ~14 words, got ${all.size}", all.size >= 10)
        val words = all.map { it.word }
        assertTrue("'the' should be in English corpus", words.contains("the"))
        assertTrue("'house' should be in English corpus", words.contains("house"))
        assertTrue("'hello' should be in English corpus", words.contains("hello"))

        // Verificar que la palabra más frecuente es "the"
        assertEquals("Top English word should be 'the'", "the", all.first().word)
    }

    @Test
    fun `idioma ingles - predict con bigramas en ingles`() {
        val enCorpusJson = """
        {
          "en": {
            "unigrams": {
              "the": 100000,
              "house": 5000,
              "world": 2000,
              "best": 3000,
              "hello": 4000,
              "home": 6000
            },
            "bigrams": {
              "the": {
                "house": 1500,
                "world": 1000,
                "best": 800
              }
            }
          }
        }
        """.trimIndent()

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(enCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "en")

        // Bigram "the" debe predecir "house", "world", "best"
        val results = engine2.predict("", "the", 3)
        assertTrue("English bigram 'the' should predict something", results.isNotEmpty())
        assertTrue("'house' should be predicted after 'the'", results.contains("house"))
    }

    @Test
    fun `idioma ingles - searchPrefix encuentra palabras inglesas`() {
        val enCorpusJson = """
        {
          "en": {
            "unigrams": {
              "the": 100000,
              "house": 5000,
              "home": 4000,
              "hello": 3000
            }
          }
        }
        """.trimIndent()

        val mockContext2 = mockk<Context>()
        val mockAssets2 = mockk<AssetManager>()
        every { mockContext2.assets } returns mockAssets2
        every { mockAssets2.open("corpus.json") } returns ByteArrayInputStream(enCorpusJson.toByteArray())
        every { mockContext2.filesDir } returns tempDir

        val engine2 = PredictorEngine(mockContext2, "en")

        // "hello" < "ho" lexicographically (e < o), so prefix "ho" finds "home" and "house" but not "hello"
        val results = engine2.searchPrefix("ho")
        val words = results.map { it.word }
        assertTrue("Prefix 'ho' should find 'house'", words.contains("house"))
        assertTrue("Prefix 'ho' should find 'home'", words.contains("home"))

        // Usar prefijo "h" para encontrar todas las palabras que empiezan con h
        val allH = engine2.searchPrefix("h")
        val hWords = allH.map { it.word }
        assertTrue("Prefix 'h' should find 'hello'", hWords.contains("hello"))
        assertTrue("Prefix 'h' should find 'home'", hWords.contains("home"))
        assertTrue("Prefix 'h' should find 'house'", hWords.contains("house"))
    }
}
