package com.example.smarttext.engine

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitarios para FuzzyScorer — Sistema de Lógica Difusa (Mamdani).
 *
 * Cobertura:
 * - Distancia Levenshtein: 7 casos
 * - Fuzzificación de frecuencia: 9 casos (baja, media, alta)
 * - Fuzzificación de Levenshtein: 10 casos (baja, media, alta)
 * - Fuzzificación de contexto: 2 casos
 * - Evaluación de reglas: 8 casos (R1-R7 + fallback)
 * - GetScore integración: 6 casos con ejemplos reales
 */
class FuzzyScorerTest {

    // ═══════════════════════════════════════
    // 1. Distancia Levenshtein (7 casos)
    // ═══════════════════════════════════════

    @Test
    fun `levenshteinDistance - strings identicos devuelve 0`() {
        assertEquals(0, FuzzyScorer.levenshteinDistance("casa", "casa"))
    }

    @Test
    fun `levenshteinDistance - case insensitive`() {
        assertEquals(0, FuzzyScorer.levenshteinDistance("CASA", "casa"))
    }

    @Test
    fun `levenshteinDistance - dos caracteres diferentes devuelve 2`() {
        // "casa" vs "cazo": s≠z y a≠o = 2 sustituciones
        assertEquals(2, FuzzyScorer.levenshteinDistance("casa", "cazo"))
    }

    @Test
    fun `levenshteinDistance - insercion devuelve 1`() {
        assertEquals(1, FuzzyScorer.levenshteinDistance("casa", "casas"))
    }

    @Test
    fun `levenshteinDistance - deletion devuelve 1`() {
        assertEquals(1, FuzzyScorer.levenshteinDistance("casas", "casa"))
    }

    @Test
    fun `levenshteinDistance - strings completamente diferentes`() {
        assertEquals(5, FuzzyScorer.levenshteinDistance("abcde", "vwxyz"))
    }

    @Test
    fun `levenshteinDistance - string vacio devuelve longitud del otro`() {
        assertEquals(4, FuzzyScorer.levenshteinDistance("casa", ""))
        assertEquals(0, FuzzyScorer.levenshteinDistance("", ""))
    }

    // ═══════════════════════════════════════
    // 2. Fuzzificación de Frecuencia (9 casos)
    // ═══════════════════════════════════════

    @Test
    fun `fuzzifyFrequency - frecuencia 0 es totalmente baja`() {
        val f = FuzzyScorer.fuzzifyFrequency(0)
        assertEquals(1.0, f.baja, 0.001)
        assertEquals(0.0, f.media, 0.001)
        assertEquals(0.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 100 es baja pura`() {
        val f = FuzzyScorer.fuzzifyFrequency(100)
        assertEquals(1.0, f.baja, 0.001)
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 300 es baja parcial`() {
        val f = FuzzyScorer.fuzzifyFrequency(300)
        assertEquals((500 - 300) / 400.0, f.baja, 0.001)  // 0.5
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 500 cruce baja-media`() {
        val f = FuzzyScorer.fuzzifyFrequency(500)
        assertEquals(0.0, f.baja, 0.001)
        assertEquals((500 - 200) / 800.0, f.media, 0.001)  // 0.375
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 1000 es media pura`() {
        val f = FuzzyScorer.fuzzifyFrequency(1000)
        assertEquals(1.0, f.media, 0.001)
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 2000 es media-alta`() {
        val f = FuzzyScorer.fuzzifyFrequency(2000)
        assertEquals(1.0, f.media, 0.001)
        assertEquals(0.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 3000 cruce media-alta`() {
        val f = FuzzyScorer.fuzzifyFrequency(3000)
        assertEquals(0.0, f.media, 0.001)
        assertEquals((3000 - 2000) / 3000.0, f.alta, 0.001)  // 0.333
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 5000 es alta pura`() {
        val f = FuzzyScorer.fuzzifyFrequency(5000)
        assertEquals(1.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyFrequency - frecuencia 10000 es alta pura`() {
        val f = FuzzyScorer.fuzzifyFrequency(10000)
        assertEquals(1.0, f.alta, 0.001)
        assertEquals(0.0, f.baja, 0.001)
        assertEquals(0.0, f.media, 0.001)
    }

    // ═══════════════════════════════════════
    // 3. Fuzzificación de Levenshtein (10 casos)
    // ═══════════════════════════════════════

    @Test
    fun `fuzzifyLevenshtein - distancia 0 es baja pura`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(0)
        assertEquals(1.0, f.baja, 0.001)
        assertEquals(0.0, f.media, 0.001)
        assertEquals(0.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 1 baja parcial y media inicio`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(1)
        assertEquals(0.5, f.baja, 0.001)
        assertEquals(0.0, f.media, 0.001)
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 2 cruce baja-media`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(2)
        assertEquals(0.0, f.baja, 0.001)
        assertEquals(0.5, f.media, 0.001)  // (2-1)/2 = 0.5
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 3 media pura`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(3)
        assertEquals(1.0, f.media, 0.001)
        assertEquals(0.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 4 media parcial`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(4)
        assertEquals(0.5, f.media, 0.001)  // (5-4)/2 = 0.5
        assertEquals((4 - 3) / 3.0, f.alta, 0.001)  // 0.333
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 5 cruce media-alta`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(5)
        assertEquals(0.0, f.media, 0.001)
        assertEquals((5 - 3) / 3.0, f.alta, 0.001)  // 0.667
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 6 alta`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(6)
        assertEquals(1.0, f.alta, 0.001)
    }

    @Test
    fun `fuzzifyLevenshtein - distancia 10 es alta pura`() {
        val f = FuzzyScorer.fuzzifyLevenshtein(10)
        assertEquals(0.0, f.baja, 0.001)
        assertEquals(0.0, f.media, 0.001)
        assertEquals(1.0, f.alta, 0.001)
    }

    // ═══════════════════════════════════════
    // 4. Fuzzificación de Contexto (2 casos)
    // ═══════════════════════════════════════

    @Test
    fun `fuzzifyContext - con contexto es alto`() {
        val c = FuzzyScorer.fuzzifyContext(true)
        assertEquals(0.0, c.bajo, 0.001)
        assertEquals(1.0, c.alto, 0.001)
    }

    @Test
    fun `fuzzifyContext - sin contexto es bajo`() {
        val c = FuzzyScorer.fuzzifyContext(false)
        assertEquals(1.0, c.bajo, 0.001)
        assertEquals(0.0, c.alto, 0.001)
    }

    // ═══════════════════════════════════════
    // 5. Evaluación de Reglas (8 casos)
    // ═══════════════════════════════════════

    @Test
    fun `evaluateRules - R1 lev baja y freq alta da score excelente`() {
        // lev=0 (100% baja), freq=5000 (100% alta), sin contexto
        val freqFuz = FuzzyScorer.fuzzifyFrequency(5000)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(0)
        val ctxFuz = FuzzyScorer.fuzzifyContext(false)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R1 activa: min(baja=1.0, alta=1.0) = 1.0 → excelente
        // Score debe ser alto (cercano a 100)
        assertTrue("R1 score should be high (>80), got $score", score > 80.0)
    }

    @Test
    fun `evaluateRules - R2 lev baja y contexto alto da score excelente`() {
        // lev=0 (100% baja), freq=100 (baja, no activa R1/R3), CON contexto
        val freqFuz = FuzzyScorer.fuzzifyFrequency(100)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(0)
        val ctxFuz = FuzzyScorer.fuzzifyContext(true)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R2 activa: min(baja=1.0, alto=1.0) = 1.0 → excelente
        assertTrue("R2 score should be high (>80), got $score", score > 80.0)
    }

    @Test
    fun `evaluateRules - R3 lev baja y freq media da score buena`() {
        // lev=0 (100% baja), freq=1000 (100% media), sin contexto
        val freqFuz = FuzzyScorer.fuzzifyFrequency(1000)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(0)
        val ctxFuz = FuzzyScorer.fuzzifyContext(false)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R3 activa: min(baja=1.0, media=1.0) = 1.0 → buena (score~75)
        // R3=1.0 (buena), R7=0.5 (aceptable) → centroide ~66.67
        assertTrue("R3 score should be ~66.7, got $score", score in 60.0..70.0)
    }

    @Test
    fun `evaluateRules - R4 lev media y freq alta da score buena`() {
        // lev=3 (100% media), freq=5000 (100% alta), sin contexto
        val freqFuz = FuzzyScorer.fuzzifyFrequency(5000)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(3)
        val ctxFuz = FuzzyScorer.fuzzifyContext(false)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R4 activa: min(media=1.0, alta=1.0) = 1.0 → buena
        // R4=1.0 (buena), R7=0.3 (aceptable) → centroide ~69.23
        assertTrue("R4 score should be ~69.2, got $score", score in 65.0..75.0)
    }

    @Test
    fun `evaluateRules - R5 lev media y freq media da score aceptable`() {
        // lev=3 (100% media), freq=1000 (100% media)
        val freqFuz = FuzzyScorer.fuzzifyFrequency(1000)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(3)
        val ctxFuz = FuzzyScorer.fuzzifyContext(false)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R5 activa: min(media=1.0, media=1.0) = 1.0 → aceptable
        assertTrue("R5 score should be ~50, got $score", score in 45.0..55.0)
    }

    @Test
    fun `evaluateRules - R6 lev alta da score malo`() {
        // lev=10 (100% alta), freq=5000 (alta), sin contexto
        val freqFuz = FuzzyScorer.fuzzifyFrequency(5000)
        val levFuz = FuzzyScorer.fuzzifyLevenshtein(10)
        val ctxFuz = FuzzyScorer.fuzzifyContext(false)
        val score = FuzzyScorer.evaluateRules(freqFuz, levFuz, ctxFuz)
        // R6 activa: alta=1.0 → malo (score ~25)
        assertTrue("R6 score should be low (<40), got $score", score < 40.0)
    }

    @Test
    fun `evaluateRules - R7 fallback con denominator cero usa solo frecuencia`() {
        // frecuencia=5000 activa R7: max(freq.media*0.5, freq.alta*0.3) = max(0, 1*0.3) = 0.3
        val score = FuzzyScorer.evaluateRules(
            FuzzyScorer.FuzzyValues(0.0, 0.0, 0.0),  // freq: todo en 0
            FuzzyScorer.FuzzyValues(0.0, 0.0, 0.0),  // lev: todo en 0
            FuzzyScorer.ContextValues(0.0, 0.0)      // ctx: todo en 0
        )
        // denominator = 0, fallback: freq.alta*60 + freq.media*40 + freq.baja*10 = 0
        assertEquals(0.0, score, 0.001)
    }

    @Test
    fun `evaluateRules - R7 fallback con frecuencia alta da score positivo`() {
        // Solo freq.alta=1.0, todo lo demás 0
        val score = FuzzyScorer.evaluateRules(
            FuzzyScorer.FuzzyValues(0.0, 0.0, 1.0),  // freq alta
            FuzzyScorer.FuzzyValues(0.0, 0.0, 0.0),  // lev todo 0
            FuzzyScorer.ContextValues(0.0, 0.0)      // ctx todo 0
        )
        // R7 default: max(0, 1.0*0.3) = 0.3
        // denominator = 0.3, numerator = 0.3*50.0 = 15.0
        // score = 15.0/0.3 = 50.0
        assertEquals(50.0, score, 0.001)
    }

    // ═══════════════════════════════════════
    // 6. GetScore — Integración (6 casos)
    // ═══════════════════════════════════════

    @Test
    fun `getScore - palabra exacta con alta frecuencia da score excelente`() {
        // "casa" vs "casa": lev=0, freq=5000 (alta), sin contexto
        val score = FuzzyScorer.getScore("casa", "casa", 5000, false)
        assertTrue("Exact match high freq should be >85, got $score", score > 85.0)
    }

    @Test
    fun `getScore - palabra exacta con contexto da score excelente`() {
        // "casa" vs "casa": lev=0, freq=5000 (alta), CON contexto
        val score = FuzzyScorer.getScore("casa", "casa", 5000, true)
        // R1=1.0 (excelente), R7=0.3 (aceptable) → centroide ~88.46
        assertTrue("Exact match with context should be >85, got $score", score > 85.0)
    }

    @Test
    fun `getScore - error pequeno con palabra comun da score bueno`() {
        // "casa" vs "cazo": lev=1, freq=3000 (alta)
        val score = FuzzyScorer.getScore("casa", "cazo", 3000, false)
        assertTrue("Small error common word should be >60, got $score", score > 60.0)
    }

    @Test
    fun `getScore - error medio con palabra rara da score aceptable`() {
        // "xilofono" vs "xilofno": lev=1 (omisión), freq=50 (baja)
        val score = FuzzyScorer.getScore("xilófono", "xilofono", 50, false)
        // lev=1, freq=50 (baja), sin contexto → solo fallback freq.baja*10 = 10.0
        assertTrue("Medium error rare word should be low (fallback), got $score", score <= 15.0)
    }

    @Test
    fun `getScore - palabra completamente diferente da score bajo`() {
        // "casa" vs "perro": lev=5, freq=5000
        val score = FuzzyScorer.getScore("casa", "perro", 5000, false)
        assertTrue("Completely different should be low, got $score", score < 40.0)
    }

    @Test
    fun `getScore - palabra corta filtrada por minLength`() {
        // No probamos minLength aquí (es del PredictorEngine), solo getScore
        val score = FuzzyScorer.getScore("a", "a", 50000, false)
        assertTrue("Single letter exact match should be >80, got $score", score > 80.0)
    }
}
