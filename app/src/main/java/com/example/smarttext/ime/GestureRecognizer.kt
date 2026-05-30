package com.example.smarttext.ime

import android.graphics.PointF
import android.util.Log
import com.example.smarttext.engine.PredictorEngine
import com.example.smarttext.engine.PredictorEngine.WordEntry

/**
 * Procesa gestos de deslizamiento (swipe/glide) sobre el teclado.
 *
 * Algoritmo:
 * 1. Recolecta puntos táctiles durante el gesto
 * 2. Interpola puntos faltantes entre muestras distantes
 * 3. Para cada punto, encuentra la tecla más cercana (por distancia al centro)
 * 4. Construye secuencia de teclas visitadas (sin duplicados consecutivos)
 * 5. Usa PredictorEngine para generar candidatos que coincidan con el patrón
 * 6. Scorer: combina distancia del path, longitud de palabra, frecuencia
 */
class GestureRecognizer {

    companion object {
        private const val TAG = "SmartIME.Gesture"
        /** Distance threshold to detect a swipe vs tap (in pixels). */
        private const val SWIPE_DISTANCE_THRESHOLD = 30f
        /** Max distance from touch point to key center to consider it "on" the key. */
        private const val KEY_HIT_RADIUS = 1.5f
        /** Interpolation step in pixels between touch samples. */
        private const val INTERPOLATION_STEP = 12f
        /** Minimum points to consider a valid gesture. */
        private const val MIN_GESTURE_POINTS = 5
        /** Max length for gesture-matched words (avoid unreasonably long matches). */
        private const val MAX_GESTURE_WORD_LENGTH = 15
    }

    data class GestureResult(
        val word: String,
        val score: Float
    )

    private val touchPoints = mutableListOf<PointF>()
    private var startTime = 0L
    private var endTime = 0L
    private var lastKeySequence = listOf<String>()

    /** Start tracking a new gesture. */
    fun startGesture(x: Float, y: Float) {
        touchPoints.clear()
        touchPoints.add(createPoint(x, y))
        startTime = System.currentTimeMillis()
        lastKeySequence = emptyList()
    }

    /** Add a point during the gesture. */
    fun addPoint(x: Float, y: Float) {
        val last = touchPoints.last()
        // Skip if the point is too close to the last one
        val dx = x - last.x
        val dy = y - last.y
        if (dx * dx + dy * dy < 25f) return
        touchPoints.add(createPoint(x, y))
    }

    /** End tracking. Returns true if the gesture is a swipe (not a tap). */
    fun endGesture(x: Float, y: Float): Boolean {
        addPoint(x, y)
        endTime = System.currentTimeMillis()
        return isSwipe()
    }

    /** Check if the tracked points constitute a swipe vs a tap. */
    fun isSwipe(): Boolean {
        if (touchPoints.size < 2) return false
        var totalDist = 0f
        for (i in 1 until touchPoints.size) {
            totalDist += dist(touchPoints[i - 1], touchPoints[i])
        }
        return totalDist >= SWIPE_DISTANCE_THRESHOLD
    }

    /**
     * Recognize the gesture and return word candidates.
     *
     * @param keys The keyboard keys with computed bounds.
     * @param predictor The PredictorEngine for word suggestions.
     * @param topK Number of candidates to return.
     * @return List of (word, score) pairs sorted by score descending.
     */
    fun recognize(
        keys: List<Key>,
        predictor: PredictorEngine,
        topK: Int = 5
    ): List<GestureResult> {
        if (touchPoints.size < MIN_GESTURE_POINTS) return emptyList()

        // 1. Interpolate points
        val interpolated = interpolatePoints(touchPoints)

        // 2. Build key sequence from interpolated path
        val keySequence = traceKeys(interpolated, keys)
        if (keySequence.isEmpty()) return emptyList()
        lastKeySequence = keySequence

        val pattern = keySequence.joinToString("")
        Log.d(TAG, "Swipe pattern: '$pattern' from ${touchPoints.size} raw pts -> ${interpolated.size} interpolated")

        // 3. Generate candidates from PredictorEngine
        return generateCandidates(pattern, predictor, topK)
    }

    /** Get the raw touch points (for drawing the trail). */
    fun getTouchPoints(): List<PointF> = touchPoints.toList()

    /** Get the last detected key sequence. */
    fun getLastKeySequence(): List<String> = lastKeySequence

    // ────────  Internal  ────────

    private fun interpolatePoints(points: List<PointF>): List<PointF> {
        if (points.size < 2) return points
        val result = mutableListOf(points[0])

        for (i in 1 until points.size) {
            val p1 = points[i - 1]
            val p2 = points[i]
            val d = dist(p1, p2)
            if (d > INTERPOLATION_STEP) {
                val steps = (d / INTERPOLATION_STEP).toInt()
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps
                    result.add(createPoint(
                        p1.x + (p2.x - p1.x) * t,
                        p1.y + (p2.y - p1.y) * t
                    ))
                }
            }
            result.add(p2)
        }
        return result
    }

    private fun traceKeys(points: List<PointF>, keys: List<Key>): List<String> {
        val visited = mutableListOf<String>()
        var lastKey: String? = null

        for (pt in points) {
            val nearest = findNearestKey(pt, keys) ?: continue
            if (nearest != lastKey) {
                visited.add(nearest)
                lastKey = nearest
            }
        }

        return visited
    }

    private fun findNearestKey(point: PointF, keys: List<Key>): String? {
        var bestKey: String? = null
        var bestDist = Float.MAX_VALUE
        var refWidth = 50f

        // Only consider letter keys and ñ for gesture typing
        for (key in keys) {
            if (key.code !in 'a'.code..'z'.code && key.code != 'ñ'.code) continue
            val cx = key.centerX
            val cy = key.centerY
            val dx = point.x - cx
            val dy = point.y - cy
            val d = dx * dx + dy * dy

            if (d < bestDist) {
                bestDist = d
                bestKey = key.label
                refWidth = key.width
            }
        }

        // Only return if close enough (within KEY_HIT_RADIUS times the key width)
        val maxDistSq = (KEY_HIT_RADIUS * refWidth) * (KEY_HIT_RADIUS * refWidth)
        return if (bestDist <= maxDistSq) bestKey else null
    }

    /**
     * Generate word candidates from a swipe pattern.
     *
     * Uses PredictorEngine.predict() which already has FuzzyScorer with
     * Levenshtein distance, frequency scoring, and context weighting.
     * This is more robust than the previous manual scoring approach.
     */
    private fun generateCandidates(
        pattern: String,
        predictor: PredictorEngine,
        topK: Int
    ): List<GestureResult> {
        val results = mutableMapOf<String, Float>()

        // Use PredictorEngine's predict() with the full pattern as "currentWord"
        // This leverages FuzzyScorer (Levenshtein + freq + context) for matching
        val predictions = predictor.predict(pattern, null, topK * 3)
        for ((i, word) in predictions.withIndex()) {
            if (word.length > MAX_GESTURE_WORD_LENGTH) continue
            // Predict already returns FuzzyScorer-ranked results; assign descending scores
            // so they outrank any prefix-fallback results
            results[word] = (100f - i * 2f).coerceAtLeast(50f)
        }

        if (results.size < topK && pattern.length >= 2) {
            // Strategy 2: try each character in pattern as a prefix
            val first = pattern.first().toString()
            val second = if (pattern.length > 1) pattern.substring(0, 2) else first
            
            // Try 2-char prefix first
            val prefixResults = predictor.searchPrefix(second, minLength = pattern.length - 1)
            for (we in prefixResults.take(100)) {
                if (we.word in results || we.word.length > MAX_GESTURE_WORD_LENGTH) continue
                val matchScore = scoreWord(we.word, pattern, we.frequency)
                if (matchScore > 0f) {
                    results[we.word] = matchScore
                }
            }

            // Try single char prefix if still not enough
            if (results.size < topK) {
                val broadResults = predictor.searchPrefix(first)
                for (we in broadResults.take(150)) {
                    if (we.word in results || we.word.length > MAX_GESTURE_WORD_LENGTH) continue
                    val matchScore = scoreWord(we.word, pattern, we.frequency)
                    if (matchScore > 0f) {
                        results[we.word] = matchScore
                    }
                }
            }
        }

        // Sort by score descending, take topK
        return results.entries
            .sortedByDescending { it.value }
            .take(topK)
            .map { GestureResult(it.key, it.value) }
    }

    /**
     * Score a word against the swipe pattern.
     *
     * Factors:
     * - Levenshtein distance (how many insertions/deletions to match)
     * - Positional match: does the pattern appear as a subsequence?
     * - Frequency boost
     *
     * Returns score 0-100.
     */
    private fun scoreWord(word: String, pattern: String, frequency: Int): Float {
        if (word.length < pattern.length - 1 || word.length > pattern.length + 2) {
            return 0f
        }

        // Subsequence check: all chars in pattern appear in order in the word
        var pi = 0
        for (ch in word) {
            if (pi < pattern.length && ch == pattern[pi]) pi++
        }
        val subsequenceRatio = pi.toFloat() / pattern.length

        // More permissive: accept partial matches with frequency boost
        if (subsequenceRatio < 0.4f) return 0f

        // Levenshtein distance
        val lev = levenshtein(word, pattern)
        val maxLen = maxOf(word.length, pattern.length, 1)

        // Score components
        val levScore = 1f - (lev.toFloat() / maxLen)
        val lengthScore = 1f - (kotlin.math.abs(word.length - pattern.length).toFloat() / maxLen)
        val freqScore = minOf(1f, kotlin.math.log10((frequency + 1).toFloat()) / 4f)

        // Weighted score: emphasize frequency for common words, subsequence for match quality
        val finalScore = (subsequenceRatio * 0.25f +
                         levScore * 0.25f +
                         lengthScore * 0.10f +
                         freqScore * 0.40f) * 100f

        return finalScore
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val a = s1.lowercase()
        val b = s2.lowercase()
        if (a.length < b.length) return levenshtein(s2, s1)
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

    /** Create a PointF with field-by-field assignment (works in Android stubs where constructor may not set fields). */
    private fun createPoint(x: Float, y: Float): PointF {
        val pt = PointF()
        pt.x = x
        pt.y = y
        return pt
    }

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    fun reset() {
        touchPoints.clear()
        lastKeySequence = emptyList()
    }
}
