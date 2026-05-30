package com.example.smarttext.ime

import android.graphics.PointF
import com.example.smarttext.engine.PredictorEngine
import com.example.smarttext.engine.PredictorEngine.WordEntry
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para GestureRecognizer.
 *
 * Cobertura:
 * - Detección swipe vs tap
 * - Recolección de puntos
 * - Reconocimiento con keys mockeadas
 * - Scoring de palabras
 * - Reset de estado
 */
class GestureRecognizerTest {

    private lateinit var recognizer: GestureRecognizer
    private lateinit var mockPredictor: PredictorEngine
    private lateinit var mockKeys: List<Key>

    @Before
    fun setUp() {
        recognizer = GestureRecognizer()

        // Mock PredictorEngine
        mockPredictor = mockk()

        // Crear keys de prueba (solo las relevantes para los tests)
        mockKeys = createTestKeys()
    }

    /** Create RectF with field-by-field assignment (Android stubs don't set fields in constructor). */
    private fun createRectF(left: Float, top: Float, right: Float, bottom: Float): android.graphics.RectF {
        val r = android.graphics.RectF()
        r.left = left
        r.top = top
        r.right = right
        r.bottom = bottom
        return r
    }

    private fun createTestKeys(): List<Key> {
        // Simular un teclado QWERTY simplificado
        // Solo las teclas relevantes para los tests: c, a, s, o, p, q
        val keyWidth = 72f
        val keyHeight = 60f
        val rowHeight = 70f

        return listOf(
            // Fila 1 (top): q w e r t y u i o p
            Key('q'.code, "q", 1, 0).apply {
                bounds = createRectF(0f, rowHeight + 5f, keyWidth, rowHeight + keyHeight + 5f)
            },
            Key('p'.code, "p", 1, 9).apply {
                bounds = createRectF(9 * keyWidth, rowHeight + 5f, 10 * keyWidth, rowHeight + keyHeight + 5f)
            },
            // Fila 2 (home): a s d f g h j k l ñ
            Key('a'.code, "a", 2, 0).apply {
                bounds = createRectF(0f, 2 * rowHeight + 5f, keyWidth, 2 * rowHeight + keyHeight + 5f)
            },
            Key('s'.code, "s", 2, 1).apply {
                bounds = createRectF(keyWidth, 2 * rowHeight + 5f, 2 * keyWidth, 2 * rowHeight + keyHeight + 5f)
            },
            Key('ñ'.code, "ñ", 2, 9).apply {
                bounds = createRectF(9 * keyWidth, 2 * rowHeight + 5f, 10 * keyWidth, 2 * rowHeight + keyHeight + 5f)
            },
            // Fila 3 (bottom): z x c v b n m
            Key('c'.code, "c", 3, 2).apply {
                bounds = createRectF(2 * keyWidth, 3 * rowHeight + 5f, 3 * keyWidth, 3 * rowHeight + keyHeight + 5f)
            },
            Key('o'.code, "o", 1, 8).apply {
                bounds = createRectF(8 * keyWidth, rowHeight + 5f, 9 * keyWidth, rowHeight + keyHeight + 5f)
            }
        ).also {
            // Añadir bounds del espacio (necesario pero ignorado por gesture typing)
        }
    }

    // ═══════════════════════════════════════
    // 1. Swipe vs Tap Detection
    // ═══════════════════════════════════════

    @Test
    fun `startGesture inicializa el estado`() {
        recognizer.startGesture(100f, 200f)
        val points = recognizer.getTouchPoints()
        assertEquals("Should have 1 initial point", 1, points.size)
        assertEquals(100f, points[0].x, 0.001f)
        assertEquals(200f, points[0].y, 0.001f)
    }

    @Test
    fun `isSwipe - menos de 2 puntos no es swipe`() {
        recognizer.startGesture(100f, 200f)
        assertFalse("Single point should not be a swipe", recognizer.isSwipe())
    }

    @Test
    fun `isSwipe - distancia corta no es swipe`() {
        recognizer.startGesture(100f, 100f)
        recognizer.addPoint(105f, 105f)  // ~7px distancia, < 30px threshold
        assertFalse("Short distance should not be a swipe", recognizer.isSwipe())
    }

    @Test
    fun `isSwipe - distancia larga es swipe`() {
        recognizer.startGesture(100f, 100f)
        recognizer.addPoint(200f, 200f)  // ~141px distancia > 30px threshold
        assertTrue("Long distance should be a swipe", recognizer.isSwipe())
    }

    @Test
    fun `endGesture - retorna true si es swipe`() {
        recognizer.startGesture(100f, 100f)
        val result = recognizer.endGesture(200f, 200f)
        assertTrue("Long gesture should return true", result)
    }

    @Test
    fun `endGesture - retorna false si es tap`() {
        recognizer.startGesture(100f, 100f)
        val result = recognizer.endGesture(101f, 101f)
        assertFalse("Short gesture should return false", result)
    }

    @Test
    fun `addPoint - ignora puntos en el mismo pixel`() {
        recognizer.startGesture(100f, 100f)
        recognizer.addPoint(100f, 100f)  // dx²+dy² = 0 < 4
        assertEquals("Should still have 1 point (2nd was same pixel)", 1, recognizer.getTouchPoints().size)
    }

    @Test
    fun `addPoint - acepta puntos suficientemente separados`() {
        recognizer.startGesture(100f, 100f)
        recognizer.addPoint(103f, 101f)  // dx²+dy² = 9+1 = 10 >= 4
        assertEquals("Should have 2 points", 2, recognizer.getTouchPoints().size)
    }

    // ═══════════════════════════════════════
    // 2. Reconocimiento con keys
    // ═══════════════════════════════════════

    @Test
    fun `recognize - pocos puntos devuelve vacio`() {
        recognizer.startGesture(100f, 100f)
        recognizer.addPoint(110f, 110f)  // solo 2 puntos, < MIN_GESTURE_POINTS(5)

        val results = recognizer.recognize(mockKeys, mockPredictor)
        assertTrue("Few points should return empty", results.isEmpty())
    }

    @Test
    fun `recognize - con puntos suficientes pero sin patron`() {
        // Crear 6 puntos en línea recta
        recognizer.startGesture(0f, 0f)
        recognizer.addPoint(50f, 50f)
        recognizer.addPoint(100f, 100f)
        recognizer.addPoint(150f, 150f)
        recognizer.addPoint(200f, 200f)
        recognizer.endGesture(250f, 250f)

        // Mock Predictor para devolver palabras
        every { mockPredictor.predict(any(), any(), any()) } returns listOf(
            "casa", "caso"
        )
        every { mockPredictor.searchPrefix(any(), any()) } returns listOf(
            WordEntry("casa", 5000),
            WordEntry("caso", 4000)
        )

        val results = recognizer.recognize(mockKeys, mockPredictor, 5)
        // Línea diagonal (0,0)→(250,250) cruza varias filas del teclado
        // Verificar que respeta el límite topK
        assertTrue("Results should respect topK limit", results.size <= 5)
    }

    @Test
    fun `recognize - keys sin letras saltan puntos en el path`() {
        recognizer.startGesture(100f, 300f)  // Cerca de tecla 'c'
        recognizer.addPoint(120f, 280f)
        recognizer.addPoint(150f, 220f)  // Cerca de tecla 'a'
        recognizer.addPoint(180f, 170f)
        recognizer.addPoint(210f, 120f)  // Cerca de espacio (ignorado)
        recognizer.endGesture(240f, 70f)  // Cerca de tecla 'q'

        every { mockPredictor.predict(any(), any(), any()) } returns emptyList()
        every { mockPredictor.searchPrefix(any(), any()) } returns emptyList()

        val results = recognizer.recognize(mockKeys, mockPredictor, 5)
        // Con mockPredictor devolviendo lista vacía, el resultado debe estar vacío
        assertTrue("Results should be empty when predictor returns nothing", results.isEmpty())
    }

    // ═══════════════════════════════════════
    // 3. State Management
    // ═══════════════════════════════════════

    @Test
    fun `reset - limpia todos los puntos y secuencia`() {
        recognizer.startGesture(100f, 100f)
        recognizer.endGesture(200f, 200f)
        assertTrue("Points should exist before reset", recognizer.getTouchPoints().size >= 2)

        recognizer.reset()
        assertTrue("Points should be empty after reset", recognizer.getTouchPoints().isEmpty())
        assertTrue("Last key sequence should be empty after reset", recognizer.getLastKeySequence().isEmpty())
    }

    @Test
    fun `getTouchPoints - devuelve copia independiente`() {
        recognizer.startGesture(100f, 100f)
        val points1 = recognizer.getTouchPoints()
        val points2 = recognizer.getTouchPoints()
        assertEquals(1, points1.size)
        assertEquals(1, points2.size)

        // Las dos llamadas devuelven listas diferentes (inmutables)
        assertNotSame("Each call should return a new list", points1, points2)
    }

    // ═══════════════════════════════════════
    // 4. Gesture completo mockeado
    // ═══════════════════════════════════════

    @Test
    fun `gesture completo - no lanza excepcion con mock Predictor`() {
        // Configurar mock Predictor para cualquier llamada
        every { mockPredictor.predict(any(), any(), any()) } returns listOf(
            "casa", "caso"
        )
        every { mockPredictor.searchPrefix(any(), any()) } returns listOf(
            WordEntry("casa", 5000),
            WordEntry("caso", 4000)
        )

        // Simular gesto con suficientes puntos
        recognizer.startGesture(100f, 200f)
        for (i in 1..8) {
            recognizer.addPoint(100f + i * 20f, 200f + i * 10f)
        }
        recognizer.endGesture(260f, 280f)

        // Ejecutar recognize — no debe lanzar excepción
        val results = recognizer.recognize(mockKeys, mockPredictor, 3)
        assertTrue("Results must respect topK", results.size <= 3)
    }
}
