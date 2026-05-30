package com.example.smarttext.ime

import android.graphics.PointF
import android.util.Log
import com.example.smarttext.engine.PredictorEngine
import com.example.smarttext.engine.PredictorEngine.WordEntry
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max

/**
 * Gesture (glide/swipe) typing recognizer for SmartText keyboard.
 *
 * ## Algorithm overview
 *
 * 1. **Point collection** — touch points collected during the gesture
 * 2. **Resampling** — path resampled to exactly [RESAMPLE_COUNT] equidistant points
 * 3. **Key sequence detection** — each resampled point finds its nearest letter key;
 *    consecutive duplicates collapsed
 * 4. **Ideal path generation** — for each candidate word, compute the ideal path
 *    through key centers
 * 5. **DTW matching** — compare the user's touch path against the ideal key path
 *    for each candidate using Dynamic Time Warping
 * 6. **Scoring** — hybrid score: DTW spatial similarity + Levenshtein + frequency +
 *    length bonus
 */
class GestureRecognizer {

    companion object {
        private const val TAG = "SmartIME.Gesture"
        /** Distance threshold to detect a swipe vs tap (in pixels). */
        private const val SWIPE_DISTANCE_THRESHOLD = 30f
        /** Hit radius multiplier for nearest-key detection. */
        private const val KEY_HIT_RADIUS_MULT = 1.5f
        /** Resample the path to this many equidistant points for DTW matching. */
        private const val RESAMPLE_COUNT = 40
        /** Minimum points to consider a valid gesture. */
        private const val MIN_GESTURE_POINTS = 5
        /** Max length for gesture-matched words. */
        private const val MAX_GESTURE_WORD_LENGTH = 20

        // Scoring weights
        private const val DTW_WEIGHT = 0.45f
        private const val LEV_WEIGHT = 0.15f
        private const val LENGTH_WEIGHT = 0.10f
        private const val FREQ_WEIGHT = 0.30f

        /** Minimum subsequence ratio to consider a match. */
        private const val MIN_SUBSEQ_RATIO = 0.30f
        /** Maximum edit distance ratio (relative to pattern length). */
        private const val MAX_EDIT_RATIO = 0.65f
    }

    data class GestureResult(
        val word: String,
        val score: Float
    )

    private val touchPoints = mutableListOf<PointF>()
    private var startTime = 0L
    private var endTime = 0L
    private var lastKeySequence = listOf<String>()
    /** Cached key center positions for fast nearest-key lookups. */
    private var cachedKeyCenters: Map<String, PointF>? = null
    /** Average key width (computed from cached centers). */
    private var avgKeyWidth: Float = 55f

    /** Start tracking a new gesture. */
    fun startGesture(x: Float, y: Float) {
        touchPoints.clear()
        touchPoints.add(createPoint(x, y))
        startTime = System.currentTimeMillis()
        lastKeySequence = emptyList()
    }

    /** Add a point during the gesture. Skips exact duplicates. */
    fun addPoint(x: Float, y: Float) {
        if (touchPoints.isEmpty()) {
            touchPoints.add(createPoint(x, y)); return
        }
        val last = touchPoints.last()
        val dx = x - last.x; val dy = y - last.y
        if (dx * dx + dy * dy < 4f) return
        touchPoints.add(createPoint(x, y))
    }

    /** Force-add a point (for ACTION_UP), bypassing the distance filter. */
    fun forceAddPoint(x: Float, y: Float) {
        if (touchPoints.isNotEmpty() && touchPoints.last().x == x && touchPoints.last().y == y) return
        touchPoints.add(createPoint(x, y))
    }

    /** End tracking. Returns true if the gesture is a swipe (not a tap). */
    fun endGesture(x: Float, y: Float): Boolean {
        addPoint(x, y)
        endTime = System.currentTimeMillis()
        return isSwipe()
    }

    /** Check if the tracked points constitute a swipe. */
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
     * @param previousWord Previous word for bigram context (may be null).
     * @return List of (word, score) pairs sorted by score descending.
     */
    fun recognize(
        keys: List<Key>,
        predictor: PredictorEngine,
        topK: Int = 5,
        previousWord: String? = null
    ): List<GestureResult> {
        if (touchPoints.size < MIN_GESTURE_POINTS) return emptyList()

        // Cache key centers for fast lookups
        cacheKeyCenters(keys)

        // 1. Resample touch path to equidistant points
        val resampled = resamplePath(touchPoints)

        // 2. Build key sequence from resampled path
        val keySequence = traceKeys(resampled)
        if (keySequence.isEmpty()) return emptyList()
        lastKeySequence = keySequence

        val pattern = keySequence.joinToString("")
        Log.d(TAG, "Swipe pattern: '$pattern' (${pattern.length} chars from ${resampled.size} pts)")

        // 3. Generate candidates using multiple strategies
        return generateCandidates(pattern, resampled, predictor, topK, previousWord)
    }

    /** Get the raw touch points (for drawing the trail). */
    fun getTouchPoints(): List<PointF> = touchPoints.toList()

    /** Get the last detected key sequence. */
    fun getLastKeySequence(): List<String> = lastKeySequence

    // ────────  Internal  ────────

    /**
     * Resample the touch path to exactly [RESAMPLE_COUNT] equidistant points
     * using linear interpolation. This normalizes different swipe speeds.
     * Delegates to [resamplePathTo] with the default count.
     */
    private fun resamplePath(points: List<PointF>): List<PointF> {
        return resamplePathTo(points, RESAMPLE_COUNT)
    }

    /**
     * Trace the path across keys, returning the sequence of key labels visited.
     * Uses adaptive hit radius based on key width.
     */
    private fun traceKeys(points: List<PointF>): List<String> {
        val visited = mutableListOf<String>()
        var lastKey: String? = null
        var consecutiveMisses = 0

        for (pt in points) {
            val nearest = findNearestKey(pt)
            if (nearest != null) {
                if (nearest != lastKey) {
                    visited.add(nearest)
                    lastKey = nearest
                }
                consecutiveMisses = 0
            } else {
                consecutiveMisses++
                // Allow a few misses in a row (between keys) but not too many
                if (consecutiveMisses > 3) break
            }
        }

        return visited
    }

    /**
     * Find the nearest letter key to a touch point using cached key centers.
     * Returns null if no key is within the hit radius.
     * Uses proximity-weighted scoring: a point can match a key even if slightly
     * outside the hit radius, but with reduced confidence.
     */
    private fun findNearestKey(point: PointF): String? {
        val centers = cachedKeyCenters ?: return null
        var bestKey: String? = null
        var bestDist = Float.MAX_VALUE

        for ((label, center) in centers) {
            val dx = point.x - center.x
            val dy = point.y - center.y
            val d = dx * dx + dy * dy
            if (d < bestDist) {
                bestDist = d
                bestKey = label
            }
        }

        // Adaptive hit radius based on average key width
        val maxDistSq = (KEY_HIT_RADIUS_MULT * avgKeyWidth) *
                         (KEY_HIT_RADIUS_MULT * avgKeyWidth)

        return if (bestDist <= maxDistSq) bestKey else null
    }

    /**
     * Cache the center positions of ALL letter keys including apostrophe and
     * special Latin characters for fast lookups during gesture recognition.
     */
    private fun cacheKeyCenters(keys: List<Key>) {
        if (cachedKeyCenters != null) return // Already cached for this recognize() call

        val centers = mutableMapOf<String, PointF>()
        var totalWidth = 0f
        var keyCount = 0

        for (key in keys) {
            // Include all letter keys (a-z, ñ, apostrophe, accented chars)
            val code = key.code
            val isLetter = (code in 'a'.code..'z'.code) ||
                           code == 'ñ'.code ||
                           code == '\''.code ||
                           (code in 'à'.code..'ÿ'.code)  // accented Latin range
            if (!isLetter) continue

            centers[key.label] = createPoint(key.centerX, key.centerY)
            totalWidth += key.width
            keyCount++
        }

        cachedKeyCenters = centers
        avgKeyWidth = if (keyCount > 0) totalWidth / keyCount else 55f

        Log.d(TAG, "Cached ${centers.size} key centers, avg width=${"%.1f".format(avgKeyWidth)}")
    }

    /**
     * Generate word candidates from a swipe pattern using DTW path matching.
     *
     * ## Strategies (applied in order, deduplicated):
     *
     * 1. **PredictorEngine.predict()** — uses FuzzyScorer for initial candidates
     * 2. **DTW spatial matching** — compares touch path vs ideal key path for
     *    each candidate from the key sequence pattern
     * 3. **Prefix fallback** — if not enough candidates, broad prefix search
     */
    private fun generateCandidates(
        pattern: String,
        touchPath: List<PointF>,
        predictor: PredictorEngine,
        topK: Int,
        previousWord: String? = null
    ): List<GestureResult> {
        val results = mutableMapOf<String, Float>()
        val centers = cachedKeyCenters ?: return emptyList()

        // ── Strategy 1: PredictorEngine.predict() with FuzzyScorer ──
        val predictions = predictor.predict(pattern, previousWord, topK * 2)
        for ((i, word) in predictions.withIndex()) {
            if (word.length > MAX_GESTURE_WORD_LENGTH) continue
            // Compute DTW score to refine the prediction ranking
            val dtwScore = computeDTWScore(word, pattern, touchPath, centers)
            val freq = getWordFrequency(word, predictor)
            val finalScore = combineScore(dtwScore, word, pattern, freq)
            results[word] = maxOf(results[word] ?: 0f, finalScore)
        }

        // ── Strategy 2: DTW spatial matching ──
        // Use the key sequence pattern to generate word candidates
        if (results.size < topK * 3 && pattern.length >= 2) {
            val candidates = generateWordCandidates(pattern, predictor)
            for (we in candidates.take(300)) {
                if (we.word in results || we.word.length > MAX_GESTURE_WORD_LENGTH) continue
                val dtwScore = computeDTWScore(we.word, pattern, touchPath, centers)
                if (dtwScore > 0f) {
                    val finalScore = combineScore(dtwScore, we.word, pattern, we.frequency)
                    if (finalScore > 0f) {
                        results[we.word] = finalScore
                    }
                }
            }
        }

        // ── Strategy 3: Single-char prefix fallback ──
        if (results.size < topK && pattern.length >= 1) {
            val broadResults = generateWordCandidates(
                pattern.first().toString(), predictor
            )
            for (we in broadResults.take(150)) {
                if (we.word in results || we.word.length > MAX_GESTURE_WORD_LENGTH) continue
                val dtwScore = computeDTWScore(we.word, pattern, touchPath, centers)
                if (dtwScore > 0f) {
                    val finalScore = combineScore(dtwScore, we.word, pattern, we.frequency)
                    if (finalScore > 0f) {
                        results[we.word] = finalScore
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
     * Generate word candidates from a key sequence pattern using prefix search.
     *
     * Uses the first 1-3 chars of the pattern as prefix, then filters by
     * subsequence match and length constraints.
     */
    private fun generateWordCandidates(
        pattern: String,
        predictor: PredictorEngine
    ): List<WordEntry> {
        val results = mutableListOf<WordEntry>()
        val patternLower = pattern.lowercase()

        if (patternLower.isEmpty()) return results

        // Try prefix lengths from longest to shortest
        for (prefixLen in minOf(3, patternLower.length) downTo 1) {
            val prefix = patternLower.take(prefixLen)
            val prefixResults = predictor.searchPrefix(prefix, minLength = 2)
            for (we in prefixResults) {
                if (results.any { it.word == we.word }) continue
                if (we.word.length > MAX_GESTURE_WORD_LENGTH) continue

                // Filter by subsequence match: letters of the word must appear
                // in order within the pattern with some tolerance
                if (isSubsequenceMatch(we.word, patternLower)) {
                    results.add(we)
                }
            }

            if (results.size >= 300) break
        }

        return results
    }

    /**
     * Check if the word's letters appear as a subsequence of the pattern
     * with tolerance for extra characters between matches.
     */
    private fun isSubsequenceMatch(word: String, pattern: String): Boolean {
        val wl = word.lowercase()
        val pl = pattern.lowercase()
        var wi = 0
        var skips = 0
        val maxSkipsPerChar = maxOf(3, pl.length / 3)

        for (ch in pl) {
            if (wi < wl.length) {
                if (ch == wl[wi]) {
                    wi++
                    skips = 0
                } else {
                    skips++
                    if (skips > maxSkipsPerChar) return false
                }
            }
        }

        val ratio = wi.toFloat() / wl.length
        return ratio >= MIN_SUBSEQ_RATIO
    }

    /**
     * Compute DTW (Dynamic Time Warping) distance between the user's touch path
     * and the ideal key-center path for a candidate word.
     *
     * - [word]: The candidate word
     * - [pattern]: The key sequence detected from the swipe
     * - [touchPath]: The resampled touch points
     * - [keyCenters]: Cached key center positions
     *
     * @return Score 0-100 where higher = better match.
     */
    private fun computeDTWScore(
        word: String,
        pattern: String,
        touchPath: List<PointF>,
        keyCenters: Map<String, PointF>
    ): Float {
        if (word.length < 2 || touchPath.size < 3) return 0f

        val wordLower = word.lowercase()
        val patternLower = pattern.lowercase()

        // ── 1. Subsequence check (quick reject) ──
        if (!isSubsequenceMatch(wordLower, patternLower)) return 0f

        // ── 2. Build ideal key path for the word ──
        val idealPath = mutableListOf<PointF>()
        for (ch in wordLower) {
            val label = ch.toString()
            val center = keyCenters[label] ?: return 0f
            idealPath.add(center)
        }
        if (idealPath.size < 2) return 0f

        // ── 3. Resample ideal path to match touch path length ──
        val resampledIdeal = resamplePathTo(idealPath, touchPath.size)

        // ── 4. Compute DTW distance ──
        val dtwDist = dynamicTimeWarping(touchPath, resampledIdeal)

        // ── 5. Normalize DTW distance ──
        // DTW distance is in pixels. Normalize by path length and key width.
        val avgDist = dtwDist / (word.length + touchPath.size)
        val normalizedDist = avgDist / (avgKeyWidth * 1.5f)

        // Convert to score: 0 distance → 100, large distance → 0
        val spatialScore = (1f - minOf(1f, normalizedDist)) * 100f

        // ── 6. Apply additional penalties ──
        // Levenshtein distance between word and pattern
        val lev = levenshtein(wordLower, patternLower)
        val maxLen = maxOf(wordLower.length, patternLower.length, 1)
        val levRatio = lev.toFloat() / maxLen
        if (levRatio > MAX_EDIT_RATIO) return 0f
        val levScore = (1f - levRatio) * 100f

        // Length difference penalty
        val lenDiff = abs(wordLower.length - patternLower.length)
        val lenPenalty = maxOf(0f, 1f - lenDiff.toFloat() / maxOf(wordLower.length, 3))
        val lenScore = lenPenalty * 100f

        // ── 7. Combined score ──
        return spatialScore * 0.50f +
               levScore * 0.25f +
               lenScore * 0.25f
    }

    /**
     * Full DTW (Dynamic Time Warping) implementation.
     *
     * Computes the minimum alignment cost between two point sequences.
     * Allows non-linear alignment (warping) to handle different gesture speeds
     * and imprecise paths.
     */
    private fun dynamicTimeWarping(seq1: List<PointF>, seq2: List<PointF>): Float {
        val n = seq1.size
        val m = seq2.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        // Use only two rows for memory efficiency
        var prev = FloatArray(m) { Float.MAX_VALUE }
        val curr = FloatArray(m)

        for (i in 0 until n) {
            curr[0] = pointDist(seq1[i], seq2[0])
            if (i > 0) curr[0] += prev[0]

            for (j in 1 until m) {
                val cost = pointDist(seq1[i], seq2[j])
                val minPrev = minOf(
                    if (i > 0) prev[j] else Float.MAX_VALUE,
                    if (i > 0) prev[j - 1] else Float.MAX_VALUE,
                    curr[j - 1]  // i is same row, j-1
                )
                curr[j] = cost + minPrev
            }

            // Swap rows
            val temp = prev
            prev = curr
            // curr will be reused
        }

        return prev[m - 1]
    }

    /**
     * Resample a path to exactly [targetCount] equidistant points.
     */
    private fun resamplePathTo(points: List<PointF>, targetCount: Int): List<PointF> {
        if (points.size < 2 || targetCount < 2) return points

        var totalLength = 0f
        for (i in 1 until points.size) {
            totalLength += dist(points[i - 1], points[i])
        }

        if (totalLength <= 0f) {
            // All points at same location
            return List(targetCount) { points[0] }
        }

        val step = totalLength / targetCount
        val result = mutableListOf(points[0])

        var accumLen = 0f
        var segmentStart = points[0]
        var segmentIndex = 1

        for (i in 1 until targetCount) {
            val targetLen = i * step
            while (segmentIndex < points.size) {
                val segLen = dist(segmentStart, points[segmentIndex])
                if (accumLen + segLen >= targetLen) {
                    val t = if (segLen > 0f) (targetLen - accumLen) / segLen else 0f
                    val pt = createPoint(
                        segmentStart.x + (points[segmentIndex].x - segmentStart.x) * t,
                        segmentStart.y + (points[segmentIndex].y - segmentStart.y) * t
                    )
                    result.add(pt)
                    break
                }
                accumLen += segLen
                segmentStart = points[segmentIndex]
                segmentIndex++
            }
            if (segmentIndex >= points.size) break
        }

        // Fill remaining with last point if needed
        while (result.size < targetCount) {
            result.add(points.last())
        }

        return result
    }

    /**
     * Combine multiple scoring metrics into a final score.
     *
     * @param dtwScore DTW spatial similarity score (0-100)
     * @param word The candidate word
     * @param pattern The detected key sequence pattern
     * @param frequency Corpus frequency of the word
     * @return Final combined score (0-100)
     */
    private fun combineScore(
        dtwScore: Float,
        word: String,
        pattern: String,
        frequency: Int
    ): Float {
        val wordLower = word.lowercase()
        val patternLower = pattern.lowercase()
        val maxLen = maxOf(wordLower.length, patternLower.length, 1)

        // Levenshtein score
        val lev = levenshtein(wordLower, patternLower)
        val levRatio = lev.toFloat() / maxLen
        val levScore = (1f - levRatio) * 100f

        // Length score
        val lenDiff = abs(wordLower.length - patternLower.length)
        val lenPenalty = maxOf(0f, 1f - lenDiff.toFloat() / maxOf(wordLower.length, 3))
        val lenScore = lenPenalty * 100f

        // Frequency score (log-scaled)
        val freqScore = minOf(100f, maxOf(0f, kotlin.math.log10((frequency + 1).toFloat()) / 3.5f * 100f))

        // Combined
        return dtwScore * DTW_WEIGHT +
               levScore * LEV_WEIGHT +
               lenScore * LENGTH_WEIGHT +
               freqScore * FREQ_WEIGHT
    }

    /** Get the combined frequency (corpus + user boosts) for a word. */
    private fun getWordFrequency(word: String, predictor: PredictorEngine): Int {
        val results = predictor.searchPrefix(word, minLength = 1)
        return results.firstOrNull { it.word == word.lowercase() }?.frequency ?: 1
    }

    // ─── Levenshtein distance ───

    private fun levenshtein(s1: String, s2: String): Int {
        val a = s1.lowercase(); val b = s2.lowercase()
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

    /** Euclidean distance between two points. */
    private fun pointDist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /** Convenience alias for pointDist. */
    private fun dist(a: PointF, b: PointF): Float = pointDist(a, b)

    private fun createPoint(x: Float, y: Float): PointF {
        val pt = PointF(); pt.x = x; pt.y = y; return pt
    }

    fun reset() {
        touchPoints.clear()
        lastKeySequence = emptyList()
        cachedKeyCenters = null
    }
}
