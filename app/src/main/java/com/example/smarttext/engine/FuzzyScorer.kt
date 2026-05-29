package com.example.smarttext.engine

/**
 * Sistema de Lógica Difusa para ranking de sugerencias de texto.
 *
 * Variables de entrada:
 *   - Distancia Levenshtein (qué tan similar es el input a la palabra candidata)
 *   - Frecuencia de la palabra en el corpus
 *   - Contexto (si el bigrama predice esta palabra)
 *
 * Variable de salida:
 *   - Score de sugerencia (0-100)
 *
 * Reglas difusas:
 *   R1: IF lev IS baja AND freq IS alta  → excelente
 *   R2: IF lev IS baja AND ctx IS alto   → excelente
 *   R3: IF lev IS baja AND freq IS media → buena
 *   R4: IF lev IS media AND freq IS alta → buena
 *   R5: IF lev IS media AND freq IS media → aceptable
 *   R6: IF lev IS alta → malo
 *   R7: (default) → aceptable (basado en freq si nada más aplica)
 */
object FuzzyScorer {

    data class FuzzyValues(val baja: Double, val media: Double, val alta: Double)
    data class ContextValues(val bajo: Double, val alto: Double)

    /**
     * Calcula la distancia de Levenshtein entre dos strings.
     * Optimizado: usa arreglos de Int en vez de listas mutables.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a.length < b.length) return levenshteinDistance(s2, s1)
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val curr = IntArray(b.length + 1)
            curr[0] = i + 1
            for (j in b.indices) {
                val insert = prev[j + 1] + 1
                val delete = curr[j] + 1
                val subst = prev[j] + if (a[i] == b[j]) 0 else 1
                curr[j + 1] = minOf(insert, delete, subst)
            }
            prev = curr
        }
        return prev[b.length]
    }

    /**
     * Fuzzificación de la frecuencia de la palabra.
     * Los rangos están ajustados para el corpus con frecuencias 1-10000.
     */
    fun fuzzifyFrequency(freq: Int): FuzzyValues {
        // Baja (rara): 0 a 500
        val baja = when {
            freq <= 100 -> 1.0
            freq <= 500 -> (500 - freq) / 400.0
            else -> 0.0
        }

        // Media (moderada): 200 a 3000
        val media = when {
            freq <= 200 -> 0.0
            freq <= 1000 -> (freq - 200) / 800.0
            freq <= 2000 -> 1.0
            freq <= 3000 -> (3000 - freq) / 1000.0
            else -> 0.0
        }

        // Alta (común): 2000+
        val alta = when {
            freq <= 2000 -> 0.0
            freq <= 5000 -> (freq - 2000) / 3000.0
            else -> 1.0
        }

        return FuzzyValues(baja, media, alta)
    }

    /**
     * Fuzzificación de la distancia Levenshtein.
     * Determina qué tan cerca está el input de la palabra candidata.
     */
    fun fuzzifyLevenshtein(dist: Int): FuzzyValues {
        // Baja (cerca): 0 a 2
        val baja = when {
            dist == 0 -> 1.0
            dist <= 2 -> (2 - dist) / 2.0
            else -> 0.0
        }

        // Media (parcial): 1 a 5
        val media = when {
            dist <= 1 -> 0.0
            dist <= 3 -> (dist - 1) / 2.0
            dist <= 5 -> (5 - dist) / 2.0
            else -> 0.0
        }

        // Alta (lejos): 3+
        val alta = when {
            dist <= 3 -> 0.0
            dist <= 6 -> (dist - 3) / 3.0
            else -> 1.0
        }

        return FuzzyValues(baja, media, alta)
    }

    /**
     * Fuzzificación booleana del contexto.
     */
    fun fuzzifyContext(hasContext: Boolean): ContextValues {
        return if (hasContext) {
            ContextValues(bajo = 0.0, alto = 1.0)
        } else {
            ContextValues(bajo = 1.0, alto = 0.0)
        }
    }

    /**
     * Evaluación de reglas difusas usando inferencia Mamdani.
     * Returns: score defuzzificado (0-100)
     */
    fun evaluateRules(freqFuz: FuzzyValues, levFuz: FuzzyValues, ctxFuz: ContextValues): Double {
        // R1: IF lev IS baja AND freq IS alta → excelente
        val r1 = minOf(levFuz.baja, freqFuz.alta)

        // R2: IF lev IS baja AND ctx IS alto → excelente
        val r2 = minOf(levFuz.baja, ctxFuz.alto)

        // R3: IF lev IS baja AND freq IS media → buena
        val r3 = minOf(levFuz.baja, freqFuz.media)

        // R4: IF lev IS media AND freq IS alta → buena
        val r4 = minOf(levFuz.media, freqFuz.alta)

        // R5: IF lev IS media AND freq IS media → aceptable
        val r5 = minOf(levFuz.media, freqFuz.media)

        // R6: IF lev IS alta → malo
        val r6 = levFuz.alta

        // R7: default - si ninguna regla aplica, usar freq como baseline
        val r7Default = maxOf(freqFuz.media * 0.5, freqFuz.alta * 0.3)

        // Agregación: combinar reglas (máximo para cada categoría)
        val scoreExcelente = maxOf(r1, r2)
        val scoreBuena = maxOf(r3, r4)
        val scoreAceptable = maxOf(r5, r7Default)
        val scoreMalo = r6

        // Defuzzificación: método del centroide
        // Centroides: malo=25, aceptable=50, buena=75, excelente=100
        val numerator = (scoreMalo * 25.0) +
                        (scoreAceptable * 50.0) +
                        (scoreBuena * 75.0) +
                        (scoreExcelente * 100.0)
        val denominator = scoreMalo + scoreAceptable + scoreBuena + scoreExcelente

        if (denominator == 0.0) {
            // Fallback: score basado solo en frecuencia
            return freqFuz.alta * 60.0 + freqFuz.media * 40.0 + freqFuz.baja * 10.0
        }

        return numerator / denominator
    }

    /**
     * Calcula el score difuso para una palabra candidata.
     *
     * @param targetWord La palabra candidata del diccionario
     * @param currentInput El texto parcial escrito por el usuario
     * @param frequency Frecuencia de la palabra en el corpus
     * @param hasContext Si hay contexto (bigrama previo)
     * @return Score entre 0.0 y 100.0
     */
    fun getScore(
        targetWord: String,
        currentInput: String,
        frequency: Int,
        hasContext: Boolean
    ): Double {
        val dist = levenshteinDistance(currentInput, targetWord)
        val freqFuz = fuzzifyFrequency(frequency)
        val levFuz = fuzzifyLevenshtein(dist)
        val ctxFuz = fuzzifyContext(hasContext)
        return evaluateRules(freqFuz, levFuz, ctxFuz)
    }
}
